#!/usr/bin/env python3
"""Source-level W1/W2 oracles through normal CLI defaults. No physical opt-in.

--runtime is a JSON map of frontend/lower/cfg/dependency main + classpath arrays,
plus checkouts.proleap-poc (for frontend resources). Preserve it with evidence.
Each JVM is bounded; timeout/failure never counts as a completed stable result.
"""
import argparse, hashlib, json, os, pathlib, subprocess, time
ROOT = pathlib.Path(__file__).resolve().parents[2]
FIXTURES = ROOT/'analysis-adapters/src/test/resources/logical-text-w2'

def run(config, work, name, source, expected):
    work.mkdir(parents=True, exist_ok=False)
    (work/'input.cbl').write_text(source)
    web=work/'src/main/resources';web.mkdir(parents=True)
    (web/'web').symlink_to(pathlib.Path(config['checkouts']['proleap-poc'])/'src/main/resources/web')
    result={'fixture':name,'expected':expected,'status':'NOT_RUN','phases':{}}
    stages=[('frontend',['--source',work/'input.cbl','--copybooks',work,'--output',work/'sp']),
            ('lower',[work/'sp/cobol-semantic-product.json',work/'air.json']),
            ('cfg',[work/'air.json',work/'cfg.json']),('dependency',[work/'air.json',work/'dependencies.json'])]
    for stage,args in stages:
        command=['java','-Xmx2g','-cp',os.pathsep.join(config[stage]['classpath']),config[stage]['main'],*map(str,args)]
        start=time.monotonic()
        with (work/(stage+'.stdout')).open('wb') as stdout,(work/(stage+'.stderr')).open('wb') as stderr:
            try:code=subprocess.run(command,cwd=work,stdout=stdout,stderr=stderr,timeout=120).returncode
            except subprocess.TimeoutExpired:result['status']='TIMEOUT';break
        result['phases'][stage]={'seconds':time.monotonic()-start,'exitCode':code,'command':command}
        if code:result['status']='PIPELINE_FAILURE';break
    else:
        dep=json.loads((work/'dependencies.json').read_text());sites=dep['sites'];m=dep['metrics']
        sp=json.loads((work/'sp/cobol-semantic-product.json').read_text());air=json.loads((work/'air.json').read_text())['publication']
        actual=sorted({c['referenceName'] for s in sites for c in s['candidates']})
        assert actual==expected['targets'],(name,actual,expected)
        assert len(sites)==1 and len(dep['edges'])==len(actual)
        if 'rawTargets' in expected:assert sorted(c['rawValue'] for c in sites[0]['rawCandidates'])==expected['rawTargets']
        if expected['targets']:
            assert sp['storage']['logicalTextViews']
            assert all(x['kind']=='cell' for x in air['storage'])
        origins={json.dumps(o['id'],sort_keys=True):o for o in dep['origins']}
        def source_lines(origin):
            o=origins[json.dumps(origin,sort_keys=True)]
            if o['kind']=='WRITTEN':return [int(o['location']['startLine'])] if o.get('location') else []
            return [line for parent in o.get('inputs',[]) for line in source_lines(parent)]
        call_line=next(i for i,line in enumerate(source.splitlines(),1) if 'CALL WS-PGM.' in line)
        assert call_line in source_lines(sites[0]['siteOrigin'])
        assert sites[0]['effectiveUnknownRemainder']==expected['remainder']
        assert sites[0]['siteOrigin'] and sites[0]['targetOrigin'] and sites[0]['provenance']
        assert sites[0]['caller']==air['units'][0]['id']
        assert sp['unit']['canonicalProgramName']==expected['caller'],sp['unit']
        assert sp['storage']['profile']=='UNSPECIFIED'
        for metric,value in {'logicalOnlyMode':1,'experimentalPhysicalMode':0,'physicalGroupsApplied':0,'physicalWritesApplied':0}.items():assert m[metric]==value,(name,metric,m.get(metric))
        result.update(status='PASS',actual=actual,remainder=sites[0]['effectiveUnknownRemainder'],modelRemainder=sites[0]['modelValueRemainder'],metrics=m,
            logicalViews=len(sp['storage'].get('logicalTextViews',[])),airCells=sum(x['kind']=='cell' for x in air['storage']),
            dependenciesSha256=hashlib.sha256((work/'dependencies.json').read_bytes()).hexdigest())
    (work/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(name,result['status'],result.get('actual'),flush=True)
    return result

def scale_source(n):
    data=['01 REC-A.','05 PART-A PIC X(4).','05 PART-B PIC X(4).','66 RANGE-A RENAMES PART-A THRU PART-B.',
          '01 ALIAS-A REDEFINES REC-A PIC X(8).','01 WS-PGM PIC X(8).']
    moves=[]
    for i in range(n):
        data += [f'01 DEAD-{i}.',f'05 A-{i} PIC X(4).',f'05 B-{i} PIC X(4).',f'66 R-{i} RENAMES A-{i} THRU B-{i}.',f'01 V-{i} REDEFINES DEAD-{i} PIC X(8).']
        moves += [f"MOVE 'UNUSED00' TO DEAD-{i}."]
    lines=['IDENTIFICATION DIVISION.','PROGRAM-ID. W2-SCALE.','DATA DIVISION.','WORKING-STORAGE SECTION.',*data,
           'PROCEDURE DIVISION.',*moves,"MOVE 'PROGA001' TO ALIAS-A.",'MOVE RANGE-A TO WS-PGM.','CALL WS-PGM.','GOBACK.']
    return ''.join('       '+x+'\n' for x in lines)

def main():
    p=argparse.ArgumentParser();p.add_argument('--runtime',type=pathlib.Path,required=True);p.add_argument('--work',type=pathlib.Path,required=True);p.add_argument('--scale',action='store_true');p.add_argument('--cases',nargs='*');a=p.parse_args()
    config=json.loads(a.runtime.read_text());work=a.work.resolve();work.mkdir(parents=True,exist_ok=False);results=[]
    if a.scale:
        for n in [10,100,1000]:results.append(run(config,work/str(n),str(n),scale_source(n),{'caller':'W2-SCALE','targets':['PROGA001'],'remainder':True}))
        metrics=['PossibleValues.prepare_demandCellsPrepared','PossibleValues.prepare_demandWritesPrepared','PossibleValues.prepare_producersPrepared']
        assert all(r['status']=='PASS' for r in results)
        for metric in metrics:assert len({r['metrics'][metric] for r in results})==1,(metric,[r['metrics'][metric] for r in results])
    else:
        for name,expected in json.loads((FIXTURES/'expected.json').read_text()).items():
            if a.cases and name not in a.cases:continue
            results.append(run(config,work/name,name,(FIXTURES/(name+'.cbl')).read_text(),expected))
    (work/'results.json').write_text(json.dumps(results,indent=2)+'\n')
    if any(r['status']!='PASS' for r in results):raise SystemExit(1)
if __name__=='__main__':main()
