from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,time
w=Path('/home/gustavo/workspace/teste-e2e');b=w/'.w5-recovery/remediation';kind=sys.argv[1];root=b/kind;envroot=w/'.w5-recovery'/('sync-env' if kind=='candidate' else 'main-env');env=os.environ.copy();env.update(JAVA_HOME=str(w/'.w5-recovery/toolchains/jdk-21.0.12.1+1'),MAVEN_OPTS='-Xmx3g -Dmaven.repo.local='+str(envroot/'m2'),W5_PRODUCERS=str(envroot/'producers/producers.json'),PYTHONPATH=str(b/'observer'));env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH'];env.pop('MAVEN_ARGS',None);parent=b/'results'/kind;parent.mkdir(parents=True,exist_ok=False);results=[]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,j):p.write_text(json.dumps(j,indent=2)+'\n')
toolchain={k:env.get(k) for k in ['PATH','JAVA_HOME','MAVEN_OPTS','MAVEN_ARGS','W5_PRODUCERS']};toolchain['maven.repo.local']=str(envroot/'m2');toolchain['tools']={}
for n in ['java','javac','javap','jdeps','mvn']:
 p=shutil.which(n,path=env['PATH']);x=subprocess.run([p,'-version'],env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);toolchain['tools'][n]=dict(which=p,realpath=str(Path(p).resolve()),output=x.stdout,exit_code=x.returncode)
write(parent/'toolchain.json',toolchain)
def run(label,argv):
 out=parent/label;out.mkdir();env['REGISTRY_GATE_OUTPUT']=str(out);start=time.time()
 with (out/'run.log').open('wb') as f:rc=subprocess.run(argv,cwd=root,env=env,stdout=f,stderr=subprocess.STDOUT).returncode
 record=dict(label=label,command=argv,cwd=str(root),exit_code=rc,elapsed_seconds=time.time()-start,log_sha256=sha(out/'run.log'));results.append(record);write(out/'result.json',record);write(parent/'results.json',results);print(kind,label,rc,flush=True)
 if rc:
  write(parent/'STOP.json',dict(first_failing=record,status='STOP_FOR_HUMAN_REVIEW',no_remediation_attempted=True));sys.exit(rc)
if kind=='main':
 run('maven',['mvn','-B','-ntp','clean','verify'])
 for gate in ['fast','docs','architecture','semantic','integration','performance']:run(gate,['bash','scripts/harness/check-'+gate+'.sh'])
 run('scope-manifest',['python3','-B','scripts/project/check_scope.py','--update-manifest']);run('scope',['python3','-B','scripts/project/check_scope.py']);run('diff-check',['git','diff','--check'])
elif kind=='main-full':run('full',['bash','scripts/harness/check-full.sh'])
else:
 run('focal',['python3','-B',str(b/'focal-probe.py'),'--root',str(root),'--out',str(parent/'focal-observation')])
 for gate in ['fast','docs','architecture']:run(gate,['bash','scripts/harness/check-'+gate+'.sh'])
 run('w5-isolated',['python3','-B','scripts/project/check_w5.py','architecture'])
 run('full',['bash','scripts/harness/check-full.sh'])
source=json.loads((b/(kind+'-source.json')).read_text());after={p:sha(root/p) for p in source['protected_before']};assert source['protected_before']==after;write(parent/'source-after.json',dict(protected_after=after,protected_bytes_unchanged=True));print(kind,'COMPLETE',flush=True)
