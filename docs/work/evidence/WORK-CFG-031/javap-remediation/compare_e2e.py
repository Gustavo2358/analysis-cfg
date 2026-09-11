from pathlib import Path
import json,gzip,hashlib,sys
root=Path(sys.argv[1]);run=Path(sys.argv[2]);out=Path(sys.argv[3]);historical=root/'docs/work/evidence/WORK-CFG-028/wave-5/e2e-sealed'
sha=lambda x:hashlib.sha256(x).hexdigest();checks={}
for case in ('cp4e-a','cp4e-b','cp3','generic-overwrite'):
 d=run/case;air=json.loads((d/'air.json').read_text());result=json.loads((d/'result.json').read_text());assert result['preparationStatus']=='COMPLETE'
 observations=[o for b in result['results'] for o in b['result']['observations']]
 instructions=[op for u in air['publication']['units'] for s in u['sequences'] for op in s['instructions']]
 assigns=[op for op in instructions if op['kind']=='assign']
 facts=[f for c in result['consumers'] for f in c['facts']]
 if case=='cp3':
  assert not observations and not assigns and not result['analyses'] and not facts
  assertions=dict(prepared='COMPLETE',analyses=0,queries=0,facts=0)
 else:
  expected='NEWER' if case=='generic-overwrite' else 'PROGA'
  assert len(observations)==1 and len(facts)==1
  o=observations[0];assert o['value']['enumerated']==[expected]
  assert o['value']['modelValueRemainder'] is False and o['sourceUnknownRemainder'] is True and o['effectiveUnknownRemainder'] is True
  last=assigns[-1];assert last['value']['value']['value']==expected
  assert o['candidateSupports']==[dict(candidate=expected,producers=[dict(evidence=last['header']['id'],origin=last['header']['origin'],premiseRefs=[])])]
  origins=air['publication']['origins'];origin=next(v for v in origins if v['id']==last['header']['origin'])
  assertions=dict(candidate=expected,producer=last['header']['id'],producer_kind='real Assign',origin=origin,model_remainder=False,source_remainder=True,effective_remainder=True,support='last Assign' if case=='generic-overwrite' else 'real Assign')
  if case=='generic-overwrite':assert len(assigns)==2 and assigns[0]['value']['value']['value']=='OLDER'
 assert (d/'result.json').read_bytes()==(d/'memory-result.json').read_bytes()
 hashes={}
 for name in ('air.json','result.json','frontend/cobol-semantic-product.json','semantic-product-entry-goback.cbl' if case=='cp3' else 'AIR-MOVE.cbl'):
  old=gzip.decompress((historical/case/(name+'.gz')).read_bytes());new=(d/name).read_bytes()
  hashes[name]=dict(old=sha(old),new=sha(new),byte_identical=old==new)
  assert old==new,case+' '+name+' changed; semantic classification required'
 checks[case]=dict(assertions=assertions,hashes=hashes,memory_file='BYTE_IDENTICAL')
for name in ('air.json','result.json','frontend/cobol-semantic-product.json'):
 assert (run/'cp4e-a'/name).read_bytes()==(run/'cp4e-b'/name).read_bytes()
out.write_text(json.dumps(dict(status='PASS',old_baseline='CP5 e2e-sealed',cp4e_ab='SEMANTIC_AND_BYTE_IDENTICAL',runs=checks),indent=2)+'\n');print('PASS: CP4E PROGA real Assign/origin false/true/true; CP3 empty; NEWER last Assign only; memory=file; all bytes equal CP5')
