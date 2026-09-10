#!/usr/bin/env python3
"""S10: actual fresh producer processes, independent AIR/result oracles and external receipts."""
from __future__ import annotations
import argparse, copy, json, os, subprocess, time
from pathlib import Path
from prepare_w5_producers import ROOT, digest, snapshot
from result_wire import read_result, load, verify_receipt, require, token

MAIN='io.github.gustavo2358.analysis.launcher.AnalysisDataflow'
MODULES=['cfg-kernel','analysis-kernel','analysis-values','analysis-dataflow','analysis-adapters','analysis-launcher']
def real_process(argv,cwd):
    with subprocess.Popen(argv,cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.PIPE) as process:
        stdout,stderr=process.communicate()
        return process.pid,subprocess.CompletedProcess(argv,process.returncode,stdout,stderr)
def classpath(root):
    return [str(root/m/'target'/(m+'-0.1.0-SNAPSHOT.jar')) for m in MODULES]+(root/'analysis-launcher/target/runtime-classpath.txt').read_text().strip().split(os.pathsep)
def execute_stage(name,argv,cwd,input_path,output_path,directory,expected=0):
    require(not output_path.exists(),'stage output must be fresh')
    before=digest(input_path);start=time.monotonic_ns()
    pid,proc=real_process(argv,cwd)
    elapsed=time.monotonic_ns()-start
    stdout=directory/(name+'.stdout');stderr=directory/(name+'.stderr');stdout.write_bytes(proc.stdout);stderr.write_bytes(proc.stderr)
    receipt=dict(stage=name,executed=True,processId=pid,argv=argv,cwd=str(cwd),input=str(input_path),inputSha256=before,output=str(output_path),exitCode=proc.returncode,expectedExit=expected,elapsedNanos=elapsed,stdoutSha256=digest(stdout),stderrSha256=digest(stderr))
    (directory/(name+'-process.json')).write_text(json.dumps(receipt,indent=2)+'\n')
    require(proc.returncode==expected,'stage failed: '+name+' see '+str(stderr))
    require(output_path.is_file(),'stage output missing: '+name);load(output_path)
    require(digest(input_path)==before,'stage altered its input')
    receipt['outputSha256']=digest(output_path)
    (directory/(name+'-process.json')).write_text(json.dumps(receipt,indent=2)+'\n')
    return receipt
def verify_stage(receipt,argv,input_path,output_path):
    require(receipt.get('executed') is True and receipt.get('argv')==argv,'golden substituted for real stage')
    require(type(receipt.get('processId')) is int and receipt['processId']>0,'real process identity absent')
    require(receipt['exitCode']==receipt['expectedExit'],'stage exit')
    require(receipt['inputSha256']==digest(input_path) and receipt['outputSha256']==digest(output_path),'stage output changed after process')

def id_token(value):
    if value is None:return ''
    names={'publication':'PublicationId','unit':'UnitId','entry':'EntryId','label':'LabelId','operation':'OperationId','object':'ObjectId','storage':'StorageId','origin':'OriginId','premise':'PremiseId','operand':'OperandId','uncertainty':'UncertaintyId'}
    parts=[names[value['domain']],value.get('publication',value['localId'])]
    if 'unit' in value:parts.append(value['unit'])
    if 'owner' in value:parts.append(id_token(value['owner']))
    parts.append(value['localId']);return ''.join(str(len(s.encode('utf-16-le'))//2)+':'+s for s in parts)
def ids(values):return sorted(map(id_token,values))
def semantic_projection(report,air_bytes):
    rows=[]
    for b in report['results']:
        for o in b['result']['observations']:
            p=o['point'];v=o['value'];rows.append(dict(batch=b['observationBatchId'],entry=id_token(p['entryId']),position=p['position'],operation=id_token(p['operationId']),object=id_token(o['subject']['objectId']),queryStatus=o['queryStatus'],reason=o['queryReason'],reachability=o['reachability'],cell=id_token(o['subject']['storageId']),candidates=None if v is None else sorted(v['enumerated']),model=None if v is None else v['modelValueRemainder'],source=o['sourceUnknownRemainder'],effective=o['effectiveUnknownRemainder'],premises=ids(o['premiseRefs']),evidence=ids(o['evidenceRefs']),origins=ids(o['provenanceRefs']),supports=[[s['candidate'],id_token(p['evidence']),id_token(p['origin']),ids(p['premiseRefs'])] for s in o['candidateSupports'] for p in s['producers']]))
    analyses=[]
    for a in report['analyses']:
        k=a['analysisKey'];analyses.append([id_token(k['entryId']),k['implementation'],k['implementationVersion'],k['profile'],k['direction'],k['precisionPolicy'],k['options'],a['executionStatus'],a['analysisReason'] or '',a['statistics']['analysis']])
    batches=[[b['observationBatchId'],b['projection'],b['result']['completion']['observation']['status'],b['result']['completion']['observation']['reason'] or '',b['result']['statistics']['observation']] for b in report['results']]
    consumers=[[c['consumerId'],c['status'],c['reason'] or '',[[id_token(f['sequenceId']),f['observationBatchId'],f['query']['point']['position'],id_token(f['query']['point']['entryId']),id_token(f['query']['point']['operationId']),id_token(f['query']['objectId'])] for f in c['facts']]] for c in report['consumers']]
    return dict(resultId=report['resultId'],publication=id_token(report['publicationId']),status=report['preparationStatus'],epoch=report['planningEpoch'],observations=rows,analyses=analyses,batches=batches,consumers=consumers,metrics=report['statistics']['planning'],composition=report['statistics']['composition'],airReads=1,airBytesObserved=air_bytes)
def unordered(value):
    if isinstance(value,dict):return {k:unordered(v) for k,v in value.items()}
    if isinstance(value,list):return sorted((unordered(v) for v in value),key=token)
    return value
def semantic_air_oracle(air,report,expected_candidate):
    require(report['preparationStatus']=='COMPLETE','canonical preparation')
    pub=air['publication'];expected=[]
    for unit in pub['units']:
        for entry in unit['entries']:
            for seq in unit['sequences']:
                last={}
                for op in seq['instructions']:
                    if op['kind']=='assign':last[token(op['destination']['object'])]=op
                for op in last.values():expected.append((entry,seq,op))
    for batch in report['results']:
        result=batch['result'];unit=next(u for u in pub['units'] if u['id']==result['unitId']);entry=next(e for e in unit['entries'] if e['id']==result['entryId'])
        expected_scope=dict(scope=unit['id'],publicationInventory=pub['coverage']['inventory'],unitInventory=unit['coverage']['inventory'],entryUncertaintyRefs=entry['state']['uncertainties'],remainderPolicy='PER_OBSERVATION_W3')
        require(unordered(result['sourceScope'])==unordered(expected_scope),'source inventory/Entry context changed in wire')
    actual=[o for b in report['results'] for o in b['result']['observations']]
    require(len(actual)==len(expected),'default write/query coverage')
    for entry,seq,op in expected:
        found=[o for o in actual if o['point']['entryId']==entry['id'] and o['point']['operationId']==seq['terminator']['header']['id'] and o['subject']['objectId']==op['destination']['object']]
        require(len(found)==1,'generic BEFORE destination binding');o=found[0]
        require(o['point']['position']=='BEFORE' and o['queryStatus']=='VALUE' and o['reachability']=='REACHABLE','expected point value')
        facts=[f for c in report['consumers'] for f in c['facts'] if f['query']['point']==o['point'] and f['query']['objectId']==o['subject']['objectId']]
        require(len(facts)==1 and facts[0]['sequenceId']==seq['label'],'generic fact Sequence binding')
        literal=op['value']['value']['value'];require(o['value']['enumerated']==[literal] and literal in ({'OLDER','NEWER'} if expected_candidate=='NEWER' else {expected_candidate}),'literal/overwrite oracle')
        require(o['value']['modelValueRemainder'] is False and o['sourceUnknownRemainder'] is True and o['effectiveUnknownRemainder'] is True,'PARTIAL source remainder preserved')
        supports=o['candidateSupports'];require(len(supports)==1 and supports[0]['candidate']==literal,'candidate support retained')
        require(supports[0]['producers']==[dict(evidence=op['header']['id'],origin=op['header']['origin'],premiseRefs=[])],'real Assign producer/origin association')
    if not expected:require(report['analyses']==report['results']==report['consumerPlan']==report['consumers']==[],'zero-write fabricated run/fact')
    require(sum(len(c['facts']) for c in report['consumers'])==len(expected),'generic fact coverage')

def run(root,work,config):
    work.mkdir(parents=True,exist_ok=False);cp=classpath(root);jars={p:digest(p) for p in cp};java='java'
    for name in ('frontend','lower'):
        require(all(digest(p)==sha for p,sha in config[name]['jars'].items()),'producer JAR drift')
    before={n:snapshot(Path(v['path'])) for n,v in config['before'].items()}
    reports={};chain={}
    for name,fixture,candidate in [('cp4e-a','AIR-MOVE.cbl','PROGA'),('cp4e-b','AIR-MOVE.cbl','PROGA'),('cp3','semantic-product-entry-goback.cbl',None),('generic-overwrite','AIR-MOVE.cbl','NEWER')]:
        directory=work/name;directory.mkdir();source=directory/fixture
        original=Path(config['frontend']['cwd'])/'src/test/resources/cobol/semantic'/fixture
        text=original.read_text()
        if name=='generic-overwrite':
            text=text.replace('AIR-MOVE','ALT-MOVE').replace('WS-PGM','ALT-VALUE').replace("MOVE 'PROGA' TO ALT-VALUE.","MOVE 'OLDER' TO ALT-VALUE\n           MOVE 'NEWER' TO ALT-VALUE.")
        source.write_text(text)
        frontend=directory/'frontend';sp=frontend/'cobol-semantic-product.json';air=directory/'air.json';result=directory/'result.json'
        f=config['frontend'];l=config['lower'];stages=[]
        commands=[('frontend',[java,'-cp',os.pathsep.join(f['classpath']),f['main'],'--source',str(source),'--copybooks',str(directory),'--output',str(frontend)],Path(f['cwd']),source,sp),('lower',[java,'-cp',os.pathsep.join(l['classpath']),l['main'],str(sp),str(air)],Path(l['cwd']),sp,air),('analysis',[java,'-cp',os.pathsep.join(cp),MAIN,str(air),str(result),'--result-id','canonical-'+('cp3' if candidate is None else 'generic' if name=='generic-overwrite' else 'cp4e')],root,air,result)]
        for stage,argv,cwd,input_path,output_path in commands:
            receipt=execute_stage(stage,argv,cwd,input_path,output_path,directory)
            verify_stage(receipt,argv,input_path,output_path);stages.append(receipt)
        report=read_result(result);delivery=load(directory/'analysis.stdout');verify_receipt(delivery,report,result);semantic_air_oracle(load(air),report,candidate)
        memory=directory/'memory-semantics.json';memory_wire=directory/'memory-result.json';argv=[java,'-cp',os.pathsep.join([str(root/'analysis-adapters/target/test-classes'),*cp]),'io.github.gustavo2358.analysis.adapters.MemoryOracle',str(air),str(memory),report['resultId'],str(memory_wire)]
        receipt=execute_stage('memory',argv,root,air,memory,directory);verify_stage(receipt,argv,air,memory);stages.append(receipt)
        require(unordered(load(memory))==unordered(semantic_projection(report,air.stat().st_size)),'memory/file semantic mismatch')
        require(result.read_bytes()==memory_wire.read_bytes(),'memory/file deterministic bytes differ')
        if name=='generic-overwrite':
            require(any(o['value'] and o['value']['enumerated']==['NEWER'] for b in report['results'] for o in b['result']['observations']),'generic overwrite final candidate missing')
            require(report['publicationId']!=reports['cp4e-a']['publicationId'],'generic Publication identity must differ')
        reports[name]=report;chain[name]=dict(stages=stages,cobolSha256=digest(source),spSha256=digest(sp),airSha256=digest(air),resultSha256=digest(result),receipt=delivery,candidate=candidate,memoryFile='SEMANTIC_AND_BYTE_IDENTICAL')
    require(unordered(reports['cp4e-a'])==unordered(reports['cp4e-b']),'fresh semantic determinism')
    for field in ('spSha256','airSha256','resultSha256'):require(chain['cp4e-a'][field]==chain['cp4e-b'][field],'fresh byte determinism: '+field)
    after={n:snapshot(Path(v['path'])) for n,v in config['before'].items()};require(before==after,'sibling modified')
    result=dict(schema='w5-s10-evidence',status='PASS',javaVersion=subprocess.check_output([java,'-version'],stderr=subprocess.STDOUT,text=True),producers=config,analysisJars=jars,before=before,after=after,runs=chain,scope='Canonical CP4E/CP3 only; external pinned AIR codec size debt remains')
    (work/'receipt.json').write_text(json.dumps(result,indent=2)+'\n');print('[w5-S10] PASS: two fresh COBOL CP4E runs + CP3 + generic overwrite; actual stages; independent AIR and memory oracles');return result
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--work',type=Path,required=True);p.add_argument('--producers',type=Path,required=True);a=p.parse_args();run(a.root.resolve(),a.work.resolve(),load(a.producers))
