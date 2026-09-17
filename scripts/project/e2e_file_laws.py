#!/usr/bin/env python3
"""FD-W11 manual source laws; uses the existing stage runner and frozen pipeline."""
import argparse
from collections import Counter
import contextlib
import json
import os
from pathlib import Path
import sys

from carddemo_baseline import execute_stage
from carddemo_setup import check_snapshot
from dependency_wire import read, require
from cfg_wire_contract import verify

BODY = """MOVE 'KEEP' TO PGM.
OPEN INPUT F.
READ F AT END CONTINUE END-READ.
CALL PGM.
CLOSE F.
GOBACK.
"""


def native(body=BODY, declaration='FD F.\n01 R PIC X(8).'):
    return """IDENTIFICATION DIVISION.
PROGRAM-ID. FILELAW.
ENVIRONMENT DIVISION.
INPUT-OUTPUT SECTION.
FILE-CONTROL.
SELECT F ASSIGN TO CLIENTDD.
DATA DIVISION.
FILE SECTION.
"""+declaration+"""
WORKING-STORAGE SECTION.
01 PGM PIC X(8).
01 DISJOINT PIC X(8).
PROCEDURE DIVISION.
"""+body


def cics(target):
    return """IDENTIFICATION DIVISION.
PROGRAM-ID. CICSLAW.
DATA DIVISION.
WORKING-STORAGE SECTION.
01 FN PIC X(8).
PROCEDURE DIVISION.
EXEC CICS ENDBR FILE("""+target+""") NOHANDLE END-EXEC.
GOBACK.
"""


def write_from(explicit):
    return """IDENTIFICATION DIVISION.
PROGRAM-ID. FROMTEST.
ENVIRONMENT DIVISION.
INPUT-OUTPUT SECTION.
FILE-CONTROL.
SELECT F ASSIGN TO CLIENTDD.
SELECT G ASSIGN TO DONORDD.
DATA DIVISION.
FILE SECTION.
FD F.
01 R PIC X(8).
FD G.
01 S PIC X(8).
PROCEDURE DIVISION.
MOVE 'DONOR' TO S.
"""+('MOVE S TO R.\nWRITE R.\n' if explicit else 'WRITE R FROM S.\n')+"CALL R.\nCALL S.\nGOBACK.\n"


@contextlib.contextmanager
def environment(changes):
    old={k:os.environ.get(k) for k in changes}
    os.environ.update(changes)
    try:yield
    finally:
        for k,v in old.items():
            if v is None:os.environ.pop(k,None)
            else:os.environ[k]=v


def one(work, config, source, copybooks, *, env=None, trace=False):
    work.mkdir(parents=True,exist_ok=False)
    (work/'program.cbl').write_text(''.join('       '+line+'\n' for line in source.splitlines()))
    web=work/'src/main/resources';web.mkdir(parents=True)
    (web/'web').symlink_to(Path(config['checkouts']['proleap-poc'])/'src/main/resources/web',target_is_directory=True)
    outputs={'frontend':work/'sp/cobol-semantic-compilation.json','lower':work/'program.air.json',
             'cfg':work/'cfg.json','dependency':work/'dependencies.json'}
    args={'frontend':['--source','program.cbl','--copybooks',str(copybooks),'--output',str(work/'sp'),*config['frontendArguments']],
          'lower':[str(outputs['frontend']),str(outputs['lower'])],
          'cfg':[str(outputs['lower']),str(outputs['cfg'])],
          'dependency':[str(outputs['lower']),str(outputs['dependency'])]}
    measurements={}
    with environment(env or {}):
        for stage in args:
            c=config[stage];command=['java','-Xmx2g','-cp',os.pathsep.join(c['classpath']),c['main'],*args[stage]]
            if trace:command=['strace','-f','-e','trace=file','-o',str(work/(stage+'.file-access.trace')),*command]
            result=execute_stage(stage,command,work,120,measure_resources=True);measurements[stage]=result
            (work/'measurements.json').write_text(json.dumps(measurements,indent=2)+'\n')
            require(result['exitCode']==0,'real CLI '+stage+' '+str(work))
    verify(outputs['cfg'].read_bytes());dependency=read(outputs['dependency'])
    return {'sp':json.loads(outputs['frontend'].read_bytes()),'air':json.loads(outputs['lower'].read_bytes()),
            'dependency':dependency,'bytes':[p.read_bytes() for p in outputs.values()]}


def scope_guard(value):
    if isinstance(value,dict):
        require(not ({'bindingMechanism','physicalResource','physicalResolution','runtimeAllocation','DSNAME'}&value.keys()),'SG1/SG4 external domain absent')
        for child in value.values():scope_guard(child)
    elif isinstance(value,list):
        for child in value:scope_guard(child)


def projection(d):
    f=d['fileDependencies']
    calls=sorted((s['command'],s['targetKind'],s['reachability'],tuple(sorted(c['referenceName'] for c in s['candidates']))) for s in d['sites'])
    files=sorted((s['namespace'],s['action'],s['reachability'],s['targetKind'],tuple(sorted(c['referenceName'] for c in s['candidates']))) for s in f['sites'])
    return calls,files


def native_oracle(result, actions=('open','read','close'), calls=('KEEP',), declarations=('CLIENTDD',)):
    d=result['dependency'];scope_guard(d);f=d['fileDependencies']
    require({x['name'] for x in f['declarations']}==set(declarations),'manual assignment-name declarations')
    require(all(x['sourceKind']=='ASSIGNMENT_NAME' and x['namespace']=='cobol.external-file-name' for x in f['declarations']),'IBM source names do not assert DD binding')
    require(Counter(s['action'] for s in f['sites'])==Counter(actions),'manual operation inventory')
    require({c['referenceName'] for s in d['sites'] for c in s['candidates']}==set(calls),'CALL candidates per source')
    require(all(c['supports'] for s in d['sites'] for c in s['candidates']),'CALL support retained')
    require(all(s['targetKind']=='LITERAL' and not s['unknownRemainder'] and {c['referenceName'] for c in s['candidates']}=={'CLIENTDD'} for s in f['sites']),'literal name proof is independent of runtime/effect uncertainty')
    require(all(c['supports'] for s in f['sites'] for c in s['candidates']),'FILE source support retained')


def run(work,runtime,guards_only=False):
    config=json.loads(runtime.read_text());work.mkdir(parents=True,exist_ok=False)
    for repo,pin in config['sources'].items():check_snapshot(Path(config['checkouts'][repo]),pin)
    copybooks=work/'copybooks';copybooks.mkdir();(copybooks/'FDECL.cpy').write_text('       FD F.\n       01 R PIC X(8).\n')
    def case(name,source,**kwargs):
        result=one(work/name,config,source,copybooks,**kwargs);scope_guard(result['dependency']);return result
    base=case('base',native());native_oracle(base)
    if not guards_only:
        rename=native().replace('SELECT F ','SELECT ACCT ').replace('FD F.','FD ACCT.').replace('01 R ','01 REC ').replace('INPUT F.','INPUT ACCT.').replace('READ F ','READ ACCT ').replace('CLOSE F.','CLOSE ACCT.')
        parts=native().split("'");formatted="'".join(p.lower() if i%2==0 else p for i,p in enumerate(parts))
        variants={'MR1':rename,'MR2':formatted,'MR3':native(declaration='COPY FDECL.'),
                  'MR5':native(BODY.replace('READ F',"MOVE 'TOUCHED' TO DISJOINT.\nREAD F")),
                  'MR6':native(BODY.replace('READ F','GO TO NEXT-P.\nNEXT-P.\nREAD F')),
                  'MR9':native(BODY.replace('GOBACK.',"DISPLAY 'UNKNOWN OPERATION'.\nGOBACK."))}
        for name,source in variants.items():
            result=case(name,source);native_oracle(result)
            require(projection(result['dependency'])==projection(base['dependency']),name+' preserves targets, actions, CALL and reachability')
            if name=='MR3':require(any(o['includeChain'] for o in result['sp']['units'][0]['product']['fileInventory']['declarations'][0]['origins']),'COPY provenance is explicit')
            print('PASS',name,flush=True)
        duplicate=case('MR4',native(BODY.replace('READ F AT END CONTINUE END-READ.','READ F AT END CONTINUE END-READ.\nREAD F AT END CONTINUE END-READ.')))
        native_oracle(duplicate,actions=('open','read','read','close'))
        uses=duplicate['dependency']['fileDependencies']['sites'];require(len({json.dumps(s['operation'],sort_keys=True) for s in uses})==4,'new occurrence has a distinct operation')
        edges=duplicate['dependency']['fileDependencies']['edges'];require(len(edges)==4 and len({json.dumps(e['site'],sort_keys=True) for e in edges})==4,'one FILE edge per reachable occurrence, no accidental duplicates');print('PASS MR4',flush=True)
        before=case('MR7-from',write_from(False));after=case('MR7-move',write_from(True))
        for r in (before,after):
            native_oracle(r,actions=('write',),calls=('DONOR',),declarations=('CLIENTDD','DONORDD'))
            require(len(r['dependency']['sites'])==2 and all({c['referenceName'] for c in s['candidates']}=={'DONOR'} for s in r['dependency']['sites']),'both receiver and donor CALL evidence observe equivalent transfer/effects')
        require(projection(before['dependency'])==projection(after['dependency']),'MR7 exact disjoint PIC X(8) transfer equivalence');print('PASS MR7',flush=True)
        for name in ('ACCOUNTS','CUSTOMER'):
            r=case('MR8-'+name,cics("'"+name+"'"));f=r['dependency']['fileDependencies']
            require(not f['declarations'] and len(f['sites'])==1 and {c['referenceName'] for c in f['sites'][0]['candidates']}=={name},'literal CICS target identity')
            require(f['sites'][0]['namespace']=='cics.file' and not r['dependency']['sites'],'CICS FILE distinct from CALL')
        print('PASS MR8',flush=True)
    sentinel=work/'business-file-must-not-be-opened';sentinel.write_text('outside source boundary\n')
    changed=case('SG3-environment',native(),env={'CLIENTDD':str(sentinel),'DD_CLIENTDD':str(sentinel)},trace=True)
    native_oracle(changed);require(base['bytes']==changed['bytes'],'environment cannot change source products')
    traces='\n'.join(p.read_text() for p in (work/'SG3-environment').glob('*.file-access.trace'))
    require('openat(' in traces and 'program.cbl' in traces and str(sentinel) not in traces,'SG2 native guard: active file tracing, zero business resource access')
    for label,value in [('A','ACCOUNTS'),('B','CUSTOMER')]:
        r=case('SG5-'+label,cics('FN'),env={'FN':value,'CICS_FILE':value});f=r['dependency']['fileDependencies']['sites'][0]
        require(not f['candidates'] and f['unknownRemainder'] and f['targetKind']=='COMPUTED','unknown source input is not resolved by environment')
        if label=='A':unknown=r
        else:require(unknown['bytes']==r['bytes'],'SG5 deterministic unknown across environments')
    for repo,pin in config['sources'].items():check_snapshot(Path(config['checkouts'][repo]),pin)
    print('PASS SG1/SG4; SG2 native file-access guard; SG3/SG5. SG2 path-literal D remains NOT_AUTHORIZED.',flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--guards-only',action='store_true');a=p.parse_args()
    try:run(a.work.resolve(),a.runtime.resolve(),a.guards_only)
    except (ValueError,RuntimeError,OSError) as error:print('FAIL:',error,file=sys.stderr);sys.exit(1)
