#!/usr/bin/env python3
"""Policy countercases A-L. No producer builds or historical qualification."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import lean
import lean_project

ROOT = Path(__file__).resolve().parents[2]


class LeanPolicy(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.work = dict(id='WORK-TEST-001', title='A bounded change', status='IN_PROGRESS', scope=['harness'])

    def test_close_merged_without_receipts_or_certificates(self):
        work = self.root / 'work.json'
        work.write_text(json.dumps(self.work))
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/harness/lean.py'), 'close',
                                 '--work', str(work), '--merged', '--tests-passed'], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('DONE', json.loads(work.read_text())['status'])
        self.assertEqual([work], list(self.root.iterdir()))

    def test_close_requires_merge_and_technical_pass(self):
        for merged, passed in ((False, True), (True, False), (False, False)):
            with self.assertRaises(ValueError):
                lean.close_work(self.work, merged=merged, tests_passed=passed)

    def test_minimal_work_and_invalid_status(self):
        self.assertEqual([], lean.work_errors(self.work))
        self.assertTrue(lean.work_errors(dict(self.work, status='ADMINISTRATIVE CLOSURE BLOCKED')))

    def fixture(self):
        for path in ('.github/workflows',):
            shutil.copytree(ROOT / path, self.root / path)
        lean_project.copy_pin_fixture(ROOT, self.root)
        (self.root / 'docs/work/history').mkdir(parents=True, exist_ok=True)
        (self.root / 'docs/work/history/broken.yaml').write_text('old: [incomplete')
        (self.root / 'docs/work/registry.json').write_text(json.dumps({'active': [], 'history': [{'id': 'OLD'}]}))
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)

    def test_broken_legacy_history_and_absent_manifest_do_not_block_fast(self):
        self.fixture()
        self.assertFalse((self.root / 'MANIFEST.sha256').exists())
        # Actual preflight reads broken history; only product execution is mocked.
        with patch('lean_project.technical_fast') as technical, patch('lean.subprocess.run') as command:
            lean.execute('CODE_CHANGE', self.root)
            technical.assert_called_once_with(self.root)
        self.assertNotIn('history', str(command.call_args_list))

    def test_duplicate_active_ids_and_missing_paths(self):
        (self.root / 'docs/work').mkdir(parents=True)
        path = self.root / 'docs/work/registry.json'
        item = {'id': 'WORK-TEST-001', 'path': 'docs/work'}
        path.write_text(json.dumps({'active': [item, item]}))
        self.assertIn('duplicate active ID', ' '.join(lean.navigation_errors(self.root)))
        path.write_text(json.dumps({'active': [dict(item, path='absent')]}))
        self.assertIn('active path missing', ' '.join(lean.navigation_errors(self.root)))
        path.write_text('{')
        self.assertTrue(lean.navigation_errors(self.root))

    def test_missing_cross_repo_pin(self):
        lean_project.copy_pin_fixture(ROOT, self.root)
        self.assertEqual([], lean_project.pin_errors(self.root))
        lean_project.break_pin_fixture(self.root)
        self.assertTrue(lean_project.pin_errors(self.root))

    def test_cfg_source_boundaries_do_not_read_history(self):
        import builtins
        project = ROOT / 'scripts/project'
        sys.path.insert(0, str(project))
        import check_w3, check_w4, check_w5
        original = Path.read_text
        def current_only(path, *args, **kwargs):
            if 'docs/work/' in str(path):
                raise AssertionError('technical source check attempted historical read: ' + str(path))
            return original(path, *args, **kwargs)
        with patch.object(Path, 'read_text', current_only):
            for checker in (check_w3, check_w4, check_w5):
                checker.verify_sources(ROOT)

    def test_remote_full_performance_mutation_qualification_and_alias_rejected(self):
        self.fixture()
        workflow = next((self.root / '.github/workflows').glob('*.y*ml'))
        original = workflow.read_text()
        for command in ('mvn verify', './scripts/harness/check-full.sh', 'python3 mutation.py',
                        './performance', 'python3 -B scripts/harness/lean.py qualification-local', './alias.sh'):
            with self.subTest(command=command):
                workflow.write_text(original.replace('python3 -B scripts/harness/lean.py ci', command))
                self.assertTrue(lean.workflow_errors(self.root))

    def test_fast_cannot_be_skipped_or_environment_overridden(self):
        self.fixture()
        workflow = next((self.root / '.github/workflows').glob('*.y*ml'))
        original = workflow.read_text()
        for override in ('        if: false\n', '        continue-on-error: true\n', '        env: {MAVEN_ARGS: -Pmutation}\n'):
            workflow.write_text(original + override)
            self.assertTrue(lean.workflow_errors(self.root))

    def test_workflow_dispatch_full_rejected(self):
        self.fixture()
        (self.root / '.github/workflows/manual.yml').write_text('on: workflow_dispatch\njobs:\n  full:\n    steps:\n      - run: mvn verify\n')
        self.assertTrue(lean.workflow_errors(self.root))

    def test_docs_only_does_not_execute_technical_build(self):
        self.fixture()
        with patch('lean_project.technical_fast') as technical, patch('lean.subprocess.run'):
            lean.execute('DOCS_ONLY', self.root)
            technical.assert_not_called()
        self.assertEqual('DOCS_ONLY', lean.classify(['README.md', 'docs/work/history/old.md']))

    def test_code_harness_tests_locks_and_unknown_execute_technical_fast(self):
        for path in ('src/X.java', 'core/pom.xml', 'adapters/src/test/X.java', '.github/workflows/ci.yml',
                     'scripts/helper.py', 'pom.xml', 'docs/sources/sources.lock.json', 'docs/fixtures/test.json', 'docs/work/evidence/WORK-001/oracle.json', 'unknown'):
            self.assertEqual('CODE_CHANGE', lean.classify(['README.md', path]))

    def test_local_full_accessible_and_remote_refused_before_any_build(self):
        self.assertTrue(callable(lean_project.full_local))
        with patch.dict(os.environ, {}, clear=True):
            lean.require_local()
        for key in ('GITHUB_ACTIONS', 'GITHUB_RUN_ID', 'CI'):
            with patch.dict(os.environ, {key: 'true'}), self.assertRaisesRegex(RuntimeError, 'REMOTE_QUALIFICATION_PROHIBITED'):
                lean.require_local()
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/harness/lean.py'), 'qualification-local'],
                                env=dict(os.environ, CI='true'), capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('REMOTE_QUALIFICATION_PROHIBITED', result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
