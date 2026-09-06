#!/usr/bin/env python3
"""Adversarial tests of harness guards only; no CFG algorithm is implemented here."""
from __future__ import annotations
import contextlib
import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from validate_docs import ROOT, validate, load_json
from run_gate import run
from cache_ir import cache, git_blob_sha1


class HarnessGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cfg-harness-test-')
        self.root = Path(self.temp.name) / 'repository'
        shutil.copytree(ROOT, self.root, ignore=shutil.ignore_patterns('.git', '.cache', '__pycache__', '.harness-results', 'target'))

    def tearDown(self):
        self.temp.cleanup()

    def edit_json(self, path, fn):
        target = self.root / path
        value = load_json(target)
        fn(value)
        target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def assert_guard(self, expected):
        errors = validate(self.root)
        self.assertTrue(any(expected in e for e in errors), '\n'.join(errors))

    def test_01_valid_package(self):
        self.assertEqual([], validate(self.root))

    def test_02_broken_link(self):
        with (self.root / 'README.md').open('a') as f:
            f.write('\n[broken](missing-local.md)\n')
        self.assert_guard('Broken local link')

    def test_03_unknown_invariant(self):
        with (self.root / 'README.md').open('a') as f:
            f.write('\nINV-CFG-999\n')
        self.assert_guard('Unknown ID INV-CFG-999')

    def test_04_duplicate_invariant(self):
        with (self.root / 'docs/architecture/invariants.md').open('a') as f:
            f.write('\n## INV-CFG-001 — Duplicate\n')
        self.assert_guard('Duplicate invariant')

    def test_05_backlog_cycle(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0]['dependencies'].append('BACKLOG-CFG-002'))
        self.assert_guard('dependency cycle')

    def test_06_missing_dependency(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0]['dependencies'].append('BACKLOG-CFG-999'))
        self.assert_guard('Missing backlog dependency')

    def test_07_registry_drift(self):
        self.edit_json('docs/work/registry.json', lambda x: x['active'][0].update(status='blocked'))
        self.assert_guard('Work registry mismatch')

    def test_08_completed_item_left_active(self):
        self.edit_json('docs/work/registry.json', lambda x: x['active'][0].update(status='completed'))
        self.edit_json('docs/work/active/WORK-CFG-001/work-item.json', lambda x: x.update(status='completed'))
        self.assert_guard('Completed/invalid work in active')

    def test_09_extra_work_file(self):
        (self.root / 'docs/work/active/WORK-CFG-001/duplicate-plan.md').write_text('# Duplicate\n')
        self.assert_guard('exactly five files')

    def test_10_java_forbidden(self):
        # A filename-only sentinel tests packaging policy; no Java code is created.
        (self.root / 'Sentinel.java').write_bytes(b'')
        self.assert_guard('Java/POM forbidden')

    def test_11_pom_forbidden(self):
        (self.root / 'pom.xml').write_bytes(b'')
        self.assert_guard('Java/POM forbidden')

    def test_12_false_product_gate(self):
        self.edit_json('docs/engineering/gate-state.json', lambda x: x['product_gates']['semantic'].update(status='implemented', hook='scripts/project/absent.sh'))
        self.assert_guard('Implemented product gate in docs_only')

    def test_13_false_profile_claim(self):
        self.edit_json('docs/evals/profile-obligations.json', lambda x: x['implemented_profiles'].append('AIR-STRUCTURE@2'))
        self.assert_guard('Implemented profile claimed')

    def test_14_profile_obligation_removed(self):
        self.edit_json('docs/evals/profile-obligations.json', lambda x: x['profiles'][0]['oracles'].pop())
        self.assert_guard('Profile obligations drift')

    def test_15_mutable_source_reference(self):
        self.edit_json('docs/sources/sources.lock.json', lambda x: x['analysis_ir'].update(commit='main'))
        self.assert_guard('IR commit must be immutable')

    def test_16_duplicate_json_key(self):
        p = self.root / 'docs/engineering/gate-state.json'
        p.write_text('{"schema_version":1,"schema_version":2}')
        self.assert_guard('duplicate JSON key')

    def test_17_phase_without_authorization(self):
        self.edit_json('docs/engineering/gate-state.json', lambda x: x.update(phase='implementation', runtime_authorization='WORK-CFG-001'))
        self.assert_guard('lacks linked implementation authorization')

    def test_18_missing_work_context(self):
        self.edit_json('docs/work/active/WORK-CFG-001/work-item.json', lambda x: x['must_read'].append('missing-rule.md'))
        self.assert_guard('Missing/invalid work path')

    def test_19_unavailable_never_passes(self):
        for gate in ('architecture', 'semantic', 'performance', 'integration', 'full'):
            with self.subTest(gate=gate), contextlib.redirect_stdout(io.StringIO()) as out:
                self.assertEqual(3, run(gate, self.root))
                self.assertIn('UNAVAILABLE', out.getvalue())

    def test_20_unresolved_eval(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0]['evals'].append('EVAL-CFG-999'))
        self.assert_guard('Missing eval reference')

    def test_21_link_cannot_escape_repository(self):
        with (self.root / 'README.md').open('a') as f:
            f.write('\n[escape](../outside.md)\n')
        self.assert_guard('Broken local link')


    def test_26_product_gate_cannot_bypass_authorization_preflight(self):
        hook = self.root / 'scripts/project/check-semantic.sh'
        hook.parent.mkdir(parents=True)
        hook.write_text('#!/usr/bin/env bash\nexit 0\n')
        self.edit_json('docs/engineering/gate-state.json', lambda x: x.update(phase='implementation'))
        self.edit_json('docs/engineering/gate-state.json', lambda x: x['product_gates']['semantic'].update(status='implemented', hook='scripts/project/check-semantic.sh'))
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, run('semantic', self.root))

    def test_27_completed_backlog_requires_evidence(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0].update(status='completed'))
        self.assert_guard('Completed backlog without evidence')

    def test_28_unknown_work_link(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0].update(work_item='WORK-CFG-999'))
        self.assert_guard('Backlog points to unknown work item')

    def test_29_legacy_ir_profile_rejected(self):
        self.edit_json('docs/evals/profile-obligations.json', lambda x: x['profiles'][0].update(id='AIR-STRUCTURE@1'))
        self.assert_guard('Profile obligations drift')

    def test_30_legacy_ir_version_rejected(self):
        self.edit_json('docs/sources/sources.lock.json', lambda x: x['analysis_ir'].update(semantic_version='1.0.0'))
        self.assert_guard('Analysis IR 2.0.0')

    def test_31_nonexistent_oracle_projection_rejected(self):
        invalid_oracles = ('O-01-SCALAR', 'O-56-STRUCT', 'O-85-REGION')
        for oracle in invalid_oracles:
            with self.subTest(oracle=oracle):
                self.edit_json('docs/evals/catalog.json', lambda x: x['evals'][0]['upstream_oracles'].append(oracle))
                self.assert_guard('Invalid upstream oracle: ' + oracle)
                self.edit_json('docs/evals/catalog.json', lambda x: x['evals'][0]['upstream_oracles'].pop())

    def test_32_normative_oracle_projections_accepted(self):
        valid_oracles = ('O-69-SCALAR', 'O-81-REGION', 'O-85-STRUCT')
        self.edit_json('docs/evals/catalog.json', lambda x: x['evals'][0]['upstream_oracles'].extend(valid_oracles))
        self.assertEqual([], validate(self.root))


class PinnedCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cfg-ir-cache-test-')
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.data = b'# Synthetic source used only for testing hash verification.\n'
        (self.source / 'README.md').write_bytes(self.data)
        self.air = {'repository':'Gustavo2358/analysis-ir', 'commit':'0'*40,
                    'files':[{'path':'README.md', 'git_blob_sha1':git_blob_sha1(self.data)}]}

    def tearDown(self):
        self.temp.cleanup()

    def test_22_import_verified_local_source(self):
        path = cache(self.air, self.root / 'cache', self.source)
        self.assertEqual(self.data, (path / 'README.md').read_bytes())
        self.assertEqual(path, cache(self.air, self.root / 'cache', self.source))

    def test_23_hash_mismatch_leaves_no_published_cache(self):
        (self.source / 'README.md').write_bytes(b'changed\n')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            cache(self.air, self.root / 'cache', self.source)
        self.assertFalse((self.root / 'cache' / ('0'*40)).exists())
        self.assertEqual([], list((self.root / 'cache').iterdir()))

    def test_24_unsafe_path(self):
        self.air['files'][0]['path'] = '../outside.md'
        with self.assertRaisesRegex(ValueError, 'unsafe'):
            cache(self.air, self.root / 'cache', self.source)

    def test_25_existing_cache_tampering(self):
        path = cache(self.air, self.root / 'cache', self.source)
        (path / 'README.md').write_bytes(b'modified\n')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            cache(self.air, self.root / 'cache', self.source)


if __name__ == '__main__':
    unittest.main(verbosity=2)
