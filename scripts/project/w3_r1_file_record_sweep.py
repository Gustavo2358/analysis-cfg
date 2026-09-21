#!/usr/bin/env python3
"""Audit a validation-only W3-R1 sweep with one regenerated FILE publication.

The caller runs the pinned AirValidator over the mixed manifest first. This
script checks immutable bytes and compares validation statuses; it never runs
the producer, solver, query or physical canaries.
"""
import argparse
import hashlib
from pathlib import Path

CASE = ('logical-final', 'source-dependencies-w3--composition')
OLD = 'd62a73d0feca84e8bc6b4a9ed87e7021f55d550f200562446755ac232681af92'

def manifest(path):
    rows = {}
    for line in path.read_text().splitlines():
        cohort, case, digest, filename = line.split('\t', 3)
        key = (cohort, case)
        assert key not in rows, key
        assert hashlib.sha256(Path(filename).read_bytes()).hexdigest() == digest, key
        rows[key] = digest
    return rows

def results(path):
    rows = {}
    for line in path.read_text().splitlines():
        cohort, case, *status = line.split('\t')
        key = (cohort, case)
        assert key not in rows, key
        rows[key] = status
    return rows

def audit(old_manifest, old_results, mixed_manifest, mixed_results):
    original, mixed = manifest(old_manifest), manifest(mixed_manifest)
    before, after = results(old_results), results(mixed_results)
    assert set(original) == set(mixed) == set(before) == set(after)
    assert len(original) == 48 and sum(k[0] == 'logical-final' for k in original) == 40
    assert original[CASE] == OLD and mixed[CASE] != OLD
    assert before[CASE][0] == 'ERROR' and before[CASE][1].count('rule=I-13') == 5
    assert after[CASE][:2] == ['STRUCTURALLY_VALID', '20']
    for key in original:
        if key == CASE: continue
        assert original[key] == mixed[key] and before[key] == after[key], key
    assert all(row[0] != 'ERROR' for row in after.values())
    print('W3_R1_FILE_RECORD_SWEEP=PASS 39 frozen logical + 1 regenerated logical + 8 frozen physical')
    print('OLD_INVALID_AIR_SHA256=' + OLD)
    print('NEW_COMPOSITION_AIR_SHA256=' + mixed[CASE])

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('old_manifest', 'old_results', 'mixed_manifest', 'mixed_results'):
        parser.add_argument('--' + name.replace('_', '-'), type=Path, required=True)
    args = parser.parse_args()
    audit(args.old_manifest, args.old_results, args.mixed_manifest, args.mixed_results)
