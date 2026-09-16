#!/usr/bin/env python3
"""Adversarial tests of the independent dependency reader, including actual Java bytes."""
import copy,json,tempfile,unittest
from pathlib import Path
from dependency_wire import read,validate,encode,candidate
ROOT=Path(__file__).resolve().parents[2]
class DependencyWireTests(unittest.TestCase):
    def setUp(self):self.path=ROOT/'analysis-adapters/target/w1d/dynamic-x8.json';self.d=read(self.path)
    def reject(self,change):
        d=copy.deepcopy(self.d);change(d)
        with self.assertRaises((ValueError,KeyError,TypeError)):validate(d)
    def test_physical_range_query_keeps_before_point_without_object_id(self):
        d=copy.deepcopy(self.d);d['sites'][0]['subject']=None
        validate(d)
        for point in (None,dict(d['sites'][0]['valuePoint'],position='AFTER')):
            bad=copy.deepcopy(d);bad['sites'][0]['valuePoint']=point
            with self.assertRaises((ValueError,KeyError,TypeError)):validate(bad)
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

class LeadingDollarWireTests(unittest.TestCase):
    def test_actual_java_dollar_candidates_and_edges_keep_profile_one(self):
        folder=ROOT/'analysis-adapters/target/ep-r2-f2'
        for name,expected in [('literal-dollar','$PROGA'),('computed-dollar','$PROGA'),('regional-dollar','$PROGA'),('eight-characters','$ABCDEFG')]:
            d=read(folder/(name+'.dependencies.json'));site=d['sites'][0]
            self.assertEqual('cobol-zos-dynamic-call-minimal@1',site['nameProfile'])
            self.assertEqual(expected,site['candidates'][0]['referenceName'])
            self.assertEqual(site['rawCandidates'][0]['rawValue'],site['candidates'][0]['rawValue'])
            self.assertEqual(site['rawCandidates'][0]['supports'],site['candidates'][0]['supports'])
            self.assertEqual(site['candidates'][0],d['edges'][0]['candidate'])
            self.assertTrue(site['interpretationUnknownRemainder']);self.assertTrue(site['effectiveUnknownRemainder'])

    def test_only_the_leading_dollar_is_added_to_the_cobol_language(self):
        source=read(ROOT/'analysis-adapters/target/ep-r2-f2/computed-control.dependencies.json')['sites'][0]['candidates'][0]
        for name in ['1PROGA','@PROGA','#PROGA','$proga',' $PROGA','$PROG-A','$ABCDEFGH','$PROGA\t','$PROGA\u00a0']:
            changed=copy.deepcopy(source);changed['referenceName']=name;changed['rawValue']=name
            with self.assertRaises(ValueError):candidate(changed)

class PartialDependencyWireTests(unittest.TestCase):
    def test_actual_partial_outputs_preserve_literal_and_open_computed(self):
        folder=ROOT/'analysis-adapters/target/ep-w4'
        for name in ('unsupported-values','unsupported-control'):
            d=read(folder/(name+'.json'))
            self.assertEqual('1.2.0',d['version']);self.assertEqual('PARTIAL',d['analysisStatus'])
            direct=next(s for s in d['sites'] if s['operation']['localId']=='direct')
            self.assertEqual(['DIRECT'],[c['referenceName'] for c in direct['candidates']])
            if name=='unsupported-values':
                unknown=next(s for s in d['sites'] if s['operation']['localId']=='computed')
                self.assertEqual('ANALYSIS_INCOMPLETE',unknown['targetStatus']);self.assertTrue(unknown['effectiveUnknownRemainder'])
            else:
                self.assertEqual('REACHABLE',direct['reachability']);self.assertTrue(direct['openControlRemainder'])
                self.assertEqual('PARTIAL',direct['analysisStatus'])
                self.assertEqual('KNOWN_GRAPH_ENTRY',d['modelScope'])

    def test_partial_contract_mutants_are_rejected(self):
        source=read(ROOT/'analysis-adapters/target/ep-w4/unsupported-values.json')
        direct=next(i for i,s in enumerate(source['sites']) if s['operation']['localId']=='direct')
        mutations=[
            lambda d:d.__setitem__('version','1.1.0'),
            lambda d:d.__setitem__('analysisStatus','COMPLETE'),
            lambda d:d['sites'][0].__setitem__('analysisReasons',[]),
            lambda d:d['sites'][0].__setitem__('analysisStatus','COMPLETE'),
            lambda d:d['sites'][0].__setitem__('openControlRemainder',False),
            lambda d:d.__setitem__('modelScope','KNOWN_GRAPH_ENTRY'),
            lambda d:d['sites'][direct]['candidates'][0].__setitem__('supports',[]),
            lambda d:d['edges'][0].__setitem__('openSite',False),
        ]
        for mutation in mutations:
            d=copy.deepcopy(source);mutation(d)
            with self.assertRaises((ValueError,KeyError,TypeError)):validate(d)
if __name__=='__main__':unittest.main()
