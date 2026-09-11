from pathlib import Path
import hashlib,json,os,subprocess
w=Path('/home/gustavo/workspace/teste-e2e');b=w/'.w5-recovery/remediation';root=w/'.worktrees/analysis-cfg-w5';out=b/'jdk-focal';out.mkdir(exist_ok=False);results=[]
for label,home in [('21',w/'.w5-recovery/toolchains/jdk-21.0.12.1+1'),('25',Path('/home/gustavo/.sdkman/candidates/java/25.0.4-tem'))]:
 env=os.environ.copy();env['JAVA_HOME']=str(home);env['PATH']=str(home/'bin')+':'+env['PATH'];version=subprocess.run(['java','-version'],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);assert version.returncode==0
 cmd=['python3','-B','scripts/harness/test_harness.py','HarnessGuardTests.test_07_registry_drift'];p=subprocess.run(cmd,cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);log=out/(label+'.log');log.write_bytes(p.stdout);results.append(dict(jdk=label,java_home=str(home),java_version=version.stdout,command=cmd,exit_code=p.returncode,log_sha256=hashlib.sha256(p.stdout).hexdigest()));assert p.returncode==0
(out/'receipt.json').write_text(json.dumps(results,indent=2)+'\n');print('focal PASS under JDK 21 and 25 environments')
