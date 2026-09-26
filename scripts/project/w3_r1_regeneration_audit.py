#!/usr/bin/env python3
"""Audit final-stack SP→AIR regeneration against immutable W3-R1 AIR evidence.

Executable comparison renames ObjectIds by distinct display name and StorageIds
by their positively published object membership. Diagnostic metadata is checked
separately; it is never used to repair or compare executable topology.
"""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path


DIAGNOSTIC_KEYS = {'origin', 'precision', 'coverage', 'origins', 'uncertainties'}
PADDING_VIEWS = {('SRC-A', 'SRC-REC'), ('DST-REC', 'WS-PGM'), ('FLAG',)}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text())


def objects(publication):
    result = {}
    for unit in publication['units']:
        for obj in unit['objects']:
            name = obj['displayName']
            assert name not in result, ('ambiguous object name', name)
            result[name] = obj
    return result


def cells(publication):
    members = defaultdict(list)
    for name, obj in objects(publication).items():
        binding = obj['storage']
        if binding['kind'] == 'cell':
            members[binding['storage']['localId']].append(name)
    return {storage['header']['id']['localId']: tuple(sorted(members[storage['header']['id']['localId']]))
            for storage in publication['storage'] if storage['kind'] == 'cell'}


def executable(air):
    publication = air['publication']
    names = {obj['id']['localId']: name for name, obj in objects(publication).items()}
    memberships = cells(publication)
    stores = {}
    for index, storage in enumerate(publication['storage']):
        sid = storage['header']['id']['localId']
        member = memberships.get(sid, ())
        stores[sid] = 'storage:' + storage['kind'] + ':' + '/'.join(member or (f'unbound-{index}',))

    def visit(value, key=''):
        if isinstance(value, dict):
            domain = value.get('domain')
            if domain == 'object' and 'localId' in value:
                return {'object': names[value['localId']]}
            if domain == 'storage' and 'localId' in value:
                return {'storage': stores[value['localId']]}
            if domain in ('origin', 'uncertainty') and 'localId' in value:
                return {'diagnostic': domain}
            return {k: visit(v, k) for k, v in value.items() if k not in DIAGNOSTIC_KEYS}
        if isinstance(value, list):
            result = [visit(v, key) for v in value]
            if key in ('objects', 'storage'):
                result.sort(key=lambda item: json.dumps(item, sort_keys=True))
            return result
        return value

    return visit(air)


def operations(publication):
    return {op['header']['id']['localId']: op
            for unit in publication['units'] for seq in unit['sequences']
            for op in seq['instructions'] + [seq['terminator']]}


def source_anchors(publication, reference):
    index = {origin['id']['localId']: origin for origin in publication['origins']}
    seen, result = set(), set()

    def visit(local_id):
        if local_id in seen:
            return
        seen.add(local_id)
        origin = index[local_id]
        if origin['kind'] == 'written':
            result.add((json.dumps(origin.get('artifact'), sort_keys=True),
                        json.dumps(origin.get('location'), sort_keys=True)))
        for edge in ('inputs', 'includes'):
            for child in origin.get(edge, ()):
                visit(child['localId'])

    visit(reference['localId'])
    return result


def storage_members(publication):
    return set(cells(publication).values())


def verify_padding(old, new, fresh_sp):
    assert storage_members(old) == {('SRC-REC',), ('SRC-A',), ('DST-REC',),
                                    ('WS-PGM',), ('FLAG',)}
    assert storage_members(new) == PADDING_VIEWS
    for storage in new['storage']:
        assert storage['kind'] == 'cell'
        assert storage['typeRef'] == {'kind': 'known', 'type': {'kind': 'text'}}
        assert storage['header']['lifetime'] == 'PERSISTENT'
        assert storage['header']['visibility'] == 'PRIVATE'
    views = fresh_sp['storage']['logicalExactViews']
    grouped = defaultdict(list)
    for view in views:
        grouped[view['representative']].append(int(view['length']))
    assert sorted(sorted(lengths) for lengths in grouped.values()) == [[1], [5, 5], [8, 8]]


def compare(old_air, new_air, fresh_sp, case):
    old, new = old_air['publication'], new_air['publication']
    old_objects, new_objects = objects(old), objects(new)
    assert old_objects.keys() == new_objects.keys(), (case, 'object inventory changed')
    old_ops, new_ops = operations(old), operations(new)
    assert old_ops.keys() == new_ops.keys(), (case, 'operation IDs changed')
    assert all(old_ops[oid]['header']['origin'] == new_ops[oid]['header']['origin']
               for oid in old_ops), (case, 'operation origin changed')
    assert old['premises'] == new['premises'], (case, 'premises changed')
    assert old['artifacts'] == new['artifacts'] and old['artifactRelations'] == new['artifactRelations']
    for name, obj in old_objects.items():
        previous = source_anchors(old, obj['origin'])
        current = source_anchors(new, new_objects[name]['origin'])
        assert previous <= current, (case, name, 'object lost source provenance')
        if case != 'logical--padding':
            assert previous == current, (case, name, 'unexplained source provenance')

    coverage = Counter()
    for name, obj in old_objects.items():
        newer = new_objects[name]
        assert {k: v['status'] for k, v in obj['precision'].items()} == {
            k: v['status'] for k, v in newer['precision'].items()}, (case, name, 'precision status')
        if obj['coverage'] != newer['coverage']:
            assert (obj['coverage'], newer['coverage']) == ('MODELED', 'ABSTRACTED')
            coverage['modeledToAbstracted'] += 1
    for oid, op in old_ops.items():
        assert op['header']['coverage'] == new_ops[oid]['header']['coverage'], (case, oid, 'operation coverage')
        assert {k: v['status'] for k, v in op['header']['precision'].items()} == {
            k: v['status'] for k, v in new_ops[oid]['header']['precision'].items()}, (case, oid, 'operation precision')

    old_uncertainty = {item['id']['localId'] for item in old['uncertainties']}
    assert old_uncertainty <= {item['id']['localId'] for item in new['uncertainties']}
    added = Counter(item['code'] for item in new['uncertainties']
                    if item['id']['localId'] not in old_uncertainty)
    assert set(added) <= {'cobol-lower:STORAGE_DECLARATION_UNKNOWN'}, (case, added)
    assert sum(added.values()) == coverage['modeledToAbstracted'], (case, added, coverage)

    a, b = executable(old_air), executable(new_air)
    if a == b:
        return 'EXECUTABLE_EQUIVALENT', coverage
    assert case == 'logical--padding', (case, 'unexpected executable AIR delta')
    verify_padding(old, new, fresh_sp)
    # Only the Cell topology may change; instructions, scopes, control and all
    # remaining publication facts must still compare equal after ID renaming.
    a['publication'].pop('storage')
    b['publication'].pop('storage')
    for unit in a['publication']['units']:
        for obj in unit['objects']:
            obj.pop('storage')
    for unit in b['publication']['units']:
        for obj in unit['objects']:
            obj.pop('storage')
    assert a == b, (case, 'non-storage executable delta')
    return 'EXPECTED_COMPLETE_VIEW_RELATION', coverage


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--regenerated', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    root, regenerated = args.root.resolve(), args.regenerated.resolve()
    frozen = root / 'evidence/w3-r1'
    results = read(regenerated / 'results.json')
    assert len(results) == 47
    old_validation = {}
    for line in (frozen / 'validator-sweep-final/final-validator.tsv').read_text().splitlines():
        cohort, case, *status = line.split('\t')
        old_validation[(cohort, case)] = status
    new_validation = {}
    for line in (regenerated / 'validator.tsv').read_text().splitlines():
        cohort, case, *status = line.split('\t')
        new_validation[(cohort, case)] = status

    summary, count, metadata = [], Counter(), Counter()
    for row in results:
        cohort, case = row['cohort'], row['case']
        old_file = frozen / cohort / case / 'air.json'
        new_file = regenerated / cohort / case / 'air.json'
        old_result = next(x for x in read(frozen / cohort / 'results.json') if x['case'] == case)
        assert sha(old_file) == row['frozenAirSha256'] == old_result['hashes']['air.json']
        assert sha(new_file) == row['airSha256']
        assert old_validation[(cohort, case)] == new_validation[(cohort, case)]
        assert new_validation[(cohort, case)][0] == 'STRUCTURALLY_VALID'
        old, new = read(old_file), read(new_file)
        fresh_sp = read(regenerated / cohort / case / 'sp/cobol-semantic-product.json')
        assert fresh_sp['storage']['profile'] == (
            'UNSPECIFIED' if cohort == 'logical-final'
            else 'IBM_ENTERPRISE_6_4_FIXED_DISPLAY_1047'), (cohort, case, 'profile changed')
        if row['byteIdentical']:
            assert old == new
            classification, coverage = 'BYTE_IDENTICAL', Counter()
        else:
            classification, coverage = compare(old, new, fresh_sp, case)
        count[classification] += 1
        metadata.update(coverage)
        summary.append({'cohort': cohort, 'case': case, 'oldAirSha256': row['frozenAirSha256'],
                        'newAirSha256': row['airSha256'], 'classification': classification,
                        'validator': new_validation[(cohort, case)]})
    assert count == {'BYTE_IDENTICAL': 10, 'EXECUTABLE_EQUIVALENT': 36,
                     'EXPECTED_COMPLETE_VIEW_RELATION': 1}, count
    assert metadata['modeledToAbstracted'] == 107, metadata
    report = {'counts': dict(count), 'metadata': dict(metadata), 'cases': summary}
    args.out.write_text(json.dumps(report, indent=2) + '\n')
    print('W3_R1_REGENERATION_AUDIT=PASS', json.dumps(report['counts']),
          json.dumps(report['metadata']))


if __name__ == '__main__':
    main()
