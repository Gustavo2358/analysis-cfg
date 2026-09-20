#!/usr/bin/env python3
"""Build immutable sibling snapshots in a controlled directory; never build in a sibling."""
from __future__ import annotations
import argparse, hashlib, io, json, os, re, shlex, subprocess, sys, tarfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/harness'))
from lean import require_local
from upstream_fast import compile_dependency

def producer_pins(lock):
    result={}
    for name,key in [('air-java','air_java'),('proleap-poc','proleap_poc'),('cobol-lower','cobol_lower')]:
        source=lock[key];pin=source.get('commit',source.get('main_commit'))
        if source['repository']!='Gustavo2358/'+name or not isinstance(pin,str) or not re.fullmatch('[0-9a-f]{40}',pin):
            raise ValueError('exact active producer pin required: '+name)
        result[name]=pin
    return result
def digest(path):
    with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def snapshot(path):
    def git(*args):return subprocess.check_output(['git','-C',str(path),*args],text=True).strip()
    return dict(path=str(path),head=git('rev-parse','HEAD'),branch=git('branch','--show-current'),status=git('status','--short'))
def build(work,front,lower,air=None,lock_path=ROOT/'docs/sources/sources.lock.json'):
    require_local()
    pins=producer_pins(json.loads(lock_path.read_text()))
    work.mkdir(parents=True,exist_ok=False)
    before={n:snapshot(p) for n,p in [('air-java',air or ROOT.parent/'air-java'),('proleap-poc',front),('cobol-lower',lower)]}
    for name,expected in pins.items():
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
    def compile_air(args,cwd=None):
        run('air-java',args)
    compile_dependency(work/'air-java',work,compile_air,lambda *args:['mvn','-B','-ntp',*args])
    run('proleap-poc',['mvn','-B','-ntp','clean','compile','org.apache.maven.plugins:maven-jar-plugin:3.4.2:jar','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile='+str(work/'frontend-classpath.txt')])
    run('cobol-lower',['mvn','-B','-ntp','clean','install','-DskipTests','-Dexec.skip=true'])
    run('cobol-lower',['mvn','-B','-ntp','-pl','adapters','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile='+str(work/'lower-classpath.txt')])
    after={n:snapshot(Path(v['path'])) for n,v in before.items()}
    if before!=after:raise ValueError('sibling state changed')
    config=dict(schema='w5-producer-build',sources=pins,mavenRepository=next((x.split('=',1)[1] for x in shlex.split(os.environ.get('MAVEN_OPTS','')) if x.startswith('-Dmaven.repo.local=')),None),before=before,after=after,commands=commands,javaVersion=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True))
    for name,jar,cp,main in [('frontend','proleap-poc/target/antlr-parse-tree-explorer-1.0.0-SNAPSHOT.jar','frontend-classpath.txt','io.github.gustavo2358.cobolexplorer.ExplorerMain'),('lower','cobol-lower/adapters/target/cobol-lower-adapters-0.1.0-SNAPSHOT.jar','lower-classpath.txt','io.github.gustavo2358.lower.adapters.cli.CobolLower')]:
        # Freeze product JAR locations as well as hashes: later producer builds may
        # replace SNAPSHOTs in a shared Maven cache without altering this runtime.
        exact={'air-java-0.1.0-SNAPSHOT.jar':work/'fast-upstream/air-model/air-java-0.1.0-SNAPSHOT.jar',
               'air-json-0.1.0-SNAPSHOT.jar':work/'fast-upstream/air-json/air-json-0.1.0-SNAPSHOT.jar',
               'cobol-lower-core-0.1.0-SNAPSHOT.jar':work/'cobol-lower/core/target/cobol-lower-core-0.1.0-SNAPSHOT.jar'}
        dependencies=[str(exact.get(Path(path).name,Path(path))) for path in (work/cp).read_text().strip().split(os.pathsep)]
        paths=[str(work/jar),*dependencies]
        config[name]=dict(main=main,cwd=str(work/('proleap-poc' if name=='frontend' else 'cobol-lower')),classpath=paths,jars={p:digest(p) for p in paths})
    (work/'producers.json').write_text(json.dumps(config,indent=2)+'\n');return config
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--frontend',type=Path,default=ROOT.parent/'proleap-poc');p.add_argument('--lower',type=Path,default=ROOT.parent/'cobol-lower');p.add_argument('--air',type=Path,default=ROOT.parent/'air-java');p.add_argument('--lock',type=Path,default=ROOT/'docs/sources/sources.lock.json');a=p.parse_args()
    build(a.work.resolve(),a.frontend.resolve(),a.lower.resolve(),a.air.resolve(),a.lock.resolve());print(a.work/'producers.json')
