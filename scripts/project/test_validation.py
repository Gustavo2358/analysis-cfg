"""Current validation entrypoints route to Fast or guarded local qualification."""
import os
import sys
import unittest
from unittest.mock import patch
import run_validation as validation

class ValidationRouting(unittest.TestCase):
    def test_fast_routes_to_technical_profile(self):
        with patch.object(sys, 'argv', ['run_validation.py', 'FAST_CI']), patch.object(validation, 'execute') as fast:
            self.assertEqual(0, validation.main())
            fast.assert_called_once_with('CODE_CHANGE', validation.ROOT)
    def test_local_full_remains_accessible(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(sys, 'argv', ['run_validation.py', 'LOCAL_QUALIFICATION']), patch.object(validation, 'full_local') as full:
            self.assertEqual(0, validation.main())
            full.assert_called_once_with(validation.ROOT)
    def test_full_refuses_ci_before_build(self):
        with patch.dict(os.environ, {'CI': 'true'}), patch.object(sys, 'argv', ['run_validation.py', 'LOCAL_QUALIFICATION']), patch.object(validation, 'full_local') as full:
            with self.assertRaisesRegex(RuntimeError, 'REMOTE_QUALIFICATION_PROHIBITED'):
                validation.main()
            full.assert_not_called()

if __name__ == '__main__':
    unittest.main()
