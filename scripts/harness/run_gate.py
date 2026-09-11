#!/usr/bin/env python3
"""Stable gate runner: absent product checks return UNAVAILABLE (exit 3)."""
from __future__ import annotations
import argparse
import subprocess
import sys
from pathlib import Path
from validate_docs import GATES, PRODUCT_GATES, ROOT, load_json, inside, validate


def run(gate: str, root: Path) -> int:
    base = root / 'scripts/harness'
    if gate == 'docs':
        return subprocess.run([sys.executable, str(base / 'validate_docs.py'), '--root', str(root)]).returncode
    if gate == 'harness':
        for suite in ('test_harness.py', 'test_cp5_harness.py', 'test_resource_limit_harness.py', '../project/test_result_wire.py', '../project/test_w5_capture.py', '../project/test_validation.py', '../project/test_cfg_wire_contract.py'):
            rc = subprocess.run([sys.executable, str(base / suite)]).returncode
            if rc:
                return rc
        return 0
    if gate == 'fast':
        for child in ('docs', 'harness'):
            rc = run(child, root)
            if rc:
                return rc
        print('[fast] PASS: documentation and harness tests only', flush=True)
        return 0
    state = load_json(root / 'docs/engineering/gate-state.json')
    if state.get('phase') != 'implementation':
        print('[' + gate + '] UNAVAILABLE: no authorized Java product/gate implementation (exit 3)', flush=True)
        return 3
    config_errors = validate(root)
    if config_errors:
        print('[' + gate + '] FAIL: harness/configuration preflight failed', file=sys.stderr)
        for issue in config_errors:
            print(issue, file=sys.stderr)
        return 1
    if gate == 'full':
        for child in ('fast', 'architecture', 'semantic', 'performance', 'integration'):
            rc = run(child, root)
            if rc:
                return rc
        print('[full] PASS', flush=True)
        return 0
    entry = state['product_gates'][gate]
    hook = entry.get('hook')
    if entry.get('status') != 'implemented' or not hook:
        print('[' + gate + '] UNAVAILABLE: product verification hook absent (exit 3)', flush=True)
        return 3
    path = root / hook
    if not hook.startswith('scripts/project/') or not inside(root, path) or not path.is_file():
        print('[' + gate + '] FAIL: unsafe/missing hook', file=sys.stderr)
        return 2
    # No shell eval or commands taken from user input. Hook is a reviewed local file.
    rc = subprocess.run(['bash', str(path)], cwd=root).returncode
    print('[' + gate + '] ' + ('PASS' if rc == 0 else 'FAIL') + ' (exit ' + str(rc) + ')', flush=True)
    return rc


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('gate', choices=sorted(GATES))
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        return run(args.gate, args.root.resolve())
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print('[' + args.gate + '] FAIL: ' + str(exc), file=sys.stderr)
        return 2

if __name__ == '__main__':
    sys.exit(main())
