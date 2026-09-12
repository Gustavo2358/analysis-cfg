#!/usr/bin/env python3
"""Compatibility CLI: PASS/FAIL with optional local cache selection."""
import argparse
import os
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'harness'))
from lean import execute, require_local
from lean_project import full_local
ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('profile', choices=['FAST_CI', 'LOCAL_QUALIFICATION'])
    parser.add_argument('--work', type=Path)
    parser.add_argument('--maven-repo', type=Path)
    args = parser.parse_args()
    if args.work:
        os.environ['CFG_BUILD_ROOT'] = str(args.work.resolve())
    if args.maven_repo:
        os.environ['MAVEN_OPTS'] = os.environ.get('MAVEN_OPTS', '') + ' -Dmaven.repo.local=' + str(args.maven_repo.resolve())
    if args.profile == 'FAST_CI':
        execute('CODE_CHANGE', ROOT)
    else:
        require_local()
        full_local(ROOT)
    print('PASS')
    return 0

if __name__ == '__main__':
    sys.exit(main())
