#!/usr/bin/env python3
"""FD-W7 manual AIR -> real CLI/wire, independent expected candidates and remainders."""
import argparse,json,os,shutil,sys
from pathlib import Path
from e2e_w2d import execute,runtime
from dependency_wire import read,require
from prepare_w2d_producers import ROOT,git
EXPECTED={
 'literal':[('file',['1FILE'],False)],
 'closed':[('file',['ALPHA001','BETA0002'],False)],
 'partial':[('file',['ALPHA001'],True)],
 'unknown':[('file',[],True)],
 'cycle':[('file',['ALPHA001','BETA0002'],False)],
 'timing':[('file1',['FIRST001'],False),('file2',['SECOND02'],False)],
 'alias':[('file',['FILE0001'],False)],'slice':[('file',['FILE0001'],False)],
 'effects':[('first',['BEFORE01'],False),('second',['BEFORE01'],True)],
 'shared':[('file',['SHARED01'],False)],
}
def run(work,manual,producers):
    work.mkdir(parents=True,exist_ok=False);cp=runtime(producers)
    config=json.loads((producers/'producers.json').read_text());lock=json.loads((ROOT/'docs/sources/sources.lock.json').read_text())
    for repo,key in [('air-java','air_java'),('proleap-poc','proleap_poc'),('cobol-lower','cobol_lower')]:
        require(config['sources'][repo]==lock[key]['commit']==git(producers/repo,'rev-parse','HEAD') and not git(producers/repo,'status','--porcelain'),'pinned producer '+repo)
    for name,expected in EXPECTED.items():
        outputs=[]
        for attempt in ('A','B'):
            cwd=work/(name+'-'+attempt);cwd.mkdir();source=cwd/'input.air.json';shutil.copyfile(manual/(name+'.air.json'),source);dep=cwd/'dependencies.json'
            execute(cwd,'dependency',['java','-cp',cp,'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',str(source),str(dep)])
            d=read(dep);require(d['version']=='2.3.0','FILE values wire');sites=d['fileDependencies']['sites'];require(len(sites)==len(expected),'manual site count')
            for id,names,remainder in expected:
                s=next(s for s in sites if s['operation']['localId']==id);require([c['referenceName'] for c in s['candidates']]==names,'independent values');require(s['unknownRemainder']==remainder,'independent remainder')
            if name=='literal':require(d['fileDependencies']['metrics']['possibleValuesPreparations']==0,'literal zero-values')
            if name=='shared':require(d['fileDependencies']['metrics']['analysis.analysisRuns']==0,'general run reused from physical CALL')
            outputs.append(dep.read_bytes());print('PASS W7 manual CLI',name,attempt,flush=True)
        require(outputs[0]==outputs[1],'deterministic manual FILE+CALL wire')
    print('PASS B-AIR/WIRE W7: 10 independent manual AIR cases twice',flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--manual',type=Path,required=True);p.add_argument('--producers',type=Path,required=True);a=p.parse_args()
    try:run(a.work.resolve(),a.manual.resolve(),a.producers.resolve())
    except (ValueError,RuntimeError,OSError) as e:print('FAIL:',e,file=sys.stderr);sys.exit(1)
