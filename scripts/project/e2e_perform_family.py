#!/usr/bin/env python3
"""PERFORM family focal E2E and activation/values oracles; local only."""
import argparse,json,os,shutil
from pathlib import Path
from dependency_wire import read,require
from e2e_evaluate import execute,control_oracle
from e2e_goto import reverse_fields
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_w2d import source_spans
from perform_family_fixtures import FIXTURES
EXPECTED={'t1':[{'PROGA'}],'t2':[{'PROGC'}],'through':[{'PROGC'}],'t3':[{'PROGC'},{'PROGC'}],
    'if-body':[{'PROGA','PROGB'}],'evaluate-body':[{'PROGA','PROGB'}],'local-jump':[{'PROGA'}],
    'terminal':[set()],'call-body':[{'PROGC'},{'PROGA'}],
    **{'thru-'+str(n):[{'PROGC'}]*n for n in (1,2,5,40)}}
PARTIAL=('unknown-body','incoming','escape','overlap','recursive','cycle','partial-end','partial-start','reverse','empty')


def oracle(name,source,sp,air,cfg,result):
    require(sp['contractVersion']=='2.2.0','versioned range contract')
    publication=air['publication'];sequences=publication['units'][0]['sequences']
    ops={op['header']['id']['localId']:op for seq in sequences for op in seq['instructions']+[seq['terminator']]}
    links={s['header']['id']:[o['localId'] for item in publication['coverage']['items']
        if item['sourceKey'].endswith('/'+s['header']['id']) for o in item['outputs'] if o['domain']=='operation'] for s in sp['statements']}
    require(all(links.values()),'every source statement retained')
    label_by_op={op['header']['id']['localId']:seq['label']['localId'] for seq in sequences for op in seq['instructions']+[seq['terminator']]}
    labels={n['id']['ordinal']:n['label']['localId'] for n in cfg['nodes'] if n['kind']=='SEQUENCE'}
    normal_edges={l:set() for l in labels.values()}
    for edge in cfg['transitions']:
        a=edge['from']['ordinal'];b=edge['to']['ordinal']
        if a in labels and b in labels:normal_edges[labels[a]].add(labels[b])
    performs=[s for s in sp['statements'] if s['variant']=='PERFORM_PROCEDURE']
    require(performs,'typed PERFORM occurrence')
    if name in PARTIAL:require(all(p['gapCodes'] for p in performs),'adversarial activation stays partial')
    else:require(all(not p['gapCodes'] for p in performs),'focal range is precise')
    for p in performs:
        control=[ops[o] for o in links[p['header']['id']]]
        if p['gapCodes']:
            require(all(op['kind']=='opaque' and not op['envelope']['control']['known'] for op in control),'partial control has no manufactured return')
            continue
        require(len(control)==1 and control[0]['kind']=='jump','one activation entry')
        entry=control[0]['destination']['localId'];first=p['procedures'][0]['entry']
        require(entry in {label_by_op[o] for o in links[first]},'typed first paragraph entry')
        members={i for r in p['procedures'] for i in r['statements']}
        body_labels={label_by_op[o] for i in members for o in links[i]}
        resume=p['normalContinuation']['statement'];resumes={label_by_op[o] for o in links[resume]}
        todo=[entry];seen=set()
        while todo:
            label=todo.pop()
            if label in seen:continue
            seen.add(label);require(label in body_labels,'control remains in activation body until its own resume')
            for successor in normal_edges[label]:
                if successor not in resumes:todo.append(successor)
        require(not (seen & resumes),'body and resume disjoint')
    calls=sorted((s for s in sp['statements'] if s['variant']=='CALL'),key=lambda s:s['header']['programPoint'])
    byop={s['operation']['localId']:s for s in result['sites']}
    sites=[byop[o] for c in calls for o in links[c['header']['id']] if o in byop]
    require(len(sites)==len(byop),'every dependency site mapped to source CALL')
    if name in EXPECTED:
        require(len(sites)==len(EXPECTED[name]),'expected CALL count')
        for site,expected in zip(sites,EXPECTED[name]):require({c['referenceName'] for c in site['candidates']}==expected,name+': candidate oracle')
    for site in sites:
        for candidate in site['candidates']:
            require(candidate['supports'],'candidate has source-derived support')
            for support in candidate['supports']:
                producer=ops[support['producer']['localId']]
                require(producer['kind']=='assign' if support['kind']=='VALUE_PRODUCER' else producer['header']['id']==site['operation'],'PERFORM is never a value producer')
                source_spans(result,support,source)
    return [{'candidates':sorted(c['referenceName'] for c in s['candidates']),'reachability':s['reachability'],**{r:s[r] for r in
        ('modelValueRemainder','sourceValueRemainder','interpretationUnknownRemainder','effectiveUnknownRemainder','openControlRemainder')}} for s in sites]


def run(work,runtime,names=None):
    require(not os.environ.get('CI'),'local only');work.mkdir(parents=True,exist_ok=False)
    config=json.loads(runtime.read_text());results={}
    for name in names or [*EXPECTED,*PARTIAL]:
        cwd=work/name;cwd.mkdir();source=cwd/(name+'.cbl');shutil.copyfile(FIXTURES/source.name,source)
        web=cwd/'src/main/resources';web.mkdir(parents=True);(web/'web').symlink_to(Path(config['checkouts']['proleap-poc'])/'src/main/resources/web',target_is_directory=True)
        snapshots=[]
        for attempt in ('a','b'):
            out=cwd/attempt;out.mkdir()
            execute(cwd,'frontend-'+attempt,config,'frontend',['--source',source.name,'--copybooks',FIXTURES,'--output',out/'sp'])
            sp=out/'sp/cobol-semantic-product.json';air=out/'air.json';cfg=out/'cfg.json';dep=out/'dependency.json'
            for stage,paths in (('lower',[sp,air]),('cfg',[air,cfg]),('dependency',[air,dep])):execute(cwd,stage+'-'+attempt,config,stage,paths)
            verify_cfg_wire(cfg.read_bytes());control_oracle(json.loads(air.read_text()),json.loads(cfg.read_text()))
            results[name]=oracle(name,source,json.loads(sp.read_text()),json.loads(air.read_text()),json.loads(cfg.read_text()),read(dep))
            snapshots.append([p.read_bytes() for p in (sp,air,cfg,dep)])
        require(snapshots[0]==snapshots[1],name+': A/B byte determinism')
        # Every family focal also challenges physical statement, field and AIR sequence order.
        if name in EXPECTED:
            model=json.loads(snapshots[0][0]);model['statements'].reverse()
            sp=cwd/'permuted.sp.json';sp.write_text(json.dumps(reverse_fields(model)));air=cwd/'permuted-lower.air.json'
            execute(cwd,'permuted-sp',config,'lower',[sp,air]);require(air.read_bytes()==snapshots[0][1],'SP physical order independence')
            model=json.loads(snapshots[0][1]);model['publication']['units'][0]['sequences'].reverse()
            air=cwd/'permuted.air.json';air.write_text(json.dumps(reverse_fields(model)));cfg=cwd/'permuted.cfg.json';dep=cwd/'permuted.dependency.json'
            execute(cwd,'permuted-cfg',config,'cfg',[air,cfg]);execute(cwd,'permuted-dependency',config,'dependency',[air,dep])
            require(cfg.read_bytes()==snapshots[0][2] and dep.read_bytes()==snapshots[0][3],'AIR sequence order independence')
        print(name+': PASS '+str(results[name][:2]),flush=True)
    (work/'results.json').write_text(json.dumps(results,indent=2)+'\n');return results


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--fixtures',nargs='+')
    a=p.parse_args();run(a.work.resolve(),a.runtime.resolve(),a.fixtures)
