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
from e2e_w2d import execute, runtime
from prepare_w2d_producers import ROOT, git, require_local

FIXTURES = ROOT / 'analysis-adapters/src/test/resources/cp6/partial-program'
# Hand-written point oracles. True means that a value can survive an interfering write.
EXPECTED = {
    'display-handler': [({'BEFORE'}, False), ({'AFTER'}, False)],
    'control-body': [({'AFTER'}, False), ({'INNER'}, False)],
    'p1': [({'PROGA'}, False)],
    'p2': [({'PROGA'}, False), ({'PROGB'}, False)],
    'p3': [({'PROGA'}, True)],
    'p4': [({'PROGA'}, False)],
    'must-write': [(set(), True)],
    'read': [({'PROGA'}, True)],
    'call-using': [({'PROGA'}, False)],
    'call-returning': [({'PROGA'}, False)],
    'perform-distinct': [({'PROGA'}, False), ({'PROGB'}, False)],
    'perform-repeated': [({'PROGA'}, False), ({'PROGA'}, False)],
    'if-arm': [({'PROGA', 'PROGB'}, True)],
    'if-nested': [({'PROGA', 'PROGB', 'PROGC'}, False)],
    'mixed-data': [({'PROGA'}, False), ({'PROGB'}, False)],
    'entry-using': [({'PROGA'}, False)],
    'stress': [({'PROGA'}, False)] * 20,
}


def source_calls(sp):
    return sorted((s for s in sp['statements'] if s['variant'] == 'CALL'), key=lambda s: s['header']['programPoint'])


def oracle(name, sp, air, result):
    require(sp['contractVersion'] == '1.8.0', 'current SP1.8')
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
    require(len(by_op) == len(calls), 'every CALL produces one distinct dependency site')
    sites = [by_op[links[c['header']['id']][0]['localId']] for c in calls]
    for site in sites:
        require(site['reachability'] == 'REACHABLE', 'known or conservatively reachable site')
    if name in EXPECTED:
        require(len(sites) == len(EXPECTED[name]), 'independent source site count')
        for site, (values, opened) in zip(sites, EXPECTED[name]):
            actual = {c['referenceName'] for c in site['candidates']}
            require(actual == values, name + ': expected ' + repr(values) + ', got ' + repr(actual))
            require(site['modelValueRemainder'] is opened, name + ': localized model remainder')
    if name in ('p5', 'if-unknown'):
        require({c['referenceName'] for c in sites[0]['candidates']} == {'PROGA'}, 'literal before control frontier survives')
        require(sites[0]['modelValueRemainder'] is False, 'earlier value remains precise')
        require(all(s['openControlRemainder'] for s in sites[1:]), 'later sites retain control uncertainty')
    if name == 'call-unknown':
        require(len(sites) == 1 and not sites[0]['candidates'] and sites[0]['effectiveUnknownRemainder'], 'unavailable name preserves open dependency site')
    if name == 'call-handlers':
        require('PROGA' in {c['referenceName'] for c in sites[0]['candidates']} and sites[0]['openControlRemainder'], 'CALL target survives unknown handler control')
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
