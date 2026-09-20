#!/usr/bin/env python3
"""Four real COBOL copy chains through the exact locked producers. Local only."""
import argparse
import json
import os
from pathlib import Path
import shutil
import sys

from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import locked_sp, open_call_model, execute, runtime, source_spans, text_leaf, literal_text
from prepare_w2d_producers import ROOT, git, require_local

FIXTURES = ROOT / 'analysis-adapters/src/test/resources/cp6/move-data'
# Independent, manually specified operations: kind, source, receiver. No MOVE evaluator.
CASES = {
    'one-hop': [('LITERAL', 'PROGA', 'WS-A'), ('DATA', 'WS-A', 'WS-PGM')],
    'multi-hop': [('LITERAL', 'PROGA', 'WS-A'), ('DATA', 'WS-A', 'WS-B'), ('DATA', 'WS-B', 'WS-PGM')],
    'overwrite': [('LITERAL', 'OLDPROG', 'WS-PGM'), ('LITERAL', 'PROGA', 'WS-A'), ('DATA', 'WS-A', 'WS-PGM')],
    'snapshot': [('LITERAL', 'PROGA', 'WS-A'), ('DATA', 'WS-A', 'WS-PGM'), ('LITERAL', 'PROGB', 'WS-A')],
}


def source_oracle(sp, case, version):
    locked_sp(sp)
    require(sp['unit']['canonicalProgramName'] == 'CALLER', 'real caller identity')
    data = {d['canonicalName']: d['id'] for d in sp['dataDeclarations']}
    require(set(data) == ({'WS-A', 'WS-B', 'WS-PGM'} if case == 'multi-hop' else {'WS-A', 'WS-PGM'}), 'scalar declarations')
    moves = [s for s in sp['statements'] if s['variant'] == 'MOVE']
    require(len(moves) == len(CASES[case]), 'all real MOVEs represented')
    for move, (kind, value, target) in zip(moves, CASES[case]):
        require(move['source']['variant'] == kind, 'explicit source discriminator')
        reference(move['target'], 'WRITE', data[target])
        if kind == 'DATA':
            reference(move['source']['reference'], 'READ', data[value])
            require(move['copySemantics'] == 'FULL_IDENTITY' and move['textAdjustment'] is None, 'full equal-extent data copy')
        else:
            require(move['source']['logicalValue']['value'] == value, 'literal source retained')
            require(move['textAdjustment']['result']['value'] == value.ljust(8), 'existing literal fitting retained')
    return data, moves


def reference(ref, role, data):
    require(ref['role'] == role and ref['binding']['status'] == 'RESOLVED'
            and ref['binding']['selected'] == data and len(ref['binding']['candidates']) == 1
            and ref['wholeItemAccess']['data'] == data, 'canonical unique whole-item reference and role')


def air_oracle(air, data, moves, case):
    require(air['airVersion'] == '2.0.0' and air['bindingVersion'] == '1.0.0', 'AIR contracts unchanged')
    p = air['publication']; require(len(p['units']) == 1, 'one caller unit')
    unit = p['units'][0]
    require(len(unit['sequences']) == len(moves) + 2, 'one Invoke sequence and its Return continuation')
    call = next(s for s in unit['sequences'] if s['terminator']['kind'] == 'invoke')
    ret = next(s for s in unit['sequences'] if s['terminator']['kind'] == 'return')
    require(not call['instructions'] and not ret['instructions'], 'linear explicit entry and return')
    require(call['terminator']['outcomes']['known'] == [{'kind': 'normal', 'label': ret['label']}], 'normal return continuation')
    objects = {}
    for name, identity in data.items():
        item = next(i for i in p['coverage']['items'] if i['sourceKey'].endswith('/data/' + identity))
        objects[name] = next(o for o in item['outputs'] if o['domain'] == 'object')
    cells = [o['storage']['storage'] for o in unit['objects']]
    require(len(cells) == len(data) and len({json.dumps(c, sort_keys=True) for c in cells}) == len(data),
            'distinct positive bases for independent scalar declarations')
    require(not p['premises'], 'positive bases need no generated negative premise')
    labels = {s['label']['localId']: s for s in unit['sequences']}
    current=labels[unit['entries'][0]['initialLabel']['localId']]; assigns=[]
    for move in moves:
        require(len(current['instructions']) == 1 and current['terminator']['kind'] == 'jump','one explicit MOVE and continuation')
        assigns.extend(current['instructions']); current=labels[current['terminator']['destination']['localId']]
    require(current == call,'all MOVEs precede the CALL in source order')
    for assign, move, (kind, value, target) in zip(assigns, moves, CASES[case]):
        require(assign['kind'] == 'assign' and assign['destination']['object'] == objects[target]
                and assign['destination']['header']['role'] == 'VALUE_WRITE', 'exact destination and write role')
        expression = assign['value']; require(expression['header']['role'] == 'VALUE_READ', 'source value read role')
        if kind == 'LITERAL':
            require(literal_text(expression) == value.ljust(8), 'padded literal in AIR')
        else:
            expression = text_leaf(expression)
            require(expression['kind'] == 'read' and expression['place']['kind'] == 'object'
                    and expression['place']['object'] == objects[value]
                    and expression['place']['header']['role'] == 'VALUE_READ', 'data MOVE is Read of source object')
            # Logical copy origins correlate statement and both operand occurrences.
            # Check the complete exact set; unrelated source statements must not leak in.
            provenance = [move['header']['provenance'], move['source']['reference']['provenance'], move['target']['provenance']]
            def coordinates(span):
                return tuple(int(span[side][axis]) for side in ('start', 'end') for axis in ('line', 'column'))
            expected = {tuple(p['original'][side + axis.title()] for side in ('start', 'end') for axis in ('line', 'column')) for p in provenance}
            for header in (assign['header'], expression['header'], expression['place']['header'], assign['destination']['header']):
                locations = source_spans(p, {'origin': header['origin']}, Path(provenance[0]['original']['file']))
                spans = [location['span'] for location in locations]
                require(all(span['lineBase'] == '1' and span['columnBase'] == '0'
                            and span['columnUnit'] == 'UNICODE_SCALAR' and span['endExclusive'] is False
                            for span in spans), 'SP coordinate conventions')
                wanted = ({tuple(move['target']['provenance']['original'][side + axis.title()]
                                 for side in ('start', 'end') for axis in ('line', 'column'))}
                          if header is assign['destination']['header'] else expected)
                require(len(spans) == len(wanted) and {coordinates(span) for span in spans} == wanted,
                        'exact copy statement/source/receiver occurrences, no unrelated origin')
    invoke = call['terminator']
    require(invoke['target']['name']['kind'] == 'read'
            and invoke['target']['name']['place']['object'] == objects['WS-PGM'], 'dynamic CALL reads final receiver')
    return unit, call, assigns, objects


def dependency_oracle(result, model, source, case):
    unit, call, assigns, objects = model
    names = ['PROGA']
    require(len(result['sites']) == 1 and len(result['edges']) == len(names), 'one CALL and exact candidate edges')
    site = result['sites'][0]
    require(site['caller'] == unit['id'] and site['entry'] == unit['entries'][0]['id'], 'CALLER identity')
    require(site['operation'] == call['terminator']['header']['id'] and site['sequence'] == call['label']
            and site['offset'] == 0, 'real Invoke location after all MOVEs')
    require(site['valuePoint']['position'] == 'BEFORE' and site['valuePoint']['operationId'] == site['operation'], 'BEFORE Invoke')
    require(site['targetKind'] == 'COMPUTED' and site['reachability'] == 'REACHABLE' and site['subject'] == objects['WS-PGM'], 'computed reachable WS-PGM')
    open_call_model(call['terminator'], site)
    require(not site['openControlRemainder'], 'supported linear sequence stays closed')
    require([c['referenceName'] for c in site['candidates']] == names, 'independent source-control candidate set')
    require([c['rawValue'] for c in site['rawCandidates']] == [n.ljust(8) for n in names]
            and [c['rawValue'] for c in site['candidates']] == [n.ljust(8) for n in names], 'padding retained through interpretation')
    require(site['sourceValueRemainder'] and site['interpretationUnknownRemainder']
            and site['effectiveUnknownRemainder'], 'real source independent open dimensions')
    for index,name in enumerate(names):
        candidate=site['candidates'][index];support=candidate['supports']
        producer_index = max(i for i, (_, _, target) in enumerate(CASES[case]) if target == 'WS-PGM')
        producer = assigns[producer_index]
        require(len(support)==1 and support[0]['kind']=='VALUE_PRODUCER'
                and support[0]['producer']==producer['header']['id'] and support[0]['origin']==producer['header']['origin'], 'exact final FitText copy produces the supported snapshot')
        require(site['rawCandidates'][index]['supports']==support and result['edges'][index]['candidate']==candidate
                and result['edges'][index]['caller']==site['caller'] and result['edges'][index]['site']==site['operation'], 'edge and raw/interpreted support consistency')
        spans=source_spans(result,support[0],source)
        copy_source = CASES[case][producer_index][1]
        literal_line = next(i for i, line in enumerate(source.read_text().splitlines(), 1)
                            if 'MOVE ' + copy_source + ' TO WS-PGM' in line)
        require(all(int(span['startLine']) == literal_line == int(span['endLine']) for span in spans), 'support reaches exact transforming MOVE occurrence')
    require(result['metrics']['possibleValuesRuns'] == 1, 'normal forward fixed point runs once')


def run(work, config_path):
    require_local(); work.mkdir(parents=True, exist_ok=False)
    producer = config_path.parent; config = json.loads(config_path.read_text())
    lock = json.loads((ROOT / 'docs/sources/sources.lock.json').read_text())
    for name, key in (('air-java', 'air_java'), ('proleap-poc', 'proleap_poc'), ('cobol-lower', 'cobol_lower')):
        pin = lock[key].get('commit', lock[key].get('main_commit'))
        require(config['sources'][name] == pin == git(producer / name, 'rev-parse', 'HEAD')
                and not git(producer / name, 'status', '--porcelain'), 'exact clean producer pin: ' + name)
    require(config['semanticProductVersion'] == lock['proleap_poc']['semantic_product_version'], 'locked SP version')
    cp = runtime(producer)
    for case in CASES:
        cwd = work / case; cwd.mkdir(); source = cwd / (case + '.cbl')
        shutil.copyfile(FIXTURES / source.name, source)
        web = cwd / 'src/main/resources'; web.mkdir(parents=True)
        (web / 'web').symlink_to(producer / 'proleap-poc/src/main/resources/web', target_is_directory=True)
        execute(cwd, 'frontend', ['java', '-cp', os.pathsep.join(config['frontend']['classpath']), config['frontend']['main'],
                '--source', source.name, '--copybooks', str(producer / 'proleap-poc/corpus/cpy'), '--output', str(cwd / 'sp')])
        sp = cwd / 'sp/cobol-semantic-product.json'
        data, moves = source_oracle(json.loads(sp.read_text()), case, config['semanticProductVersion'])
        air = cwd / 'program.air.json'
        execute(cwd, 'lower', ['java', '-cp', os.pathsep.join(config['lower']['classpath']), config['lower']['main'], str(sp), str(air)])
        model = air_oracle(json.loads(air.read_text()), data, moves, case)
        cfg = cwd / 'cfg.json'; dep = cwd / 'dependencies.json'
        execute(cwd, 'cfg', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg', str(air), str(cfg)])
        contract = verify_cfg_wire(cfg.read_bytes()); require('INVOKE_NORMAL' in contract['transitions'], 'real CFG continuation')
        execute(cwd, 'dependency', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(air), str(dep)])
        dependency_oracle(read(dep), model, source, case)
        print('PASS real ' + case + ': exact candidates, snapshot, copies and original supports', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--producers', required=True, type=Path)
    args = parser.parse_args()
    try:
        run(args.work.resolve(), args.producers.resolve())
    except (ValueError, RuntimeError, OSError) as error:
        print('FAIL: ' + str(error), file=sys.stderr); sys.exit(1)
