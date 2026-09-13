#!/usr/bin/env python3
"""GO TO first slice: real pipeline, explicit-edge and PossibleValues oracles. Local only."""
import argparse
import json
import os
from pathlib import Path
import shutil

from dependency_wire import read, require
from e2e_evaluate import execute, control_oracle
from e2e_w2d import source_spans
from cfg_wire_contract import verify as verify_cfg_wire

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / 'analysis-adapters/src/test/resources/cp6/goto'
EXPECTED = {'g1': [{'PROGA'}], 'g2': [{'PROGA', 'PROGB'}], 'g3': [{'PROGA'}, {'PROGB'}, {'PROGC'}],
    'g4': [{'PROGA', 'PROGB'}], 'g5': [{'NEWPROG'}], 'backward': [{'PROGA'}], 'cycle': [{'PROGA'}],
    'if-jump': [{'PROGA', 'OTHER'}], 'evaluate-jump': [{'PROGA', 'OTHER'}], 'qualified': [{'PROGA'}],
    'empty': [{'PROGA'}], 'unknown': [{'PROGA'}], 'depending': [{'PROGA'}], 'alter': [{'PROGA'}],
    'section': [{'PROGA'}], 'ambiguous': [{'PROGA'}], 'perform-disjoint': [{'PROGA'}],
    'perform-adjacent': [{'PROGA'}], 'perform-overlap': [],
    **{'compose-' + str(n): [{'PROGA'}] * n for n in (1, 2, 5, 40)}}


def oracle(name, source, sp, air, cfg, result):
    require(sp['contractVersion'] == '2.1.0', 'GO TO requires SP2.1')
    publication = air['publication']; sequences = publication['units'][0]['sequences']
    ops = {o['header']['id']['localId']: o for s in sequences for o in s['instructions'] + [s['terminator']]}
    links = {f['header']['id']: [o['localId'] for item in publication['coverage']['items']
        if item['sourceKey'].endswith('/' + f['header']['id']) for o in item['outputs'] if o['domain'] == 'operation'] for f in sp['statements']}
    require(all(links.values()), 'every source occurrence retained')
    label_by_op = {s['terminator']['header']['id']['localId']: s['label']['localId'] for s in sequences}
    for s in sequences:
        for o in s['instructions']: label_by_op[o['header']['id']['localId']] = s['label']['localId']
    nodes = {n['label']['localId']: n['id']['ordinal'] for n in cfg['nodes'] if n['kind'] == 'SEQUENCE'}
    for g in (s for s in sp['statements'] if s['variant'] == 'GO_TO'):
        derived = links[g['header']['id']]; require(len(derived) == 1, 'one transfer per GO TO')
        op = ops[derived[0]]
        if not g['gapCodes']:
            target = label_by_op[links[g['targetEntry']][0]]
            require(op['kind'] == 'jump' and op['destination']['localId'] == target, 'Jump uses only published executable entry')
            node = nodes[label_by_op[derived[0]]]
            edges = [e for e in cfg['transitions'] if e['from']['ordinal'] == node]
            require(len(edges) == 1 and edges[0]['kind'] == 'JUMP' and edges[0]['to']['ordinal'] == nodes[target], 'sole CFG successor; no false fallthrough')
        else:
            require(op['kind'] == 'opaque' and not op['envelope']['control']['known'], 'partial GO TO has no fabricated continuation')
    calls = sorted((s for s in sp['statements'] if s['variant'] == 'CALL'), key=lambda s: s['header']['programPoint'])
    by_op = {s['operation']['localId']: s for s in result['sites']}
    require(len(calls) == len(by_op), 'CALL identities remain separate')
    sites = [by_op[links[c['header']['id']][0]] for c in calls]
    if EXPECTED[name] is not None:
        require(len(sites) == len(EXPECTED[name]), 'expected site count')
        for site, expected in zip(sites, EXPECTED[name]):
            require({c['referenceName'] for c in site['candidates']} == expected, name + ': exact candidate set')
    for site in sites:
        for candidate in site['candidates']:
            require(candidate['supports'], 'source-derived support retained')
            for support in candidate['supports']:
                producer = ops[support['producer']['localId']]
                if support['kind'] == 'VALUE_PRODUCER':
                    require(producer['kind'] == 'assign', 'GO TO cannot become value producer')
                    require(producer['value']['value']['value'] == candidate['rawValue'], 'support names the actual literal MOVE')
                else: require(producer['header']['id'] == site['operation'], 'literal support belongs to its CALL')
                source_spans(result, support, source)
    return [{'candidates': sorted(c['referenceName'] for c in s['candidates']), **{r: s[r] for r in
        ('modelValueRemainder','sourceValueRemainder','interpretationUnknownRemainder','effectiveUnknownRemainder','openControlRemainder')}} for s in sites]


def reverse_fields(value):
    if isinstance(value, dict): return {k: reverse_fields(v) for k, v in reversed(list(value.items()))}
    if isinstance(value, list): return [reverse_fields(v) for v in value]
    return value


def run(work, runtime, names=None):
    require(not os.environ.get('CI'), 'local only'); work.mkdir(parents=True, exist_ok=False)
    config = json.loads(runtime.read_text()); results = {}
    for name in names or EXPECTED:
        cwd = work / name; cwd.mkdir(); source = cwd / (name + '.cbl'); shutil.copyfile(FIXTURES / source.name, source)
        web = cwd / 'src/main/resources'; web.mkdir(parents=True)
        (web / 'web').symlink_to(Path(config['checkouts']['proleap-poc']) / 'src/main/resources/web', target_is_directory=True)
        snapshots = []
        for attempt in ('a', 'b'):
            out = cwd / attempt; out.mkdir()
            execute(cwd, 'frontend-' + attempt, config, 'frontend', ['--source', source.name, '--copybooks', FIXTURES, '--output', out / 'sp'])
            sp = out / 'sp/cobol-semantic-product.json'; air = out / 'air.json'; cfg = out / 'cfg.json'; dep = out / 'dependency.json'
            for stage, paths in (('lower', [sp, air]), ('cfg', [air, cfg]), ('dependency', [air, dep])): execute(cwd, stage + '-' + attempt, config, stage, paths)
            verify_cfg_wire(cfg.read_bytes()); control_oracle(json.loads(air.read_text()), json.loads(cfg.read_text()))
            results[name] = oracle(name, source, json.loads(sp.read_text()), json.loads(air.read_text()), json.loads(cfg.read_text()), read(dep))
            snapshots.append([p.read_bytes() for p in (sp, air, cfg, dep)])
        require(snapshots[0] == snapshots[1], name + ': SP/AIR/CFG/dependency A/B byte deterministic')
        if name in ('g1', 'backward', 'if-jump', 'evaluate-jump', 'compose-40'):
            sp = json.loads(snapshots[0][0]); sp['statements'].reverse()
            path = cwd / 'permuted.sp.json'; path.write_text(json.dumps(reverse_fields(sp))); air = cwd / 'permuted-lower.air.json'
            execute(cwd, 'permuted-sp', config, 'lower', [path, air]); require(air.read_bytes() == snapshots[0][1], 'statement inventory and field order independence')
            model = json.loads(snapshots[0][1]); model['publication']['units'][0]['sequences'].reverse()
            air = cwd / 'permuted.air.json'; air.write_text(json.dumps(reverse_fields(model))); cfg = cwd / 'permuted.cfg.json'; dep = cwd / 'permuted.dependency.json'
            execute(cwd, 'permuted-cfg', config, 'cfg', [air, cfg]); execute(cwd, 'permuted-dependency', config, 'dependency', [air, dep])
            require(cfg.read_bytes() == snapshots[0][2] and dep.read_bytes() == snapshots[0][3], 'AIR sequence order independence')
        print(name + ': PASS ' + str(results[name][:2]), flush=True)
    (work / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--work', type=Path, required=True); parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--fixtures', nargs='+', choices=list(EXPECTED)); args = parser.parse_args()
    run(args.work.resolve(), args.runtime.resolve(), args.fixtures)
