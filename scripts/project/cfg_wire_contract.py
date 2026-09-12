#!/usr/bin/env python3
"""Independent oracle for CFG wire version/token compatibility, not an AIR or CFG reader."""
import json

TERMINATORS = {'1.0.0': {'JUMP', 'BRANCH', 'RETURN', 'HALT'},
               '2.0.0': {'JUMP', 'BRANCH', 'RETURN', 'HALT', 'INVOKE'}}
TRANSITIONS = {'1.0.0': {'ENTRY', 'JUMP', 'BRANCH_TRUE', 'BRANCH_FALSE', 'RETURN', 'HALT'},
               '2.0.0': {'ENTRY', 'JUMP', 'BRANCH_TRUE', 'BRANCH_FALSE', 'RETURN', 'HALT', 'INVOKE_NORMAL'}}


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
    minimum = '2.0.0' if 'INVOKE' in terminators or 'INVOKE_NORMAL' in transitions else '1.0.0'
    if version != minimum:
        raise ValueError('writer did not select the minimum required CFG contract')
    return dict(schemaVersion=version, terminators=sorted(terminators), transitions=sorted(transitions))
