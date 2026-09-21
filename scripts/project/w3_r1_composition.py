#!/usr/bin/env python3
"""SP -> lower AIR -> validated CFG -> value query composition witness.

The independent regions are a test-only AIR extension after lower publication:
they carry no relation to the SP logical target and must not change its effect.
"""
import argparse
import hashlib
import json
import pathlib
import subprocess


def stage(runtime, name, args, out):
    command = runtime['commands'][name] + list(map(str, args))
    result = subprocess.run(command, cwd=runtime['frontendCheckout'], capture_output=True,
                            text=True, timeout=120)
    (out / (name + '.log')).write_text(result.stdout + result.stderr)
    if result.returncode:
        raise AssertionError(f'{name} failed ({result.returncode}); see {name}.log')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', required=True, type=pathlib.Path)
    parser.add_argument('--sp', required=True, type=pathlib.Path)
    parser.add_argument('--out', required=True, type=pathlib.Path)
    parser.add_argument('--independent-regions', type=int, default=32)
    parser.add_argument('--target-name', default='TARGET-PGM')
    parser.add_argument('--expected-candidate', default='PROGA')
    parser.add_argument('--repeat-writes', type=int, default=1)
    args = parser.parse_args()
    runtime = json.loads(args.runtime.read_text())
    args.out.mkdir(parents=True, exist_ok=False)
    raw = args.out / 'lower-air.json'
    stage(runtime, 'lower', [args.sp.resolve(), raw], args.out)
    air = json.loads(raw.read_text())
    publication = air['publication']
    unit = publication['units'][0]
    target = next(o for o in unit['objects'] if o['displayName'] == args.target_name)
    if args.repeat_writes > 1:
        seq = next(s for s in unit['sequences'] if any(op['kind'] == 'assign' for op in s['instructions']))
        original = next(op for op in seq['instructions'] if op['kind'] == 'assign')
        ids = [original['header']['id']['localId'], original['destination']['header']['id']['localId'],
               original['value']['header']['id']['localId']]
        for ordinal in range(1, args.repeat_writes):
            encoded = json.dumps(original)
            for old in ids:
                new = hashlib.sha256(f'w3-r1-repeat-{ordinal}-{old}'.encode()).hexdigest()[:32]
                encoded = encoded.replace(old, new)
            seq['instructions'].append(json.loads(encoded))
    supported_writes = sum(op['kind'] == 'assign' for seq in unit['sequences'] for op in seq['instructions'])
    for ordinal in range(args.independent_regions):
        publication['storage'].append({
            'kind': 'region',
            'header': {'id': {'domain': 'storage', 'localId': f'w3-r1-independent-{ordinal}',
                              'publication': publication['id']['localId']},
                       'owner': unit['id'], 'lifetime': 'PERSISTENT',
                       'visibility': 'PRIVATE', 'origin': unit['origin']},
            'extent': {'kind': 'known', 'value': '8'},
        })
    required = publication['capabilities']['required']
    if args.independent_regions and not any(c['name'] == 'memory.regions' for c in required):
        required.append({'name': 'memory.regions', 'version': '1'})
    augmented = args.out / 'air.json'
    augmented.write_text(json.dumps(air, indent=2) + '\n')
    stage(runtime, 'cfg', [augmented, args.out / 'cfg.json'], args.out)
    stage(runtime, 'dependency', [augmented, args.out / 'dependencies.json'], args.out)
    command = runtime['commands']['dependency'] + [str(augmented), str(args.out / 'dependencies-physical.json'),
                                                    '--experimental-physical']
    result = subprocess.run(command, cwd=runtime['frontendCheckout'], capture_output=True, text=True, timeout=120)
    (args.out / 'dependency-physical.log').write_text(result.stdout + result.stderr)
    if result.returncode:
        raise AssertionError(f'physical query failed ({result.returncode})')
    logical = json.loads((args.out / 'dependencies.json').read_text())
    physical = json.loads((args.out / 'dependencies-physical.json').read_text())
    lm, pm = logical['metrics'], physical['metrics']
    sites = [s for s in logical['sites'] if s['command'] == 'XCTL']
    assert len(sites) == 1
    result = {'binding': target['storage']['kind'], 'independentRegions': args.independent_regions,
              'supportedWrites': supported_writes,
              'logicalTargets': lm['targetsPrepared'], 'physicalTargets': pm['targetsPrepared'],
              'physicalEvents': pm.get('RegionalValues.prepare_eventsPrepared', 0),
              'physicalGroups': pm['physicalGroupsApplied'],
              'candidateNames': [c['referenceName'] for c in sites[0]['candidates']]}
    (args.out / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))
    assert result['binding'] == 'cell', 'supported logical value requires CellBinding'
    assert result['logicalTargets'] <= supported_writes and result['physicalTargets'] <= supported_writes, 'unrelated regions became targets'
    assert result['physicalEvents'] <= supported_writes * 2 and result['physicalGroups'] <= supported_writes, 'unrelated regions became Events'
    assert result['candidateNames'] == [args.expected_candidate], 'supported logical target was lost'


if __name__ == '__main__':
    main()
