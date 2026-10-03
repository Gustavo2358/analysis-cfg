#!/usr/bin/env python3
"""Independent oracle for CFG wire version/token compatibility, not an AIR or CFG reader."""
import json
import re

TERMINATORS = {'1.0.0': {'JUMP', 'BRANCH', 'RETURN', 'HALT'},
               '2.0.0': {'JUMP', 'BRANCH', 'RETURN', 'HALT', 'INVOKE'}}
TRANSITIONS = {'1.0.0': {'ENTRY', 'JUMP', 'BRANCH_TRUE', 'BRANCH_FALSE', 'RETURN', 'HALT'},
               '2.0.0': {'ENTRY', 'JUMP', 'BRANCH_TRUE', 'BRANCH_FALSE', 'RETURN', 'HALT', 'INVOKE_NORMAL'}}

TERMINATORS['3.0.0'] = TERMINATORS['2.0.0'] | {'OPAQUE'}
TRANSITIONS['3.0.0'] = TRANSITIONS['2.0.0'] | {'OPAQUE_JUMP', 'OPAQUE_RETURN'}
TERMINATORS['4.0.0'] = TERMINATORS['3.0.0']
TRANSITIONS['4.0.0'] = TRANSITIONS['3.0.0'] | {'EXCEPTION', 'CONTROL_EXIT'}
LOCAL = {'LOCAL_INVOKE', 'LOCAL_BOUNDARY', 'LOCAL_RESUME', 'LOCAL_UNWIND'}
TERMINATORS['5.0.0'] = TERMINATORS['4.0.0'] | LOCAL
TRANSITIONS['5.0.0'] = TRANSITIONS['4.0.0']
TERMINATORS['6.0.0'] = TERMINATORS['5.0.0']
TRANSITIONS['6.0.0'] = TRANSITIONS['5.0.0']
TERMINATORS['7.0.0'] = TERMINATORS['6.0.0']
TRANSITIONS['7.0.0'] = TRANSITIONS['6.0.0']

def verify_local(doc):
    def identity(ref):
        return json.dumps(ref, sort_keys=True, separators=(',', ':'))
    nodes = {identity(n['id']): n for n in doc['nodes']}
    if len(nodes) != len(doc['nodes']): raise ValueError('duplicate CFG node')
    expected = {k: n for k, n in nodes.items() if n.get('terminator', {}).get('kind') in LOCAL}
    rules = doc.get('localControl', [])
    seen = set()
    for rule in rules:
        source = identity(rule['source']); node = expected.get(source)
        if node is None or source in seen: raise ValueError('invalid local rule source')
        seen.add(source)
        if rule['operation'] != node['terminator']['operation'] or rule['kind'] != node['terminator']['kind']:
            raise ValueError('local rule correlation mismatch')
        kind = rule['kind']
        fields = {'LOCAL_INVOKE': {'entry', 'resume', 'ports'}, 'LOCAL_BOUNDARY': {'port', 'defaultDestination'},
                  'LOCAL_RESUME': {'invalidExit'}, 'LOCAL_UNWIND': {'count', 'destination', 'invalidExit'}}[kind]
        if 'reentryGuard' in rule and kind=='LOCAL_INVOKE' and doc['schemaVersion'] in {'6.0.0','7.0.0'}: fields=fields | {'reentryGuard'}
        if doc['schemaVersion']=='7.0.0':
            optional={'LOCAL_INVOKE':{'resumeRoutes'},'LOCAL_RESUME':{'resumeKey'},'LOCAL_UNWIND':{'all'}}.get(kind,set())
            fields=fields | (optional & set(rule))
        if set(rule) != fields | {'source', 'operation', 'kind'}: raise ValueError('local rule fields')
        if 'reentryGuard' in fields:
            guard=rule['reentryGuard']
            if not isinstance(guard,dict) or set(guard)!={'activationKey','destination'}:raise ValueError('invalid reentry guard')
            if not isinstance(guard['activationKey'],str) or not guard['activationKey'].strip():raise ValueError('invalid activation key')
            target=nodes.get(identity(guard['destination']))
            if target is None or target.get('kind')!='SEQUENCE':raise ValueError('missing guard target')
            op=target['terminator']['operation']
            if any(op.get(k)!=rule['operation'].get(k) for k in ('publication','unit')):raise ValueError('cross-owner guard target')
        if 'resumeRoutes' in fields:
            routes=rule['resumeRoutes'];keys=set()
            if not isinstance(routes,list) or not routes:raise ValueError('invalid resume routes')
            for route in routes:
                if not isinstance(route,dict) or set(route)!={'key','destination'}:raise ValueError('invalid resume route')
                name=route['key']
                if not isinstance(name,str) or not name.strip() or name in keys:raise ValueError('duplicate/empty resume key')
                keys.add(name);target=nodes.get(identity(route['destination']))
                if target is None or target.get('kind')!='SEQUENCE':raise ValueError('missing resume route target')
                op=target['terminator']['operation']
                if any(op.get(k)!=rule['operation'].get(k) for k in ('publication','unit')):raise ValueError('cross-owner resume route')
        if 'resumeKey' in fields and (not isinstance(rule['resumeKey'],str) or not rule['resumeKey'].strip()):raise ValueError('invalid resume key')
        if 'all' in fields and (rule['all'] is not True or rule['count']!='0'):raise ValueError('invalid unwind all')
        targets = fields - {'ports', 'port', 'count', 'reentryGuard', 'resumeRoutes', 'resumeKey', 'all'}
        for field in targets:
            target = nodes.get(identity(rule[field]))
            if target is None: raise ValueError('missing local rule target')
            if field == 'invalidExit':
                tag = 'invalid_local_return' if kind == 'LOCAL_RESUME' else 'invalid_local_unwind'
                if target.get('kind') != 'OUTCOME_EXIT' or target.get('tag') != tag or target.get('operation') != rule['operation']:
                    raise ValueError('invalid local exception target')
            elif target.get('kind') != 'SEQUENCE': raise ValueError('invalid local sequence target')
        if kind == 'LOCAL_UNWIND' and (not isinstance(rule['count'], str) or not re.fullmatch(r'0|[1-9][0-9]*', rule['count'])):
            raise ValueError('invalid local unwind count')
        ports = rule.get('ports', [rule['port']] if 'port' in rule else [])
        if not isinstance(ports, list) or len({identity(p) for p in ports}) != len(ports): raise ValueError('invalid local ports')
        if any(p.get('publication') != rule['operation'].get('publication') or p.get('unit') != rule['operation'].get('unit') for p in ports):
            raise ValueError('cross-owner local port')
    if seen != set(expected): raise ValueError('missing local rules')
    if any(identity(e['from']) in seen for e in doc['transitions']): raise ValueError('unconditional local transition')

def pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError('duplicate CFG key: ' + key)
        result[key] = value
    return result


def verify(data):
    doc = json.loads(data, object_pairs_hook=pairs)
    version = doc['schemaVersion']
    if doc['schema'] != 'analysis-cfg-json' or version not in TERMINATORS:
        raise ValueError('unknown CFG schema/version')
    terminators = {n['terminator']['kind'] for n in doc['nodes'] if n['kind'] == 'SEQUENCE'}
    transitions = {t['kind'] for t in doc['transitions']}
    if not terminators <= TERMINATORS[version] or not transitions <= TRANSITIONS[version]:
        raise ValueError('CFG tokens incompatible with declared version')
    outside = [n for n in doc['nodes'] if n['kind'] == 'OUTCOME_EXIT']
    if outside and version not in {'4.0.0', '5.0.0', '6.0.0', '7.0.0'}:
        raise ValueError('CFG tokens incompatible with declared version')
    for node in outside:
        if node.get('outcome') not in {'HALT', 'EXCEPTION', 'ANY_EXCEPTION'} or ('tag' in node) != (node.get('outcome') == 'EXCEPTION'):
            raise ValueError('invalid outside outcome')
    if version in {'5.0.0','6.0.0','7.0.0'}:
        verify_local(doc)
    elif 'localControl' in doc:
        raise ValueError('local rules incompatible with declared version')
    minimum = '7.0.0' if any(set(r) & {'resumeRoutes','resumeKey','all'} for r in doc.get('localControl',[])) else '6.0.0' if any('reentryGuard' in r for r in doc.get('localControl',[])) else '5.0.0' if terminators & LOCAL else '4.0.0' if outside or transitions & {'EXCEPTION', 'CONTROL_EXIT'} else '3.0.0' if 'OPAQUE' in terminators or transitions & {'OPAQUE_JUMP', 'OPAQUE_RETURN'} else '2.0.0' if 'INVOKE' in terminators or 'INVOKE_NORMAL' in transitions else '1.0.0'
    if version != minimum:
        raise ValueError('writer did not select the minimum required CFG contract')
    return dict(schemaVersion=version, terminators=sorted(terminators), transitions=sorted(transitions))
