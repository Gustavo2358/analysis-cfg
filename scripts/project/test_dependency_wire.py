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

class PartialDependencyWireTests(unittest.TestCase):
    def test_actual_partial_outputs_preserve_literal_and_open_computed(self):
        folder=ROOT/'analysis-adapters/target/ep-w4'
        for name in ('unsupported-values','unsupported-control'):
            d=read(folder/(name+'.json'))
            self.assertEqual('2.1.0',d['version']);self.assertEqual('PARTIAL',d['analysisStatus'])
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
class FileDependencyWireTests(unittest.TestCase):
    def test_manual_a1_a2_a3_a4_a6_and_old_reader_rejection(self):
        import runpy
        old=runpy.run_path(str(ROOT/'scripts/project/fixtures/dependency_wire_v1.py'))['validate']
        for case in ('A1','A2','A3','A4','A6'):
            d=read(ROOT/('analysis-adapters/target/fd-w1/'+case+'.json'))
            self.assertEqual('2.1.0',d['version'])
            self.assertEqual('COBOL_SOURCE_ONLY',d['analysisBoundary'])
            with self.assertRaises(ValueError):old(d)
        d=read(ROOT/'analysis-adapters/target/fd-w1/A1.json')['fileDependencies']
        self.assertEqual('CLIENTDD',d['declarations'][0]['name'])
        self.assertEqual('ASSIGNMENT_NAME',d['declarations'][0]['sourceKind'])
        self.assertEqual([],d['sites']);self.assertEqual([],d['edges'])
    def test_call_projection_still_satisfies_frozen_reader(self):
        import runpy
        old=runpy.run_path(str(ROOT/'scripts/project/fixtures/dependency_wire_v1.py'))['validate']
        for name in ('dynamic-x8','literal','dynamic-no-move','orphan'):
            d=read(ROOT/('analysis-adapters/target/w1d/'+name+'.json'))
            projected=copy.deepcopy(d);projected.pop('fileDependencies');projected.pop('analysisBoundary')
            if projected['analysisStatus']=='PARTIAL':projected['version']='1.2.0'
            else:
                projected['version']='1.1.0';projected.pop('analysisStatus');projected.pop('analysisReasons')
                for site in projected['sites']:site.pop('analysisStatus');site.pop('analysisReasons')
            self.assertEqual(projected,old(projected));self.assertEqual(projected,validate(projected))
            self.assertEqual(d['edges'],projected['edges'])

    def test_file_negatives_and_source_scope(self):
        d=read(ROOT/'analysis-adapters/target/fd-w1/A2.json')
        mutations=[
            lambda d:d.pop('fileDependencies'),
            lambda d:d.__setitem__('analysisBoundary','RUNTIME'),
            lambda d:d['fileDependencies']['declarations'][0].pop('owner'),
            lambda d:d['fileDependencies']['declarations'][0].__setitem__('bindingMechanism','UNKNOWN'),
            lambda d:d['fileDependencies']['sites'][0]['bindings'][0]['declaration'].__setitem__('localId','absent'),
            lambda d:d['fileDependencies']['sites'][0]['candidates'][0].__setitem__('referenceName','OTHER'),
            lambda d:d['fileDependencies']['sites'][0]['candidates'][0].__setitem__('supports',[]),
            lambda d:d['fileDependencies']['sites'][0].__setitem__('unknownRemainder',True),
            lambda d:d['fileDependencies'].__setitem__('edges',[]),
            lambda d:d['fileDependencies']['declarations'][0]['objects'][0]['object'].__setitem__('unit','foreign'),
            lambda d:d['fileDependencies']['sites'][0]['origin'].__setitem__('localId','absent'),
        ]
        for change in mutations:
            bad=copy.deepcopy(d);change(bad)
            with self.assertRaises((ValueError,KeyError,TypeError)):validate(bad)
    def test_local_sd_is_not_unknown_external_name_and_is_closed_in_v21(self):
        source=read(ROOT/'analysis-adapters/target/fd-w5/manual/local-true.json')
        self.assertEqual('2.1.0',source['version'])
        site=source['fileDependencies']['sites'][0]
        self.assertEqual('LOCAL',site['targetKind']);self.assertIsNone(site['namespace'])
        self.assertEqual([],site['candidates']);self.assertFalse(site['unknownRemainder'])
        mutations=[
            lambda d:d.__setitem__('version','2.0.0'),
            lambda d:d['fileDependencies']['sites'][0].__setitem__('namespace','cobol.external-file-name'),
            lambda d:d['fileDependencies']['sites'][0].__setitem__('unknownRemainder',True),
            lambda d:d['fileDependencies']['sites'][0].__setitem__('bindings',[]),
            lambda d:d['fileDependencies']['sites'][0].__setitem__('action','release'),
            lambda d:d['fileDependencies']['sites'][0].__setitem__('targetKind','COMPUTED'),
        ]
        for mutate in mutations:
            d=copy.deepcopy(source);mutate(d)
            with self.assertRaises((ValueError,KeyError,TypeError)):validate(d)
        # The old variant still admits its own vocabulary.
        historical=read(ROOT/'analysis-adapters/target/fd-w1/A6.json');historical['version']='2.0.0';validate(historical)

    def test_unknown_computed_cannot_be_closed(self):
        d=read(ROOT/'analysis-adapters/target/fd-w1/A4.json')
        self.assertTrue(d['fileDependencies']['sites'][0]['unknownRemainder'])
        d['fileDependencies']['sites'][0]['unknownRemainder']=False
        with self.assertRaises(ValueError):validate(d)

if __name__=='__main__':unittest.main()
