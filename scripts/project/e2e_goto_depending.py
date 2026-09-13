#!/usr/bin/env python3
"""Conditional transfer oracles use source occurrence order and published control only."""
import argparse,json,os,time
from pathlib import Path
from collections import defaultdict
from dependency_wire import read,require
from cfg_wire_contract import verify as verify_cfg_wire
from e2e_evaluate import execute,control_oracle
from e2e_goto import reverse_fields
from goto_depending_fixtures import fixtures

def maps(sp,air):
    p=air['publication'];ops={};labels={};links=defaultdict(list)
    for u in p['units']:
        for seq in u['sequences']:
            for o in seq['instructions']+[seq['terminator']]:
                i=o['header']['id']['localId'];ops[i]=o;labels[i]=seq['label']['localId']
    for item in p['coverage']['items']:
        source=item['sourceKey'].rsplit('/',1)[-1]
        for o in item['outputs']:
            if o['domain']=='operation' and o['localId'] in ops:links[source].append(o['localId'])
    return p,ops,labels,links

def oracle(name,case,sp,air,cfg,result):
    require(sp['contractVersion'] in ('2.6.0','2.7.0'),'explicit new SP wire version')
    gs=[s for s in sp['statements'] if s['variant']=='GO_TO_DEPENDING_ON'];require(len(gs)==1,'one typed source occurrence')
    g=gs[0];ds=g['destinations'];require(len(ds)==len(case['order']),'all destination occurrences survive')
    facts={s['header']['id']:s for s in sp['statements']}
    lines=case['source'].splitlines()
    for i,(d,name_expected) in enumerate(zip(ds,case['order'])):
        require(d['ordinal']==i,'contiguous semantic ordinals')
        loc=d['referenceOrigin']['original'];line=lines[loc['startLine']-1]
        require(line[loc['startColumn']:loc['endColumn']+1].strip()==name_expected,'source reference ordinal is semantic')
        if d['targetEntry']:
            require(d['target'] is not None and not d['gapCodes'],'known destination identity and local proof')
            require(d['entryOrigin']==facts[d['targetEntry']]['header']['provenance'],'explicit entry provenance')
            p=d['procedureOrigin']['original'];require(lines[p['startLine']-1].strip().split()[0].rstrip('.')==name_expected,'resolved procedure identity matches source declaration')
    if name=='d4':require(ds[0]['target']==ds[2]['target'] and ds[0]['targetEntry']==ds[2]['targetEntry'],'duplicate occurrence mapping preserved')
    if name in ('d5','range-partial-target','cross-unit'):
        require(any(d['targetEntry'] for d in ds) and any(d['target'] is None for d in ds),'unresolved peer never erases known target')
    if name=='section':require(ds[1]['target'] is not None and ds[1]['targetEntry'] is None,'section identity kept without invented entry')
    if name=='empty':require(ds[1]['target'] is not None and ds[1]['targetEntry'] is None,'empty paragraph identity survives')
    if name=='alter':require(all(d['targetEntry'] is None for d in ds),'ALTER policy prevents precise transfer')
    require(bool(g['gapCodes'])==case['partial'],'expected precise/partial profile '+name+str(g['gapCodes']))
    require(g['selectorInteger']==case.get('selector',True),'reuse integer proof')
    require(bool(g['normalContinuation']['statement'])==case.get('fallthrough',True),'only proven normal continuation')
    p,ops,labels,links=maps(sp,air)
    require(all(links[s['header']['id']] for s in sp['statements']),'all source occurrences retained')
    transfers=links[g['header']['id']];require(len(transfers)==1,'one selector and one physical conditional transfer')
    op=ops[transfers[0]];require(op['kind']=='opaque','existing generic control envelope')
    env=op['envelope'];require(not env['memory']['knownWrites'] and env['memory']['otherWrites']['kind']=='none','control does not write selector or values')
    require(not env['dependencies']['known'] and env['dependencies']['remainder']['kind']=='none','control is not a dependency producer')
    require(len(env['memory']['knownReads'])==(1 if g['selectorInteger'] else 0),'one integer selector memory read')
    if g['selectorInteger']:
        obj=op['knownOperands'][0]['object'];decl=next(d for u in p['units'] for d in u['objects'] if d['id']==obj)
        require(decl['typeRef']['type']['kind']=='int','AIR INT transport retained')
    expected={('jump',labels[links[d['targetEntry']][0]]) for d in ds if d['targetEntry']}
    if g['normalContinuation']['statement']:expected.add(('normal',labels[links[g['normalContinuation']['statement']][0]]))
    actual={(a['kind'],a['label']['localId']) for a in env['control']['known']}
    require(actual==expected,'exactly known destinations and fallthrough, no arbitrary textual successor')
    require((env['control']['remainder']['kind']!='none')==case['partial'],'partial keeps open control; complete finite list closes it')
    nodes={n['label']['localId']:n['id']['ordinal'] for n in cfg['nodes'] if n['kind']=='SEQUENCE'}
    outgoing=[e for e in cfg['transitions'] if e['from']['ordinal']==nodes[labels[transfers[0]]]]
    known={e['to']['ordinal'] for e in outgoing if e['kind']=='OPAQUE_JUMP'}
    require(known=={nodes[label] for _,label in expected},'CFG preserves every known alternative')
    if not case['partial']:require(len(outgoing)==len(known),'closed computed control has no false or open edge')
    if case.get('perform')=='basic-partial':require(not any(s['variant']=='PERFORM' for s in sp['statements']),'external candidate prevents BASIC isolation')
    if case.get('perform','').startswith('range-'):
        ps=[s for s in sp['statements'] if s['variant']=='PERFORM_PROCEDURE'];require(len(ps)==1,'range occurrence retained')
        require(bool(ps[0]['gapCodes'])==(case['perform']=='range-partial'),'range closure/isolation agrees with candidate control')
    sites=result['sites'];candidates=[{c['referenceName'] for c in s['candidates']} for s in sites]
    if case['values'] is not None:
        require(candidates==([set(case['values'])] if case['values'] else []),name+': independent candidate set '+str(candidates))
    for site in sites:
        for candidate in site['candidates']:
            require(candidate['supports'],'candidate needs real support')
            for support in candidate['supports']:
                producer=ops[support['producer']['localId']]
                require(producer['kind'] in ('assign','invoke'),'conditional/PERFORM/IF/EVALUATE never produces candidate values')
    control_oracle(air,cfg)
    return dict(destinations=len(ds),precise=not case['partial'],candidates=[sorted(x) for x in candidates])

def run(work,runtime,names=None,attempts=2,permutations=True):
    require(not os.environ.get('CI'),'local only');work.mkdir(parents=True,exist_ok=False)
    config=json.loads(runtime.read_text());results={}
    for name,case in fixtures().items():
        if names and name not in names:continue
        cwd=work/name;cwd.mkdir();src=cwd/(name+'.cbl');src.write_text(case['source'])
        web=cwd/'src/main/resources';web.mkdir(parents=True);(web/'web').symlink_to(Path(config['checkouts']['proleap-poc'])/'src/main/resources/web',target_is_directory=True)
        snapshots=[];started=time.monotonic()
        for i in range(attempts):
            out=cwd/chr(97+i);out.mkdir()
            execute(cwd,'frontend-'+str(i),config,'frontend',['--source',src.name,'--copybooks',cwd,'--output',out/'sp'])
            sp=out/'sp/cobol-semantic-product.json';air=out/'air.json';cfg=out/'cfg.json';dep=out/'dependency.json'
            for stage,paths in [('lower',[sp,air]),('cfg',[air,cfg]),('dependency',[air,dep])]:execute(cwd,stage+'-'+str(i),config,stage,paths)
            verify_cfg_wire(cfg.read_bytes())
            results[name]=oracle(name,case,json.loads(sp.read_text()),json.loads(air.read_text()),json.loads(cfg.read_text()),read(dep))
            snapshots.append([p.read_bytes() for p in (sp,air,cfg,dep)])
        require(all(s==snapshots[0] for s in snapshots),'SP/AIR/CFG/dependency A/B byte identity')
        if permutations:
            spdoc=reverse_fields(json.loads(snapshots[0][0]));spdoc['statements'].reverse()
            path=cwd/'permuted.sp.json';path.write_text(json.dumps(spdoc));ap=cwd/'permuted.air.json'
            execute(cwd,'sp-permutation',config,'lower',[path,ap]);require(ap.read_bytes()==snapshots[0][1],'statement inventory and fields preserve ordinal semantics')
            a=json.loads(ap.read_text());a['publication']['units'][0]['sequences'].reverse();ap.write_text(json.dumps(reverse_fields(a)))
            for stage,index in [('cfg',2),('dependency',3)]:
                dest=cwd/('permuted.'+stage+'.json');execute(cwd,stage+'-permutation',config,stage,[ap,dest]);require(dest.read_bytes()==snapshots[0][index],'AIR sequence/field order independence')
        results[name]['elapsedSeconds']=round(time.monotonic()-started,3)
        print(name+': PASS '+json.dumps(results[name]),flush=True)
    (work/'results.json').write_text(json.dumps(results,indent=2)+'\n')
    return results
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True)
    p.add_argument('--fixtures',nargs='+');p.add_argument('--attempts',type=int,default=2);p.add_argument('--no-permutations',action='store_true')
    a=p.parse_args();run(a.work.resolve(),a.runtime.resolve(),a.fixtures,a.attempts,not a.no_permutations)
