#!/usr/bin/env python3
"""Remote orchestration: one Fast workflow, no qualification or artifact receipts."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'harness'))
from lean import workflow_errors

def verify(root):
    errors = workflow_errors(root)
    if errors:
        raise ValueError('\n'.join(errors))
    print('PASS: remote Fast only')

if __name__ == '__main__':
    verify(Path(__file__).resolve().parents[2])
