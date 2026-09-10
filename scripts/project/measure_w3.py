#!/usr/bin/env python3
"""External HotSpot full-GC histograms and JFR; no diagnostic dependency in core."""
import argparse,gzip,hashlib,json,os,re,subprocess,time

def diagnostic(cmd):
    result=subprocess.run(cmd,capture_output=True,text=True)
    if result.returncode:raise RuntimeError(str(cmd)+'\n'+result.stdout+'\n'+result.stderr)
    return result
from pathlib import Path
from check_w3 import ROOT
from challenge_w1 import hashes

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
    java=Path(os.environ['JAVA_HOME'])/'bin/java';jcmd=java.with_name('jcmd');jfr=java.with_name('jfr')
    cp=os.pathsep.join(str(ROOT/p) for p in ['analysis-values/target/test-classes','analysis-values/target/classes','analysis-kernel/target/classes','cfg-kernel/target/classes'])+os.pathsep+(ROOT/'analysis-values/target/test-classpath.txt').read_text().strip()
    receipt={'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'working_source_hashes':hashes(ROOT),'kind':'OBSERVATION_ONLY','physical_bytes':'HotSpot GC.class_histogram shallow bytes after full GC, not exclusive retained size','cases':[]}
    for n in [10000,20000,200000]:
        case=out/str(n);case.mkdir();cmd=[str(java),'-Xms64m','-Xmx768m','-XX:+UseCompressedOops','-XX:+UseCompressedClassPointers','-XX:ObjectAlignmentInBytes=8',f'-Xlog:gc*:file={case}/gc.log',f'-XX:StartFlightRecording=filename={case}/recording.jfr,settings=profile,dumponexit=true','-cp',cp,'io.github.gustavo2358.analysis.values.RetentionProbe',str(n)]
        row={'operations':n,'command':cmd,'phases':{}};receipt['cases'].append(row)
        start=time.monotonic();p=subprocess.Popen(cmd,cwd=ROOT,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        raw=[]
        try:
            for line in p.stdout:
                raw.append(line)
                if not line.startswith('W3_PHASE '):continue
                phase=line.strip().split()[1]
                if phase=='air_cfg':
                    for name in ['VM.version','VM.flags','GC.heap_info']:
                        result=diagnostic([str(jcmd),str(p.pid),name]);(case/(name+'.txt')).write_text(result.stdout)
                result=diagnostic([str(jcmd),str(p.pid),'GC.class_histogram'])
                data=result.stdout.encode();(case/(phase+'.histogram.txt.gz')).write_bytes(gzip.compress(data,mtime=0))
                counts={}
                for text in result.stdout.splitlines():
                    match=re.match(r'\s*\d+:\s+(\d+)\s+(\d+)\s+(\S+)',text)
                    if match and match[3].startswith('io.github.gustavo2358.analysis.'):
                        counts[match[3]]={'instances':int(match[1]),'shallow_bytes':int(match[2])}
                row['phases'][phase]={'sha256':hashlib.sha256(data).hexdigest(),'counts':counts}
                p.stdin.write('\n');p.stdin.flush()
            row['exit']=p.wait(timeout=30);row['elapsed_seconds']=time.monotonic()-start;(case/'process.log').write_text(''.join(raw));assert row['exit']==0
        finally:
            (case/'process.log').write_text(''.join(raw))
            (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
            if p.poll() is None:p.kill();p.wait()
        assert set(row['phases'])=={'air_cfg','prepared','solved','batch_only','released'}
        prefix='io.github.gustavo2358.analysis.values.'
        def count(phase,name):return row['phases'][phase]['counts'].get(prefix+name,{}).get('instances',0)
        assert count('solved','PersistentBindings$Node')==1, row
        assert count('solved','PossibleValuesState')<=4
        assert count('solved','SupportSet')==n+1, 'prepared singleton supports plus EMPTY'
        assert count('batch_only','SupportSet')==1 and count('released','SupportSet')==1, 'only global EMPTY support remains'
        assert count('batch_only','ValueFact$Support')==1 and count('released','ValueFact$Support')==0, 'detached support released'
        for phase in ['batch_only','released']:
            for name in ['PersistentBindings$Node','ValueUniverse','PossibleValuesAnalysis$Execution','PossibleValuesAnalysis','TextProfile']:
                assert count(phase,name)==0,(n,phase,name)
        assert count('batch_only','ValueFact')==1 and count('released','ValueFact')==0
        summary=subprocess.run([str(jfr),'summary',str(case/'recording.jfr')],capture_output=True,text=True,check=True).stdout;(case/'jfr-summary.txt').write_text(summary)
        # Keep actual recording compressed as evidence, never a regenerated approximation.
        data=(case/'recording.jfr').read_bytes();(case/'recording.jfr.gz').write_bytes(gzip.compress(data,mtime=0));(case/'recording.jfr').unlink()
        data=(case/'gc.log').read_bytes();(case/'gc.log.gz').write_bytes(gzip.compress(data,mtime=0));(case/'gc.log').unlink()
        print(f'[w3-memory] N={n}: measured node=1; batch releases execution/state/universe; release removes fact',flush=True)
    receipt['files']={p.relative_to(out).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.rglob('*')) if p.is_file()}
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
if __name__=='__main__':main()
