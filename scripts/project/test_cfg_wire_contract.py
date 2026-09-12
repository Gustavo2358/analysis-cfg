#!/usr/bin/env python3
import json
from pathlib import Path
import unittest
from cfg_wire_contract import verify

ROOT = Path(__file__).resolve().parents[2]


class CfgWireContractTest(unittest.TestCase):
    def document(self, version, terminator='INVOKE', transition='INVOKE_NORMAL'):
        return json.dumps(dict(schema='analysis-cfg-json', schemaVersion=version,
                               nodes=[dict(kind='SEQUENCE', terminator=dict(kind=terminator))],
                               transitions=[dict(kind=transition)]))

    def test_historical_bytes_use_closed_v1(self):
        for name in ('goback', 'scalar-assign'):
            data = (ROOT / ('cfg-adapters/src/test/resources/cfg/' + name + '.manual.json')).read_bytes()
            self.assertEqual('1.0.0', verify(data)['schemaVersion'])

    def test_each_new_token_requires_v2_independently(self):
        for terminator, transition in (('INVOKE', 'INVOKE_NORMAL'), ('INVOKE', 'ENTRY'), ('RETURN', 'INVOKE_NORMAL')):
            self.assertEqual('2.0.0', verify(self.document('2.0.0', terminator, transition))['schemaVersion'])
            with self.assertRaisesRegex(ValueError, 'incompatible'):
                verify(self.document('1.0.0', terminator, transition))

    def test_each_conservative_token_requires_v3_independently(self):
        for term, edge in (('OPAQUE','ENTRY'),('RETURN','OPAQUE_JUMP'),('RETURN','OPAQUE_RETURN')):
            self.assertEqual('3.0.0', verify(self.document('3.0.0',term,edge))['schemaVersion'])
            for version in ('1.0.0','2.0.0'):
                with self.assertRaisesRegex(ValueError,'incompatible'):
                    verify(self.document(version,term,edge))

    def test_rejects_unknown_versions_tokens_and_unnecessary_upgrade(self):
        for version, term, edge in (('1.1.0', 'INVOKE', 'INVOKE_NORMAL'), ('2.0.0', 'CALL', 'INVOKE_NORMAL'),
                                    ('2.0.0', 'INVOKE', 'CALL'), ('2.0.0', 'RETURN', 'RETURN')):
            with self.assertRaises(ValueError):
                verify(self.document(version, term, edge))

    def test_duplicate_version_cannot_hide_incompatible_contract(self):
        data = self.document('2.0.0').replace('"schemaVersion": "2.0.0"', '"schemaVersion":"1.0.0","schemaVersion":"2.0.0"')
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            verify(data)


if __name__ == '__main__':
    unittest.main()
