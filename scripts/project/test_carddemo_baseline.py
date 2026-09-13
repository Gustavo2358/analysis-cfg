"""FAST-only evaluation contracts: synthetic processes/data, no upstream corpus."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import carddemo_baseline as runner
import carddemo_metrics as metrics
import carddemo_entry_delta as entry_delta


class BaselineTests(unittest.TestCase):
    def test_explicit_after_pins_keep_strict_runtime_admission(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); pins = root / 'pins.json'; runtime = root / 'runtime.json'
            pins.write_text(json.dumps({'analysisRepositories': {'frontend': 'a' * 40}}))
            runtime.write_text(json.dumps({'sources': {'frontend': 'b' * 40}}))
            with patch.object(runner, 'PINS', root / 'unused-historical-pins.json'):
                with self.assertRaisesRegex(ValueError, 'runtime pipeline snapshots differ'):
                    runner.run(root / 'upstream', root / 'run', runtime, 120, [], pins)

    def test_entry_delta_call_identity_ignores_expanded_lines_but_keeps_include_instance(self):
        call = {'provenance': {'original': {'file': 'a.cbl', 'startLine': 8},
                               'includeChain': [], 'expanded': {'startLine': 30}}}
        changed = copy.deepcopy(call); changed['provenance']['expanded']['startLine'] = 60
        self.assertEqual(entry_delta.call_key(call), entry_delta.call_key(changed))
        changed['provenance']['includeChain'] = [{'includedFile': 'X', 'line': 2}]
        self.assertNotEqual(entry_delta.call_key(call), entry_delta.call_key(changed))
        with self.assertRaisesRegex(ValueError, 'ambiguous source CALL'):
            entry_delta.calls({'path': 'a.cbl', 'sourceCallInventory': [dict(call, statement='s1'),
                               dict(call, statement='s2')], 'callSites': []})

    def test_entry_delta_distinguishes_observed_sites_from_analyzed_sites(self):
        p = {'stages': {stage: {'state': 'PARTIAL'} for stage in runner.STAGES},
             'sourceCallInventory': [{}, {}], 'callSites': []}
        value = entry_delta.totals([p])
        self.assertEqual(value['callsObserved'], 2)
        self.assertEqual(value['callsAnalyzed'], 0)
        self.assertEqual(value['withKnownCandidates'], 0)

    def test_enumerates_case_insensitively_without_success_whitelist(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ('a.cbl', 'b.CBL', 'sub/c.COB', 'sub/d.cob', 'sub/book.cpy'):
                f = root / name; f.parent.mkdir(exist_ok=True); f.write_text('same bytes')
            found = runner.discover(root)
            self.assertEqual([x['path'] for x in found], ['a.cbl', 'b.CBL', 'sub/c.COB', 'sub/d.cob'])
            # Equal content is NOT a reason to drop a path.
            records = runner.attempt_all(found, lambda p: {'path': p['path']})
            self.assertEqual(len(records), 4)
            metrics.validate_enumeration(found, records)
            with self.assertRaises(ValueError):
                metrics.validate_enumeration(found, records + records[:1])

    def test_failure_does_not_abort_later_programs(self):
        def attempt(p):
            if p['path'] == 'bad.cbl':
                raise ValueError('bad metrics input')
            return {**runner.empty_program(p), 'visited': True}
        records = runner.attempt_all([{'path': p} for p in ('good.cbl', 'bad.cbl', 'last.cbl')], attempt)
        self.assertTrue(records[-1]['visited'])
        self.assertEqual(records[1]['runnerFailure']['reasonCategory'], 'INTERNAL_FAILURE')
        self.assertGreaterEqual(records[1]['timings']['programElapsedMs'], 0)

    def test_archive_sources_and_cl2_are_not_silently_excluded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with zipfile.ZipFile(root / 'runtime.zip', 'w') as archive:
                archive.writestr('app/A.cbl', b'COBOL source')
                archive.writestr('app/B.cl2', b'COBOL source')
                archive.writestr('__MACOSX/app/._A.cbl', bytes.fromhex('00051607') + b'metadata')
                archive.writestr('app/cpy/COPY1', b'copybook')
            discovered = runner.discover(root)
            self.assertEqual([s['path'] for s in discovered], ['runtime.zip!/app/A.cbl', 'runtime.zip!/app/B.cl2'])
            audit = runner.extract_archives(root, root / 'extracted')
            self.assertEqual(len(audit[0]['excludedNonSourceMetadata']), 1)
            self.assertEqual((root / 'extracted/runtime.zip!/app/B.cl2').read_bytes(), b'COBOL source')

    def test_archive_extraction_rejects_path_escape(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with zipfile.ZipFile(root / 'bad.zip', 'w') as archive:
                archive.writestr('../escape.cbl', 'bad')
            with self.assertRaisesRegex(ValueError, 'unsafe archive'):
                runner.extract_archives(root, root / 'outputs')

    def test_dependency_roots_prefer_program_context_and_preserve_collisions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for path, content in [('app/cpy/FOO.cpy', 'native'), ('app/ext/cpy/FOO.cpy', 'extension')]:
                p = root / path; p.parent.mkdir(parents=True); p.write_text(content)
            roots, collisions, physical = runner.libraries(root, root / 'app/ext/cbl/A.cbl')
            self.assertEqual(roots, ['app/ext/cpy', 'app/cpy'])
            self.assertEqual(physical[0], root / 'app/ext/cpy')
            self.assertFalse(collisions[0]['sameContent'])

    def test_timeout_is_operational_and_preserves_elapsed(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = runner.execute_stage('frontend', [sys.executable, '-c', 'import time; time.sleep(10)'], Path(tmp), 0.05)
        self.assertEqual(result['state'], 'TIMEOUT')
        self.assertEqual(result['reasonCategory'], 'TIMEOUT')
        self.assertEqual(result['configuredTimeoutSeconds'], 0.05)
        self.assertGreater(result['elapsedMs'], 0)

    def test_process_failure_is_timed_and_logs_retained(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = runner.execute_stage('lower', [sys.executable, '-c', 'import sys; print("failure", file=sys.stderr); sys.exit(4)'], Path(tmp), 5)
            self.assertEqual(result['state'], 'BLOCKED')
            self.assertGreater(result['elapsedMs'], 0)
            self.assertIn('failure', (Path(tmp) / 'lower.stderr').read_text())

    def test_blockers_are_not_semantic_capabilities(self):
        for text, category in [('COPY_NOT_FOUND DFHAID', 'COPYBOOK_MISSING'),
                               ('Unsupported tab in fixed-format source', 'NORMALIZATION_GAP'),
                               ('event=analysis_failed phase=PREPROCESSING', 'PREPROCESSING_GAP'),
                               ('OutOfMemoryError', 'RESOURCE_LIMIT')]:
            self.assertEqual(runner.classify_failure('frontend', 1, text)[1], category)
        self.assertEqual(runner.classify_failure('lower', 5, 'AIR codec INVALID_INPUT')[1], 'AIR_TRANSPORT_GAP')

    def test_unexpected_java_failure_is_not_a_frontend_capability_gap(self):
        for failure in ('NullPointerException', 'NoClassDefFoundError', 'Could not find or load main class'):
            self.assertEqual(runner.classify_failure('frontend', 1, 'phase=PREPROCESSING ' + failure),
                             ('FAILED', 'INTERNAL_FAILURE', 'UNEXPECTED_RUNTIME_FAILURE'))

    def test_timing_statistics_and_extremes(self):
        values = [('c', 9), ('a', 1), ('b', 2), ('d', 4)]
        actual = metrics.timing_stats(values)
        for key, expected in [('count', 4), ('totalMs', 16), ('meanMs', 4), ('medianMs', 3), ('minMs', 1), ('maxMs', 9)]:
            self.assertEqual(actual[key], expected)
        self.assertEqual(actual['fastestProgram'], 'a')
        self.assertEqual(actual['slowestProgram'], 'c')
        self.assertEqual(metrics.timing_stats([])['meanMs'], None)
        self.assertEqual(metrics.timing_stats([('a', 1)])['medianMs'], 1)
        self.assertEqual(metrics.timing_stats([(str(i), i) for i in range(1, 21)])['p95Ms'], 19)

    def test_call_classification_never_closes_unknown(self):
        s = {'candidates': [{'referenceName': 'PROGA'}], 'reachability': 'REACHABLE',
             'modelValueRemainder': False, 'sourceValueRemainder': False,
             'interpretationUnknownRemainder': False, 'effectiveUnknownRemainder': False,
             'openControlRemainder': False, 'uncertaintyRefs': []}
        self.assertEqual(metrics.classify_site(s), 'CLOSED_RESOLVED')
        for field in ('modelValueRemainder', 'sourceValueRemainder', 'interpretationUnknownRemainder', 'effectiveUnknownRemainder', 'openControlRemainder'):
            self.assertEqual(metrics.classify_site({**s, field: True}), 'PARTIAL_RESOLVED')
        self.assertEqual(metrics.classify_site({**s, 'uncertaintyRefs': ['unknown']}), 'PARTIAL_RESOLVED')
        self.assertEqual(metrics.classify_site({**s, 'candidates': [], 'effectiveUnknownRemainder': True}), 'OPEN_UNRESOLVED')
        self.assertEqual(metrics.classify_site({**s, 'reachability': 'UNREACHABLE_IN_MODEL'}), 'UNREACHABLE_IN_MODEL')
        self.assertEqual(metrics.classify_site({**s, 'candidates': []}), 'OTHER')

    def test_ratios_have_denominators_and_empty_is_not_zero_percent(self):
        self.assertEqual(metrics.ratio(2, 4), {'numerator': 2, 'denominator': 4, 'percent': 50.0})
        self.assertEqual(metrics.ratio(0, 0)['percent'], None)

    def test_dependencies_deduplicate_globally_without_losing_sites(self):
        sites = [{'sourceProgram': 'app/A.cbl', 'callerName': 'A', 'operation': {'localId': str(i)},
                  'reachability': 'REACHABLE', 'candidates': [{'referenceName': 'B'}, {'referenceName': 'B'}]}
                 for i in range(2)]
        deps = metrics.known_dependencies(sites)
        self.assertEqual(len(deps), 1)
        self.assertEqual(deps[0]['referenceName'], 'B')
        self.assertEqual(len(deps[0]['sites']), 2)

    def test_advisory_policy_is_not_a_roadmap_selector(self):
        a = metrics.advisory([])
        self.assertEqual(a['decisionPolicy'], 'HUMAN')
        self.assertEqual(a['rankingAuthority'], 'ADVISORY_ONLY')
        metrics.validate_advisory(a)
        for field in ('nextFeature', 'mandatoryNext', 'selectedCapability'):
            invalid = copy.deepcopy(a); invalid['suggestions'].append({field: 'GO_TO'})
            with self.assertRaises(ValueError):
                metrics.validate_advisory(invalid)
        with self.assertRaises(ValueError):
            metrics.validate_advisory({**a, 'decisionPolicy': 'AUTOMATIC'})

    def test_timing_populations_do_not_mix_blocked_and_completed(self):
        def record(name, total, completed):
            p = runner.empty_program({'path': name, 'sourceBytes': 1, 'sourceLines': 1})
            p.update(gaps=[], callSites=[], sourceCallInventory=[], statements=None, callBoundaryUncertainties=[])
            p['timings']['programElapsedMs'] = total
            for stage in (runner.STAGES if completed else ('frontend',)):
                p['stages'][stage].update(state='PARTIAL' if completed else 'BLOCKED', elapsedMs=1)
            return p
        aggregate = metrics.aggregate([record('blocked', 2, False), record('completed', 10, True)], {'corpusElapsedMs': 15})
        self.assertEqual(aggregate['timing']['allAttempted']['meanMs'], 6)
        self.assertEqual(aggregate['timing']['completedDependencyPipeline']['meanMs'], 10)
        self.assertEqual(aggregate['timing']['byStage']['frontend']['executions'], 2)
        self.assertEqual(aggregate['timing']['byStage']['lower']['executions'], 1)

    def test_sp_inventory_preserves_generic_kinds_and_count_partition(self):
        sp = {'coverage': {'observedStatements': 2, 'modeledStatements': 1, 'partialStatements': 0,
                           'unsupportedStatements': 1, 'inputMissingStatements': 0, 'inventoryStatus': 'COMPLETE'},
              'statements': [{'variant': variant, **extras, 'header': {'id': str(i), 'programPoint': i, 'coverage': coverage, 'provenance': {}}}
                             for i, (variant, extras, coverage) in enumerate([
                                 ('GOBACK', {}, 'MODELED'), ('OBSERVED', {'observedKind': 'MODELED_STATEMENT'}, 'UNSUPPORTED')])]}
        inventory = metrics.statement_inventory(sp)
        self.assertEqual(set(inventory['byFamily']), {'GOBACK', 'MODELED_STATEMENT'})
        sp['coverage']['unsupportedStatements'] = 0
        with self.assertRaises(ValueError): metrics.statement_inventory(sp)

    def test_potential_attribution_uses_wire_bound_not_text_or_order(self):
        unit = {'domain': 'unit', 'localId': 'u', 'publication': 'p'}
        op = {'kind': 'opaque', 'envelope': {'control': {'remainder': {'kind': 'within', 'scope': {'kind': 'unit', 'unit': unit, 'labels': True}}}}}
        site = {'caller': unit, 'openControlRemainder': True}
        self.assertTrue(metrics.control_bound_contains(op, site))
        self.assertFalse(metrics.control_bound_contains(op, {**site, 'caller': {**unit, 'localId': 'other'}}))
        self.assertFalse(metrics.control_bound_contains(op, {**site, 'openControlRemainder': False}))
        self.assertFalse(metrics.control_bound_contains({'kind': 'assign'}, site))

    def test_same_gap_code_on_another_statement_does_not_prove_direct_impact(self):
        op = {'domain': 'operation', 'publication': 'p', 'unit': 'u', 'localId': 'call'}
        uncertainty = {'domain': 'uncertainty', 'publication': 'p', 'localId': 'why'}
        record = {'gaps': [{'statement': 'different', 'gapCode': 'CODE'}],
                  'callSites': [{'operation': op, 'uncertaintyRefs': [uncertainty]}]}
        air = {'coverage': {'items': []}, 'uncertainties': [{'id': uncertainty, 'code': 'CODE',
                'scope': {'entities': [op]}, 'dimensions': ['DEPENDENCIES']}]}
        metrics.attach_impacts(record, None, air)
        self.assertEqual(record['gaps'][0]['impact']['attribution'], 'UNKNOWN')

    def test_historical_report_refuses_overwrite(self):
        from carddemo_report import write_report
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / 'historical'; dest.with_suffix('.json').write_text('original')
            with self.assertRaisesRegex(ValueError, 'immutable'):
                write_report(Path(tmp) / 'unused.json', dest)
            self.assertEqual(dest.with_suffix('.json').read_text(), 'original')

    def test_committed_baseline_schema_aggregates_and_policy(self):
        path = Path(__file__).resolve().parents[2] / 'docs/evals/cp6/carddemo-full-baseline.json'
        if not path.exists(): self.skipTest('historical result not generated yet')
        data = json.loads(path.read_text())
        metrics.validate_baseline(data)
        invalid = copy.deepcopy(data); invalid['corpus']['programsAttempted'] += 1
        with self.assertRaises(ValueError): metrics.validate_baseline(invalid)


if __name__ == '__main__':
    unittest.main()
