"""Read-only external capture of the unmodified W5 function's actual value."""
import difflib,hashlib,json,os,sys
from pathlib import Path
out=Path(os.environ['REGISTRY_GATE_OUTPUT'])
def write(name,obj):(out/name).write_text(json.dumps(obj,indent=2,sort_keys=True)+'\n')
def trace(frame,event,arg):
 if Path(frame.f_code.co_filename).name=='check_w5.py' and frame.f_code.co_name=='architecture' and event in ('return','exception'):
  actual=frame.f_locals.get('actual',{});root=frame.f_locals.get('root')
  if set(actual)=={'analysis-dataflow','analysis-adapters','analysis-launcher'}:
   path=root/'docs/evals/resource-limit-w5-inventory.json';raw=path.read_bytes();expected=json.loads(raw);actual=json.loads(json.dumps(actual));(out/'expected.json').write_bytes(raw);write('actual.json',actual);rows=[]
   for m in expected.keys()|actual.keys():
    for field in expected.get(m,{}).keys()|actual.get(m,{}).keys():
     e=expected.get(m,{}).get(field);a=actual.get(m,{}).get(field)
     if e!=a:
      row=dict(module=m,field=field,expected=e,actual=a)
      if isinstance(e,list) and isinstance(a,list):row.update(only_expected=sorted(set(e)-set(a)),only_actual=sorted(set(a)-set(e)))
      if isinstance(e,dict) and isinstance(a,dict):row.update(changed_keys={k:dict(expected=e.get(k),actual=a.get(k)) for k in e.keys()|a.keys() if e.get(k)!=a.get(k)})
      rows.append(row)
   write('inventory-diff.json',dict(equal=not rows,differences=rows,canonical_sha256=hashlib.sha256(json.dumps(actual,sort_keys=True,separators=(',',':')).encode()).hexdigest()))
 return trace
sys.settrace(trace)
