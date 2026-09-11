"""Post-audit obligations only. No CFG builder, transfer or solver implementation."""
from __future__ import annotations
import json
from pathlib import Path
from cp5_phase_contract import validate_completion

CHECKPOINT = 'CP5_CORE_SIZE_UNBOUNDED_HARNESS_REMEDIATION'
START_HEAD = 'feb79d59cc72d7dbc6269b7cacfb74db58f90867'
AUDIT_SHA = '32d4542adcf31445cf35196a5f6d2496e8e3962c0bfa9d10c26615f6ceea5bc1'
DOC = 'docs/architecture/cp5-post-audit.md'
# Semantic properties, not container names, algorithms for experiments or product targets.
OBLIGATIONS = {
    'A': ['SUPPORTED_BUILDCFG_BOUNDARY', 'STATUS_IS_NOT_CERTIFICATE',
          'CANONICAL_SOURCE_IDENTITY', 'PROFILE_MEMBERSHIP_CORRELATION_COMPLETENESS',
          'LINEAR_INDEX_ADMISSION', 'REJECT_MUTILATED_GRAPH', 'NO_REBUILD_OR_SECOND_VALIDATOR'],
    'B': ['FORWARD_STABLE_IN_FIRST_TO_LAST', 'BACKWARD_STABLE_OUT_LAST_TO_FIRST',
          'PROGRAM_POINTS_DIRECTION_INVARIANT', 'BATCH_PREFIX_OR_SUFFIX_UNION'],
    'C': ['STORAGE_EFFECTS_BEFORE_FIXPOINT', 'DOMAIN_TRANSFERS_USE_EFFECTS',
          'CONSUMERS_AFTER_STABLE_FACTS', 'NO_CONSUMER_STATE_REPAIR',
          'UNKNOWN_EFFECT_IS_NOT_IDENTITY', 'INVOKE_REQUIRES_PIPELINE_SUPPORT'],
    'D': ['LOGICAL_VALUE_IS_NOT_BYTE_LAYOUT', 'BYTE_SLICE_REQUIRES_REGION_VIEW_CODEC_OR_PROVEN_REDUCTION',
          'MARGINAL_CANDIDATES_ARE_NOT_PATH_PAIRS', 'NO_GENERAL_RELATIONAL_DOMAIN_IN_CP5'],
    'E': ['ACTIVATION_ENTRY_IS_NOT_LOCAL_FRAME', 'RETURN_PAIRING_REQUIRES_CONTROL_SEMANTICS',
          'CONTEXT_INSENSITIVE_APPROXIMATION_MUST_BE_DECLARED'],
    'F': ['STABLE_ANALYSIS_SURVIVES_LATER_FAILURE', 'INCOMPLETE_PIPELINE_IS_EXPLICIT',
          'OBSERVATION_BATCH_ATOMIC', 'CONSUMER_COMPLETION_IS_INDEPENDENT',
          'EXPLICIT_PARTIAL_BY_PHASE', 'OUTPUT_SUCCESS_REQUIRES_EXTERNAL_DELIVERY_RECEIPT',
          'SIZE_IS_NOT_SEMANTIC_ADMISSION', 'CONSUMER_DEPENDENCIES_ARE_EXPLICIT',
          'STRUCTURAL_CONSUMER_NEEDS_ZERO_ANALYSES'],
    'G': ['MANUAL_EXPECTED', 'INDEPENDENT_RECOMPUTATION_SOLVER', 'FINITE_CONCRETE_SEMANTIC_ORACLE',
          'TEST_ONLY_SYNTHETIC_FINITE_ENUMERATION', 'NO_PRODUCTION_TRANSFER_JOIN_WORKLIST_REUSE',
          'CONCRETE_BEHAVIORS_INCLUDED_IN_ABSTRACT_RESULT', 'NO_SECOND_FRAMEWORK'],
    'H': ['QUALITY_WITH_COST', 'RAW_COUNTS_AND_DENOMINATORS', 'SAME_CORPUS_SEMANTIC_PROFILE',
          'NO_UNJUSTIFIED_QUALITY_THRESHOLD'],
    'I': ['HEAD_AND_BASE_AND_CHECKOUT_SHA', 'HEAD_AND_CHECKOUT_TREE_SHA', 'RUN_ID_AND_EVENT',
          'EXACT_COMMIT_IS_NOT_TREE_EQUIVALENCE', 'NO_REMOTE_CALL_IN_HARNESS'],
}
WAVES = {'A':[1], 'B':[2,3], 'C':[3,4], 'D':[3,4], 'E':[1,2,3],
         'F':[1,2,3,4,5], 'G':[2,3], 'H':[1,2,3,4,5], 'I':[5]}
QUALITY = {
    'queriesAnswered': (3, 'query_batch', 'distinct VALUE outcomes; includes unreachable, excludes refusal'),
    'unsupportedQueries': (3, 'query_batch', 'distinct UNSUPPORTED_POINT outcomes'),
    'queriesNotMaterialized': (3, 'query_batch', 'distinct planned queries with no committed outcome after batch failure'),
    'unsupportedStorageProfiles': (3, 'admission', 'analysis requests refused for insufficient storage semantics'),
    'unsupportedEffectProfiles': (3, 'admission', 'analysis requests refused for insufficient effect semantics'),
    'modelOpenResults': (3, 'query_batch', 'VALUE outcomes with modelValueRemainder true'),
    'sourceOpenResults': (3, 'query_batch', 'VALUE outcomes with sourceUnknownRemainder true'),
    'effectiveOpenResults': (3, 'query_batch', 'VALUE outcomes with effectiveUnknownRemainder true'),
    'closedInModelResults': (3, 'query_batch', 'reachable VALUE outcomes closed in admitted model'),
    'observationFailures': (3, 'session', 'observation batches ending with a controlled failure'),
    'consumerFailures': (4, 'extraction', 'consumer instances ending FAILED'),
    'publicationFailures': (5, 'output', 'publication attempts ending FAILED'),
}
CHALLENGES = {
    'missing-required-edge': (1, 'S11', 'Reject missing FALSE even if all surviving TRUE transitions are valid.'),
    'missing-required-sequence': (1, 'S11', 'Reject omitted AIR Sequence including unreachable/orphan Sequence.'),
    'foreign-sequence-source': (1, 'S11', 'Reject replacement Sequence instance, including same label/owner and altered instructions.'),
    'foreign-publication-entry': (1, 'S11', 'Reject foreign Publication or Entry instance even with equal-looking IDs.'),
    'duplicated-wrong-contextual-edge': (1, 'S11', 'Reject duplicate/context-mismatched transition; equal Branch targets retain two distinct outcomes.'),
    'backward-replay-from-wrong-anchor': (2, 'S12', 'use Y; def X; use X has IN={Y}, OUT={}; after last use={} rejects the wrong IN anchor independently of order.'),
    'backward-replay-forward-order': (2, 'S12', 'Reverse gen/kill order must preserve the manual before/after witness at each operation.'),
    'consumer-repairs-stale-state': (None, None, 'Future Invoke/effects slice: consumer cannot repair incorrectly preserved PROGA after BY REFERENCE may-write.'),
    'unknown-invoke-treated-as-no-effect': (None, None, 'Future Invoke/effects slice: absent effect proof must open affected storage before fixpoint.'),
    'stable-analysis-observation-failure-reported-as-full-success': (3, 'S14', 'STABLE solver + controlled observation failure => INCOMPLETE preparation with empty atomic batch; solver unchanged.'),
    'consumer-failure-reported-as-complete-output': (4, 'S14', 'Failed consumer leaves other completed consumer status intact and pipeline INCOMPLETE.'),
    'performance-optimization-by-lost-candidates': (3, 'S15', 'Fixed corpus/profile: compare all candidates and manual quality counts; loss of candidates changes expected outcomes.'),
    'performance-optimization-by-unsupported-everything': (3, 'S15', 'Fixed admitted queries: answered/unsupported counts and expected outcomes expose refusal replacing work.'),
    'shared-wrong-transfer-agreement': (2, 'S13', 'Manual and finite concrete inclusion oracle reject wrong gen/kill even when two abstract solvers agree.'),
}
CHALLENGES.update({
    'prepared-payload-certifies-own-delivery': (5, 'S14', 'Writer failure preserves prepared bytes and solver state; only external receipt FAILED, never COMPLETE.'),
    'observation-failure-blocks-independent-consumer': (4, 'S14', 'Failed QueryConsumer batch leaves zero-query StructuralConsumer COMPLETE; preparation INCOMPLETE.'),
})
PROBES = {'S11':[1], 'S12':[2,3], 'S13':[2,3], 'S14':[3,4,5], 'S15':[3,4,5]}


def validate_audit(root: Path) -> list[str]:
    errors = []
    def require(ok, reason):
        if not ok: errors.append('CP5 post-audit: ' + reason)
    def read(path): return json.loads((root/path).read_text())
    try:
        data = read('docs/evals/cp5/post-audit-contracts.json')
        require(data['schema_version'] == 1 and set(data['requirements']) == set(OBLIGATIONS), 'requirement inventory A-I')
        document = (root/DOC).read_text()
        for key, obligations in OBLIGATIONS.items():
            row = data['requirements'].get(key, {})
            require(row.get('obligations') == obligations and row.get('waves') == WAVES[key], 'obligations/routing ' + key)
            require(row.get('documentation') == DOC+'#'+key.lower() and '\n## '+key+' — ' in document, 'documentation ' + key)
            require(row.get('runtime_status') == 'IMPLEMENTED_BY_WAVE_HOOKS' and row.get('runtime_hook') is None, 'no engine claim ' + key)
        for key in OBLIGATIONS:
            expected_hooks = {str(n):{'status':'IMPLEMENTED','hook':f'scripts/project/check_w{n}.py'} for n in (1,2,3,4,5) if n in WAVES[key]}
            require(data['requirements'][key].get('wave_hooks',{}) == expected_hooks, 'W1/W2/W3/W4/W5 audit hook '+key)
        witness = data['backward_witness']
        require(witness == {'language':'TEST_ONLY_GEN_KILL', 'operations':['def X','use X'],
                'OUT':[], 'after_use':[], 'before_use':['X'], 'after_def':['X'], 'before_def':[],
                'execution':'NOT_EXECUTED', 'waves':[2,3]}, 'backward manual witness')
        require(data['backward_anchor_witness'] == {'language':'TEST_ONLY_GEN_KILL',
                'operations':['use Y','def X','use X'],'IN':['Y'],'OUT':[],
                'after_last':[],'before_last':['X'],'after_def':['X'],'before_def':[],
                'after_first':[],'before_first':['Y'],'execution':'NOT_EXECUTED','waves':[2,3]}, 'backward distinct-anchor witness')
        work = read('docs/work/history/WORK-CFG-028/work-item.json')
        require(DOC in work['must_read'] and 'docs/evals/cp5/post-audit-contracts.json' in work['must_read'], 'must-read routing')
        require(data['quality']['metrics'] == ['queryRequests','uniqueQueries','candidateCardinality'] + list(QUALITY), 'precision quality metrics')
        require(data['quality']['thresholds'] is None and data['quality']['measurements'] is None, 'quality unmeasured/no threshold')
        require(data['quality']['coverage_equation'] == 'uniqueQueries = queriesAnswered + unsupportedQueries + queriesNotMaterialized', 'query denominators')
        require(data['future_invoke_hook'] == {'slice':'POST_CP5_INVOKE_EFFECTS','status':'NOT_AVAILABLE_UNTIL_IMPLEMENTED','hook':None}, 'future Invoke hook')
        workflow = (root/'.github/workflows/ci.yml').read_text()
        require('scripts/project/ci_source_receipt.py --output .harness-results/ci-source-receipt.json' in workflow and
                'name: ci-source-receipt' in workflow and 'actions/upload-artifact@v4' in workflow, 'CI receipt workflow capture/artifact')
        require((root/'scripts/project/ci_source_receipt.py').is_file(), 'CI receipt collector')
        metrics = {m['name']:m for m in read('docs/evals/cp5/metrics.json')['metrics']}
        for name,(wave,scope,event) in QUALITY.items():
            require(name in metrics and metrics[name]['available_by_wave'] == wave and metrics[name]['scope'] == scope and metrics[name]['event'] == event, 'precision metric definition '+name)
        probes = read('docs/evals/cp5/probes.json')['probes']
        for probe in probes:
            if probe['id'] in {'S3','S6','S9','S10'}:
                require(probe.get('quality_companion') == 'S15', 'quality companion probe')
        plan = read('docs/evals/cp5/gate-plan.json')
        for wave in plan['waves']:
            require(wave['audit_requirements'] == [k for k in OBLIGATIONS if wave['wave'] in WAVES[k]], 'gate requirement routing')
        life = read('docs/work/cp5-lifecycle.json')
        require(life['audit_remediation'] == {'decision':'KEEP_ARCHITECTURE_WITH_FOCUSED_REMEDIATIONS',
                'authorization':'HARNESS_REMEDIATION_ONLY', 'review':'AWAITING_HUMAN_REVIEW',
                'start_head':START_HEAD, 'audit_sha256':AUDIT_SHA,
                'evidence':'docs/work/evidence/WORK-CFG-028/architectural-audit-remediation/validation.md'}, 'current authorization/evidence')
        require(life['review_history'][0] == {'date':'2026-09-09','source':'explicit user post-audit remediation request',
                'discovery':'APPROVED','harness_preparation':'APPROVED','B1':'APPROVED',
                'CP5-F01':'NONBLOCKING_FOLLOW_UP_W1','authorized_wave':None}, 'approved history and nonblocking F01')
        require(life['review_history'][1] == {'date':'2026-09-09','source':'explicit human review of post-audit PR #12',
                'reviewed_head':'e053f14f8f5dc7b0b80b7bbbe015fde684522835','decision':'REQUEST_CHANGES_F_ONLY',
                'approved_remediations':['A','B','C','D','E','G','H','I'],'requested_changes':['F1','F2','F3'],
                'CP5-F01':'NONBLOCKING_FOLLOW_UP_W1','authorized_wave':None}, 'F-only human review')
        require(life['f_correction'] == {'reviewed_head':'e053f14f8f5dc7b0b80b7bbbe015fde684522835',
                'authorization':'F1_F2_F3_ONLY','review':'AWAITING_HUMAN_REVIEW',
                'evidence':'docs/work/evidence/WORK-CFG-028/review-f/validation.md'}, 'F-only authorization')
        from cp5_phase_contract import validate_prepared
        phase_review = read('docs/evals/cp5/history/phase-review.json')
        require(phase_review['design']['kind'] == 'REVIEW_SNAPSHOT' and phase_review['design']['execution'] == 'NOT_EXECUTED', 'F snapshot design only')
        prepared = phase_review['prepared']
        errors += validate_prepared(prepared, phase_review['design']['requestedConsumers'], phase_review['design']['requestedQueriesByBatch'])
        require(prepared['preparationStatus'] == 'INCOMPLETE' and len(prepared['results']) == 1, 'F3 failed batch witness')
        result = prepared['results'][0]['result']
        require(result['executionStatus'] == 'STABLE' and result['completion']['observation']['status'] == 'FAILED', 'F3 stable analysis failed observation')
        require(prepared['consumers'] == [{'id':'StructuralConsumer','status':'COMPLETE','reason':None},
                {'id':'QueryConsumer','status':'NOT_STARTED','reason':'DEPENDENCY_UNAVAILABLE'}], 'F3 independent consumer witness')
        require(prepared['consumerPlan'][0] == {'consumerId':'StructuralConsumer','requiredAnalysisKeys':[],
                'requiredObservationBatchIds':[]}, 'F3 zero-analysis structural plan')
        evidence = read('docs/work/evidence/WORK-CFG-028/architectural-audit-remediation/baseline.json')
        require(evidence['audit']['sha256'] == AUDIT_SHA and evidence['audit']['read'] == 'integral', 'integral audit hash')
        require(evidence['analysis-cfg']['head'] == START_HEAD, 'real starting head')
    except (OSError, KeyError, TypeError, ValueError, IndexError) as exc:
        errors.append('CP5 post-audit: missing/invalid contract: ' + str(exc))
    return errors
