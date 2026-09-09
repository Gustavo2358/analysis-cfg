"""Review-only phase contracts. No writer, scheduling, solver or fact extraction."""
from __future__ import annotations
import hashlib
import json
import re

EXECUTION_STATUSES = {'STABLE', 'ADMISSION_LIMIT', 'ANALYSIS_LIMIT', 'UNSUPPORTED', 'INVALID_INPUT'}
COMPLETION_FIELDS = {'admission', 'analysis', 'observation'}
PREPARED_FIELDS = {'schema','version','resultId','publicationId','results','consumerPlan','consumers','preparationStatus','partialPolicy'}
PLAN_FIELDS = {'consumerId','requiredAnalysisKeys','requiredObservationBatchIds'}
RECEIPT_FIELDS = {'schema','version','resultId','resultSha256','destination','status','reason'}


def identity(value):
    """Full structured identity for review comparisons, not a product ID or wire codec."""
    return json.dumps(value, sort_keys=True)


def check_phase(row, statuses, require, consumer=False):
    require(set(row) == ({'id','status','reason'} if consumer else {'status','reason'}), 'phase fields')
    require(row['status'] in statuses, 'phase status')
    needs_reason = row['status'] in {'REJECTED','LIMIT','FAILED'} or (consumer and row['status'] == 'NOT_STARTED')
    require(isinstance(row['reason'],str) and bool(row['reason'].strip()) if needs_reason else row['reason'] is None, 'phase reason')


def validate_completion(result: dict) -> list[str]:
    errors = []
    def require(ok, reason):
        if not ok: errors.append('CP5 phase completion: ' + reason)
    try:
        c = result['completion']
        require(set(c) == COMPLETION_FIELDS, 'run fields exclude consumers and delivery acknowledgement')
        check_phase(c['admission'], {'COMPLETE','REJECTED','LIMIT'}, require)
        check_phase(c['analysis'], {'STABLE','LIMIT','NOT_STARTED'}, require)
        check_phase(c['observation'], {'COMPLETE','LIMIT','FAILED','NOT_STARTED'}, require)
        status = result['executionStatus']
        require(status in EXECUTION_STATUSES, 'execution status')
        admission = {'STABLE':'COMPLETE','ANALYSIS_LIMIT':'COMPLETE',
                     'ADMISSION_LIMIT':'LIMIT','UNSUPPORTED':'REJECTED','INVALID_INPUT':'REJECTED'}[status]
        analysis = {'STABLE':'STABLE','ANALYSIS_LIMIT':'LIMIT','ADMISSION_LIMIT':'NOT_STARTED',
                    'UNSUPPORTED':'NOT_STARTED','INVALID_INPUT':'NOT_STARTED'}[status]
        require(c['admission']['status'] == admission, 'admission LIMIT differs from UNSUPPORTED/INVALID_INPUT')
        require(c['analysis']['status'] == analysis, 'solver status is independent of later failure')
        if status in {'ADMISSION_LIMIT','ANALYSIS_LIMIT'}:
            phase = 'admission' if status == 'ADMISSION_LIMIT' else 'analysis'
            require(c[phase]['reason'] == result['limitReason'], 'limit reason belongs to ' + phase)
        else:
            require(result['limitReason'] is None, 'no run resource limit on stable/unsupported/invalid input')
        if c['admission']['reason'] == 'ADMISSION_BUDGET':
            require(status == 'ADMISSION_LIMIT' and admission == 'LIMIT', 'ADMISSION_BUDGET is never UNSUPPORTED')
        if status != 'STABLE':
            require(c['observation']['status'] == 'NOT_STARTED', 'observation needs stable analysis')
        if c['observation']['status'] != 'COMPLETE':
            require(result['observations'] == [], 'atomic observation batch, no partial values')
    except (KeyError, TypeError, ValueError) as exc:
        errors.append('CP5 phase completion: missing/invalid envelope: ' + str(exc))
    return errors


def validate_prepared(prepared: dict, requested_consumers: list[dict] | None = None,
                      requested_queries_by_batch: dict | None = None) -> list[str]:
    """Check explicit dependencies against supplied results; never execute a consumer."""
    from validate_cp5 import validate_result
    errors = []
    def require(ok, reason):
        if not ok: errors.append('CP5 prepared result: ' + reason)
    try:
        require(set(prepared) == PREPARED_FIELDS, 'payload fields exclude delivery acknowledgement')
        require((prepared['schema'],prepared['version']) == ('prepared-analysis-result','1.0.0'), 'schema/version')
        require(isinstance(prepared['resultId'],str) and bool(prepared['resultId'].strip()), 'prepared result identity')
        pub = prepared['publicationId']
        require(set(pub) == {'domain','localId'} and pub['domain'] == 'publication' and isinstance(pub['localId'],str) and bool(pub['localId']), 'publication identity')
        require(prepared['partialPolicy'] == 'EXPLICIT_PARTIAL_BY_DEPENDENCY', 'explicit partial policy')
        batches, analyses = {}, {}
        for row in prepared['results']:
            require(set(row) == {'observationBatchId','result'}, 'batch/result fields')
            batch, result = row['observationBatchId'], row['result']
            require(isinstance(batch,str) and bool(batch) and batch not in batches, 'unique batch identity')
            requests = requested_queries_by_batch.get(batch) if requested_queries_by_batch is not None else None
            errors += validate_result(result, requests)
            require(result['publicationId'] == pub, 'same publication snapshot')
            key = identity(result['analysisKey'])
            # Multiple batches can observe the same run; they cannot change its status/scope.
            run = {k:result[k] for k in ['analysisKey','publicationId','unitId','entryId','executionStatus','modelScope','sourceScope','limitReason']}
            run.update(admission=result['completion']['admission'],analysis=result['completion']['analysis'])
            require(key not in analyses or analyses[key] == run, 'consistent run across observation batches')
            analyses[key] = run
            batches[batch] = (key, result['completion']['observation']['status'])
        if requested_queries_by_batch is not None:
            require(set(batches) == set(requested_queries_by_batch), 'requested batch coverage')
        plans, consumers = {}, {}
        for plan in prepared['consumerPlan']:
            require(set(plan) == PLAN_FIELDS, 'consumer dependency fields')
            name = plan['consumerId']
            require(isinstance(name,str) and bool(name) and name not in plans, 'unique planned consumer')
            keys = [identity(k) for k in plan['requiredAnalysisKeys']]
            ids = plan['requiredObservationBatchIds']
            require(len(keys) == len(set(keys)) and len(ids) == len(set(ids)), 'unique dependencies')
            require(set(keys) <= set(analyses) and set(ids) <= set(batches), 'resolved dependency identities')
            require(all(batches[b][0] in keys for b in ids if b in batches), 'batch requires its owning AnalysisKey')
            plans[name] = plan
        if requested_consumers is not None:
            expected = {p['consumerId']:p for p in requested_consumers}
            require(len(expected) == len(requested_consumers) and plans == expected, 'independent consumer plan coverage/dependencies')
        for consumer in prepared['consumers']:
            check_phase(consumer, {'COMPLETE','LIMIT','FAILED','NOT_STARTED'}, require, True)
            name = consumer['id']
            require(name in plans and name not in consumers, 'unique requested consumer result')
            consumers[name] = consumer
            if name not in plans: continue
            plan = plans[name]
            ready = all(identity(k) in analyses and analyses[identity(k)]['executionStatus'] == 'STABLE' for k in plan['requiredAnalysisKeys'])
            ready = ready and all(b in batches and batches[b][1] == 'COMPLETE' for b in plan['requiredObservationBatchIds'])
            if not ready:
                require(consumer['status'] == 'NOT_STARTED' and consumer['reason'] == 'DEPENDENCY_UNAVAILABLE', 'only dependent consumers blocked')
        require(set(consumers) == set(plans), 'requested consumer coverage')
        complete = all(r['executionStatus'] == 'STABLE' for r in analyses.values())
        complete = complete and all(status == 'COMPLETE' for _,status in batches.values())
        complete = complete and all(c['status'] == 'COMPLETE' for c in consumers.values())
        require(prepared['preparationStatus'] == ('COMPLETE' if complete else 'INCOMPLETE'), 'incomplete preparation cannot claim full success')
    except (KeyError, TypeError, ValueError) as exc:
        errors.append('CP5 prepared result: missing/invalid envelope: ' + str(exc))
    return errors


def validate_delivery_receipt(receipt: dict, payload_bytes: bytes | None,
                              observed_delivery_status: str | None = None,
                              expected_result_id: str | None = None,
                              expected_destination: str | None = None) -> list[str]:
    """Bind a separate receipt to intended bytes; actual delivery needs independent W5 evidence."""
    errors = []
    def require(ok, reason):
        if not ok: errors.append('CP5 delivery receipt: ' + reason)
    try:
        require(set(receipt) == RECEIPT_FIELDS, 'external receipt fields')
        require((receipt['schema'],receipt['version']) == ('analysis-delivery-receipt','1.0.0'), 'schema/version')
        require(isinstance(receipt['resultId'],str) and bool(receipt['resultId'].strip()), 'prepared result identity')
        if expected_result_id is not None:
            require(receipt['resultId'] == expected_result_id, 'result identity correlation')
        digest = receipt['resultSha256']
        if digest is None:
            require(receipt['status'] in {'FAILED','LIMIT'}, 'COMPLETE requires payload hash')
        else:
            require(isinstance(digest,str) and re.fullmatch('[0-9a-f]{64}',digest) is not None, 'SHA-256')
            require(payload_bytes is not None and digest == hashlib.sha256(payload_bytes).hexdigest(), 'exact intended payload bytes')
        require(isinstance(receipt['destination'],str) and bool(receipt['destination'].strip()), 'destination')
        if expected_destination is not None:
            require(receipt['destination'] == expected_destination, 'destination correlation')
        check_phase({k:receipt[k] for k in ['status','reason']}, {'COMPLETE','FAILED','LIMIT'}, require)
        if observed_delivery_status is not None:
            require(observed_delivery_status in {'COMPLETE','FAILED','LIMIT'} and receipt['status'] == observed_delivery_status, 'independent delivery outcome')
    except (KeyError, TypeError, ValueError) as exc:
        errors.append('CP5 delivery receipt: missing/invalid receipt: ' + str(exc))
    return errors
