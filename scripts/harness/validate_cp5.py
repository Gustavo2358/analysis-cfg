#!/usr/bin/env python3
"""CP5 preparation contract checks. Does not execute or certify a dataflow engine."""
from __future__ import annotations
import hashlib
import json
import re
from pathlib import Path
from validate_docs import existing, files_under, load_json
import cp5_audit_contract as audit
import cp5_phase_contract as phases
import cp5_size_contract as size
import cp5_w5_contract as w5

BASE = 'ec525cbbad96d70c9663faa88e2672148fa8ee71'
LIFECYCLE = 'docs/work/cp5-lifecycle.json'
PLAN = 'docs/evals/cp5/'
WORK = 'docs/work/active/WORK-CFG-028/work-item.json'
PROBES = {'S1': [1, 3, 5], 'S2': [1, 3, 5], 'S3': [3, 5], 'S4': [1, 2, 5],
          'S4b': [2, 3, 5], 'S5': [4, 5], 'S6': [3, 4, 5], 'S7': [3, 5],
          'S8': [1, 2, 4, 5], 'S9': [3, 5], 'S10': [5]}
CHALLENGES = {
    'predecessor-recomputation': 2, 'propagate-unchanged-out': 2,
    'enqueue-unchanged-join': 2, 'lose-self-loop-reenqueue': 2,
    'full-state-clone': 3, 'scan-objects-per-assign': 1, 'scan-global-successors': 1,
    'identity-by-display-name': 1, 'ignore-activation-entry': 1,
    'missing-key-as-bottom': 3, 'non-convergent-semantic-domain': 3, 'silent-truncation': 3,
    'unknown-effect-as-nop': 3, 'consumer-starts-solver': 4,
    'full-traversal-per-consumer': 4, 'broadcast-i-times-k': 4,
    'replay-per-query-site': 3, 'emit-provisional-facts': 4,
    'partial-promoted-to-exact': 3, 'mutate-shared-root': 3, 'reapply-entry-seed': 2,
    'backward-only-at-exits': 2, 'identity-only-equality': 2,
    'storage-id-implies-disjoint': 3, 'solver-concrete-domain-dependency': 2,
    'transitive-air-only': 1, 'cache-ignores-options-entry': 4,
    'fixture-specific-production-route': 5,
    'unsupported-query-aborts-batch': 3, 'unsupported-query-disappears': 3,
}
CHALLENGES.update({'missing-first-publication': 2, 'skip-edge-transfer': 2, 'skip-block-transfer': 2, 'apply-block-twice': 2, 'block-uses-wrong-state': 2, 'boundary-omitted': 2, 'wrong-boundary-side': 2, 'ignore-self-loop': 2, 'ignore-context-state': 2, 'duplicate-enqueue': 2, 'wrong-backward-direction': 2, 'wrong-backward-publication': 2, 'max-iterations': 2})
METRICS = set('''nodesIndexed edgesIndexed operationsIndexed referencesResolved objectsIndexed
locationsIndexed structuralVisits firstPublications nodesPopped nodesTransferred
operationsTransferred initializationAttempts edgeContributionJoins edgeTransferInvocations
accumulatorStatesChanged accumulatorStatesUnchanged publishedStatesChanged publishedStatesUnchanged
predecessorContributionReads boundaryJoins worklistAttempts worklistPushes duplicatePushesSuppressed
maxWorklistSize joinEntriesVisited stateCompareEntries stateAllocations stateBytesAllocated
stateBytesRetained stateRootsRetained maxSparseBindings setElementsRetained valuesInterned poolHits
bytesHashed candidateCardinality candidateSites siteMatches consumerInvocations factsEmitted queryRequests
uniqueQueries sequencesReplayed operationsReplayed analysisRuns analysisCacheHits'''.split())
CHALLENGES.update({k:v[0] for k,v in audit.CHALLENGES.items()})
PROBES.update(audit.PROBES)
METRICS.update(audit.QUALITY)
CHALLENGES.update({k:v[0] for k,v in size.CHALLENGES.items()})
CHALLENGES.update({'solver-per-consumer': 4, 'solver-per-observation-batch': 4, 'replay-per-consumer': 4, 'analysis-only-consumer-forced-to-depend-on-batch': 4, 'structural-consumer-forced-to-start-analysis': 4, 'consumer-partial-facts-committed-on-failure': 4, 'missing-consumer-dependency-treated-as-success': 4, 'wrong-batch-bound-to-analysis-key': 4, 'wrong-provider-execution-binding': 4, 'site-query-zero-match-drops-required-batch': 4, 'unplanned-query-triggers-hidden-replay': 4, 'global-static-run-cache': 4, 'resource-cap-on-sites': 4, 'resource-cap-on-facts': 4, 'resource-cap-on-batches': 4, 'drop-W3-support-or-remainder-before-consumer': 4, 'w4-unsupported-everything': 4, 'w4-drop-candidates': 4})
METRICS.update({'analysisRequests', 'factsDiscarded', 'consumersComplete', 'planningCallbacks', 'filterEvaluations', 'observationBatchesPlanned', 'observationBatchesExecuted', 'factsStaged', 'factsCommitted', 'consumersNotStarted', 'planningEpochs', 'notRequested', 'analysisAdmissionCacheHits'})
PROBES['S16'] = [1,2,3,4,5]
CHALLENGES.update(w5.CHALLENGES)
METRICS.update(w5.METRICS)
RESULT_FIELDS = set('''schema version analysisKey publicationId unitId entryId executionStatus
modelScope sourceScope observations statistics completion'''.split())
OBS_FIELDS = set('''point subject queryStatus queryReason reachability value sourceUnknownRemainder effectiveUnknownRemainder
precision premiseRefs evidenceRefs provenanceRefs'''.split())
QUERY_STATUSES = {'VALUE', 'UNSUPPORTED_POINT'}
UNSUPPORTED_NULL_FIELDS = {'reachability', 'value', 'sourceUnknownRemainder',
                           'effectiveUnknownRemainder', 'precision'}


def validate_result(result: dict, requested_queries: list[dict] | None = None) -> list[str]:
    """Review shape/consistency and optional external batch coverage; no AIR execution."""
    errors = audit.validate_completion(result)
    def require(ok, reason):
        if not ok: errors.append('CP5 result: ' + reason)
    try:
        require(set(result) == RESULT_FIELDS, 'required result fields')
        require((result['schema'], result['version']) == ('analysis-dataflow-result', '1.0.0'), 'schema/version')
        status = result['executionStatus']
        require(status in phases.EXECUTION_STATUSES, 'execution status')
        require(result['modelScope'] == 'KNOWN_GRAPH_ENTRY', 'model scope')
        pub = result['publicationId']['localId']
        unit = result['unitId']['localId']
        require(result['publicationId'] == {'domain': 'publication', 'localId': pub} and bool(pub), 'PublicationId')
        def check_id(value, domain, has_unit=False):
            keys = {'domain', 'localId', 'publication'} | ({'unit'} if has_unit else set())
            require(isinstance(value, dict) and set(value) == keys, 'full ID fields for ' + domain)
            require(value['domain'] == domain and value['publication'] == pub and
                    isinstance(value['localId'], str) and bool(value['localId']), 'ID owner/domain for ' + domain)
            if has_unit: require(value['unit'] == unit, 'Unit owner for ' + domain)
        check_id(result['unitId'], 'unit')
        check_id(result['entryId'], 'entry', True)
        key = result['analysisKey']
        require(set(key) == {'implementation', 'implementationVersion', 'profile', 'direction', 'precisionPolicy', 'options', 'entryId'}, 'analysis key fields')
        require(key['entryId'] == result['entryId'], 'analysis key context')
        require(key['direction'] in {'FORWARD', 'BACKWARD'}, 'direction')
        require(all(isinstance(key[x], str) and key[x] for x in ['implementation','implementationVersion','profile','precisionPolicy']), 'analysis identity')
        require(key['options'] == {}, 'CORE-SIZE-001: CP5 semantic options only; no resource/candidate cap')
        source = result['sourceScope']
        require(set(source) == {'open', 'inventory', 'dimension', 'scope'}, 'source scope fields')
        require(type(source['open']) is bool and source['inventory'] in {'COMPLETE','PARTIAL','UNAVAILABLE'}, 'source scope status')
        require(source['dimension'] == 'CONTROL' and source['scope'] == result['unitId'], 'source scope owner')
        require(source['inventory'] == 'COMPLETE' or source['open'], 'PARTIAL/UNAVAILABLE cannot close source')
        require(isinstance(result['observations'], list), 'observations array')
        if status != 'STABLE': require(result['observations'] == [], 'non-stable cannot emit provisional facts')
        if status == 'STABLE' and result['completion']['observation']['status'] == 'COMPLETE' and requested_queries is not None:
            # The caller supplies the plan independently of the response. Repeated
            # requests share one outcome for the same complete point + subject.
            require(isinstance(requested_queries, list) and
                    all(set(q) == {'point', 'subject'} for q in requested_queries), 'requested query fields')
            def query_key(query):
                return json.dumps({k: query[k] for k in ('point', 'subject')}, sort_keys=True)
            expected = {query_key(q) for q in requested_queries}
            actual = [query_key(o) for o in result['observations']]
            require(len(actual) == len(expected) and set(actual) == expected, 'requested query coverage')
        for obs in result['observations']:
            require(set(obs) == OBS_FIELDS, 'required observation fields')
            query_status = obs['queryStatus']
            require(query_status in QUERY_STATUSES, 'query status')
            point, subject = obs['point'], obs['subject']
            require(set(point) == {'position','operationId','entryId','outcome'}, 'program point fields')
            check_id(point['operationId'], 'operation', True)
            require(point['entryId'] == result['entryId'] and point['position'] in {'BEFORE','AFTER'}, 'program point/context')
            require((point['position'] == 'BEFORE' and point['outcome'] is None) or
                    (point['position'] == 'AFTER' and (
                        (isinstance(point['outcome'], str) and bool(point['outcome'])) or
                        (query_status == 'UNSUPPORTED_POINT' and point['outcome'] is None))), 'point outcome')
            require(set(subject) == {'place','objectId','storageId','locationKind'}, 'subject/place/storage fields')
            check_id(subject['objectId'], 'object', True); check_id(subject['storageId'], 'storage')
            require(subject['place'] == 'ObjectPlace' and subject['locationKind'] == 'WHOLE_CELL', 'location profile')
            for name, domain in [('premiseRefs','premise'),('evidenceRefs','operation'),('provenanceRefs','origin')]:
                require(isinstance(obs[name], list), name + ' array')
                for ref in obs[name]: check_id(ref, domain, domain == 'operation')
            if query_status == 'UNSUPPORTED_POINT':
                require(isinstance(obs['queryReason'], str) and bool(obs['queryReason'].strip()), 'unsupported point requires query reason')
                require(all(obs[field] is None for field in UNSUPPORTED_NULL_FIELDS), 'unsupported point cannot claim value/reachability/precision/remainders')
                continue
            require(obs['queryReason'] is None, 'VALUE has no query refusal reason')
            require(type(obs['sourceUnknownRemainder']) is bool and type(obs['effectiveUnknownRemainder']) is bool, 'boolean remainders')
            require(obs['sourceUnknownRemainder'] == source['open'], 'source remainder cannot be hidden')
            value = obs['value']
            if obs['reachability'] == 'UNREACHABLE_IN_MODEL':
                require(value is None, 'unreachable point is not empty Candidates')
                require(obs['effectiveUnknownRemainder'] == source['open'], 'unreachable model is not source proof')
            else:
                require(obs['reachability'] == 'REACHABLE', 'reachability')
                require(isinstance(value, dict) and set(value) == {'domain','kind','enumerated','modelValueRemainder'}, 'value fields')
                require(value['domain'] == 'known(text)', 'value domain')
                vals = value['enumerated']
                require(isinstance(vals, list) and all(isinstance(x,str) for x in vals) and len(vals) == len(set(vals)), 'distinct text candidates')
                require(type(value['modelValueRemainder']) is bool, 'model remainder type')
                require(value['kind'] == 'Candidates', 'CORE-SIZE-001: value kind preserves all finite candidates')
                require(bool(vals) or value['modelValueRemainder'], 'reachable empty closed value forbidden')
                require(obs['effectiveUnknownRemainder'] == (value['modelValueRemainder'] or source['open']), 'effective remainder OR')
            precision = obs['precision']
            require(set(precision) == {'model','source','pathWitness'}, 'precision fields')
            require(precision['source'] == ('OPEN' if source['open'] else 'CLOSED'), 'source precision')
            require(precision['pathWitness'] == 'NOT_PROVIDED', 'no invented path witness')
            require(precision['model'] in {'CLOSED_IN_ADMITTED_MODEL','OPEN_IN_ADMITTED_MODEL','UNREACHABLE_IN_MODEL'}, 'model precision')
            if value is not None:
                require(precision['model'] == ('OPEN_IN_ADMITTED_MODEL' if value['modelValueRemainder'] else 'CLOSED_IN_ADMITTED_MODEL'), 'model precision/remainder consistency')
            else: require(precision['model'] == 'UNREACHABLE_IN_MODEL', 'unreachable precision')
        require(result['statistics'] == {'status':'NOT_AVAILABLE_UNTIL_IMPLEMENTED','measurements':None}, 'design must not invent measurements')
    except (KeyError, TypeError, ValueError) as exc:
        errors.append('CP5 result: malformed shape: ' + str(exc))
    return errors


def validate_cp5(root: Path) -> list[str]:
    errors = audit.validate_audit(root) + size.validate_size_contract(root) + w5.validate(root)
    def require(ok, reason):
        if not ok: errors.append('CP5: ' + reason)
    def rows(items, key, expected, label):
        names = [x[key] for x in items]
        require(len(names) == len(set(names)) and set(names) == set(expected), label + ' inventory')
    try:
        life = load_json(root / LIFECYCLE)
        work = load_json(root / WORK)
        require(life['schema_version'] == 1, 'lifecycle schema')
        require(life['work_item'] == work['id'] == 'WORK-CFG-028' and life['backlog_id'] == work['backlog_id'] == 'BACKLOG-CFG-020', 'work/backlog linkage')
        require(life['branch'] == 'feat/cp5-dataflow-engine' and life['base_main'] == BASE, 'branch/main baseline')
        require(life['current_checkpoint'] == work['checkpoint'] == 'WAVE_5', 'W5 checkpoint only')
        require(life['authorized_wave'] == 5, 'only Wave 5 authorized')
        require(life['harness_preparation'] == {'status':'implemented','review':'APPROVED'}, 'preparation review state')
        require(work['authorization'] == 'implementation' and work['status'] == 'active', 'harness authorization')
        policy = life['policy']
        require(policy == {'same_branch_same_pr':True,'draft_until_explicit_human_change':True,'human_review_between_waves':True,'auto_start_next_wave':False,'commits_per_wave':'unrestricted_focused','no_intermediate_merge_required':True}, 'same branch/PR and review policy')
        approval = life['last_human_approval']
        require(set(approval['approved']) == {'H1','H2','H3','H4_PROPERTIES','H5','H6','H7','R1','R2'} and approval['does_not_authorize_waves'] is True, 'approved architecture is not Wave authorization')
        require(set(approval['implementation_candidates_not_frozen']) == {'Patricia/radix','FIFO','k=8'}, 'implementation-neutral H4')
        require(approval['source'] and approval['date'] and approval['decision'] == 'APPROVED_DISCOVERY_AND_HARNESS_PREPARATION_ONLY', 'human evidence')
        pr = life['pr']
        require(pr['draft'] is False and pr['auto_merge'] is False, 'approved CP5 PR without auto-merge')
        if pr['number'] is None:
            require(pr['url'] is None and pr['state'] == 'NOT_CREATED', 'pending PR metadata')
        else:
            require(type(pr['number']) is int and pr['number'] > 0 and pr['state'] == 'MERGED' and pr['url'] == f"https://github.com/Gustavo2358/analysis-cfg/pull/{pr['number']}", 'PR linkage')
        require([w['wave'] for w in life['waves']] == list(range(1,6)), 'five Waves in order')
        for w in life['waves']:
            n = w['wave']
            if n == 1:
                require(w['status'] == 'APPROVED' and w['authorization'] == 'AUTHORIZED' and life['wave_1']['review'] == 'APPROVED' and w.get('reviewed_head') == 'b84389b6ccf94c259774b82a99bc7296278b65c0', 'W1 explicit human-approved HEAD required')
                require(w['approval_evidence'] == 'docs/work/evidence/WORK-CFG-028/wave-1/authorization.json', 'W1 explicit authorization evidence')
                authorization = load_json(root / w['approval_evidence'])
                require(len(life['review_history']) > 4 and authorization == life['review_history'][4] and authorization['reviewed_head'] == '4aeb4c0ad086ec4fc8911879b13139d84ca57bc9' and authorization['CORE_SIZE_UNBOUNDED_REMEDIATION'] == 'APPROVED' and authorization['authorized_wave'] == 1, 'W1 reviewed HEAD and append-only human authorization')
                expected_evidence = None if w['status'] == 'STARTED' else 'docs/work/evidence/WORK-CFG-028/wave-1/validation.md'
                require(w['completion_evidence'] == expected_evidence, 'no Wave evidence invented')
                if w['status'] == 'IMPLEMENTED': require(life['wave_1']['review'] == 'AWAITING_HUMAN_REVIEW' and existing(root,expected_evidence), 'W1 awaits human review')
            elif n == 2:
                require(w['status'] == 'APPROVED' and w['authorization'] == 'AUTHORIZED' and life['wave_2']['review'] == 'APPROVED' and w.get('reviewed_head') == '0202c7424db04a1d83fb5e35fce055ea81f5b8fa', 'W2 explicit human-approved HEAD required')
                require(w['approval_evidence'] == 'docs/work/evidence/WORK-CFG-028/wave-2/authorization.json', 'W2 explicit authorization evidence')
                authorization = load_json(root / w['approval_evidence'])
                require(len(life['review_history']) > 5 and authorization == life['review_history'][5] and authorization['reviewed_head'] == 'b84389b6ccf94c259774b82a99bc7296278b65c0' and authorization['W1'] == 'APPROVED' and authorization['authorized_wave'] == 2, 'W2 reviewed HEAD and append-only human authorization')
                expected_evidence = None if w['status'] == 'STARTED' else 'docs/work/evidence/WORK-CFG-028/wave-2/validation.md'
                require(w['completion_evidence'] == expected_evidence, 'no Wave evidence invented')
                if w['status'] == 'IMPLEMENTED': require(life['wave_2']['review'] == 'AWAITING_HUMAN_REVIEW' and existing(root,expected_evidence), 'W2 awaits human review')
            elif n == 3:
                require(w['status'] == 'APPROVED' and w['authorization'] == 'AUTHORIZED' and life['wave_3']['review'] == 'APPROVED' and w.get('reviewed_head') == '855628200fba3851493991cec869dee899e82299', 'W3 explicit human-approved HEAD required')
                require(w['approval_evidence'] == 'docs/work/evidence/WORK-CFG-028/wave-3/authorization.json', 'W3 explicit authorization evidence')
                authorization=load_json(root / w['approval_evidence'])
                require(len(life['review_history'])>6 and authorization==life['review_history'][6] and authorization['reviewed_head']=='0202c7424db04a1d83fb5e35fce055ea81f5b8fa' and authorization['W2']=='APPROVED' and authorization['authorized_wave']==3, 'W3 reviewed HEAD and append-only human authorization')
                expected_evidence=None if w['status']=='STARTED' else 'docs/work/evidence/WORK-CFG-028/wave-3/validation.md'
                remediation=life['wave_3'].get('remediation')
                require(remediation is not None, 'W3 F1/F2 remediation review required')
                if remediation is not None:
                    require(remediation['reviewed_head']=='8cb55b86c83644e4727cc532787a518775d7e868' and remediation['blockers']==['W3-F1','W3-F2'], 'W3 focal blockers and reviewed HEAD')
                    review=load_json(root / remediation['review_evidence'])
                    require(review==life['review_history'][7] and review['reviewed_head']=='8cb55b86c83644e4727cc532787a518775d7e868' and review['decision']=='REQUEST_CHANGES' and review['blockers']==['W3-F1','W3-F2'], 'W3 F1/F2 exact reviewed HEAD/blockers')
                    require(remediation['status'] in {'STARTED','IMPLEMENTED'}, 'W3 focal remediation status')
                    if remediation['status']=='STARTED':
                        require(w['status']=='REQUEST_CHANGES' and life['wave_3']['review']=='REQUEST_CHANGES' and remediation['completion_evidence'] is None, 'W3 review remains REQUEST_CHANGES until remediation')
                    else:
                        expected_evidence='docs/work/evidence/WORK-CFG-028/wave-3/review-f1-f2/validation.md'
                        require(w['status']=='APPROVED' and remediation['completion_evidence']==expected_evidence, 'W3 remediation evidence')
                require(w['completion_evidence']==expected_evidence, 'no Wave evidence invented')
                if w['status']=='IMPLEMENTED': require(life['wave_3']['review']=='AWAITING_HUMAN_REVIEW' and existing(root,expected_evidence), 'W3 awaits human review')
            elif n == 4:
                require(w['status']=='APPROVED' and w['authorization']=='AUTHORIZED' and life['wave_4']['review']=='APPROVED' and w.get('reviewed_head')=='21d65d08512f1fb8a945009c2919946a61566eed', 'W4 explicit human-approved HEAD required')
                require(w['approval_evidence']=='docs/work/evidence/WORK-CFG-028/wave-4/authorization.json', 'W4 explicit authorization evidence')
                authorization=load_json(root/w['approval_evidence'])
                require(len(life['review_history'])>8 and authorization==life['review_history'][8] and authorization['reviewed_head']=='855628200fba3851493991cec869dee899e82299' and authorization['W3']=='APPROVED' and authorization['authorized_wave']==4, 'W4 reviewed HEAD and append-only human authorization')
                require(authorization['resolved_findings']==['W3-F1','W3-F2'] and authorization['nonblocking_follow_ups']==['W3-PERF-01','W3-METRICS-01'], 'W3 findings resolution and remaining follow-ups')
                require(life['wave_3']['remediation'].get('findings_resolution')=={'W3-F1':'RESOLVED','W3-F2':'RESOLVED','approved_head':'855628200fba3851493991cec869dee899e82299'}, 'W3 blockers resolved by explicit review')
                expected_evidence=None if w['status']=='STARTED' else 'docs/work/evidence/WORK-CFG-028/wave-4/validation.md'
                remediation=life['wave_4'].get('remediation')
                require(remediation is not None, 'W4-F1 focal review required')
                if remediation is not None:
                    require(remediation['reviewed_head']=='330d63427c0905e8ef140924b643e9fb012c73de' and remediation['blockers']==['W4-F1'], 'W4-F1 exact reviewed HEAD and blocker')
                    review=load_json(root/remediation['review_evidence'])
                    require(len(life['review_history'])>9 and review==life['review_history'][9] and review['reviewed_head']=='330d63427c0905e8ef140924b643e9fb012c73de' and review['decision']=='REQUEST_CHANGES' and review['blockers']==['W4-F1'], 'W4-F1 append-only review')
                    require(remediation['status'] in {'STARTED','IMPLEMENTED'}, 'W4-F1 focal status')
                    if remediation['status']=='STARTED':
                        require(w['status']=='REQUEST_CHANGES' and life['wave_4']['review']=='REQUEST_CHANGES' and remediation['completion_evidence'] is None, 'W4-F1 pending correction cannot claim completion')
                    else:
                        expected_evidence='docs/work/evidence/WORK-CFG-028/wave-4/review-f1/validation.md'
                        require(w['status']=='APPROVED' and remediation['completion_evidence']==expected_evidence, 'W4-F1 focal completion evidence')
                require(w['completion_evidence']==expected_evidence, 'no Wave evidence invented')
                if w['status']=='IMPLEMENTED': require(life['wave_4']['review']=='AWAITING_HUMAN_REVIEW' and existing(root,expected_evidence), 'W4 awaits human review')
            else:
                require(w['status']=='APPROVED' and w['authorization']=='AUTHORIZED' and life['wave_5']['review']=='APPROVED' and life['cp5_status']=='APPROVED / MERGED' and life['closure']['merge']=='4229ec1cfd9c1d9f9e851f3cabe6993b4d4ed9b8' and w['reviewed_head']=='c6b12bf3efe6360b4e33c9a587ad0185b449990d', 'W5 explicit final human approval and merge required')
                require(w['approval_evidence']=='docs/work/evidence/WORK-CFG-028/wave-5/authorization.json','W5 explicit authorization evidence')
                authorization=load_json(root/w['approval_evidence'])
                require(len(life['review_history'])>10 and authorization==life['review_history'][10] and authorization['reviewed_head']=='21d65d08512f1fb8a945009c2919946a61566eed' and authorization['W4']=='APPROVED' and authorization['authorized_wave']==5,'W5 reviewed HEAD and append-only human authorization')
                require(authorization['resolved_findings']==['W4-F1','W4-BINDING-01'] and authorization['nonblocking_follow_ups']==['W3-PERF-01','W3-METRICS-01'],'W4 findings and W3 nonblocking follow-ups')
                require(life['wave_4']['remediation']['findings_resolution']=={'W4-F1':'RESOLVED','W4-BINDING-01':'RESOLVED','approved_head':'21d65d08512f1fb8a945009c2919946a61566eed'},'W4 explicit findings resolution')
                require(life['cp6']=={'status':'NOT_STARTED','authorization':'NOT_AUTHORIZED'},'CP6 remains unauthorized')
                expected_evidence=None if w['status']=='STARTED' else 'docs/work/evidence/WORK-CFG-028/wave-5/validation.md'
                require(w['completion_evidence']==expected_evidence,'no Wave evidence invented')
                if w['status']=='IMPLEMENTED':require(life['wave_5']['review']=='AWAITING_HUMAN_REVIEW' and life['cp5_status']=='AWAITING_FINAL_HUMAN_REVIEW' and existing(root,expected_evidence),'W5 awaits final human review')
            require(w['requires_review_of'] == ('CORE_SIZE_UNBOUNDED_REMEDIATION' if n == 1 else f'WAVE_{n-1}'), 'sequential Wave review dependency')
            require(w['eval'] == f'EVAL-CFG-{33+n:03}', 'Wave eval linkage')
        require(set(work['related_decisions']) == {f'ADR-{n:04}' for n in range(10,15)}, 'ADR routing')
        for p in ['docs/architecture/cp5-dataflow.md','docs/domain/cp5-solver.md','docs/domain/cp5-values.md','docs/engineering/cp5-performance.md','docs/engineering/cp5-challenges.md','docs/architecture/analysis-dataflow-result-v1.md','docs/product/cp5-roadmap.md',LIFECYCLE]:
            require(p in work['must_read'] and existing(root,p), 'required routing ' + p)
        for adr in work['related_decisions']:
            p = root / 'docs/architecture/decisions' / (adr + '.md')
            require(p.is_file() and 'Status: `accepted`' in p.read_text(), 'accepted ADR ' + adr)
        # Exact bytes, including tests and new modules. No absence-as-performance-PASS.
        inventory = load_json(root / PLAN / 'preparation-source-inventory.json')
        require(inventory['baseline'] == BASE, 'source inventory baseline')
        actual = {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()
                  for p in files_under(root) if p.suffix == '.java' or p.name == 'pom.xml'}
        require(actual == load_json(root / 'docs/work/evidence/WORK-CFG-029/source-inventory.json')['files'], 'no unauthorized Java/POM implementation (exact inventory)')
        require((root/'analysis-kernel').is_dir() and (root/'analysis-values').is_dir(), 'W3 modules required')
        require(not (root/'analysis-consumers').exists(), 'no speculative consumer modules')
        require(not any(p.suffix in {'.jar','.class'} for p in files_under(root)), 'no vendored bytecode')
        gates = load_json(root / 'docs/engineering/gate-state.json')
        require(gates['product_gates']['performance'] == {'status':'implemented','hook':'scripts/project/check-performance.sh'}, 'performance requires actual W1–W5 hook')
        catalog = load_json(root / 'docs/evals/catalog.json')['evals']
        require(all(next(e for e in catalog if e['id'] == f'EVAL-CFG-{n:03}')['status'] == 'implemented' for n in range(38,39)), 'W5 eval requires implementation')
        metrics = load_json(root / PLAN / 'metrics.json')['metrics']
        rows(metrics,'name',METRICS,'metric')
        for m in metrics:
            require(m['event'] and m['scope'] and m['unit'] and m['audit'] and 1 <= m['available_by_wave'] <= 5, 'auditable metric ' + m['name'])
        probes = load_json(root / PLAN / 'probes.json')['probes']
        rows(probes,'id',PROBES,'probe')
        for p in probes:
            require(p['waves'] == PROBES.get(p['id']), 'probe Wave routing ' + p['id'])
            require(p['status'] == 'IMPLEMENTED_BY_WAVE_HOOKS' and p['hook'] is None, 'no false probe PASS/hook')
            expected_hooks = {str(n):{'status':'IMPLEMENTED','hook':f'scripts/project/check_w{n}.py'} for n in (1,2,3,4,5) if n in p['waves']}
            require(p.get('wave_hooks',{}) == expected_hooks, 'W1/W2/W3/W4/W5 real probe hooks only')
            require(p['dimension'] and p['oracle'] and p['kills'] and p['metrics'] and set(p['metrics']) <= METRICS, 'probe cost/oracle/metric ' + p['id'])
        challenges = load_json(root / PLAN / 'challenges.json')
        require(challenges['stages'] == ['baseline_green','compilable_mutant','expected_red','byte_exact_restore','second_green'], 'challenge restore protocol')
        rows(challenges['challenges'],'id',CHALLENGES,'challenge')
        for c in challenges['challenges']:
            require(c['wave'] == CHALLENGES.get(c['id']) and c['oracle'], 'challenge Wave/oracle')
            require(c['probe'] is None or (c['probe'] in PROBES and c['wave'] in PROBES[c['probe']]), 'challenge/probe activation')
            if c['wave'] is None:
                require(c.get('future_slice') == 'POST_CP5_INVOKE_EFFECTS', 'future Invoke challenge routing')
            if c['wave'] in (1,2,3,4,5):
                require(c['status'] == 'IMPLEMENTED' and c['hook'] == f"scripts/project/challenge_w{c['wave']}.py" and existing(root,c['hook']) and existing(root,c['target']), 'W1/W2/W3/W4/W5 concrete challenge target/hook')
            else:
                require(c['status'] == 'NOT_AVAILABLE_UNTIL_IMPLEMENTED' and c['hook'] is None and c['target'] is None, 'no fictitious engine mutant')
        plan = load_json(root / PLAN / 'gate-plan.json')
        require(plan['work_item'] == work['id'] and plan['preparation_check'] == 'scripts/harness/validate_cp5.py', 'gate plan linkage')
        require([w['wave'] for w in plan['waves']] == list(range(1,6)), 'gate plan Wave inventory')
        for w in plan['waves']:
            require(w['eval'] == f"EVAL-CFG-{33+w['wave']:03}", 'gate eval routing')
            require(set(w['gates']) == ({'architecture','semantic','performance','integration'} if w['wave']==5 else {'architecture','semantic','performance'}), 'Wave gate inventory')
            require(all(g == ({'status':'IMPLEMENTED','hook':f"scripts/project/check_w{w['wave']}.py"} if w['wave'] in (1,2,3,4,5) else {'status':'NOT_AVAILABLE_UNTIL_IMPLEMENTED','hook':None}) for g in w['gates'].values()), 'no empty hook product PASS')
        arch = load_json(root / PLAN / 'architecture.json')
        require(len(arch['baseline_findings']) == 1 and arch['baseline_findings'][0]['id'] == 'CP5-F01' and arch['baseline_findings'][0]['status'] == 'FIXED_W1', 'baseline direct AIR finding fixed in W1')
        require(arch['air_direct_dependency'] == {'import_prefix':'io.github.gustavo2358.air.','group':'io.github.gustavo2358','artifact':'air-java','scope':'compile'}, 'direct AIR dependency declaration')
        require(arch['modules'] == {
            'cfg-kernel':{'wave':0,'direct':['air-java'],'forbidden':['analysis-kernel','analysis-values']},
            'analysis-kernel':{'wave':1,'direct':['cfg-kernel','air-java'],'forbidden':['analysis-values','cfg-adapters','cfg-launcher']},
            'analysis-values':{'wave':3,'direct':['analysis-kernel','air-java','cfg-kernel'],'forbidden':['cfg-adapters','cfg-launcher','consumers']}, **w5.MODULES}, 'approved module DAG')
        required_deny = {'solver_spi':{'io.github.gustavo2358.air.model.Operations','io.github.gustavo2358.air.model.Values','analysis.values','analysis.extraction','analysis.consumers','cfg.adapters','cfg.launcher','java.io','java.nio.file','java.net','lower','cobolexplorer','org.antlr'},'values':{'analysis.consumers','analysis.extraction','cfg.adapters','cfg.launcher','cfg.application.BuildCfg','lower','cobolexplorer','org.antlr'},'consumers':{'cfg.application.BuildCfg','analysis.solver','analysis.structure.ProgramIndex','cfg.domain.CfgGraph','lower','cobolexplorer','org.antlr'}}
        required_deny['consumers'].update({'org.antlr', 'java.lang.reflect', 'cfg.application.BuildCfg', 'cobolexplorer', 'java.net', 'analysis.application', 'analysis.structure.AnalysisSession', 'cfg.application.CfgBuildCoordinator', 'java.nio.file', 'java.util.ServiceLoader', 'air.model.Publication', 'lower', 'cfg.domain.CfgGraph', 'air.model.Sequence', 'analysis.query.BatchReplayer', 'analysis.structure.ProgramIndex', 'air.model.Unit', 'analysis.values.PossibleValuesAnalysis', 'analysis.values.PossibleValuesProvider', 'java.io', 'analysis.solver'})
        require(set(arch['package_rules']) == set(required_deny), 'architecture role inventory')
        for role,deny in required_deny.items():
            require(set(arch['package_rules'][role]['forbidden']) == deny, 'architecture boundary ' + role)
        require(set(arch['future_inventories']) == {'analysis-kernel','analysis-values'}, 'future architecture inventories')
        for module,inv in arch['future_inventories'].items():
            expected = {'status':'NOT_AVAILABLE_UNTIL_IMPLEMENTED','sources':None,'classfiles':None,'javap_descriptors':None,'jdeps_edges':None,'effective_maven':None}
            if module == 'analysis-kernel': expected = {k:('IMPLEMENTED' if k=='status' else 'docs/evals/cp5/w1-inventory.json') for k in expected}
            if module == 'analysis-values': expected = {k:('IMPLEMENTED' if k=='status' else 'docs/evals/cp5/w3-inventory.json') for k in expected}
            require(inv == expected, 'Wave bytecode inventory')
        require(existing(root,'scripts/project/check_w1.py') and existing(root,'docs/evals/cp5/w1-inventory.json'), 'W1 gate and bytecode inventory exist')
        require(arch.get('solver_inventory') == 'docs/evals/cp5/w2-inventory.json' and existing(root,arch['solver_inventory']), 'W2 solver bytecode inventory exists')
        require(load_json(root / PLAN / 'metrics.json').get('wave_2',{}).get('hook') == 'scripts/project/check_w2.py', 'W2 real metrics hook')
        require(load_json(root / PLAN / 'metrics.json').get('wave_3',{}).get('hook') == 'scripts/project/check_w3.py', 'W3 real metrics hook')
        require(arch.get('planning_inventory')=='docs/evals/cp5/w4-inventory.json' and existing(root,arch['planning_inventory']), 'W4 planning bytecode inventory exists')
        require(load_json(root / PLAN / 'metrics.json').get('wave_4',{}).get('hook')=='scripts/project/check_w4.py', 'W4 real metrics hook')
        import sys
        sys.path.insert(0,str(root/'scripts/project'))
        from check_analysis_architecture import check_direct_air
        require(not check_direct_air(root), 'strict direct AIR dependencies')
        contract = load_json(root / PLAN / 'history/result-contract.json')
        require(contract['schema_version'] == 1 and contract['status'] == 'REVIEW_SNAPSHOT_NOT_CODEC' and contract['schema'] == 'analysis-dataflow-result' and contract['version'] == '1.0.0' and set(contract['execution_statuses']) == phases.EXECUTION_STATUSES and set(contract['value_kinds']) == {'Candidates'} and set(contract['reachability']) == {'REACHABLE','UNREACHABLE_IN_MODEL'}, 'result contract status/schema')
        require(set(contract['required_result_fields']) == RESULT_FIELDS and set(contract['required_observation_fields']) == OBS_FIELDS, 'result contract minimum fields')
        require(set(contract['query_statuses']) == QUERY_STATUSES and
                set(contract['unsupported_point_null_fields']) == UNSUPPORTED_NULL_FIELDS, 'result contract query outcomes')
        require(set(contract['completion']['required_fields']) == phases.COMPLETION_FIELDS and set(contract['completion']['prepared_fields']) == phases.PREPARED_FIELDS and set(contract['completion']['consumer_plan_fields']) == phases.PLAN_FIELDS and set(contract['completion']['delivery_receipt_fields']) == phases.RECEIPT_FIELDS and contract['completion']['observation_batch'] == 'ATOMIC', 'phase completion contract')
        require(contract['completion']['admission_statuses'] == ['COMPLETE','REJECTED'] and contract['completion']['delivery_statuses'] == ['COMPLETE','FAILED'], 'F admission/delivery status contract')
        snapshot = load_json(root / PLAN / 'history/result-review.json')
        require(snapshot['design']['kind'] == 'REVIEW_SNAPSHOT' and snapshot['design']['execution'] == 'NOT_EXECUTED', 'result example is design only')
        requests = snapshot['design']['requestedQueries']
        require(len(requests) == 2, 'mixed batch requires two requested queries')
        if len(requests) == 2:
            before, after = requests
            require(before['point']['position'] == 'BEFORE' and before['point']['outcome'] is None and
                    after == {'point': dict(before['point'], position='AFTER'), 'subject': before['subject']}, 'mixed batch before/after same point and subject')
        errors += validate_result(snapshot['result'], requests)
        require(snapshot['result']['executionStatus'] == 'STABLE', 'mixed batch must remain STABLE')
        for request, expected_status in zip(requests, ('VALUE', 'UNSUPPORTED_POINT')):
            matches = [o for o in snapshot['result']['observations']
                       if o['point'] == request['point'] and o['subject'] == request['subject']]
            require(len(matches) == 1 and matches[0]['queryStatus'] == expected_status, 'mixed batch query outcomes')
        require(snapshot['result']['sourceScope']['open'] is True, 'CP4E source remains open')
        baseline = load_json(root / 'docs/work/evidence/WORK-CFG-028/baseline.json')
        require(len(baseline['authorities']) == 2 and all(x['read'] == 'integral' and re.fullmatch('[0-9a-f]{64}',x['sha256']) for x in baseline['authorities'].values()), 'integral authority hashes')
    except (OSError, KeyError, TypeError, ValueError, StopIteration, IndexError) as exc:
        errors.append('CP5: missing/invalid contract: ' + str(exc))
    return errors


def main() -> int:
    import argparse
    from validate_docs import ROOT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    errors = validate_cp5(args.root.resolve())
    for error in errors:
        print('[cp5-harness] FAIL: ' + error)
    if errors:
        return 1
    print('[cp5-harness] PASS: W1/W2/W3/W4/W5 contracts only; actual product gates execute separately')
    return 0


if __name__ == '__main__':
    import sys
    sys.exit(main())
