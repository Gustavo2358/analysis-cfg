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

class FileEpR2CompositionWireTests(unittest.TestCase):
    def test_cross_contract_entry_factorization_and_dollar_documents(self):
        folder=ROOT/'analysis-adapters/target/fd-post-ep-r2'
        for name in ['factors-1','factors-8','factors-32','entry-forward','entry-reverse','dollar-file','dollar-file-negative']:
            d=read(folder/(name+'.json'))
            self.assertEqual('2.3.0',d['version']);self.assertEqual('COBOL_SOURCE_ONLY',d['analysisBoundary'])
            call=d['sites'][0];file=d['fileDependencies']['sites'][0]
            self.assertEqual('cics.file',file['namespace'])
            if name.startswith('dollar'):
                self.assertEqual('$PROGA',call['candidates'][0]['referenceName'])
                self.assertEqual([] if name.endswith('negative') else ['1FILE'],[c['referenceName'] for c in file['candidates']])
            else:
                self.assertEqual(['PROG0000'],[c['referenceName'] for c in call['candidates']])
                self.assertEqual(['PROG0000'],[c['referenceName'] for c in file['candidates']])
                self.assertTrue(call['modelValueRemainder']);self.assertTrue(file['unknownRemainder'])
                self.assertEqual('BEFORE',file['valuePoint']['position'])

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
            self.assertEqual('2.3.0',d['version']);self.assertEqual('PARTIAL',d['analysisStatus'])
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
            self.assertEqual('2.3.0',d['version'])
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
        self.assertEqual('2.3.0',source['version'])
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
        historical=read(ROOT/'analysis-adapters/target/fd-w1/A6.json');historical['version']='2.0.0';historical['fileDependencies']['valuesProfile']='file-literal@1'
        for group in ('sites','edges'):
            for item in historical['fileDependencies'][group]:item.pop('context')
        validate(historical)

    def test_unknown_computed_cannot_be_closed(self):
        d=read(ROOT/'analysis-adapters/target/fd-w1/A4.json')
        self.assertTrue(d['fileDependencies']['sites'][0]['unknownRemainder'])
        d['fileDependencies']['sites'][0]['unknownRemainder']=False
        with self.assertRaises(ValueError):validate(d)


class ComputedFileWireTests(unittest.TestCase):
    def test_four_states_and_frozen_reader_rejection(self):
        import runpy
        old=runpy.run_path(str(ROOT/'scripts/project/fixtures/dependency_wire_v21.py'))['validate']
        for name,expected,remainder in [('literal',['1FILE'],False),('closed',['ALPHA001','BETA0002'],True),('partial',['ALPHA001'],True),('unknown',[],True)]:
            d=read(ROOT/('analysis-adapters/target/fd-w7/manual/'+name+'.json'))
            self.assertEqual('2.3.0',d['version']);s=d['fileDependencies']['sites'][0]
            self.assertEqual(expected,[c['referenceName'] for c in s['candidates']]);self.assertEqual(remainder,s['unknownRemainder'])
            with self.assertRaises(ValueError):old(d)
    def test_computed_support_point_and_remainder_mutants(self):
        base=read(ROOT/'analysis-adapters/target/fd-w7/manual/closed.json')
        changes=[lambda s:s.__setitem__('valuePoint',None),lambda s:s['valuePoint'].__setitem__('position','AFTER'),lambda s:s['candidates'][0].__setitem__('supports',[]),lambda s:s['candidates'][0]['supports'][0].__setitem__('kind','FILE_LITERAL'),lambda s:s['candidates'][0].__setitem__('referenceName','FORGED')]
        for change in changes:
            d=copy.deepcopy(base);change(d['fileDependencies']['sites'][0])
            with self.assertRaises((ValueError,KeyError)):validate(d)
    def test_literal_cics_policy_is_closed_in_the_reader(self):
        base=read(ROOT/'analysis-adapters/target/fd-w7/manual/literal.json')
        for raw,name in [('lower','lower'),(' FILE',' FILE'),('A-B','A-B'),('123456789','123456789'),('FILE    ','FILE    ')]:
            d=copy.deepcopy(base);candidate=d['fileDependencies']['sites'][0]['candidates'][0]
            candidate['rawValue']=raw;candidate['referenceName']=name
            d['fileDependencies']['edges'][0]['candidate']=copy.deepcopy(candidate)
            with self.assertRaises(ValueError):validate(d)

class CicsContextWireTests(unittest.TestCase):
    def test_context_roundtrip_and_old_reader_rejection(self):
        import runpy
        old=runpy.run_path(str(ROOT/'scripts/project/fixtures/dependency_wire_v22.py'))['validate']
        for name in ('systems','invalid','computed-systems','systems-closed','systems-partial','systems-unknown'):
            d=read(ROOT/('analysis-adapters/target/fd-w8/manual/'+name+'.json'))
            self.assertEqual('2.3.0',d['version'])
            with self.assertRaises(ValueError):old(d)
        d=read(ROOT/'analysis-adapters/target/fd-w8/manual/systems.json')
        contexts={s['operation']['localId']:s['context'] for s in d['fileDependencies']['sites']}
        self.assertEqual('DEFAULT',contexts['default']['selection'])
        self.assertEqual(['R001'],[c['referenceName'] for c in contexts['r1']['candidates']])
        self.assertEqual(['R002'],[c['referenceName'] for c in contexts['r2']['candidates']])
    def test_computed_context_before_supports_and_independent_remainder(self):
        for name,expected,remainder in [('closed',['R001','R002'],True),('partial',['R001'],True),('unknown',[],True)]:
            d=read(ROOT/('analysis-adapters/target/fd-w8/manual/systems-'+name+'.json'))
            s=d['fileDependencies']['sites'][0];c=s['context']
            self.assertEqual(expected,[x['referenceName'] for x in c['candidates']]);self.assertEqual(remainder,c['unknownRemainder'])
            self.assertTrue(s['unknownRemainder']);self.assertEqual(0,d['metrics']['physicalGroupsApplied']);self.assertEqual('BEFORE',c['valuePoint']['position'])
        base=read(ROOT/'analysis-adapters/target/fd-w8/manual/systems-closed.json')
        for change in [lambda c:c['valuePoint'].__setitem__('position','AFTER'),lambda c:c.__setitem__('valuePoint',None),lambda c:c['candidates'][0].__setitem__('supports',[]),lambda c:c['candidates'][0]['supports'][0].__setitem__('kind','CICS_SYSID_LITERAL')]:
            d=copy.deepcopy(base);change(d['fileDependencies']['sites'][0]['context'])
            with self.assertRaises((ValueError,KeyError,TypeError)):validate(d)
    def test_context_cannot_be_removed_forged_or_detached_from_edge(self):
        base=read(ROOT/'analysis-adapters/target/fd-w8/manual/systems.json')
        changes=[lambda s:s.pop('context'),lambda s:s['context'].__setitem__('selection','LOCAL'),
            lambda s:s['context'].__setitem__('candidates',[]),
            lambda s:s['context']['candidates'][0].__setitem__('referenceName','OTHER'),
            lambda s:s['context']['candidates'][0].__setitem__('supports',[]),
            lambda s:s['context'].__setitem__('bindingMechanism','UNKNOWN')]
        for change in changes:
            d=copy.deepcopy(base);s=next(s for s in d['fileDependencies']['sites'] if s['operation']['localId']=='r1');change(s)
            with self.assertRaises((ValueError,KeyError,TypeError)):validate(d)

if __name__=='__main__':unittest.main()
