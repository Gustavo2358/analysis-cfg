from pathlib import Path
import hashlib,json,subprocess
w=Path('/home/gustavo/workspace/teste-e2e');b=w/'.w5-recovery/remediation';f=w/'.w5-recovery/sync-snapshot';original=w/'analysis-cfg'
def git(root,*args):return subprocess.check_output(['git','-C',str(root),*args])
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(f/'sha256.json')=='c88ac70d20be2eb1707d95e3a4535d9b999f7663b7a743e9d16bf0f05ed9cb4d'
for name,args in [('tracked.patch',['diff','--binary']),('staged.patch',['diff','--cached','--binary']),('head.txt',['rev-parse','HEAD']),('branch.txt',['branch','--show-current']),('status.txt',['status','--short']),('untracked.txt',['ls-files','--others','--exclude-standard'])]:
 assert git(original,*args)==(f/name).read_bytes(),name
for p in (f/'untracked').rglob('*'):
 if p.is_file():assert p.read_bytes()==(original/p.relative_to(f/'untracked')).read_bytes(),str(p)
for kind in ['main','main-full','candidate']:
 r=b/kind;source=json.loads((b/(kind+'-source.json')).read_text())
 assert all(sha(r/p)==digest for p,digest in source['protected_before'].items()),kind
 if kind=='candidate':
  assert hashlib.sha256(git(r,'diff','--binary')).hexdigest()==source['diff_sha256']
  for p in (f/'untracked').rglob('*'):
   if p.is_file():assert p.read_bytes()==(r/p.relative_to(f/'untracked')).read_bytes(),str(p)
out=dict(original_blocked_tree_unchanged=True,frozen_snapshot_sha256=sha(f/'sha256.json'),candidate_tracked_diff_unchanged_after_fix=True,candidate_untracked_frozen_files_unchanged=True,all_protected_bytes_unchanged=True)
(b/'preservation.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps(out))
