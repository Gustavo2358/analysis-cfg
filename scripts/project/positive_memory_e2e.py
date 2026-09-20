#!/usr/bin/env python3
"""Reproducible W1 cohort capture. Retains raw evidence; missing wire fields fail loudly.
Runtime uses commands {stage: JVM/classpath/main argv} and frontendCheckout.
The cohort is frozen separately from execution; --physical changes only frontend facts.
"""
import argparse, hashlib, json, pathlib, shutil, subprocess, time

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def execute(runtime, cohort, out, physical=False):
    out.mkdir(parents=True,exist_ok=False); rows=[]
    for case in sorted(p for p in cohort.iterdir() if p.is_dir()):
        dest=out/case.name; dest.mkdir(); front=['--source',case/'input.cbl','--copybooks',case/'copybooks','--output',dest/'sp']
        if (case/'inventory.json').exists():front+=['--source-inventory',case/'inventory.json']
        if physical:front+=['--storage-profile','ibm-enterprise-6.4-fixed-display-1047@1']
        row={'case':case.name,'phases':{},'status':'OBSERVED','producerProfile':'ibm-enterprise-6.4-fixed-display-1047@1' if physical else 'UNSPECIFIED'}
        stages=[('frontend',front),('lower',[dest/'sp/cobol-semantic-product.json',dest/'air.json']),('cfg',[dest/'air.json',dest/'cfg.json']),('dependency',[dest/'air.json',dest/'dependencies.json'])]
        if physical:stages += [('dependencyPhysical',[dest/'air.json',dest/'dependencies-physical.json','--experimental-physical'])]
        for stage,args in stages:
            air_before=sha(dest/'air.json') if stage.startswith('dependency') else None
            command=runtime['commands']['dependency' if stage=='dependencyPhysical' else stage]+list(map(str,args)); begin=time.monotonic()
            with (dest/(stage+'.log')).open('w') as log:
                try: code=subprocess.run(command,cwd=runtime['frontendCheckout'],stdout=log,stderr=subprocess.STDOUT,timeout=120).returncode
                except subprocess.TimeoutExpired:code='TIMEOUT'
            row['phases'][stage]={'command':command,'exitCode':code,'seconds':time.monotonic()-begin}
            if air_before is not None:
                assert air_before==sha(dest/'air.json'), 'consumer mutated AIR'
                row['phases'][stage]['airSha256']=air_before
            if code:row['status']='PIPELINE_FAILURE';break
        for suffix in ('','-physical'):
            p=dest/('dependencies'+suffix+'.json')
            if p.exists():
                d=json.loads(p.read_text())
                for s in d['sites']:
                    for key in ('sourceValueRemainder','modelValueRemainder','effectiveUnknownRemainder'):assert key in s,(case.name,key)
                row['metrics'+suffix]=d['metrics']
        row['hashes']={str(p.relative_to(dest)):sha(p) for p in dest.rglob('*') if p.is_file()}
        rows.append(row); (out/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(case.name,row['status'],flush=True)
    return rows

def main():
    p=argparse.ArgumentParser();p.add_argument('--runtime',required=True,type=pathlib.Path);p.add_argument('--cohort',required=True,type=pathlib.Path);p.add_argument('--out',required=True,type=pathlib.Path);p.add_argument('--physical',action='store_true');a=p.parse_args()
    execute(json.loads(a.runtime.read_text()),a.cohort.resolve(),a.out.resolve(),a.physical)

# Deliberately retain full candidate supports, timing and semantic IDs. Coverage is
# compared separately, never stripped from the calculation before it executes.
def compare(before, after):
    rows=[]
    for case in sorted(p.name for p in before.iterdir() if p.is_dir()):
        a=before/case/'dependencies.json'; b=after/case/'dependencies.json'
        if not a.exists() or not b.exists():
            rows.append({'case':case,'status':'BASELINE_FAILURE' if not a.exists() and not b.exists() else 'PIPELINE_DELTA'});continue
        old=json.loads(a.read_text());new=json.loads(b.read_text())
        changes=[]
        for section in ('sites','edges','fileDependencies','sourceDependencies'):
            if old.get(section)!=new.get(section):changes.append(section)
        def sites(d):
            return [{k:s[k] for k in ('operation','command','targetKind','subject','valuePoint','reachability','targetStatus','rawCandidates','candidates','modelValueRemainder','interpretationUnknownRemainder','openControlRemainder','evidence','provenance','premises','analysisStatus','analysisReasons') if k in s} for s in d['sites']]
        rows.append({'case':case,'status':'IDENTICAL_SEMANTICS' if sites(old)==sites(new) and old.get('fileDependencies')==new.get('fileDependencies') and old.get('sourceDependencies')==new.get('sourceDependencies') else 'REVIEW_DELTA','changedSections':changes,'oldSites':sites(old),'newSites':sites(new)})
    return rows

def verify_vertical(directory, physical):
    expected={'call-before':'OLDPGM','layout-independent':'OLDPGM','layout-family':'OLDPGM','partial-group-copy':'PROGA001'}
    results=[]
    for name,target in expected.items():
        case=directory/name; air=json.loads((case/'air.json').read_text())['publication'];sp=json.loads((case/'sp/cobol-semantic-product.json').read_text())
        assert sp['storage']['profile']==('IBM_ENTERPRISE_6_4_FIXED_DISPLAY_1047' if physical else 'UNSPECIFIED'),sp['storage']['profile']
        for suffix in ('','-physical') if physical else ('',):
            d=json.loads((case/('dependencies'+suffix+'.json')).read_text());m=d['metrics'];enabled=suffix=='-physical'
            assert m['logicalOnlyMode']==int(not enabled) and m['experimentalPhysicalMode']==int(enabled)
            assert (m['physicalGroupsApplied']>0)==enabled and (m['physicalWritesApplied']>0)==enabled
            site=next(s for s in d['sites'] if s['targetKind']=='COMPUTED')
            if enabled or not physical:
                assert [c['referenceName'] for c in site['candidates']]==[target],(name,suffix,site)
                assert site['modelValueRemainder'] is False
                assert site['valuePoint']['position']=='BEFORE'
                assert all(c['supports'] for c in site['candidates'])
            # A family copy result cannot be recovered by logical literal bookkeeping
            # in the physical-only publication: D must deliver actual region work.
            if physical and name=='partial-group-copy' and not enabled:assert site['candidates']==[]
            results.append({'case':name,'airSha256':sha(case/'air.json'),'mode':'EXPERIMENTAL_PHYSICAL' if enabled else 'LOGICAL_ONLY','physicalGroupsApplied':m['physicalGroupsApplied'],'physicalWritesApplied':m['physicalWritesApplied'],'candidates':[c['referenceName'] for c in site['candidates']],'expectedSupportedTarget':target,'valuePoint':site['valuePoint'],'supports':[c['supports'] for c in site['candidates']],'modelValueRemainder':site['modelValueRemainder'],'status':'PASS'})
        if physical:assert any(s['kind']=='region' for s in air['storage'])
        else:assert sp['storage']['logicalTextViews']
    return results

if __name__=='__main__':main()
