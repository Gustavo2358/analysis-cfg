from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys
w=Path('/home/gustavo/workspace/teste-e2e');b=w/'.w5-recovery';group=sys.argv[1];p=b/group;p.mkdir(exist_ok=False);seed=Path('/home/gustavo/.m2/repository')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def ignore(src,names):return ['gustavo2358'] if Path(src).relative_to(seed).as_posix()=='io/github' else []
shutil.copytree(seed,p/'m2',ignore=ignore);assert not (p/'m2/io/github/gustavo2358').exists()
(p/'seed.json').write_text(json.dumps(dict(source=str(seed),role='third-party only; all io/github/gustavo2358 excluded',files={str(x.relative_to(p/'m2')):sha(x) for x in sorted((p/'m2').rglob('*')) if x.is_file()}),indent=2)+'\n')
air='17029898fd0ee8fabcaaae89f7260148633d4b12' if group=='main-env' else '3bafe3978f0f392e842038ad5628e85dfd91d00d';lower='2329993ce61b33fd7105759e211a1861ca6cb217' if group=='main-env' else '18016f16b4f63149eb1bb4ca13db7e12593d8909';receipts={}
for name,pin in [('air-java',air),('cobol-lower',lower),('proleap-poc','8722945cc4cd2052c6091533f6ee6989278aa2f8')]:
 subprocess.run(['git','clone','--quiet','--no-hardlinks','--no-checkout',str(w/name),str(p/name)],check=True);subprocess.run(['git','-C',str(p/name),'checkout','--quiet','--detach',pin],check=True)
 receipts[name]=dict(commit=pin,tree=subprocess.check_output(['git','-C',str(p/name),'rev-parse','HEAD^{tree}'],text=True).strip())
(p/'sources.json').write_text(json.dumps(receipts,indent=2)+'\n')
env=os.environ.copy();env.update(JAVA_HOME=str(b/'toolchains/jdk-21.0.12.1+1'),MAVEN_OPTS='-Xmx3g -Dmaven.repo.local='+str(p/'m2'));env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH'];env.pop('MAVEN_ARGS',None);commands=[]
def run(cwd,args,label):
 with (p/(label+'.log')).open('wb') as f:rc=subprocess.run(args,cwd=cwd,env=env,stdout=f,stderr=subprocess.STDOUT).returncode
 commands.append(dict(command=args,cwd=str(cwd),exit_code=rc,log_sha256=sha(p/(label+'.log'))));(p/'commands.json').write_text(json.dumps(commands,indent=2)+'\n');print(group,label,rc,flush=True)
 if rc:sys.exit(rc)
run(p/'air-java',['mvn','-B','-ntp','clean','install'],'air-install')
cfg=b/'remediation'/('main' if group=='main-env' else 'candidate')
run(cfg,['python3','-B','scripts/project/record_air_dependency.py','--checkout',str(p/'air-java'),'--built-source',str(p/'air-java'),'--maven-repo',str(p/'m2'),'--output',str(p/'air-receipt.json')],'air-receipt')
run(cfg,['python3','-B','scripts/project/prepare_w5_producers.py','--work',str(p/'producers'),'--frontend',str(p/'proleap-poc'),'--lower',str(p/'cobol-lower')],'producers')
