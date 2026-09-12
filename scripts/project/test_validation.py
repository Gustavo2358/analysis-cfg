#!/usr/bin/env python3
"""Adversarial orchestration/receipt tests; never execute a producer or qualification here."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch
import check_ci_orchestration as guard
import run_validation as validation

ROOT = Path(__file__).resolve().parents[2]


class OrchestrationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'repository'
        for relative in ('docs/sources/sources.lock.json', '.github/workflows/ci.yml',
                         '.github/workflows/qualification.yml', 'scripts/harness/run_gate.py',
                         'scripts/project/check-performance.sh'):
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)

    def test_fixed_fast_and_identical_full_plans(self):
        guard.verify(self.root)
        inventory = json.loads((ROOT / 'docs/evals/cp6/fast-test-inventory.json').read_text())
        names = {name: methods for suites in inventory.values() for name, methods in suites.items()}
        self.assertEqual(286, sum(map(len, names.values())))
        self.assertEqual(5, len(next(m for n, m in names.items() if n.endswith('.W1dInvokeWireTest'))))
        self.assertFalse(any(n.endswith(('ScaleTest', 'WideResultTest')) for n in names))
        self.assertNotIn('invokeAndEffectsScaleHaveLinearWorkCounts', sum(names.values(), []))

    def test_rejects_automatic_heavy_or_manual_automatic_triggers(self):
        for relative, old, new in (
                ('.github/workflows/ci.yml', 'check-pr-fast.sh', 'check-qualification.sh'),
                ('.github/workflows/ci.yml', '  push:', '  push:\n    paths: [docs/**]'),
                ('.github/workflows/qualification.yml', '  workflow_dispatch:', '  workflow_dispatch:\n  push:'),
                ('.github/workflows/qualification.yml', 'check-qualification.sh', 'check-pr-fast.sh'),
                ('.github/workflows/qualification.yml', 'if: always()', 'if: success()'),
                ('scripts/project/check-performance.sh', '1 2 3 4 5', '1 2 3 4')):
            with self.subTest(relative=relative, mutation=new):
                path = self.root / relative
                original = path.read_text()
                path.write_text(original.replace(old, new))
                with self.assertRaises(ValueError):
                    guard.verify(self.root)
                path.write_text(original)

    def run_fake(self, commands, authority='LOCAL_QUALIFICATION', status=''):
        work = Path(self.temp.name) / 'run'
        info = dict(commit='a'*40, tree='b'*40, status=status, branch='test')
        with patch.object(validation, 'source', return_value=info), patch.object(validation, 'plan', return_value=commands), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = validation.execute(authority, work, Path(self.temp.name) / 'm2', self.root)
        return code, json.loads((work / 'evidence/receipt.json').read_text()), work

    def test_failure_retains_exit_raw_log_and_marks_remaining_not_run(self):
        command = dict(name='controlled-failure', argv=[sys.executable, '-c', 'print("original failure"); raise SystemExit(7)'], cwd=str(self.root), env={})
        code, receipt, work = self.run_fake([command, dict(command, name='must-not-run')])
        self.assertEqual(1, code)
        self.assertEqual('FAIL', receipt['result'])
        self.assertEqual(7, receipt['stages'][0]['exitCode'])
        self.assertEqual('NOT_RUN', receipt['stages'][1]['result'])
        log = work / 'evidence/controlled-failure.log'
        self.assertEqual(b'original failure\n', log.read_bytes())
        self.assertEqual(validation.digest(log), receipt['artifactSha256'][log.name])

    def test_dirty_full_never_runs_a_gate(self):
        code, receipt, _ = self.run_fake([], status=' M source.java')
        self.assertEqual(1, code)
        self.assertIn('clean committed source', receipt['failure'])

    def test_remote_full_requires_explicit_dispatch(self):
        with patch.dict('os.environ', {'GITHUB_EVENT_NAME': 'push'}):
            code, receipt, _ = self.run_fake([], authority='REMOTE_MANUAL_QUALIFICATION')
        self.assertEqual(1, code)
        self.assertIn('workflow_dispatch', receipt['failure'])


if __name__ == '__main__':
    unittest.main()
