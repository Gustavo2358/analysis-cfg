from pathlib import Path
import subprocess,json,hashlib,shutil,os
w=Path('/home/gustavo/workspace/teste-e2e'); b=w/'.pre-cp6'; r=w/'.worktrees/analysis-cfg-w5';frozen=w/'.w5-recovery/sync-snapshot';base='15bd3afe1affdcb5ec49956960f884bde8c89498'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args):return subprocess.check_output(args)
assert sha(frozen/'sha256.json')=='c88ac70d20be2eb1707d95e3a4535d9b999f7663b7a743e9d16bf0f05ed9cb4d'
patch=run(['git','-C',str(r),'diff','--binary','--','scripts/harness/test_harness.py','scripts/harness/run_gate.py','scripts/project/check_w5.py']);(b/'harness.patch').write_bytes(patch)
for kind in ('main','candidate'):
 p=b/kind;run(['git','clone','--quiet','--no-hardlinks','--no-checkout',str(r),str(p)]);run(['git','-C',str(p),'checkout','--quiet','--detach',base])
 if kind=='candidate':
  for fn in ('staged.patch','tracked.patch'):
   if (frozen/fn).stat().st_size:run(['git','-C',str(p),'apply','--binary',str(frozen/fn)])
  shutil.copytree(frozen/'untracked',p,dirs_exist_ok=True)
  assert run(['git','-C',str(p),'diff','--binary'])==(frozen/'tracked.patch').read_bytes()
 before={str(x.relative_to(p)):sha(x) for x in p.rglob('*') if x.is_file() and '.git' not in x.parts and (x.suffix=='.java' or x.name=='pom.xml' or 'inventory' in x.name or x.name=='sources.lock.json')}
 run(['git','-C',str(p),'apply','--binary',str(b/'harness.patch')]);shutil.copy2(r/'scripts/project/test_w5_capture.py',p/'scripts/project/test_w5_capture.py')
 assert before=={name:sha(p/name) for name in before}
 (b/(kind+'-source.json')).write_text(json.dumps(dict(base=base,tree=run(['git','-C',str(p),'rev-parse','HEAD^{tree}']).decode().strip(),frozen_manifest_sha256=sha(frozen/'sha256.json') if kind=='candidate' else None,protected=before,harness_patch_sha256=sha(b/'harness.patch')),indent=2)+'\n')
 envdir=b/(kind+'-env');envdir.mkdir();seed=w/'.w5-recovery'/('sync-env' if kind=='candidate' else 'main-env')/'m2'
 def ignore(src,names):return ['gustavo2358'] if Path(src).relative_to(seed).as_posix()=='io/github' else []
 shutil.copytree(seed,envdir/'m2',ignore=ignore)
 assert not (envdir/'m2/io/github/gustavo2358').exists()
 pins={'air-java':'17029898fd0ee8fabcaaae89f7260148633d4b12' if kind=='main' else '3bafe3978f0f392e842038ad5628e85dfd91d00d','cobol-lower':'2329993ce61b33fd7105759e211a1861ca6cb217' if kind=='main' else '18016f16b4f63149eb1bb4ca13db7e12593d8909','proleap-poc':'8722945cc4cd2052c6091533f6ee6989278aa2f8'}
 for name,pin in pins.items():
  run(['git','clone','--quiet','--no-hardlinks','--no-checkout',str(w/name),str(envdir/name)]);run(['git','-C',str(envdir/name),'checkout','--quiet','--detach',pin])
 (envdir/'sources.json').write_text(json.dumps({n:dict(commit=c,tree=run(['git','-C',str(envdir/n),'rev-parse','HEAD^{tree}']).decode().strip()) for n,c in pins.items()},indent=2)+'\n')
 print(kind,'prepared; protected sources unchanged; first-party Maven artifacts excluded',flush=True)
