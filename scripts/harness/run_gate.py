#!/usr/bin/env python3
"""Compatibility gate names for the lean technical workflow."""
import argparse
from pathlib import Path
import subprocess
import sys
from lean import execute, require_local
ROOT = Path(__file__).resolve().parents[2]

def run(gate, root):
    if gate in ('fast', 'docs', 'harness'):
        execute('CODE_CHANGE' if gate == 'fast' else 'DOCS_ONLY', root)
        return 0
    require_local()
    if gate == 'full':
        from lean_project import full_local
        full_local(root)
        return 0
    return subprocess.run(['bash', str(root / ('scripts/project/check-' + gate + '.sh'))], cwd=root).returncode

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('gate', choices=['fast','docs','harness','full','architecture','semantic','performance','integration'])
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    return run(args.gate, args.root)

if __name__ == '__main__':
    sys.exit(main())
