#!/usr/bin/env python3
"""Local real-source partial/compositional qualification at immutable producer pins."""
import argparse
import copy
import json
import os
from pathlib import Path
import shutil
import sys

from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import locked_sp, open_call_model, execute, runtime
from prepare_w2d_producers import ROOT, git, require_local

FIXTURES = ROOT / 'analysis-adapters/src/test/resources/cp6/partial-program'
# Hand-written known candidates; computed CALL remainders follow the explicit AIR bounds.
EXPECTED = {
    'display-handler': [{'BEFORE'}, set()],
    'control-body': [{'AFTER'}, {'INNER'}],
    'p1': [{'PROGA'}],
    'p2': [{'PROGA'}, {'PROGB'}],
    'p3': [{'PROGA'}],
    'p4': [{'PROGA'}],
    'must-write': [{'PROGA'}],
    'read': [{'PROGA'}],
    'call-using': [{'PROGA'}],
    'call-returning': [{'PROGA'}],
    'perform-distinct': [{'PROGA'}, {'PROGB'}],
    'perform-repeated': [{'PROGA'}, {'PROGA'}],
    'if-arm': [{'PROGA', 'PROGB'}],
    'if-nested': [{'PROGA', 'PROGB', 'PROGC'}],
    'mixed-data': [{'PROGA'}, {'PROGB'}],
    'entry-using': [{'PROGA'}],
    'stress': [{'PROGA'}] * 20,
}


def source_calls(sp):
    return sorted((s for s in sp['statements'] if s['variant'] == 'CALL'), key=lambda s: s['header']['programPoint'])


def oracle(name, sp, air, result):
    locked_sp(sp)
    p = air['publication']; unit = p['units'][0]
    operations = {op['header']['id']['localId']: op for seq in unit['sequences'] for op in seq['instructions'] + [seq['terminator']]}
    links = {}
    for fact in sp['statements']:
        ident = fact['header']['id']
        links[ident] = [o for i in p['coverage']['items'] if i['sourceKey'].endswith('/' + ident)
                       for o in i['outputs'] if o['domain'] == 'operation']
        require(links[ident], 'no silent source elision: ' + ident)
    by_op = {s['operation']['localId']: s for s in result['sites']}
    calls = source_calls(sp)
    # A contextual PERFORM may publish an inactive lexical shadow alongside the
    # executable CALL. Require every published site to trace to source and prove
    # that exactly one site per lexical CALL is reachable in these fixtures.
    sites = []; linked = set(); inactive = []
    for call_index, call in enumerate(calls):
        ids = {o['localId'] for o in links[call['header']['id']]}
        require(ids and ids <= by_op.keys(), 'every CALL output has a dependency site')
        linked.update(ids)
        active = [by_op[i] for i in ids if by_op[i]['reachability'] == 'REACHABLE']
        if name == 'display-handler' and call_index == 1:
            require(not active and len(ids) == 1, 'post-DISPLAY CALL is behind an unproved handler completion')
            sites.append(by_op[next(iter(ids))])
        else:
            require(len(active) == 1, 'one reachable site per source CALL')
            sites.extend(active)
        for i in ids:
            site = by_op[i]
            if site['reachability'] != 'REACHABLE':
                require(site['reachability'] == 'UNREACHABLE_IN_MODEL' and not site['candidates'],
                        'inactive lexical shadow has no invented candidate')
                inactive.append(site)
    require(linked == by_op.keys(), 'no dependency site without a source CALL')
    if name == 'display-handler':
        observed = [s for s in sp['statements'] if s['variant'] == 'OBSERVED']
        require(len(observed) == 1 and observed[0]['normalContinuation']['availability'] == 'UNAVAILABLE',
                'DISPLAY handler completion is not published')
        boundary = operations[links[observed[0]['header']['id']][0]['localId']]
        require(boundary['kind'] == 'opaque' and not boundary['envelope']['control']['known']
                and boundary['envelope']['control']['remainder'] ==
                {'kind': 'within', 'scope': {'kind': 'labels', 'labels': []}},
                'DISPLAY handler retains explicit open control frontier')
    if name == 'mixed-data':
        numeric = [d for d in sp['dataDeclarations'] if d['canonicalName'] == 'UNMODELED-NUMBER']
        require(len(numeric) == 1 and numeric[0]['picture'] == '9(4)'
                and numeric[0]['scalarText'] is None and all(not s['modelValueRemainder'] for s in sites),
                'unsupported numeric layout does not erase independent local TEXT values')
    if name == 'entry-using':
        signature = unit['entries'][0]['signature']
        require(signature['parameters']['remainder']['kind'] == 'unknown'
                and not sites[0]['modelValueRemainder'],
                'unknown linkage input does not erase independent local CALL value')
    if name == 'control-body':
        require(len(inactive) == 1, 'one inactive contextual body shadow')
        shadow = operations[inactive[0]['operation']['localId']]
        require(not shadow['outcomes']['known'] and shadow['outcomes']['remainder'] ==
                {'kind': 'within', 'scope': {'kind': 'labels', 'labels': []}},
                'inactive body shadow retains its explicit open boundary')
    if name in EXPECTED:
        require(len(sites) == len(EXPECTED[name]), 'independent source site count')
        for site, values in zip(sites, EXPECTED[name]):
            actual = {c['referenceName'] for c in site['candidates']}
            require(actual == values, name + ': expected ' + repr(values) + ', got ' + repr(actual))
            invoke=operations[site['operation']['localId']]
            # Local exact values survive unrelated unknown linkage and numeric layout.
            if site['reachability'] == 'REACHABLE':
                open_call_model(invoke, site)
    if name in ('p5', 'if-unknown'):
        require({c['referenceName'] for c in sites[0]['candidates']} == {'PROGA'}, 'literal before control frontier survives')
        require(sites[0]['modelValueRemainder'] is False, 'earlier value remains precise')
        if name == 'if-unknown':
            branch = next(o for o in operations.values() if o['kind'] == 'branch')
            require(branch['predicate']['kind'] == 'unknown'
                    and branch['trueDestination'] != branch['falseDestination'],
                    'unknown predicate retains both proved IF arms')
            require({c['referenceName'] for c in sites[1]['candidates']} == {'PROGB', 'PROGC'}
                    and not sites[1]['openControlRemainder'] and not sites[1]['modelValueRemainder'],
                    'unknown predicate does not erase modeled branch values')
        else:
            require({c['referenceName'] for c in sites[1]['candidates']} == {'PROGB', 'PROGC'}, 'supported EVALUATE arms preserve both values')
            require(all(not s['openControlRemainder'] and not s['modelValueRemainder'] for s in sites), 'supported EVALUATE and CALL are closed')
    if name == 'call-unknown':
        require(len(sites) == 1 and not sites[0]['candidates'] and sites[0]['effectiveUnknownRemainder'], 'unavailable name preserves open dependency site')
    if name == 'call-handlers':
        require('PROGA' in {c['referenceName'] for c in sites[0]['candidates']} and sites[0]['openControlRemainder'], 'CALL target survives unknown handler control')
    if name == 'must-write':
        moves = [s for s in sp['statements'] if s['variant'] == 'MOVE']
        require(len(moves) == 2 and operations[links[moves[0]['header']['id']][0]['localId']]['kind'] == 'assign'
                and operations[links[moves[1]['header']['id']][0]['localId']]['kind'] == 'nop',
                'unimplemented oversized transform retains diagnostic Nop and prior supported Assign')
    if name == 'body-gap':
        require(any(o['kind'] == 'opaque' for o in operations.values()), 'semantic body gap remains conservative')
    precise = name.startswith('perform-') or name in ('if-nested', 'stress')
    if precise:
        require(all(o['kind'] != 'opaque' for o in operations.values()), 'supported composition needs no fallback')
    else:
        require(p['uncertainties'] if 'uncertainties' in p else False, 'partial source gaps retained')
    if name == 'stress':
        require(sum(s['variant'] == 'IF' for s in sp['statements']) == 10, 'ten IFs')
        require(sum(s['variant'] == 'PERFORM' for s in sp['statements']) == 5, 'five BASIC activations')
        require(sum(s['variant'] == 'MOVE' for s in sp['statements']) == 36, 'dozens of MOVEs')
    return sites


def run(work, config_path):
    require_local(); work.mkdir(parents=True, exist_ok=False)
    producer = config_path.parent; config = json.loads(config_path.read_text())
    lock = json.loads((ROOT / 'docs/sources/sources.lock.json').read_text())
    for name, key in (('air-java', 'air_java'), ('proleap-poc', 'proleap_poc'), ('cobol-lower', 'cobol_lower')):
        pin = lock[key].get('commit', lock[key].get('main_commit'))
        require(config['sources'][name] == pin == git(producer / name, 'rev-parse', 'HEAD') and not git(producer / name, 'status', '--porcelain'), 'clean immutable producer: ' + name)
    cp = runtime(producer)
    for fixture in sorted(FIXTURES.glob('*.cbl')):
        outputs = []; name = fixture.stem
        for attempt in ('A', 'B'):
            cwd = work / (name + '-' + attempt); cwd.mkdir(); source = cwd / fixture.name
            shutil.copyfile(fixture, source)
            web = cwd / 'src/main/resources'; web.mkdir(parents=True)
            (web / 'web').symlink_to(producer / 'proleap-poc/src/main/resources/web', target_is_directory=True)
            execute(cwd, 'frontend', ['java', '-cp', os.pathsep.join(config['frontend']['classpath']), config['frontend']['main'], '--source', source.name, '--copybooks', str(producer / 'proleap-poc/corpus/cpy'), '--output', str(cwd / 'sp')])
            sp_path = cwd / 'sp/cobol-semantic-product.json'; air_path = cwd / 'program.air.json'
            cfg = cwd / 'cfg.json'; dep = cwd / 'dependencies.json'
            execute(cwd, 'lower', ['java', '-cp', os.pathsep.join(config['lower']['classpath']), config['lower']['main'], str(sp_path), str(air_path)])
            execute(cwd, 'cfg', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg', str(air_path), str(cfg)])
            verify_cfg_wire(cfg.read_bytes())
            execute(cwd, 'dependency', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(air_path), str(dep)])
            sp = json.loads(sp_path.read_text()); air = json.loads(air_path.read_text()); result = read(dep)
            sites = oracle(name, sp, air, result)
            if attempt == 'A':
                shuffled = copy.deepcopy(air); shuffled['publication']['units'][0]['sequences'].reverse()
                perm_air = cwd / 'permuted.air.json'; perm_air.write_text(json.dumps(shuffled)); perm_dep = cwd / 'permuted.dependencies.json'
                execute(cwd, 'permuted', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(perm_air), str(perm_dep)])
                other = read(perm_dep); oracle(name, sp, shuffled, other)
                require(result['sites'] == other['sites'] and result['edges'] == other['edges'], 'physical array order cannot determine control')
            outputs.append([p.read_bytes() for p in (sp_path, air_path, cfg, dep)])
            print('PASS partial ' + name + ' ' + attempt + ': ' + repr([([c['referenceName'] for c in s['candidates']], s['modelValueRemainder']) for s in sites]), flush=True)
        require(outputs[0] == outputs[1], name + ': SP/AIR/CFG/dependency A/B exact bytes')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--work', required=True, type=Path); parser.add_argument('--producers', required=True, type=Path)
    args = parser.parse_args()
    try: run(args.work.resolve(), args.producers.resolve())
    except (ValueError, RuntimeError, OSError) as error:
        print('FAIL: ' + str(error), file=sys.stderr); sys.exit(1)
