#!/usr/bin/env python3
"""Compiled W1D module and consumer capabilities, plus frozen generic foundation."""
import argparse,json,os,subprocess
from pathlib import Path
from check_transport_architecture import dependencies_from_jdeps
from w1d_scope import verify,protections
ROOT=Path(__file__).resolve().parents[2]
INVENTORY='docs/evals/cp6/w1d-dependencies-inventory.json'
def check(root=ROOT,refresh=False):
    protections(root);verify(root)
    classes=root/'analysis-dependencies/target/classes';cpfile=root/'analysis-dependencies/target/architecture-classpath.txt'
    if not cpfile.exists():cpfile=root/'analysis-dependencies/target/runtime-classpath.txt'
    cp=cpfile.read_text().strip();paths=sorted(p.relative_to(classes).as_posix() for p in classes.rglob('*.class'))
    if not paths:raise ValueError('W1D compiled artifacts missing')
    def capture(args):return subprocess.check_output(args,cwd=root,text=True,stderr=subprocess.PIPE)
    edges=dependencies_from_jdeps(capture(['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)]))
    denied=('java.io.','java.nio.file.','java.net.','java.lang.reflect.','analysis.adapters.','analysis.launcher.','air.json.','cobolexplorer','org.antlr','lower.')
    consumer_denied=('analysis.structure.','analysis.solver.','analysis.application.','analysis.query.BatchReplayer','analysis.values.PossibleValues','analysis.cfg.','air.model.Publication','air.model.Unit','air.model.Sequence')
    for source,targets in edges.items():
        for target in targets:
            if any(x in target for x in denied):raise ValueError('W1D core outward dependency: '+source+' -> '+target)
            if source.endswith('CallDependencyConsumer') and any(x in target for x in consumer_denied):raise ValueError('W1D consumer acquired execution capability: '+target)
    # The source check protects absence of program-name semantics in the generic effect interpreter.
    effect=(root/'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ForeignEffectTransfer.java').read_text()
    if any(x in effect for x in ('ComputedTarget','LiteralTarget','cobol.program','CallNameInterpreter','stripTrailing','Operations.Invoke')):raise ValueError('generic effect interpreter acquired target semantics')
    actual=dict(classfiles=paths,jdeps={k:sorted(v) for k,v in sorted(edges.items())},publicDescriptors={p:capture(['javap','-classpath',str(classes)+os.pathsep+cp,'-public','-s',p[:-6].replace('/','.')]) for p in paths})
    if refresh:(root/INVENTORY).write_text(json.dumps(actual,indent=2)+'\n')
    elif json.loads((root/INVENTORY).read_text())!=actual:raise ValueError('W1D compiled module inventory drift')
    print('PASS: compiled W1D module/consumer capabilities; generic effects; frozen solver/lattice/DefaultValuePlan')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--refresh',action='store_true');a=p.parse_args();check(refresh=a.refresh)
