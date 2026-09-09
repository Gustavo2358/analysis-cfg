#!/usr/bin/env python3
"""CP5 preparation contract checks. Does not execute or certify a dataflow engine."""
from __future__ import annotations
import hashlib
import json
import re
from pathlib import Path
from validate_docs import existing, files_under, load_json

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
    'missing-key-as-bottom': 3, 'unbounded-values': 3, 'silent-truncation': 3,
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
METRICS = set('''nodesIndexed edgesIndexed operationsIndexed referencesResolved objectsIndexed
locationsIndexed structuralVisits firstPublications nodesPopped nodesTransferred
operationsTransferred initializationAttempts edgeContributionJoins edgeTransferInvocations
accumulatorStatesChanged accumulatorStatesUnchanged publishedStatesChanged publishedStatesUnchanged
predecessorContributionReads boundaryJoins worklistAttempts worklistPushes duplicatePushesSuppressed
maxWorklistSize joinEntriesVisited stateCompareEntries stateAllocations stateBytesAllocated
stateBytesRetained stateRootsRetained maxSparseBindings setElementsRetained valuesInterned poolHits
bytesHashed saturations candidateSites siteMatches consumerInvocations factsEmitted queryRequests
uniqueQueries sequencesReplayed operationsReplayed analysisRuns analysisCacheHits'''.split())
RESULT_FIELDS = set('''schema version analysisKey publicationId unitId entryId executionStatus
modelScope sourceScope limitReason observations statistics'''.split())
OBS_FIELDS = set('''point subject queryStatus queryReason reachability value sourceUnknownRemainder effectiveUnknownRemainder
precision premiseRefs evidenceRefs provenanceRefs'''.split())
QUERY_STATUSES = {'VALUE', 'UNSUPPORTED_POINT'}
UNSUPPORTED_NULL_FIELDS = {'reachability', 'value', 'sourceUnknownRemainder',
                           'effectiveUnknownRemainder', 'precision'}


def validate_result(result: dict, requested_queries: list[dict] | None = None) -> list[str]:
    """Review shape/consistency and optional external batch coverage; no AIR execution."""
    errors = []
    def require(ok, reason):
        if not ok: errors.append('CP5 result: ' + reason)
    try:
        require(set(result) == RESULT_FIELDS, 'required result fields')
        require((result['schema'], result['version']) == ('analysis-dataflow-result', '1.0.0'), 'schema/version')
        status = result['executionStatus']
        require(status in {'STABLE', 'ANALYSIS_LIMIT', 'UNSUPPORTED', 'INVALID_INPUT'}, 'execution status')
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
        k = key['options']['maxCandidates']
        require(type(k) is int and k > 0, 'positive configurable cardinality')
        require('resourceBudgets' in key['options'], 'explicit resource budgets')
        source = result['sourceScope']
        require(set(source) == {'open', 'inventory', 'dimension', 'scope'}, 'source scope fields')
        require(type(source['open']) is bool and source['inventory'] in {'COMPLETE','PARTIAL','UNAVAILABLE'}, 'source scope status')
        require(source['dimension'] == 'CONTROL' and source['scope'] == result['unitId'], 'source scope owner')
        require(source['inventory'] == 'COMPLETE' or source['open'], 'PARTIAL/UNAVAILABLE cannot close source')
        require(isinstance(result['observations'], list), 'observations array')
        if status != 'STABLE': require(result['observations'] == [], 'non-stable cannot emit provisional facts')
        if status == 'STABLE' and requested_queries is not None:
            # The caller supplies the plan independently of the response. Repeated
            # requests share one outcome for the same complete point + subject.
            require(isinstance(requested_queries, list) and
                    all(set(q) == {'point', 'subject'} for q in requested_queries), 'requested query fields')
            def query_key(query):
                return json.dumps({k: query[k] for k in ('point', 'subject')}, sort_keys=True)
            expected = {query_key(q) for q in requested_queries}
            actual = [query_key(o) for o in result['observations']]
            require(len(actual) == len(expected) and set(actual) == expected, 'requested query coverage')
        require((status == 'ANALYSIS_LIMIT') == (result['limitReason'] is not None), 'budget limit distinct from stable/saturation')
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
                require(isinstance(value, dict) and set(value) == {'domain','kind','enumerated','modelValueRemainder','saturationReason'}, 'value fields')
                require(value['domain'] == 'known(text)', 'value domain')
                vals = value['enumerated']
                require(isinstance(vals, list) and all(isinstance(x,str) for x in vals) and len(vals) == len(set(vals)), 'distinct text candidates')
                require(type(value['modelValueRemainder']) is bool, 'model remainder type')
                require(value['kind'] in {'Candidates','Saturated'}, 'value kind')
                if value['kind'] == 'Saturated':
                    require(value['modelValueRemainder'] and value['saturationReason'] == 'CARDINALITY_LIMIT', 'saturation must expose limit and remainder')
                else:
                    require(len(vals) <= k and value['saturationReason'] is None, 'bounded candidates, no silent truncation claim')
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
    errors = []
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
        require(life['current_checkpoint'] == work['checkpoint'] == 'CP5_HARNESS_PREPARATION', 'preparation checkpoint only')
        require(life['authorized_wave'] is None, 'no Wave authorized')
        require(life['harness_preparation'] == {'status':'implemented','review':'AWAITING_HUMAN_REVIEW'}, 'preparation review state')
        require(work['authorization'] == 'implementation' and work['status'] == 'active', 'harness authorization')
        policy = life['policy']
        require(policy == {'same_branch_same_pr':True,'draft_until_explicit_human_change':True,'human_review_between_waves':True,'auto_start_next_wave':False,'commits_per_wave':'unrestricted_focused','no_intermediate_merge_required':True}, 'same branch/PR and review policy')
        approval = life['last_human_approval']
        require(set(approval['approved']) == {'H1','H2','H3','H4_PROPERTIES','H5','H6','H7','R1','R2'} and approval['does_not_authorize_waves'] is True, 'approved architecture is not Wave authorization')
        require(set(approval['implementation_candidates_not_frozen']) == {'Patricia/radix','FIFO','k=8'}, 'implementation-neutral H4')
        require(approval['source'] and approval['date'] and approval['decision'] == 'APPROVED_DISCOVERY_AND_HARNESS_PREPARATION_ONLY', 'human evidence')
        pr = life['pr']
        require(pr['draft'] is True and pr['auto_merge'] is False, 'PR draft without auto-merge')
        if pr['number'] is None:
            require(pr['url'] is None and pr['state'] == 'NOT_CREATED', 'pending PR metadata')
        else:
            require(type(pr['number']) is int and pr['number'] > 0 and pr['state'] == 'OPEN' and pr['url'] == f"https://github.com/Gustavo2358/analysis-cfg/pull/{pr['number']}", 'PR linkage')
        require([w['wave'] for w in life['waves']] == list(range(1,6)), 'five Waves in order')
        for w in life['waves']:
            n = w['wave']
            require(w['status'] == 'NOT_STARTED' and w['authorization'] == 'NOT_AUTHORIZED', 'Waves 1-5 must be NOT_STARTED / NOT_AUTHORIZED')
            require(w['approval_evidence'] is None and w['completion_evidence'] is None, 'no Wave evidence invented')
            require(w['requires_review_of'] == ('HARNESS_PREPARATION' if n == 1 else f'WAVE_{n-1}'), 'sequential Wave review dependency')
            require(w['eval'] == f'EVAL-CFG-{33+n:03}', 'Wave eval linkage')
        require(set(work['related_decisions']) == {f'ADR-{n:04}' for n in range(10,14)}, 'ADR routing')
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
        require(actual == inventory['files'], 'no unauthorized Java/POM implementation (exact inventory)')
        require(not any((root/m).exists() for m in ['analysis-kernel','analysis-values']), 'no speculative modules')
        require(not any(p.suffix in {'.jar','.class'} for p in files_under(root)), 'no vendored bytecode')
        gates = load_json(root / 'docs/engineering/gate-state.json')
        require(gates['product_gates']['performance'] == {'status':'unavailable','hook':None}, 'performance remains UNAVAILABLE')
        catalog = load_json(root / 'docs/evals/catalog.json')['evals']
        require(all(next(e for e in catalog if e['id'] == f'EVAL-CFG-{n:03}')['status'] == 'planned' for n in range(34,39)), 'no engine eval implemented')
        metrics = load_json(root / PLAN / 'metrics.json')['metrics']
        rows(metrics,'name',METRICS,'metric')
        for m in metrics:
            require(m['event'] and m['scope'] and m['unit'] and m['audit'] and 1 <= m['available_by_wave'] <= 5, 'auditable metric ' + m['name'])
        probes = load_json(root / PLAN / 'probes.json')['probes']
        rows(probes,'id',PROBES,'probe')
        for p in probes:
            require(p['waves'] == PROBES.get(p['id']), 'probe Wave routing ' + p['id'])
            require(p['status'] == 'NOT_AVAILABLE_UNTIL_IMPLEMENTED' and p['hook'] is None, 'no false probe PASS/hook')
            require(p['dimension'] and p['oracle'] and p['kills'] and p['metrics'] and set(p['metrics']) <= METRICS, 'probe cost/oracle/metric ' + p['id'])
        challenges = load_json(root / PLAN / 'challenges.json')
        require(challenges['stages'] == ['baseline_green','compilable_mutant','expected_red','byte_exact_restore','second_green'], 'challenge restore protocol')
        rows(challenges['challenges'],'id',CHALLENGES,'challenge')
        for c in challenges['challenges']:
            require(c['wave'] == CHALLENGES.get(c['id']) and c['oracle'], 'challenge Wave/oracle')
            require(c['probe'] is None or (c['probe'] in PROBES and c['wave'] in PROBES[c['probe']]), 'challenge/probe activation')
            require(c['status'] == 'NOT_AVAILABLE_UNTIL_IMPLEMENTED' and c['hook'] is None and c['target'] is None, 'no fictitious engine mutant')
        plan = load_json(root / PLAN / 'gate-plan.json')
        require(plan['work_item'] == work['id'] and plan['preparation_check'] == 'scripts/harness/validate_cp5.py', 'gate plan linkage')
        require([w['wave'] for w in plan['waves']] == list(range(1,6)), 'gate plan Wave inventory')
        for w in plan['waves']:
            require(w['eval'] == f"EVAL-CFG-{33+w['wave']:03}", 'gate eval routing')
            require(set(w['gates']) == ({'architecture','semantic','performance','integration'} if w['wave']==5 else {'architecture','semantic','performance'}), 'Wave gate inventory')
            require(all(g == {'status':'NOT_AVAILABLE_UNTIL_IMPLEMENTED','hook':None} for g in w['gates'].values()), 'no empty hook product PASS')
        arch = load_json(root / PLAN / 'architecture.json')
        require(len(arch['baseline_findings']) == 1 and arch['baseline_findings'][0]['id'] == 'CP5-F01' and arch['baseline_findings'][0]['status'] == 'OPEN_FOR_HUMAN_REVIEW', 'baseline direct AIR finding stays explicit')
        require(arch['air_direct_dependency'] == {'import_prefix':'io.github.gustavo2358.air.','group':'io.github.gustavo2358','artifact':'air-java','scope':'compile'}, 'direct AIR dependency declaration')
        require(arch['modules'] == {
            'cfg-kernel':{'wave':0,'direct':['air-java'],'forbidden':['analysis-kernel','analysis-values']},
            'analysis-kernel':{'wave':1,'direct':['cfg-kernel','air-java'],'forbidden':['analysis-values','cfg-adapters','cfg-launcher']},
            'analysis-values':{'wave':3,'direct':['analysis-kernel','air-java'],'forbidden':['cfg-adapters','cfg-launcher','consumers']}}, 'approved module DAG')
        required_deny = {'solver_spi':{'io.github.gustavo2358.air.model.Operations','io.github.gustavo2358.air.model.Values','analysis.values','analysis.extraction','analysis.consumers','cfg.adapters','cfg.launcher','java.io','java.nio.file','java.net','lower','cobolexplorer','org.antlr'},'values':{'analysis.consumers','analysis.extraction','cfg.adapters','cfg.launcher','cfg.application.BuildCfg','lower','cobolexplorer','org.antlr'},'consumers':{'cfg.application.BuildCfg','analysis.solver','analysis.structure.ProgramIndex','cfg.domain.CfgGraph','lower','cobolexplorer','org.antlr'}}
        require(set(arch['package_rules']) == set(required_deny), 'architecture role inventory')
        for role,deny in required_deny.items():
            require(set(arch['package_rules'][role]['forbidden']) == deny, 'architecture boundary ' + role)
        require(set(arch['future_inventories']) == {'analysis-kernel','analysis-values'}, 'future architecture inventories')
        for inv in arch['future_inventories'].values():
            require(inv == {'status':'NOT_AVAILABLE_UNTIL_IMPLEMENTED','sources':None,'classfiles':None,'javap_descriptors':None,'jdeps_edges':None,'effective_maven':None}, 'unimplemented bytecode inventory')
        contract = load_json(root / PLAN / 'result-contract.json')
        require(contract['schema_version'] == 1 and contract['status'] == 'REVIEW_SNAPSHOT_NOT_CODEC' and contract['schema'] == 'analysis-dataflow-result' and contract['version'] == '1.0.0' and set(contract['execution_statuses']) == {'STABLE','ANALYSIS_LIMIT','UNSUPPORTED','INVALID_INPUT'} and set(contract['value_kinds']) == {'Candidates','Saturated'} and set(contract['reachability']) == {'REACHABLE','UNREACHABLE_IN_MODEL'}, 'result contract status/schema')
        require(set(contract['required_result_fields']) == RESULT_FIELDS and set(contract['required_observation_fields']) == OBS_FIELDS, 'result contract minimum fields')
        require(set(contract['query_statuses']) == QUERY_STATUSES and
                set(contract['unsupported_point_null_fields']) == UNSUPPORTED_NULL_FIELDS, 'result contract query outcomes')
        snapshot = load_json(root / PLAN / 'result-review.json')
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
    except (OSError, KeyError, TypeError, ValueError, StopIteration) as exc:
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
    print('[cp5-harness] PASS: preparation contracts only; engine gates NOT_AVAILABLE_UNTIL_IMPLEMENTED')
    return 0


if __name__ == '__main__':
    import sys
    sys.exit(main())
