#!/usr/bin/env python3
"""Adversarial tests of the nominal reader; fixture is an executed W5 output, never a stage substitute."""
import copy, hashlib, json, tempfile, unittest, sys
from pathlib import Path
from result_wire import load, validate, verify_receipt, read_result, WireError
from e2e_w5 import verify_stage, execute_stage
ROOT=Path(__file__).resolve().parents[2]

class ResultReaderTest(unittest.TestCase):
    def setUp(self):self.report=load(ROOT/'docs/evals/cp5/w5-prepared.snapshot.json')
    def test_real_refused_profile_preserves_batch_reason(self):
        report=read_result(ROOT/'docs/evals/cp5/w5-unsupported.snapshot.json')
        self.assertEqual('INCOMPLETE',report['preparationStatus'])
        self.assertEqual('DEPENDENCY_UNAVAILABLE',report['results'][0]['result']['completion']['observation']['reason'])
        report['results'][0]['result']['completion']['observation']['reason']=None
        with self.assertRaises(WireError):validate(report)
    def test_production_snapshot_is_valid(self):self.assertIs(self.report,validate(self.report))
    def test_missing_schema_version_and_unknown_status_are_rejected(self):
        for field in ('schema','version'):
            r=copy.deepcopy(self.report);del r[field]
            with self.assertRaises(WireError):validate(r)
        r=copy.deepcopy(self.report);r['results'][0]['result']['observations'][0]['queryStatus']='MAYBE'
        with self.assertRaises(WireError):validate(r)
    def test_full_ids_reject_lost_or_wrong_owners(self):
        for mutation in ('lost','wrong-publication','wrong-unit'):
            r=copy.deepcopy(self.report);obj=r['results'][0]['result']['observations'][0]['subject']['objectId']
            if mutation=='lost':del obj['publication']
            else:obj['publication' if mutation=='wrong-publication' else 'unit']='foreign'
            with self.assertRaises(WireError):validate(r)
    def test_candidate_support_loss_and_malformed_association_are_rejected(self):
        for mutation in ('drop','wrong-candidate','wrong-producer','lost-origin'):
            r=copy.deepcopy(self.report);o=r['results'][0]['result']['observations'][0]
            if mutation=='drop':o['candidateSupports']=[]
            elif mutation=='wrong-candidate':o['candidateSupports'][0]['candidate']='wrong'
            elif mutation=='wrong-producer':o['candidateSupports'][0]['producers'][0]['evidence']['localId']='wrong'
            else:del o['candidateSupports'][0]['producers'][0]['origin']
            with self.assertRaises(WireError):validate(r)
    def test_source_partial_and_effective_remainder_are_not_promoted(self):
        for field in ('sourceUnknownRemainder','effectiveUnknownRemainder'):
            r=copy.deepcopy(self.report);r['results'][0]['result']['observations'][0][field]=False
            with self.assertRaises(WireError):validate(r)
    def test_consumer_dependencies_and_outcomes_are_checked(self):
        for mutate in (lambda r:r['consumerPlan'][0]['requiredObservationBatchIds'].append('missing'),lambda r:r['consumers'].clear(),lambda r:r['consumers'][0].update(status='FAILED',reason='CONSUMER_ERROR')):
            r=copy.deepcopy(self.report);mutate(r)
            with self.assertRaises(WireError):validate(r)
    def test_payload_cannot_certify_own_delivery(self):
        for field in ('deliveryStatus','resultSha256','receipt'):
            r=copy.deepcopy(self.report);r[field]='COMPLETE'
            with self.assertRaises(WireError):validate(r)
    def test_receipt_correlates_exact_hash_result_id_and_destination(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'result.json';p.write_text(json.dumps(self.report));receipt=dict(schema='analysis-delivery-receipt',version='1.0.0',resultId=self.report['resultId'],resultSha256=hashlib.sha256(p.read_bytes()).hexdigest(),destination=str(p),status='COMPLETE',reason=None)
            verify_receipt(receipt,self.report,p)
            for field,wrong in [('resultId','other'),('resultSha256','0'*64),('destination',str(p.parent/'other'))]:
                r=dict(receipt);r[field]=wrong
                with self.assertRaises(WireError):verify_receipt(r,self.report,p)
            with p.open('a') as f:f.write('\n')
            with self.assertRaises(WireError):verify_receipt(receipt,self.report,p)
    def test_duplicate_members_and_nonfinite_numbers_are_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'bad.json'
            for raw in ('{"schema":1,"schema":2}','{"value":NaN}'):
                p.write_text(raw)
                with self.assertRaises(WireError):read_result(p)
    def test_failed_batch_preserves_independent_consumers(self):
        r=copy.deepcopy(self.report);r['consumerPlan'].append(dict(consumerId='independent',requiredAnalysisKeys=[],requiredObservationBatchIds=[]));r['consumers'].append(dict(consumerId='independent',status='COMPLETE',reason=None,facts=[]))
        b=r['results'][0]['result'];b['observations']=[];b['completion']['observation']=dict(status='FAILED',reason='OBSERVATION_ERROR')
        r['consumers'][0].update(status='NOT_STARTED',reason='DEPENDENCY_UNAVAILABLE',facts=[]);r['preparationStatus']='INCOMPLETE';validate(r)
        r['preparationStatus']='COMPLETE'
        with self.assertRaises(WireError):validate(r)
    def test_golden_substitution_without_real_stage_receipt_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'golden.json';p.write_text(json.dumps(self.report))
            with self.assertRaises(WireError):verify_stage(dict(executed=False,argv=['copy','golden']),['java','real.Main'],p,p)
    def test_real_stage_must_execute_before_output_is_accepted(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d);source=directory/'source.json';source.write_text('{}');output=directory/'result.json'
            argv=[sys.executable,'-c','import pathlib,sys; pathlib.Path(sys.argv[1]).write_text(\'{"actualProcess":true}\')',str(output)]
            receipt=execute_stage('test-stage',argv,directory,source,output,directory);verify_stage(receipt,argv,source,output)
            self.assertEqual({'actualProcess':True},load(output))
    def test_report_is_detached_from_input_file(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'result.json';p.write_text(json.dumps(self.report));report=read_result(p);p.unlink();self.assertEqual(self.report,report)
if __name__=='__main__':unittest.main(verbosity=2)
