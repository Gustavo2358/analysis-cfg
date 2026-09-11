#!/usr/bin/env python3
"""Build immutable sibling snapshots in a controlled directory; never build in a sibling."""
from __future__ import annotations
import argparse, hashlib, io, json, os, shlex, subprocess, tarfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
FRONT='8722945cc4cd2052c6091533f6ee6989278aa2f8'
LOWER='18016f16b4f63149eb1bb4ca13db7e12593d8909'
def digest(path):
    with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def snapshot(path):
    def git(*args):return subprocess.check_output(['git','-C',str(path),*args],text=True).strip()
    return dict(path=str(path),head=git('rev-parse','HEAD'),branch=git('branch','--show-current'),status=git('status','--short'))
def build(work,front,lower):
    work.mkdir(parents=True,exist_ok=False);before={n:snapshot(p) for n,p in [('proleap-poc',front),('cobol-lower',lower)]}
    for name,expected in [('proleap-poc',FRONT),('cobol-lower',LOWER)]:
        # Export exact reviewed Git objects; preserve occupied sibling checkouts.
        actual=subprocess.check_output(['git','-C',before[name]['path'],'rev-parse',expected+'^{commit}'],text=True).strip()
        if actual!=expected:raise ValueError('E2E producer commit unavailable: '+name)
        data=subprocess.check_output(['git','-C',before[name]['path'],'archive','--format=tar',expected])
        (work/name).mkdir()
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:archive.extractall(work/name,filter='data')
    commands=[]
    def run(name,args):
        p=subprocess.run(args,cwd=work/name,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        log=work/(str(len(commands)+1)+'-'+name+'-build.log');log.write_bytes(p.stdout)
        commands.append(dict(argv=args,cwd=str(work/name),exitCode=p.returncode,log=str(log),logSha256=digest(log)))
        if p.returncode:raise ValueError('producer build failed: '+str(log))
    run('proleap-poc',['mvn','-B','-ntp','clean','compile','org.apache.maven.plugins:maven-jar-plugin:3.4.2:jar','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile='+str(work/'frontend-classpath.txt')])
    run('cobol-lower',['mvn','-B','-ntp','clean','install','-DskipTests','-Dexec.skip=true'])
    run('cobol-lower',['mvn','-B','-ntp','-pl','adapters','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile='+str(work/'lower-classpath.txt')])
    after={n:snapshot(Path(v['path'])) for n,v in before.items()}
    if before!=after:raise ValueError('sibling state changed')
    config=dict(schema='w5-producer-build',sources={'proleap-poc':FRONT,'cobol-lower':LOWER},mavenRepository=next((x.split('=',1)[1] for x in shlex.split(os.environ.get('MAVEN_OPTS','')) if x.startswith('-Dmaven.repo.local=')),None),before=before,after=after,commands=commands,javaVersion=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True))
    for name,jar,cp,main in [('frontend','proleap-poc/target/antlr-parse-tree-explorer-1.0.0-SNAPSHOT.jar','frontend-classpath.txt','io.github.gustavo2358.cobolexplorer.ExplorerMain'),('lower','cobol-lower/adapters/target/cobol-lower-adapters-0.1.0-SNAPSHOT.jar','lower-classpath.txt','io.github.gustavo2358.lower.adapters.cli.CobolLower')]:
        paths=[str(work/jar),*(work/cp).read_text().strip().split(os.pathsep)]
        config[name]=dict(main=main,cwd=str(work/('proleap-poc' if name=='frontend' else 'cobol-lower')),classpath=paths,jars={p:digest(p) for p in paths})
    (work/'producers.json').write_text(json.dumps(config,indent=2)+'\n');return config
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--frontend',type=Path,default=ROOT.parent/'proleap-poc');p.add_argument('--lower',type=Path,default=ROOT.parent/'cobol-lower');a=p.parse_args()
    build(a.work.resolve(),a.frontend.resolve(),a.lower.resolve());print(a.work/'producers.json')
