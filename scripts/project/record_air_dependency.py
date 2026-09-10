#!/usr/bin/env python3
"""Bind a fresh isolated install to exact Git source and the built reactor JARs."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(checkout,built,repo,output):
    def git(*args):return subprocess.check_output(['git','-C',str(checkout),*args])
    pin=json.loads((ROOT/'docs/sources/sources.lock.json').read_text())['air_java']['commit']
    head=git('rev-parse','HEAD').decode().strip()
    if head!=pin or git('diff','--name-only','HEAD'):raise ValueError('upstream source is not exact clean pin')
    names=git('ls-tree','-r','--name-only',pin).decode().splitlines()
    source_hashes={}
    for name in names:
        expected=git('show',pin+':'+name)
        if (built/name).read_bytes()!=expected:raise ValueError('built source differs from pin: '+name)
        source_hashes[name]=hashlib.sha256(expected).hexdigest()
    jars={}
    for artifact,module in [('air-java','air-model'),('air-json','air-json')]:
        jar=repo/'io/github/gustavo2358'/artifact/'0.1.0-SNAPSHOT'/(artifact+'-0.1.0-SNAPSHOT.jar')
        product=built/module/'target'/(artifact+'-0.1.0-SNAPSHOT.jar')
        if jar.read_bytes()!=product.read_bytes():raise ValueError('installed JAR differs from exact-source build: '+artifact)
        jars[artifact]=dict(installed=str(jar),built=str(product),sha256=sha(jar))
    receipt=dict(source=head,tree=git('rev-parse','HEAD^{tree}').decode().strip(),branch=git('branch','--show-current').decode().strip(),status=git('status','--short').decode(),built_source=str(built),source_sha256=source_hashes,jars=jars,maven_repository=str(repo),java=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True))
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(receipt,indent=2)+'\n')
    print('[air dependency] PASS '+head+' tree '+receipt['tree']+' JARs '+json.dumps(jars))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--checkout',type=Path,required=True);p.add_argument('--built-source',type=Path,required=True);p.add_argument('--maven-repo',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();record(a.checkout.resolve(),a.built_source.resolve(),a.maven_repo.resolve(),a.output.resolve())
