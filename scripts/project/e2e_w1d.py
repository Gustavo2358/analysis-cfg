#!/usr/bin/env python3
"""Two real COBOL → SP 1.3 → AIR → CFG/dependencies executions, with independent source audit."""
import argparse,json,os,shutil,subprocess
from pathlib import Path
from dependency_wire import read,require
from prepare_w5_producers import digest
from prepare_w1d_producers import FRONT,LOWER
from cfg_wire_contract import verify as verify_cfg_wire
ROOT=Path(__file__).resolve().parents[2]
def run(work,config):
    work.mkdir(parents=True,exist_ok=False);producer=Path(config).resolve().parent;config=json.loads(Path(config).read_text())
    require(config['sources']['proleap-poc']['commit']==FRONT and config['sources']['cobol-lower']['commit']==LOWER,'frozen producer pins')
    for part in ('frontend','lower'):
        require(all(digest(Path(p))==sha for p,sha in config[part]['jars'].items()),'producer JAR changed')
    runtime=(ROOT/'analysis-launcher/target/runtime-classpath.txt').read_text().strip()
    # Reactor target/classes take precedence over installed development snapshots.
    modules=['cfg-kernel','cfg-adapters','cfg-launcher','analysis-kernel','analysis-values','analysis-dependencies','analysis-dataflow','analysis-adapters','analysis-launcher']
    cp=os.pathsep.join([str(ROOT/m/'target/classes') for m in modules]+[runtime]);commands=[];cases={};cfg_contracts={}
    def execute(cwd,label,args):
        completed=subprocess.run(args,cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        stdout=cwd/(label+'.stdout');stderr=cwd/(label+'.stderr');stdout.write_bytes(completed.stdout);stderr.write_bytes(completed.stderr)
        commands.append(dict(argv=args,cwd=str(cwd),exitCode=completed.returncode,stdout=str(stdout),stderr=str(stderr),stdoutSha256=digest(stdout),stderrSha256=digest(stderr)))
        return completed.returncode
    for name in ('dynamic-x8','literal','dynamic-no-move','using'):
        runs=[]
        for attempt in (1,2):
            cwd=work/(name+'-'+str(attempt));cwd.mkdir();fixture=producer/'cobol-lower/adapters/src/test/resources/sp/cp6'/(name+'.cbl');source=cwd/fixture.name;shutil.copyfile(fixture,source)
            web=cwd/'src/main/resources';web.mkdir(parents=True);(web/'web').symlink_to(producer/'proleap-poc/src/main/resources/web',target_is_directory=True)
            out=cwd/'sp';front=['java','-cp',os.pathsep.join(config['frontend']['classpath']),config['frontend']['main'],'--source',source.name,'--copybooks',str(producer/'proleap-poc/corpus/cpy'),'--output',str(out)]
            require(execute(cwd,'frontend',front)==0,'frontend failed')
            sp=out/'cobol-semantic-product.json';require(json.loads(sp.read_text())['contractVersion']=='1.3.0','SP contract version')
            air=cwd/'program.air.json';rc=execute(cwd,'lower',['java','-cp',os.pathsep.join(config['lower']['classpath']),config['lower']['main'],str(sp),str(air)])
            if name=='using':
                require(rc!=0 and not air.exists(),'USING outside W1C must not silently lower');runs.append(dict(source=digest(source),sp=digest(sp),lowerExit=rc));continue
            require(rc==0,'lower failed');cfg=cwd/'cfg.json';dep=cwd/'dependencies.json'
            require(execute(cwd,'cfg',['java','-cp',cp,'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg',str(air),str(cfg)])==0,'CFG failed')
            cfg_contract=verify_cfg_wire(cfg.read_bytes());cfg_contracts[name]=cfg_contract
            require(cfg_contract['schemaVersion']=='2.0.0' and 'INVOKE' in cfg_contract['terminators'] and 'INVOKE_NORMAL' in cfg_contract['transitions'],'real Invoke requires CFG wire v2')
            require('PROGA' not in cfg.read_text(),'dependency names do not belong in CFG JSON')
            require(execute(cwd,'dependency',['java','-cp',cp,'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',str(air),str(dep)])==0,'dependency failed')
            d=read(dep);a=json.loads(air.read_text())['publication'];unit=a['units'][0];sequence=next(s for s in unit['sequences'] if s['terminator']['kind']=='invoke');invoke=sequence['terminator'];site=d['sites'][0]
            require(len(d['sites'])==1 and site['operation']==invoke['header']['id'] and site['sequence']==sequence['label'],'actual Invoke site identity')
            require(site['entry']==unit['entries'][0]['id'] and site['offset']==len(sequence['instructions']),'Entry and terminator offset')
            require(site['sourceValueRemainder'] and site['interpretationUnknownRemainder'] and site['effectiveUnknownRemainder'] and site['openControlRemainder'],'real source remains PARTIAL/open')
            if name=='literal':
                require(site['targetKind']=='LITERAL' and invoke['target']['kind']=='literal' and d['metrics']['possibleValuesRuns']==0,'literal requires no solver')
                require(site['candidates'][0]['referenceName']=='PROGA' and site['candidates'][0]['rawValue']=='PROGA','literal target')
            elif name=='dynamic-no-move':
                require(site['modelValueRemainder'] and not site['rawCandidates'] and not site['candidates'] and not d['edges'],'no-MOVE is open without invented target')
            else:
                require(site['targetKind']=='COMPUTED' and site['modelValueRemainder'] is False and d['metrics']['possibleValuesRuns']==1,'BEFORE closed-in-model dynamic query')
                assign=next(i for s in unit['sequences'] for i in s['instructions'] if i['kind']=='assign');candidate=site['candidates'][0];support=candidate['supports'][0]
                require(candidate['rawValue']=='PROGA   ' and candidate['referenceName']=='PROGA','X8 padding and minimal name policy')
                require(support['producer']==assign['header']['id'] and support['origin']==assign['header']['origin'],'candidate-specific Assign support')
                require(site['subject']==assign['destination']['object']==invoke['target']['name']['place']['object'],'actual ObjectId')
                # Independently resolve the producer origin DAG down to original COBOL source spans.
                origins={o['id']['localId']:o for o in d['origins']};artifacts={a['id']['localId']:a['logicalName'] for a in d['artifacts']};pending=[support['origin']['localId']];seen=set();lines=[]
                while pending:
                    key=pending.pop()
                    if key in seen:continue
                    seen.add(key);o=origins[key]
                    if o['kind']=='DERIVED':pending.extend(i['localId'] for i in o['inputs'])
                    elif o['kind']=='WRITTEN' and artifacts[o['artifact']['localId']]==source.name:lines.append(o['location'])
                require(bool(lines),'Assign support has original source span')
                require(all(o['startLine']=='7' and o['endLine']=='7' for o in lines) and "MOVE 'PROGA' TO WS-PGM" in source.read_text().splitlines()[6], 'manual original MOVE line oracle')
                (cwd/'move-provenance.json').write_text(json.dumps(dict(producer=support['producer'],origin=support['origin'],sourceSpans=lines),indent=2)+'\n')
            runs.append({p.name:digest(p) for p in (source,sp,air,cfg,dep)})
        require(runs[0]==runs[1],'two-run bytes or outcome differ: '+name);cases[name]=runs
    receipt=dict(schema='cp6-w1d-e2e@1',producers=str(producer/'producers.json'),cases=cases,cfgContracts=cfg_contracts,commands=commands,injections=False,determinism='BYTE_IDENTICAL')
    (work/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS: real W1D E2E twice; dynamic X8, literal zero-values, no-MOVE, USING boundary; exact bytes')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--producers',type=Path,required=True);a=p.parse_args();run(a.work.resolve(),a.producers)
