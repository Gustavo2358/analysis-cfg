#!/usr/bin/env python3
"""Real source → SP → AIR → CFG → dependency regression for scoped PERFORM control.

Consumes an immutable stage/main/classpath/checkouts/sources runtime (carddemo_setup
format) and reviewed source/candidate oracles. Raw products and hashes are retained.
No source semantics are inferred here: source spans identify oracle CALL sites only.
"""
from __future__ import annotations
import argparse, concurrent.futures, hashlib, json, os, shutil, signal, subprocess, time
from pathlib import Path
from typing import Any
ROOT=Path(__file__).resolve().parents[2]/'analysis-adapters/src/test/resources/cp6/perform-completion'
STAGES=('frontend','lower','cfg','dependency')

def dump(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def sha(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024), b''):h.update(block)
    return h.hexdigest()

def key(value: Any) -> str:
    return json.dumps(value, sort_keys=True, separators=(',', ':'))

def written_spans(origin: Any, origins: dict[str,dict[str,Any]]) -> list[tuple[int,int]]:
    todo=[key(origin)];seen=set();spans=[]
    while todo:
        k=todo.pop()
        if k in seen:continue
        seen.add(k)
        if k not in origins:raise ValueError('Referenced origin is absent: '+k)
        o=origins[k]
        if o['kind']=='DERIVED':todo.extend(key(i) for i in o.get('inputs',[]))
        elif o['kind']=='WRITTEN':
            loc=o.get('location') or {}
            if loc.get('kind')=='LINE_COLUMNS':
                delta=1-int(loc.get('lineBase',1))
                start,end=int(loc['startLine'])+delta,int(loc['endLine'])+delta
                # A span ending at column zero/one of the next line excludes that line.
                if loc.get('endExclusive') and int(loc.get('endColumn',1))==int(loc.get('columnBase',1)) and end>start:
                    end-=1
                spans.append((start,end))
    return spans

def evaluate(case: dict[str,Any], doc: dict[str,Any]) -> dict[str,Any]:
    """Compare candidates AND materialized edges at each original CALL source line.

    Multiple specialized activations of one source CALL are aggregated only at
    that same source site. Remainders/PARTIAL do not excuse missing candidates.
    """
    if doc.get('schema')!='analysis-dependency-result':raise ValueError('Wrong dependency schema')
    for field in ('sites','edges','origins'):
        if not isinstance(doc.get(field),list):raise ValueError('Missing/wrong '+field)
    origins={key(o['id']):o for o in doc['origins']}
    grouped={tag:[] for tag in case['calls']};errors=[]
    for site in doc['sites']:
        if site.get('command')!='CALL':continue
        spans=written_spans(site['siteOrigin'],origins)
        possible=[tag for tag,spec in case['calls'].items() if any(a<=spec['line']<=b for a,b in spans)]
        exact=[tag for tag in possible if any(a==case['calls'][tag]['line'] for a,b in spans)]
        if len(exact)==1:possible=exact
        if len(possible)!=1:
            errors.append({'error':'AMBIGUOUS_OR_MISSING_SOURCE_MAPPING','operation':site.get('operation'),
                           'sourceSpans':spans,'possibleTags':possible})
            continue
        grouped[possible[0]].append(site)
    per_call={}
    for tag,spec in case['calls'].items():
        sites=grouped[tag]
        names={c['referenceName'] for s in sites for c in s.get('candidates',[])}
        operations={(key(s.get('entry')),key(s['operation'])) for s in sites}
        edges=[e for e in doc['edges'] if (key(e.get('entry')),key(e['site'])) in operations]
        edge_names={e['candidate']['referenceName'] for e in edges}
        required=set(spec['required']);allowed=set(spec['allowed']);forbidden=set(spec.get('forbidden',[]))
        failures=[]
        if spec.get('reachable',True):
            if not sites:failures.append('CALL_SITE_MISSING')
            elif not any(s.get('reachability')=='REACHABLE' for s in sites):failures.append('NO_REACHABLE_ACTIVATION')
        elif any(s.get('reachability')=='REACHABLE' for s in sites):
            failures.append('DEAD_CALL_REPORTED_REACHABLE')
        missing=sorted(required-names);missing_edges=sorted(required-edge_names)
        unexpected=sorted(names-allowed);unexpected_edges=sorted(edge_names-allowed)
        if missing:failures.append('MISSING_CANDIDATES')
        if missing_edges:failures.append('MISSING_EDGES')
        if unexpected or unexpected_edges:failures.append('UNEXPECTED_CANDIDATES_OR_EDGES')
        if (names|edge_names)&forbidden:failures.append('FORBIDDEN_TARGET')
        for site in sites:
            for candidate in site.get('candidates',[]):
                for support in candidate.get('supports',[]):
                    if not written_spans(support['origin'],origins):failures.append('SUPPORT_WITHOUT_SOURCE_PROVENANCE')
        if any(not c.get('supports') for s in sites for c in s.get('candidates',[]) if c['referenceName'] in required):
            failures.append('REQUIRED_CANDIDATE_WITHOUT_SUPPORT')
        per_call[tag]={'sourceLine':spec['line'],'status':'FAIL' if failures else 'PASS',
            'required':sorted(required),'allowed':sorted(allowed),'actual':sorted(names),
            'actualEdges':sorted(edge_names),'missing':missing,'missingEdges':missing_edges,
            'unexpected':unexpected,'unexpectedEdges':unexpected_edges,'failures':failures,
            'activations':[{k:s.get(k) for k in ('operation','entry','reachability','targetStatus',
                'analysisStatus','valuePoint','modelValueRemainder','sourceValueRemainder',
                'interpretationUnknownRemainder','effectiveUnknownRemainder','openControlRemainder')}
                for s in sites]}
    return {'status':'FAIL' if errors or any(c['status']=='FAIL' for c in per_call.values()) else 'PASS',
            'analysisStatus':doc.get('analysisStatus'),'sourceMappingErrors':errors,'calls':per_call}

def run_process(command: list[str], cwd: Path, name: str, timeout: float) -> dict[str,Any]:
    start=time.monotonic(); result={'command':command,'status':'NOT_STARTED'}
    with (cwd/(name+'.stdout')).open('wb') as out, (cwd/(name+'.stderr')).open('wb') as err:
        try:
            p=subprocess.Popen(command,cwd=cwd,stdout=out,stderr=err,start_new_session=True)
            try:
                code=p.wait(timeout=timeout)
                result.update(status='PASS' if code==0 else 'PROCESS_FAILED',exitCode=code)
            except subprocess.TimeoutExpired:
                try:os.killpg(p.pid,signal.SIGKILL)
                except ProcessLookupError:pass
                p.wait();result.update(status='TIMEOUT',exitCode=p.returncode)
        except OSError as exc:result.update(status='PROCESS_START_FAILED',error=str(exc))
    result['elapsedSeconds']=round(time.monotonic()-start,6)
    return result

def run_case(case:dict[str,Any],config:dict[str,Any],out:Path,timeout:float,java:str,heap:str) -> dict[str,Any]:
    source=(ROOT/case['source']).resolve()
    if sha(source)!=case['sha256']:raise ValueError('Fixture changed without oracle review: '+case['id'])
    work=out/case['id'];work.mkdir()
    cpy=work/'empty-copybooks';cpy.mkdir()
    resource=Path(config['checkouts']['proleap-poc'])/'src/main/resources/web'
    if not resource.is_dir():raise ValueError('Frontend UI resource directory absent: '+str(resource))
    link=work/'src/main/resources/web';link.parent.mkdir(parents=True);link.symlink_to(resource,target_is_directory=True)
    paths={'frontend':work/'sp'/config.get('semanticProductFile','cobol-semantic-product.json'),
           'lower':work/'program.air.json','cfg':work/'cfg.json','dependency':work/'dependencies.json'}
    # Deliberately no physical-storage profile and no experimental physical flag.
    # Synthetic fixtures are single-program fixed-format sources with no COPY.
    arguments={'frontend':['--source',str(source),'--copybooks',str(cpy),'--output',str(work/'sp')],
               'lower':[str(paths['frontend']),str(work/'dependency-input.json')],
               'cfg':[str(paths['lower']),str(paths['cfg'])],
               'dependency':[str(work/'dependency-input.json'),str(paths['dependency'])]}
    record={'id':case['id'],'sourceSha256':case['sha256'],'status':'NOT_RUN','stages':{}}
    for stage in STAGES:
        info=config[stage]
        command=[java,'-Xmx'+heap,'-cp',os.pathsep.join(info['classpath']),info['main'],*arguments[stage]]
        result=run_process(command,work,stage,timeout);record['stages'][stage]=result
        if stage=='lower' and result['status']=='PASS':
            bundle=json.loads((work/'dependency-input.json').read_text())
            for kind,name in (('air','program.air.json'),('qualifiedSource','source.json')):
                product=work/bundle[kind]['path']
                if sha(product)!=bundle[kind]['sha256']:raise ValueError('Bundle hash mismatch: '+kind)
                shutil.copyfile(product,work/name)
        result['artifactExists']=paths[stage].is_file()
        if result['artifactExists']:result['artifactSha256']=sha(paths[stage])
        if stage=='cfg':
            # The dependency command builds its CFG from AIR independently.
            # Preserve a strict CFG export refusal; do not skip the product oracle.
            continue
        if result['status']!='PASS' or not result['artifactExists']:
            record['status']='PIPELINE_FAILED';break
    if record.get('stages',{}).get('dependency',{}).get('status')=='PASS' and paths['dependency'].is_file():
        try:
            record['oracle']=evaluate(case,json.loads(paths['dependency'].read_text()))
            record['status']=record['oracle']['status']
            if record['status']=='PASS' and (record['stages']['cfg']['status']!='PASS' or not paths['cfg'].is_file()):
                record['status']='PRODUCT_PASS_CFG_EXPORT_FAILED'
        except (ValueError,KeyError,TypeError) as exc:
            record.update(status='ORACLE_ERROR',error=str(exc))
    # Preserve source-stage gaps for locating the first lost structural fact.
    if paths['frontend'].is_file():
        try:
            sp=json.loads(paths['frontend'].read_text());products=[u['product'] for u in sp['units']] if sp.get('schema')=='cobol-semantic-compilation' else [sp]
            record['performFacts']=[{'variant':s.get('variant'),'id':s.get('header',{}).get('id'),
                'gapCodes':s.get('gapCodes',[]),'normalContinuation':s.get('normalContinuation'),
                'start':s.get('start'),'end':s.get('end'),'procedures':s.get('procedures')}
                for product in products for s in product.get('statements',[])
                if 'PERFORM' in str(s.get('variant','')) or 'PERFORM' in str(s.get('observedKind',''))]
        except (ValueError,KeyError,TypeError) as exc:record['spInspectionError']=str(exc)
    dump(work/'result.json',record)
    return record

def main():
    global ROOT
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--work',type=Path,required=True)
    parser.add_argument('--fixtures',type=Path,default=ROOT)
    parser.add_argument('--manifest',default='expected.json')
    parser.add_argument('--java',default='java')
    parser.add_argument('--jobs',type=int,default=2)
    args=parser.parse_args();ROOT=args.fixtures.resolve();work=args.work.resolve();work.mkdir(parents=True)
    runtime=json.loads(args.runtime.read_text());cases=json.loads((ROOT/args.manifest).read_text())['cases']
    lock=json.loads((Path(__file__).resolve().parents[2]/'docs/sources/sources.lock.json').read_text())
    expected={'proleap-poc':lock['proleap_poc']['commit'],'cobol-lower':lock['cobol_lower']['commit']}
    for name,head in expected.items():
        if runtime['sources'][name]!=head:raise ValueError('Runtime differs from reviewed source pin: '+name)
    for name,checkout in runtime['checkouts'].items():
        if subprocess.check_output(['git','-C',checkout,'rev-parse','HEAD'],text=True).strip()!=runtime['sources'][name]:raise ValueError('Runtime checkout differs: '+name)
        if subprocess.check_output(['git','-C',checkout,'status','--porcelain'],text=True).strip():raise ValueError('Runtime checkout is dirty: '+name)
    hashes={str(p):sha(Path(p)) for stage in STAGES for p in runtime[stage]['classpath']}
    if 'artifactHashes' in runtime and hashes!=runtime['artifactHashes']:raise ValueError('Runtime artifact hashes differ')
    dump(work/'runtime.json',runtime);dump(work/'artifact-hashes.json',hashes)
    rows=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        for row in pool.map(lambda c:run_case(c,runtime,work,180,args.java,'1g'),cases):
            rows.append(row);dump(work/'results.json',{'runs':rows});print(row['id'],row['status'],flush=True)
    if hashes!={p:sha(Path(p)) for p in hashes}:raise ValueError('Runtime changed during execution')
    passed=sum(r['status']=='PASS' for r in rows);print(f'PERFORM_COMPLETION={passed}/{len(rows)}')
    return 0 if passed==len(rows) else 1

if __name__=='__main__':raise SystemExit(main())
