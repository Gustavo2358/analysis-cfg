from pathlib import Path
import hashlib,json,shutil,subprocess
w=Path('/home/gustavo/workspace/teste-e2e');b=w/'.w5-recovery/remediation';r=w/'.worktrees/analysis-cfg-w5';frozen=w/'.w5-recovery/sync-snapshot';base='15bd3afe1affdcb5ec49956960f884bde8c89498'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def git(root,*args):return subprocess.check_output(['git','-C',str(root),*args])
assert sha(frozen/'sha256.json')=='c88ac70d20be2eb1707d95e3a4535d9b999f7663b7a743e9d16bf0f05ed9cb4d'
patch=git(r,'diff','--binary','--','scripts/harness/test_harness.py');(b/'harness-only.patch').write_bytes(patch)
for name in ['main','main-full','candidate']:
 p=b/name;subprocess.run(['git','clone','--quiet','--no-hardlinks','--no-checkout',str(r),str(p)],check=True);subprocess.run(['git','-C',str(p),'checkout','--quiet','--detach',base],check=True)
 if name=='candidate':
  for fn in ['staged.patch','tracked.patch']:
   if (frozen/fn).stat().st_size:subprocess.run(['git','-C',str(p),'apply','--binary',str(frozen/fn)],check=True)
  shutil.copytree(frozen/'untracked',p,dirs_exist_ok=True);assert git(p,'diff','--binary')==(frozen/'tracked.patch').read_bytes()
  assert all((p/x.relative_to(frozen/'untracked')).read_bytes()==x.read_bytes() for x in (frozen/'untracked').rglob('*') if x.is_file())
 before={str(x.relative_to(p)):sha(x) for x in p.rglob('*') if x.is_file() and '.git' not in x.parts and (x.suffix=='.java' or x.name=='pom.xml' or 'inventory' in x.name or x.name=='sources.lock.json')}
 subprocess.run(['git','-C',str(p),'apply','--binary',str(b/'harness-only.patch')],check=True)
 after={s:sha(p/s) for s in before};assert before==after
 text=(p/'scripts/harness/test_harness.py').read_text();focal=text[text.index('    def test_07_registry_drift('):text.index('    def test_08_')];source=(r/'scripts/harness/test_harness.py').read_text();assert focal==source[source.index('    def test_07_registry_drift('):source.index('    def test_08_')]
 (b/(name+'-source.json')).write_text(json.dumps(dict(base=base,frozen_snapshot_sha256=sha(frozen/'sha256.json') if name=='candidate' else None,patch_sha256=sha(b/'harness-only.patch'),test_source_sha256=sha(p/'scripts/harness/test_harness.py'),focal_function_sha256=hashlib.sha256(focal.encode()).hexdigest(),protected_before=before,protected_after=after,only_variable='registry test fix',diff_sha256=hashlib.sha256(git(p,'diff','--binary')).hexdigest()),indent=2)+'\n')
print('PASS: exact frozen candidate and main, same focal patch, zero protected changes')
