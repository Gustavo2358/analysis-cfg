#!/usr/bin/env python3
"""Selected EP-R2 AIR → dependency CLI oracles. Timeout is failure, never success."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
CASES = ['F1', *['F-order-' + x for x in ['012', '021', '102', '120', '201', '210']],
         'H2', 'F5', 'MUST', 'G-1', 'G-2', 'G-4', 'G-7', 'I', 'E1', 'E3']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--maven-repo', type=Path, required=True)
    parser.add_argument('--only', nargs='+', choices=CASES)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    source_files = [ROOT / p for p in [
        'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/RegionalValuesAnalysis.java',
        'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/FactorizedAlternatives.java',
        'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/EntryFacts.java',
        'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/rd/ReachingDefinitions.java']]
    metadata = {
        'revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'trackedStatus': subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True),
        'sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files},
        'pins': json.loads((ROOT / 'docs/sources/sources.lock.json').read_text()),
        'baseline': 'EP-LAB-01 selected CSV; reused, not rerun',
    }
    (args.output / 'execution-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    classes = args.output / 'classes'
    classes.mkdir()
    modules = ['cfg-kernel', 'analysis-kernel', 'analysis-values', 'cfg-adapters',
               'analysis-dataflow', 'analysis-dependencies', 'analysis-adapters', 'analysis-launcher']
    jars = [args.maven_repo / s for s in [
        'io/github/gustavo2358/air-java/0.1.0-SNAPSHOT/air-java-0.1.0-SNAPSHOT.jar',
        'io/github/gustavo2358/air-json/0.1.0-SNAPSHOT/air-json-0.1.0-SNAPSHOT.jar',
        'com/fasterxml/jackson/core/jackson-core/2.22.2/jackson-core-2.22.2.jar',
        'com/fasterxml/jackson/core/jackson-databind/2.22.2/jackson-databind-2.22.2.jar',
        'com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar']]
    for path in jars:
        if not path.is_file():
            raise RuntimeError('missing build prerequisite: ' + str(path))
    cp = ':'.join(map(str, [classes, *[ROOT / m / 'target/classes' for m in modules], *jars]))
    fixture = ROOT / 'analysis-values/src/test/java/io/github/gustavo2358/analysis/values/EpR2Fixtures.java'
    subprocess.run(['javac', '--release', '21', '-Xlint:all', '-Werror', '-cp', cp, '-d', str(classes),
                    str(fixture), str(ROOT / 'scripts/project/EpR2Probe.java')], check=True)
    records = []
    for case in args.only or CASES:
        out = args.output / case
        out.mkdir()
        command = ['java', '-Xmx768m', '-cp', cp, 'EpR2Probe', str(out), case]
        start = time.monotonic()
        with (out / 'run.log').open('x') as log:
            try:
                run = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=60)
                result = {'exitCode': run.returncode, 'status': 'EXIT'}
            except subprocess.TimeoutExpired:
                result = {'exitCode': None, 'status': 'TIMEOUT'}
        result.update(command=command, seconds=time.monotonic()-start, case=case)
        (out / 'execution.json').write_text(json.dumps(result, indent=2) + '\n')
        if result['status'] != 'EXIT' or result['exitCode'] != 0:
            raise RuntimeError(f'{case}: not qualified, see {out / "run.log"}')
        metrics = json.loads((out / 'metrics.json').read_text())
        if metrics.get('qualified') is not True:
            raise RuntimeError('missing semantic qualification')
        records.append(metrics)
        (args.output / 'metrics.json').write_text(json.dumps(records, indent=2) + '\n')
        print(case, 'QUALIFIED', flush=True)
    orders = [r['sites'] for r in records if r['case'].startswith('F-order-')]
    if orders and not all(s == orders[0] for s in orders):
        raise RuntimeError('permutation-dependent product/support')
    print(f'PASS {len(records)} selected synthetic verticals; real case NOT RUN')


if __name__ == '__main__':
    main()
