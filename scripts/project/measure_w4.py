#!/usr/bin/env python3
"""External GC histograms and JFR for W4 plan/run/result release; diagnostic APIs remain test-only."""
import argparse,gzip,hashlib,json,os,re,subprocess,time
from pathlib import Path
from check_w4 import ROOT
from challenge_w4 import hashes

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args();out=a.output.resolve();out.mkdir(parents=True,exist_ok=False)
    java=Path(os.environ['JAVA_HOME'])/'bin/java';jcmd=java.with_name('jcmd');jfr=java.with_name('jfr')
    cp=os.pathsep.join(str(ROOT/path) for path in ['analysis-values/target/test-classes','analysis-values/target/classes','analysis-kernel/target/classes','cfg-kernel/target/classes'])+os.pathsep+(ROOT/'analysis-values/target/test-classpath.txt').read_text().strip()
    receipt={'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'source_sha256':hashes(ROOT),'kind':'OBSERVATION_ONLY','measurement':'HotSpot GC.class_histogram shallow bytes after full GC; not exclusive retained heap. JFR allocation samples, not census.','cases':[]}
    def save(): (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    def evidence(path,data):path.with_suffix(path.suffix+'.gz').write_bytes(gzip.compress(data,mtime=0))
    try:
        for n in [1000,2000,4000]:
            case=out/str(n);case.mkdir();args=[str(java),'-Xms64m','-Xmx768m','-XX:+UseCompressedOops','-XX:+UseCompressedClassPointers','-XX:ObjectAlignmentInBytes=8',f'-Xlog:gc*:file={case}/gc.log',f'-XX:StartFlightRecording=filename={case}/recording.jfr,settings=profile,dumponexit=true','-cp',cp,'io.github.gustavo2358.analysis.values.PlanningRetentionProbe',str(n)]
            row={'consumers':n,'operations':10001,'command':args,'phases':{}};receipt['cases'].append(row);raw=[];start=time.monotonic()
            proc=subprocess.Popen(args,cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
            try:
                for line in proc.stdout:
                    raw.append(line)
                    if not line.startswith('W4_PHASE '):continue
                    phase=line.split()[1]
                    if phase=='air_cfg':
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
            assert set(row['phases'])=={'air_cfg','planned','executed','result_only','released'}
            assert count('executed','values.PossibleValuesAnalysis$Execution')==1
            assert count('executed','values.ValueFact')==1
            assert count('planned','application.ConsumerRegistration')==n
            for phase in ['result_only','released']:
                for name in ['structure.AnalysisSession','structure.ProgramIndex','solver.DataflowResult','application.PlanningExecution','application.ExecutionPlan','application.ConsumerRegistration','values.ValueUniverse','values.TextProfile','values.PossibleValuesAnalysis$Execution']:
                    assert count(phase,name)==0,(n,phase,name)
            assert count('result_only','values.ValueFact')==1 and count('released','values.ValueFact')==0
            assert count('result_only','values.ValueFact$Support')==1 and count('released','values.ValueFact$Support')==0
            assert count('result_only','values.PlanningFixtures$TestFact')==n and count('released','values.PlanningFixtures$TestFact')==0
            data=subprocess.check_output([str(jfr),'summary',str(case/'recording.jfr')]);evidence(case/'jfr-summary.txt',data)
            for name in ['gc.log','recording.jfr']:
                path=case/name;evidence(path,path.read_bytes());path.unlink()
            print('[w4-memory] consumers='+str(n)+': shared ValueFact=1; result-only releases plan, session, run, universe and consumers; release removes fact/support',flush=True)
        receipt['status']='PASS'
    finally:
        receipt['files']={p.relative_to(out).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.rglob('*')) if p.is_file() and p!=out/'receipt.json'};save()
if __name__=='__main__':main()
