#!/usr/bin/env python3
"""Exact W1A/W1C Git archives, isolated builds and source/JAR receipts. Siblings remain untouched."""
import argparse,io,json,os,subprocess,tarfile
from pathlib import Path
from prepare_w5_producers import digest,snapshot
ROOT=Path(__file__).resolve().parents[2]
FRONT='53d774026a1e4bcd969c7783a1d277aaa87b5f2f'
LOWER='9de3825da64898258e647727393f01b9e9198d9e'
def build(work):
    work.mkdir(parents=True,exist_ok=False);before={};sources={};commands=[]
    for name,pin in [('proleap-poc',FRONT),('cobol-lower',LOWER)]:
        source=ROOT.parent/name;before[name]=snapshot(source)
        tree=subprocess.check_output(['git','-C',str(source),'rev-parse',pin+'^{tree}'],text=True).strip()
        archive=subprocess.check_output(['git','-C',str(source),'archive',pin]);dest=work/name;dest.mkdir()
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:tar.extractall(dest,filter='data')
        sources[name]=dict(commit=pin,tree=tree,files={str(p.relative_to(dest)):digest(p) for p in sorted(dest.rglob('*')) if p.is_file()})
    def run(name,args):
        p=subprocess.run(args,cwd=work/name,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        log=work/(str(len(commands)+1)+'-build.log');log.write_bytes(p.stdout)
        commands.append(dict(argv=args,cwd=str(work/name),exitCode=p.returncode,log=str(log),logSha256=digest(log)))
        if p.returncode:raise ValueError('build failed: '+str(log))
    mvn=['mvn','-B','-ntp']
    run('proleap-poc',mvn+['clean','compile','org.apache.maven.plugins:maven-jar-plugin:3.4.2:jar','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile='+str(work/'front-classpath.txt')])
    run('cobol-lower',mvn+['clean','install','-DskipTests','-Dexec.skip=true'])
    run('cobol-lower',mvn+['-pl','adapters','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile='+str(work/'lower-classpath.txt')])
    config=dict(schema='cp6-w1d-producers@1',sources=sources,commands=commands,before=before,after={n:snapshot(ROOT.parent/n) for n in before},javaVersion=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True),mavenOptions=os.environ.get('MAVEN_OPTS',''))
    if config['before']!=config['after']:raise ValueError('sibling checkout changed')
    for name,jar,cp,main in [('frontend','proleap-poc/target/antlr-parse-tree-explorer-1.0.0-SNAPSHOT.jar','front-classpath.txt','io.github.gustavo2358.cobolexplorer.ExplorerMain'),('lower','cobol-lower/adapters/target/cobol-lower-adapters-0.1.0-SNAPSHOT.jar','lower-classpath.txt','io.github.gustavo2358.lower.adapters.cli.CobolLower')]:
        paths=[str(work/jar),*(work/cp).read_text().strip().split(os.pathsep)]
        config[name]=dict(main=main,classpath=paths,jars={p:digest(p) for p in paths})
    for name,receipt in sources.items():
        if any(digest(work/name/p)!=sha for p,sha in receipt['files'].items()):raise ValueError('build modified exported source')
    (work/'producers.json').write_text(json.dumps(config,indent=2)+'\n');print(work/'producers.json')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--work',type=Path,required=True);args=parser.parse_args();build(args.work.resolve())
