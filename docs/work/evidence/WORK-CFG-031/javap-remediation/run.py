from pathlib import Path
import subprocess,sys,json,os,time,hashlib,xml.etree.ElementTree as ET
w=Path('/home/gustavo/workspace/teste-e2e');b=w/'.pre-cp6';kind=sys.argv[1];phase=sys.argv[2];root=b/kind;ep=b/(kind+'-env');out=b/'results'/kind/phase;out.mkdir(parents=True,exist_ok=False)
env=os.environ.copy();env.update(JAVA_HOME=str(w/'.w5-recovery/toolchains/jdk-21.0.12.1+1'),MAVEN_OPTS='-Xmx3g -Dmaven.repo.local='+str(ep/'m2'),W5_PRODUCERS=str(ep/'producers/producers.json'));env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH'];env.pop('MAVEN_ARGS',None);env.pop('PYTHONPATH',None)
records=[]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(label,args,cwd=root):
 start=time.time();log=out/(label+'.log')
 with log.open('wb') as f:rc=subprocess.run(args,cwd=cwd,env=env,stdout=f,stderr=subprocess.STDOUT).returncode
 record=dict(label=label,command=args,cwd=str(cwd),exit_code=rc,elapsed_seconds=time.time()-start,log_sha256=sha(log));records.append(record);(out/'results.json').write_text(json.dumps(records,indent=2)+'\n');print(kind,phase,label,rc,flush=True)
 if rc:print(log.read_text()[-4500:],flush=True);sys.exit(rc)
 if label=='maven':
  counts={k:0 for k in ['tests','failures','errors','skipped']}
  for p in root.glob('*/target/surefire-reports/TEST-*.xml'):
   a=ET.parse(p).getroot().attrib
   for k in counts:counts[k]+=int(a.get(k,0))
  (out/'maven-summary.json').write_text(json.dumps(counts,indent=2)+'\n');print(counts,flush=True)
(out/'environment.json').write_text(json.dumps({k:env[k] for k in ('JAVA_HOME','MAVEN_OPTS','PATH','W5_PRODUCERS')},indent=2)+'\n')
if phase=='build':
 run('java',['java','-version']);run('air-install',['mvn','-B','-ntp','clean','install'],ep/'air-java')
 run('air-receipt',['python3','-B','scripts/project/record_air_dependency.py','--checkout',str(ep/'air-java'),'--built-source',str(ep/'air-java'),'--maven-repo',str(ep/'m2'),'--output',str(ep/'air-receipt.json')])
 run('producers',['python3','-B','scripts/project/prepare_w5_producers.py','--work',str(ep/'producers'),'--frontend',str(ep/'proleap-poc'),'--lower',str(ep/'cobol-lower')])
elif phase=='gates':
 run('maven',['mvn','-B','-ntp','clean','verify'])
 run('focal',['python3','-B',str(w/'.worktrees/analysis-cfg-w5/docs/work/evidence/WORK-CFG-031/HARNESS-REGISTRY-DRIFT-001/focal-probe.py'),'--root',str(root),'--out',str(out/'focal-observation')])
 for gate in ['fast','docs','architecture']:
  run(gate,['bash','scripts/harness/check-'+gate+'.sh'])
 run('w5-isolated',['python3','-B','scripts/project/check_w5.py','architecture'])
 run('w5-adversarial',['python3','-B',str(b/'inventory_adversary.py'),str(root),str(out/'inventory-adversarial.json')])
 for gate in ['semantic','integration','performance','full']:run(gate,['bash','scripts/harness/check-'+gate+'.sh'])
 run('scope-manifest',['python3','-B','scripts/project/check_scope.py','--update-manifest'])
 run('scope',['python3','-B','scripts/project/check_scope.py'])
 run('diff-check',['git','diff','--check'])
 e2e=Path((root/'.harness-results/w5-s10-latest.txt').read_text().strip())
 run('e2e-comparison',['python3','-B',str(b/'compare_e2e.py'),str(root),str(e2e),str(out/'e2e-comparison.json')])
 source=json.loads((b/(kind+'-source.json')).read_text());assert source['protected']=={p:sha(root/p) for p in source['protected']};(out/'source-preservation.json').write_text(json.dumps(dict(protected_sources_unchanged=True),indent=2)+'\n')
print(kind,phase,'COMPLETE',flush=True)
