#!/usr/bin/env python3
"""Orthogonal AIR uncertainty records cannot change executable work or values."""
import argparse
import copy
import hashlib
import json
import pathlib
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime', type=pathlib.Path, required=True)
    parser.add_argument('--air', type=pathlib.Path, required=True)
    parser.add_argument('--out', type=pathlib.Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    runtime = json.loads(args.runtime.read_text())
    original = json.loads(args.air.read_text())
    template = original['publication']['uncertainties'][0]
    rows = []
    for count in (0, 1, 50):
        air = copy.deepcopy(original)
        for ordinal in range(count):
            diagnostic = copy.deepcopy(template)
            diagnostic['id']['localId'] = hashlib.sha256(f'w3-r1-orthogonal-{ordinal}'.encode()).hexdigest()[:32]
            diagnostic['code'] = 'W3_R1_ORTHOGONAL_DIAGNOSTIC'
            diagnostic['reason'] = 'Diagnostic record only; no executable edge or binding'
            air['publication']['uncertainties'].append(diagnostic)
        path = args.out / f'air-{count}.json'
        path.write_text(json.dumps(air, indent=2) + '\n')
        row = {'addedDiagnostics': count}
        for mode in ('logical', 'physical'):
            output = args.out / f'{mode}-{count}.json'
            command = runtime['commands']['dependency'] + [str(path), str(output)]
            if mode == 'physical':
                command.append('--experimental-physical')
            result = subprocess.run(command, cwd=runtime['frontendCheckout'], capture_output=True, text=True, timeout=120)
            (args.out / f'{mode}-{count}.log').write_text(result.stdout + result.stderr)
            assert result.returncode == 0, f'{mode} {count} failed validation/analysis'
            data = json.loads(output.read_text())
            row[mode] = {'sites': data['sites'], 'metrics': data['metrics']}
        rows.append(row)
    for mode in ('logical', 'physical'):
        expected = rows[0][mode]
        for row in rows[1:]:
            assert row[mode] == expected, f'orthogonal diagnostics changed {mode} semantics/work at {row["addedDiagnostics"]}'
    summary = {'counts': [r['addedDiagnostics'] for r in rows],
               'logicalMetrics': rows[0]['logical']['metrics'], 'physicalMetrics': rows[0]['physical']['metrics']}
    (args.out / 'result.json').write_text(json.dumps(summary, indent=2) + '\n')
    print('W3_R1_DIAGNOSTIC_ISOLATION=PASS 0/1/50')


if __name__ == '__main__':
    main()
