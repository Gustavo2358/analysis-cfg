#!/usr/bin/env python3
"""FD-W2 source oracles, plus the W1 cohort at current producer pins."""
import argparse,json,sys
from pathlib import Path
from e2e_file_dependencies import run,ROOT,EXPECTED as W1,source_spans
from dependency_wire import require
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w2'
EXPECTED={**W1,
    'native':(['F','G'],['open','read','write','rewrite','delete-record','start','close'],[]),
    'delete-only':(['F'],['delete-record'],[]),
    'close-only':(['F'],['close'],[]),
    'start-only':(['F'],['start'],[]),
    'handlers':(['F'],['delete-record'],['BAD','GOOD','AFTER']),
    'composition':(['F'],['read','write'],['EOF','BAD','OK']),
    'procedure':(['F'],['read'],['LOCAL']),
    'evaluate':(['F','G'],['read','start'],['AFTER']),
}
def oracle(name,sp,air,result,source,*,contract=('2.23.0','1.2.0')):
    require((sp['contractVersion'],sp['fileInventory']['version'])==contract,'current SP/file contract')
    names,actions,calls=EXPECTED[name];files=result['fileDependencies']
    require(sorted(d['logicalFile'] for d in files['declarations'])==sorted(names),'manual declaration oracle')
    require(sorted(s['action'] for s in files['sites'])==sorted(actions),'manual operation oracle')
    require(sorted(c['referenceName'] for s in result['sites'] for c in s['candidates'])==sorted(calls),'CALL preserved, including each handler once')
    require(len(result['sites'])==len(calls),'CALL inventory count')
    require(len(files['edges'])==len(actions),'candidate edge projection')
    declarations={d['id']['localId']:d for d in files['declarations']}
    for d in declarations.values():
        require(d['name']==('CLIENTDD' if d['logicalFile']=='F' else 'OTHERDD'),'external source name')
        require(d['owner']==air['publication']['units'][0]['id'] and len(d['objects'])==1,'owner/record identity')
        require(d['namespace']=='cobol.external-file-name' and d['sourceKind']=='ASSIGNMENT_NAME','source domain')
    for s in files['sites']:
        require(s['targetKind']=='LITERAL' and not s['unknownRemainder'],'exact literal name')
        require(s['effects']=='OPEN' and s['control']=='OPEN','W2 keeps effects/outcomes partial')
        require(len(s['bindings'])==1 and len(s['candidates'])==1,'one file connector')
        d=declarations[s['bindings'][0]['declaration']['localId']]
        require(s['candidates'][0]['referenceName']==d['name'],'resource binding')
        if name=='native':require(d['logicalFile']=='F','FROM other FD is not a file read')
        source_spans(result,s['candidates'][0]['supports'][0],source)
    if name in ('handlers','composition'):
        uses=sp['fileInventory']['operations']['uses'];require(all(u['handlers'] and u['explicitTerminator'] for u in uses),'structured handlers survive producer')
        ids=[statement for use in uses for handler in use['handlers'] for statement in handler['statements']]
        require(len(ids)==len(set(ids)),'handler bodies not duplicated')
    forbidden={'bindingMechanism','physicalResource','physicalResolution','DSNAME'}
    def scope(value):
        if isinstance(value,dict):
            require(not(set(value)&forbidden),'source-only scope')
            for child in value.values():scope(child)
        elif isinstance(value,list):
            for child in value:scope(child)
    scope(result)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--work',type=Path,required=True);parser.add_argument('--producers',type=Path,required=True);args=parser.parse_args()
    try:run(args.work.resolve(),args.producers.resolve(),fixtures=FIXTURES,expected=EXPECTED,check=oracle,label='FD-W2')
    except (ValueError,RuntimeError,OSError) as error:print('FAIL: '+str(error),file=sys.stderr);sys.exit(1)
