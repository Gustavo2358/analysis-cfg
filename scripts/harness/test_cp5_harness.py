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
from check_analysis_architecture import check_direct_air, forbidden_dependencies
from check_cp5_gate import run
from cp5_phase_contract import validate_prepared, validate_delivery_receipt

def prepared_for(result):
    return {'schema':'prepared-analysis-result', 'version':'1.0.0', 'resultId':'prepared-review',
            'publicationId':copy.deepcopy(result['publicationId']),
            'results':[{'observationBatchId':'batch','result':result}],
            'consumerPlan':[],'consumers':[],'preparationStatus':'COMPLETE',
            'partialPolicy':'EXPLICIT_PARTIAL_BY_DEPENDENCY'}


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
        result['completion']['observation'] = {'status':'FAILED','reason':'OBSERVATION_ERROR'}
        result['observations'] = []
        prepared = prepared_for(result)
        self.assertTrue(any('incomplete preparation' in e for e in validate_prepared(prepared)))
        prepared['preparationStatus'] = 'INCOMPLETE'
        self.assertEqual([], validate_prepared(prepared))

    def test_f3_global_barrier_snapshot_is_rejected_and_restored(self):
        path=self.root/PLAN/'phase-review.json';original=path.read_bytes()
        self.edit(PLAN+'phase-review.json',lambda x:x['prepared']['consumers'][0].update(status='NOT_STARTED',reason='GLOBAL_BARRIER'))
        self.guard('F3 independent consumer witness')
        path.write_bytes(original)
        self.assertEqual([],validate_cp5(self.root))

    def test_f1_and_f2_contract_fields_cannot_disappear(self):
        path=self.root/PLAN/'result-contract.json';original=path.read_bytes()
        self.edit(PLAN+'result-contract.json',lambda x:x['completion']['admission_statuses'].remove('REJECTED'))
        self.guard('F admission/delivery status contract')
        path.write_bytes(original)
        self.edit(PLAN+'result-contract.json',lambda x:x['completion']['delivery_receipt_fields'].remove('resultSha256'))
        self.guard('phase completion contract')
        path.write_bytes(original)
        self.assertEqual([],validate_cp5(self.root))

    def test_size_roles_have_parseable_counterexamples_and_exact_restore(self):
        path = self.root/PLAN/'core-size-contract.json'
        original = path.read_bytes()
        self.assertEqual([],validate_cp5(self.root))
        mutations = [
            ('node-count-admission','admission', lambda r:r.update(criteria=['STRUCTURAL_VALIDITY','SUPPORTED_SEMANTIC_PROFILE','NODE_COUNT'])),
            ('operation-count-admission','admission', lambda r:r['capacity_rejections'].append({'field':'operations','above':100,'outcome':'UNSUPPORTED'})),
            ('query-count-admission','planner_consumers', lambda r:r.update(demand_count_effect='REJECT_ABOVE_100')),
            ('maxCandidates','possible_values', lambda r:r.update(maxCandidates=8)),
            ('CARDINALITY_LIMIT','possible_values', lambda r:r.update(capacity_saturation=True)),
            ('resourceBudgets','analysis_key', lambda r:r['resource_policy_fields'].append('resourceBudgets')),
            ('max-visits-aborts','solver', lambda r:r['resource_interruptions'].append({'field':'visits','above':100,'outcome':'LIMIT'})),
            ('worklist-aborts','solver', lambda r:r.update(termination='MAX_WORKLIST_PUSHES')),
            ('oom-mapping','execution_boundary', lambda r:r['resource_failure_semantic_mappings'].update(OutOfMemoryError='ANALYSIS_LIMIT')),
            ('overflow-is-product-ceiling','representation', lambda r:r.update(integer_product_ceiling=2147483647)),
            ('metric-as-policy','metrics', lambda r:r['rejection_thresholds'].update(maxWorklistSize=100)),
            ('output-cap','composition', lambda r:r.update(input_output_size_policy={'maxBytes':100})),
        ]
        for name,role,change in mutations:
            with self.subTest(mutant=name):
                try:
                    self.edit(PLAN+'core-size-contract.json',lambda x:change(x['roles'][role]))
                    json.loads(path.read_bytes())  # parseable mutant, not incidental syntax RED
                    self.guard('CORE-SIZE-001: role contract '+role)
                    print('[core-size challenge] '+name+': parseable RED')
                finally:
                    path.write_bytes(original)
                self.assertEqual(original,path.read_bytes())
                self.assertEqual([],validate_cp5(self.root))
        print('[core-size challenge] all role mutants: byte-exact restore / second GREEN')

    def test_size_scale_outcomes_have_independent_oracle_and_restore(self):
        path = self.root/PLAN/'core-size-review.json'; original = path.read_bytes()
        self.assertEqual([],validate_cp5(self.root))
        for name,change,diagnostic in [
            ('candidate-N-plus-1-open',lambda x:x.update(model_remainder=True),'candidate count cannot open'),
            ('candidate-N-plus-1-top',lambda x:x.update(value_kind='Saturated'),'no cardinality TOP'),
            ('candidate-dropped',lambda x:x['candidates'].pop(),'all semantic candidates'),
            ('large-unsupported',lambda x:x.update(admission='UNSUPPORTED'),'size cannot change admission'),
            ('large-invalid',lambda x:x.update(admission='INVALID_INPUT'),'size cannot change admission'),
            ('work-exhausted',lambda x:x.update(analysis='LIMIT'),'work cannot abort')]:
            with self.subTest(mutant=name):
                try:
                    self.edit(PLAN+'core-size-review.json',lambda x:change(x['cases'][1]['outcome']))
                    json.loads(path.read_bytes())
                    self.guard('CORE-SIZE-001 scale: '+diagnostic)
                    print('[core-size challenge] '+name+': parseable RED')
                finally: path.write_bytes(original)
                self.assertEqual(original,path.read_bytes())
                self.assertEqual([],validate_cp5(self.root))
        print('[core-size challenge] scale outcomes: byte-exact restore / second GREEN')

    def test_architecture_cannot_map_oom_to_semantics(self):
        path=self.root/PLAN/'architecture.json';original=path.read_bytes()
        self.assertEqual([],validate_cp5(self.root))
        try:
            self.edit(PLAN+'architecture.json',lambda x:x['resource_failure_semantic_mappings'].update(OutOfMemoryError='ANALYSIS_LIMIT'))
            json.loads(path.read_bytes())
            self.guard('CORE-SIZE-001: architecture capacity behavior')
        finally: path.write_bytes(original)
        self.assertEqual(original,path.read_bytes())
        self.assertEqual([],validate_cp5(self.root))

    def test_size_decision_routing_and_approval_cannot_disappear(self):
        for path,change,diagnostic in [
            (LIFECYCLE,lambda x:x['review_history'].pop(3),'CORE-SIZE-001'),
            (LIFECYCLE,lambda x:x['core_size_remediation']['implementation_candidates'].append('k=8'),'current review/candidates'),
            (PLAN+'core-size-contract.json',lambda x:x['roles'].pop('solver'),'role inventory'),
            (PLAN+'core-size-contract.json',lambda x:x['waves'][0].update(hook='fake'),'Wave role hooks'),
            (PLAN+'gate-plan.json',lambda x:x['waves'][0].pop('core_size_probe'),'CORE-SIZE-001')]:
            original=(self.root/path).read_bytes()
            try:
                self.edit(path,change);self.guard(diagnostic)
            finally:(self.root/path).write_bytes(original)
            self.assertEqual([],validate_cp5(self.root))

    def test_valid_preparation(self):
        self.assertEqual([], validate_cp5(self.root))

    def test_wave_order_and_review_dependency(self):
        self.edit(LIFECYCLE, lambda x:x['waves'].reverse())
        self.guard('five Waves in order')
        self.edit(LIFECYCLE, lambda x:x['waves'][0].update(requires_review_of='HARNESS_PREPARATION'))
        self.guard('sequential Wave review')

    def test_each_wave_cannot_start_or_be_authorized(self):
        original = (self.root/LIFECYCLE).read_bytes()
        for index in range(3,5):
            for change in [dict(status='STARTED'),dict(authorization='AUTHORIZED')]:
                with self.subTest(wave=index+1,change=change):
                    (self.root/LIFECYCLE).write_bytes(original)
                    self.edit(LIFECYCLE, lambda x:x['waves'][index].update(change))
                    self.guard('NOT_STARTED / NOT_AUTHORIZED')

    def test_no_active_wave_pointer(self):
        self.edit(LIFECYCLE,lambda x:x.update(authorized_wave=4))
        self.guard('only Wave 3 authorized')

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

    def test_historical_h4_approval_is_not_rewritten(self):
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
        (self.root/'analysis-consumers').mkdir()
        self.guard('no speculative consumer modules')

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
        self.guard('concrete challenge target/hook')

    def test_future_hook_cannot_be_empty_pass(self):
        self.edit(PLAN+'gate-plan.json',lambda x:x['waves'][0]['gates']['performance'].update(status='implemented',hook='true'))
        self.guard('no empty hook product PASS')

    def test_engine_eval_cannot_be_marked_implemented(self):
        self.edit('docs/evals/catalog.json',lambda x:next(e for e in x['evals'] if e['id']=='EVAL-CFG-037').update(status='implemented'))
        self.guard('no engine eval implemented')

    def test_existing_performance_stays_unavailable(self):
        self.edit('docs/engineering/gate-state.json',lambda x:x['product_gates']['performance'].update(status='implemented',hook='scripts/project/check_cp5_gate.py'))
        self.guard('performance remains UNAVAILABLE')

    def test_product_routes_are_never_preparation_pass(self):
        with contextlib.redirect_stdout(io.StringIO()):
            for wave in range(4,6):
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

    def test_launcher_has_strict_direct_dependency_without_exception(self):
        self.assertEqual([],check_direct_air(self.root))
        pom=self.root/'cfg-launcher/pom.xml'
        original=pom.read_text()
        import re
        pom.write_text(re.sub(r'    <dependency>\s*<groupId>io.github.gustavo2358</groupId>\s*<artifactId>air-java</artifactId>\s*</dependency>\n','',original))
        self.assertTrue(any('cfg-launcher/pom.xml' in e for e in check_direct_air(self.root)))
        self.guard('strict direct AIR dependencies')
        pom.write_text(original)
        self.assertEqual([],check_direct_air(self.root))

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
        self.assertIn('W1/W2/W3 contracts only',good.stdout)
        self.edit(LIFECYCLE,lambda x:x.update(authorized_wave=4))
        bad=subprocess.run(command,capture_output=True,text=True)
        self.assertEqual(1,bad.returncode,bad.stdout+bad.stderr)
        self.assertIn('only Wave 3 authorized',bad.stdout)

    def test_w1_authorization_and_runtime_hooks_are_required(self):
        for path,change,reason in [
            (LIFECYCLE,lambda x:x['waves'][2].update(status='APPROVED'),'never human-approved'),
            (LIFECYCLE,lambda x:x['review_history'][4].update(reviewed_head='0'*40),'W1 reviewed HEAD'),
            (PLAN+'probes.json',lambda x:x['probes'][0].pop('wave_hooks'),'W1/W2/W3 real probe'),
            (PLAN+'gate-plan.json',lambda x:x['waves'][0]['gates']['performance'].update(hook=None),'no empty hook')]:
            original=(self.root/path).read_bytes()
            try:self.edit(path,change);self.guard(reason)
            finally:(self.root/path).write_bytes(original)
            self.assertEqual([],validate_cp5(self.root))

    def test_w1_nominal_reports_and_metrics_cannot_be_fabricated(self):
        from check_w1 import verify_reports, verify_metrics, Failure
        with self.assertRaises(Failure):verify_reports(self.root,{'ScaleTest'})
        for output in ['', 'W1_METRICS {}']:
            with self.assertRaises((Failure,KeyError)):verify_metrics(output)

    def test_w2_authorization_and_approval_cannot_be_inferred(self):
        for path,change,reason in [
            (LIFECYCLE,lambda x:x['waves'][2].update(status='APPROVED'),'never human-approved'),
            (LIFECYCLE,lambda x:x['waves'][0].update(reviewed_head='0'*40),'W1 explicit human-approved HEAD'),
            (LIFECYCLE,lambda x:x['review_history'][5].update(reviewed_head='0'*40),'W2 reviewed HEAD'),
            (PLAN+'gate-plan.json',lambda x:x['waves'][1]['gates']['semantic'].update(hook=None),'no empty hook')]:
            original=(self.root/path).read_bytes()
            try:self.edit(path,change);self.guard(reason)
            finally:(self.root/path).write_bytes(original)
            self.assertEqual([],validate_cp5(self.root))

    def test_w2_nominal_metrics_corpus_and_reports_are_required(self):
        from check_w2 import verify_reports,verify_metrics,verify_corpus,Failure
        with self.assertRaises(Failure):verify_reports(self.root,{'SolverScaleTest'})
        for output in ['', 'W2_METRICS {}']:
            with self.assertRaises((Failure,KeyError)):verify_metrics(output)
        for output in ['', 'W2_CORPUS {}']:
            with self.assertRaises((Failure,KeyError)):verify_corpus(output)

    def test_w3_authorization_hooks_and_approved_w2_are_required(self):
        for path,change,reason in [
            (LIFECYCLE,lambda x:x['waves'][2].update(status='APPROVED'),'never human-approved'),
            (LIFECYCLE,lambda x:x['waves'][1].update(reviewed_head='0'*40),'W2 explicit human-approved HEAD'),
            (LIFECYCLE,lambda x:x['review_history'][6].update(reviewed_head='0'*40),'W3 reviewed HEAD'),
            (PLAN+'gate-plan.json',lambda x:x['waves'][2]['gates']['semantic'].update(hook=None),'no empty hook'),
            (PLAN+'probes.json',lambda x:next(p for p in x['probes'] if p['id']=='S6')['wave_hooks'].pop('3'),'real probe')]:
            original=(self.root/path).read_bytes()
            try:self.edit(path,change);self.guard(reason)
            finally:(self.root/path).write_bytes(original)
            self.assertEqual([],validate_cp5(self.root))

    def test_w3_nominal_metrics_and_oracles_cannot_be_empty(self):
        from check_w3 import verify_reports,verify_metrics,verify_corpus,Failure
        with self.assertRaises(Failure):verify_reports(self.root,{'ValuesScaleTest'})
        for output in ['', 'W3_METRICS {}']:
            with self.assertRaises((Failure,KeyError)):verify_metrics(output)
        for output in ['', 'W3_CORPUS {}']:
            with self.assertRaises((Failure,KeyError)):verify_corpus(output)

    def test_result_contract_cannot_drop_semantic_statuses_or_change_version(self):
        self.edit(PLAN+'result-contract.json',lambda x:x.update(version='2.0.0',execution_statuses=['STABLE']))
        self.guard('result contract status/schema')

    def test_wire_capacity_enums_and_options_are_rejected_with_exact_restore(self):
        from cp5_size_contract import PHASE_ENUMS
        path = self.root/PLAN/'result-contract.json'
        original = path.read_bytes()
        self.assertEqual([],validate_cp5(self.root))
        changes = [(field, lambda x,field=field:x['completion'][field].append('LIMIT'))
                   for field,value in PHASE_ENUMS.items() if isinstance(value,list)]
        changes += [('resourceBudgets',lambda x:x['analysis_key_options'].update(resourceBudgets={'visits':100})),
                    ('maxCandidates',lambda x:x['analysis_key_options'].update(maxCandidates=8)),
                    ('ADMISSION_LIMIT',lambda x:x['execution_statuses'].append('ADMISSION_LIMIT')),
                    ('ANALYSIS_LIMIT',lambda x:x['execution_statuses'].append('ANALYSIS_LIMIT'))]
        for name,change in changes:
            with self.subTest(mutant=name):
                try:
                    self.edit(PLAN+'result-contract.json',change)
                    json.loads(path.read_bytes())
                    self.assertTrue(validate_cp5(self.root))
                    print('[core-size challenge] wire '+name+': parseable RED')
                finally: path.write_bytes(original)
                self.assertEqual(original,path.read_bytes())
                self.assertEqual([],validate_cp5(self.root))

    def test_scale_oracles_and_metric_role_cannot_be_weakened(self):
        changes = [(PLAN+'probes.json',lambda x:next(p for p in x['probes'] if p['id']=='S16').update(oracle='Reject large input')),
                   (PLAN+'probes.json',lambda x:next(p for p in x['probes'] if p['id']=='S7').update(oracle='TOP after 8')),
                   (PLAN+'metrics.json',lambda x:x.update(role='ADMISSION_THRESHOLDS'))]
        for path,change in changes:
            original=(self.root/path).read_bytes()
            self.assertEqual([],validate_cp5(self.root))
            try:
                self.edit(path,change)
                json.loads((self.root/path).read_bytes())
                self.guard('CORE-SIZE-001')
            finally: (self.root/path).write_bytes(original)
            self.assertEqual(original,(self.root/path).read_bytes())
            self.assertEqual([],validate_cp5(self.root))

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
        result.update(executionStatus=status, observations=[])
        result['completion'].update(admission={'status':'REJECTED','reason':
            'INVALID_STRUCTURE' if status == 'INVALID_INPUT' else 'UNSUPPORTED_PROFILE'},
            analysis={'status':'NOT_STARTED','reason':None}, observation={'status':'NOT_STARTED','reason':None})

    def test_consumer_failure_keeps_other_completion_and_requires_full_plan(self):
        prepared = prepared_for(copy.deepcopy(self.example))
        plans = [{'consumerId':name,'requiredAnalysisKeys':[],'requiredObservationBatchIds':[]} for name in ['A','B']]
        prepared.update(preparationStatus='INCOMPLETE', consumerPlan=copy.deepcopy(plans), consumers=[
            {'id':'A','status':'COMPLETE','reason':None}, {'id':'B','status':'FAILED','reason':'CONSUMER_ERROR'}])
        self.assertEqual([], validate_prepared(prepared, plans))
        prepared['preparationStatus'] = 'COMPLETE'
        self.assertTrue(any('incomplete preparation' in e for e in validate_prepared(prepared)))
        prepared['preparationStatus'] = 'INCOMPLETE'
        prepared['consumers'].pop()
        self.assertTrue(any('requested consumer coverage' in e for e in validate_prepared(prepared, plans)))

    def test_controlled_failure_cannot_rewrite_stable_solver_or_emit_partial_batch(self):
        for phase in ['observation']:
            for status in ['FAILED']:
                with self.subTest(phase=phase,status=status):
                    result = copy.deepcopy(self.example)
                    result['completion'][phase] = {'status':status,'reason':'OBSERVATION_ERROR'}
                    if phase == 'observation':
                        self.assertTrue(any('atomic observation batch' in e for e in validate_result(result)))
                        result['observations'] = []
                    self.assertEqual([], validate_result(result))
                    result['executionStatus'] = 'ANALYSIS_LIMIT'
                    result['limitReason'] = 'wrong phase'
                    self.assertTrue(any('execution status' in e for e in validate_result(result)))

    def test_completion_shape_reasons_and_partial_policy(self):
        for field in self.example['completion']:
            mutant = copy.deepcopy(self.example)
            del mutant['completion'][field]
            self.assertTrue(any('phase completion' in e for e in validate_result(mutant)))
        for change in [{'publication':{'status':'COMPLETE','reason':None}},
                       {'observation':{'status':'LIMIT','reason':None}}, {'consumers':[]}]:
            mutant = copy.deepcopy(self.example)
            mutant['completion'].update(change)
            self.assertTrue(any('phase completion' in e for e in validate_result(mutant)))

    def test_f1_semantic_admission_never_maps_capacity_to_rejection(self):
        for status in ['INVALID_INPUT','UNSUPPORTED']:
            result = copy.deepcopy(self.example)
            self.rejected_run(result,status)
            self.assertEqual([],validate_result(result))
            for reason in ['ADMISSION_BUDGET','WORK_BUDGET','OutOfMemoryError','MAX_NODES']:
                result['completion']['admission']['reason'] = reason
                with self.subTest(status=status,reason=reason):
                    self.assertTrue(any('semantic admission reason' in e for e in validate_result(result)))

    def test_f2_result_cannot_self_certify_delivery(self):
        self.assertNotIn('publication', self.example['completion'])
        self.assertNotIn('publicationPolicy', self.example['completion'])

    def test_f3_dependency_aware_prepared_result_contract_exists(self):
        import importlib.util
        self.assertIsNotNone(importlib.util.find_spec('cp5_phase_contract'), 'F3 dependency-aware contract absent')
        import cp5_phase_contract as phase
        self.assertTrue(callable(getattr(phase, 'validate_prepared', None)),
                        'F3 needs a dependency-aware prepared-result validator')

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
        for status in ['UNSUPPORTED', 'INVALID_INPUT']:
            self.rejected_run(result, status)
            self.assertEqual([], validate_result(result, requests))

    def test_example_and_honest_unknown_unreachable(self):
        self.assertEqual([],validate_result(self.example))
        for kind in ['unknown','unreachable']:
            result=copy.deepcopy(self.example);obs=result['observations'][0]
            if kind=='unknown':
                obs['value'].update(enumerated=[],modelValueRemainder=True)
                obs['precision']['model']='OPEN_IN_ADMITTED_MODEL'
            elif kind=='unreachable':
                obs.update(reachability='UNREACHABLE_IN_MODEL',value=None)
                obs['precision']['model']='UNREACHABLE_IN_MODEL'
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
        self.assertTrue(any('required result fields' in e for e in validate_result(self.example)))
        self.example['executionStatus']='ANALYSIS_LIMIT'
        self.assertTrue(any('provisional facts' in e for e in validate_result(self.example)))

    def test_saturation_cannot_be_reintroduced(self):
        self.example['observations'][0]['value']['kind']='Saturated'
        self.assertTrue(any('value kind' in e for e in validate_result(self.example)))



class FCompletionTests(unittest.TestCase):
    def setUp(self):
        self.snapshot = load_json(ROOT/PLAN/'phase-review.json')
        self.prepared = copy.deepcopy(self.snapshot['prepared'])
        self.plans = copy.deepcopy(self.snapshot['design']['requestedConsumers'])
        self.queries = copy.deepcopy(self.snapshot['design']['requestedQueriesByBatch'])

    def validate(self):
        return validate_prepared(self.prepared, self.plans, self.queries)

    def test_structural_consumer_survives_controlled_failed_query_batch(self):
        self.assertEqual([], self.validate())
        run = self.prepared['results'][0]['result']
        self.assertEqual('STABLE', run['executionStatus'])
        self.assertNotIn('limitReason',run)
        self.assertEqual([], run['observations'])
        self.assertEqual(['COMPLETE','NOT_STARTED'], [c['status'] for c in self.prepared['consumers']])
        self.assertEqual('INCOMPLETE', self.prepared['preparationStatus'])
        self.prepared['consumers'][1].update(status='COMPLETE',reason=None)
        self.assertTrue(any('dependent consumers blocked' in e for e in self.validate()))

    def test_structural_only_needs_no_fake_analysis(self):
        self.prepared.update(results=[],consumerPlan=self.plans[:1],consumers=self.prepared['consumers'][:1],preparationStatus='COMPLETE')
        self.assertEqual([], validate_prepared(self.prepared,self.plans[:1],{}))

    def test_analysis_only_consumer_does_not_depend_on_observation(self):
        plan={'consumerId':'AnalysisOnly','requiredAnalysisKeys':self.plans[1]['requiredAnalysisKeys'],'requiredObservationBatchIds':[]}
        self.plans.append(plan);self.prepared['consumerPlan'].append(copy.deepcopy(plan))
        self.prepared['consumers'].append({'id':'AnalysisOnly','status':'COMPLETE','reason':None})
        self.assertEqual([], self.validate())

    def test_independent_batches_and_consumer_dependencies(self):
        result=load_json(ROOT/PLAN/'result-review.json')['result']
        self.prepared['results'].append({'observationBatchId':'independent','result':result})
        self.queries['independent']=load_json(ROOT/PLAN/'result-review.json')['design']['requestedQueries']
        plan={'consumerId':'IndependentQueries','requiredAnalysisKeys':[result['analysisKey']], 'requiredObservationBatchIds':['independent']}
        self.plans.append(plan);self.prepared['consumerPlan'].append(copy.deepcopy(plan))
        self.prepared['consumers'].append({'id':'IndependentQueries','status':'COMPLETE','reason':None})
        self.assertEqual([], self.validate())
        self.prepared['consumerPlan'][-1]['requiredObservationBatchIds']=['query-batch']
        self.assertTrue(any('independent consumer plan' in e for e in self.validate()))
        self.assertTrue(any('dependent consumers blocked' in e for e in self.validate()))

    def test_dependencies_cannot_be_removed_or_rebound_silently(self):
        original=copy.deepcopy(self.prepared)
        for mutation in ['drop-consumer','drop-result','drop-plan','drop-dependency','foreign-key','foreign-batch','duplicate-result']:
            self.prepared=copy.deepcopy(original)
            if mutation=='drop-consumer':self.prepared['consumers'].pop()
            elif mutation=='drop-result':self.prepared['results'].clear()
            elif mutation=='drop-plan':self.prepared['consumerPlan'].pop()
            elif mutation=='drop-dependency':self.prepared['consumerPlan'][1]['requiredObservationBatchIds']=[]
            elif mutation=='foreign-key':self.prepared['consumerPlan'][1]['requiredAnalysisKeys'][0]['entryId']['localId']='foreign'
            elif mutation=='foreign-batch':self.prepared['consumerPlan'][1]['requiredObservationBatchIds']=['foreign']
            else:self.prepared['results'].append(copy.deepcopy(self.prepared['results'][0]))
            with self.subTest(mutation=mutation):self.assertTrue(self.validate())

    def test_writer_failure_keeps_prepared_bytes_and_cannot_claim_complete(self):
        import hashlib
        payload=json.dumps(self.prepared,sort_keys=True).encode()
        digest=hashlib.sha256(payload).hexdigest()
        before=copy.deepcopy(self.prepared)
        for status in ['FAILED','COMPLETE']:
            receipt={'schema':'analysis-delivery-receipt','version':'1.0.0','resultId':self.prepared['resultId'],'resultSha256':digest,
                     'destination':'review://prepared.json','status':status,'reason':None if status=='COMPLETE' else 'WRITE_FAILED'}
            self.assertEqual([], validate_delivery_receipt(receipt,payload,status))
            if status!='COMPLETE':
                receipt.update(status='COMPLETE',reason=None)
                self.assertTrue(any('independent delivery outcome' in e for e in validate_delivery_receipt(receipt,payload,status)))
        self.assertEqual(before,self.prepared)
        self.assertEqual(payload,json.dumps(self.prepared,sort_keys=True).encode())
        self.assertEqual([],self.validate())
        self.assertEqual('STABLE',self.prepared['results'][0]['result']['executionStatus'])

    def test_delivery_failure_before_hash_does_not_require_buffering_or_fake_hash(self):
        receipt={'schema':'analysis-delivery-receipt','version':'1.0.0','resultId':self.prepared['resultId'],
                 'resultSha256':None,'destination':'review://out','status':'FAILED','reason':'ENCODING_FAILED'}
        self.assertEqual([],validate_delivery_receipt(receipt,None,'FAILED',self.prepared['resultId']))
        self.assertTrue(validate_delivery_receipt(receipt,None,'FAILED','foreign-result'))
        self.assertTrue(validate_delivery_receipt(receipt,None,'FAILED',self.prepared['resultId'],'review://foreign'))
        receipt.update(status='COMPLETE',reason=None)
        self.assertTrue(validate_delivery_receipt(receipt,None,'COMPLETE',self.prepared['resultId']))

    def test_receipt_correlation_and_self_certification_are_guarded(self):
        import hashlib
        payload=json.dumps(self.prepared).encode()
        receipt={'schema':'analysis-delivery-receipt','version':'1.0.0','resultId':self.prepared['resultId'],'resultSha256':hashlib.sha256(payload).hexdigest(),
                 'destination':'review://out','status':'COMPLETE','reason':None}
        self.assertEqual([],validate_delivery_receipt(receipt,payload,'COMPLETE'))
        self.assertTrue(validate_delivery_receipt(receipt,payload+b'\n','COMPLETE'))
        for field in receipt:
            mutant=copy.deepcopy(receipt);del mutant[field]
            with self.subTest(field=field):self.assertTrue(validate_delivery_receipt(mutant,payload))
        for field in ['publication','deliveryReceipt']:
            mutant=copy.deepcopy(self.prepared);mutant[field]=receipt
            self.assertTrue(any('exclude delivery' in e for e in validate_prepared(mutant)))
        run=self.prepared['results'][0]['result']
        run['completion']['publication']={'status':'COMPLETE','reason':None}
        self.assertTrue(any('exclude consumers and delivery' in e for e in validate_result(run)))

class CoreSizeContractTests(unittest.TestCase):
    """Wire/manifest counterexamples only; actual engine hooks remain unavailable."""

    def setUp(self):
        self.result = load_json(ROOT/PLAN/'result-review.json')['result']

    def test_analysis_key_has_no_resource_options(self):
        self.assertEqual({}, self.result['analysisKey']['options'])
        for field in ['resourceBudgets', 'maxCandidates', 'maxNodes', 'maxQueries', 'k']:
            mutant = copy.deepcopy(self.result)
            mutant['analysisKey']['options'][field] = 8
            with self.subTest(field=field):
                self.assertTrue(validate_result(mutant))

    def test_capacity_outcomes_cannot_be_reintroduced(self):
        for status in ['ADMISSION_LIMIT', 'ANALYSIS_LIMIT']:
            mutant = copy.deepcopy(self.result)
            mutant.update(executionStatus=status, observations=[], limitReason='WORK_BUDGET')
            with self.subTest(status=status): self.assertTrue(validate_result(mutant))
        for phase in ['admission', 'analysis', 'observation']:
            mutant = copy.deepcopy(self.result)
            mutant['completion'][phase] = {'status':'LIMIT', 'reason':'QUERY_BUDGET'}
            mutant['observations'] = []
            with self.subTest(phase=phase): self.assertTrue(validate_result(mutant))

    def test_all_finite_candidates_are_representable(self):
        for count in [1, 8, 9, 100, 10000]:
            result = copy.deepcopy(self.result)
            result['observations'][0]['value']['enumerated'] = [str(i) for i in range(count)]
            with self.subTest(count=count): self.assertEqual([], validate_result(result))

    def test_capacity_cannot_hide_in_failed_phase_or_delivery(self):
        for reason in ['WORK_BUDGET','QUERY_BUDGET','OutOfMemoryError','INFRA_TIMEOUT','DISK_EXHAUSTION']:
            with self.subTest(reason=reason):
                result = copy.deepcopy(self.result)
                result['completion']['observation'] = {'status':'FAILED','reason':reason}
                result['observations'] = []
                self.assertTrue(validate_result(result))
                prepared = prepared_for(copy.deepcopy(self.result))
                prepared.update(consumerPlan=[{'consumerId':'A','requiredAnalysisKeys':[],'requiredObservationBatchIds':[]}],
                                consumers=[{'id':'A','status':'FAILED','reason':reason}],preparationStatus='INCOMPLETE')
                self.assertTrue(validate_prepared(prepared))
                prepared['consumers'][0]['status'] = 'LIMIT'
                self.assertTrue(validate_prepared(prepared))
                receipt = {'schema':'analysis-delivery-receipt','version':'1.0.0','resultId':'review',
                           'resultSha256':None,'destination':'review://out','status':'FAILED','reason':reason}
                self.assertTrue(validate_delivery_receipt(receipt,None,'FAILED'))
                receipt['status'] = 'LIMIT'
                self.assertTrue(validate_delivery_receipt(receipt,None,'LIMIT'))

    def test_cardinality_saturation_is_not_a_value_kind(self):
        mutant = copy.deepcopy(self.result)
        mutant['observations'][0]['value'].update(kind='Saturated', enumerated=[],
            modelValueRemainder=True, saturationReason='CARDINALITY_LIMIT')
        mutant['observations'][0]['precision']['model'] = 'OPEN_IN_ADMITTED_MODEL'
        self.assertTrue(validate_result(mutant))


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
