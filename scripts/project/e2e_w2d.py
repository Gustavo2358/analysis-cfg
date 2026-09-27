#!/usr/bin/env python3
"""Real W2D twice, with public wire/source oracles. No solver internals or path enumeration."""
import argparse
import json
import os
import re
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


def locked_sp(sp):
    version=json.loads((ROOT/'docs/sources/sources.lock.json').read_text())['proleap_poc']['semantic_product_version']
    # The exact producer SHA is checked by run(). Its lock supplies the ceiling;
    # feature-selected historical publications can be older than that ceiling.
    def parsed(value):
        require(isinstance(value, str) and re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", value),
                'three-part SP contract version')
        return tuple(map(int, value.split('.')))
    ceiling = parsed(version)
    require(ceiling[0] == 2 and ceiling >= (2, 31, 0)
            and sp.get('sourceDependencies') is not None, 'locked source-dependency generation')
    require(not any(s.get('copySemantics') == 'POSSIBLE_TEXT' or s['variant'].startswith('CICS')
                    for s in sp['statements']), 'fixture stays within scoped source-dependency evidence')
    published = parsed(sp['contractVersion'])
    floor = (2, 31, 0)
    if sp.get('storage', {}).get('logicalExactViews'):
        floor = (2, 35, 0)
    if any(s.get('publicationKind') == 'STRUCTURAL_FACTS' for s in sp['statements']):
        floor = max(floor, (2, 36, 0))
    if sp.get('ordinaryContinuations'):
        floor = max(floor, (2, 37, 0))
    if any(s.get('condition', {}).get('textPredicate') for s in sp['statements']):
        floor = max(floor, (2, 46, 0))
    if any(s.get('logicalTransfers') for s in sp['statements']):
        floor = max(floor, (2, 38, 0))
    require(floor <= published <= ceiling, 'published SP contract covers fixture features')


def text_leaf(expression):
    """Verify the public logical fitting envelope and retain the original operand."""
    while expression['kind'] == 'fit_text':
        require(int(expression['length']) > 0 and expression['pad'] == ' ', 'supported space-padded logical fit')
        require(expression['header']['role'] == 'VALUE_READ', 'fit retains value-read role')
        expression = expression['value']
    return expression


def literal_text(expression):
    if expression['kind'] == 'fit_text':
        text_leaf(expression)  # Check each public operation, never skip an unknown transform.
        length = int(expression['length'])
        return literal_text(expression['value'])[:length].ljust(length)
    require(expression['kind'] == 'literal' and expression['value']['kind'] == 'text', 'actual TEXT literal')
    return expression['value']['value']


def open_call_model(invoke, site, *, model_open=False, normal_continuation=True):
    # W1: missing external implementation does not publish memory/control effects.
    # Value openness is asserted independently from each fixture's BEFORE path.
    if normal_continuation:
        require(invoke['outcomes']['remainder']['kind'] == 'none', 'modeled normal CALL has closed outcomes')
        require(len(invoke['outcomes']['known']) == 1 and invoke['outcomes']['known'][0]['kind'] == 'normal',
                'preserve modeled normal continuation')
    else:
        # Explicit W2/M13 boundary: no materialized completion is still an open frontier.
        require(not invoke['outcomes']['known'] and invoke['outcomes']['remainder']['kind'] == 'within'
                and invoke['outcomes']['remainder']['scope']['kind'] == 'all', 'unmodeled completion remains explicit')
    effect = invoke['effectBound']['otherwise']
    require(effect['reads']['kind'] == 'none' and effect['writes']['kind'] == 'none'
            and not effect['mustOverwrite'], 'missing external body creates no memory effect')
    require(site['modelValueRemainder'] is model_open, 'BEFORE target openness follows supported path')


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
        if origin['kind'].upper() == 'DERIVED':
            pending.extend(o['localId'] for o in origin['inputs'])
        elif origin['kind'].upper() == 'WRITTEN' and artifacts[origin['artifact']['localId']] == source.name:
            spans.append(origin['location'])
    require(bool(spans), 'candidate must reach original COBOL span')
    return spans


def declaration_object(publication, identity):
    # The final component is the producer's source identity. Profile namespaces
    # are allowed to change; an object must still have exactly one source owner.
    items = [i for i in publication['coverage']['items']
             if i['sourceKey'].rsplit('/', 1)[-1] == identity
             and any(o['domain'] == 'object' for o in i['outputs'])]
    require(len(items) == 1, 'one declaration-owned object: ' + identity)
    outputs = [o for o in items[0]['outputs'] if o['domain'] == 'object']
    require(len(outputs) == 1, 'one object output per declaration: ' + identity)
    return outputs[0]


def statement_operation(publication, operations, identity, kind):
    outputs = [o for i in publication['coverage']['items']
               if i['sourceKey'].rsplit('/', 1)[-1] == identity
               for o in i['outputs'] if o['domain'] == 'operation']
    require(len({o['localId'] for o in outputs}) == len(outputs), 'unique coverage outputs')
    linked = [operations[o['localId']] for o in outputs]
    principal = [record for record in linked if record[2]['kind'] == kind]
    require(len(principal) == 1, 'one typed source operation: ' + identity)
    seq, offset, op = principal[0]
    extras = [record for record in linked if record is not principal[0]]
    # A MOVE publishes its Assign and explicit completion Jump. No arbitrary
    # extra operation or extra execution context is accepted by this oracle.
    require(not extras or (kind == 'assign' and len(extras) == 1
            and extras[0][0] == seq and extras[0][1] == len(seq['instructions'])
            and extras[0][2] == seq['terminator'] and extras[0][2]['kind'] == 'jump'),
            'only the source MOVE completion may accompany its Assign')
    return principal[0]


def text_equality_predicate(predicate, subject, value, extent):
    require(predicate['kind'] == 'binary' and predicate['operator'] == 'eq'
            and predicate['header']['role'] == 'PREDICATE', 'typed text equality predicate')
    for side in ('left', 'right'):
        require(predicate[side]['kind'] == 'fit_text' and int(predicate[side]['length']) == extent,
                'comparison uses declared logical extent')
    left = text_leaf(predicate['left'])
    require(left['kind'] == 'read' and left['place']['kind'] == 'object'
            and left['place']['object'] == subject, 'predicate reads the source condition subject')
    require(literal_text(predicate['right']) == value, 'predicate preserves the source literal')


def air_oracle(air, opened, semantic):
    p = air['publication']; require(air['airVersion'] == '2.0.0', 'AIR version')
    require(len(p['units']) == 1, 'one caller unit')
    unit = p['units'][0]; seq = unit['sequences']
    require(len(seq) == (4 if opened else 5), 'diamond sequences without artificial block')
    by_label = {s['label']['localId']: s for s in seq}
    branch = next(s for s in seq if s['terminator']['kind'] == 'branch')
    call = next(s for s in seq if s['terminator']['kind'] == 'invoke')
    ret = next(s for s in seq if s['terminator']['kind'] == 'return')
    require(unit['entries'][0]['initialLabel'] == branch['label'], 'explicit AIR entry is branch')
    b = branch['terminator']
    flag = next(d for d in semantic['dataDeclarations'] if d['canonicalName'] == 'FLAG')
    subject = declaration_object(p, flag['id'])
    text_equality_predicate(b['predicate'], subject, 'Y', flag['scalarText']['logicalExtent'])
    yes = by_label[b['trueDestination']['localId']]; no = by_label[b['falseDestination']['localId']]
    require(yes['terminator']['kind'] == 'jump' and yes['terminator']['destination'] == call['label'], 'true jumps to join')
    require(no == call if opened else no['terminator']['kind'] == 'jump' and no['terminator']['destination'] == call['label'], 'false open bypass / closed assignment')
    require(not branch['instructions'] and not call['instructions'] and not ret['instructions'], 'no invented seed/assignment')
    assignments = [i for s in seq for i in s['instructions']]
    require(all(i['kind'] == 'assign' for i in assignments), 'only actual assignments, no havoc')
    expected = ['PROGA   '] if opened else ['PROGA   ', 'PROGB   ']
    require(sorted(literal_text(i['value']) for i in assignments) == expected, 'exact padded AIR assignments')
    require(literal_text(yes['instructions'][0]['value']) == 'PROGA   ', 'true arm assignment')
    if not opened:
        require(literal_text(no['instructions'][0]['value']) == 'PROGB   ', 'false arm assignment')
    invoke = call['terminator']
    require(invoke['outcomes']['known'] == [{'kind': 'normal', 'label': ret['label']}], 'normal Invoke continuation')
    return unit, call, assignments


def dependency_oracle(result, air, source, opened, semantic):
    unit, call, assignments = air_oracle(air, opened, semantic)
    require(len(result['sites']) == 1, 'one CALL site')
    site = result['sites'][0]; invoke = call['terminator']
    require(site['caller'] == unit['id'] and site['entry'] == unit['entries'][0]['id'], 'caller/entry identity')
    require(site['operation'] == invoke['header']['id'] and site['sequence'] == call['label'] and site['offset'] == 0, 'join Invoke identity')
    require(site['valuePoint']['position'] == 'BEFORE' and site['valuePoint']['operationId'] == site['operation'], 'BEFORE Invoke observation')
    require(site['targetKind'] == 'COMPUTED' and site['reachability'] == 'REACHABLE', 'reachable dynamic CALL')
    open_call_model(invoke, site, model_open=opened)
    # These are independent existing source/name-policy dimensions, not inferred from model closure.
    require(site['sourceValueRemainder'] and site['interpretationUnknownRemainder'] and site['effectiveUnknownRemainder'], 'preserve source/interpretation/effective remainders')
    require(not site['openControlRemainder'], 'supported diamond and normal continuation stay closed')
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
        assign = next(a for a in assignments if literal_text(a['value']) == raw[index])
        require(support['kind'] == 'VALUE_PRODUCER' and support['producer'] == assign['header']['id'] and support['origin'] == assign['header']['origin'], 'candidate supports only its own Assign')
        require(site['subject'] == assign['destination']['object'] == invoke['target']['name']['place']['object'], 'real WS-PGM object identity')
        line = 11 if name == 'PROGA' else 13
        spans = source_spans(result, support, source)
        require(all(int(s['startLine']) == line == int(s['endLine']) for s in spans), 'support must not cross source arms')
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
            require(config['semanticProductVersion'] == lock['proleap_poc']['semantic_product_version'], 'locked producer contract generation')
            locked_sp(semantic)
            require(semantic['unit']['canonicalProgramName'] == 'CALLER', 'real caller program identity')
            air = cwd / 'program.air.json'
            execute(cwd, 'lower', ['java', '-cp', os.pathsep.join(config['lower']['classpath']), config['lower']['main'], str(sp), str(air)])
            publication = json.loads(air.read_text()); air_oracle(publication, name == 'open', semantic)
            cp = runtime(producer); cfg = cwd / 'cfg.json'; dep = cwd / 'dependencies.json'
            execute(cwd, 'cfg', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg', str(air), str(cfg)])
            contract = verify_cfg_wire(cfg.read_bytes())
            require({'BRANCH_TRUE', 'BRANCH_FALSE', 'JUMP', 'INVOKE_NORMAL'} <= set(contract['transitions']), 'real CFG labeled diamond')
            execute(cwd, 'dependency', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(air), str(dep)])
            result = read(dep); dependency_oracle(result, publication, source, name == 'open', semantic)
            # Public transport metamorphism: change physical order only, keep every identity/destination.
            for unit in publication['publication']['units']:
                unit['sequences'].reverse()
            permuted = cwd / 'permuted.air.json'; permuted.write_text(json.dumps(publication))
            permdep = cwd / 'permuted.dependencies.json'
            execute(cwd, 'permuted', ['java', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(permuted), str(permdep)])
            other = read(permdep); dependency_oracle(other, publication, source, name == 'open', semantic)
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
