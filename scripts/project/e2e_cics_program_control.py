#!/usr/bin/env python3
"""Local focal CICS vertical through existing production CLIs; reuses prepared builds.

No producer manifests or new solver. Each stage retains its raw input/output/log.
"""
import argparse, hashlib, json, os, subprocess, sys
from pathlib import Path
from cics_fixtures import fixtures
from dependency_wire import read, require
from cfg_wire_contract import verify as verify_cfg

ROOT=Path(__file__).resolve().parents[2]


def classpaths(frontend,lower,m2):
    # Maven-owned runtime classpath, generated once, not for every source.
    f=frontend/'target/runtime-classpath.txt'
    require(f.is_file(),'prepare frontend runtime-classpath.txt with dependency:build-classpath')
    air=[m2/'io/github/gustavo2358'/a/'0.1.0-SNAPSHOT'/(a+'-0.1.0-SNAPSHOT.jar') for a in ['air-java','air-json']]
    l=lower/'adapters/target/runtime-classpath.txt'
    require(l.is_file(),'prepare lower adapters runtime-classpath.txt with dependency:build-classpath')
    return {'frontend':os.pathsep.join([str(frontend/'target/classes'),f.read_text().strip()]),
            'lower':os.pathsep.join([str(lower/'core/target/classes'),str(lower/'adapters/target/classes'),l.read_text().strip()]),
            'cfg':os.pathsep.join([str(p) for p in ROOT.glob('*/target/classes')]+list(map(str,air)))}


def oracle(case,sp,air,dependency,cfg):
    verify_cfg(cfg)
    require(sp['contractVersion']=='2.13.0' and sp['storage']['version']=='1.3.0','current SP/storage contract')
    p=air['publication'];u=p['units'][0];ops={o['header']['id']['localId']:o for s in u['sequences'] for o in s['instructions']+[s['terminator']]}
    def linked(statement):
        return {o['localId'] for item in p['coverage']['items'] if item['sourceKey'].endswith('/'+statement['header']['id']) for o in item['outputs'] if o['domain']=='operation'}
    sites=dependency['sites'];cics=[s for s in sites if s['technology']=='CICS'];source=[s for s in sp['statements'] if s['variant']=='CICS_PROGRAM_CONTROL']
    require(len(cics)==len(source)==case.get('count',1),'CICS site inventory/identity')
    require(sorted({c['referenceName'] for s in cics for c in s['candidates']})==sorted(case['names']),'handwritten CICS target golden')
    for s in source:
        matched=[f for f in cics if f['operation']['localId'] in linked(s)];require(len(matched)==1,'source to AIR to dependency identity')
        site=matched[0];op=ops[site['operation']['localId']]
        require(site['command']==s['command'] and site['namespace']=='cics.program' and site['nameProfile']=='cics-ts.program@1','technology/command/profile retained')
        require(op['action']==('call' if s['command']=='LINK' else 'execute'),'standard action')
        require(all(op['signature']['signature'][k]['remainder']['kind']=='unknown' for k in ['parameters','results']),'partial signature is not zero arity')
        require(op['effectBound']['otherwise']['writes']['scope']['kind']=='all' and not op['effectBound']['otherwise']['mustOverwrite'],'conservative foreign effects')
        if s['command']=='XCTL':require(not op['outcomes']['known'],'no success return or halt invented for XCTL')
        elif s['localContinuation']['availability']=='KNOWN':require(any(o['kind']=='normal' for o in op['outcomes']['known']),'LINK normal continuation retained')
        if case.get('closed_value'):require(site['modelValueRemainder'] is False and site['valuePoint']['position']=='BEFORE','precise BEFORE observation')
        if case.get('unreadable'):require(site['interpretationUnknownRemainder'] and not site['candidates'],'unproved name area stays open')
        if case.get('gap'):require(case['gap'] in s['gapCodes'],'source diagnostic retained')
        if case.get('raw'):require([c['rawValue'] for c in site['rawCandidates']]==[case['raw']] and site['interpretationUnknownRemainder'],'no case conversion')
    calls=[s for s in sites if s['technology']=='COBOL']
    if 'calls' in case:require(len(calls)==case['calls'],'COBOL CALL inventory retained')
    if case.get('call_open'):require(any(s['targetKind']=='COMPUTED' and s['modelValueRemainder'] for s in calls),'post-interaction CALL remains unknown')
    if 'after' in case:
        source_call=next(s for s in sp['statements'] if s['variant']=='CALL' and s['target'].get('text')=='AFTER')
        site=next(s for s in calls if s['operation']['localId'] in linked(source_call))
        require((site['reachability']=='REACHABLE')==case['after'],'actual downstream reachability')
        require([c['referenceName'] for c in site['candidates']]==(['AFTER'] if case['after'] else []),'actual downstream value/candidate, not edge count')
    if case.get('perform'):require(all(not s['gapCodes'] for s in sp['statements'] if s['variant']=='PERFORM_PROCEDURE'),'supported performed range')
    if case.get('copy'):require(any(s['header']['provenance']['includeChain'] for s in source),'COPY provenance through target site')
    if case['mode']=='disabled':require(sum(s['variant']=='OBSERVED' for s in sp['statements'])>=2,'disabled extension remains opaque')
    return {'sites':len(cics),'names':sorted({c['referenceName'] for s in cics for c in s['candidates']}),'sourceOpen':sum(s['sourceValueRemainder'] for s in cics)}


def execute(command,cwd,log):
    with log.open('w') as out:
        out.write(json.dumps(list(map(str,command)))+'\n');out.flush()
        subprocess.run(list(map(str,command)),cwd=cwd,stdout=out,stderr=subprocess.STDOUT,check=True,timeout=120)


def run(args):
    require(not os.environ.get('CI'),'local E2E only')
    paths=classpaths(args.frontend,args.lower,args.m2);args.work.mkdir(parents=True,exist_ok=False)
    for label,path in [('frontend',args.frontend),('lower',args.lower),('cfg',ROOT)]:
        print(label,subprocess.check_output(['git','-C',str(path),'rev-parse','HEAD'],text=True).strip(),flush=True)
    selected={n:c for n,c in fixtures().items() if not args.cases or n in args.cases};require(selected,'nonempty cases')
    for name,case in selected.items():
        folder=args.work/name;folder.mkdir();source=folder/(name+'.cbl');source.write_text(case['source'])
        for filename,content in case['books'].items():(folder/filename).write_text(content)
        execute(['java','-Xmx1g','-cp',paths['frontend'],'io.github.gustavo2358.cobolexplorer.ExplorerMain','--source',source,'--copybooks',folder,'--output',folder/'frontend','--storage-profile','ibm-enterprise-6.4-fixed-display-1047@1','--cics-entry-mode',case['mode']],args.frontend,folder/'frontend.log')
        sp=folder/'frontend/cobol-semantic-product.json';air=folder/'air.json';cfg=folder/'cfg.json';deps=folder/'dependencies.json'
        original=hashlib.sha256(sp.read_bytes()).digest()
        execute(['java','-Xmx1g','-cp',paths['lower'],'io.github.gustavo2358.lower.adapters.cli.CobolLower',sp,air],args.lower,folder/'lower.log')
        require(hashlib.sha256(sp.read_bytes()).digest()==original,'lower never rewrites SP')
        original=hashlib.sha256(air.read_bytes()).digest()
        execute(['java','-Xmx1g','-cp',paths['cfg'],'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg',air,cfg],ROOT,folder/'cfg.log')
        execute(['java','-Xmx1g','-cp',paths['cfg'],'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',air,deps],ROOT,folder/'dependencies.log')
        require(hashlib.sha256(air.read_bytes()).digest()==original,'consumers never rewrite AIR')
        result=oracle(case,json.loads(sp.read_text()),json.loads(air.read_text()),read(deps),cfg.read_bytes())
        print(name,'PASS',json.dumps(result),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--frontend',type=Path,required=True);parser.add_argument('--lower',type=Path,required=True);parser.add_argument('--m2',type=Path,required=True);parser.add_argument('--work',type=Path,required=True);parser.add_argument('--cases',nargs='*');args=parser.parse_args()
    for key in ['frontend','lower','m2','work']:setattr(args,key,getattr(args,key).resolve())
    run(args)
