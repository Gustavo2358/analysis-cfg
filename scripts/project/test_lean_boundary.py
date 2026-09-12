"""Focal architectural regression without historical preservation requirements."""
import unittest
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from unittest.mock import patch
import check_w1d_boundary
ROOT = Path(__file__).resolve().parents[2]
class PlanBoundary(unittest.TestCase):
    def test_generic_plan_cannot_acquire_call_semantics(self):
        import check_w1d_boundary
        original = Path.read_text
        def with_forbidden_call(path, *args, **kwargs):
            text = original(path, *args, **kwargs)
            return text + '\nOperations.Invoke' if path.name == 'DefaultValuePlan.java' else text
        # Exercise the source invariant independently of compiled outputs.
        with patch.object(Path, 'read_text', with_forbidden_call):
            with self.assertRaisesRegex(ValueError, 'DefaultValuePlan acquired CALL'):
                check_w1d_boundary.source_boundaries(ROOT)


class PinnedFixtureBoundary(unittest.TestCase):
    def test_scalar_goldens_use_verified_build_when_sibling_lacks_pin(self):
        from check_scalar_contract import verify_scalar_contract, SCALAR, GOBACK
        from check_architecture import GateFailure
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            root, isolated = workspace / 'consumer', workspace / 'isolated-air'
            # The occupied sibling intentionally has no matching commit; it is not authority.
            (workspace / 'air-java/.git').mkdir(parents=True)
            for path in (SCALAR, GOBACK, 'cfg-adapters/src/test/resources/air/scalar-assign.provenance.json',
                         'docs/sources/sources.lock.json'):
                target = root / path; target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / path, target)
            def git(*args):
                return subprocess.check_output(['git', '-C', str(isolated), *args], text=True).strip()
            isolated.mkdir(); git('init', '-q')
            for path in (SCALAR, GOBACK):
                target = isolated / 'air-json/src/test/resources' / Path(path).name
                target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(root / path, target)
            git('add', '.')
            git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
                '-c', 'commit.gpgsign=false', 'commit', '-qm', 'isolated fixture pin')
            pin = git('rev-parse', 'HEAD')
            lock_path = root / 'docs/sources/sources.lock.json'; lock = json.loads(lock_path.read_text())
            lock['air_java']['commit'] = pin; lock_path.write_text(json.dumps(lock))
            with patch.dict(os.environ, {'AIR_JAVA_CHECKOUT': str(isolated)}):
                verify_scalar_contract(root)
                # The same code still rejects an unpinned dirty source checkout.
                (isolated / 'unexpected').write_text('dirty')
                with self.assertRaisesRegex(GateFailure, 'differs from immutable source pin'):
                    verify_scalar_contract(root)


if __name__ == '__main__':
    unittest.main()
