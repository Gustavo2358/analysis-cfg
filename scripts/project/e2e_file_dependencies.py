#!/usr/bin/env python3
"""Selected FD-W1 real CLI cohort. Hand-written source oracles, isolated immutable producers."""
import argparse,json,os,shutil,subprocess,sys
from pathlib import Path
from dependency_wire import read,require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import execute,runtime,source_spans
from prepare_w2d_producers import ROOT,git,require_local
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w1'
EXPECTED={
    'static':(['F'],['open','read','close'],[]),
    'declaration-only':(['F'],[],[]),
    'call-only':([],[],['PROGA']),
    'empty':([],[],[]),
    'mixed':(['F'],['open','read','close'],['BEFORE','AFTER']),
    'multi-open':(['F','G'],['open','open','close','close'],[]),
}

def oracle(name,sp,air,result,source):
    require(sp['contractVersion']=='2.22.0' and sp['fileInventory']['version']=='1.1.0','bilateral W1 SP contract')
    names,actions,calls=EXPECTED[name];files=result['fileDependencies']
    require(sorted(d['logicalFile'] for d in files['declarations'])==sorted(names),'manual declaration oracle')
    require(sorted(s['action'] for s in files['sites'])==sorted(actions),'manual use oracle')
    require(sorted(c['referenceName'] for s in result['sites'] for c in s['candidates'])==sorted(calls),'CALL projection')
    require(len(result['sites'])==len(calls),'CALL site count')
    require(len(files['edges'])==len(actions),'reachable FILE edges')
    for d in files['declarations']:
        require(d['name']==('CLIENTDD' if d['logicalFile']=='F' else 'OTHERDD'),'terminal external file name')
        require(d['owner']==air['publication']['units'][0]['id'],'exact owner')
        require(d['sourceKind']=='ASSIGNMENT_NAME' and d['namespace']=='cobol.external-file-name','assignment source/domain')
        require(len(d['objects'])==1,'FD record identity')
    declarations={d['id']['localId']:d for d in files['declarations']}
    for s in files['sites']:
        require(s['targetKind']=='LITERAL' and not s['unknownRemainder'],'known name independent of effects')
        require(s['reachability']=='REACHABLE','known graph use')
        require(s['effects']=='OPEN' and s['control']=='OPEN','W1 does not close effects/control')
        require(len(s['bindings'])==1 and len(s['candidates'])==1,'one native conector')
        d=declarations[s['bindings'][0]['declaration']['localId']]
        require(s['candidates'][0]['referenceName']==d['name'],'resource/use association')
        source_spans(result,s['candidates'][0]['supports'][0],source)
    forbidden={'bindingMechanism','physicalResource','physicalResolution','DSNAME'}
    def scope(value):
        if isinstance(value,dict):
            require(not(set(value)&forbidden),'external resolution field')
            for child in value.values():scope(child)
        elif isinstance(value,list):
            for child in value:scope(child)
    scope(result)


def run(work,config_path,*,fixtures=FIXTURES,expected=EXPECTED,check=oracle,label="FD-W1",frontend_args=()):
    require_local();work.mkdir(parents=True,exist_ok=False)
    producer=config_path.parent;config=json.loads(config_path.read_text());lock=json.loads((ROOT/'docs/sources/sources.lock.json').read_text())
    for repo,key in (('air-java','air_java'),('proleap-poc','proleap_poc'),('cobol-lower','cobol_lower')):
        require(config['sources'][repo]==lock[key]['commit']==git(producer/repo,'rev-parse','HEAD') and not git(producer/repo,'status','--porcelain'),'immutable producer '+repo)
    cp=runtime(producer)
    for name in expected:
        outputs=[]
        for attempt in ('A','B'):
            cwd=work/(name+'-'+attempt);cwd.mkdir();source=cwd/(name+'.cbl');shutil.copyfile(fixtures/source.name,source)
            web=cwd/'src/main/resources';web.mkdir(parents=True);(web/'web').symlink_to(producer/'proleap-poc/src/main/resources/web',target_is_directory=True)
            execute(cwd,'frontend',['java','-cp',os.pathsep.join(config['frontend']['classpath']),config['frontend']['main'],'--source',source.name,'--copybooks',str(producer/'proleap-poc/corpus/cpy'),'--output',str(cwd/'sp'),*frontend_args])
            sp=cwd/'sp/cobol-semantic-product.json';air=cwd/'program.air.json';cfg=cwd/'cfg.json';dep=cwd/'dependencies.json'
            execute(cwd,'lower',['java','-cp',os.pathsep.join(config['lower']['classpath']),config['lower']['main'],str(sp),str(air)])
            execute(cwd,'cfg',['java','-cp',cp,'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg',str(air),str(cfg)])
            verify_cfg_wire(cfg.read_bytes())
            command=['java','-cp',cp,'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',str(air)]
            execute(cwd,'dependency',command+[str(dep)])
            check(name,json.loads(sp.read_text()),json.loads(air.read_text()),read(dep),source)
            outputs.append([p.read_bytes() for p in (sp,air,cfg,dep)])
            if name=='static' and attempt=='A':
                old=cwd/'old-output';old.mkdir();sentinel=old/'prior.dependencies.json';sentinel.write_bytes(dep.read_bytes())
                failure=subprocess.run(command+[str(old)],cwd=cwd,capture_output=True)
                (cwd/'output-failure.stderr').write_bytes(failure.stderr)
                require(failure.returncode==6 and b'OUTPUT_FAILURE' in failure.stderr,'explicit output failure')
                require(sentinel.read_bytes()==dep.read_bytes() and sorted(p.name for p in old.iterdir())==['prior.dependencies.json'],'old output untouched; never new success')
                require(not list(cwd.glob('.dependencies-*.tmp')),'no abandoned temp output')
            print('PASS '+label+' '+name+' '+attempt,flush=True)
        require(outputs[0]==outputs[1],name+' deterministic SP/AIR/CFG/FILE+CALL bytes')
    print('PASS E-SELECTED '+label+' '+str(len(expected))+' fixtures twice, deterministic SP/AIR/CFG/dependencies',flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--producers',type=Path,required=True);args=p.parse_args()
    try:run(args.work.resolve(),args.producers.resolve())
    except (ValueError,RuntimeError,OSError) as error:print('FAIL: '+str(error),file=sys.stderr);sys.exit(1)
