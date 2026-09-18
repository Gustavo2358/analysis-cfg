#!/usr/bin/env python3
"""Selected W3 sources, pinned producers, real CLIs; no intermediate product edits."""
import argparse
import json
import os
from pathlib import Path
import shutil
from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import execute, locked_sp, source_spans
from prepare_w2d_producers import ROOT, git, require_local
from probe_analysis_gaps_w2 import PROFILE, operations_for
from e2e_cics_files import oracle as file_oracle


def cases():
    fixtures = ROOT / 'analysis-adapters/src/test/resources'
    selected = {p.stem: p for p in sorted((fixtures / 'analysis-gaps/w3').glob('*.cbl'))}
    selected['native-static'] = fixtures / 'file-dependencies/w2/static.cbl'
    for name in ('read-dataset-literal', 'read-dataset-computed', 'read-file-computed', 'computed-unknown'):
        selected[name] = fixtures / ('file-dependencies/w8/' + name + '.cbl')
    return selected


def oracle(name, source, sp, air, cfg, result):
    locked_sp(sp); verify_cfg_wire(json.dumps(cfg).encode())
    require(sp['storage']['profileId'] == PROFILE, 'explicit storage profile')
    if name.startswith(('link-', 'xctl-')):
        fact, = [s for s in sp['statements'] if s['variant'] == 'CICS_PROGRAM_CONTROL']
        operation, = operations_for(air['publication'], fact['header']['id'])
        site, = result['sites']; require(not result['fileDependencies']['sites'], 'program is not FILE')
        expected = [] if name == 'link-unknown' else ['PROGA', 'PROGB'] if name == 'link-multiple' else ['PROGA']
        require([c['referenceName'] for c in site['candidates']] == expected, 'independent target set')
        command = name.split('-')[0].upper()
        require(site['command'] == command == fact['command'] and site['technology'] == 'CICS'
                and site['namespace'] == 'cics.program' and site['nameProfile'] == 'cics-ts.program@1', 'program identity')
        require(operation['kind'] == 'invoke' and operation['action'] == ('call' if command == 'LINK' else 'execute'), 'typed AIR action')
        require(site['targetStatus'] == ('OPEN_TARGET' if not expected else 'RESOLVED_CANDIDATES'), 'source target status')
        if command == 'XCTL': require(not operation['outcomes']['known'], 'no normal success return invented')
        else: require(any(o['kind'] == 'normal' for o in operation['outcomes']['known']), 'LINK continuation')
        if name.endswith('literal'):
            require(site['targetKind'] == 'LITERAL' and all(c['supports'][0]['kind'] == 'CICS_LITERAL' for c in site['candidates']), 'literal evidence')
        else:
            require(site['valuePoint']['position'] == 'BEFORE' and operation['target']['name']['kind'] == 'read', 'plain computed query')
            if not expected: require(site['modelValueRemainder'] and site['effectiveUnknownRemainder'], 'uninitialized remains unknown')
            moves = {s['source']['logicalValue']['value']: s for s in sp['statements'] if s['variant'] == 'MOVE'}
            for candidate in site['candidates']:
                producer, = operations_for(air['publication'], moves[candidate['referenceName']]['header']['id'])
                support, = candidate['supports']; require(support['producer'] == producer['header']['id'] and support['origin'] == producer['header']['origin'], 'source MOVE support association')
        for candidate in site['candidates']: source_spans(result, candidate['supports'][0], source)
        return dict(command=command, candidates=expected, status=site['targetStatus'], modelOpen=site['modelValueRemainder'], sourceOpen=site['sourceValueRemainder'], effectiveOpen=site['effectiveUnknownRemainder'])
    files = result['fileDependencies']
    if name.startswith('native-'):
        declaration, = files['declarations']; sp_decl, = sp['fileInventory']['declarations']
        require(declaration['logicalFile'] == 'F' and declaration['namespace'] == 'cobol.external-file-name', 'native identity')
        require(sorted(s['action'] for s in files['sites']) == ['close', 'open', 'read'], 'native occurrence inventory')
        if name == 'native-static':
            require(declaration['name'] == 'CLIENTDD' and declaration['sourceKind'] == 'ASSIGNMENT_NAME', 'source external name, not allocation')
            for site in files['sites']:
                require([c['referenceName'] for c in site['candidates']] == ['CLIENTDD'] and not site['unknownRemainder'], 'exact native name')
                require(site['bindings'][0]['declaration'] == declaration['id'], 'resource identity linkage')
                source_spans(result, site['candidates'][0]['supports'][0], source)
        else:
            assignment = sp_decl['assignment']
            require(assignment['availability'] == 'UNAVAILABLE' and assignment['externalFileName'] is None
                    and 'ASSIGN_OUTSIDE_N_LR' in assignment['gapCodes'], 'first boundary is frontend profile admission')
            require(declaration['name'] is None and declaration['targetKind'] == 'UNKNOWN', 'no guessed dynamic external name')
            require(all(not s['candidates'] and s['unknownRemainder'] for s in files['sites']), 'native unknown conservative')
        require(files['metrics']['possibleValuesPreparations'] == 0, 'native FILE does not query values')
    else:
        file_oracle(name, sp, air, result, source)
    return dict(namespace=[s['namespace'] for s in files['sites']], candidates=[[c['referenceName'] for c in s['candidates']] for s in files['sites']], unknown=[s['unknownRemainder'] for s in files['sites']])


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
    for name, source in cases().items():
        cwd = work / name; cwd.mkdir(); shutil.copyfile(source, cwd / source.name)
        web = cwd / 'src/main/resources'; web.mkdir(parents=True)
        (web / 'web').symlink_to(producer / 'proleap-poc/src/main/resources/web', target_is_directory=True)
        execute(cwd, 'frontend', ['java', '-cp', os.pathsep.join(config['frontend']['classpath']), config['frontend']['main'],
                '--source', source.name, '--copybooks', str(producer / 'proleap-poc/corpus/cpy'), '--output', str(cwd / 'sp'), '--storage-profile', PROFILE, '--cics-entry-mode', 'unknown'])
        sp = cwd / 'sp/cobol-semantic-product.json'; air = cwd / 'program.air.json'
        execute(cwd, 'lower', ['java', '-cp', os.pathsep.join(config['lower']['classpath']), config['lower']['main'], str(sp), str(air)])
        for stage, main in [('cfg', 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg'),
                           ('dependency', 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies')]:
            execute(cwd, stage, ['java', '-cp', cp, main, str(air), str(cwd / (stage + '.json'))])
        summary[name] = oracle(name, source, json.loads(sp.read_text()), json.loads(air.read_text()),
                               json.loads((cwd / 'cfg.json').read_text()), read(cwd / 'dependency.json'))
        print('PASS W3 source ' + name + ': ' + json.dumps(summary[name]), flush=True)
    (work / 'summary.json').write_text(json.dumps(dict(sources=config['sources'], cases=summary), indent=2) + '\n')
    print('PASS W3 source probe: ' + json.dumps(summary, sort_keys=True))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--producers', required=True, type=Path)
    args = parser.parse_args()
    run(args.work.resolve(), args.producers.resolve())
