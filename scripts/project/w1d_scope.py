#!/usr/bin/env python3
"""Exact authorized CP6 W1D delta, preserving solver/lattice/W5 and historical contracts."""
import hashlib
import json
import subprocess
import os
from pathlib import Path

BASE = 'c39a92f930b1c693857a0b30a1f5155f3f81520c'
INVENTORY = 'docs/evals/cp6/w1d-source-inventory.json'
MUTABLE = {
    'pom.xml', 'analysis-adapters/pom.xml', 'analysis-launcher/pom.xml',
    'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java',
    'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CfgGraph.java',
    'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CfgTransition.java',
    'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/IndexBuilder.java',
    'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/TextProfile.java',
    'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/PossibleValuesAnalysis.java',
    'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/PossibleValuesProvider.java',
    'cfg-adapters/src/main/java/io/github/gustavo2358/analysis/cfg/adapters/CfgJsonWriter.java',
    *{'cfg-kernel/src/test/java/io/github/gustavo2358/analysis/cfg/domain/EvalCfg0'+str(n)+'Test.java' for n in (28,29,30)},
    'docs/sources/sources.lock.json',
}
NEW_VALUES = 'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ForeignEffectTransfer.java'
NEW_W5 = {'analysis-adapters/src/main/java/io/github/gustavo2358/analysis/adapters/'+n+'.java' for n in ('DependencyJson','DependencyFileWriter')} | {'analysis-launcher/src/main/java/io/github/gustavo2358/analysis/launcher/AnalysisDependencies.java'}
NEW_CFG_TEST = 'cfg-adapters/src/test/java/io/github/gustavo2358/analysis/cfg/adapters/W1dInvokeWireTest.java'


def digest(data): return hashlib.sha256(data).hexdigest()
def git(root,*args): return subprocess.check_output(['git','-C',str(root),*args])
def authorized(root):
    # Closed W1D retains its exact approved scope in the immutable archive.
    p=root/'docs/work/history/WORK-CFG-033/work-item.json'
    if not p.exists(): return False
    w=json.loads(p.read_text())
    return w['id']=='WORK-CFG-033' and w['authorization']=='implementation' and w['checkpoint']=='CP6_W1D'


def files(root):
    return sorted({p for p in git(root,'ls-files','--cached','--others','--exclude-standard','-z').decode().split('\0') if p and (root/p).is_file()})


def source_files(root):
    result={}
    for directory,dirs,names in os.walk(root):
        dirs[:]=[d for d in dirs if d not in {'.git','target','.cache','.harness-results','__pycache__','node_modules'}]
        for name in names:
            if name.endswith('.java') or name=='pom.xml':
                path=Path(directory)/name
                result[str(path.relative_to(root))]=digest(path.read_bytes())
    return dict(sorted(result.items()))


def protections(root):
    if not authorized(root): raise ValueError('W1D implementation authorization missing')
    git(root,'merge-base','--is-ancestor',BASE,'HEAD')
    baseline=git(root,'ls-tree','-r','--name-only',BASE).decode().splitlines()
    for path in baseline:
        if path.endswith('.java') or path.endswith('pom.xml'):
            if path not in MUTABLE and (root/path).read_bytes()!=git(root,'show',BASE+':'+path):
                raise ValueError('W1D changed protected source: '+path)
    for path in files(root):
        if path not in baseline and (path.endswith('.java') or path.endswith('pom.xml')):
            if path not in NEW_W5|{NEW_VALUES,NEW_CFG_TEST} and not path.startswith(('analysis-dependencies/','analysis-adapters/src/test/java/io/github/gustavo2358/analysis/adapters/W1d','analysis-launcher/src/test/java/io/github/gustavo2358/analysis/launcher/DependencyCliTest')):
                raise ValueError('unregistered W1D source: '+path)
    protected=['docs/work/evidence/WORK-CFG-028','docs/work/evidence/WORK-CFG-029','docs/work/evidence/WORK-CFG-030','docs/work/evidence/WORK-CFG-031','docs/work/evidence/WORK-CFG-032','docs/evals/cp5','docs/work/cp5-lifecycle.json','analysis-adapters/src/main/java/io/github/gustavo2358/analysis/adapters/ResultJson.java','analysis-kernel/src/main/java/io/github/gustavo2358/analysis/solver']
    changed=git(root,'diff','--name-only',BASE,'--',*protected).decode().splitlines()
    route='docs/work/evidence/WORK-CFG-032/README.md'
    for path in changed:
        if path!=route or (root/path).read_bytes()!=git(root,'show',BASE+':'+path).replace(b'../../active/WORK-CFG-032/state.md',b'../../history/WORK-CFG-032/state.md'):
            raise ValueError('historical evidence or frozen solver/W5 result changed: '+path)
    # Remediation preserves the first W1D delivery, including its now-diagnosed wire evidence.
    remediation_base='1ab16bdeae8d8af23e723d0b239ba191a695764a'
    remediation_mutable={NEW_CFG_TEST, 'cfg-adapters/src/main/java/io/github/gustavo2358/analysis/cfg/adapters/CfgJsonWriter.java'}
    for path in git(root,'ls-tree','-r','--name-only',remediation_base).decode().splitlines():
        frozen=(path.endswith('.java') or path.endswith('pom.xml') or '/src/test/resources/' in path
                or path.startswith('docs/work/evidence/WORK-CFG-033/') or path=='docs/architecture/cfg-json-v1.md')
        if frozen and path not in remediation_mutable and (root/path).read_bytes()!=git(root,'show',remediation_base+':'+path):
            raise ValueError('remediation changed protected source/evidence: '+path)
    lock=json.loads((root/'docs/sources/sources.lock.json').read_text())
    for key,field,sha in [('air_java','commit','2a37f5e980ba25fdc79614a66030a84d8bf5b8c9'),('proleap_poc','main_commit','53d774026a1e4bcd969c7783a1d277aaa87b5f2f'),('cobol_lower','commit','9de3825da64898258e647727393f01b9e9198d9e'),('analysis_ir','commit','51b4d9a8ae0364232bd97103cd73a77e1a34996c')]:
        if lock[key][field]!=sha: raise ValueError('W1D frozen source pin drift: '+key)


def verify(root):
    # Documentation guard tests use exported trees with no Git metadata.
    # Exact current bytes are verifiable there; ancestry is checked by the scope gate.
    if not authorized(root): raise ValueError('W1D implementation authorization missing')
    expected=json.loads((root/INVENTORY).read_text())
    if expected['base']!=BASE or source_files(root)!=expected['files']: raise ValueError('no unauthorized Java/POM implementation (W1D exact inventory)')


def allows_change(root,path,old_digest):
    if not authorized(root) or path not in MUTABLE: return False
    data=json.loads((root/INVENTORY).read_text())
    return old_digest in data['historicalDigests'].get(path,[]) and data['current'].get(path)==digest((root/path).read_bytes())


def refresh(root):
    protections(root);old={p:{digest(git(root,'show',BASE+':'+p))} for p in MUTABLE}
    def visit(value):
        if isinstance(value,dict):
            for k,v in value.items():
                if k in old and isinstance(v,str) and len(v)==64: old[k].add(v)
                visit(v)
        elif isinstance(value,list):
            for v in value:visit(v)
    for p in (root/'docs/work/evidence/WORK-CFG-028').rglob('*.json'):
        visit(json.loads(p.read_text()))
    for p in list((root/'docs/evals/cp5').glob('*inventory*.json'))+[root/'docs/work/evidence/WORK-CFG-029/source-inventory.json']:
        visit(json.loads(p.read_text()))
    path=root/INVENTORY;path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(dict(base=BASE,files=source_files(root),current={p:digest((root/p).read_bytes()) for p in sorted(MUTABLE)},historicalDigests={p:sorted(v) for p,v in sorted(old.items())}),indent=2)+'\n')


if __name__=='__main__':
    import sys
    root=Path(__file__).resolve().parents[2]
    if sys.argv[1:]==['--refresh']:refresh(root)
    protections(root);verify(root);print('PASS: exact W1D scope; solver/lattice/DefaultValuePlan and historical evidence preserved')
