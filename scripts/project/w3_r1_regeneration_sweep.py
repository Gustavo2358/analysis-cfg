#!/usr/bin/env python3
"""Regenerate frozen W3-R1 cases through final frontend/lower, without consumers.

Inputs and CLI flags come from the frozen results, not a reconstructed case list.
Writes new artifacts in a separate directory; never changes the frozen cohort.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(command, cwd, log):
    start = time.monotonic()
    with log.open('w') as output:
        result = subprocess.run(command, cwd=cwd, stdout=output,
                                stderr=subprocess.STDOUT, timeout=120)
    return {'exitCode': result.returncode, 'seconds': time.monotonic() - start,
            'command': command}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    runtime = json.loads(args.runtime.read_text())
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    manifest = []
    for cohort in ('logical-final', 'physical-final'):
        frozen = root / 'evidence/w3-r1' / cohort
        for previous in json.loads((frozen / 'results.json').read_text()):
            name = previous['case']
            if cohort == 'logical-final' and name == 'source-dependencies-w3--composition':
                continue  # already replaced and requalified in the FILE closure
            old = frozen / name / 'air.json'
            case = out / cohort / name
            case.mkdir(parents=True)
            original = previous['phases']['frontend']['command']
            tail = original[original.index('--source'):]
            assert tail.count('--source') == tail.count('--copybooks') == tail.count('--output') == 1
            source = Path(tail[tail.index('--source') + 1]).resolve()
            copybooks = Path(tail[tail.index('--copybooks') + 1]).resolve()
            assert source.is_file() and copybooks.is_dir(), (name, source, copybooks)
            sp = case / 'sp'
            tail[tail.index('--output') + 1] = str(sp)
            front = runtime['commands']['frontend'] + tail
            row = {'cohort': cohort, 'case': name, 'source': str(source),
                   'sourceSha256': sha(source), 'frozenAirSha256': sha(old),
                   'producerProfile': previous['producerProfile']}
            row['frontend'] = run(front, runtime['frontendCheckout'], case / 'frontend.log')
            if row['frontend']['exitCode'] == 0:
                sp_file = sp / 'cobol-semantic-product.json'
                row['spSha256'] = sha(sp_file)
                air = case / 'air.json'
                command = runtime['commands']['lower'] + [str(sp_file), str(air)]
                row['lower'] = run(command, runtime['frontendCheckout'], case / 'lower.log')
                if row['lower']['exitCode'] == 0:
                    row['airSha256'] = sha(air)
                    row['byteIdentical'] = row['airSha256'] == row['frozenAirSha256']
            manifest.append(row)
            (out / 'results.json').write_text(json.dumps(manifest, indent=2) + '\n')
            print(cohort, name, row.get('byteIdentical', 'FAILED'), flush=True)
    assert len(manifest) == 47, len(manifest)
    assert all(r['frontend']['exitCode'] == r.get('lower', {}).get('exitCode') == 0
               for r in manifest), 'producer/lower failure: inspect results.json and per-case logs'


if __name__ == '__main__':
    main()
