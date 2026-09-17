#!/usr/bin/env python3
"""FD-W6 source oracles: auxiliary declarations, checkpoint triggers, aliasing and CALL."""
import argparse,sys
from pathlib import Path
from e2e_file_dependencies import run,ROOT,source_spans
from e2e_file_memory import oracle as memory_oracle,EXPECTED as MEMORY,FIXTURES as MEMORY_FIXTURES,PROFILE
from e2e_file_sort import oracle as sort_oracle,EXPECTED as SORT,FIXTURES as SORT_FIXTURES
from dependency_wire import require
CONTRACT=('2.27.0','1.6.0')
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w6'
# Source oracle: declaration count, action inventory, source CALL count, required CALL candidates.
EXPECTED={
 'checkpoint-sort':(4,['sort','sort','resource-use','checkpoint'],1,['SAFE0001']),
 'checkpoint-declaration':(2,[],0,[]),
 'checkpoint-records':(2,['open','read','close','checkpoint'],1,['SAFE0001']),
 'documentary':(2,[],1,['SAFE0001']),
 'same-indexed':(2,['read'],2,['SAFE0001']),
 'same-sequential':(2,['read'],2,['SAFE0001','STALE001']),
 'same-vsam-sequential':(2,['read'],2,['SAFE0001']),
 'line-sequential':(1,['open','read','close'],1,['SAFE0001']),
 'linage-counter':(1,['open','write'],2,['SAFE0001']),
 'password-read':(1,['open'],1,['SAFE0001']),
 'linage-parameters':(1,['open','write'],1,['SAFE0001']),
}
def oracle(name,sp,air,result,source,*,contract=CONTRACT):
    require((sp['contractVersion'],sp['fileInventory']['version'])==contract,'auxiliary contract')
    require(sp['fileInventory']['auxiliary']['availability']=='KNOWN','closed auxiliary inventory')
    n,actions,calls,required=EXPECTED[name];f=result['fileDependencies']
    require(len(f['declarations'])==n,'declaration source count')
    require(sorted(s['action'] for s in f['sites'])==sorted(actions),'triggered operation inventory')
    require(len(result['sites'])==calls,'source CALL occurrences preserved')
    candidates={c['referenceName']:s for s in result['sites'] for c in s['candidates']}
    require(all(c in candidates for c in required),'disjoint CALL support preserved')
    if 'SAFE0001' in candidates:
        safe=next(c for c in candidates['SAFE0001']['candidates'] if c['referenceName']=='SAFE0001')
        require(bool(safe['supports']),'disjoint source support preserved despite open external CALL control')
    if name in ('same-indexed','same-vsam-sequential'):
        dirty=result['sites'][0];require(dirty['modelValueRemainder'],'possible shared READ cannot leave stale exact singleton')
    if name.startswith('same-'):
        clause=next(c for c in sp['fileInventory']['auxiliary']['clauses'] if c['kind']=='SAME_AREA')
        require(clause['effect']==('DOCUMENTARY' if name=='same-sequential' else 'RECORD_ALIAS'),'source method controls SAME meaning')
        nodes={n['data']:n['id'] for n in sp['storage']['nodes'] if n.get('data')}
        bases={v['node']:v['base'] for v in sp['storage']['views']}
        records=[d['records'][0] for d in sp['fileInventory']['declarations']]
        require((bases[nodes[records[0]]]==bases[nodes[records[1]]])==(name!='same-sequential'),'QSAM separation versus VSAM shared record allocation')
    if name=='linage-counter':
        dirty=result['sites'][0];require(dirty['modelValueRemainder'],'counter value unproved')
        require(not any(c['referenceName']=='STALE001' for c in dirty['candidates']),'MOVE counter overwrites old receiver evidence')
    checkpoint=[s for s in f['sites'] if s['action']=='checkpoint']
    for site in checkpoint:
        require(site['candidates'][0]['referenceName']=='CHKPT' and not site['unknownRemainder'],'checkpoint name source proof')
        require(site['bindings'][0]['role']=='checkpoint','checkpoint binding role')
        source_spans(result,site['candidates'][0]['supports'][0],source)
    if name=='checkpoint-declaration':require(not checkpoint,'declaration alone does not execute')
    for site in checkpoint:
        operation=next(q['terminator'] for q in air['publication']['units'][0]['sequences'] if q['terminator']['header']['id']==site['operation'])
        require(operation['effectBound']['otherwise']['writes']['kind']=='none','checkpoint never kills COBOL memory')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--producers',type=Path,required=True);p.add_argument('--runtime',type=Path);p.add_argument('--sp-version',default=CONTRACT[0]);a=p.parse_args();contract=(a.sp_version,CONTRACT[1])
    try:
        for label,fixtures,expected,check in (
            ('auxiliary',FIXTURES,EXPECTED,lambda *x:oracle(*x,contract=contract)),
            ('memory',MEMORY_FIXTURES,MEMORY,lambda *x:memory_oracle(*x,contract=contract)),
            ('sort',SORT_FIXTURES,SORT,lambda *x:sort_oracle(*x,contract=contract))):
            run(a.work.resolve()/label,a.producers.resolve(),runtime_config=a.runtime,fixtures=fixtures,expected=expected,check=check,label='FD-W6 '+label,frontend_args=PROFILE)
    except (ValueError,RuntimeError,OSError) as e:print('FAIL: '+str(e),file=sys.stderr);sys.exit(1)
