#!/usr/bin/env python3
"""FD-W9: manual source scope oracles through the existing immutable CLI harness."""
import argparse,sys
from pathlib import Path
from e2e_file_dependencies import ROOT,run
from e2e_file_memory import PROFILE
from dependency_wire import require
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w9'
# unit count, declaration count, FILE occurrence count, CALL occurrence count, proven CALL candidates
EXPECTED={
 'nested':(3,3,3,4,{'PARENTPG','SHADOWPG','CHILDLNK'}),
 'external':(2,2,2,2,{'ONECALL','TWOCALL'}),
 'copy':(2,2,2,2,{'COPYA','COPYB'}),
 'qualified':(1,2,2,1,{'KEEP'}),
 'missing-copy':(2,2,2,2,{'GOODCALL','GAPCALL'}),
 'global-read':(2,1,1,1,{'KEEP'}),
 'cics-nested':(2,0,1,2,{'PARENTPG','CHILDPG'}),
 'cics-nested-closed':(2,0,1,1,{'PARENTPG'}),
}
def oracle(name,sp,air,result,source):
    require(sp['schema']=='cobol-semantic-compilation' and sp['contractVersion']=='1.0.0','versioned compilation envelope')
    units,decls,files,calls,names=EXPECTED[name];au=air['publication']['units'];fd=result['fileDependencies']
    require(len(sp['unitInventory'])==len(sp['units'])==len(au)==units,'all published units composed')
    require(len(fd['declarations'])==decls and len(fd['sites'])==files and len(result['sites'])==calls,'independent source inventories')
    actual={c['referenceName'] for s in result['sites'] for c in s['candidates']};require(actual==names,'CALL names per source oracle')
    if name=='nested':
        parent,child,shadow=[u['id'] for u in au]
        require(au[1]['containingUnit']==au[2]['containingUnit']==parent,'canonical parentage')
        shared=next(d for d in fd['declarations'] if d['name']=='PARENTDD');local=next(d for d in fd['declarations'] if d['name']=='CHILDDD')
        require(shared['owner']==parent and local['owner']==shadow,'GLOBAL versus shadow declaration owners')
        require(all(s['owner']==child for s in fd['sites'] if any(b['declaration']==shared['id'] for b in s['bindings'])),'uses owned by child')
        require(not any(s['owner']==parent for s in fd['sites']),'no transitive parent FILE')
        for s in result['sites']:
            for c in s['candidates']:
                require(s['caller']=={'PARENTPG':parent,'SHADOWPG':shadow,'CHILDLNK':child}[c['referenceName']],'contained CALL owner')
    if name in ('external','copy'):
        require(len({d['owner']['localId'] for d in fd['declarations']})==2,'same external name never fuses declarations')
        require(all(not u['fileCaptures'] and not u['dataCaptures'] for u in sp['units']),'siblings do not capture by name or local AST id')
        if name=='copy':require(all(any(o['includeChain'] for o in u['product']['fileInventory']['declarations'][0]['origins']) for u in sp['units']),'COPY origins retained')
    if name=='qualified':
        require(sorted(s['targetKind'] for s in fd['sites'])==['COMPUTED','LITERAL'],'ambiguous reference remains local unknown')
        require({e['candidate']['referenceName'] for e in fd['edges']}=={'FIRSTDD'},'qualification picks its canonical file only')
    if name=='missing-copy':
        require(sp['inventoryStatus']=='INPUT_MISSING','unknown source inventory remains open')
        require(any(u['code']=='COMPILATION_UNIT_INVENTORY_INPUT_MISSING' for u in air['publication']['uncertainties']),'AIR compilation gap')
    if name=='global-read':
        require(fd['declarations'][0]['owner']==au[0]['id'] and fd['sites'][0]['owner']==au[1]['id'],'implicit record owner versus use owner')
        require(all(c['supports'] for s in result['sites'] for c in s['candidates']),'READ GLOBAL preserves disjoint MOVE/CALL support')
        require(any(o['storage']['kind']=='alias' for o in au[1]['objects']),'captured record aliases original object')
    if name.startswith('cics-nested'):
        site=fd['sites'][0];parent,child=[u['id'] for u in au]
        require(au[1]['containingUnit']==parent and site['owner']==child,'computed CICS FILE belongs only to contained program')
        require(site['targetKind']=='COMPUTED' and {c['referenceName'] for c in site['candidates']}=={'ACCOUNTS'},'contained FILE uses general possible-values')
        require({c['referenceName'] for c in site['context']['candidates']}=={'R001'},'contained SYSID source context')
        require(('FILE_MODEL_VALUE_REMAINDER' in site['analysisReasons'])==(name=='cics-nested') and 'FILE_SOURCE_VALUE_REMAINDER' in site['analysisReasons'],'local CALL model remainder versus closed child model; source remains partial')
        if name=='cics-nested':
            call=next(q['terminator'] for q in au[1]['sequences'] if q['terminator']['kind']=='invoke' and q['terminator']['action']=='call')
            require(call['outcomes']['remainder']['scope']['kind']=='all' and call['effectBound']['otherwise']['writes']['scope']['kind']=='all','independent open CALL law: revisiting FILE after may-write cannot prove a singleton')
        require(site['valuePoint']['position']=='BEFORE' and all(c['supports'] for c in site['candidates']),'command-point proof retained')
        require(fd['metrics']['possibleValuesPreparations']==1,'shared FILE/SYSID solver in contained unit')
        require(all(s['caller']==({'PARENTPG':parent,'CHILDPG':child}[s['candidates'][0]['referenceName']]) for s in result['sites']),'CALL owners remain independent')
    require(all(u['product']['entryInventory']['scope']=='PRIMARY_ONLY' for u in sp['units']),'entry limitation remains explicit per unit')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--producers',type=Path,required=True);p.add_argument('--runtime',type=Path);p.add_argument('--case',choices=EXPECTED,action='append');a=p.parse_args()
    try:run(a.work.resolve(),a.producers.resolve(),runtime_config=a.runtime,fixtures=FIXTURES,expected={k:EXPECTED[k] for k in (a.case or EXPECTED)},check=oracle,label='FD-W9',frontend_args=PROFILE,sp_filename='cobol-semantic-compilation.json',copybooks=FIXTURES/'cpy')
    except (ValueError,RuntimeError,OSError) as e:print('FAIL: '+str(e),file=sys.stderr);sys.exit(1)
