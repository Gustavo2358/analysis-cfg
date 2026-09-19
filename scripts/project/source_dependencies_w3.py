#!/usr/bin/env python3
"""Synthetic source -> SP -> AIR -> CFG/dependencies qualification, using real CLIs.

Runtime JSON uses the existing frontend/lower/cfg/dependency classpath/main entries
and checkouts.proleap-poc. Scale additionally uses sourceTiming (test-only probe).
No output is modified to satisfy an oracle. Corporate inputs are not part of this tool.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
from dependency_wire import read


def execute(runtime, stage, arguments, directory):
    config=runtime[stage]
    command=['java','-Xmx1g','-cp',':'.join(config['classpath']),config['main'],*map(str,arguments)]
    start=time.monotonic()
    with (directory/(stage+'.log')).open('w') as log:
        result=subprocess.run(command,cwd=runtime['checkouts']['proleap-poc'],stdout=log,stderr=subprocess.STDOUT,timeout=180)
    return dict(exit=result.returncode,seconds=time.monotonic()-start,command=command)


def verify_source(case, product, expected):
    facts=product['sourceDependencies']; actual={}
    for fact in facts['dependencies']:
        actual.setdefault(fact['kind'],{})[fact['name']]=len(fact['supports'])
    assert actual==expected,(case,actual,expected)
    assert facts['available'],case
    metrics=product['metrics']
    assert [metrics[k] for k in ('logicalOnlyMode','experimentalPhysicalMode','physicalGroupsApplied','physicalWritesApplied')]==[1,0,0,0]
    unresolved=case in {'copy-missing','copy-qualified','dclgen-missing','sqlca-not-dclgen','sqlda-not-dclgen','unresolved-include'}
    origins={o['id']['localId']:o for o in product['origins']}
    artifacts={a['id']['localId']:a['logicalName'] for a in product['artifacts']}
    rows=[]
    for fact in facts['dependencies']:
        actual_lines=[]
        for support in fact['supports']:
            assert support['resolution']==('UNRESOLVED' if unresolved else 'RESOLVED')
            assert support['resolvedArtifact'] if not unresolved else not support['resolvedArtifact']
            nested=case in ('copy-nested','dclgen-inside-copybook') and fact['name']!='A'
            assert support['relationship']==('TRANSITIVE' if nested else 'DIRECT')
            assert artifacts[support['sourceOwner']['localId']]==('A.cpy' if nested else 'program.cbl')
            origin=origins[support['origin']['localId']]
            written=[origins[i['localId']] for i in origin['inputs'] if origins[i['localId']]['kind']=='WRITTEN' and origins[i['localId']]['artifact']==support['sourceOwner']]
            assert len(written)==1
            assert int(written[0]['location']['startColumn'])==7
            actual_lines.append(int(written[0]['location']['startLine']))
        if case=='composition':lines=[12 if fact['kind']=='COPYBOOK' else 13]
        elif case in ('copy-repeated','copy-case','dclgen-repeated','copy-qualified'):lines=[5,6]
        elif case in ('copy-two','dclgen-two'):lines=[5 if fact['name'] in ('CPY001','DCLCLI') else 6]
        elif nested:lines=[1]
        else:lines=[5]
        assert sorted(actual_lines)==lines,(case,fact['name'],actual_lines,lines)
        rows.append(dict(kind=fact['kind'],name=fact['name'],supports=len(fact['supports']),resolved=not unresolved,remainder=fact['remainder']))
    assert facts['remainder']==(unresolved or case=='malformed-sql-include')
    if case=='malformed-sql-include':assert 'SQL_INCLUDE_FORM_UNPROVED' in facts['gapCodes']
    if case=='composition':
        assert {c['referenceName'] for s in product['sites'] for c in s['candidates']}=={'SUBA'}
        assert any(d['name']=='DD001' for d in product['fileDependencies']['declarations'])
    return rows


def matrix(runtime, fixtures, work):
    rows=[]
    for case in sorted(fixtures.iterdir()):
        if not (case/'expected.json').exists():continue
        output=work/case.name;output.mkdir();phases={}
        stages=[('frontend',['--source',case/'program.cbl','--copybooks',case/'copybooks','--source-inventory',case/'inventory.json','--output',output/'front']),
                ('lower',[output/'front/cobol-semantic-product.json',output/'air.json']),
                ('cfg',[output/'air.json',output/'cfg.json']),('dependency',[output/'air.json',output/'dependencies.json'])]
        for stage,args in stages:
            phases[stage]=execute(runtime,stage,args,output)
            if phases[stage]['exit']:break
        row=dict(fixture=case.name,phases=phases)
        if case.name=='copy-cycle':
            assert phases['frontend']['exit']==0 and phases['lower']['exit']==4
            sp=json.loads((output/'front/cobol-semantic-product.json').read_text())['sourceDependencies']
            assert len(sp['occurrences'])==3 and any(o['resolution']=='CYCLIC' for o in sp['occurrences'])
            row.update(status='BLOCKED_EXISTING_ENTRY',limit='Cyclic preprocessing invalidates usable primary runtime entry; no E2E PASS claimed.')
        else:
            assert len(phases)==4 and all(p['exit']==0 for p in phases.values()),row
            product=read(output/'dependencies.json')
            row.update(status='PASS',acceptance=verify_source(case.name,product,json.loads((case/'expected.json').read_text())),
                       sha256=hashlib.sha256((output/'dependencies.json').read_bytes()).hexdigest())
        rows.append(row);(work/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(case.name,row['status'],flush=True)
    return rows


def scale(runtime,work):
    rows=[]
    for kind in ('COPYBOOK','DCLGEN'):
        for count in (10,100,1000):
            output=work/(kind.lower()+'-'+str(count));output.mkdir();(output/'copybooks').mkdir()
            names=['M'+str(i).zfill(5) for i in range(count)]
            for name in names:(output/'copybooks'/(name+'.cpy')).write_text('')
            directives=[('COPY '+n+'.') if kind=='COPYBOOK' else ('EXEC SQL INCLUDE '+n+' END-EXEC') for n in names]
            source=['IDENTIFICATION DIVISION.','PROGRAM-ID. SCALEPGM.','DATA DIVISION.','WORKING-STORAGE SECTION.',*directives,'PROCEDURE DIVISION.','    GOBACK.']
            (output/'program.cbl').write_text(''.join('       '+line+'\n' for line in source))
            (output/'inventory.json').write_text(json.dumps({'version':'1.0.0','artifacts':[] if kind=='COPYBOOK' else [{'name':n,'kind':'DCLGEN','artifact':'copybooks/'+n+'.cpy'} for n in names]}))
            row=dict(kind=kind,occurrences=count,unique=count,phases={})
            for stage,args in [('frontend',['--source',output/'program.cbl','--copybooks',output/'copybooks','--source-inventory',output/'inventory.json','--output',output/'front']),('lower',[output/'front/cobol-semantic-product.json',output/'air.json']),('dependency',[output/'air.json',output/'dependencies.json']),('sourceTiming',[output/'air.json'])]:
                phase=execute(runtime,stage,args,output);row['phases'][stage]=phase;assert phase['exit']==0,row
            product=read(output/'dependencies.json');facts=product['sourceDependencies']
            assert {d['name'] for d in facts['dependencies']}==set(names) and facts['occurrences']==count and not facts['remainder']
            assert product['metrics']['physicalGroupsApplied']==product['metrics']['physicalWritesApplied']==0
            row.update(json.loads((output/'sourceTiming.log').read_text()))
            row.update(airBytes=(output/'air.json').stat().st_size,jsonBytes=(output/'dependencies.json').stat().st_size)
            rows.append(row);(work/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(kind,count,row['aggregationNanosMedian'],flush=True)
    return rows


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['matrix','scale']);parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--fixtures',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();runtime=json.loads(args.runtime.read_text());work=args.output.resolve();work.mkdir(parents=True,exist_ok=False)
    if args.mode=='matrix':
        fixtures=args.fixtures or Path(runtime['checkouts']['proleap-poc'])/'src/test/resources/cobol/source-dependencies-w3'
        matrix(runtime,fixtures.resolve(),work)
    else:scale(runtime,work)
