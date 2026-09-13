#!/usr/bin/env python3
"""M3 real file vertical and independent byte/provenance oracles; local only."""
import argparse,json
from pathlib import Path
from storage_composition_fixtures import fixtures
from storage_group_fixtures import ibm_octets
from e2e_storage_groups import run,local
from e2e_evaluate import control_oracle
from cfg_wire_contract import verify as verify_cfg
from dependency_wire import require
from regional_result_wire import validate

def oracle(name,case,sp,air,cfg,dependency,product):
 validate(product);verify_cfg(json.dumps(cfg).encode());control_oracle(air,cfg)
 require(sp['contractVersion']=='2.8.0' and sp['storage']['version']=='1.1.0','exact source contract')
 require(product['profile']=='regional-text-images@2' and product['pathWitness']=='NOT_PROVIDED','no invented path witness')
 p=air['publication'];u=p['units'][0];coverage=p['coverage']['items']
 def outputs(source,domain):return {local(o) for item in coverage if item['sourceKey'].endswith('/'+source) for o in item['outputs'] if o['domain']==domain}
 def one(values):require(len(values)==1,'one correlated identity '+str(values));return next(iter(values))
 objects={o['canonicalName']:one(outputs('data/'+o['id'],'object')) for o in sp['dataDeclarations']}
 declarations={local(o['id']):o for o in u['objects']}
 def base(name):return local(declarations[objects[name]]['storage']['region'])
 op_tags={};tag_ops={}
 for tag,line in case['tags'].items():
  source=[s for s in sp['statements'] if s['header']['provenance']['original']['startLine']==line]
  require(len(source)==1,'one statement correlated with handwritten source line '+tag)
  ids=outputs(source[0]['header']['id'],'operation');require(ids,'source statement cannot disappear '+tag);tag_ops[tag]=ids
  for ident in ids:require(ident not in op_tags,'distinct tagged statement');op_tags[ident]=tag
 operations={local(o['header']['id']):o for seq in u['sequences'] for o in seq['instructions']+[seq['terminator']]}
 call=one(tag_ops['call']);sites=[s for s in dependency['sites'] if local(s['operation'])==call];require(len(sites)==1,'source CALL retained')
 site=sites[0];require(site['sourceValueRemainder'] and site['openControlRemainder'],'source/external control gaps retained')
 if case['fallback']:
  require(any(op['kind']=='opaque' for op in operations.values()),'unsupported source proof retains Opaque')
  require(site['modelValueRemainder'] is True and site['effectiveUnknownRemainder'],'fallback cannot report closed exact value')
  return dict(status='CONSERVATIVE_FALLBACK',calls=1,modelOpen=True,sourceOpen=True,queries=len(product['observations']))
 actual_control=[s['variant'] for s in sp['statements'] if s['variant'] in ('IF','EVALUATE','GO_TO','PERFORM','PERFORM_PROCEDURE')]
 require(sorted(actual_control)==sorted(case['control']),'all admitted control facts present')
 for ident,operation in operations.items():
  if operation['kind']=='opaque':
   rows=[o for o in product['observations'] if local(o['point']['operationId'])==ident]
   require(rows and all(o['values']['fact']['reachability']=='UNREACHABLE_IN_MODEL' for o in rows),'only proved unreachable Opaque is allowed in admitted fixtures')
 results=[]
 for expected in case['observations']:
  obj=objects[expected['object']];rows=[o for o in product['observations'] if local(o['point']['operationId'])==call and local(o['subject']['objectId'])==obj]
  require(len(rows)==1,'one named public observation');row=rows[0];require(row['values']['status']==row['rd']['status']=='VALUE','RD/value query admitted');v=row['values']['fact'];rd=row['rd']['fact']
  require(v['reachability']==rd['reachability']=='REACHABLE','reached query')
  require(v['candidates']==sorted(expected['candidates']) and v['modelValueRemainder']==expected['model'],name+': independent value golden '+str(v['candidates']))
  require(v['sourceUnknownRemainder'] and rd['sourceUnknownRemainder'],'source precision remains independently open')
  require(rd['unknownRemainder']==expected['model'],'RD remainder golden')
  supports={c['candidate']:{op_tags[local(x['evidence'])] for x in c['producers']} for c in v['candidateSupports']}
  require(supports=={text:{f['producer'] for f in fs} for text,fs in expected['candidates'].items()},name+': independent candidate supports')
  actual=set();wanted=set()
  for alternative in v['alternatives']:
   if alternative['candidate'] is None:continue
   bytes=[None]*8
   for fragment in alternative['fragments']:
    require(fragment['kind']=='KNOWN_BYTES','known candidate has known bytes');producer=fragment['producer'];start=int(fragment['location']['range']['start']);original=producer['contributedRange'];offset=int(original['range']['start'])
    captures=tuple(sorted({op_tags[local(c['definition']['operationId'])] for c in fragment['captures']}))
    for i,byte in enumerate(fragment['bytes']):bytes[start+i]=(byte,op_tags[local(producer['definition']['operationId'])],local(original['storageId']),offset+i,captures)
   actual.add((alternative['candidate'],tuple(bytes)))
  for text,fragments in expected['candidates'].items():
   bytes=[None]*8;octets=ibm_octets(text)
   for f in fragments:
    for i in range(f['start'],f['end']):bytes[i]=(octets[i],f['producer'],base(f['source']),f['offset']+i-f['start'],tuple(sorted(f['captures'])))
   wanted.add((text,tuple(bytes)))
  require(actual==wanted,name+': independent original/captured byte coordinates '+str(actual))
  actual_rd=[set() for _ in range(8)];wanted_rd=[set() for _ in range(8)]
  for d in rd['definitions']:
   definition=d['definition'];operation=definition['operationId']
   if operation is None:
    require(definition['kind']=='ENTRY_UNKNOWN' and definition['unknown'],'explicit unspecified entry event');tag='ENTRY_UNKNOWN'
   else:tag=op_tags[local(operation)]
   for loc in d['contributedRanges']:
    require(local(loc['storageId'])==base(expected['object']),'RD physical destination identity')
    for i in range(int(loc['range']['start']),int(loc['range']['end'])):actual_rd[i].add(tag)
  for tag,start,end in expected['rd']:
   for i in range(start,end):wanted_rd[i].add(tag)
  require(actual_rd==wanted_rd,name+': independent RD surviving bytes '+str(actual_rd))
  if local(site['subject'])==obj:
   require([c['rawValue'] for c in site['rawCandidates']]==v['candidates'],'dependency target is the same BEFORE value')
   require(site['modelValueRemainder']==v['modelValueRemainder'],'dependency remainder matches')
  results.append(row)
 if name=='perform-twice':
  require(len(tag_ops['body'])==2,'two explicit specialized body operations, no shared flat resume')
  captures=[]
  for row in results:captures.append({local(c['definition']['operationId']) for a in row['values']['fact']['alternatives'] for f in a['fragments'] for c in f['captures'] if local(c['definition']['operationId']) in tag_ops['body']})
  require(all(len(c)==1 for c in captures) and captures[0].isdisjoint(captures[1]),'two source activations have separate captures')
 return dict(status='SUPPORTED_WITH_SOURCE_REMAINDER',calls=1,candidates=len(site['rawCandidates']),modelOpen=site['modelValueRemainder'],sourceOpen=True,queries=len(product['observations']),goldenQueries=len(results))
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--cases',nargs='*');p.add_argument('--attempts',type=int,default=2);p.add_argument('--no-permutations',action='store_true');a=p.parse_args()
 run(a.work.resolve(),a.runtime.resolve(),a.cases,a.attempts,not a.no_permutations,cases=fixtures(),inspect=oracle,status='W5_COMPOSITION_VERTICAL',probe_name='RegionalE2eProbe')
