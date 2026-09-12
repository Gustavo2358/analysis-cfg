"""Focal architectural regression without historical preservation requirements."""
import unittest
from pathlib import Path
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


if __name__ == '__main__':
    unittest.main()
