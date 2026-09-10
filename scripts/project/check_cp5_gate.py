#!/usr/bin/env python3
"""Readiness route for future CP5 product gates; absent runtime is exit 3, never PASS."""
from __future__ import annotations
import argparse
import sys
import subprocess
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'harness'))
from validate_docs import ROOT, load_json
from validate_cp5 import validate_cp5


def run(root: Path, category: str, wave: int) -> int:
    errors = validate_cp5(root)
    if errors:
        for error in errors: print('[cp5-' + category + '] FAIL: ' + error, file=sys.stderr)
        return 1
    plan = load_json(root / 'docs/evals/cp5/gate-plan.json')
    gate = plan['waves'][wave-1]['gates'].get(category)
    if wave in (1,2,3,4) and gate is not None:
        return subprocess.run([sys.executable,str(root/f'scripts/project/check_w{wave}.py'),category,'--root',str(root)],cwd=root).returncode
    if gate is None:
        print(f'[cp5-{category}] NOT_APPLICABLE_YET: category not due in Wave {wave} (exit 3)')
    else:
        print(f'[cp5-{category}] NOT_AVAILABLE_UNTIL_IMPLEMENTED: Wave {wave} runtime hook absent (exit 3)')
    # Preparation intentionally cannot run future hooks. Activation requires a reviewed
    # Wave change adding nominal report validation, actual execution and challenges.
    return 3


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('category', choices=['architecture','semantic','performance','integration'])
    parser.add_argument('--wave', type=int, choices=range(1,6), required=True)
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    return run(args.root.resolve(), args.category, args.wave)

if __name__ == '__main__':
    sys.exit(main())
