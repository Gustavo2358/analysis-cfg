#!/usr/bin/env python3
"""Runs existing historical oracles against the current PERFORM wave runtime."""
import argparse,json,os,shutil
from pathlib import Path
import e2e_perform_basic as basic
import e2e_multi_call as multi
import e2e_move_data as move
import e2e_partial as partial
import e2e_w2d as conditional
from e2e_evaluate import execute,control_oracle
from dependency_wire import read,require
from cfg_wire_contract import verify as verify_cfg_wire


def run(work,runtime):
    require(not os.environ.get('CI'),'local only');work.mkdir(parents=True,exist_ok=False);config=json.loads(runtime.read_text())
    cases=[('basic',n,basic.FIXTURES/(n+'.cbl')) for n in ('literal','copy','overwrite')]
    cases += [('multi',n,multi.FIXTURES/('fixture-'+str(n)+'.cbl')) for n in multi.SITES]
    cases += [('move',n,move.FIXTURES/(n+'.cbl')) for n in move.CASES]
    cases += [('partial',p.stem,p) for p in sorted(partial.FIXTURES.glob('*.cbl'))]
    cases += [('if',n,conditional.FIXTURES/(n+'.cbl')) for n in ('closed','open')]
    results=[]
    for family,name,fixture in cases:
        cwd=work/(family+'-'+str(name));cwd.mkdir();source=cwd/fixture.name;shutil.copyfile(fixture,source)
        web=cwd/'src/main/resources';web.mkdir(parents=True);(web/'web').symlink_to(Path(config['checkouts']['proleap-poc'])/'src/main/resources/web',target_is_directory=True)
        outputs=[]
        for attempt in ('a','b'):
            out=cwd/attempt;out.mkdir()
            execute(cwd,'frontend-'+attempt,config,'frontend',['--source',source.name,'--copybooks',fixture.parent,'--output',out/'sp'])
            sp=out/'sp/cobol-semantic-product.json';air=out/'air.json';cfg=out/'cfg.json';dep=out/'dependency.json'
            for stage,paths in (('lower',[sp,air]),('cfg',[air,cfg]),('dependency',[air,dep])):execute(cwd,stage+'-'+attempt,config,stage,paths)
            s=json.loads(sp.read_text());a=json.loads(air.read_text());d=read(dep);verify_cfg_wire(cfg.read_bytes());control_oracle(a,json.loads(cfg.read_text()))
            if family=='basic':basic.dependency_oracle(d,basic.air_oracle(a,basic.source_oracle(s,name),name,source),source)
            elif family=='multi':
                multi.air_oracle(a,s,multi.source_oracle(s,name));multi.dependency_oracle(d,a,s,name,source)
            elif family=='move':
                data,moves=move.source_oracle(s,name,s['contractVersion']);move.dependency_oracle(d,move.air_oracle(a,data,moves,name),source,name)
            elif family=='partial':partial.oracle(name,s,a,d)
            else:
                conditional.air_oracle(a,name=='open');conditional.dependency_oracle(d,a,source,name=='open')
            outputs.append([p.read_bytes() for p in (sp,air,cfg,dep)])
        require(outputs[0]==outputs[1],family+str(name)+': A/B deterministic')
        results.append(family+'-'+str(name));print(results[-1]+': PASS',flush=True)
    (work/'results.json').write_text(json.dumps({'passed':results},indent=2)+'\n')


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True)
    a=p.parse_args();run(a.work.resolve(),a.runtime.resolve())
