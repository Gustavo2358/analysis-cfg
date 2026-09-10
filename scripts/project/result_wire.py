#!/usr/bin/env python3
"""Nominal independent reader for prepared-analysis-result 1.1.0 and external receipts.

Uses the Python standard JSON parser, rejects duplicate members/nonfinite numbers,
validates IDs/status/dependency/support invariants, and retains no input byte buffer.
This is a derived-result contract, not an AIR codec or validator.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

class WireError(ValueError):
    pass

def require(ok, message):
    if not ok:
        raise WireError(message)

def fields(obj, names, label):
    require(isinstance(obj, dict) and set(obj) == set(names.split()), label + ' fields')

def pairs(items):
    out = {}
    for key, value in items:
        require(key not in out, 'duplicate JSON member')
        out[key] = value
    return out

def load(path):
    with Path(path).open(encoding='utf-8') as stream:
        return json.load(stream, object_pairs_hook=pairs, parse_constant=lambda x: (_ for _ in ()).throw(WireError('nonfinite number')))

def identity(v, domain, pub=None, unit=None):
    require(isinstance(v, dict), 'full AIR identity required')
    unit_owned = domain in {'entry', 'operation', 'object', 'label', 'operand'}
    expected = {'domain', 'localId'} | ({'publication'} if domain != 'publication' else set()) | ({'unit'} if unit_owned else set()) | ({'owner'} if domain == 'operand' else set())
    require(set(v) == expected and v['domain'] == domain, 'full ID fields/domain')
    require(isinstance(v['localId'], str) and v['localId'], 'local ID')
    if domain != 'publication':
        require(isinstance(v['publication'], str) and bool(v['publication']), 'publication owner')
        if pub is not None: require(v['publication'] == pub, 'wrong publication owner')
    if unit_owned:
        require(isinstance(v['unit'], str) and bool(v['unit']), 'unit owner')
        if unit is not None: require(v['unit'] == unit, 'wrong unit owner')
    if domain == 'operand':
        require(v['owner'].get('domain') in {'entry', 'operation'}, 'operand owner domain')
        identity(v['owner'], v['owner']['domain'], pub, v['unit'])
    return v['localId']

def token(v):
    return json.dumps(v, sort_keys=True, ensure_ascii=False, separators=(',', ':'))

def distinct(values, label):
    require(isinstance(values, list), label + ' array')
    keys = [token(v) for v in values]
    require(len(keys) == len(set(keys)), label + ' duplicates')
    return set(keys)

def refs(values, domain, pub):
    distinct(values, domain + ' refs')
    for v in values:
        d = v.get('domain') if domain == 'producer' else domain
        if domain == 'producer': require(d in {'operation', 'operand'}, 'producer identity domain')
        identity(v, d, pub)

def key(k, pub):
    fields(k, 'implementation implementationVersion profile direction precisionPolicy options entryId', 'analysisKey')
    for n in ('implementation','implementationVersion','profile','precisionPolicy'):
        require(isinstance(k[n],str) and k[n], 'analysis identity')
    require(k['direction'] in {'FORWARD','BACKWARD'}, 'direction')
    require(isinstance(k['options'],dict) and all(isinstance(a,str) and isinstance(b,str) for a,b in k['options'].items()), 'semantic options')
    if k['implementation']=='PossibleValues': require(k['options']=={}, 'CP5 options are semantic and empty')
    identity(k['entryId'],'entry',pub)

def point(p, entry, status):
    fields(p,'position operationId entryId outcome','point')
    require(p['entryId']==entry, 'query Entry binding')
    require(p['position'] in {'ENTRY','BEFORE','AFTER','OUTCOME'}, 'point position')
    if p['position']=='ENTRY': require(p['operationId'] is None and p['outcome'] is None,'Entry point shape')
    else: identity(p['operationId'],'operation',entry['publication'],entry['unit'] if status=='VALUE' else None)
    if p['position']=='BEFORE': require(p['outcome'] is None,'BEFORE outcome')
    if p['outcome'] is not None:
        o=p['outcome'];require(isinstance(o,dict) and o.get('kind') in {'normal','exception','other-exception','halt','diverge'},'outcome')
        fields(o,'kind tag' if o['kind']=='exception' else 'kind','outcome')
        if o['kind']=='exception':require(isinstance(o['tag'],str) and o['tag'],'exception tag')

def metrics(v):
    require(isinstance(v,dict),'statistics object')
    for value in v.values():
        if isinstance(value,dict):metrics(value)
        elif value is not None:require(type(value) is int and value>=0,'metric must be nonnegative integer or unavailable/null')

def observation(o,run):
    fields(o,'point subject queryStatus queryReason reachability value sourceUnknownRemainder effectiveUnknownRemainder precision candidateSupports premiseRefs evidenceRefs provenanceRefs','observation')
    status=o['queryStatus'];require(status in {'VALUE','UNSUPPORTED_POINT'},'query status')
    entry=run['entryId'];pub=entry['publication'];point(o['point'],entry,status)
    subject=o['subject'];fields(subject,'place objectId storageId locationKind','subject')
    identity(subject['objectId'],'object',pub,entry['unit'] if status=='VALUE' else None)
    if subject['storageId'] is not None:identity(subject['storageId'],'storage',pub)
    require(subject['place']=='ObjectPlace' and subject['locationKind']=='WHOLE_CELL','subject profile')
    refs(o['premiseRefs'],'premise',pub);refs(o['evidenceRefs'],'producer',pub);refs(o['provenanceRefs'],'origin',pub)
    if status=='UNSUPPORTED_POINT':
        require(o['queryReason'] in {'CONTEXT_NOT_SELECTED','UNKNOWN_OPERATION','FOREIGN_UNIT','AFTER_TERMINATOR','OUTCOME_UNAVAILABLE','UNSUPPORTED_SUBJECT'},'query reason')
        require(all(o[n] is None for n in ('reachability','value','precision','sourceUnknownRemainder','effectiveUnknownRemainder')),'refused query has invented semantic fields')
        require(all(o[n]==[] for n in ('candidateSupports','premiseRefs','evidenceRefs','provenanceRefs')),'refused query support')
        return
    require(o['queryReason'] is None and subject['storageId'] is not None,'VALUE shape')
    require(o['reachability'] in {'REACHABLE','UNREACHABLE_IN_MODEL'},'reachability')
    source=o['sourceUnknownRemainder'];require(type(source) is bool,'source remainder')
    scope=run['sourceScope']
    if scope['publicationInventory']!='COMPLETE' or scope['unitInventory']!='COMPLETE' or scope['entryUncertaintyRefs']:
        require(source,'source partial promoted to exact')
    model=False;vals=[]
    if o['reachability']=='UNREACHABLE_IN_MODEL':require(o['value'] is None,'unreachable is not empty Candidates')
    else:
        v=o['value'];fields(v,'domain kind enumerated modelValueRemainder','value')
        require(v['domain']=='known(text)' and v['kind']=='Candidates','value domain')
        vals=v['enumerated'];distinct(vals,'candidates');require(all(isinstance(x,str) for x in vals),'text candidates')
        model=v['modelValueRemainder'];require(type(model) is bool and (bool(vals) or model),'reached empty closed value')
    require(type(o['effectiveUnknownRemainder']) is bool and o['effectiveUnknownRemainder']==(model or source),'effective remainder inconsistent')
    precision=o['precision'];fields(precision,'model source pathWitness','precision')
    require(precision['pathWitness']=='NOT_PROVIDED','invented path witness')
    require(precision['source']==('OPEN' if source else 'CLOSED'),'source precision')
    expected='UNREACHABLE_IN_MODEL' if o['reachability']=='UNREACHABLE_IN_MODEL' else 'OPEN_IN_ADMITTED_MODEL' if model else 'CLOSED_IN_ADMITTED_MODEL'
    require(precision['model']==expected,'model precision')
    supports=o['candidateSupports'];require(isinstance(supports,list),'candidate support array')
    require(len(supports)==len(vals) and {s.get('candidate') for s in supports}==set(vals),'candidate/support association malformed')
    evidence=set();origins=set();premises=set()
    for s in supports:
        fields(s,'candidate producers','candidate support');require(bool(s['producers']),'candidate without producer');distinct(s['producers'],'producers')
        for p in s['producers']:
            fields(p,'evidence origin premiseRefs','producer');refs([p['evidence']],'producer',pub);identity(p['origin'],'origin',pub);refs(p['premiseRefs'],'premise',pub)
            evidence.add(token(p['evidence']));origins.add(token(p['origin']));premises.update(token(x) for x in p['premiseRefs'])
    require(evidence==set(map(token,o['evidenceRefs'])) and origins==set(map(token,o['provenanceRefs'])),'aggregate producer evidence mismatch')
    require(premises<=set(map(token,o['premiseRefs'])),'producer premise omitted')

def run_result(r,pub,batch):
    fields(r,'schema version analysisKey publicationId unitId entryId executionStatus modelScope sourceScope observations statistics completion analysisReason','analysis result')
    require((r['schema'],r['version'])==('analysis-dataflow-result','1.1.0'),'analysis schema/version')
    require(identity(r['publicationId'],'publication')==pub,'result publication')
    unit=identity(r['unitId'],'unit',pub);identity(r['entryId'],'entry',pub,unit);key(r['analysisKey'],pub)
    require(r['analysisKey']['entryId']==r['entryId'],'analysis key Entry')
    require(r['modelScope']=='KNOWN_GRAPH_ENTRY','model scope')
    s=r['sourceScope'];fields(s,'scope publicationInventory unitInventory entryUncertaintyRefs remainderPolicy','source scope')
    require(s['scope']==r['unitId'] and s['remainderPolicy']=='PER_OBSERVATION_W3','source scope binding')
    require(s['publicationInventory'] in {'COMPLETE','PARTIAL','UNAVAILABLE'} and s['unitInventory'] in {'COMPLETE','PARTIAL','UNAVAILABLE'},'inventory status')
    refs(s['entryUncertaintyRefs'],'uncertainty',pub)
    status=r['executionStatus'];require(status in {'STABLE','UNSUPPORTED','INVALID_INPUT'},'execution status')
    c=r['completion'];fields(c,'admission analysis observation','completion')
    for phase in c.values():fields(phase,'status reason','phase')
    stable=status=='STABLE';expected_admission={'status':'COMPLETE','reason':None} if stable else {'status':'REJECTED','reason':'INVALID_STRUCTURE' if status=='INVALID_INPUT' else 'UNSUPPORTED_PROFILE'}
    require(c['admission']==expected_admission and c['analysis']=={'status':'STABLE' if stable else 'NOT_STARTED','reason':None},'phase completion inconsistent')
    require(r['analysisReason'] is None if stable else isinstance(r['analysisReason'],str) and bool(r['analysisReason']),'analysis reason')
    phase=c['observation'];require(phase['status'] in {'COMPLETE','FAILED','NOT_STARTED'},'observation completion')
    require(phase['reason']==('OBSERVATION_ERROR' if phase['status']=='FAILED' else 'DEPENDENCY_UNAVAILABLE' if batch and phase['status']=='NOT_STARTED' else None),'observation reason')
    if not batch or not stable:require(phase['status']=='NOT_STARTED','observation started without run/batch')
    if phase['status']!='COMPLETE':require(r['observations']==[],'incomplete batch is atomic')
    distinct([{'point':o['point'],'subject':o['subject']['objectId']} for o in r['observations']],'observations')
    for o in r['observations']:observation(o,r)
    metrics(r['statistics'])
    return token(r['analysisKey'])

def validate(report):
    fields(report,'schema version resultId publicationId planningEpoch analyses results consumerPlan consumers preparationStatus partialPolicy statistics','prepared result (delivery forbidden)')
    require((report['schema'],report['version'])==('prepared-analysis-result','1.1.0'),'prepared schema/version')
    require(isinstance(report['resultId'],str) and bool(report['resultId'].strip()),'stable resultId')
    require(type(report['planningEpoch']) is int and report['planningEpoch']>0,'planning epoch')
    pub=identity(report['publicationId'],'publication');analyses={};batches={};plans={};all_complete=True
    for a in report['analyses']:
        k=run_result(a,pub,False);require(k not in analyses,'duplicate analysis');analyses[k]=a
        all_complete &= a['executionStatus']=='STABLE'
    for b in report['results']:
        fields(b,'observationBatchId projection result','batch');bid=b['observationBatchId'];require(isinstance(bid,str) and bool(bid) and bid not in batches,'batch identity')
        require(b['projection']=='ValueFact@1','projection');k=run_result(b['result'],pub,True);require(k in analyses,'batch without analysis')
        a=analyses[k];r=b['result']
        for f in ('executionStatus','sourceScope','analysisReason'):require(a[f]==r[f],'batch/run disagreement')
        require(a['statistics']['analysis']==r['statistics']['analysis'],'batch/run metrics disagreement')
        batches[bid]=b;all_complete &= r['completion']['observation']['status']=='COMPLETE'
    for p in report['consumerPlan']:
        fields(p,'consumerId requiredAnalysisKeys requiredObservationBatchIds','consumer dependencies');cid=p['consumerId'];require(isinstance(cid,str) and cid and cid not in plans,'consumer identity')
        distinct(p['requiredAnalysisKeys'],'consumer keys');distinct(p['requiredObservationBatchIds'],'consumer batches');ks=set()
        for k in p['requiredAnalysisKeys']:key(k,pub);require(token(k) in analyses,'unknown analysis dependency');ks.add(token(k))
        for b in p['requiredObservationBatchIds']:require(b in batches and token(batches[b]['result']['analysisKey']) in ks,'consumer dependency malformed')
        plans[cid]=p
    seen=set()
    for c in report['consumers']:
        fields(c,'consumerId status reason facts','consumer');cid=c['consumerId'];require(cid in plans and cid not in seen,'consumer coverage');seen.add(cid);p=plans[cid]
        available=all(analyses[token(k)]['executionStatus']=='STABLE' for k in p['requiredAnalysisKeys']) and all(batches[b]['result']['completion']['observation']['status']=='COMPLETE' for b in p['requiredObservationBatchIds'])
        status=c['status'];require(status in {'COMPLETE','FAILED','NOT_STARTED'},'consumer status')
        if not available:require(status=='NOT_STARTED' and c['reason']=='DEPENDENCY_UNAVAILABLE','dependency failure not isolated')
        elif status=='COMPLETE':require(c['reason'] is None,'complete consumer reason')
        elif status=='FAILED':require(c['reason']=='CONSUMER_ERROR','consumer failure reason')
        else:require(c['reason']=='NOT_EXECUTED','consumer not executed reason')
        if status!='COMPLETE':require(c['facts']==[],'failed consumer facts not atomic')
        distinct(c['facts'],'facts')
        for f in c['facts']:
            fields(f,'kind sequenceId observationBatchId query','ObservedValueFact');require(f['kind']=='ObservedValueFact','generic fact kind')
            identity(f['sequenceId'],'label',pub);bid=f['observationBatchId'];require(bid in p['requiredObservationBatchIds'],'fact batch dependency');q=f['query'];fields(q,'point objectId','fact query')
            require(any(o['point']==q['point'] and o['subject']['objectId']==q['objectId'] for o in batches[bid]['result']['observations']),'fact observation reference')
        all_complete &= status=='COMPLETE'
    require(seen==set(plans),'consumer outcome coverage')
    require(report['partialPolicy']=='EXPLICIT_PARTIAL_BY_DEPENDENCY','partial policy')
    require(report['preparationStatus']==('COMPLETE' if all_complete else 'INCOMPLETE'),'preparation completion')
    metrics(report['statistics']);return report

def read_result(path):
    try:return validate(load(path))
    except (KeyError,TypeError,AttributeError,UnicodeError,json.JSONDecodeError) as e:raise WireError('malformed result: '+str(e)) from e

def verify_receipt(receipt,report,path):
    fields(receipt,'schema version resultId resultSha256 destination status reason','external receipt')
    require((receipt['schema'],receipt['version'])==('analysis-delivery-receipt','1.0.0'),'receipt schema/version')
    require(receipt['resultId']==report['resultId'],'wrong receipt resultId')
    require(receipt['destination']==str(Path(path).absolute()),'wrong receipt destination')
    require(receipt['status'] in {'COMPLETE','FAILED'},'receipt status')
    if receipt['status']=='COMPLETE':
        require(receipt['reason'] is None,'complete receipt reason')
        with Path(path).open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
        require(receipt['resultSha256']==digest,'wrong receipt result hash')
    else:
        require(receipt['reason'] in {'ENCODING_FAILED','WRITE_FAILED','FINALIZATION_FAILED'},'failed receipt reason')
        h=receipt['resultSha256']
        require(h is None or isinstance(h,str) and len(h)==64 and all(c in '0123456789abcdef' for c in h),'failed receipt hash format')
        if receipt['reason'] in {'ENCODING_FAILED','WRITE_FAILED'}:require(h is None,'prefix is not result hash')
    return receipt

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('result',type=Path);parser.add_argument('--receipt',type=Path);a=parser.parse_args()
    try:
        report=read_result(a.result)
        if a.receipt:verify_receipt(load(a.receipt),report,a.result)
        print(json.dumps({'schema':'analysis-result-validation','status':'PASS','resultId':report['resultId'],'preparationStatus':report['preparationStatus']},sort_keys=True))
    except (WireError,OSError) as e:parser.exit(1,'RESULT_INVALID: '+str(e)+'\n')
if __name__=='__main__':main()
