"""Version/pin guard adversaries for the current real-producer integration gate."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import e2e_w2d


class LockedSourceContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.lock = self.root / 'docs/sources/sources.lock.json'
        self.lock.parent.mkdir(parents=True)
        self.pin('2.50.0')
        self.patch = patch.object(e2e_w2d, 'ROOT', self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def pin(self, version):
        self.lock.write_text(json.dumps({'proleap_poc': {'semantic_product_version': version}}))

    def sample(self, version='2.47.0'):
        return {'contractVersion': version, 'sourceDependencies': {'availability': 'KNOWN'}, 'statements': []}

    def test_current_pin_admits_earlier_feature_selected_and_current_publications(self):
        for version in ('2.31.0', '2.38.0', '2.47.0', '2.50.0'):
            with self.subTest(version=version):
                e2e_w2d.locked_sp(self.sample(version))

    def test_feature_floors_are_independent_of_the_pinned_ceiling(self):
        features = [({'storage': {'logicalExactViews': [{}]}}, '2.35.0'),
                    ({'statements': [{'variant': 'PROCEDURE_PERFORM', 'publicationKind': 'STRUCTURAL_FACTS'}]}, '2.36.0'),
                    ({'ordinaryContinuations': [{}]}, '2.37.0'),
                    ({'statements': [{'variant': 'MOVE', 'logicalTransfers': [{}]}]}, '2.38.0')]
        for extra, version in features:
            with self.subTest(version=version):
                sp = self.sample(version) | extra
                e2e_w2d.locked_sp(sp)
                sp['contractVersion'] = f'2.{int(version.split(".")[1])-1}.0'
                with self.assertRaises(ValueError):
                    e2e_w2d.locked_sp(sp)

    def test_future_old_and_malformed_publications_are_rejected(self):
        for version in ('2.30.0', '2.50.1', '2.51.0', '3.0.0', '2.47', '2.47.0.1', '2.x.0'):
            with self.subTest(version=version), self.assertRaises(ValueError):
                e2e_w2d.locked_sp(self.sample(version))

    def test_lock_is_authoritative_and_not_an_unbounded_allowlist(self):
        self.pin('2.38.0')
        e2e_w2d.locked_sp(self.sample('2.38.0'))
        with self.assertRaises(ValueError):
            e2e_w2d.locked_sp(self.sample('2.47.0'))
        for version in ('3.0.0', '2.30.0', '2.50', 'bogus'):
            self.pin(version)
            with self.subTest(version=version), self.assertRaises(ValueError):
                e2e_w2d.locked_sp(self.sample('2.31.0'))

    def test_absent_source_evidence_and_out_of_scope_facts_still_fail(self):
        for extra in ({'sourceDependencies': None},
                      {'statements': [{'variant': 'OBSERVED', 'copySemantics': 'POSSIBLE_TEXT'}]},
                      {'statements': [{'variant': 'CICS_RETURN'}]}):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                e2e_w2d.locked_sp(self.sample() | extra)


if __name__ == '__main__':
    unittest.main()
