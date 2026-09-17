#!/usr/bin/env python3
"""FD-W3 manual source oracles: storage, source evidence and conditional effects.
No expected value is derived from the analyzed AIR or from a previous run.
"""
import argparse,sys
from pathlib import Path
from e2e_file_dependencies import run,ROOT
from e2e_native_files import oracle as native_oracle,EXPECTED as NATIVE,FIXTURES as NATIVE_FIXTURES
from dependency_wire import require

FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w3'
EXPECTED={
    'read-disjoint':(['F'],['read'],['SAFE']),
    'read-buffer':(['F'],['read'],['OLD']),
    'read-into-status':(['F'],['read'],['OLDINTO','SAFE']),
    'rewrite-from':(['F','G'],['rewrite'],['SOURCE','SAFE']),
    'read-alias':(['F'],['read'],['OLD']),
    'unresolved-from':(['F'],['write'],['SAFE']),
}
CONTRACT=('2.24.0','1.3.0')
PROFILE=('--storage-profile','ibm-enterprise-6.4-fixed-display-1047@1')

def oracle(name,sp,air,result,source,*,contract=CONTRACT):
    require((sp['contractVersion'],sp['fileInventory']['version'])==contract,'SP memory contract')
    require(sp['storage']['version']=='1.8.0','general source allocation contract')
    names,actions,expected=EXPECTED[name];files=result['fileDependencies']
    require(sorted(d['logicalFile'] for d in files['declarations'])==sorted(names),'source file ownership')
    require(sorted(s['action'] for s in files['sites'])==sorted(actions),'one I/O use; FROM is not another file read')
    require(sorted(c['referenceName'] for s in result['sites'] for c in s['candidates'])==sorted(expected),'manual CALL evidence oracle')
    require(len(result['sites'])==len(expected),'every written CALL exactly once')
    by_name={c['referenceName']:s for s in result['sites'] for c in s['candidates']}
    if 'SAFE' in expected:
        candidate=next(c for c in by_name['SAFE']['candidates'] if c['referenceName']=='SAFE')
        require(bool(candidate['supports']),'disjoint declarative source support survives I/O')
    if name in ('read-buffer','read-alias'):
        require(by_name['OLD']['modelValueRemainder'],'READ may invalidate old buffer bytes; no stale exact singleton')
    use=sp['fileInventory']['operations']['uses'][0];e=use['effects'];cases={c['outcome']:c['steps'] for c in e['outcomes']}
    require(set(cases)=={'SUCCESS','END','INVALID_KEY','OTHER_ERROR'},'conditional outcomes are not default success')
    for steps in cases.values():
        require(all(s['kind']!='MUST_UNKNOWN' for s in steps if s['role']=='RECORD'),'no verb-based buffer MUST')
    for outcome in ('END','INVALID_KEY','OTHER_ERROR'):
        require(all(s['role']!='INTO' for s in cases[outcome]),'unsuccessful READ never does implied INTO')
    if name=='read-into-status':
        require([s['role'] for s in cases['SUCCESS']]==['RECORD','FILE_STATUS','INTO'],'READ/status precedes INTO addressing')
        require(cases['SUCCESS'][-1]['kind']=='MUST_UNKNOWN','exact disjoint receiver overwrites on success')
        require(by_name['OLDINTO']['modelValueRemainder'],'success/error join retains old INTO support plus remainder')
    if name=='rewrite-from':
        require(len(e['before'])==1 and e['before'][0]['kind']=='COPY_BYTES','FROM copy executes before I/O')
        require(not any(s['role']=='RECORD' for s in cases['INVALID_KEY']),'REWRITE invalid key does not undo FROM')
        require(files['sites'][0]['candidates'][0]['referenceName']=='CLIENTDD','FROM owner G is not the I/O resource')
    if name=='read-alias':
        views=sp['storage']['views'];base_by_node={v['node']:v['base'] for v in views}
        records=sp['fileInventory']['declarations'][0]['records']
        nodes=[n['id'] for n in sp['storage']['nodes'] if n.get('data') in records]
        require(len(nodes)==2 and len({base_by_node[n] for n in nodes})==1,'two FD descriptions share one allocation')
    if name=='unresolved-from':
        require(e['unknownReadBound'] and not e['unknownWriteBound'],'unknown FROM reads do not become a global write')
        require(any(g['scope']=='NOMINAL_BINDING' for g in sp['gaps']),'missing source binding is published as a localized gap')
    invokes=[s['terminator'] for s in air['publication']['units'][0]['sequences'] if s['terminator']['kind']=='invoke' and s['terminator']['action'] in actions]
    require(all(i['effectBound']['otherwise']['writes']['kind']=='none' for i in invokes),'writes are represented in ordered outcome blocks, not a global invoke kill')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work',type=Path,required=True);parser.add_argument('--producers',type=Path,required=True);args=parser.parse_args()
    try:
        run(args.work.resolve()/'native',args.producers.resolve(),fixtures=NATIVE_FIXTURES,expected=NATIVE,
            check=lambda *a:native_oracle(*a,contract=CONTRACT),label='FD-W3 native regression')
        run(args.work.resolve()/'memory',args.producers.resolve(),fixtures=FIXTURES,expected=EXPECTED,check=oracle,label='FD-W3 memory',frontend_args=PROFILE)
    except (ValueError,RuntimeError,OSError) as error:print('FAIL: '+str(error),file=sys.stderr);sys.exit(1)
