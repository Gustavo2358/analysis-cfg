#!/usr/bin/env python3
"""FD-W5 manual source oracles: multiparty I/O, local SD, phases and CALL evidence."""
import argparse,sys
from pathlib import Path
from e2e_file_dependencies import run,ROOT,source_spans
from e2e_native_files import oracle as native_oracle,EXPECTED as NATIVE,FIXTURES as NATIVE_FIXTURES
from e2e_file_memory import oracle as memory_oracle,EXPECTED as MEMORY,FIXTURES as MEMORY_FIXTURES,PROFILE
from e2e_file_control import oracle as control_oracle,EXPECTED as CONTROL,FIXTURES as CONTROL_FIXTURES
from dependency_wire import require

CONTRACT=('2.26.0','1.5.0')
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w5'
# External occurrences, local roles, CALL names and source CALL count are authorial.
EXPECTED={
 'merge-output-procedure':(['INA','INB'],['work','return'],['EMPTY','RECORD'],2),
 'sort-multi-output':(['INA','INB','OUTC'],['work'],[],0),
 'return-buffer':(['INA'],['work','return'],[],1),
 'perform-procedure':([],['work','release','return'],['AFTER','FILLPGM'],2),
 'sort-multiparty':(['INA','INB','OUTC'],['work'],[],0),
 'merge-multiparty':(['INA','INB','OUTC'],['work'],[],0),
 'same-file':(['INA','INA'],['work'],[],0),
 'release-from':(['OUTC'],['work','release'],[],0),
 'return-into':(['INA'],['work','return'],['EMPTY','RECORD'],2),
 'procedure-ranges':([],['work','release','return'],['AFTER','INPGM','INTAIL','EOFPGM','OUTTAIL'],5),
 'section-procedure':(['OUTC'],['work','release'],['INPUT'],1),
 'missing-procedure':(['OUTC'],['work'],[],0),
 'implicit-use':(['INA','INB','OUTC'],['work'],['INERR','OUTERR'],2),
 'table-key':([],[],[],0), 'table-no-key':([],[],[],0),
 'release-disjoint':(['OUTC'],['work','release'],['SAFE0001'],1),
}
def oracle(name,sp,air,result,source,*,contract=CONTRACT):
    require((sp['contractVersion'],sp['fileInventory']['version'])==contract,'current phase contract')
    require(result['version'] in ('2.1.0','2.2.0','2.3.0'),'explicit local-site wire version')
    names,roles,calls,count=EXPECTED[name];files=result['fileDependencies']
    local=[s for s in files['sites'] if s['targetKind']=='LOCAL'];external=[s for s in files['sites'] if s['targetKind']!='LOCAL']
    require(len(files['sites'])==len(names)+len(roles),'all implicit/local uses, no Cartesian product')
    require(sorted(c['referenceName'] for s in external for c in s['candidates'])==sorted(names),'all USING/GIVING external participants')
    require(sorted(b['role'] for s in local for b in s['bindings'])==sorted(roles),'SD work/release/return associations')
    require(len(files['edges'])==len(names),'SD creates no external edge')
    require(len(result['sites'])==count,'local procedures preserve each source CALL once')
    require(sorted(c['referenceName'] for s in result['sites'] for c in s['candidates'])==sorted(calls),'CALL candidate oracle')
    require(len(files['declarations'])==(0 if name.startswith('table-') else 4),'declarations never invent operational uses')
    for s in local:
        require(s['namespace'] is None and s['valuePoint'] is None and not s['unknownRemainder'] and not s['candidates'],'local SD is not unknown external name')
        require(s['reachability']=='REACHABLE','shared procedure SD uses remain reachable')
    for d in files['declarations']:
        require(d['owner']==air['publication']['units'][0]['id'],'declaration owner')
        if d['logicalFile']=='S':require(d['targetKind']=='LOCAL' and d['name'] is None,'sort work has no external target')
    for s in external:
        require(s['action'] in ('sort','merge') and len(s['bindings'])==1,'one implicit participant operation')
        require(s['bindings'][0]['role'] in ('input','output') and not s['unknownRemainder'],'external participant role/exact name')
        source_spans(result,s['candidates'][0]['supports'][0],source)
    if name=='same-file':require({s['bindings'][0]['role'] for s in external}=={'input','output'},'same FD preserves both roles')
    if name=='missing-procedure':require(sp['fileInventory']['sortPlans'][0]['availability']=='PARTIAL','unresolved procedure remains explicit')
    if name=='release-disjoint':
        site=result['sites'][0];require(site['targetKind']=='COMPUTED' and site['candidates'][0]['supports'],'RELEASE keeps disjoint FROM source evidence')
    if name=='return-buffer':
        site=result['sites'][0];require(site['targetKind']=='COMPUTED' and site['modelValueRemainder'],'successful RETURN INTO cannot retain stale singleton')
    if name.startswith('table-'):require(not sp['fileInventory']['operations']['uses'],'SORT table is not FILE')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work',type=Path,required=True);parser.add_argument('--producers',type=Path,required=True);args=parser.parse_args()
    try:
        for label,fixtures,expected,check,options in (
            ('sort',FIXTURES,EXPECTED,oracle,PROFILE),
            ('native',NATIVE_FIXTURES,NATIVE,lambda *a:native_oracle(*a,contract=CONTRACT),()),
            ('memory',MEMORY_FIXTURES,MEMORY,lambda *a:memory_oracle(*a,contract=CONTRACT),PROFILE),
            ('control',CONTROL_FIXTURES,CONTROL,lambda *a:control_oracle(*a,contract=CONTRACT),PROFILE)):
            run(args.work.resolve()/label,args.producers.resolve(),fixtures=fixtures,expected=expected,check=check,label='FD-W5 '+label,frontend_args=options)
    except (ValueError,RuntimeError,OSError) as error:print('FAIL: '+str(error),file=sys.stderr);sys.exit(1)
