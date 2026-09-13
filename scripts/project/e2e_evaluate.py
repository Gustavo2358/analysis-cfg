#!/usr/bin/env python3
"""Local EVALUATE SP→AIR→CFG→dependency oracles, using the existing fixed point."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import source_spans

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'analysis-adapters/src/test/resources/cp6/evaluate'
ABC = {'PROGA', 'PROGB', 'PROGC'}
EXPECTED = {
    'e1': [(ABC, False)], 'e2': [({'OLDPROG', 'PROGA'}, False)],
    'e3': [({'PROGA'}, False), ({'PROGB'}, False), ({'PROGC'}, False)],
    'e4': [(ABC, False)], 'e5': [(ABC, False), (ABC, False)],
    'strong': [({'NEW2', 'OTHER'}, False)], 'closed': [({'PROGA', 'PROGB'}, True)],
    # The MOVE after DISPLAY strongly overwrites any opaque data effect.
    'unknown': [(ABC, False)], 'partial-body': [(ABC, False)],
    'three-arms': [(ABC | {'PROGD'}, False)],
    'also': [({'PROGA'}, False), ({'PROGC'}, False)],
    'empty': [({'PROGC'}, True)], 'perform': [({'PROGA', 'PROGC'}, False)],
    'goback': [({'PROGB', 'PROGC'}, False)],
    **{'compose-' + str(n): [(ABC, False)] * n for n in (1, 2, 5, 40)},
}


def oracle(name, source, sp, air, result):
    require(sp['contractVersion'] in ('2.0.0', '2.1.0','2.2.0', '2.3.0', '2.4.0', '2.5.0', '2.6.0', '2.7.0','2.8.0'), 'SP2 typed family')
    publication = air['publication']
    ops = {op['header']['id']['localId']: op for seq in publication['units'][0]['sequences']
           for op in seq['instructions'] + [seq['terminator']]}
    links = {s['header']['id']: [o['localId'] for item in publication['coverage']['items']
             if item['sourceKey'].endswith('/' + s['header']['id'])
             for o in item['outputs'] if o['domain'] == 'operation'] for s in sp['statements']}
    require(all(links.values()), 'every observed statement has AIR representation')
    calls = sorted((s for s in sp['statements'] if s['variant'] == 'CALL'), key=lambda s: s['header']['programPoint'])
    sites_by_op = {s['operation']['localId']: s for s in result['sites']}
    require(len(calls) == len(sites_by_op) == len(EXPECTED[name]), 'distinct dependency site per CALL')
    sites = [sites_by_op[links[c['header']['id']][0]] for c in calls]
    for site, (expected, remainder) in zip(sites, EXPECTED[name]):
        require({c['referenceName'] for c in site['candidates']} == expected, name + ': candidate join')
        require(site['modelValueRemainder'] is remainder, name + ': model remainder')
        for candidate in site['candidates']:
            for support in candidate['supports']:
                producer = ops[support['producer']['localId']]
                if support['kind'] == 'VALUE_PRODUCER':
                    require(producer['kind'] == 'assign', 'support remains the original MOVE Assign')
                    require(producer['value']['value']['value'] == candidate['rawValue'], 'candidate belongs to its own MOVE')
                else:
                    require(producer['header']['id'] == site['operation'], 'literal CALL support belongs to its own site')
                source_spans(result, support, source)
    evaluates = [s for s in sp['statements'] if s['variant'] == 'EVALUATE']
    if name not in ('empty', 'also', 'partial-body'):
        require(all(op['kind'] != 'opaque' for op in ops.values()), name + ': precise control needs no Opaque')
    for e in evaluates:
        if name == 'empty':
            require(ops[links[e['header']['id']][0]]['kind'] == 'opaque', 'empty entry remains unknown')
        else:
            require(len(links[e['header']['id']]) == len(e['arms']), 'one branch per semantic WHEN')
    return [{'candidates': sorted(c['referenceName'] for c in s['candidates']),
             **{r: s[r] for r in ('modelValueRemainder', 'sourceValueRemainder',
                'interpretationUnknownRemainder', 'effectiveUnknownRemainder', 'openControlRemainder')}} for s in sites]


def control_oracle(air, cfg):
    labels = {n['label']['localId']: n['id']['ordinal'] for n in cfg['nodes'] if n['kind'] == 'SEQUENCE'}
    edges = {(e['from']['ordinal'], e['kind'], e['to']['ordinal']) for e in cfg['transitions']}
    for unit in air['publication']['units']:
        for sequence in unit['sequences']:
            op = sequence['terminator']; origin = labels[sequence['label']['localId']]
            destinations = [('BRANCH_TRUE', op['trueDestination']), ('BRANCH_FALSE', op['falseDestination'])] if op['kind'] == 'branch' else [('JUMP', op['destination'])] if op['kind'] == 'jump' else []
            for kind, destination in destinations:
                require((origin, kind, labels[destination['localId']]) in edges, 'CFG preserves every explicit branch/arm completion edge')


def execute(cwd, label, config, stage, args):
    command = ['java', '-Xmx2g', '-cp', os.pathsep.join(config[stage]['classpath']), config[stage]['main'], *map(str, args)]
    with (cwd / (label + '.stdout')).open('wb') as out, (cwd / (label + '.stderr')).open('wb') as err:
        code = subprocess.run(command, cwd=cwd, stdout=out, stderr=err).returncode
    require(code == 0, label + ': see ' + str(cwd / (label + '.stderr')))


def run(work, runtime, names=None):
    require(not os.environ.get('CI'), 'local/on-demand only')
    work.mkdir(parents=True, exist_ok=False)
    config = json.loads(runtime.read_text()); results = {}
    for name in (names or EXPECTED):
        cwd = work / name; cwd.mkdir(); source = cwd / (name + '.cbl'); shutil.copyfile(FIXTURES / source.name, source)
        web = cwd / 'src/main/resources'; web.mkdir(parents=True)
        (web / 'web').symlink_to(Path(config['checkouts']['proleap-poc']) / 'src/main/resources/web', target_is_directory=True)
        snapshots = []
        for attempt in ('a', 'b'):
            out = cwd / attempt; out.mkdir()
            execute(cwd, 'frontend-' + attempt, config, 'frontend', ['--source', source.name, '--copybooks', str(FIXTURES), '--output', str(out / 'sp')])
            sp = out / 'sp/cobol-semantic-product.json'; air = out / 'air.json'; cfg = out / 'cfg.json'; dep = out / 'dependency.json'
            execute(cwd, 'lower-' + attempt, config, 'lower', [sp, air])
            execute(cwd, 'cfg-' + attempt, config, 'cfg', [air, cfg])
            execute(cwd, 'dependency-' + attempt, config, 'dependency', [air, dep])
            verify_cfg_wire(cfg.read_bytes())
            control_oracle(json.loads(air.read_text()), json.loads(cfg.read_text()))
            results[name] = oracle(name, source, json.loads(sp.read_text()), json.loads(air.read_text()), read(dep))
            snapshots.append([p.read_bytes() for p in (sp, air, cfg, dep)])
        require(snapshots[0] == snapshots[1], name + ': SP/AIR/CFG/dependency byte deterministic')
        if name in ('e1', 'e5', 'compose-40'):
            permuted = json.loads(snapshots[0][1]); permuted['publication']['units'][0]['sequences'].reverse()
            path = cwd / 'permuted.air.json'; path.write_text(json.dumps(permuted)); dep = cwd / 'permuted.dependency.json'
            execute(cwd, 'permuted', config, 'dependency', [path, dep])
            require(dep.read_bytes() == snapshots[0][3], 'AIR physical sequence order independence')
        print(name + ': PASS ' + str(results[name][:2]), flush=True)
    (work / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--work', type=Path, required=True); parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--fixtures', nargs='+', choices=list(EXPECTED))
    args = parser.parse_args(); run(args.work.resolve(), args.runtime.resolve(), args.fixtures)
