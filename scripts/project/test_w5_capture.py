#!/usr/bin/env python3
"""Real-process adversaries for the product/diagnostic boundary of W5 tools."""
import contextlib
import hashlib
import io
import json
from pathlib import Path
import sys
import unittest

from check_w5 import capture, Failure


class ToolCaptureTests(unittest.TestCase):
    def invoke(self, stdout, stderr, code=0):
        program = ('import sys; sys.stdout.write(sys.argv[1]); '
                   'sys.stderr.write(sys.argv[2]); sys.exit(int(sys.argv[3]))')
        return capture(Path.cwd(), [sys.executable, '-c', program, stdout, stderr, str(code)])

    def test_success_preserves_stdout_exactly_and_reports_arbitrary_stderr(self):
        descriptor = 'public class Example {\n  descriptor: ()V\n}\n'
        for warning in ('arbitrary diagnostic /tmp/random-123\n',
                        'no newline', '[warning] arbitrary\nsecond line\n'):
            with self.subTest(warning=warning), contextlib.redirect_stderr(io.StringIO()) as diagnostics:
                value = self.invoke(descriptor, warning)
            self.assertEqual(descriptor, value)
            self.assertIn(warning, diagnostics.getvalue())
            expected = {'javap_descriptors': {'Example.class': descriptor}}
            actual = {'javap_descriptors': {'Example.class': value}}
            self.assertEqual(expected, actual)
            self.assertEqual(hashlib.sha256(json.dumps(expected).encode()).hexdigest(),
                             hashlib.sha256(json.dumps(actual).encode()).hexdigest())

    def test_stdout_is_never_filtered(self):
        product = '[warning]\nperf,memops\n/tmp/hsperfdata_arbitrary\n'
        self.assertEqual(product, self.invoke(product, ''))

    def test_nonzero_exit_preserves_both_streams_for_investigation(self):
        with self.assertRaises(Failure) as caught:
            self.invoke('partial product\n', 'arbitrary fatal detail\n', 7)
        self.assertIn('partial product', str(caught.exception))
        self.assertIn('arbitrary fatal detail', str(caught.exception))
        self.assertIn('7', str(caught.exception))


if __name__ == '__main__':
    unittest.main()
