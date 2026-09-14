#!/usr/bin/env python3
"""Compare the three unchanged W0 CardDemo witnesses; report actual candidate deltas."""
import argparse,hashlib,json,subprocess
from pathlib import Path
from e2e_cics_program_control import classpaths,execute,ROOT
from e2e_cics_cohort import SOURCES
from dependency_wire import read,require

def snapshot(dep):
    return [dict(technology=s['technology'],command=s['command'],names=sorted(c['referenceName'] for c in s['candidates']),
                 modelValueRemainder=s['modelValueRemainder'],sourceValueRemainder=s['sourceValueRemainder'],
                 interpretationUnknownRemainder=s['interpretationUnknownRemainder']) for s in dep['sites']]

def run(a):
    cp=classpaths(a.frontend,a.lower,a.m2);a.work.mkdir(parents=True,exist_ok=False);rows=[]
    pins={n:subprocess.check_output(['git','-C',str(p),'rev-parse','HEAD'],text=True).strip() for n,p in [('frontend',a.frontend),('lower',a.lower),('cfg',ROOT)]}
    (a.work/'pins.json').write_text(json.dumps(pins,indent=2)+'\n')
    corpus=a.frontend/'corpus/carddemo'
    for name in ['COACTUPC','COCRDSLC','COUSR01C']:
        source=corpus/'cbl'/(name+'.cbl');require(hashlib.sha256(source.read_bytes()).hexdigest()==SOURCES[name],'unchanged CardDemo input')
        folder=a.work/name;folder.mkdir();before=snapshot(read(a.before/name/'dependencies.json'))
        execute(['java','-Xmx2g','-cp',cp['frontend'],'io.github.gustavo2358.cobolexplorer.ExplorerMain','--source',source,'--copybooks',str(corpus/'cpy')+','+str(corpus/'cpy-bms'),'--output',folder/'frontend','--storage-profile','ibm-enterprise-6.4-fixed-display-1047@1'],a.frontend,folder/'frontend.log')
        sp=folder/'frontend/cobol-semantic-product.json';air=folder/'air.json';dep=folder/'dependencies.json'
        execute(['java','-Xmx2g','-cp',cp['lower'],'io.github.gustavo2358.lower.adapters.cli.CobolLower',sp,air],a.lower,folder/'lower.log')
        execute(['java','-Xmx2g','-cp',cp['cfg'],'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',air,dep],ROOT,folder/'dependencies.log')
        after=snapshot(read(dep));d=json.loads(sp.read_text())
        require([(s['technology'],s['command']) for s in before]==[(s['technology'],s['command']) for s in after],'source dependency inventory preserved')
        known_before={v for s in before for v in s['names']};known_after={v for s in after for v in s['names']}
        require(known_before<=known_after,'existing candidates retained')
        require(all(s['modelValueRemainder'] or s['sourceValueRemainder'] or s['interpretationUnknownRemainder'] for s in after if s['technology']=='CICS'),'real runtime possibilities remain open')
        missing=[g['detail'] for e in d['entryInventory']['entries'] for g in e['gaps'] if g['code']=='UNRESOLVED_COPY']
        row=dict(program=name,sourceSha256=SOURCES[name],before=before,after=after,
                 delta=sorted(known_after-known_before),status='GAIN' if known_after-known_before else 'NO_REAL_GAIN',
                 layoutGaps=d['storage']['gapCodes'],missingInput=missing,initialProofs=sorted({c['proof'] for c in d['storage']['entryState']['conditions']}))
        rows.append(row);print(json.dumps(row,sort_keys=True),flush=True)
    (a.work/'summary.json').write_text(json.dumps(rows,indent=2)+'\n')

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for key in ['frontend','lower','m2','work','before']:p.add_argument('--'+key,type=Path,required=True)
    a=p.parse_args()
    for key in ['frontend','lower','m2','work','before']:setattr(a,key,getattr(a,key).resolve())
    run(a)
