"""CORE-SIZE-001 manifest and review oracles; no admission, domain or solver runtime.

Closed role contracts protect what counts may DO, not words such as 'max'.
Waves must replace unavailable hooks with tests against the real implementation.
"""
from __future__ import annotations
import json
from pathlib import Path

DECISION = 'CORE-SIZE-001'
APPROVED_HEAD = 'e86a57c1f744bd499dd47326ebcb84d23c61ab2d'
PATH = 'docs/evals/cp5/core-size-contract.json'
ADR = 'docs/architecture/decisions/ADR-0014.md'
ROLES = {
    'admission': {'criteria':['STRUCTURAL_VALIDITY','SUPPORTED_SEMANTIC_PROFILE'],
                  'size_count_effect':'OBSERVE_ONLY', 'capacity_rejections':[]},
    'analysis_key': {'configuration_role':'SEMANTIC_IDENTITY', 'cp5_options':{},
                     'resource_policy_fields':[]},
    'solver': {'termination':'DOMAIN_FIXED_POINT', 'convergence_proof':'REQUIRED',
               'resource_interruptions':[], 'work_counters':'OBSERVE_ONLY'},
    'possible_values': {'profile':'scalar-text-direct@1',
                        'enumeration':'ALL_SEMANTIC_CANDIDATES',
                        'universe':'FINITE_PROGRAM_LITERALS_AND_ADMITTED_SEEDS',
                        'candidate_count_effect':'OBSERVE_ONLY',
                        'remainder_sources':['SEMANTIC_UNKNOWN','SOURCE_SCOPE_OPEN'],
                        'capacity_saturation':False},
    'planner_consumers': {'demand_count_effect':'MORE_WORK_SAME_COVERAGE',
                          'partial_policy':'EXPLICIT_PARTIAL_BY_DEPENDENCY',
                          'resource_partial_results':False},
    'composition': {'input_output_size_policy':None, 'upstream_caps':'EXTERNAL_SIZE_CAP_DEBT',
                    'delivery_statuses':['COMPLETE','FAILED']},
    'execution_boundary': {'infrastructure_knobs_in_core':[],
                           'resource_failure_semantic_mappings':{},
                           'resource_exhaustion':'EXTERNAL_PROCESS_FAILURE_NO_SEMANTIC_RESULT',
                           'internal_invariant_failure':'IMPLEMENTATION_DEFECT_NOT_COVERAGE'},
    'representation': {'checked_arithmetic':True, 'index_and_conversion_safety':True,
                       'overflow':'IMPLEMENTATION_DEFECT_NOT_SUPPORTED_SIZE_CONTRACT',
                       'integer_product_ceiling':None},
    'metrics': {'role':'OBSERVATION_ONLY', 'rejection_thresholds':{},
                 'required_observations':['nodesIndexed','edgesIndexed','operationsIndexed',
                    'worklistPushes','joinEntriesVisited','stateAllocations','stateBytesRetained',
                    'candidateCardinality','maxSparseBindings','maxWorklistSize'],
                 'external_measurements':['elapsedTime','heap','GC']},
}
WAVE_ROLES = {1:['admission','representation','metrics'],
              2:['solver','analysis_key','execution_boundary','metrics'],
              3:['possible_values','representation','metrics'],
              4:['planner_consumers','analysis_key','metrics'],
              5:['composition','execution_boundary','metrics']}
CHALLENGES = {
    'size-cap-rejects-valid-program': (1, 'Valid supported N/2N/4N all remain ACCEPTED.'),
    'max-nodes-admission': (1, 'Changing only node count cannot change admission.'),
    'max-edges-admission': (1, 'Changing only edge count cannot change admission.'),
    'max-operations-admission': (1, 'Changing only operation count cannot change admission.'),
    'max-objects-admission': (1, 'Changing only object count cannot change admission.'),
    'max-visits-aborts-analysis': (2, 'Valid convergent run ends at fixed point regardless of visits/pushes/joins.'),
    'resource-budget-changes-semantic-result': (2, 'No work/resource budget in solver SPI or semantic AnalysisKey.'),
    'candidate-count-causes-top': (3, 'All finite candidates survive N+1 including 9, 100 and 10000, without new remainder.'),
    'max-queries-admission': (4, 'Increasing query demand changes work, not supported coverage.'),
    'max-consumers-admission': (4, 'More consumers/sites/batches do not impose admission ceilings.'),
    'maximum-result-size': (5, 'Composition has no internal AIR/result/output byte ceiling.'),
    'catch-oom-as-analysis-limit': (5, 'OOM/container/host/timeout/disk exhaustion is not mapped to any semantic outcome.'),
}
PHASE_ENUMS = {
    'admission_statuses':['COMPLETE','REJECTED'],
    'analysis_statuses':['STABLE','NOT_STARTED'],
    'observation_statuses':['COMPLETE','FAILED','NOT_STARTED'],
    'consumer_statuses':['COMPLETE','FAILED','NOT_STARTED'],
    'preparation_statuses':['COMPLETE','INCOMPLETE'],
    'delivery_statuses':['COMPLETE','FAILED'],
    'admission_reasons':{'INVALID_INPUT':'INVALID_STRUCTURE','UNSUPPORTED':'UNSUPPORTED_PROFILE'},
    'observation_failure_reasons':['OBSERVATION_ERROR'],
    'consumer_failure_reasons':['CONSUMER_ERROR'],
    'delivery_failure_reasons':['ENCODING_FAILED','WRITE_FAILED','FINALIZATION_FAILED'],
}
SCALE_ORACLE = ('All sizes admitted; convergent analyses STABLE; all finite candidates preserved with unchanged semantic remainder. '
                'Infra failure is not a semantic outcome. Core size role contracts and runtime scale oracles both required.')
VALUES_ORACLE = ('All supported finite candidates preserved without count-induced remainder; U source-proportional; '
                 'sharing, convergence and pool release.')


def validate_scale_review(case: dict) -> list[str]:
    """Compare a review response with a separately declared finite input oracle.

    Counts are fixture parameters, not a real CFG. This cannot certify runtime scale.
    """
    errors = []
    def require(ok, message):
        if not ok: errors.append('CORE-SIZE-001 scale: ' + message)
    try:
        source, outcome = case['input'], case['outcome']
        require(source['structurally_valid'] is True and source['profile'] == 'scalar-text-direct@1', 'valid supported fixture')
        require(set(source['counts']) == {'nodes','edges','operations','objects','queries','visits'}, 'count dimensions')
        require(all(type(n) is int and n > 0 for n in source['counts'].values()), 'positive synthetic dimensions')
        require(outcome['admission'] == 'ACCEPTED', 'size cannot change admission')
        require(outcome['analysis'] == 'STABLE', 'work cannot abort convergent analysis')
        require(outcome['value_kind'] == 'Candidates', 'no cardinality TOP')
        # The synthetic fixture has closed branches each assigning one literal.
        # Equality of sets, not merely inclusion, catches truncation and spurious values.
        literals = source['branch_literals']
        require(len(literals) == len(set(literals)) and all(isinstance(x,str) for x in literals), 'finite distinct literals')
        candidates = outcome['candidates']
        require(len(candidates) == len(literals) and set(candidates) == set(literals), 'all semantic candidates preserved')
        require(outcome['model_remainder'] is False, 'candidate count cannot open a closed model')
    except (KeyError, TypeError, ValueError) as exc:
        errors.append('CORE-SIZE-001 scale: malformed review: ' + str(exc))
    return errors


def validate_size_contract(root: Path) -> list[str]:
    errors = []
    def require(ok, message):
        if not ok: errors.append('CORE-SIZE-001: ' + message)
    def read(path): return json.loads((root/path).read_text())
    try:
        data = read(PATH)
        require(set(data) == {'decision','adr','roles','waves','scale_review','status'}, 'closed manifest fields')
        require(data['decision'] == DECISION and data['adr'] == ADR and data['status'] == 'CONTRACT_ONLY', 'decision routing')
        require(set(data['roles']) == set(ROLES), 'role inventory')
        for name, expected in ROLES.items():
            require(data['roles'].get(name) == expected, 'role contract ' + name)
        require(data['waves'] == [dict(wave=n,roles=roles,probe='S16',
                status='NOT_AVAILABLE_UNTIL_IMPLEMENTED',hook=None) for n,roles in WAVE_ROLES.items()], 'Wave role hooks')
        require(data['scale_review'] == 'docs/evals/cp5/core-size-review.json', 'scale review route')
        review = read(data['scale_review'])
        require(review['execution'] == 'NOT_EXECUTED' and review['kind'] == 'REVIEW_SNAPSHOT', 'scale review is not engine evidence')
        require([c['scale'] for c in review['cases']] == ['N','2N','4N'], 'N/2N/4N coverage')
        for factor,case in zip([1,2,4],review['cases']):
            require(case['input']['counts'] == {k:v*factor for k,v in
                    {'nodes':20,'edges':27,'operations':30,'objects':1,'queries':9,'visits':40}.items()}, 'synthetic scale dimensions')
            require(case['input']['branch_literals'] == [f'literal-{n}' for n in range(9*factor)], 'independent literal fixture')
            errors += validate_scale_review(case)
        require((root/ADR).is_file() and 'Program size is not a semantic admission criterion' in (root/ADR).read_text(), 'normative ADR')
        work = read('docs/work/active/WORK-CFG-028/work-item.json')
        require(ADR in work['must_read'] and PATH in work['must_read'], 'must-read route')
        life = read('docs/work/cp5-lifecycle.json')
        approval, decision = life['review_history'][-2:]
        require(approval['decision'] == 'POST_AUDIT_REMEDIATION_APPROVED' and approval['reviewed_head'] == APPROVED_HEAD and
                approval['approved_remediations'] == ['A','B','C','D','E','F1','F2','F3','G','H','I'] and approval['authorized_wave'] is None, 'post-audit approval at exact HEAD')
        require(decision['decision'] == 'CORE_SIZE_UNBOUNDED_AUTHORIZED_FOR_HARNESS_REMEDIATION_ONLY' and
                decision['decision_id'] == DECISION and decision['authorized_wave'] is None, 'harness-only human authorization')
        current = life['core_size_remediation']
        require(current['review'] == 'AWAITING_HUMAN_REVIEW' and current['approved_post_audit_head'] == APPROVED_HEAD and
                current['authorization'] == 'AUTHORIZED_FOR_HARNESS_REMEDIATION_ONLY' and
                current['implementation_candidates'] == ['Patricia/radix','FIFO'], 'current review/candidates')
        arch = read('docs/evals/cp5/architecture.json')
        require(arch['core_size_contract'] == PATH and arch['resource_failure_semantic_mappings'] == {}, 'architecture capacity behavior')
        for wave in read('docs/evals/cp5/gate-plan.json')['waves']:
            require(wave['core_size_contract'] == PATH and wave['core_size_probe'] == 'S16', 'gate activation route')
        contract = read('docs/evals/cp5/result-contract.json')
        require(contract['core_size_decision'] == DECISION and contract['analysis_key_options'] == {}, 'result semantic configuration')
        require(contract['value_fields'] == ['domain','kind','enumerated','modelValueRemainder'], 'result value fields')
        for field, expected in PHASE_ENUMS.items():
            require(contract['completion'].get(field) == expected, 'phase enum/reason ' + field)
        probes = {p['id']:p for p in read('docs/evals/cp5/probes.json')['probes']}
        require(probes['S16']['oracle'] == SCALE_ORACLE and probes['S16']['kills'] == list(CHALLENGES), 'scale probe behavior')
        require(probes['S7']['oracle'] == VALUES_ORACLE, 'finite values probe behavior')
        metrics = read('docs/evals/cp5/metrics.json')
        require(metrics['role'] == 'OBSERVATION_ONLY', 'metrics are observations')
        challenges = {c['id']:c for c in read('docs/evals/cp5/challenges.json')['challenges']}
        for name,(wave,oracle) in CHALLENGES.items():
            c = challenges.get(name,{})
            require(c.get('wave') == wave and c.get('oracle') == oracle and c.get('probe') == 'S16', 'challenge ' + name)
    except (OSError, KeyError, TypeError, ValueError) as exc:
        errors.append('CORE-SIZE-001: missing/invalid contract: ' + str(exc))
    return errors
