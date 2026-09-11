#!/usr/bin/env python3
"""Post-CP5 baseline diff: immutable Java, historical evidence, exact pins and delivery manifest."""
import argparse,hashlib,json,subprocess,sys
from pathlib import Path
from resource_limit_scope import sha,boundaries,INVENTORY
ROOT=Path(__file__).resolve().parents[2]
BASE='15bd3afe1affdcb5ec49956960f884bde8c89498'
BASE_TREE='0de533bcabd868e2ee18686a9cdd265973408f75'
BASELINE='docs/work/evidence/WORK-CFG-030/baseline.json'
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def files():return sorted({p for p in git('ls-files','-z','--cached','--others','--exclude-standard').decode().split('\0') if p and p!='MANIFEST.sha256' and (ROOT/p).is_file()})
def manifest():return ('# SHA-256 da entrega analysis-cfg; exclui este manifesto e artefatos ignorados.\n'+''.join(sha((ROOT/p).read_bytes())+'  '+p+'\n' for p in files())).encode()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--update-manifest',action='store_true');args=parser.parse_args()
    try:
        from w1d_scope import authorized, protections, verify
        if authorized(ROOT):
            protections(ROOT);verify(ROOT)
            changed=set(git('diff','--name-only','c39a92f930b1c693857a0b30a1f5155f3f81520c').decode().splitlines())|set(git('ls-files','--others','--exclude-standard').decode().splitlines())
            for p in changed:
                if p not in {'ARCHITECTURE.md','MANIFEST.sha256','pom.xml','.github/workflows/ci.yml'} and not p.startswith(('docs/','scripts/','analysis-','cfg-')):raise ValueError('path outside W1D scope: '+p)
            expected=manifest()
            if args.update_manifest:(ROOT/'MANIFEST.sha256').write_bytes(expected)
            elif (ROOT/'MANIFEST.sha256').read_bytes()!=expected:raise ValueError('delivery manifest mismatch; review diff before --update-manifest')
            git('diff','--check','c39a92f930b1c693857a0b30a1f5155f3f81520c')
            print('[scope/manifest] PASS: exact W1D delta; protected historical evidence, solver, lattice and DefaultValuePlan')
            return 0
        git('merge-base','--is-ancestor',BASE,'HEAD')
        if git('rev-parse',BASE+'^{tree}').decode().strip()!=BASE_TREE:raise ValueError('approved post-CP5 tree mismatch')
        b=json.loads((ROOT/BASELINE).read_text())
        if b['base']!=BASE or b['base_tree']!=BASE_TREE or b['production_semantic_changes'] is not False:raise ValueError('baseline authority mismatch')
        work=json.loads((ROOT/'docs/work/history/WORK-CFG-030/work-item.json').read_text())
        if work['authorization']!='implementation' or work['checkpoint']!='CP5_CLOSEOUT_AND_CP6_BASELINE_SYNCHRONIZATION':raise ValueError('wrong authorized checkpoint')
        changed=set(git('diff','--name-only',BASE).decode().splitlines())|set(git('ls-files','--others','--exclude-standard').decode().splitlines())
        for p in changed:
            if p.endswith('.java') or p.endswith('pom.xml'):raise ValueError('Java/POM change forbidden during baseline synchronization: '+p)
            if p not in {'AGENTS.md','ARCHITECTURE.md','MANIFEST.sha256','.github/workflows/ci.yml','.gitattributes'} and not p.startswith(('docs/','scripts/')):raise ValueError('path outside baseline scope: '+p)
        protected=['docs/work/evidence/WORK-CFG-028','docs/work/evidence/WORK-CFG-029','docs/evals/resource-limit-compatibility.json','cfg-adapters/src/test/resources','analysis-adapters/src/test/resources','analysis-launcher/src/test/resources','scripts/project/ci_source_receipt.py','docs/evals/cp5']
        if git('diff','--name-only',BASE,'--',*protected):raise ValueError('historical evidence/approved inventory/fixture/receipt changed')
        for number in (28,29):
            w=f'WORK-CFG-{number:03}'
            for fn in ('work-item.json','spec.md','plan.md','eval.md','state.md'):
                before=git('show',BASE+f':docs/work/active/{w}/'+fn)
                if before!=(ROOT/f'docs/work/history/{w}'/fn).read_bytes():raise ValueError('archival bytes changed: '+w+'/'+fn)
            if (ROOT/f'docs/work/active/{w}').exists():raise ValueError('completed work remains active: '+w)
        historical=json.loads(git('show',BASE+':docs/work/cp5-lifecycle.json'));life=json.loads((ROOT/'docs/work/cp5-lifecycle.json').read_text())
        if life['review_history'][:len(historical['review_history'])]!=historical['review_history']:raise ValueError('historical review events rewritten')
        for field in ('waves','wave_1','wave_2','wave_3','wave_4','wave_5','pr'):
            if life[field]!=historical[field]:raise ValueError('approved CP5 metadata changed: '+field)
        if life['cp5_status']!='APPROVED / MERGED / CLOSED' or life['cp6']!={'status':'NOT_STARTED','authorization':'NOT_AUTHORIZED'}:raise ValueError('CP5/CP6 lifecycle')
        lock_path=ROOT/'docs/sources/sources.lock.json';lock=json.loads(lock_path.read_text())
        if sha(lock_path.read_bytes())!=b['source_lock_sha256']:raise ValueError('source lock differs from reviewed synchronization')
        if {k:lock[k]['main_commit' if k=='proleap_poc' else 'commit'] for k in b['sources']}!=b['sources']:raise ValueError('inconsistent component pins')
        if lock['air_java']['analysis_ir']['commit']!=lock['analysis_ir']['commit']:raise ValueError('normative pins differ')
        from prepare_w5_producers import FRONT,LOWER
        if FRONT!=b['sources']['proleap_poc'] or LOWER!=b['sources']['cobol_lower']:raise ValueError('E2E producers differ from lock')
        actual={p:sha((ROOT/p).read_bytes()) for p in files() if p.endswith('.java') or p.endswith('pom.xml')}
        if actual!=json.loads((ROOT/INVENTORY).read_text())['files']:raise ValueError('exact approved Java/POM inventory mismatch')
        from check_scalar_contract import verify_scalar_contract
        verify_scalar_contract(ROOT);boundaries(ROOT)
        from check_analysis_architecture import check_direct_air
        if check_direct_air(ROOT):raise ValueError('direct AIR dependency drift')
        sys.path.insert(0,str(ROOT/'scripts/harness'))
        from validate_cp5 import validate_cp5
        errors=validate_cp5(ROOT)
        if errors:raise ValueError('; '.join(errors))
        expected=manifest()
        if args.update_manifest:(ROOT/'MANIFEST.sha256').write_bytes(expected)
        elif (ROOT/'MANIFEST.sha256').read_bytes()!=expected:raise ValueError('delivery manifest mismatch; review diff before --update-manifest')
        git('diff','--check',BASE)
        print('[scope/manifest] PASS: zero Java/POM/fixture changes; exact historical archives, append-only approvals, merged upstream pins, no CP6')
        return 0
    except (ValueError,OSError,subprocess.CalledProcessError) as e:print('[scope/manifest] FAIL: '+str(e));return 1
if __name__=='__main__':sys.exit(main())
