#!/usr/bin/env python3
"""Local, pinned three-source IF admission probe. Expected gaps are explicit assertions."""
import argparse
import json
import os
from pathlib import Path
import shutil

from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import execute, locked_sp, source_spans
from prepare_w2d_producers import ROOT, git, require_local

PROFILE = 'ibm-enterprise-6.4-fixed-display-1047@1'
FIXTURES = ROOT / 'analysis-adapters/src/test/resources/analysis-gaps/w2'


def operations_for(publication, statement):
    ids = {output['localId'] for item in publication['coverage']['items']
           if item['sourceKey'].endswith('/' + statement)
           for output in item['outputs'] if output['domain'] == 'operation'}
    return [operation for unit in publication['units'] for sequence in unit['sequences']
            for operation in sequence['instructions'] + [sequence['terminator']]
            if operation['header']['id']['localId'] in ids]


def oracle(name, source, sp, air, cfg, result):
    locked_sp(sp)
    require(sp['storage']['profileId'] == PROFILE, 'explicit regional profile, not unspecified storage')
    fact = next(s for s in sp['statements'] if s['variant'] == 'IF')
    predicate = fact['condition']['predicate']
    reference, = fact['condition']['references']
    require(reference['binding']['status'] == 'RESOLVED', 'unique predicate declaration')
    view = next(v for v in sp['storage']['views'] if v['node'] == reference['regionalAccess']['view'])
    require(view['extent']['value'] == '1' and view['offset']['value'] == '0'
            and view['codec'] == 'text.ebcdic.ibm1047@1', 'predicate storage view exists independently of scalar proof')
    p = air['publication']; unit, = p['units']
    operation, = operations_for(p, fact['header']['id'])
    call_sequence, = [s for s in unit['sequences'] if s['terminator']['kind'] == 'invoke']
    verify_cfg_wire(json.dumps(cfg).encode())
    kinds = [edge['kind'] for edge in cfg['transitions']]
    if name == 'regional-predicate':
        require(predicate['availability'] == 'PARTIAL' and predicate['profile'] == 'UNAVAILABLE'
                and predicate['gapCodes'] == ['PREDICATE_NOT_PROVEN'], 'first gap: predicate proof is not published')
        require(reference['wholeItemAccess'] is None, 'regional read lacks scalar whole-item proof')
        require(operation['kind'] == 'opaque', 'lower conservatively retains IF as Opaque')
        control = operation['envelope']['control']; memory = operation['envelope']['memory']
        require(control['known'] == [] and control['remainder']['kind'] == 'within'
                and control['remainder']['scope']['kind'] == 'unit', 'no fabricated typed branch successors')
        require(memory['otherWrites']['kind'] == 'within' and memory['otherWrites']['scope']['kind'] == 'all', 'opaque effects stay open')
        require('BRANCH_TRUE' not in kinds and 'BRANCH_FALSE' not in kinds, 'known CFG cannot repair missing Branch')
        require('cobol-lower:PRECISE_SEMANTICS_UNAVAILABLE' in {u['code'] for u in p['uncertainties']}, 'explicit lower gap')
    else:
        require(predicate['availability'] == 'KNOWN' and predicate['profile'] == 'SCALAR_TEXT_EQUALITY'
                and predicate['evaluation'] == 'PURE' and predicate['normalCompletion'] == 'TOTAL'
                and predicate['readsCompleteness'] == 'COMPLETE', 'scalar source predicate admitted')
        require(reference['wholeItemAccess'] is not None and operation['kind'] == 'branch', 'whole-item -> AIR Branch')
        require(operation['predicate']['kind'] == 'unknown'
                and operation['predicate']['typeRef'] == {'kind': 'known', 'type': {'kind': 'bool'}}, 'unknown truth, known total BOOL evaluation')
        by_label = {s['label']['localId']: s for s in unit['sequences']}
        for arm, destination in [('thenArm', 'trueDestination'), ('elseArm', 'falseDestination')]:
            sequence = by_label[operation[destination]['localId']]
            move, = operations_for(p, fact[arm]['entry']['statement'])
            require(move in sequence['instructions'] and move['kind'] == 'assign', 'typed branch enters corresponding source MOVE')
            require(sequence['terminator']['kind'] == 'jump'
                    and sequence['terminator']['destination'] == call_sequence['label'], 'both predecessors join at CALL')
        require(kinds.count('BRANCH_TRUE') == kinds.count('BRANCH_FALSE') == 1, 'both typed CFG branch edges')
    require(kinds.count('JUMP') == 2 and kinds.count('INVOKE_NORMAL') == 1, 'known arm continuations and CALL normal edge')
    target = call_sequence['terminator']['target']
    require(target['kind'] == 'computed' and target['name']['kind'] == 'read'
            and target['name']['place']['kind'] == 'object', 'supported plain CALL object, never inline expression')
    site, = result['sites']
    require([c['referenceName'] for c in site['candidates']] == ['PROGA', 'PROGB'], 'known candidates survive even conservative source model')
    require(site['targetStatus'] == 'RESOLVED_CANDIDATES' and site['reachability'] == 'REACHABLE', 'candidate status is not a closed-world guarantee')
    require(all(site[k] for k in ('modelValueRemainder', 'sourceValueRemainder', 'interpretationUnknownRemainder',
                                 'effectiveUnknownRemainder', 'openControlRemainder')), 'real external CALL and source remainders remain explicit')
    moves = {s['source']['logicalValue']['value']: s for s in sp['statements'] if s['variant'] == 'MOVE'}
    for candidate in site['candidates']:
        move = moves[candidate['referenceName']]; assigned, = operations_for(p, move['header']['id'])
        require(assigned['kind'] == 'assign', 'source MOVE capability already present')
        support, = candidate['supports']
        require(support['producer'] == assigned['header']['id'] and support['origin'] == assigned['header']['origin'], 'candidate-specific source assignment support')
        span = move['header']['provenance']['original']
        require(all(int(s['startLine']) == span['startLine'] and int(s['endLine']) == span['endLine']
                    for s in source_spans(result, support, source)), 'support reaches only its own original source arm')
    return dict(predicateAvailability=predicate['availability'], predicateProfile=predicate['profile'],
                wholeItemAccess=reference['wholeItemAccess'], regionalView=view['node'],
                airIf=operation['kind'], cfgEdges=kinds, candidates=[c['referenceName'] for c in site['candidates']],
                targetStatus=site['targetStatus'], modelOpen=site['modelValueRemainder'],
                sourceOpen=site['sourceValueRemainder'], interpretationOpen=site['interpretationUnknownRemainder'],
                controlOpen=site['openControlRemainder'])


def run(work, producers):
    require_local(); work.mkdir(parents=True, exist_ok=False)
    config = json.loads(producers.read_text()); producer = producers.parent
    lock = json.loads((ROOT / 'docs/sources/sources.lock.json').read_text())
    for name, key in [('air-java', 'air_java'), ('proleap-poc', 'proleap_poc'), ('cobol-lower', 'cobol_lower')]:
        require(config['sources'][name] == lock[key]['commit'] == git(producer / name, 'rev-parse', 'HEAD')
                and not git(producer / name, 'status', '--porcelain'), 'clean producer at immutable pin: ' + name)
    modules = ['cfg-kernel', 'cfg-adapters', 'cfg-launcher', 'analysis-kernel', 'analysis-values',
               'analysis-dependencies', 'analysis-dataflow', 'analysis-adapters', 'analysis-launcher']
    # Reuse the pinned AIR and runtime dependencies built by the existing producer wrapper.
    dependencies = [p for p in config['lower']['classpath'] if not Path(p).name.startswith('cobol-lower-')]
    cp = os.pathsep.join([str(ROOT / m / 'target/classes') for m in modules] + dependencies)
    summary = {}
    for source in sorted(FIXTURES.glob('*.cbl')):
        cwd = work / source.stem; cwd.mkdir(); shutil.copyfile(source, cwd / source.name)
        web = cwd / 'src/main/resources'; web.mkdir(parents=True)
        (web / 'web').symlink_to(producer / 'proleap-poc/src/main/resources/web', target_is_directory=True)
        execute(cwd, 'frontend', ['java', '-cp', os.pathsep.join(config['frontend']['classpath']), config['frontend']['main'],
                '--source', source.name, '--copybooks', str(producer / 'proleap-poc/corpus/cpy'), '--output', str(cwd / 'sp'), '--storage-profile', PROFILE])
        sp = cwd / 'sp/cobol-semantic-product.json'; air = cwd / 'program.air.json'
        execute(cwd, 'lower', ['java', '-cp', os.pathsep.join(config['lower']['classpath']), config['lower']['main'], str(sp), str(air)])
        for name, main in [('cfg', 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg'),
                           ('dependency', 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies')]:
            execute(cwd, name, ['java', '-cp', cp, main, str(air), str(cwd / (name + '.json'))])
        summary[source.stem] = oracle(source.stem, source, json.loads(sp.read_text()), json.loads(air.read_text()),
                                      json.loads((cwd / 'cfg.json').read_text()), read(cwd / 'dependency.json'))
    (work / 'summary.json').write_text(json.dumps(dict(sources=config['sources'], cases=summary), indent=2) + '\n')
    print('PASS W2 source probe: ' + json.dumps(summary, sort_keys=True))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--producers', required=True, type=Path)
    args = parser.parse_args()
    run(args.work.resolve(), args.producers.resolve())
