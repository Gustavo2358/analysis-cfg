"""Focal post-CP5 authorization, byte preservation and operational boundary checks."""
import hashlib,json,re,subprocess
from pathlib import Path
BASE='4229ec1cfd9c1d9f9e851f3cabe6993b4d4ed9b8'
CONTRACT='docs/evals/resource-limit-compatibility.json'
INVENTORY='docs/work/evidence/WORK-CFG-029/source-inventory.json'
PRODUCTION={
'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/CfgBuildCoordinator.java',
'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/CfgBuildResult.java',
'analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/AnalysisDataflow.java',
'analysis-adapters/src/main/java/io/github/gustavo2358/analysis/adapters/DataflowAirReader.java',
'analysis-launcher/src/main/java/io/github/gustavo2358/analysis/launcher/AnalysisDataflow.java'}
TESTS={'cfg-adapters/src/test/java/io/github/gustavo2358/analysis/cfg/adapters/TransportTest.java', 'cfg-kernel/src/test/java/io/github/gustavo2358/analysis/cfg/application/CfgBuildCoordinatorTest.java', 'analysis-dataflow/src/test/java/io/github/gustavo2358/analysis/dataflow/CompositionTest.java', 'analysis-launcher/src/test/java/io/github/gustavo2358/analysis/launcher/DataflowCliTest.java', 'cfg-launcher/src/test/java/io/github/gustavo2358/analysis/cfg/launcher/EvalCfg031Test.java', 'cfg-kernel/src/test/java/io/github/gustavo2358/analysis/cfg/domain/EvalCfg030Test.java', 'cfg-adapters/src/test/java/io/github/gustavo2358/analysis/cfg/adapters/ScalarAssignTest.java'}

def sha(data):return hashlib.sha256(data).hexdigest()
def contract(root):return json.loads((root/CONTRACT).read_text())
def allows_change(root,path,old_digest):
    from w1d_scope import allows_change as w1d_allows
    if w1d_allows(root,path,old_digest): return True
    c=contract(root)
    if (path=='docs/sources/sources.lock.json' and c['base']==BASE and c['baseline_sha256'].get(path)==old_digest
            and set(c['changed_tests'])==TESTS and set(c['changed_production'])==PRODUCTION):
        b=json.loads((root/'docs/work/evidence/WORK-CFG-030/baseline.json').read_text())
        return b['source_lock_sha256']==sha((root/path).read_bytes())
    return (c['base']==BASE and c['baseline_sha256'].get(path)==old_digest
            and c['current_sha256'].get(path)==sha((root/path).read_bytes())
            and set(c['changed_tests'])==TESTS and set(c['changed_production'])==PRODUCTION
            and path in TESTS|PRODUCTION|{'docs/sources/sources.lock.json'})
def boundaries(root):
    """Nominal pre-analysis ordering, complementary to real runtime oracles."""
    flow=(root/'analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/AnalysisDataflow.java').read_text()
    prefix=flow.split('        requireBuilt(cfg);')[0]
    if any(x in prefix for x in ('AnalysisSession.open(', 'new PlanningExecution(', 'DefaultValuePlan.select(', 'new AnalysisRegistry(', 'new PossibleValuesProvider(')):
        raise ValueError('resource failure starts analysis before preflight classification')
    arm=flow.split('case RESOURCE_LIMIT ->',1)[1].split('case INCOMPLETE_VALIDATION',1)[0]
    if 'throw new PreparationException(Failure.EXTERNAL_RESOURCE_LIMIT,' not in arm or any(x in arm for x in ('sourceUnknownRemainder','effectiveUnknownRemainder','PreparedDataflowResult.capture','AnalysisSession.open')):
        raise ValueError('resource failure must produce no semantic/source-open result')
    cli=(root/'analysis-launcher/src/main/java/io/github/gustavo2358/analysis/launcher/AnalysisDataflow.java').read_text()
    if cli.index('new LocalResultWriter()')<cli.index('catch(io.github.gustavo2358.analysis.dataflow.AnalysisDataflow.PreparationException'):
        raise ValueError('delivery started before preparation completed')
    if 'catch(Error' in flow.replace(' ','') or 'catch(Throwable' in flow.replace(' ',''):
        raise ValueError('JVM errors are not typed resource outcomes')
def refresh(root):
    c=contract(root)
    names=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=root).decode().split('\0')
    c['current_sha256']={p:sha((root/p).read_bytes()) for p in c['baseline_sha256']}
    (root/CONTRACT).write_text(json.dumps(c,indent=2)+'\n')
    files={p:sha((root/p).read_bytes()) for p in sorted(set(names)) if p and (p.endswith('.java') or p.endswith('pom.xml'))}
    (root/INVENTORY).write_text(json.dumps(dict(baseline=BASE,files=files),indent=2)+'\n')
if __name__=='__main__':
    import sys
    root=Path(__file__).resolve().parents[2]
    if sys.argv[1:]==['--refresh-inventory']:refresh(root)
    else:boundaries(root);print('[resource boundary] PASS: no session/key/solver/facts/result/delivery before completed preflight')
