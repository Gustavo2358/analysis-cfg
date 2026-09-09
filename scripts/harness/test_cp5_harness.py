#!/usr/bin/env python3
"""Adversarial checks of CP5 harness contracts in disposable copies, not engine mutations."""
from __future__ import annotations
import copy
import contextlib
import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from validate_docs import ROOT, load_json
from validate_cp5 import validate_cp5, validate_result, LIFECYCLE, WORK, PLAN, CHALLENGES, METRICS
sys.path.insert(0, str(ROOT / 'scripts/project'))
from check_analysis_architecture import check_direct_air, check_preparation_air, forbidden_dependencies
from check_cp5_gate import run


class Cp5HarnessTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cfg-cp5-harness-')
        self.root = Path(self.temp.name) / 'repository'
        shutil.copytree(ROOT, self.root, ignore=shutil.ignore_patterns('.git','.cache','.harness-results','__pycache__','target'))

    def tearDown(self):
        self.temp.cleanup()

    def edit(self, path, change):
        value = load_json(self.root/path)
        change(value)
        (self.root/path).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

    def guard(self, expected):
        errors = validate_cp5(self.root)
        self.assertTrue(any(expected in e for e in errors), errors)

    def test_audit_requirements_cannot_disappear(self):
        path = self.root/PLAN/'post-audit-contracts.json'
        original = path.read_bytes() if path.exists() else None
        for key in 'ABCDEFGHI':
            with self.subTest(remediation=key):
                if original is not None:
                    path.write_bytes(original)
                    self.edit(PLAN+'post-audit-contracts.json', lambda x: x['requirements'].pop(key))
                self.guard('post-audit')
        if original is not None:
            path.write_bytes(original)
            self.assertEqual([], validate_cp5(self.root))

    def test_each_audit_obligation_is_required_with_byte_exact_restore(self):
        path = self.root/PLAN/'post-audit-contracts.json'
        original = path.read_bytes()
        data = json.loads(original)
        for key, row in data['requirements'].items():
            for obligation in row['obligations']:
                with self.subTest(remediation=key, obligation=obligation):
                    path.write_bytes(original)
                    self.edit(PLAN+'post-audit-contracts.json',lambda x:x['requirements'][key]['obligations'].remove(obligation))
                    self.guard('post-audit: obligations/routing '+key)
        path.write_bytes(original)
        self.assertEqual(original,path.read_bytes())
        self.assertEqual([],validate_cp5(self.root))

    def test_backward_anchor_oracle_and_quality_cannot_be_weakened(self):
        path=self.root/PLAN/'post-audit-contracts.json'
        original=path.read_bytes()
        for change, diagnostic in [
            (lambda x:x['backward_witness'].update(before_use=[]), 'backward manual witness'),
            (lambda x:x['backward_anchor_witness'].update(IN=[]), 'backward distinct-anchor witness'),
            (lambda x:x['quality']['metrics'].remove('unsupportedQueries'), 'precision quality metrics'),
            (lambda x:x['quality'].update(thresholds={'resolve_percent':95}), 'quality unmeasured/no threshold')]:
            path.write_bytes(original);self.edit(PLAN+'post-audit-contracts.json',change);self.guard(diagnostic)
        path.write_bytes(original)
        self.assertEqual([],validate_cp5(self.root))

    def test_ci_receipt_capture_and_documentation_cannot_disappear(self):
        path=self.root/'.github/workflows/ci.yml'
        path.write_text(path.read_text().replace('ci_source_receipt.py','missing_receipt.py'))
        self.guard('CI receipt workflow')
        (self.root/'docs/architecture/cp5-post-audit.md').unlink()
        self.guard('post-audit')

    def test_stable_solver_is_not_complete_pipeline(self):
        result = load_json(self.root/PLAN/'result-review.json')['result']
        result['completion'] = {
            'pipelineStatus': 'COMPLETE', 'publicationPolicy': 'EXPLICIT_PARTIAL_BY_PHASE',
            'admission': {'status': 'COMPLETE', 'reason': None},
            'analysis': {'status': 'STABLE', 'reason': None},
            'observation': {'status': 'LIMIT', 'reason': 'observation budget'},
            'consumers': [], 'publication': {'status': 'COMPLETE', 'reason': None}}
        result['observations'] = []
        errors = validate_result(result)
        self.assertTrue(any('phase completion' in e for e in errors), errors)
        result['completion']['pipelineStatus'] = 'INCOMPLETE'
        self.assertEqual([], validate_result(result, load_json(self.root/PLAN/'result-review.json')['design']['requestedQueries']))

    def test_valid_preparation(self):
        self.assertEqual([], validate_cp5(self.root))

    def test_wave_order_and_review_dependency(self):
        self.edit(LIFECYCLE, lambda x:x['waves'].reverse())
        self.guard('five Waves in order')
        self.edit(LIFECYCLE, lambda x:x['waves'][0].update(requires_review_of='HARNESS_PREPARATION'))
        self.guard('sequential Wave review')

    def test_each_wave_cannot_start_or_be_authorized(self):
        original = (self.root/LIFECYCLE).read_bytes()
        for index in range(5):
            for change in [dict(status='STARTED'),dict(authorization='AUTHORIZED')]:
                with self.subTest(wave=index+1,change=change):
                    (self.root/LIFECYCLE).write_bytes(original)
                    self.edit(LIFECYCLE, lambda x:x['waves'][index].update(change))
                    self.guard('NOT_STARTED / NOT_AUTHORIZED')

    def test_no_active_wave_pointer(self):
        self.edit(LIFECYCLE,lambda x:x.update(authorized_wave=1))
        self.guard('no Wave authorized')

    def test_discovery_approval_cannot_authorize_wave(self):
        self.edit(LIFECYCLE,lambda x:x['last_human_approval'].update(does_not_authorize_waves=False))
        self.guard('not Wave authorization')

    def test_no_fabricated_wave_completion(self):
        self.edit(LIFECYCLE,lambda x:x['waves'][0].update(completion_evidence='PASS'))
        self.guard('no Wave evidence')

    def test_single_draft_pr_policy(self):
        original = (self.root/LIFECYCLE).read_bytes()
        for change in [dict(draft=False),dict(auto_merge=True),dict(number=12,url='https://example.com',state='OPEN')]:
            (self.root/LIFECYCLE).write_bytes(original)
            self.edit(LIFECYCLE,lambda x:x['pr'].update(change))
            self.guard('PR')
        (self.root/LIFECYCLE).write_bytes(original)
        self.edit(LIFECYCLE,lambda x:x['policy'].update(auto_start_next_wave=True))
        self.guard('same branch/PR')

    def test_h4_candidates_not_frozen(self):
        self.edit(LIFECYCLE,lambda x:x['last_human_approval']['implementation_candidates_not_frozen'].remove('k=8'))
        self.guard('implementation-neutral H4')

    def test_adr_and_work_routing(self):
        self.edit(WORK,lambda x:x['related_decisions'].pop())
        self.guard('ADR routing')
        (self.root/'docs/architecture/decisions/ADR-0011.md').unlink()
        self.guard('accepted ADR')

    def test_java_pom_inventory_covers_existing_new_and_hidden_files(self):
        source = next((self.root/'cfg-kernel/src/main').rglob('*.java'))
        original = source.read_bytes()
        source.write_bytes(original+b'\n// unauthorized production change\n')
        self.guard('no unauthorized Java/POM')
        source.write_bytes(original)
        for relative in ['analysis-kernel/src/main/java/AnalysisSession.java','docs/Sneaky.java','.hidden/pom.xml']:
            with self.subTest(path=relative):
                p=self.root/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('')
                self.guard('no unauthorized Java/POM')
                p.unlink()

    def test_no_speculative_module_even_without_java(self):
        (self.root/'analysis-values').mkdir()
        self.guard('no speculative modules')

    def test_inventory_detects_missing_java(self):
        next((self.root/'cfg-kernel/src/main').rglob('*.java')).unlink()
        self.guard('no unauthorized Java/POM')

    def test_no_vendored_engine_bytecode(self):
        (self.root/'docs/engine.jar').write_bytes(b'not a product')
        self.guard('no vendored bytecode')

    def test_every_required_metric_is_guarded(self):
        path=PLAN+'metrics.json';original=(self.root/path).read_bytes()
        for name in sorted(METRICS):
            with self.subTest(metric=name):
                (self.root/path).write_bytes(original)
                self.edit(path,lambda x:x.update(metrics=[m for m in x['metrics'] if m['name']!=name]))
                self.guard('metric inventory')

    def test_metric_requires_auditable_event_and_scope(self):
        self.edit(PLAN+'metrics.json',lambda x:x['metrics'][0].update(event=''))
        self.guard('auditable metric')

    def test_s4b_removal_and_wrong_wave(self):
        path=PLAN+'probes.json'
        original=(self.root/path).read_bytes()
        self.edit(path,lambda x:x.update(probes=[p for p in x['probes'] if p['id']!='S4b']))
        self.guard('probe inventory')
        (self.root/path).write_bytes(original)
        self.edit(path,lambda x:next(p for p in x['probes'] if p['id']=='S4b').update(waves=[5]))
        self.guard('probe Wave routing')

    def test_probe_cannot_pass_for_document_existence(self):
        self.edit(PLAN+'probes.json',lambda x:x['probes'][0].update(status='PASS',hook='docs/engineering/cp5-performance.md'))
        self.guard('no false probe PASS')

    def test_probe_requires_dimension_and_known_metric(self):
        self.edit(PLAN+'probes.json',lambda x:x['probes'][0].update(dimension='',metrics=['madeUp']))
        self.guard('probe cost/oracle/metric')

    def test_each_required_challenge_is_guarded(self):
        path=PLAN+'challenges.json';original=(self.root/path).read_bytes()
        for ident in CHALLENGES:
            with self.subTest(challenge=ident):
                (self.root/path).write_bytes(original)
                self.edit(path,lambda x:x.update(challenges=[c for c in x['challenges'] if c['id']!=ident]))
                self.guard('challenge inventory')

    def test_challenge_restore_and_real_target_not_yet_available(self):
        self.edit(PLAN+'challenges.json',lambda x:x['stages'].remove('byte_exact_restore'))
        self.guard('challenge restore protocol')
        self.edit(PLAN+'challenges.json',lambda x:x['challenges'][0].update(target='planned.java',status='PASS'))
        self.guard('no fictitious engine mutant')

    def test_future_hook_cannot_be_empty_pass(self):
        self.edit(PLAN+'gate-plan.json',lambda x:x['waves'][0]['gates']['performance'].update(status='implemented',hook='true'))
        self.guard('no empty hook product PASS')

    def test_engine_eval_cannot_be_marked_implemented(self):
        self.edit('docs/evals/catalog.json',lambda x:next(e for e in x['evals'] if e['id']=='EVAL-CFG-035').update(status='implemented'))
        self.guard('no engine eval implemented')

    def test_existing_performance_stays_unavailable(self):
        self.edit('docs/engineering/gate-state.json',lambda x:x['product_gates']['performance'].update(status='implemented',hook='scripts/project/check_cp5_gate.py'))
        self.guard('performance remains UNAVAILABLE')

    def test_product_routes_are_never_preparation_pass(self):
        with contextlib.redirect_stdout(io.StringIO()):
            for wave in range(1,6):
                for category in ['architecture','semantic','performance','integration']:
                    self.assertEqual(3,run(self.root,category,wave))

    def test_direct_air_dependency_contracase(self):
        # A dependencyManagement entry and transitive cfg-kernel are insufficient.
        p=self.root/'synthetic';(p/'src/main/java').mkdir(parents=True)
        (p/'src/main/java/Consumer.java').write_text('import io.github.gustavo2358.air.model.Publication;')
        dep='<dependency><groupId>io.github.gustavo2358</groupId><artifactId>air-java</artifactId></dependency>'
        prefix='<project xmlns="http://maven.apache.org/POM/4.0.0">'
        (p/'pom.xml').write_text(prefix+'<dependencyManagement><dependencies>'+dep+'</dependencies></dependencyManagement></project>')
        self.assertTrue(check_direct_air(p))
        (p/'pom.xml').write_text(prefix+'<dependencies>'+dep+'</dependencies></project>')
        self.assertEqual([],check_direct_air(p))
        (p/'pom.xml').write_text(prefix+'<dependencies>'+dep.replace('</dependency>','<scope>test</scope></dependency>')+'</dependencies></project>')
        self.assertTrue(check_direct_air(p))

    def test_baseline_air_debt_is_explicit_and_exception_cannot_expand(self):
        self.assertTrue(any('cfg-launcher/pom.xml' in e for e in check_direct_air(self.root)))
        errors, findings = check_preparation_air(self.root)
        self.assertEqual([],errors)
        self.assertTrue(any('CP5-F01' in f for f in findings))
        source=next((self.root/'cfg-launcher/src/main').rglob('*.java'))
        source.write_text(source.read_text()+'\n// changed baseline\n')
        self.assertTrue(check_preparation_air(self.root)[0])

    def test_all_package_deny_rules_have_contracases(self):
        rules=load_json(self.root/PLAN/'architecture.json')
        for role,rule in rules['package_rules'].items():
            for dependency in rule['forbidden']:
                with self.subTest(role=role,dependency=dependency):
                    self.assertEqual([dependency],forbidden_dependencies(role,[dependency],rules))
            self.assertEqual([],forbidden_dependencies(role,['java.lang.Object'],rules))
        self.edit(PLAN+'architecture.json',lambda x:x['package_rules']['solver_spi']['forbidden'].pop())
        self.guard('architecture boundary')

    def test_approved_maven_dag_cannot_drift(self):
        self.edit(PLAN+'architecture.json',lambda x:x['modules']['analysis-values']['direct'].remove('air-java'))
        self.guard('approved module DAG')

    def test_preparation_cli_executes_checks_and_rejects_invalid_input(self):
        command=[sys.executable,str(self.root/'scripts/harness/validate_cp5.py'),'--root',str(self.root)]
        good=subprocess.run(command,capture_output=True,text=True)
        self.assertEqual(0,good.returncode,good.stdout+good.stderr)
        self.assertIn('preparation contracts only',good.stdout)
        self.edit(LIFECYCLE,lambda x:x.update(authorized_wave=1))
        bad=subprocess.run(command,capture_output=True,text=True)
        self.assertEqual(1,bad.returncode,bad.stdout+bad.stderr)
        self.assertIn('no Wave authorized',bad.stdout)

    def test_result_contract_cannot_drop_limits_or_change_version(self):
        self.edit(PLAN+'result-contract.json',lambda x:x.update(version='2.0.0',execution_statuses=['STABLE']))
        self.guard('result contract status/schema')

    def test_result_is_design_not_measurement(self):
        self.edit(PLAN+'result-review.json',lambda x:x['design'].update(execution='EXECUTED'))
        self.guard('result example is design only')
        self.edit(PLAN+'result-review.json',lambda x:x['result']['statistics'].update(measurements={'nodesPopped':3}))
        self.guard('design must not invent measurements')

    def test_mixed_snapshot_cannot_abort_batch_or_drop_unsupported_query(self):
        path = PLAN + 'result-review.json'
        original = (self.root/path).read_bytes()
        self.assertEqual([], validate_cp5(self.root))
        mutations = [
            ('abort', lambda x:x['result'].update(executionStatus='UNSUPPORTED', observations=[]), 'mixed batch must remain STABLE'),
            ('drop', lambda x:x['result'].update(observations=x['result']['observations'][:1]), 'requested query coverage'),
        ]
        for name, change, diagnostic in mutations:
            with self.subTest(mutant=name):
                try:
                    self.edit(path, change)
                    self.guard(diagnostic)
                finally:
                    (self.root/path).write_bytes(original)
        self.assertEqual(original, (self.root/path).read_bytes())
        self.assertEqual([], validate_cp5(self.root))

    def test_result_contract_cannot_drop_query_outcome_rules(self):
        self.edit(PLAN+'result-contract.json', lambda x:x.update(query_statuses=['VALUE']))
        self.guard('result contract query outcomes')

    def test_mixed_request_plan_cannot_be_removed_with_unsupported_response(self):
        self.edit(PLAN+'result-review.json', lambda x:x['design']['requestedQueries'].pop())
        self.edit(PLAN+'result-review.json', lambda x:x['result']['observations'].pop())
        self.guard('mixed batch requires two requested queries')


class ResultContractTests(unittest.TestCase):
    def setUp(self):
        self.example=load_json(ROOT/PLAN/'result-review.json')['result']

    def rejected_run(self, result, status):
        result.update(executionStatus=status, observations=[], limitReason='WORK_BUDGET' if status == 'ANALYSIS_LIMIT' else None)
        result['completion'].update(pipelineStatus='INCOMPLETE',
            admission={'status':'COMPLETE','reason':None} if status == 'ANALYSIS_LIMIT' else {'status':'REJECTED','reason':'profile or input rejected'},
            analysis={'status':'LIMIT','reason':'WORK_BUDGET'} if status == 'ANALYSIS_LIMIT' else {'status':'NOT_STARTED','reason':None},
            observation={'status':'NOT_STARTED','reason':None})

    def test_consumer_failure_keeps_other_completion_and_requires_full_plan(self):
        result = copy.deepcopy(self.example)
        result['completion'].update(pipelineStatus='INCOMPLETE', consumers=[
            {'id':'A','status':'COMPLETE','reason':None}, {'id':'B','status':'FAILED','reason':'extraction failed'}])
        self.assertEqual([], validate_result(result, requested_consumers=['A','B']))
        result['completion']['pipelineStatus'] = 'COMPLETE'
        self.assertTrue(any('phase completion' in e for e in validate_result(result)))
        result['completion']['pipelineStatus'] = 'INCOMPLETE'
        result['completion']['consumers'].pop()
        self.assertTrue(any('requested consumer coverage' in e for e in validate_result(result, requested_consumers=['A','B'])))

    def test_phase_limit_or_failure_cannot_rewrite_stable_solver_or_emit_partial_batch(self):
        for phase in ['observation','publication']:
            for status in ['LIMIT','FAILED']:
                with self.subTest(phase=phase,status=status):
                    result = copy.deepcopy(self.example)
                    result['completion'][phase] = {'status':status,'reason':'phase failure'}
                    result['completion']['pipelineStatus'] = 'INCOMPLETE'
                    if phase == 'observation':
                        self.assertTrue(any('atomic observation batch' in e for e in validate_result(result)))
                        result['observations'] = []
                    self.assertEqual([], validate_result(result))
                    result['executionStatus'] = 'ANALYSIS_LIMIT'
                    result['limitReason'] = 'wrong phase'
                    self.assertTrue(any('solver status is independent' in e for e in validate_result(result)))

    def test_completion_shape_reasons_and_partial_policy(self):
        for field in self.example['completion']:
            mutant = copy.deepcopy(self.example)
            del mutant['completion'][field]
            self.assertTrue(any('phase completion' in e for e in validate_result(mutant)))
        for change in [{'publicationPolicy':'IMPLICIT'}, {'observation':{'status':'LIMIT','reason':None}},
                       {'consumers':[{'id':'A','status':'COMPLETE','reason':None}]*2}]:
            mutant = copy.deepcopy(self.example)
            mutant['completion'].update(change)
            self.assertTrue(any('phase completion' in e for e in validate_result(mutant)))

    def mixed_batch(self):
        # Manual response oracle: no solver or expected-value regeneration.
        result = copy.deepcopy(self.example)
        before = copy.deepcopy(result['observations'][0])
        before.update(queryStatus='VALUE', queryReason=None)
        after = copy.deepcopy(before)
        after['point'].update(position='AFTER', outcome=None)
        after.update(queryStatus='UNSUPPORTED_POINT',
                     queryReason='No post-memory state is admitted after Return.',
                     reachability=None, value=None, sourceUnknownRemainder=None,
                     effectiveUnknownRemainder=None, precision=None,
                     premiseRefs=[], evidenceRefs=[], provenanceRefs=[])
        result['observations'] = [before, after]
        return result

    def test_mixed_query_outcomes_are_representable(self):
        result = self.mixed_batch()
        self.assertEqual('STABLE', result['executionStatus'])
        self.assertEqual(['PROGA'], result['observations'][0]['value']['enumerated'])
        self.assertEqual([], validate_result(result))

    def test_review_snapshot_preserves_supported_value_and_explicit_refusal(self):
        self.assertEqual('STABLE', self.example['executionStatus'])
        before, after = self.example['observations']
        self.assertEqual(['VALUE', 'UNSUPPORTED_POINT'], [before['queryStatus'], after['queryStatus']])
        self.assertEqual(['PROGA'], before['value']['enumerated'])
        self.assertEqual('BEFORE', before['point']['position'])
        self.assertEqual(dict(before['point'], position='AFTER'), after['point'])
        self.assertEqual(before['subject'], after['subject'])
        self.assertIsNone(after['value'])
        self.assertTrue(after['queryReason'])

    def test_batch_coverage_uses_independent_plan_and_full_query_identity(self):
        result = self.mixed_batch()
        requests = copy.deepcopy([{k:o[k] for k in ('point','subject')} for o in result['observations']])
        self.assertEqual([], validate_result(result, requests))
        # Deduplication and response order do not alter the set of queries.
        result['observations'].reverse()
        self.assertEqual([], validate_result(result, requests + requests))
        for kind in ['drop', 'duplicate', 'replace-point', 'replace-subject']:
            mutant = copy.deepcopy(result)
            if kind == 'drop': mutant['observations'].pop(0)
            elif kind == 'duplicate': mutant['observations'][0] = copy.deepcopy(mutant['observations'][1])
            elif kind == 'replace-point': mutant['observations'][0]['point']['operationId']['localId'] = 'other-op'
            else: mutant['observations'][0]['subject']['objectId']['localId'] = 'other-object'
            with self.subTest(kind=kind):
                self.assertIn('CP5 result: requested query coverage', validate_result(mutant, requests))

    def test_unsupported_query_requires_reason_and_cannot_claim_semantic_value(self):
        result = self.mixed_batch()
        mutations = {'queryReason':[None, '', '  '], 'reachability':['UNREACHABLE_IN_MODEL'],
                     'value':[result['observations'][0]['value']], 'sourceUnknownRemainder':[False, True],
                     'effectiveUnknownRemainder':[False, True], 'precision':[result['observations'][0]['precision']]}
        for field, values in mutations.items():
            for value in values:
                mutant = copy.deepcopy(result)
                mutant['observations'][1][field] = value
                with self.subTest(field=field, value=value):
                    self.assertTrue(any('unsupported point' in e for e in validate_result(mutant)))

    def test_query_status_and_reason_are_required_and_distinct_from_run_status(self):
        for field, value in [('queryStatus', 'STABLE'), ('queryStatus', 'UNSUPPORTED'), ('queryReason', 'refused')]:
            mutant = self.mixed_batch()
            mutant['observations'][0][field] = value
            self.assertTrue(validate_result(mutant), (field, value))
        for field in ['queryStatus', 'queryReason']:
            mutant = self.mixed_batch()
            del mutant['observations'][1][field]
            self.assertTrue(validate_result(mutant), field)

    def test_unsupported_query_still_checks_context_subject_and_refs(self):
        for path in ['point', 'subject', 'evidenceRefs']:
            mutant = self.mixed_batch()
            after = mutant['observations'][1]
            if path == 'point': after['point']['entryId']['unit'] = 'other'
            elif path == 'subject': after['subject']['objectId']['unit'] = 'other'
            else: after['evidenceRefs'] = [dict(after['point']['operationId'], unit='other')]
            with self.subTest(path=path): self.assertTrue(validate_result(mutant))

    def test_supported_after_requires_an_outcome_and_run_failures_remain_separate(self):
        result = self.mixed_batch()
        result['observations'] = result['observations'][:1]
        result['observations'][0]['point']['position'] = 'AFTER'
        self.assertIn('CP5 result: point outcome', validate_result(result))
        result['observations'][0]['point']['outcome'] = 'NORMAL'
        self.assertEqual([], validate_result(result))
        requests = [{'point': result['observations'][0]['point'], 'subject': result['observations'][0]['subject']}]
        for status in ['ANALYSIS_LIMIT', 'UNSUPPORTED', 'INVALID_INPUT']:
            self.rejected_run(result, status)
            self.assertEqual([], validate_result(result, requests))

    def test_example_and_honest_unknown_unreachable_saturation_limit(self):
        self.assertEqual([],validate_result(self.example))
        for kind in ['unknown','unreachable','saturated','limit']:
            result=copy.deepcopy(self.example);obs=result['observations'][0]
            if kind=='unknown':
                obs['value'].update(enumerated=[],modelValueRemainder=True)
                obs['precision']['model']='OPEN_IN_ADMITTED_MODEL'
            elif kind=='unreachable':
                obs.update(reachability='UNREACHABLE_IN_MODEL',value=None)
                obs['precision']['model']='UNREACHABLE_IN_MODEL'
            elif kind=='saturated':
                obs['value'].update(kind='Saturated',enumerated=[],modelValueRemainder=True,saturationReason='CARDINALITY_LIMIT')
                obs['precision']['model']='OPEN_IN_ADMITTED_MODEL'
            else:
                self.rejected_run(result, 'ANALYSIS_LIMIT')
            with self.subTest(kind=kind):self.assertEqual([],validate_result(result))

    def test_missing_fields_and_owner_mismatch(self):
        for field in self.example:
            result=copy.deepcopy(self.example);del result[field]
            self.assertTrue(validate_result(result),field)
        self.example['observations'][0]['subject']['objectId']['unit']='other'
        self.assertTrue(any('owner' in e for e in validate_result(self.example)))

    def test_partial_effective_remainder_and_precision(self):
        self.example['observations'][0]['effectiveUnknownRemainder']=False
        self.assertTrue(any('effective remainder' in e for e in validate_result(self.example)))
        self.example['sourceScope']['open']=False
        self.assertTrue(any('cannot close source' in e for e in validate_result(self.example)))

    def test_missing_key_cannot_be_closed_empty(self):
        self.example['observations'][0]['value']['enumerated']=[]
        self.assertTrue(any('reachable empty closed' in e for e in validate_result(self.example)))

    def test_limit_cannot_be_stable_or_emit_provisional_facts(self):
        self.example['limitReason']='WORK_BUDGET'
        self.assertTrue(any('budget limit' in e for e in validate_result(self.example)))
        self.example['executionStatus']='ANALYSIS_LIMIT'
        self.assertTrue(any('provisional facts' in e for e in validate_result(self.example)))

    def test_saturation_cannot_hide_reason_or_open_remainder(self):
        self.example['observations'][0]['value']['kind']='Saturated'
        self.assertTrue(any('saturation must expose' in e for e in validate_result(self.example)))


class CiReceiptTests(unittest.TestCase):
    def setUp(self):
        from ci_source_receipt import collect, validate_receipt
        self.collect, self.validate = collect, validate_receipt
        self.temp = tempfile.TemporaryDirectory(prefix='cp5-ci-receipt-')
        self.root = Path(self.temp.name)
        def git(*args, input=None):
            return subprocess.check_output(['git', '-c', 'user.name=Harness', '-c', 'user.email=harness@example.invalid', *args], cwd=self.root, input=input, text=True, stderr=subprocess.PIPE).strip()
        self.git = git
        git('init', '-q')
        (self.root/'source.txt').write_text('base\n')
        git('add','source.txt');git('commit','-qm','base')
        self.base = git('rev-parse','HEAD')
        (self.root/'source.txt').write_text('head\n')
        git('commit','-qam','head');self.head=git('rev-parse','HEAD')
        self.head_tree = git('rev-parse','HEAD^{tree}')
        self.merge = git('commit-tree',self.head_tree,'-p',self.base,'-p',self.head,input='synthetic merge\n')
        self.env={'GITHUB_EVENT_NAME':'pull_request','GITHUB_RUN_ID':'123','GITHUB_SHA':self.merge}
        self.event={'pull_request':{'head':{'sha':self.head},'base':{'sha':self.base}}}

    def tearDown(self): self.temp.cleanup()

    def test_real_local_git_exact_head_and_synthetic_merge_identical_tree(self):
        exact = self.collect(self.root,self.event,self.env)
        self.assertEqual('EXACT_COMMIT_CHECKOUT',exact['classification'])
        self.assertEqual([],self.validate(exact,self.head))
        self.git('checkout','--detach','-q',self.merge)
        merge = self.collect(self.root,self.event,self.env)
        self.assertEqual('SYNTHETIC_MERGE_IDENTICAL_TREE',merge['classification'])
        self.assertNotEqual(merge['actual_checkout_sha'],merge['pr_head_sha'])
        self.assertEqual(merge['head_tree_sha'],merge['checkout_tree_sha'])
        self.assertEqual([],self.validate(merge,self.head))
        merge['classification']='EXACT_COMMIT_CHECKOUT'
        self.assertTrue(self.validate(merge))

    def test_missing_evidence_cannot_claim_exact_checkout(self):
        receipt=self.collect(self.root,self.event,self.env)
        for field in receipt:
            mutant=copy.deepcopy(receipt);del mutant[field]
            with self.subTest(field=field):self.assertTrue(self.validate(mutant))
        mutant=copy.deepcopy(receipt);mutant['checkout_tree_sha']='0'*40
        self.assertTrue(self.validate(mutant))

    def test_different_tree_and_stale_source_are_not_head_evidence(self):
        self.git('checkout','--detach','-q',self.base)
        receipt=self.collect(self.root,self.event,self.env)
        self.assertEqual('DIFFERENT_TREE',receipt['classification'])
        self.assertTrue(self.validate(receipt,expected_head=self.base))
        receipt['classification']='SYNTHETIC_MERGE_IDENTICAL_TREE'
        self.assertTrue(self.validate(receipt))

    def test_push_has_no_invented_pr_base(self):
        env=dict(self.env,GITHUB_EVENT_NAME='push',GITHUB_SHA=self.head)
        receipt=self.collect(self.root,{'after':self.head},env)
        self.assertIsNone(receipt['pr_base_sha'])
        self.assertIsNone(receipt['pr_head_sha'])
        self.assertEqual([],self.validate(receipt))
        receipt['pr_base_sha']=self.base
        self.assertTrue(self.validate(receipt))

if __name__ == '__main__':
    unittest.main(verbosity=2)
