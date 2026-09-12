#!/usr/bin/env python3
"""Real isolated paragraph PERFORM through locked SP/AIR/CFG/dependency boundaries; local only."""
import argparse
import copy
import json
import os
from pathlib import Path
import shutil
import sys

from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import execute, runtime, source_spans
from e2e_move_data import reference
from prepare_w2d_producers import ROOT, git, require_local

FIXTURES = ROOT / 'analysis-adapters/src/test/resources/cp6/perform-basic'


def source_oracle(sp, case):
    require(sp['contractVersion'] == '1.7.0' and sp['unit']['canonicalProgramName'] == 'CALLER', 'real CALLER at SP1.7')
    statements = {s['header']['id']: s for s in sp['statements']}
    performs = [s for s in statements.values() if s['variant'] == 'PERFORM']
    require(len(performs) == 1, 'one typed PERFORM')
    p = performs[0]
    require(p['profile'] == 'SIMPLE_SINGLE_CALLSITE_PROCEDURE_PERFORM' and not p['gapCodes'], 'isolated single-callsite proof')
    require(p['target']['id'].startswith('procedure:'), 'canonical typed procedure identity')
    body = [statements[s] for s in p['targetStatements']]
    primary = [statements[s] for s in p['primaryStatements']]
    require(len(body) == (2 if case == 'copy' else 1), 'complete nonempty body')
    require([s['variant'] for s in primary] == (['MOVE'] if case == 'overwrite' else []) + ['PERFORM', 'CALL', 'GOBACK'], 'closed primary flow ends at GOBACK')
    require(set(p['primaryStatements']).isdisjoint(p['targetStatements'])
            and set(p['primaryStatements'] + p['targetStatements']) == set(statements), 'no extra modeled target entry')
    require(sp['entryInventory']['entries'][0]['start']['statement'] == primary[0]['header']['id'], 'explicit primary entry')
    require(p['targetEntry'] == body[0]['header']['id'] and p['targetExit'] == body[-1]['header']['id'], 'published body endpoints')
    call = primary[-2]
    require(p['normalContinuation']['availability'] == 'KNOWN' and p['normalContinuation']['statement'] == call['header']['id'], 'unique resume')
    require(body[-1]['normalContinuation']['statement'] == call['header']['id'], 'isolated activation return fact')
    data = {d['canonicalName']: d['id'] for d in sp['dataDeclarations']}
    require(set(data) == ({'WS-A', 'WS-PGM'} if case == 'copy' else {'WS-PGM'}), 'actual scalar declarations')
    require(body[0]['source']['variant'] == 'LITERAL' and body[0]['source']['logicalValue']['value'] == 'PROGA'
            and body[0]['textAdjustment']['result']['value'] == 'PROGA   ', 'original literal and padding')
    reference(body[0]['target'], 'WRITE', data['WS-A' if case == 'copy' else 'WS-PGM'])
    if case == 'copy':
        require(body[1]['source']['variant'] == 'DATA', 'typed MOVE-to-MOVE composition')
        reference(body[1]['source']['reference'], 'READ', data['WS-A']); reference(body[1]['target'], 'WRITE', data['WS-PGM'])
        proof = sp['storageIndependence']
        require(proof['availability'] == 'KNOWN' and set(proof['members']) == set(data.values()), 'original independent storage proof')
    require(all(origin['exact'] for origin in [p['header']['provenance'], p['target']['referenceOrigin'], p['target']['paragraphOrigin'], p['normalContinuation']['provenance']]), 'exact control origins')
    return p, data, body


def air_oracle(air, semantic, case, source):
    perform, data, source_body = semantic
    require(air['airVersion'] == '2.0.0' and air['bindingVersion'] == '1.0.0', 'unchanged AIR contracts')
    p = air['publication']; require(len(p['units']) == 1, 'one unit')
    unit = p['units'][0]; seq = unit['sequences']; require(len(seq) == 4, 'four explicit control sequences')
    labels = {s['label']['localId']: s for s in seq}
    main = labels[unit['entries'][0]['initialLabel']['localId']]
    require(main['terminator']['kind'] == 'jump', 'PERFORM is direct Jump, never Invoke or bypass')
    target = labels[main['terminator']['destination']['localId']]
    require(target != main and target['terminator']['kind'] == 'jump', 'separate paragraph body returns with Jump')
    call = labels[target['terminator']['destination']['localId']]
    require(call != main and call != target and call['terminator']['kind'] == 'invoke' and not call['instructions'], 'one CALL only at resume')
    returns = [s for s in seq if s['terminator']['kind'] == 'return']; require(len(returns) == 1, 'GOBACK Return')
    require(call['terminator']['outcomes']['known'] == [{'kind': 'normal', 'label': returns[0]['label']}], 'CALL return')
    require(sum(s['terminator']['kind'] == 'invoke' for s in seq) == 1, 'no PERFORM program dependency site')
    require(len(main['instructions']) == (1 if case == 'overwrite' else 0), 'no duplicate CALL or body in primary block')
    if case == 'overwrite': require(main['instructions'][0]['value']['value']['value'] == 'OLDPROG ', 'old value executes before PERFORM')
    assigns = target['instructions']; require(len(assigns) == len(source_body) and all(a['kind'] == 'assign' for a in assigns), 'entire MOVE body')
    require(assigns[0]['value']['kind'] == 'literal' and assigns[0]['value']['value']['value'] == 'PROGA   ', 'literal body value')
    objects = {}
    for name, identity in data.items():
        item = next(i for i in p['coverage']['items'] if i['sourceKey'].endswith('/data/' + identity))
        objects[name] = next(o for o in item['outputs'] if o['domain'] == 'object')
    require(assigns[0]['destination']['object'] == objects['WS-A' if case == 'copy' else 'WS-PGM'], 'body writes correct target')
    if case == 'copy':
        expression = assigns[1]['value']
        require(expression['kind'] == 'read' and expression['place']['object'] == objects['WS-A']
                and assigns[1]['destination']['object'] == objects['WS-PGM'], 'Read copy, not constant folding')
        require(len(p['premises']) == 1 and p['premises'][0]['assertion']['kind'] == 'disjoint_storage', 'preserved storage premise')
    require(call['terminator']['target']['name']['place']['object'] == objects['WS-PGM'], 'CALL reads receiver')
    for assign, fact in zip(assigns, source_body):
        spans = source_spans(p, {'origin': assign['header']['origin']}, source)
        require(all(int(s['span']['start']['line']) == fact['header']['provenance']['original']['startLine'] for s in spans), 'body origins preserved')
    control_spans = source_spans(p, {'origin': target['terminator']['header']['origin']}, source)
    required_lines = {perform['header']['provenance']['original']['startLine'], perform['target']['paragraphOrigin']['original']['startLine'], perform['normalContinuation']['provenance']['original']['startLine']}
    require(required_lines <= {int(s['span']['start']['line']) for s in control_spans}, 'return preserves callsite/paragraph/resume provenance')
    return unit, call, assigns[0], objects['WS-PGM']


def dependency_oracle(result, model, source):
    unit, call, producer, subject = model
    require(len(result['sites']) == 1 and len(result['edges']) == 1, 'CALL is the only dependency site/edge')
    site = result['sites'][0]
    require(site['caller'] == unit['id'] and site['entry'] == unit['entries'][0]['id'] and site['subject'] == subject, 'CALLER and receiver identity')
    require(site['sequence'] == call['label'] and site['operation'] == call['terminator']['header']['id'] and site['offset'] == 0, 'CALL only in resume')
    require(site['reachability'] == 'REACHABLE' and site['valuePoint']['position'] == 'BEFORE', 'reachable BEFORE Invoke query')
    require([c['referenceName'] for c in site['candidates']] == ['PROGA'], 'PROGA only; OLDPROG killed')
    require([c['rawValue'] for c in site['rawCandidates']] == ['PROGA   '] and site['candidates'][0]['rawValue'] == 'PROGA   ', 'raw padding retained')
    require(site['modelValueRemainder'] is False, 'closed model')
    require(site['sourceValueRemainder'] and site['interpretationUnknownRemainder'] and site['effectiveUnknownRemainder'], 'other real-source remainders remain explicit')
    supports = site['candidates'][0]['supports']
    require(len(supports) == 1 and supports[0]['producer'] == producer['header']['id'] and supports[0]['origin'] == producer['header']['origin'], 'original literal supports copied candidate')
    require(site['rawCandidates'][0]['supports'] == supports and result['edges'][0]['candidate'] == site['candidates'][0], 'edge/raw support consistency')
    line = next(i for i, text in enumerate(source.read_text().splitlines(), 1) if "MOVE 'PROGA'" in text)
    require(all(int(s['startLine']) == line == int(s['endLine']) for s in source_spans(result, supports[0], source)), 'support reaches original MOVE literal')
    require(result['metrics']['possibleValuesRuns'] == 1, 'ordinary forward solver')


def run(work, config_path):
    require_local(); work.mkdir(parents=True, exist_ok=False)
    producer = config_path.parent; config = json.loads(config_path.read_text())
    lock = json.loads((ROOT / 'docs/sources/sources.lock.json').read_text())
    for name, key in (('air-java', 'air_java'), ('proleap-poc', 'proleap_poc'), ('cobol-lower', 'cobol_lower')):
        pin = lock[key].get('commit', lock[key].get('main_commit'))
        require(config['sources'][name] == pin == git(producer / name, 'rev-parse', 'HEAD') and not git(producer / name, 'status', '--porcelain'), 'exact clean source pin: ' + name)
    require(config['semanticProductVersion'] == lock['proleap_poc']['semantic_product_version'] == '1.7.0', 'exact SP version')
    cp = runtime(producer)
    for case in ('literal', 'copy', 'overwrite'):
        outputs = []
        for attempt in ('A', 'B'):
            cwd = work / (case + '-' + attempt); cwd.mkdir(); source = cwd / (case + '.cbl')
            shutil.copyfile(FIXTURES / source.name, source)
            web = cwd / 'src/main/resources'; web.mkdir(parents=True)
            (web / 'web').symlink_to(producer / 'proleap-poc/src/main/resources/web', target_is_directory=True)
            execute(cwd, 'frontend', ['java', '-cp', os.pathsep.join(config['frontend']['classpath']), config['frontend']['main'], '--source', source.name,
                '--copybooks', str(producer / 'proleap-poc/corpus/cpy'), '--output', str(cwd / 'sp')])
            sp = cwd / 'sp/cobol-semantic-product.json'; semantic = source_oracle(json.loads(sp.read_text()), case)
            air = cwd / 'program.air.json'
            execute(cwd, 'lower', ['java', '-cp', os.pathsep.join(config['lower']['classpath']), config['lower']['main'], str(sp), str(air)])
            publication = json.loads(air.read_text()); model = air_oracle(publication, semantic, case, source)
            cfg = cwd / 'cfg.json'; dep = cwd / 'dependencies.json'
            execute(cwd, 'cfg', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg', str(air), str(cfg)])
            require({'JUMP', 'INVOKE_NORMAL', 'RETURN'} <= set(verify_cfg_wire(cfg.read_bytes())['transitions']), 'explicit CFG transfers')
            execute(cwd, 'dependency', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(air), str(dep)])
            result = read(dep); dependency_oracle(result, model, source)
            permuted = copy.deepcopy(publication); permuted['publication']['units'][0]['sequences'].reverse()
            perm_air = cwd / 'permuted.air.json'; perm_air.write_text(json.dumps(permuted)); perm_dep = cwd / 'permuted.dependencies.json'
            execute(cwd, 'permuted', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(perm_air), str(perm_dep)])
            other = read(perm_dep); dependency_oracle(other, air_oracle(permuted, semantic, case, source), source)
            require(result['sites'] == other['sites'] and result['edges'] == other['edges'], 'sequence permutation preserves result')
            outputs.append([path.read_bytes() for path in (sp, air, cfg, dep)])
            print(f'PASS PERFORM {case} {attempt}: CALLER -> PROGA; raw="PROGA   "; modelValueRemainder=false; original literal support; permutation PASS', flush=True)
        require(outputs[0] == outputs[1], case + ' A/B bytes differ')
        print('PASS PERFORM ' + case + ' A/B: SP, AIR, CFG, dependency bytes identical', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--work', required=True, type=Path); parser.add_argument('--producers', required=True, type=Path)
    args = parser.parse_args()
    try: run(args.work.resolve(), args.producers.resolve())
    except (ValueError, RuntimeError, OSError) as error:
        print('FAIL: ' + str(error), file=sys.stderr); sys.exit(1)
