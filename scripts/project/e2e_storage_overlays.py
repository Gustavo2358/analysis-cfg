#!/usr/bin/env python3
"""W4 REDEFINES source vertical; analysis remains the generic AIR storage engine."""
import argparse
from pathlib import Path
from e2e_storage_groups import run,oracle as group_oracle,ordered_nodes,local
from storage_overlay_fixtures import fixtures
from dependency_wire import require

def oracle(name,case,sp,air,cfg,dependency,probe):
    require(sp['contractVersion']=='2.8.0' and sp['storage']['version']=='1.1.0','W4 versioned relation contract')
    storage=sp['storage'];relations=storage['relations'];nodes=ordered_nodes(storage);ordinals={n['id']:i for i,n in enumerate(nodes)}
    require(bool(relations),'physical relations are never lost')
    if case.get('unproved'):
        sites=dependency['sites'];require(len(sites)==1,'one conservative call');site=sites[0]
        require(site['candidates']==[] and site['effectiveUnknownRemainder'] and site['sourceValueRemainder'],'unknown target remains open')
        require(not probe['queries'],'no precise target fabricated')
        if case['missingRelation']:
            require(all(r['status']=='UNPROVEN' and r['target'] is None and r['gapCodes'] for r in relations),'unproved relation has no invented target')
            require(not air['publication']['storage'],'unproved allocation has no independent Region/Cell')
        else:
            require(all(r['status']=='PROVEN' for r in relations),'known relation distinct from unknown footprint')
            require(any(b['extent']['value'] is None for b in storage['bases']),'unknown max footprint is not zero')
            require(any(s['kind']=='region' and s['extent']['kind']=='unknown' for s in air['publication']['storage']),'unknown region retained')
        return dict(calls=1,candidates=0,unknownTargets=1,sourceOpen=1,modelOpen=0,qualifiedPhysicalQueries=0,relations=len(relations))
    actual=sorted((ordinals[r['owner']],ordinals[r['target']]) for r in relations)
    require(actual==sorted(case['relations']),'independent physical relation golden')
    require(all(r['status']=='PROVEN' and r['gapCodes']==[] for r in relations),'every relation proved')
    publication=air['publication'];coverage=publication['coverage']['items']
    for relation in relations:
        items=[item for item in coverage if item['sourceKey'].endswith('/storage-relation/'+relation['id'])]
        require(len(items)==1,'individual relation provenance/coverage retained')
        require(items[0]['origin'] in [o['id'] for o in publication['origins']],'relation source origin retained')
    result=group_oracle(name,case,sp,air,cfg,dependency,probe);result['relations']=len(relations)
    if name=='local-scalar-control':
        declarations={d['canonicalName']:d for d in sp['dataDeclarations']}
        require(declarations['WS-PGM']['scalarText'] is not None and declarations['COUNTER']['scalarInteger'] is not None,'disjoint roots recover scalar and numeric source proofs')
        require(declarations['RAW']['scalarText'] is None and declarations['ALT-AREA']['scalarText'] is None,'related roots stay shared')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--cases',nargs='*');p.add_argument('--attempts',type=int,default=2);p.add_argument('--no-permutations',action='store_true');a=p.parse_args()
    run(a.work.resolve(),a.runtime.resolve(),a.cases,a.attempts,not a.no_permutations,cases=fixtures(),inspect=oracle,status='W4_FOCAL_VERTICAL')
