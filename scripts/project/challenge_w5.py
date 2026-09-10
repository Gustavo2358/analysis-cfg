#!/usr/bin/env python3
"""W5 mutation ritual: actual compiled nominal RED, exact restore and a second nominal GREEN."""
from __future__ import annotations
import argparse, gzip, hashlib, json, os, subprocess, sys, xml.etree.ElementTree as ET
from pathlib import Path
from check_w5 import ROOT, TESTS, test_inventory, verify_reports

ADAPTER='analysis-adapters/src/main/java/io/github/gustavo2358/analysis/adapters/'
PLAN='analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/DefaultValuePlan.java'
def java(name,old,new):return dict(target=ADAPTER+name+'.java',old=old,new=new,kind='java')
MUTANTS={
 'fixture-specific-production-route':dict(target=PLAN,old='if(assign.destination() instanceof Places.ObjectPlace place)',new='if(assign.destination() instanceof Places.ObjectPlace place && place.object().localId().equals("data-slot"))',kind='java'),
 'prepared-payload-certifies-own-delivery':java('ResultJson','return object("schema","prepared-analysis-result"','return object("deliveryStatus","COMPLETE","schema","prepared-analysis-result"'),
 'drop-batch-dependency-reason-in-wire':java('ResultJson','String observationReason=batch==null?null:batch.reason();','String observationReason=batch!=null&&batch.status()==BatchStatus.FAILED?batch.reason():null;'),
 'drop-candidate-support-in-wire':java('ResultJson','"candidateSupports",f==null?List.of():supports(f)','"candidateSupports",List.of()'),
 'swap-candidate-support-in-wire':java('ResultJson','new ArrayList<>(s.producers())','new ArrayList<>(supports.getFirst().producers())'),
 'source-partial-promoted-to-exact-in-wire':java('ResultJson','"sourceUnknownRemainder",f==null?null:f.sourceUnknownRemainder()','"sourceUnknownRemainder",f==null?null:false'),
 'full-id-owner-loss':java('WireIds','return Collections.unmodifiableMap(out);','return Map.of("localId",id.localId());'),
 'wrong-result-id-receipt':java('LocalResultWriter','new DeliveryReceipt(result.result().resultId(),hash','new DeliveryReceipt("wrong-result",hash'),
 'wrong-result-hash-receipt':java('LocalResultWriter','hash=HexFormat.of().formatHex(output.digest.digest())','hash="0".repeat(64)'),
 'wrong-result-destination-receipt':java('LocalResultWriter','hash,destination.toString()','hash,destination.resolveSibling("wrong").toString()'),
 'writer-failure-reported-complete':java('LocalResultWriter','reason==null?DeliveryReceipt.Status.COMPLETE:DeliveryReceipt.Status.FAILED,reason','DeliveryReceipt.Status.COMPLETE,null'),
 'partial-final-file-after-write-failure':java('LocalResultWriter','return Files.createTempFile(destination.getParent(),".analysis-result-",".tmp");','return destination;'),
 'nondeterministic-result-order':java('ResultJson','var supports=new ArrayList<>(fact.candidateSupports());supports.sort(Comparator.comparing(s->s.candidate().value()));','var supports=new ArrayList<>(fact.candidateSupports());'),
 'local-input-size-cap-used-as-semantic-admission':java('DataflowAirReader','byte[] bytes;','if(Files.size(path)>16*1024*1024)throw new IOException("UNSUPPORTED local input size"); byte[] bytes;'),
 'maximum-result-size':java('LocalResultWriter','@Override public void write(byte[] b,int off,int len)throws IOException {try{out.write(b,off,len);}','@Override public void write(byte[] b,int off,int len)throws IOException {if(bytes+len>64L*1024*1024)throw new IOException("result cap");try{out.write(b,off,len);}'),
 'resource-cap-on-result-facts-queries':dict(target=PLAN,old='return new Selection(registrations,Map.of(',new='if(queries>1500)registrations.clear(); return new Selection(registrations,Map.of(',kind='java'),
 'catch-oom-as-analysis-limit':java('LocalResultWriter','catch(IllegalArgumentException|IllegalStateException failure)','catch(Throwable failure)'),
 'legacy-cfg-writer-used-for-analysis-result':java('ResultJson','"schema","prepared-analysis-result"','"schema","cfg-json"'),
 'golden-substituted-for-real-stage':dict(target='scripts/project/e2e_w5.py',old='pid,proc=real_process(argv,cwd)',new='output_path.parent.mkdir(parents=True,exist_ok=True); output_path.write_bytes(Path(os.environ["W5_GOLDEN_RESULT"]).read_bytes()); pid,proc=None,subprocess.CompletedProcess(argv,0,b"",b"")',kind='python')}

def hashes(root):
    paths=[root/'pom.xml']+[p for m in ('analysis-dataflow','analysis-adapters','analysis-launcher') for p in (root/m).rglob('*') if p.is_file() and 'target' not in p.parts and (p.suffix=='.java' or p.name=='pom.xml')]
    paths += [root/'scripts/project/e2e_w5.py',root/'scripts/project/result_wire.py',root/'scripts/project/test_result_wire.py']
    return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}
def campaign(root,out,selected):
    out.mkdir(parents=True,exist_ok=False);original=hashes(root);receipt=dict(schema='w5-challenge-campaign',source_head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),source_sha256=original,attempts=[])
    env=dict(os.environ,W5_GOLDEN_RESULT=str(root/'docs/evals/cp5/w5-prepared.snapshot.json'))
    selector=','.join(['BuildCfgContractTest','StructureTest','ValuesTest',*(t for suites in TESTS.values() for t in suites)])
    mvn=['mvn','-B','-ntp','-pl','analysis-launcher','-am']
    nominal=mvn+['test','-Dtest='+selector]
    def save(): (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    def execute(directory,phase,args):
        proc=subprocess.run(args,cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        path=directory/(phase+'.log.gz');path.write_bytes(gzip.compress(proc.stdout,mtime=0))
        return dict(command=args,exitCode=proc.returncode,log=path.relative_to(out).as_posix(),sha256=hashlib.sha256(proc.stdout).hexdigest())
    def test(directory,phase,kind):
        row=execute(directory,phase,[sys.executable,'-B','scripts/project/test_result_wire.py'] if kind=='python' else nominal)
        if row['exitCode']==0 and kind=='java':verify_reports(root,test_inventory('performance'))
        return row
    try:
        for name in selected:
            mutation=MUTANTS[name];directory=out/name;directory.mkdir();path=root/mutation['target'];data=path.read_bytes();entry=dict(id=name,target=mutation['target'],status='STARTED',stages=[]);receipt['attempts'].append(entry);save()
            try:
                stage=test(directory,'baseline-green',mutation['kind']);entry['stages'].append(stage)
                if stage['exitCode']!=0:raise AssertionError('baseline not GREEN')
                text=data.decode();assert text.count(mutation['old'])==1,'mutation anchor not unique'
                path.write_text(text.replace(mutation['old'],mutation['new'],1));entry['mutant_sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
                compile_args=mvn+['test-compile','-DskipTests'] if mutation['kind']=='java' else [sys.executable,'-c','import ast,pathlib;ast.parse(pathlib.Path('+repr(str(path))+').read_text())']
                stage=execute(directory,'compile',compile_args);entry['stages'].append(stage)
                if stage['exitCode']!=0:entry['status']='INVALID_COMPILE';raise AssertionError('compilation failure is not nominal RED')
                stage=test(directory,'nominal-red',mutation['kind']);entry['stages'].append(stage)
                if stage['exitCode']==0:entry['status']='SURVIVED';raise AssertionError('mutant survived nominal tests')
                if mutation['kind']=='java':
                    failures=[]
                    for module,suites in TESTS.items():
                        for report in (root/module/'target/surefire-reports').glob('TEST-*.xml'):
                            doc=ET.parse(report).getroot()
                            for case in doc.findall('testcase'):
                                if case.find('failure') is not None or case.find('error') is not None:failures.append(dict(classname=case.attrib['classname'],method=case.attrib['name']))
                    if not failures:raise AssertionError('nonzero command without a nominal test RED')
                    entry['nominal_failures']=failures
                entry['status']='VALID_RED'
            finally:
                path.write_bytes(data);entry['byte_exact_restore']=hashes(root)==original;save()
            if not entry['byte_exact_restore']:raise AssertionError('exact restore failed')
            stage=test(directory,'second-green',mutation['kind']);entry['stages'].append(stage)
            if stage['exitCode']!=0:raise AssertionError('second GREEN failed')
            entry['status']='KILLED';save();print('[w5-challenge] '+name+': compilable → nominal RED → exact restore → second GREEN',flush=True)
        receipt['status']='PASS'
    finally:receipt['final_source_sha256']=hashes(root);save()
    return receipt
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--output',type=Path,required=True);p.add_argument('--only',choices=list(MUTANTS));a=p.parse_args();campaign(a.root.resolve(),a.output.resolve(),[a.only] if a.only else list(MUTANTS))
