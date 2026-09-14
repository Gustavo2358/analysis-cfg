#!/usr/bin/env python3
"""Selected DVI production-CLI vertical; reuses the existing prepared-build E2E helpers."""
import argparse,hashlib,json,subprocess
from pathlib import Path
from e2e_cics_program_control import classpaths,execute,ROOT
from dependency_wire import read,require
from cfg_wire_contract import verify as verify_cfg
from dvi_fixtures import fixtures

def oracle(case,sp,air,dep,cfg):
    verify_cfg(cfg)
    require(sp['contractVersion']=='2.15.0' and sp['storage']['version']=='1.4.0','DVI versioned proof contract')
    entry=sp['storage']['entryState'];c=entry['conditions'][0]
    require(entry['mode']==case['mode'].upper() and c['proof']==case['proof'],'global policy and per-condition proof')
    if c['proof']=='NONE': require(c['kind']=='UNKNOWN' and c['gapCodes'] and not c['bytes'],'fail-open reason retained')
    p=air['publication'];u=p['units'][0];origins={o['id']['localId']:o for o in p['origins']}
    sites=[s for s in dep['sites'] if not case.get('computed_only') or s['targetKind']=='COMPUTED']
    require(len(sites)==1,'one selected dependency site')
    site=sites[0];names=sorted(c['referenceName'] for c in site['candidates'])
    require(names==sorted(case['names']),'handwritten candidates: '+str(names)+' != '+str(case['names']))
    opened=any(site[k] for k in ['modelValueRemainder','sourceValueRemainder','interpretationUnknownRemainder'])
    if case.get('open') or not names:require(opened,'runtime/unknown alternatives must stay open')
    if case.get('command'):require(site['technology']=='CICS' and site['command']==case['command'],'shared CICS dependency consumer')
    if names:
        require(site['valuePoint']['position']=='BEFORE','target observed before invocation effects')
        require(all(c['supports'] for c in site['candidates']),'candidates have actual value supports')
    # Initial literal originates from the declared VALUE with a versioned source-proof rule.
    rule='storage@1.4/entry-mode='+entry['mode']+'; proof='+c['proof']+'; source-proved invocation condition'
    require(any(o.get('rule')==rule for o in origins.values()),'AIR retains source proof and VALUE origin')
    if case.get('killed'):
        require(all(x['rawValue']!='PROGA   ' for x in site['rawCandidates']),'overwrites/backedges do not reseed VALUE')
    if case.get('copy'):
        require(any(o['kind']=='copy_bytes' for seq in u['sequences'] for o in seq['instructions']),'existing data MOVE machinery')
    if not any(s['variant']=='MOVE' for s in sp['statements']):
        require(not any(o['kind'] in ['assign','copy_bytes'] for seq in u['sequences'] for o in seq['instructions']),'no synthetic MOVE')
    return dict(names=names,unknownRemainder=opened,modelValueRemainder=site['modelValueRemainder'],sourceValueRemainder=site['sourceValueRemainder'],interpretationUnknownRemainder=site['interpretationUnknownRemainder'],proof=c['proof'])

def run(a):
    cp=classpaths(a.frontend,a.lower,a.m2);a.work.mkdir(parents=True,exist_ok=False)
    pins={n:subprocess.check_output(['git','-C',str(p),'rev-parse','HEAD'],text=True).strip() for n,p in [('frontend',a.frontend),('lower',a.lower),('cfg',ROOT)]}
    (a.work/'pins.json').write_text(json.dumps(pins,indent=2)+'\n');rows=[]
    for name,case in fixtures().items():
        if a.cases and name not in a.cases:continue
        folder=a.work/name;folder.mkdir();source=folder/(name+'.cbl');source.write_text(case['source'])
        execute(['java','-Xmx1g','-cp',cp['frontend'],'io.github.gustavo2358.cobolexplorer.ExplorerMain','--source',source,'--copybooks',folder,'--output',folder/'frontend','--storage-profile','ibm-enterprise-6.4-fixed-display-1047@1','--entry-storage-state',case['mode']],a.frontend,folder/'frontend.log')
        sp=folder/'frontend/cobol-semantic-product.json';air=folder/'air.json';cfg=folder/'cfg.json';dep=folder/'dependencies.json'
        sp_hash=hashlib.sha256(sp.read_bytes()).hexdigest()
        execute(['java','-Xmx1g','-cp',cp['lower'],'io.github.gustavo2358.lower.adapters.cli.CobolLower',sp,air],a.lower,folder/'lower.log')
        air_hash=hashlib.sha256(air.read_bytes()).hexdigest()
        execute(['java','-Xmx1g','-cp',cp['cfg'],'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg',air,cfg],ROOT,folder/'cfg.log')
        execute(['java','-Xmx1g','-cp',cp['cfg'],'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',air,dep],ROOT,folder/'dependencies.log')
        require(sp_hash==hashlib.sha256(sp.read_bytes()).hexdigest() and air_hash==hashlib.sha256(air.read_bytes()).hexdigest(),'immutable stage inputs')
        result=oracle(case,json.loads(sp.read_text()),json.loads(air.read_text()),read(dep),cfg.read_bytes())
        rows.append(dict(case=name,sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),**result));print(name,'PASS',json.dumps(result),flush=True)
    (a.work/'summary.json').write_text(json.dumps(rows,indent=2)+'\n')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--frontend',type=Path,required=True);p.add_argument('--lower',type=Path,required=True);p.add_argument('--m2',type=Path,required=True);p.add_argument('--work',type=Path,required=True);p.add_argument('--cases',nargs='*');a=p.parse_args()
    for key in ['frontend','lower','m2','work']:setattr(a,key,getattr(a,key).resolve())
    run(a)
