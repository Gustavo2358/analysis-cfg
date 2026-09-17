#!/usr/bin/env python3
"""FD-W4 source oracles: conditional handlers, shared USE, status and continuation."""
import argparse,sys
from pathlib import Path
from e2e_file_dependencies import run,ROOT
from e2e_native_files import oracle as native_oracle,EXPECTED as NATIVE,FIXTURES as NATIVE_FIXTURES
from e2e_file_memory import oracle as memory_oracle,EXPECTED as MEMORY,FIXTURES as MEMORY_FIXTURES,PROFILE
from dependency_wire import require

CONTRACT=('2.25.0','1.4.0')
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w4'
EXPECTED={
 'use-and-handler':(['read'],['USEFILE','EOFPGM','AFTER']),
 'mode-and-nested-handler':(['open','read'],['MODEPGM','EOFPGM','OTHERPGM','NOTEOF','AFTER']),
 'nested-use':(['read','read','read'],['INNEREND','USETAIL','NESTEOF','AFTER']),
 'implicit-handler':(['read'],['EOFPGM','AFTER']),
 'key-status':(['delete-record'],['USEFILE','MISS','OTHER','GOOD','ZERO','NONZERO']),
 'read-key':(['read'],['USEFILE','MISS','GOOD','AFTER']),
 'eop':(['write'],['PAGEPGM','NOTPAGE','AFTER']),
 'escape-use':(['read'],['USEFILE','BETWEEN','AFTER']),
}
def oracle(name,sp,air,result,source):
    require((sp['contractVersion'],sp['fileInventory']['version'])==CONTRACT,'SP dispatch contract')
    actions,calls=EXPECTED[name];files=result['fileDependencies']
    require(len(files['declarations'])==1 and files['declarations'][0]['name']=='INDD','terminal source file identity')
    require(sorted(s['action'] for s in files['sites'])==sorted(actions),'native/handler/USE file uses once')
    require(len(result['sites'])==len(calls),'shared CALL occurrences, never cloned/erased')
    require(sorted(c['referenceName'] for s in result['sites'] for c in s['candidates'])==sorted(calls),'manual CALL names survive conditional control')
    declarations=sp['fileInventory']['declaratives'];primary=sp['entryInventory']['entries'][0]['start']['statement']
    require(all(primary not in d['roots'] for d in declarations),'USE is not primary prefix')
    seq=air['publication']['units'][0]['sequences'];returns=[q['terminator'] for q in seq if q['terminator']['kind']=='opaque' and q['terminator']['observedKind']=='use-body-return']
    require(len(returns)==len(declarations),'shared completion body')
    for r in returns:
        require(r['envelope']['memory']['otherWrites']['kind']=='none','return cannot duplicate body memory effects')
        require(r['envelope']['control']['known'],'return retains actual USE resumes')
        bound=r['envelope']['control']['remainder']['scope']
        require(not bound['labels'] and not bound['normalExit'],'no arbitrary statement or program return from USE completion')
    if name=='read-key':require({r['event'] for r in sp['fileInventory']['operations']['uses'][0]['control']['routes']}=={'SUCCESS','INVALID_KEY','OTHER_ERROR'},'keyed READ does not invent EOF')
    if name=='eop':require(any(r['event']=='END_OF_PAGE' and r['effects']=='SUCCESS' for r in sp['fileInventory']['operations']['uses'][0]['control']['routes']),'EOP is executed WRITE, not EOF')
    require(not any(q['terminator'].get('observedKind')=='file-outcome-continuation' for q in seq),'W3 broad continuation replaced')
    for use in sp['fileInventory']['operations']['uses']:
        require(use['control']['routes'],'typed event routing')
        for route in use['control']['routes']:
            require(route['criticalExit']==(route['event']=='OTHER_ERROR'),'critical error cannot silently become success')
            if route['effects']!='SUCCESS':
                effect=next(e for e in use['effects']['outcomes'] if e['outcome']==route['effects'])
                require(all(s['role']!='INTO' for s in effect['steps']),'no INTO on failed read')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work',type=Path,required=True);parser.add_argument('--producers',type=Path,required=True);args=parser.parse_args()
    try:
        for label,fixtures,expected,check,options in (
            ('native',NATIVE_FIXTURES,NATIVE,lambda *a:native_oracle(*a,contract=CONTRACT),()),
            ('memory',MEMORY_FIXTURES,MEMORY,lambda *a:memory_oracle(*a,contract=CONTRACT),PROFILE),
            ('control',FIXTURES,EXPECTED,oracle,PROFILE)):
            run(args.work.resolve()/label,args.producers.resolve(),fixtures=fixtures,expected=expected,check=check,label='FD-W4 '+label,frontend_args=options)
    except (ValueError,RuntimeError,OSError) as error:print('FAIL: '+str(error),file=sys.stderr);sys.exit(1)
