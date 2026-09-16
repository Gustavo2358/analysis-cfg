#!/usr/bin/env python3
"""Independent strict parser/oracle for analysis-dependency-result 1.1.0/1.2.0."""
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
    require(d in ('publication', 'unit', 'entry', 'label', 'operation', 'object', 'storage', 'origin', 'premise', 'uncertainty', 'operand', 'artifact'), 'ID domain')
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
    require(s['effectiveUnknownRemainder'] == (s['modelValueRemainder'] is True or s['sourceValueRemainder'] or s['interpretationUnknownRemainder']), 'remainder OR')
    refs(s['evidence'], 'operation operand'); refs(s['provenance'], 'origin'); refs(s['premises'], 'premise'); refs(s['uncertaintyRefs'], 'uncertainty')
    if extended:
        require(s['analysisStatus'] in ('COMPLETE', 'PARTIAL'), 'analysis status')
        reasons(s['analysisReasons'])
        require(bool(s['analysisReasons']) == (s['analysisStatus'] == 'PARTIAL'), 'site partial reasons')
    require(s['reachability'] in (('REACHABLE', 'UNREACHABLE_IN_MODEL', 'UNKNOWN') if extended else ('REACHABLE', 'UNREACHABLE_IN_MODEL')), 'reachability')
    if s['reachability'] == 'UNKNOWN':
        require(s['analysisStatus'] == 'PARTIAL' and s['openControlRemainder'] and s['effectiveUnknownRemainder'], 'unknown execution must remain explicitly partial')
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


def validate(d):
    extended = d.get('version') == '1.2.0'
    fields(d, 'schema version airVersion publication interpretationProfile valuesProfile modelScope publicationInventory sites edges metrics origins artifacts sourceUncertaintyRefs' + (' analysisStatus analysisReasons' if extended else ''))
    require(d['schema'] == 'analysis-dependency-result' and d['version'] in ('1.1.0', '1.2.0') and d['airVersion'] == '2.0.0', 'schema/version')
    identity(d['publication'], 'publication')
    require(d['interpretationProfile'] == 'per-site' and d['valuesProfile'] == 'scalar-text-effects@1' and d['modelScope'] in (('KNOWN_GRAPH_ENTRY', 'STRUCTURAL_AIR_OCCURRENCES') if extended else ('KNOWN_GRAPH_ENTRY',)), 'profiles/scope')
    require(d['publicationInventory'] in ('COMPLETE', 'PARTIAL', 'UNAVAILABLE'), 'inventory')
    for s in array(d['sites']):
        site(s, extended); require(s['caller']['publication'] == d['publication']['localId'], 'foreign site')
    ordered(d['sites'], site_order)
    if extended:
        require(d['analysisStatus'] == 'PARTIAL', 'extended result partial status')
        reasons(d['analysisReasons'])
        require(bool(d['analysisReasons']) or any(s['analysisStatus'] == 'PARTIAL' for s in d['sites']), 'partial result must expose its cause')
        structural = any(s['reachability'] == 'UNKNOWN' for s in d['sites']) or not d['sites'] and bool(d['analysisReasons'])
        require((d['modelScope'] == 'STRUCTURAL_AIR_OCCURRENCES') == structural, 'structural/graph scope mismatch')
    expected = [dict(caller=s['caller'], entry=s['entry'], site=s['operation'], candidate=c, openSite=s['effectiveUnknownRemainder'])
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
