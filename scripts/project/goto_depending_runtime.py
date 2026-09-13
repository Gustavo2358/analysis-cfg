#!/usr/bin/env python3
"""Freeze compiled products from explicit checkouts; cache jars supply third parties only."""
import argparse,hashlib,json,subprocess,zipfile,time
from pathlib import Path
MODULES=('cfg-kernel','cfg-adapters','cfg-launcher','analysis-kernel','analysis-values','analysis-dependencies','analysis-dataflow','analysis-adapters','analysis-launcher')
THIRD=('org/antlr/antlr4-runtime/4.13.2/antlr4-runtime-4.13.2.jar','org/slf4j/slf4j-api/2.0.18/slf4j-api-2.0.18.jar','ch/qos/logback/logback-classic/1.6.3/logback-classic-1.6.3.jar','ch/qos/logback/logback-core/1.6.3/logback-core-1.6.3.jar','com/fasterxml/jackson/core/jackson-core/2.22.2/jackson-core-2.22.2.jar','com/fasterxml/jackson/core/jackson-databind/2.22.2/jackson-databind-2.22.2.jar','com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar','com/dynatrace/hash4j/hash4j/0.30.0/hash4j-0.30.0.jar')

def freeze(root,work,m2,allow_dirty=False,semantic_product_version="2.6.0"):
    started=time.monotonic_ns()
    work.mkdir(parents=True,exist_ok=False);checkouts={r:root/r for r in ('air-java','proleap-poc','cobol-lower','analysis-cfg')};sources={}
    def git(repo,*args):return subprocess.check_output(['git','-C',str(repo),*args],text=True).strip()
    for r,path in checkouts.items():
        if not allow_dirty and git(path,'status','--porcelain'):raise ValueError('clean exact snapshot required: '+r)
        sources[r]=git(path,'rev-parse','HEAD')
    jars={};hashes={}
    modules={'frontend':root/'proleap-poc','lower-core':root/'cobol-lower/core','lower-adapters':root/'cobol-lower/adapters','air-model':root/'air-java/air-model','air-json':root/'air-java/air-json',**{m:root/'analysis-cfg'/m for m in MODULES}}
    for name,path in modules.items():
        classes=path/'target/classes';files=sorted(classes.rglob('*'))
        if not any(p.suffix=='.class' for p in files):raise ValueError('compiled product required: '+name)
        jar=work/(name+'.jar')
        with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
            for p in files:
                if p.is_file():z.write(p,str(p.relative_to(classes)))
        jars[name]=str(jar);hashes[name]=hashlib.sha256(jar.read_bytes()).hexdigest()
    third=[str(m2/p) for p in THIRD]
    if not all(Path(p).is_file() for p in third):raise ValueError('third-party dependency missing')
    air=[jars['air-model'],jars['air-json']];cfg=[jars[m] for m in MODULES]+air+third
    config=dict(sources=sources,checkouts={r:str(p) for r,p in checkouts.items()},semanticProductVersion=semantic_product_version,jarSha256=hashes,
        setupElapsedMs=(time.monotonic_ns()-started)/1_000_000,
        qualification='DEVELOPMENT ONLY' if allow_dirty else 'clean exact sources; immutable compiled products',
        frontend=dict(main='io.github.gustavo2358.cobolexplorer.ExplorerMain',classpath=[jars['frontend']]+third),
        lower=dict(main='io.github.gustavo2358.lower.adapters.cli.CobolLower',classpath=[jars['lower-core'],jars['lower-adapters']]+air+third),
        cfg=dict(main='io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg',classpath=cfg),
        dependency=dict(main='io.github.gustavo2358.analysis.launcher.AnalysisDependencies',classpath=cfg))
    (work/'runtime.json').write_text(json.dumps(config,indent=2)+'\n')
    return config
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);p.add_argument('--work',type=Path,required=True);p.add_argument('--m2',type=Path,required=True);p.add_argument('--allow-dirty',action='store_true');p.add_argument('--semantic-product-version',choices=['2.6.0','2.7.0','2.8.0'],default='2.6.0')
    a=p.parse_args();freeze(a.root.resolve(),a.work.resolve(),a.m2.resolve(),a.allow_dirty,a.semantic_product_version)
