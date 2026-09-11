from pathlib import Path
import hashlib,json,subprocess
w=Path('/home/gustavo/workspace/teste-e2e');b=w/'.w5-recovery/remediation';r=w/'.worktrees/analysis-cfg-w5';p=r/'scripts/harness/test_harness.py';raw=p.read_bytes();logs={}
def run(label,argv):
 with (b/(label+'.log')).open('wb') as f:rc=subprocess.run(argv,cwd=r,stdout=f,stderr=subprocess.STDOUT).returncode
 logs[label]=dict(command=argv,exit_code=rc,log_sha256=hashlib.sha256((b/(label+'.log')).read_bytes()).hexdigest());return rc
for state in ['active','blocked']:
 assert run('green-'+state,['python3','-B',str(b/'focal-probe.py'),'--root',str(r),'--out',str(b/('green-'+state)),'--matched',state])==0
cmd=['python3','-B','scripts/harness/test_harness.py','HarnessGuardTests.test_07_registry_drift'];assert run('ritual-green',cmd)==0
try:
 s=raw.decode();old="mutated = 'active' if work_status == 'blocked' else 'blocked'";assert s.count(old)==1;p.write_text(s.replace(old,"mutated = 'blocked'"))
 assert run('ritual-mutant-import',['python3','-B','-c',"import sys; sys.path.insert(0,'scripts/harness'); import test_harness"])==0
 assert run('ritual-red',cmd)==1;assert 'registry drift adversarial was not constructed' in (b/'ritual-red.log').read_text()
finally:p.write_bytes(raw)
assert p.read_bytes()==raw;assert run('ritual-second-green',cmd)==0
(b/'ritual.json').write_text(json.dumps(dict(mutant='constant-blocked-status',valid=1,killed=1,baseline_sha256=hashlib.sha256(raw).hexdigest(),restored_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),byte_exact_restore=True,logs=logs),indent=2)+'\n');print('PASS controlled starts, GREEN/import/RED/restore/second GREEN')
