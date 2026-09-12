#!/usr/bin/env python3
"""Fixed, reviewed PR unit/contract inventory. No path filtering or test discovery at execution."""
import json
import xml.etree.ElementTree as ET
from pathlib import Path

INVENTORY = 'docs/evals/cp6/fast-test-inventory.json'


def inventory(root):
    return json.loads((root / INVENTORY).read_text())


def selector(root):
    # Explicit methods also prevent a future heavy method silently joining a fast suite.
    return ','.join(name + '#' + '+'.join(methods)
                    for suites in inventory(root).values() for name, methods in suites.items())


def verify_reports(root):
    from check_integration import verify_suite
    from check_architecture import GateFailure
    total = 0
    for module, suites in inventory(root).items():
        reports = root / module / 'target/surefire-reports'
        if {p.name for p in reports.glob('TEST-*.xml')} != {'TEST-' + n + '.xml' for n in suites}:
            raise GateFailure('fast nominal suite inventory mismatch: ' + module)
        for name, methods in suites.items():
            verify_suite(ET.parse(reports / ('TEST-' + name + '.xml')).getroot(), name, set(methods))
            total += len(methods)
    print(f'[FAST_CI] PASS: {total} required unit/contract methods; zero skips', flush=True)
