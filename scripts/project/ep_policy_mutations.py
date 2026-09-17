#!/usr/bin/env python3
"""Bounded EP policy mutations; semantic assertion failures are the oracle.

Run against a quiescent worktree. Each patch is restored in finally. Evidence is
written to a new directory; no production flag or mutant remains installed.
"""
import argparse
import difflib
import hashlib
import json
import subprocess
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
VALUES = Path('analysis-values/src/main/java/io/github/gustavo2358/analysis/values')
REGIONAL = VALUES / 'RegionalValuesAnalysis.java'
MUTANTS = [
    ('occurrence-hides-logical', Path('analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/StorageIndex.java'),
     'return explicitObjects(place);', 'return List.of();', 'LogicalPlaceRepresentationTest'),
    ('choice-hides-logical', Path('analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/StorageIndex.java'),
     'else if(place instanceof Places.Choice choice)pending.addAll(choice.candidates());', '', 'LogicalPlaceRepresentationTest'),

    ('unproved-precondition-kill', Path('analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/StatementEffects.java'),
     'if(storage.session().index().unprovedPreconditions(operation.header().id()))strength=Strength.MAY;', '', 'EvidenceMonotonicityTest'),
    ('unknown-clear', VALUES / 'PossibleValuesState.java',
     'store(cell,value(cell,w).withOpen(w),w)', 'store(cell,Candidates.UNKNOWN,w)',
     'ValuesTest'),
    ('may-kill', REGIONAL, 'return weakUnion(current,replace(current,captured,plan,logicalInputs));',
     'return replace(current,captured,plan,logicalInputs);', 'EvidencePreservingPolicyTest'),
    ('unproved-alias-kill', REGIONAL,
     'if(!plan.target.sourceApplicable())current=weak(current,captured,plan,logicalInputs);',
     'if(!plan.target.sourceApplicable())current=replace(current,captured,plan,logicalInputs);',
     'EvidencePreservingPolicyTest'),
    ('query-reseed', REGIONAL,
     'subject.apply(query.subject())),state));}',
     'subject.apply(query.subject())),engine.apply(state,initial.getOrDefault(state.entry,List.of()),false)));}',
     'EvidencePreservingPolicyTest'),
    ('unknown-storage-empty', REGIONAL,
     'for(var value:state.logical.getOrDefault(object,Set.of()))',
     'for(var value:Set.<LogicalValue>of())', 'EvidencePreservingPolicyTest'),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--maven-repo', type=Path, required=True)
    parser.add_argument('--only', nargs='+', choices=[m[0] for m in MUTANTS])
    args = parser.parse_args()
    args.evidence_dir.mkdir(parents=True, exist_ok=False)
    records = []
    baseline_oracles = set()
    for name, relative, old, new, oracle in MUTANTS:
        if args.only and name not in args.only: continue
        path = ROOT / relative
        original = path.read_text()
        if original.count(old) != 1:
            raise RuntimeError(f'{name}: mutation site changed; refusing ambiguous patch')
        changed = original.replace(old, new)
        (args.evidence_dir / (name + '.patch')).write_text(''.join(difflib.unified_diff(
            original.splitlines(True), changed.splitlines(True), str(relative), str(relative))))
        module = 'analysis-adapters' if oracle == 'EvidenceMonotonicityTest' else 'analysis-values'
        command = ['mvn', '-o', '-B', '-ntp', f'-Dmaven.repo.local={args.maven_repo}',
                   '-pl', module, '-am',
                   f'-Dtest=CfgPreflightTest,StorageRangeTest,ValuesTest,RegionalAnalysisTest,NameInterpreterTest,{oracle}', 'test']
        if oracle not in baseline_oracles:
            with (args.evidence_dir / (oracle + '-baseline.log')).open('x') as log:
                baseline = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            if baseline.returncode != 0:
                raise RuntimeError(f'{oracle}: unmutated baseline failed; no mutation is qualified')
            baseline_oracles.add(oracle)
        oracle_class = oracle.split('#')[0]
        reports = ROOT / module / 'target/surefire-reports'
        for report in reports.glob(f'TEST-*.{oracle_class}.xml'):
            report.unlink()
        try:
            path.write_text(changed)
            with (args.evidence_dir / (name + '.log')).open('x') as log:
                run = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            failures, errors, executed = [], [], 0
            for report in reports.glob(f'TEST-*.{oracle_class}.xml'):
                xml = ET.parse(report).getroot()
                executed += int(xml.attrib['tests'])
                for case in xml.findall('testcase'):
                    for failure in case.findall('failure'):
                        failures.append({'test': case.attrib['name'], 'type': failure.attrib.get('type'),
                                         'message': failure.attrib.get('message')})
                    errors.extend(e.attrib for e in case.findall('error'))
                (args.evidence_dir / (name + '-' + report.name)).write_bytes(report.read_bytes())
            killed = run.returncode != 0 and executed > 0 and bool(failures) and not errors
            records.append({'mutant': name, 'path': str(relative), 'command': command,
                            'original_sha256': hashlib.sha256(original.encode()).hexdigest(),
                            'exit_code': run.returncode, 'executed': executed, 'failures': failures,
                            'errors': errors, 'status': 'KILLED' if killed else 'NOT_QUALIFIED'})
            (args.evidence_dir / 'results.json').write_text(json.dumps(records, indent=2) + '\n')
            print(name, records[-1]['status'], flush=True)
            if not killed:
                raise RuntimeError(f'{name}: requires a semantic assertion failure, not a setup failure')
        finally:
            path.write_text(original)
            with (args.evidence_dir / (name + '-restored.log')).open('x') as log:
                restored = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            if records and records[-1]['mutant'] == name:
                records[-1]['restored_exit_code'] = restored.returncode
                (args.evidence_dir / 'results.json').write_text(json.dumps(records, indent=2) + '\n')
            if restored.returncode != 0:
                raise RuntimeError(f'{name}: source restored but unmutated rebuild failed')
    print(f'PASS: {len(records)} policy mutants killed; source restored and unmutated rebuilds passed')


if __name__ == '__main__':
    main()
