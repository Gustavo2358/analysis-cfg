#!/usr/bin/env python3
"""Post-CP5 RESOURCE_LIMIT focal diff, immutable baseline and exact delivery manifest."""
import argparse,hashlib,json,subprocess,sys
from pathlib import Path
from resource_limit_scope import BASE,PRODUCTION,TESTS,contract,sha,boundaries,INVENTORY
ROOT=Path(__file__).resolve().parents[2]
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def files():return sorted({p for p in git('ls-files','-z','--cached','--others','--exclude-standard').decode().split('\0') if p and p!='MANIFEST.sha256' and (ROOT/p).is_file()})
def manifest():return ('# SHA-256 da remediação RESOURCE_LIMIT; exclui este manifesto e artefatos ignorados.\n'+''.join(sha((ROOT/p).read_bytes())+'  '+p+'\n' for p in files())).encode()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--update-manifest',action='store_true');args=parser.parse_args()
    try:
        git('merge-base','--is-ancestor',BASE,'HEAD')
        if git('rev-parse',BASE+'^{tree}').decode().strip()!='3bc8b948de250c9eb4a756513294fb14a3ccc6db':raise ValueError('CP5 tree mismatch')
        c=contract(ROOT)
        if c['base']!=BASE or set(c['changed_production'])!=PRODUCTION or set(c['changed_tests'])!=TESTS or set(c['baseline_sha256'])!=PRODUCTION|TESTS|{'docs/sources/sources.lock.json'}:raise ValueError('focal production authority mismatch')
        work=json.loads((ROOT/'docs/work/active/WORK-CFG-029/work-item.json').read_text())
        if work['authorization']!='implementation' or work['checkpoint']!='POST_CP5_COMPATIBILITY_REMEDIATION':raise ValueError('wrong authorized checkpoint')
        changed=set(git('diff','--name-only',BASE).decode().splitlines())|set(git('ls-files','--others','--exclude-standard').decode().splitlines())
        allowed=PRODUCTION|set(c['changed_tests'])|{'MANIFEST.sha256','.github/workflows/ci.yml','.gitattributes'}
        for p in changed:
            if p.endswith('.java') or p.endswith('pom.xml'):
                if p not in allowed:raise ValueError('unapproved source/POM change: '+p)
            elif p not in allowed and not p.startswith(('docs/','scripts/')):raise ValueError('path outside focal scope: '+p)
        protected=['docs/work/evidence/WORK-CFG-028','docs/work/active/WORK-CFG-028','cfg-adapters/src/test/resources','analysis-adapters/src/test/resources','analysis-launcher/src/test/resources','scripts/project/ci_source_receipt.py']
        protected += ['docs/evals/cp5/'+p for p in ['history','preparation-source-inventory.json','w1-inventory.json','w2-inventory.json','w3-inventory.json','w4-inventory.json','w5-inventory.json','w1-source-inventory.json','w2-source-inventory.json','w3-source-inventory.json','w4-source-inventory.json','w5-source-inventory.json','core-size-review.json','result-review.json','phase-review.json']]
        if git('diff','--name-only',BASE,'--',*protected):raise ValueError('historical evidence/approved inventory/fixture/receipt changed')
        for p,digest in c['baseline_sha256'].items():
            if sha(git('show',BASE+':'+p))!=digest:raise ValueError('focal baseline digest differs from Git: '+p)
            if sha((ROOT/p).read_bytes())!=c['current_sha256'][p]:raise ValueError('unreviewed focal bytes: '+p)
        historical_life=json.loads(git('show',BASE+':docs/work/cp5-lifecycle.json'))
        historical_life['pr'].update(state='MERGED',draft=False,merge_commit=BASE)
        historical_life['waves'][4].update(status='APPROVED',reviewed_head='c6b12bf3efe6360b4e33c9a587ad0185b449990d')
        historical_life['wave_5'].update(review='APPROVED',reviewed_head='c6b12bf3efe6360b4e33c9a587ad0185b449990d')
        historical_life['cp5_status']='APPROVED / MERGED'
        historical_life['closure']=dict(merge=BASE,tree='3bc8b948de250c9eb4a756513294fb14a3ccc6db',evidence='docs/work/evidence/WORK-CFG-029/upstream-remote.json',authorization='docs/work/evidence/WORK-CFG-029/authorization.json')
        if json.loads((ROOT/'docs/work/cp5-lifecycle.json').read_text())!=historical_life:raise ValueError('historical CP5 lifecycle changed outside explicit final approval/merge')
        before=json.loads(git('show',BASE+':docs/sources/sources.lock.json'));after=json.loads((ROOT/'docs/sources/sources.lock.json').read_text())
        before.pop('air_java');a=after.pop('air_java')
        if before!=after or a['commit']!='17029898fd0ee8fabcaaae89f7260148633d4b12':raise ValueError('only exact air-java repin authorized')
        actual={p:sha((ROOT/p).read_bytes()) for p in files() if p.endswith('.java') or p.endswith('pom.xml')}
        if actual!=json.loads((ROOT/INVENTORY).read_text())['files']:raise ValueError('exact source inventory mismatch')
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
        print('[scope/manifest] PASS: exact CP5 base; focal production; W1–W4/wire/writer/history preserved; only air-java repinned')
        return 0
    except (ValueError,OSError,subprocess.CalledProcessError) as e:print('[scope/manifest] FAIL: '+str(e));return 1
if __name__=='__main__':sys.exit(main())
