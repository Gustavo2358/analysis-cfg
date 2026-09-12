#!/usr/bin/env python3
"""Adversarial tests of the independent dependency reader, including actual Java bytes."""
import copy,json,tempfile,unittest
from pathlib import Path
from dependency_wire import read,validate,encode
ROOT=Path(__file__).resolve().parents[2]
class DependencyWireTests(unittest.TestCase):
    def setUp(self):self.path=ROOT/'analysis-adapters/target/w1d/dynamic-x8.json';self.d=read(self.path)
    def reject(self,change):
        d=copy.deepcopy(self.d);change(d)
        with self.assertRaises((ValueError,KeyError,TypeError)):validate(d)
    def test_real_cases(self):
        for name in ('dynamic-x8','literal','dynamic-no-move','orphan'):read(self.path.with_name(name+'.json'))
    def test_closed_wire_fields_enums_versions_null_ids_and_boolean(self):
        for field in ('version','schema'):
            self.reject(lambda d,f=field:d.__setitem__(f,'bad'))
        self.reject(lambda d:d.__setitem__('unknown',0))
        self.reject(lambda d:d['sites'][0].__setitem__('targetKind','dynamic'))
        self.reject(lambda d:d['sites'][0].__setitem__('modelValueRemainder',None))
        self.reject(lambda d:d['sites'][0]['operation'].__setitem__('domain','object'))
        self.reject(lambda d:d['sites'][0].__setitem__('sourceValueRemainder',1))
        self.reject(lambda d:d['sites'][0].__setitem__('effectiveUnknownRemainder',False))
        self.reject(lambda d:d['sites'][0]['valuePoint'].__setitem__('position','AFTER'))
    def test_candidates_edges_and_origins_are_not_free_form(self):
        self.reject(lambda d:d['sites'][0]['candidates'][0].__setitem__('rawValue','PROGA'))
        self.reject(lambda d:d['sites'][0]['candidates'][0].__setitem__('referenceName','proga'))
        self.reject(lambda d:d.__setitem__('edges',[]))
        self.reject(lambda d:d.__setitem__('origins',[]))
        self.reject(lambda d:d['sites'][0]['candidates'][0]['supports'][0].__setitem__('kind','GUESS'))
    def test_duplicate_keys_noncanonical_bytes_and_invalid_unicode(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'bad.json';raw=self.path.read_bytes()
            for malformed in (b'{"schema":"duplicate",'+raw[1:],raw+b'\n',raw.replace(b'"schema":',b'"schema" :',1),b'\xff'):
                p.write_bytes(malformed)
                with self.assertRaises((ValueError,UnicodeError)):read(p)
if __name__=='__main__':unittest.main()
