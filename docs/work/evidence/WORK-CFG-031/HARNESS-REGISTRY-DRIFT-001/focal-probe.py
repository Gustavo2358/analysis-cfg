"""Observe the actual adversarial test and real validator; do not replace their verdicts."""
from pathlib import Path
import argparse,hashlib,json,sys,traceback
from unittest.mock import patch
p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--matched',choices=['active','blocked']);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=False)
sys.path.insert(0,str(a.root/'scripts/harness'));import test_harness as h
case=h.HarnessGuardTests('test_07_registry_drift');events=[]
def state(ident):
 work=case.root/('docs/work/active/'+ident+'/work-item.json');w=h.load_json(work);entry=next(x for x in h.load_json(case.root/'docs/work/registry.json')['active'] if x['id']==ident)
 return dict(id=ident,work_status=w['status'],registry_status=entry['status'],work_sha256=hashlib.sha256(work.read_bytes()).hexdigest(),mismatch=w['status']!=entry['status'])
setup=False;code=0
try:
 case.setUp();ident=case.ensure_active_work()
 if a.matched:
  case.edit_json('docs/work/active/'+ident+'/work-item.json',lambda w:w.update(status=a.matched))
  case.edit_json('docs/work/registry.json',lambda r:next(x for x in r['active'] if x['id']==ident).update(status=a.matched))
 before=state(ident);print('BEFORE',json.dumps(before),flush=True);setup=True;real=h.validate
 def capture(root):
  s=state(ident);s['diagnostics']=real(root);events.append(s);print('VALIDATE',json.dumps(s),flush=True);return s['diagnostics']
 with patch.object(h,'validate',side_effect=capture):case.test_07_registry_drift()
except Exception:code=1;traceback.print_exc()
finally:
 after=state(ident) if setup else None;print('AFTER',json.dumps(after),flush=True)
 receipt=dict(command=sys.argv,exit_code=code,setup_succeeded=setup,before=before if setup else None,after=after,validator_calls=events,test_source_sha256=hashlib.sha256((a.root/'scripts/harness/test_harness.py').read_bytes()).hexdigest())
 (a.out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');case.tearDown()
sys.exit(code)
