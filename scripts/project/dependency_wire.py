#!/usr/bin/env python3
"""Independent strict parser/oracle for analysis-dependency-result 1.1 through 2.4."""
import argparse
import json
import re
from pathlib import Path


def require(ok, message):
    if not ok:
        raise ValueError(message)


def fields(obj, names):
    require(type(obj) is dict and set(obj) == set(names.split()), 'unknown/missing fields: ' + str(names))


def text(value):
    require(type(value) is str and not any(0xD800 <= ord(c) <= 0xDFFF for c in value), 'Unicode text')


def boolean(value):
    require(type(value) is bool, 'boolean')


def integer(value):
    require(type(value) is int and value >= 0, 'nonnegative integer')


def array(value):
    require(type(value) is list, 'array')
    return value


def u16(value):
    return value.encode('utf-16-be')


def identity(value, domain=None):
    require(type(value) is dict, 'ID object')
    d = value.get('domain')
    require(d in ('publication', 'unit', 'entry', 'label', 'operation', 'object', 'storage', 'origin', 'premise', 'uncertainty', 'operand', 'artifact', 'resource'), 'ID domain')
    if domain:
        require(d in domain.split(), 'wrong ID domain')
    names = 'domain localId' + ('' if d == 'publication' else ' publication')
    if d in ('entry', 'label', 'operation', 'object', 'operand'):
        names += ' unit'
    if d == 'operand':
        names += ' owner'
    fields(value, names)
    for key in set(value) - {'owner'}:
        text(value[key]); require(bool(value[key]), 'empty ID component')
    if d == 'operand':
        identity(value['owner'], 'entry operation')
        require(value['owner']['publication'] == value['publication'] and value['owner']['unit'] == value['unit'], 'operand owner')
    return value


def id_order(value):
    owner = value.get('owner', {})
    return tuple(u16(x) for x in (value.get('publication', value['localId']), value['domain'], value.get('unit', ''),
                                  owner.get('domain', '') + ':' + owner.get('localId', '') if owner else '', value['localId']))


def ordered(values, key):
    array(values); keys = [key(v) for v in values]
    require(keys == sorted(keys) and len(set(keys)) == len(keys), 'noncanonical/duplicate ordering')


def refs(values, domain=None):
    for v in array(values):
        identity(v, domain)
    ordered(values, id_order)


def supports(values):
    for s in array(values):
        fields(s, 'kind producer origin premises')
        require(s['kind'] in ('VALUE_PRODUCER', 'CALL_LITERAL', 'CICS_LITERAL'), 'support kind')
        identity(s['producer'], 'operation operand'); identity(s['origin'], 'origin'); refs(s['premises'], 'premise')
    ordered(values, lambda s: (id_order(s['producer']), id_order(s['origin'])))


def candidate(c, raw=False, technology="COBOL"):
    fields(c, 'rawValue supports' if raw else 'referenceName rawValue supports')
    text(c['rawValue']); supports(c['supports']); require(bool(c['supports']), 'candidate without support')
    if not raw:
        text(c['referenceName']); require(re.fullmatch(r'[A-Z0-9$@#]{1,8}' if technology=='CICS' else r'[A-Z_$][A-Z0-9_@#$]{0,7}', c['referenceName']) is not None, 'noncanonical reference')


def location(loc):
    if loc is None:
        return
    require(type(loc) is dict, 'location')
    if loc.get('kind') == 'OFFSETS':
        fields(loc, 'kind start end unit endExclusive'); text(loc['unit']); numbers = ('start', 'end')
    else:
        fields(loc, 'kind startLine startColumn endLine endColumn lineBase columnBase columnUnit endExclusive')
        require(loc['kind'] == 'LINE_COLUMNS', 'location kind')
        require(loc['columnUnit'] in ('UNICODE_SCALAR', 'UTF16_CODE_UNIT', 'OCTET'), 'column unit')
        require(loc['lineBase'] in ('0', '1') and loc['columnBase'] in ('0', '1'), 'coordinate base')
        numbers = ('startLine', 'startColumn', 'endLine', 'endColumn')
    for n in numbers:
        require(type(loc[n]) is str and re.fullmatch(r'0|[1-9][0-9]*', loc[n]) is not None, 'decimal location coordinate')
    boolean(loc['endExclusive'])


def site(s, extended=False):
    fields(s, 'caller entry sequence operation offset siteOrigin targetOrigin technology command namespace nameProfile targetKind subject valuePoint reachability targetStatus rawCandidates candidates modelValueRemainder sourceValueRemainder interpretationUnknownRemainder effectiveUnknownRemainder openControlRemainder evidence provenance premises uncertaintyRefs' + (' analysisStatus analysisReasons' if extended else ''))
    for name, domain in [('caller', 'unit'), ('entry', 'entry'), ('sequence', 'label'), ('operation', 'operation'), ('siteOrigin', 'origin'), ('targetOrigin', 'origin')]:
        identity(s[name], domain)
    require((s['technology'],s['namespace']) in (('CICS','cics.program'),('COBOL','cobol.program')), 'technology namespace')
    require(s['command'] in (('LINK','XCTL','UNKNOWN') if s['technology']=='CICS' else ('CALL',)), 'command')
    text(s['nameProfile']); require(bool(s['nameProfile']), 'name profile')
    integer(s['offset']); require(s['targetKind'] in ('LITERAL', 'COMPUTED'), 'target kind')
    for name in ('sourceValueRemainder', 'interpretationUnknownRemainder', 'effectiveUnknownRemainder', 'openControlRemainder'):
        boolean(s[name])
    if s['modelValueRemainder'] is not None:
        boolean(s['modelValueRemainder'])
    require(s['effectiveUnknownRemainder'] == (s['modelValueRemainder'] is True or s['interpretationUnknownRemainder']), 'remainder OR')
    refs(s['evidence'], 'operation operand'); refs(s['provenance'], 'origin'); refs(s['premises'], 'premise'); refs(s['uncertaintyRefs'], 'uncertainty')
    if extended:
        require(s['analysisStatus'] in ('COMPLETE', 'PARTIAL'), 'analysis status')
        reasons(s['analysisReasons'])
        require(bool(s['analysisReasons']) == (s['analysisStatus'] == 'PARTIAL'), 'site partial reasons')
    require(s['reachability'] in (('REACHABLE', 'UNREACHABLE_IN_MODEL', 'UNKNOWN') if extended else ('REACHABLE', 'UNREACHABLE_IN_MODEL')), 'reachability')
    if s['reachability'] == 'UNKNOWN':
        require(s['analysisStatus'] == 'PARTIAL' and s['openControlRemainder'], 'unknown execution must remain explicitly partial')
    require(s['targetStatus'] in ('RESOLVED_CANDIDATES', 'OPEN_TARGET', 'UNREACHABLE_IN_MODEL', 'UNSUPPORTED_TARGET_EXPRESSION', 'UNSUPPORTED_INVOCATION_SHAPE') + (('ANALYSIS_INCOMPLETE',) if extended else ()), 'target status')
    for c in array(s['rawCandidates']):
        candidate(c, raw=True)
    ordered(s['rawCandidates'], lambda c: u16(c['rawValue']))
    for c in array(s['candidates']):
        candidate(c,technology=s['technology'])
        require({'rawValue': c['rawValue'], 'supports': c['supports']} in s['rawCandidates'], 'candidate-specific raw/support association')
        interpreted = c['rawValue'].rstrip(' ') if s['targetKind'] == 'COMPUTED' or s['technology']=='CICS' else c['rawValue']
        require(c['referenceName'] == interpreted, 'unauthorized name transformation')
    ordered(s['candidates'], lambda c: (u16(c['referenceName']), u16(c['rawValue'])))
    for c in s['rawCandidates']:
        for support in c['supports']:
            require(support['producer'] in s['evidence'] and support['origin'] in s['provenance'], 'support refs lost')
            require(all(p in s['premises'] for p in support['premises']), 'premises lost')
            if s['targetKind'] == 'LITERAL':
                require(support['kind'] == ('CICS_LITERAL' if s['technology']=='CICS' else 'CALL_LITERAL') and support['producer'] == s['operation'] and support['origin'] == s['targetOrigin'], 'literal support')
            else:
                require(support['kind'] == 'VALUE_PRODUCER', 'computed support')
    require(all(s[n]['publication'] == s['caller']['publication'] for n in ('entry', 'sequence', 'operation', 'siteOrigin', 'targetOrigin')), 'site publication')
    require(all(s[n]['unit'] == s['caller']['localId'] for n in ('entry', 'sequence', 'operation')), 'caller UnitId')
    if s['targetKind'] == 'LITERAL':
        require(s['subject'] is None and s['valuePoint'] is None, 'literal value query')
    elif s['valuePoint'] is not None:
        # A fixed physical read has no nominal ObjectId; its AIR operation owns the range.
        if s['subject'] is not None: identity(s['subject'], 'object')
        p = s['valuePoint']
        fields(p, 'position entryId operationId outcome'); require(p == {'position': 'BEFORE', 'entryId': s['entry'], 'operationId': s['operation'], 'outcome': None}, 'BEFORE actual Invoke')
    else:
        require(s['subject'] is None and s['targetStatus'] in ('UNSUPPORTED_TARGET_EXPRESSION', 'UNREACHABLE_IN_MODEL', 'UNSUPPORTED_INVOCATION_SHAPE') + (('ANALYSIS_INCOMPLETE',) if extended else ()), 'missing computed subject')
    if s['reachability'] == 'UNREACHABLE_IN_MODEL':
        require(s['targetStatus'] == 'UNREACHABLE_IN_MODEL' and s['modelValueRemainder'] is None and not s['candidates'] and not s['rawCandidates'], 'unreachable shape')
    elif s['targetStatus'] == 'ANALYSIS_INCOMPLETE':
        require(extended and s['analysisStatus'] == 'PARTIAL' and s['targetKind'] == 'COMPUTED' and not s['candidates'] and not s['rawCandidates'] and s['modelValueRemainder'] is True and s['effectiveUnknownRemainder'], 'incomplete target must not invent or close values')
    elif s['targetStatus'] == 'RESOLVED_CANDIDATES':
        require(bool(s['candidates']) and s['modelValueRemainder'] is not None, 'resolved shape')
    elif s['targetStatus'] == 'OPEN_TARGET':
        require(not s['candidates'] and s['effectiveUnknownRemainder'], 'empty closed target')
    else:
        require(not s['candidates'] and s['interpretationUnknownRemainder'], 'unsupported target shape')


def site_order(s):
    return id_order(s['entry']), id_order(s['operation'])


def reasons(values):
    for reason in array(values):
        text(reason); require(bool(reason.strip()), 'empty analysis reason')
    ordered(values, u16)


def file_dependencies(f, document):
    fields(f, 'valuesProfile declarationInventory declarations sites edges metrics')
    context_profile = document['version'] in ('2.3.0','2.4.0','2.5.0')
    computed_profile = document['version'] in ('2.2.0','2.3.0','2.4.0','2.5.0')
    require(f['valuesProfile'] == ('file-values-context@1' if context_profile else 'file-values@1' if computed_profile else 'file-literal@1'), 'FILE values profile')
    require(f['declarationInventory'] in ('COMPLETE','PARTIAL','UNAVAILABLE'), 'FILE inventory')
    publication = document['publication']['localId']
    origins = [o['id'] for o in document['origins']]
    uncertainties = document['sourceUncertaintyRefs']
    def ref(value, domain):
        identity(value, domain); require(value['publication'] == publication, 'foreign FILE identity')
    def origin(value):
        ref(value, 'origin'); require(value in origins, 'unresolved FILE origin')
    def context(c, site):
        if site['namespace'] != 'cics.file':
            require(c is None, 'non-CICS context is not inferred'); return
        fields(c, 'selection targetKind valuePoint candidates unknownRemainder origin analysisReasons')
        require(c['selection'] in ('DEFAULT','EXPLICIT','UNAVAILABLE'), 'CICS source selection')
        boolean(c['unknownRemainder']); origin(c['origin']); reasons(c['analysisReasons']); array(c['candidates'])
        if c['selection'] != 'EXPLICIT':
            require(c['targetKind']==('ABSENT' if c['selection']=='DEFAULT' else 'UNKNOWN'), 'selection kind')
            require(not c['candidates'] and c['valuePoint'] is None, 'no fabricated system name')
            require(c['unknownRemainder']==(c['selection']=='UNAVAILABLE'), 'default selection is known without lookup')
            require(bool(c['analysisReasons'])==(c['selection']=='UNAVAILABLE'), 'context reason')
            return
        require(c['targetKind'] in ('LITERAL','COMPUTED'), 'SYSID target kind')
        if c['targetKind']=='LITERAL':require(c['valuePoint'] is None and len(c['candidates'])<=1, 'literal SYSID')
        else:require(c['valuePoint']==dict(position='BEFORE',entryId=site['entry'],operationId=site['operation'],outcome=None), 'SYSID query point')
        for candidate in c['candidates']:
            fields(candidate,'referenceName rawValue supports');text(candidate['referenceName']);text(candidate['rawValue'])
            raw=candidate['rawValue'];name=candidate['referenceName']
            require(name==raw.rstrip(' ') and 1<=len(raw)<=4 and 1<=len(name)<=4 and all(x in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789$@#' for x in name), 'SYSID source name policy')
            require(bool(array(candidate['supports'])), 'SYSID support required')
            for support in candidate['supports']:
                fields(support,'kind producer origin premises');ref(support['producer'],'operation operand');origin(support['origin']);refs(support['premises'],'premise')
                if c['targetKind']=='LITERAL':
                    require(support['kind']=='CICS_SYSID_LITERAL' and support['producer']['domain']=='operand' and support['producer']['owner']==site['operation'] and support['origin']==c['origin'] and not support['premises'], 'SYSID literal support')
                else:require(support['kind']=='VALUE_PRODUCER' and len(raw)==4, 'SYSID computed support and extent')
            if c['targetKind']=='LITERAL':require(len(candidate['supports'])==1 and not c['unknownRemainder'], 'SYSID literal interpretation closed')
        ordered(c['candidates'],lambda candidate:(u16(candidate['referenceName']),u16(candidate['rawValue'])))
        if site['reachability']=='UNREACHABLE_IN_MODEL':require(not c['candidates'], 'unreachable SYSID candidates')
        elif not c['unknownRemainder']:require(bool(c['candidates']), 'empty closed explicit SYSID')
    for d in array(f['declarations']):
        fields(d, 'id owner logicalFile classification sourceKind targetKind namespace name objects origin')
        ref(d['id'], 'resource'); origin(d['origin'])
        for k in ('classification','sourceKind'):
            text(d[k]); require(bool(d[k]), 'empty declaration classification')
        if d['owner'] is None:
            require(d['logicalFile'] is None and not d['objects'], 'unavailable owner association')
        else:
            ref(d['owner'], 'unit'); text(d['logicalFile']); require(bool(d['logicalFile']), 'logical file')
        require(d['targetKind'] in ('LITERAL','LOCAL','COMPUTED','UNKNOWN'), 'declaration target kind')
        if d['targetKind'] == 'LOCAL':
            require(d['namespace'] is None and d['name'] is None, 'local work has no external target')
        else:
            text(d['namespace']); require(bool(d['namespace']), 'target namespace')
            require(d['name'] is not None if d['targetKind'] == 'LITERAL' else d['name'] is None, 'declaration name status')
            if d['name'] is not None: text(d['name']); require(bool(d['name']), 'empty external name')
        if d['sourceKind'] == 'ASSIGNMENT_NAME':
            require(d['targetKind'] == 'LITERAL' and d['namespace'] == 'cobol.external-file-name', 'ASSIGN source name domain')
        for obj in array(d['objects']):
            fields(obj, 'object role'); ref(obj['object'], 'object'); text(obj['role']); require(bool(obj['role']), 'object role')
            require(d['owner'] is not None and obj['object']['unit'] == d['owner']['localId'], 'record owner')
        ordered(d['objects'], lambda o:(id_order(o['object']),u16(o['role'])))
    ordered(f['declarations'], lambda d: u16(d['id']['localId']))
    declarations = [d['id'] for d in f['declarations']]
    for s in array(f['sites']):
        fields(s, 'owner entry sequence operation action namespace targetKind bindings valuePoint candidates unknownRemainder reachability effects control origin targetOrigin uncertaintyRefs analysisReasons' + (' context' if context_profile else ''))
        ref(s['owner'],'unit')
        for k, domain in (('entry','entry'),('sequence','label'),('operation','operation')):
            ref(s[k],domain); require(s[k]['unit']==s['owner']['localId'], 'FILE site owner')
        text(s['action']); require(bool(s['action']),'FILE action')
        local = s['targetKind'] == 'LOCAL' and document['version'] in ('2.1.0','2.2.0','2.3.0','2.4.0','2.5.0')
        if local: require(s['namespace'] is None, 'local use has no external namespace')
        else: text(s['namespace']); require(bool(s['namespace']), 'FILE namespace')
        require(local or s['targetKind'] in ('LITERAL','COMPUTED'), 'FILE target kind')
        require(s['reachability'] in ('REACHABLE','UNREACHABLE_IN_MODEL','UNKNOWN'), 'FILE reachability')
        boolean(s['unknownRemainder']); reasons(s['analysisReasons'])
        for k in ('effects','control'):require(s[k] in ('EXACT','CONSERVATIVE','OPEN','UNAVAILABLE','NOT_APPLICABLE'), 'FILE precision')
        origin(s['origin']); origin(s['targetOrigin']); refs(s['uncertaintyRefs'],'uncertainty')
        require(all(u in uncertainties for u in s['uncertaintyRefs']), 'FILE uncertainty closure')
        if s['reachability']=='UNKNOWN':require(bool(s['analysisReasons']), 'unknown reachability reason')
        for b in array(s['bindings']):
            fields(b,'declaration role origin'); ref(b['declaration'],'resource'); origin(b['origin']); text(b['role']); require(bool(b['role']), 'binding role')
            require(b['declaration'] in declarations, 'unresolved FILE declaration')
        ordered(s['bindings'],lambda b:(u16(b['declaration']['localId']),u16(b['role'])))
        if local:
            require(s['action']=='resource-use' and s['valuePoint'] is None and not s['candidates'] and not s['unknownRemainder'], 'local use is not an unknown external target')
            require(any(b['declaration']==d['id'] and d['targetKind']=='LOCAL' for b in s['bindings'] for d in f['declarations']), 'local use needs local declaration')
        elif s['targetKind']=='LITERAL':require(s['valuePoint'] is None, 'literal does not query values')
        else:
            require(s['valuePoint']==dict(position='BEFORE',entryId=s['entry'],operationId=s['operation'],outcome=None), 'FILE query point')
            if not computed_profile:require(s['unknownRemainder'] and not s['candidates'] and 'FILE_VALUES_NOT_YET_ANALYZED' in s['analysisReasons'], 'W1 computed value remains open')
        for c in array(s['candidates']):
            fields(c,'referenceName rawValue supports');text(c['referenceName']);text(c['rawValue'])
            require(bool(c['referenceName']), 'empty FILE name')
            if computed_profile and s['namespace']=='cics.file':
                require(c['referenceName']==c['rawValue'].rstrip(' '), 'FILE unauthorized name transformation')
                require(1<=len(c['rawValue'])<=8 and 1<=len(c['referenceName'])<=8 and all(x in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789$@#' for x in c['referenceName']), 'CICS filename alphabet and length')
            else:require(c['referenceName']==c['rawValue'], 'FILE exact name transformed')
            if s['targetKind']=='LITERAL':
                require(not s['unknownRemainder'], 'literal interpretation closed')
                require(c['supports']==[dict(kind='FILE_LITERAL',producer=s['operation'],origin=s['targetOrigin'],premises=[])], 'FILE literal support')
            else:
                require(computed_profile and s['namespace']=='cics.file' and len(c['rawValue'])==8, 'computed CICS name area')
                require(bool(array(c['supports'])), 'computed candidate needs support')
                for support in c['supports']:
                    fields(support,'kind producer origin premises');require(support['kind']=='VALUE_PRODUCER', 'computed FILE support')
                    identity(support['producer'],'operation operand');require(support['producer']['publication']==publication,'foreign producer');origin(support['origin']);refs(support['premises'],'premise')
        ordered(s['candidates'],lambda c:(u16(c['referenceName']),u16(c['rawValue'])))
        if s['targetKind']=='LITERAL' or not computed_profile:require(len(s['candidates'])<=1,'literal cardinality')
        if s['reachability']=='UNREACHABLE_IN_MODEL':require(not s['candidates'],'unreachable FILE candidates')
        elif not local and not s['unknownRemainder']:require(bool(s['candidates']),'empty closed FILE target')
        if context_profile:context(s['context'],s)
    ordered(f['sites'],lambda s:(u16(s['entry']['unit']),u16(s['entry']['localId']),u16(s['operation']['localId'])))
    expected=[dict(owner=s['owner'],entry=s['entry'],site=s['operation'],candidate=c,openSite=s['unknownRemainder'] or bool(context_profile and s['context'] and s['context']['unknownRemainder']),**({'context':s['context']} if context_profile else {}))
              for s in f['sites'] if s['reachability']!='UNREACHABLE_IN_MODEL' for c in s['candidates']]
    require(f['edges']==expected,'FILE edge projection')
    require(type(f['metrics']) is dict,'FILE metrics')
    for k,v in f['metrics'].items():text(k);integer(v)


def source_dependencies(value, document):
    fields(value, 'profile available remainder gapCodes occurrences dependencies')
    require(value['profile']=='source-dependencies@1','source profile')
    boolean(value['available']); boolean(value['remainder']); reasons(value['gapCodes']); integer(value['occurrences'])
    origins={o['id']['localId']:o for o in document['origins']}
    artifacts={a['id']['localId']:a for a in document['artifacts']}
    publication=document['publication']['localId']; count=0; seen=set()
    dependencies=array(value['dependencies'])
    require(value['available'] or not dependencies,'unavailable source inventory has dependencies')
    ordered(dependencies,lambda d:(u16(d['program']['localId']),u16(d['kind']),u16(d['name']),u16(d['qualification'])))
    for d in dependencies:
        fields(d,'program kind name qualification remainder supports')
        identity(d['program'],'unit'); require(d['program']['publication']==publication,'foreign source owner')
        require(d['kind'] in (('COPYBOOK','DCLGEN','SQL_INCLUDE','DB2_TABLE') if document['version']=='2.5.0' else ('COPYBOOK','DCLGEN','SQL_INCLUDE')),'source kind')
        text(d['name']); text(d['qualification']); boolean(d['remainder'])
        require(d['name'] and d['name']==d['name'].upper() and d['qualification']==d['qualification'].upper(),'canonical source name')
        array(d['supports']); require(bool(d['supports']),'source evidence absent')
        ordered(d['supports'],lambda s:u16(s['occurrence']['localId']))
        incomplete=False
        for support in d['supports']:
            fields(support,'occurrence origin sourceOwner relationship resolution resolvedArtifact classificationAuthority'+(' operation access' if document['version']=='2.5.0' else ''))
            for name,domain in [('occurrence','resource'),('origin','origin'),('sourceOwner','artifact')]:
                identity(support[name],domain); require(support[name]['publication']==publication,'foreign source reference')
            occurrence=support['occurrence']['localId']; require(occurrence not in seen,'duplicate source occurrence'); seen.add(occurrence)
            require(support['origin']['localId'] in origins and support['sourceOwner']['localId'] in artifacts,'source provenance absent')
            origin=origins[support['origin']['localId']]
            if origin['kind']=='DERIVED' and origin['rule']=='sp-provenance/original-expanded@1':
                written=[origins[i['localId']] for i in origin['inputs'] if origins[i['localId']]['kind']=='WRITTEN' and origins[i['localId']]['artifact']==support['sourceOwner']]
                require(len(written)==1,'original source evidence ambiguous'); origin=written[0]
            require(origin['kind']=='WRITTEN' and origin['artifact']==support['sourceOwner'] and origin['location'] is not None,'written original source evidence')
            require(support['relationship']==('TRANSITIVE' if origin['includes'] else 'DIRECT'),'nested source ownership')
            require(support['resolution'] in ('RESOLVED','UNRESOLVED','CYCLIC','IO_ERROR','NOT_APPLICABLE'),'source resolution')
            text(support['resolvedArtifact']); require(bool(support['resolvedArtifact'])==(support['resolution']=='RESOLVED'),'resolved artifact presence')
            authority=support['classificationAuthority']
            require(authority in ('COPY_SYNTAX','CONFIGURED_DCLGEN','CONFIGURED_SQL_INCLUDE','BUILTIN_SQL_INCLUDE','UNKNOWN','STATIC_SQL_TABLE_POSITION'),'source authority')
            require((d['kind']=='COPYBOOK')==(authority=='COPY_SYNTAX'),'COPY authority')
            require((d['kind']=='DCLGEN')==(authority=='CONFIGURED_DCLGEN'),'DCLGEN authority')
            require(d['kind']!='DCLGEN' or d['name'] not in ('SQLCA','SQLDA'),'builtins not DCLGEN')
            require((d['kind']=='DB2_TABLE')==(authority=='STATIC_SQL_TABLE_POSITION')==(support['resolution']=='NOT_APPLICABLE'),'DB2 authority/resolution')
            if document['version']=='2.5.0':
                usage=(support['operation'],support['access'])
                require(usage in {('SELECT','READ'),('INSERT','WRITE'),('UPDATE','WRITE'),('DELETE','WRITE'),('MERGE','READ'),('MERGE','READ_WRITE')} if d['kind']=='DB2_TABLE' else usage==('NONE','NONE'),'source usage')
            incomplete |= support['resolution'] not in ('RESOLVED','NOT_APPLICABLE') or authority=='UNKNOWN'
            count+=1
        require(d['remainder']==incomplete,'source remainder')
    require(value['occurrences']==count,'source occurrence count')
    require(value['remainder']==(not value['available'] or bool(value['gapCodes']) or any(d['remainder'] for d in dependencies)),'source inventory remainder')


def validate(d):
    files = d.get('version') in ('2.0.0','2.1.0','2.2.0','2.3.0','2.4.0','2.5.0')
    extended = files or d.get('version') == '1.2.0'
    fields(d, 'schema version airVersion publication interpretationProfile valuesProfile modelScope publicationInventory sites edges metrics origins artifacts sourceUncertaintyRefs' + (' analysisStatus analysisReasons' if extended else '') + (' analysisBoundary fileDependencies' if files else '') + (' sourceDependencies' if d.get('version') in ('2.4.0','2.5.0') else ''))
    require(d['schema'] == 'analysis-dependency-result' and d['version'] in ('1.1.0', '1.2.0', '2.0.0', '2.1.0', '2.2.0', '2.3.0', '2.4.0', '2.5.0') and d['airVersion'] == '2.0.0', 'schema/version')
    identity(d['publication'], 'publication')
    require(d['interpretationProfile'] == 'per-site' and d['valuesProfile'] == 'scalar-text-effects@1' and d['modelScope'] in (('KNOWN_GRAPH_ENTRY', 'STRUCTURAL_AIR_OCCURRENCES') if extended else ('KNOWN_GRAPH_ENTRY',)), 'profiles/scope')
    require(d['publicationInventory'] in ('COMPLETE', 'PARTIAL', 'UNAVAILABLE'), 'inventory')
    for s in array(d['sites']):
        site(s, extended); require(s['caller']['publication'] == d['publication']['localId'], 'foreign site')
    ordered(d['sites'], site_order)
    if extended:
        require(d['analysisStatus'] in (('COMPLETE','PARTIAL') if files else ('PARTIAL',)), 'extended result partial status')
        reasons(d['analysisReasons'])
        require((d['analysisStatus']=='PARTIAL') == (bool(d['analysisReasons']) or any(s['analysisStatus'] == 'PARTIAL' for s in d['sites']) or d.get('version') in ('2.4.0','2.5.0') and d['sourceDependencies']['available'] and d['sourceDependencies']['remainder']), 'partial result must expose its cause')
        structural = any(s['reachability'] == 'UNKNOWN' for s in d['sites']) or not d['sites'] and bool(d['analysisReasons'])
        require((d['modelScope'] == 'STRUCTURAL_AIR_OCCURRENCES') == structural, 'structural/graph scope mismatch')
    expected = [dict(caller=s['caller'], entry=s['entry'], site=s['operation'], candidate=c, openSite=s['effectiveUnknownRemainder'] or s['reachability']=='UNKNOWN')
                for s in d['sites'] if s['reachability'] != 'UNREACHABLE_IN_MODEL' for c in s['candidates']]
    require(d['edges'] == expected, 'edge projection differs from reachable candidates')
    require(type(d['metrics']) is dict, 'metrics')
    for name, value in d['metrics'].items():
        text(name); integer(value)
    for name in ('possibleValuesPreparations', 'possibleValuesRuns', 'reachabilityRuns'):
        require(name in d['metrics'], 'missing execution counter')
    refs(d['sourceUncertaintyRefs'], 'uncertainty')
    for o in array(d['origins']):
        identity(o.get('id'), 'origin'); kind = o.get('kind')
        if kind == 'DERIVED':
            fields(o, 'id kind inputs rule'); refs(o['inputs'], 'origin'); text(o['rule']); require(bool(o['inputs']), 'derived inputs')
        elif kind == 'UNAVAILABLE':
            fields(o, 'id kind reason'); text(o['reason'])
        elif kind == 'CONTRACTUAL':
            fields(o, 'id kind authority version'); text(o['authority']); text(o['version'])
        else:
            fields(o, 'id kind artifact location exact includes'); require(kind == 'WRITTEN', 'origin kind'); identity(o['artifact'], 'artifact'); location(o['location']); boolean(o['exact'])
            for f in array(o['includes']):
                fields(f, 'including included requestedName site'); identity(f['including'], 'artifact'); identity(f['included'], 'artifact'); text(f['requestedName']); location(f['site'])
    ordered(d['origins'], lambda o: u16(o['id']['localId']))
    origins = [o['id'] for o in d['origins']]
    for s in d['sites']:
        require(all(ref in origins for ref in [s['siteOrigin'], s['targetOrigin'], *s['provenance']]), 'unresolved source origin')
    for o in d['origins']:
        if o['kind'] == 'DERIVED':
            require(all(ref in origins for ref in o['inputs']), 'unresolved derivation origin')
    for a in array(d['artifacts']):
        fields(a, 'id logicalName contentDigest'); identity(a['id'], 'artifact'); text(a['logicalName'])
        if a['contentDigest'] is not None:
            text(a['contentDigest'])
    ordered(d['artifacts'], lambda a: u16(a['id']['localId']))
    artifacts = [a['id'] for a in d['artifacts']]
    require(all(o['artifact'] in artifacts for o in d['origins'] if o['kind'] == 'WRITTEN'), 'unresolved artifact')
    if files:
        require(d['analysisBoundary']=='COBOL_SOURCE_ONLY','source-only boundary')
        file_dependencies(d['fileDependencies'],d)
    if d['version'] in ('2.4.0','2.5.0'):
        source_dependencies(d['sourceDependencies'],d)
    return d


def encode(d):
    return (json.dumps(d, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False) + '\n').encode('utf-8')


def read(path):
    raw = Path(path).read_bytes()
    def pairs(items):
        result = {}
        for k, v in items:
            require(k not in result, 'duplicate JSON key'); result[k] = v
        return result
    d = json.loads(raw.decode('utf-8'), object_pairs_hook=pairs, parse_constant=lambda x: (_ for _ in ()).throw(ValueError('nonfinite number')))
    validate(d); require(encode(d) == raw, 'noncanonical bytes'); return d


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('path', type=Path); args = parser.parse_args()
    data = read(args.path); print('PASS: strict dependency wire, sites=' + str(len(data['sites'])))
