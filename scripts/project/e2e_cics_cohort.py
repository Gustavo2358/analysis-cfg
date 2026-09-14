#!/usr/bin/env python3
"""Four pinned, unchanged CardDemo sources; disclose partial input and stage failures."""
import argparse,hashlib,json,subprocess
from pathlib import Path
from e2e_cics_program_control import classpaths,execute,ROOT
from dependency_wire import read,require
SOURCES={
'COPAUS1C':'27a969cbee69426fa1056053e676041430e99399912f0e27ee1f1a454093c21e',
'COUSR01C':'aa131b1e3382dc6d101b42f1c97d4fb0c2fdd706819ed9f9a0187c82b30f3019',
'COACTUPC':'b5bb7d6ccad022e0fc91b4dd1e971f49d184adf89b56abdce14eccff35b39396',
'COCRDSLC':'d5af307fb4b1a155f03df9eea14b402d866a332360e14a1e37dbefe59b73363b'}
EXPECTED={'COPAUS1C':['LINK','XCTL'],'COUSR01C':['XCTL'],'COACTUPC':['XCTL'],'COCRDSLC':['XCTL']}

def run(a):
    cp=classpaths(a.frontend,a.lower,a.m2);corpus=a.frontend/'corpus/carddemo';a.work.mkdir(parents=True,exist_ok=False)
    print('CardDemo upstream 59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e; input bytes pinned below',flush=True)
    for name,digest in SOURCES.items():
        source=corpus/'cbl'/(name+'.cbl');require(hashlib.sha256(source.read_bytes()).hexdigest()==digest,'unchanged pinned source '+name)
        for mode in ['disabled','unknown']:
            folder=a.work/(name+'-'+mode);folder.mkdir();row={'program':name,'mode':mode,'sourceSha256':digest};stage='frontend'
            try:
                execute(['java','-Xmx2g','-cp',cp['frontend'],'io.github.gustavo2358.cobolexplorer.ExplorerMain','--source',source,'--copybooks',str(corpus/'cpy')+','+str(corpus/'cpy-bms'),'--output',folder/'frontend','--storage-profile','ibm-enterprise-6.4-fixed-display-1047@1','--cics-entry-mode',mode],a.frontend,folder/'frontend.log')
                sp=folder/'frontend/cobol-semantic-product.json';doc=json.loads(sp.read_text());cics=[s for s in doc['statements'] if s['variant']=='CICS_PROGRAM_CONTROL'];row.update(spCics=len(cics),spCalls=sum(s['variant']=='CALL' for s in doc['statements']),inventory=doc['coverage']['inventoryStatus'],inputGaps=[g['code'] for e in doc['entryInventory']['entries'] for g in e['gaps'] if g['scope']=='ANALYSIS_INPUT'])
                require(sorted(s['command'] for s in cics)==sorted(EXPECTED[name] if mode=='unknown' else []),'handwritten command inventory')
                row['targets']=[{'command':s['command'],'binding':s['target'].get('reference',{}).get('binding') if s.get('target') else None,'gaps':s['gapCodes']} for s in cics]
                stage='lower';execute(['java','-Xmx2g','-cp',cp['lower'],'io.github.gustavo2358.lower.adapters.cli.CobolLower',sp,folder/'air.json'],a.lower,folder/'lower.log')
                stage='dependencies';execute(['java','-Xmx2g','-cp',cp['cfg'],'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',folder/'air.json',folder/'dependencies.json'],ROOT,folder/'dependencies.log')
                sites=read(folder/'dependencies.json')['sites'];row.update(pipeline='PASS',dependencyCics=sum(s['technology']=='CICS' for s in sites),dependencyCalls=sum(s['technology']=='COBOL' for s in sites),cicsNames=sorted({c['referenceName'] for s in sites if s['technology']=='CICS' for c in s['candidates']}))
            except subprocess.CalledProcessError as error:row.update(pipeline='PARTIAL',blockedStage=stage,exitCode=error.returncode)
            require(hashlib.sha256(source.read_bytes()).hexdigest()==digest,'source preserved')
            print(json.dumps(row,sort_keys=True),flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--frontend',type=Path,required=True);p.add_argument('--lower',type=Path,required=True);p.add_argument('--m2',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args()
    for key in ['frontend','lower','m2','work']:setattr(a,key,getattr(a,key).resolve())
    run(a)
