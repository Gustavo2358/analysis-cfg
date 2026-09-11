from pathlib import Path
import sys,os,tempfile,shutil,json,hashlib
root=Path(sys.argv[1]);out=Path(sys.argv[2]);sys.path.insert(0,str(root/'scripts/project'));import check_w5 as w5
expected=root/'docs/evals/resource-limit-w5-inventory.json';before=expected.read_bytes();records=[];original_path=os.environ['PATH']
with tempfile.TemporaryDirectory(prefix='w5-tool-adversary-') as d:
 for name in ('javap','jdeps'):
  real=shutil.which(name);p=Path(d)/name
  p.write_text('#!'+sys.executable+'\nimport os,sys,subprocess\nsys.stderr.write("arbitrary diagnostic: random path /tmp/tool-123 and pid 456\\n")\np=subprocess.run(['+repr(real)+']+sys.argv[1:],stdout=subprocess.PIPE)\nsys.stdout.buffer.write(p.stdout)\nif os.environ.get("W5_DESCRIPTOR_MUTANT") and '+repr(name)+'=="javap":sys.stdout.write("structural descriptor change\\n")\nsys.exit(p.returncode)\n');p.chmod(0o755)
 os.environ['PATH']=d+os.pathsep+original_path
 captured={};real_capture=w5.capture
 def observe(r,args):
  value=real_capture(r,args)
  if args[0]=='javap':captured[args[2].split(os.pathsep)[0]+'|'+args[-1]]=value
  return value
 w5.capture=observe
 w5.architecture(root)
 exp=json.loads(before);actual={m:{} for m in w5.MODULES}
 for m in w5.MODULES:
  for cls,descriptor in exp[m]['javap_descriptors'].items():
   value=captured[str(root/m/'target/classes')+'|'+cls[:-6].replace('/','.')];assert value==descriptor;actual[m][cls]=value
 canonical=lambda x:hashlib.sha256(json.dumps(x,sort_keys=True,separators=(',',':')).encode()).hexdigest()
 records.append(dict(case='arbitrary stderr on every real javap and jdeps process',architecture='PASS',javap_descriptors_equal=True,expected_inventory_hash=canonical(exp),actual_inventory_hash=canonical({m:dict(v,javap_descriptors=actual[m]) for m,v in exp.items()})))
 os.environ['W5_DESCRIPTOR_MUTANT']='1'
 try:w5.architecture(root)
 except w5.Failure as e:
  assert 'W5 compiled inventory drift' in str(e);records.append(dict(case='stdout descriptor changed',architecture='FAIL as required',diagnostic=str(e)))
 else:raise AssertionError('real descriptor mutation escaped inventory')
 assert expected.read_bytes()==before
 os.environ.pop('W5_DESCRIPTOR_MUTANT');os.environ['PATH']=original_path
out.write_text(json.dumps(dict(cases=records,expected_inventory_unchanged=True),indent=2)+'\n');print('PASS: injected diagnostics do not alter inventory; real stdout change rejected')
