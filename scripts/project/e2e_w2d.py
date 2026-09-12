#!/usr/bin/env python3
"""Real W2D twice, with public wire/source oracles. No solver internals or path enumeration."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg_wire
from prepare_w2d_producers import ROOT, git, require_local

FIXTURES = ROOT / 'analysis-adapters/src/test/resources/cp6/w2d'


def execute(cwd, label, command):
    print('+ ' + label + ': ' + ' '.join(command), flush=True)
    with (cwd / (label + '.stdout')).open('wb') as out, (cwd / (label + '.stderr')).open('wb') as err:
        result = subprocess.run(command, cwd=cwd, stdout=out, stderr=err)
    require(result.returncode == 0, label + ' failed; see ' + str(cwd / (label + '.stderr')))


def runtime(producer):
    modules = ['cfg-kernel', 'cfg-adapters', 'cfg-launcher', 'analysis-kernel', 'analysis-values',
               'analysis-dependencies', 'analysis-dataflow', 'analysis-adapters', 'analysis-launcher']
    cp = (ROOT / 'analysis-launcher/target/runtime-classpath.txt').read_text().strip()
    air = [producer / 'fast-upstream/air-model/air-java-0.1.0-SNAPSHOT.jar',
           producer / 'fast-upstream/air-json/air-json-0.1.0-SNAPSHOT.jar']
    require(all(p.is_file() for p in air), 'locked AIR build outputs required')
    return os.pathsep.join([str(ROOT / m / 'target/classes') for m in modules] + [str(p) for p in air] + [cp])


def source_spans(result, support, source):
    origins = {o['id']['localId']: o for o in result['origins']}
    artifacts = {a['id']['localId']: a['logicalName'] for a in result['artifacts']}
    pending, seen, spans = [support['origin']['localId']], set(), []
    while pending:
        key = pending.pop()
        if key in seen:
            continue
        seen.add(key)
        origin = origins[key]
        if origin['kind'] == 'DERIVED':
            pending.extend(o['localId'] for o in origin['inputs'])
        elif origin['kind'] == 'WRITTEN' and artifacts[origin['artifact']['localId']] == source.name:
            spans.append(origin['location'])
    require(bool(spans), 'candidate must reach original COBOL span')
    return spans


def air_oracle(air, opened):
    p = air['publication']; require(air['airVersion'] == '2.0.0', 'AIR version')
    require(len(p['units']) == 1, 'one caller unit')
    unit = p['units'][0]; seq = unit['sequences']
    require(len(seq) == (4 if opened else 5), 'diamond sequences without artificial block')
    by_label = {s['label']['localId']: s for s in seq}
    branch = next(s for s in seq if s['terminator']['kind'] == 'branch')
    call = next(s for s in seq if s['terminator']['kind'] == 'invoke')
    ret = next(s for s in seq if s['terminator']['kind'] == 'return')
    require(unit['entries'][0]['initialLabel'] == branch['label'], 'explicit AIR entry is branch')
    b = branch['terminator']; require(b['predicate']['kind'] == 'unknown', 'real IF is Unknown BOOL')
    require(b['predicate']['typeRef'] == {'kind': 'known', 'type': {'kind': 'bool'}}, 'predicate is BOOL')
    yes = by_label[b['trueDestination']['localId']]; no = by_label[b['falseDestination']['localId']]
    require(yes['terminator']['kind'] == 'jump' and yes['terminator']['destination'] == call['label'], 'true jumps to join')
    require(no == call if opened else no['terminator']['kind'] == 'jump' and no['terminator']['destination'] == call['label'], 'false open bypass / closed assignment')
    require(not branch['instructions'] and not call['instructions'] and not ret['instructions'], 'no invented seed/assignment')
    assignments = [i for s in seq for i in s['instructions']]
    require(all(i['kind'] == 'assign' for i in assignments), 'only actual assignments, no havoc')
    expected = ['PROGA   '] if opened else ['PROGA   ', 'PROGB   ']
    require(sorted(i['value']['value']['value'] for i in assignments) == expected, 'exact padded AIR assignments')
    require(yes['instructions'][0]['value']['value']['value'] == 'PROGA   ', 'true arm assignment')
    if not opened:
        require(no['instructions'][0]['value']['value']['value'] == 'PROGB   ', 'false arm assignment')
    invoke = call['terminator']
    require(invoke['outcomes']['known'] == [{'kind': 'normal', 'label': ret['label']}], 'normal Invoke continuation')
    return unit, call, assignments


def dependency_oracle(result, air, source, opened):
    unit, call, assignments = air_oracle(air, opened)
    require(len(result['sites']) == 1, 'one CALL site')
    site = result['sites'][0]; invoke = call['terminator']
    require(site['caller'] == unit['id'] and site['entry'] == unit['entries'][0]['id'], 'caller/entry identity')
    require(site['operation'] == invoke['header']['id'] and site['sequence'] == call['label'] and site['offset'] == 0, 'join Invoke identity')
    require(site['valuePoint']['position'] == 'BEFORE' and site['valuePoint']['operationId'] == site['operation'], 'BEFORE Invoke observation')
    require(site['targetKind'] == 'COMPUTED' and site['reachability'] == 'REACHABLE', 'reachable dynamic CALL')
    require(site['modelValueRemainder'] is opened, 'natural model remainder')
    # These are independent existing source/name-policy dimensions, not inferred from model closure.
    require(site['sourceValueRemainder'] and site['interpretationUnknownRemainder'] and site['effectiveUnknownRemainder'], 'preserve source/interpretation/effective remainders')
    require(site['openControlRemainder'], 'preserve real source open control')
    names = ['PROGA'] if opened else ['PROGA', 'PROGB']
    raw = [name.ljust(8) for name in names]
    require([c['referenceName'] for c in site['candidates']] == names, 'exact known dependencies')
    require([c['rawValue'] for c in site['rawCandidates']] == raw, 'raw values preserve padding')
    require([c['rawValue'] for c in site['candidates']] == raw, 'interpreted candidates retain raw values')
    require(result['metrics']['possibleValuesRuns'] == 1, 'one normal fixed-point run')
    require(len(result['edges']) == len(names), 'one edge per known candidate')
    require([e['candidate'] for e in result['edges']] == site['candidates'], 'edge candidates preserve supports')
    require(all(e['site'] == site['operation'] and e['caller'] == site['caller'] and e['openSite'] for e in result['edges']), 'same caller and CALL site')
    for index, name in enumerate(names):
        candidate = site['candidates'][index]
        require(len(candidate['supports']) == 1, 'candidate-specific single support')
        support = candidate['supports'][0]
        require(site['rawCandidates'][index]['supports'] == candidate['supports'], 'same raw/interpreted supports')
        assign = next(a for a in assignments if a['value']['value']['value'] == raw[index])
        require(support['kind'] == 'VALUE_PRODUCER' and support['producer'] == assign['header']['id'] and support['origin'] == assign['header']['origin'], 'candidate supports only its own Assign')
        require(site['subject'] == assign['destination']['object'] == invoke['target']['name']['place']['object'], 'real WS-PGM object identity')
        line = 11 if name == 'PROGA' else 13
        spans = source_spans(result, support, source)
        require(all(s['startLine'] == str(line) and s['endLine'] == str(line) for s in spans), 'support must not cross source arms')
        require("MOVE '" + name + "' TO WS-PGM" in source.read_text().splitlines()[line - 1], 'manual COBOL MOVE oracle')


def run(work, config_path):
    require_local()
    work.mkdir(parents=True, exist_ok=False)
    producer = config_path.parent; config = json.loads(config_path.read_text())
    lock = json.loads((ROOT / 'docs/sources/sources.lock.json').read_text())
    for name, key in (('air-java', 'air_java'), ('proleap-poc', 'proleap_poc'), ('cobol-lower', 'cobol_lower')):
        pin = lock[key].get('commit', lock[key].get('main_commit'))
        require(config['sources'][name] == pin and git(producer / name, 'rev-parse', 'HEAD') == pin
                and not git(producer / name, 'status', '--porcelain'), 'exact source pin: ' + name)
    for name in ('closed', 'open'):
        outputs = []
        for attempt in ('A', 'B'):
            cwd = work / (name + '-' + attempt); cwd.mkdir()
            source = cwd / (name + '.cbl'); shutil.copyfile(FIXTURES / source.name, source)
            web = cwd / 'src/main/resources'; web.mkdir(parents=True)
            (web / 'web').symlink_to(producer / 'proleap-poc/src/main/resources/web', target_is_directory=True)
            execute(cwd, 'frontend', ['java', '-cp', os.pathsep.join(config['frontend']['classpath']), config['frontend']['main'],
                    '--source', source.name, '--copybooks', str(producer / 'proleap-poc/corpus/cpy'), '--output', str(cwd / 'sp')])
            sp = cwd / 'sp/cobol-semantic-product.json'
            semantic = json.loads(sp.read_text())
            version = semantic['contractVersion']
            require(version == config['semanticProductVersion'] == lock['proleap_poc']['semantic_product_version'],
                    'W2D must consume the exact locked SP version; actual producer emitted ' + version)
            require(semantic['unit']['canonicalProgramName'] == 'CALLER', 'real caller program identity')
            air = cwd / 'program.air.json'
            execute(cwd, 'lower', ['java', '-cp', os.pathsep.join(config['lower']['classpath']), config['lower']['main'], str(sp), str(air)])
            publication = json.loads(air.read_text()); air_oracle(publication, name == 'open')
            cp = runtime(producer); cfg = cwd / 'cfg.json'; dep = cwd / 'dependencies.json'
            execute(cwd, 'cfg', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg', str(air), str(cfg)])
            contract = verify_cfg_wire(cfg.read_bytes())
            require({'BRANCH_TRUE', 'BRANCH_FALSE', 'JUMP', 'INVOKE_NORMAL'} <= set(contract['transitions']), 'real CFG labeled diamond')
            execute(cwd, 'dependency', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(air), str(dep)])
            result = read(dep); dependency_oracle(result, publication, source, name == 'open')
            # Public transport metamorphism: change physical order only, keep every identity/destination.
            for unit in publication['publication']['units']:
                unit['sequences'].reverse()
            permuted = cwd / 'permuted.air.json'; permuted.write_text(json.dumps(publication))
            permdep = cwd / 'permuted.dependencies.json'
            execute(cwd, 'permuted', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(permuted), str(permdep)])
            other = read(permdep); dependency_oracle(other, publication, source, name == 'open')
            require(result['sites'] == other['sites'] and result['edges'] == other['edges'], 'physical sequence order must not affect semantics')
            outputs.append([p.read_bytes() for p in (source, sp, air, cfg, dep)])
            print('PASS real ' + name + ' ' + attempt + ': candidates=' + str([c['referenceName'] for c in result['sites'][0]['candidates']])
                  + ' modelValueRemainder=' + str(result['sites'][0]['modelValueRemainder']) + ' candidate-specific MOVE supports; permutation PASS', flush=True)
        require(outputs[0] == outputs[1], name + ' A/B bytes differ')
        print('PASS ' + name + ' A/B: COBOL, SP, AIR, CFG, dependency bytes identical', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--producers', required=True, type=Path)
    args = parser.parse_args()
    try:
        run(args.work.resolve(), args.producers.resolve())
    except (ValueError, RuntimeError, OSError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        sys.exit(1)
