#!/usr/bin/env python3
"""External GC histograms and JFR for W5 composition/result/delivery release; diagnostic APIs remain test-only."""
import argparse,gzip,hashlib,json,os,re,subprocess,time
from pathlib import Path
from check_w5 import ROOT
from challenge_w5 import hashes

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    java=Path(os.environ['JAVA_HOME'])/'bin/java';jcmd=java.with_name('jcmd');jfr=java.with_name('jfr')
    from e2e_w5 import classpath
    cp=os.pathsep.join([str(ROOT/'analysis-adapters/target/test-classes'),*classpath(ROOT)])
    receipt={'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'source_sha256':hashes(ROOT),'kind':'OBSERVATION_ONLY','measurement':'HotSpot GC.class_histogram shallow bytes after full GC; not exclusive retained heap. JFR allocation samples, not census.','cases':[]}
    def save(): (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    def evidence(path,data):path.with_suffix(path.suffix+'.gz').write_bytes(gzip.compress(data,mtime=0))
    try:
        for n in [1000,2000,4000]:
            case=out/str(n);case.mkdir();args=[str(java),'-Xms64m','-Xmx768m','-XX:+UseCompressedOops','-XX:+UseCompressedClassPointers','-XX:ObjectAlignmentInBytes=8',f'-Xlog:gc*:file={case}/gc.log',f'-XX:StartFlightRecording=filename={case}/recording.jfr,settings=profile,dumponexit=true','-cp',cp,'io.github.gustavo2358.analysis.adapters.DataflowRetentionProbe',str(n),str(case/'result.json')]
            row={'objects':n,'operations':n,'warmupRuns':0,'command':args,'phases':{}};receipt['cases'].append(row);raw=[];start=time.monotonic()
            proc=subprocess.Popen(args,cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
            try:
                for line in proc.stdout:
                    raw.append(line)
                    if not line.startswith('W5_PHASE '):continue
                    phase=line.split()[1]
                    if phase=='air':
                        for name in ['VM.version','VM.flags','GC.heap_info']:
                            data=subprocess.check_output([str(jcmd),str(proc.pid),name]);evidence(case/(name+'.txt'),data)
                    data=subprocess.check_output([str(jcmd),str(proc.pid),'GC.class_histogram']);evidence(case/(phase+'.histogram.txt'),data)
                    counts={}
                    for line in data.decode().splitlines():
                        m=re.match(r'\s*\d+:\s+(\d+)\s+(\d+)\s+(\S+)',line)
                        if m and m[3].startswith('io.github.gustavo2358.analysis.'):counts[m[3]]={'instances':int(m[1]),'shallow_bytes':int(m[2])}
                    row['phases'][phase]={'sha256':hashlib.sha256(data).hexdigest(),'counts':counts};save();proc.stdin.write('\n');proc.stdin.flush()
                row['exit']=proc.wait(timeout=30);row['elapsedSeconds']=time.monotonic()-start
                if row['exit']!=0:raise AssertionError('diagnostic probe failed')
            finally:
                evidence(case/'process.log',''.join(raw).encode());save()
                if proc.poll() is None:proc.kill();proc.wait()
            prefix='io.github.gustavo2358.analysis.'
            def count(phase,name):return row['phases'][phase]['counts'].get(prefix+name,{}).get('instances',0)
            assert set(row['phases'])=={'air','prepared','delivered','released'}
            assert count('air','dataflow.PreparedDataflowResult')==0
            for phase in ['prepared','delivered','released']:
                for name in ['structure.AnalysisSession','structure.ProgramIndex','solver.DataflowResult','application.PlanningExecution','application.ExecutionPlan','application.ConsumerRegistration','values.ValueUniverse','values.TextProfile','values.PossibleValuesAnalysis$Execution','adapters.LocalResultWriter$CountingOutput']:
                    assert count(phase,name)==0,(n,phase,name)
            for phase in ['prepared','delivered']:
                assert count(phase,'values.ValueFact')==n
                assert count(phase,'dataflow.ObservedValueFact')==n
            for name in ['values.ValueFact','values.ValueFact$Support','dataflow.ObservedValueFact','dataflow.PreparedDataflowResult','adapters.DeliveryReceipt']:
                assert count('released',name)==0,(n,name)
            assert count('delivered','adapters.DeliveryReceipt')==1
            row['resultBytes']=(case/'result.json').stat().st_size
            row['resultSha256']=hashlib.sha256((case/'result.json').read_bytes()).hexdigest()
            (case/'result.json').unlink()
            data=subprocess.check_output([str(jfr),'summary',str(case/'recording.jfr')]);evidence(case/'jfr-summary.txt',data)
            for name in ['gc.log','recording.jfr']:
                path=case/name;evidence(path,path.read_bytes());path.unlink()
            print('[w5-memory] N='+str(n)+': prepared/delivered detached; no session/plan/run/universe/writer stream; release removes facts and receipt',flush=True)
        receipt['status']='PASS'
    finally:
        receipt['files']={p.relative_to(out).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.rglob('*')) if p.is_file() and p!=out/'receipt.json'};save()
if __name__=='__main__':main()
