"""Independent v1 source certificate / dependency 2.6 oracle (no AIR graph inference)."""
import json
import re
from pathlib import Path
from functools import lru_cache


def require(ok, why):
    if not ok: raise ValueError(why)


def key(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'))


@lru_cache(maxsize=3)
def schema(version="1.0.0"):
    return json.loads((Path(__file__).resolve().parents[2] / f'docs/contracts/qualified-source-dependencies-{version}.schema.json').read_text())


def shape(value, spec, version="1.0.0"):
    if 'oneOf' in spec:
        matches=0
        for alternative in spec['oneOf']:
            try: shape(value,alternative,version);matches+=1
            except (ValueError,KeyError,TypeError): pass
        require(matches==1,'closed source authority variant');return
    if '$ref' in spec: return shape(value, schema(version)['$defs'][spec['$ref'].rsplit('/', 1)[1]],version)
    if 'const' in spec: require(type(value) is str and value == spec['const'], 'contract version/constant'); return
    if 'enum' in spec: require(value in spec['enum'], 'source enum')
    kind=spec['type']
    require(type(value) is {'object':dict,'array':list,'string':str,'integer':int,'boolean':bool}[kind], 'wire type')
    if kind=='object':
        require(set(spec.get('required', spec['properties'])) <= set(value) <= set(spec['properties']), 'closed source fields')
        for name, item in value.items(): shape(item, spec['properties'][name],version)
    elif kind=='array':
        require(spec.get('minItems',0)<=len(value)<=spec.get('maxItems',len(value)), 'source array cardinality')
        for item in value: shape(item, spec['items'],version)
    elif kind=='string':
        require(not any(0xD800<=ord(c)<=0xDFFF for c in value), 'Unicode scalar')
        require(len(value)>=spec.get('minLength',0), 'source text length')
    elif kind=='integer' and 'minimum' in spec: require(value>=spec['minimum'], 'source integer minimum')


def index(values, identity='id'):
    result={key(v[identity]):v for v in values}
    require(len(result)==len(values), 'duplicate identity')
    return result


def refs(values, inventory):
    require(len(set(map(key,values)))==len(values) and all(key(v) in inventory for v in values), 'dangling/duplicate reference')


def validate(evidence):
    require(evidence.get('version') in ('1.0.0','1.1.0','1.2.0','1.3.0','1.4.0'), 'source version')
    shape(evidence, {'$ref':'#/$defs/QualifiedSourceDependencies'},evidence['version'])
    require(len(evidence['air'])<=1 and all(re.fullmatch('[0-9a-f]{64}', d['sha256']) for d in [evidence['source']]+evidence['air']), 'digest')
    require(evidence['source']['schema'] in ('cobol-semantic-product','cobol-semantic-compilation'), 'source schema')
    require(len({key(u['unit']) for u in evidence['units']})==len(evidence['units']), 'duplicate source unit')
    for u in evidence['units']:
        unit=u['unit']; require(bool(unit['compilationUnitId']) and bool(unit['structuralPath']) and all(n>=0 for n in unit['structuralPath']), 'unit identity')
        ss=index(u['statements']); ns=index(u['nodes']); ps=index(u['proofs']); es=index(u['events']); gs=index(u['guards']); sels=index(u['selections']); ts=index(u['targets']); index(u['derivations']); index(u['occurrences'])
        for s in u['statements']: require(s['id']['unit']==unit and bool(s['id']['handle']), 'statement identity')
        for p in u['proofs']:
            refs(p['dependencies'],ps)
            require(p['kind'] in {'LOCAL_GRAMMAR','RESOLVED_TARGET','EXPANDED_INCLUDE','INPUT_REGION_ISOLATION','PARTIAL_UNKNOWN'} | ({'CONTROL_POSSIBILITY'} if evidence['version'] in ('1.1.0','1.2.0','1.3.0','1.4.0') else set()), 'proof kind/version')
        pending_proofs=list(u['proofs']); grounded_proofs=set()
        while pending_proofs:
            ready=[p for p in pending_proofs if set(p['dependencies'])<=grounded_proofs]
            require(bool(ready),'cyclic proof authority')
            ready_ids={p['id'] for p in ready};grounded_proofs.update(ready_ids);pending_proofs=[p for p in pending_proofs if p['id'] not in ready_ids]
        for g in u['guards']: refs([g['event']],es);require(g['kind'] in ('CONDITION_RAISED','DEFAULT_DISPOSITION_APPLIES'), 'guard kind')
        for t in u['targets']:
            refs(t['entry'],ss); require(len(t['entry'])<=1 and bool(t['registrations']), 'target registration')
            for r in t['registrations']: refs([r['statement']],ss); require(r['statementOrigin']==ss[key(r['statement'])]['provenance'], 'registration origin')
        def support(s):
            causes={'NONE','RESET_HISTORY_UNAVAILABLE','RESET_WITHOUT_CANCELED_EVIDENCE','CALL_EFFECT_UNAVAILABLE','HANDLER_OPERATION_UNAVAILABLE'}
            if evidence['version'] in ('1.2.0','1.3.0','1.4.0'):causes.add('SOURCE_REENTRY_UNDEFINED')
            require(s['cause'] in causes and (s['kind']=='UNKNOWN') == (s['cause']!='NONE'),'support cause/version')
            refs(s['target'],ts);refs(s['activation'],ss)
            require(len(s['target'])==len(s['activation'])<=1, 'state target/activation')
            require((s['kind'] in ('ACTIVE','CANCELED','DEACTIVATED'))==bool(s['target']), 'state target')
            if s['target']: require(any(r['statement']==s['activation'][0] for r in ts[key(s['target'][0])]['registrations']), 'activation authority')
        for n in u['nodes']: support(n['support'])
        for e in u['events']:
            refs([e['statement']],ss);refs(e['proofs'],ps);refs(e['guards'],gs)
            require(e['origin'] in ('EXPLICIT_ABEND','XCTL_PGMIDERR'), 'event origin')
            kinds=[gs[key(g)]['kind'] for g in e['guards']]
            require(all(gs[key(g)]['event']==e['id'] for g in e['guards']), 'guard owner')
            require(kinds==([] if e['origin']=='EXPLICIT_ABEND' else ['CONDITION_RAISED','DEFAULT_DISPOSITION_APPLIES']), 'event guards')
        for s in u['selections']:
            refs([s['source']],ns);refs([s['event']],es);refs(s['target'],ts);refs(s['localEntry'],ns)
            e=es[key(s['event'])];source=ns[key(s['source'])]
            require(e['statement']['handle']==source['location'] and s['guards']==e['guards'] and s['proofs']==e['proofs'], 'selection qualification')
            if s['target']:
                require(source['support']['kind']=='ACTIVE' and source['support']['target']==s['target'], 'ACTIVE selection')
                require(len(s['stateOnEntry'])==1, 'entry support');entry=s['stateOnEntry'][0];support(entry)
                require(entry['kind']=='DEACTIVATED' and entry['activation']==source['support']['activation'] and entry['target']==s['target'], 'deactivation')
                if s['localEntry']: require(ns[key(s['localEntry'][0])]['support']==entry, 'entry node support')
            else: require(not s['localEntry'] and not s['stateOnEntry'], 'inactive has no entry')
        reached=set();pending=list(u['derivations'])
        for d in pending:
            refs(d['source'],ns);refs(d['callerPremise'],ns);refs([d['destination']],ns);refs(d['proofs'],ps);refs(d['selection'],sels)
            if d['selection']:
                require(len(d['selection'])==1, 'selection alternatives');s=sels[key(d['selection'][0])]
                require(d['source']==[s['source']] and d['destination'] in s['localEntry'] and d['proofs']==s['proofs'] and not d['callerPremise'], 'selection derivation')
            if not d['source']: require(d['authority']=='PRIMARY_ENTRY' and not d['selection'] and not d['callerPremise'], 'root authority')
        while pending:
            ready=[d for d in pending if set(d['source']+d['callerPremise'])<=reached]
            if not ready: break
            reached.update(d['destination'] for d in ready);ready_ids={d['id'] for d in ready};pending=[d for d in pending if d['id'] not in ready_ids]
        require(reached=={n['id'] for n in u['nodes']}, 'grounded source nodes')
        for o in u['occurrences']:
            refs([o['id']],ss);refs(o['qualifications'],ns)
            require(set(o['qualifications'])=={n['id'] for n in u['nodes'] if n['location']==o['id']['handle']}, 'complete occurrence alternatives')
            require(all(p['id']['statement']==o['id'] and p['id']['handle'] for p in o['operands']), 'operand identity')
            require(len(o['values'])<=1 and len(o['operands'])<=1 and o['valueRemainder']==(not o['values']), 'value remainder')
            if o['values']: require(o['targetKind']=='LITERAL' and bool(o['operands']), 'literal value authority')
            for v in o['values']: require(v['logicalDomain']=='TEXT' and len(v['value'])==v['logicalExtent'], 'logical value')
        file_uses=set()
        for f in u.get('nativeFiles',[]):
            refs([f['statement']],ss);refs(f['qualifications'],ns)
            ident=(key(f['statement']),f['ordinal']);require(ident not in file_uses,'duplicate native file use');file_uses.add(ident)
            require(f['statement']['unit']==unit and f['command'] in {'OPEN','READ','WRITE','REWRITE','DELETE_RECORD','START','CLOSE','RELEASE','RETURN','SORT','MERGE'},'native file owner/command')
            require(set(f['qualifications'])=={n['id'] for n in u['nodes'] if n['location']==f['controlLocation']},'native file control point alternatives')
            require(len(f['names'])<=1 and (not f['local'] or not f['names']),'native file binding/locality')
            require(all(n['declarationOrigins'] for n in f['names']),'native file declaration provenance')
        if 'nominalValues' in u: nominal(u)
        if not u['controlAvailable']: require(not u['nodes'] and not u['derivations'] and not u['selections'], 'no unavailable authority')
    return evidence


def nominal(unit):
    value=unit['nominalValues']; facts=value['facts']
    symbols=index(facts['symbols'],'node'); declarations=index(value['declarations'],'node')
    require(set(symbols)==set(declarations), 'nominal declaration provenance')
    statements={s['id']['handle']:s for s in unit['statements']}
    def term(t):
        if t.get('arguments'):
            require(facts['authority'] in ('NOMINAL_TEXT_SOURCE_V3','NOMINAL_TEXT_SOURCE_V4'), 'source expression authority')
            require(len(t['arguments'])>=2 if t['kind']=='CHOICE' else len(t['arguments'])==1, 'source expression arity')
            require(t['kind']!='CHOICE' or facts['authority']=='NOMINAL_TEXT_SOURCE_V4','choice authority')
            for argument in t['arguments']:term(argument)
        if t['kind']=='READ': require(key(t['value']) in symbols, 'nominal read reference')
        elif t['kind']!='LITERAL': require(t['value']=='', 'nonliteral payload')
    def predicate(p):
        kind=p['kind'];terms=p['terms'];children=p['children']
        require((kind=='EQ' and len(terms)==2 and not children) or (kind=='NOT' and not terms and len(children)==1) or (kind in ('AND','OR') and not terms and len(children)>=2), 'nominal predicate shape')
        for t in terms: term(t)
        for child in children: predicate(child)
    index(facts.get('tableFields',[]),'node')
    for field in facts.get('tableFields',[]):
        require(key(field['node']) in symbols,'table field owner')
        for seed in field['initial']: require(key(seed['origin']) in symbols,'table initializer origin')
    seen=set()
    for a in facts['assignments']:
        ident=(a['statement'],a['target']);require(ident not in seen, 'duplicate nominal write');seen.add(ident)
        require(a['statement'] in statements and key(a['target']) in symbols, 'nominal assignment owner');term(a['source'])
    index(facts['conditions'],'statement')
    for c in facts['conditions']:
        require(c['statement'] in statements, 'nominal condition owner');predicate(c['predicate'])
    for seed in value['seeds']: require(key(seed['node']) in symbols, 'nominal seed owner')
    index(value['seeds'],'node');index(value['branches'],'derivation');index(value['uncertainties'])
    nodes={n['id']:n for n in unit['nodes']};derivations={d['id']:d for d in unit['derivations']};predicates={c['statement'] for c in facts['conditions']}
    for b in value['branches']:
        d=derivations.get(b['derivation']);require(d is not None and len(d['source'])==1 and not d['selection'] and not d['callerPremise'], 'nominal branch derivation')
        require(nodes[d['source'][0]]['location'] in predicates, 'nominal branch predicate owner')
    occurrences={o['id']['handle']:o for o in unit['occurrences']};index(facts['queries'],'statement')
    for q in facts['queries']: require(key(q['node']) in symbols and q['statement'] in occurrences and occurrences[q['statement']]['targetKind']=='COMPUTED', 'nominal query owner')


def assumed_only(unit):
    # Independent least fixed point: alternatives are OR; source/caller premises are AND.
    assumed={p['id'] for p in unit['proofs'] if p['kind']=='CONTROL_POSSIBILITY'}
    if not assumed:return set()
    while True:
        expanded=assumed | {p['id'] for p in unit['proofs'] if set(p['dependencies']) & assumed}
        if expanded==assumed:break
        assumed=expanded
    ground=set()
    while True:
        expanded=ground | {d['destination'] for d in unit['derivations'] if not set(d['proofs']) & assumed and set(d['source']+d['callerPremise'])<=ground}
        if expanded==ground:break
        ground=expanded
    return {n['id'] for n in unit['nodes']}-ground


def control_affected(unit):
    hypothetical={p['id'] for p in unit['proofs'] if p['kind']=='CONTROL_POSSIBILITY'}
    while True:
        expanded=hypothetical|{p['id'] for p in unit['proofs'] if set(p['dependencies'])&hypothetical}
        if expanded==hypothetical:break
        hypothetical=expanded
    affected=set()
    while True:
        expanded=affected|{d['destination'] for d in unit['derivations'] if set(d['proofs'])&hypothetical or set(d['source']+d['callerPremise'])&affected}
        if expanded==affected:return affected
        affected=expanded


def status(unit, qualifications, assumptions=None):
    if assumptions is None: assumptions=control_affected(unit)
    return 'CONTROL_UNAVAILABLE' if not unit['controlAvailable'] else 'NOT_QUALIFIED_IN_SOURCE_MODEL' if not qualifications else 'POSSIBLE_UNDER_UNKNOWN_CONTROL' if set(qualifications)&assumptions else 'QUALIFIED_POSSIBLE'


def publication(section, publication_id):
    require(set(section)=={'analysisBoundary','evidence','occurrences'} and section['analysisBoundary']=='NON_EXECUTABLE_SOURCE', 'source publication boundary')
    e=validate(section['evidence']);require(len(e['air'])==1 and e['air'][0]['publication']==publication_id, 'AIR correlation')
    assumptions={key(u['unit']):control_affected(u) for u in e['units']}
    source={key(o['id']):(u,o) for u in e['units'] for o in u['occurrences']}
    require(len(source)==len(section['occurrences']), 'occurrence preservation')
    seen=set()
    for o in section['occurrences']:
        require(set(o)=={'occurrence','status','valueRemainder','interpretationRemainder','candidates'}, 'source occurrence result')
        k=key(o['occurrence']);require(k in source and k not in seen, 'result occurrence identity');seen.add(k);u,s=source[k]
        state=status(u,s['qualifications'],assumptions[key(u['unit'])])
        require(o['status']==state and o['valueRemainder']==s['valueRemainder'], 'source status/value remainder')
        for c in o['candidates']:
            require(set(c)=={'referenceName','rawValue','occurrence','qualifications'}, 'candidate fields')
            require(state in ('QUALIFIED_POSSIBLE','POSSIBLE_UNDER_UNKNOWN_CONTROL') and c['occurrence']==o['occurrence'] and c['qualifications']==s['qualifications'], 'candidate qualification')
            require(c['rawValue'] in [v['value'] for v in s['values']], 'candidate value authority')
            require(re.fullmatch(r'[A-Z_$][A-Z0-9_@#$]{0,7}' if s['technology']=='COBOL' else r'[A-Z0-9$@#]{1,8}',c['referenceName']) is not None, 'candidate name')
