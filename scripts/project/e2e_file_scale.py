#!/usr/bin/env python3
"""FD-W11 authored scale witnesses through the existing immutable stage runner."""
import argparse,json
from pathlib import Path
from e2e_file_laws import one,native,cics
from carddemo_setup import check_snapshot
from dependency_wire import require


def source(dimension,n):
    if dimension=='uses':return native('READ F.\n'*n+'GOBACK.\n')
    if dimension=='layouts':return native('READ F.\nGOBACK.\n',declaration='FD F.\n'+''.join(f'01 R{i} PIC X({i+1}).\n' for i in range(n)))
    if dimension=='files':
        return ('IDENTIFICATION DIVISION.\nPROGRAM-ID. MANYFILES.\nENVIRONMENT DIVISION.\nINPUT-OUTPUT SECTION.\nFILE-CONTROL.\n'+
                ''.join(f'SELECT F{i} ASSIGN TO D{i:07}.\n' for i in range(n))+'DATA DIVISION.\nFILE SECTION.\n'+
                ''.join(f'FD F{i}.\n01 R{i} PIC X(8).\n' for i in range(n))+'PROCEDURE DIVISION.\n'+''.join(f'READ F{i}.\n' for i in range(n))+'GOBACK.\n')
    if dimension=='aliases':
        return cics(f'A{n-1}').replace('PROCEDURE DIVISION.', ''.join(f'01 A{i} REDEFINES FN PIC X(8).\n' for i in range(n))+"PROCEDURE DIVISION.\nMOVE 'ACCOUNTS' TO FN.")
    if dimension=='candidates':
        branches=''.join(f"IF CHOICE = '{i:04}' MOVE 'F{i:07}' TO FN\nELSE\n" for i in range(n-1))+f"MOVE 'F{n-1:07}' TO FN\n"+'END-IF\n'*(n-1)+'.\n'
        return cics('FN').replace('PROCEDURE DIVISION.','01 CHOICE PIC X(4).\nPROCEDURE DIVISION.\n'+branches)
    if dimension=='units':return ''.join(cics("'ACCOUNTS'").replace('CICSLAW',f'UNIT{i}')+f'END PROGRAM UNIT{i}.\n' for i in range(n))
    if dimension=='sort':
        return ('IDENTIFICATION DIVISION.\nPROGRAM-ID. MANYSORT.\nENVIRONMENT DIVISION.\nINPUT-OUTPUT SECTION.\nFILE-CONTROL.\nSELECT S ASSIGN TO SORTWK.\nSELECT O ASSIGN TO OUTDD.\n'+
                ''.join(f'SELECT F{i} ASSIGN TO D{i:07}.\n' for i in range(n))+'DATA DIVISION.\nFILE SECTION.\nSD S.\n01 SR.\n02 SK PIC X(8).\nFD O.\n01 ORC PIC X(8).\n'+
                ''.join(f'FD F{i}.\n01 R{i} PIC X(8).\n' for i in range(n))+'PROCEDURE DIVISION.\nSORT S ON ASCENDING KEY SK\nUSING\n'+''.join(f'F{i}\n' for i in range(n))+'GIVING O.\nGOBACK.\n')
    raise ValueError(dimension)


def oracle(dimension,n,r):
    f=r['dependency']['fileDependencies'];sites=f['sites'];decl=f['declarations'];names={c['referenceName'] for s in sites for c in s['candidates']}
    require(not r['dependency']['sites'],'no invented CALL from repeated FILE operations')
    if dimension=='files':require(len(decl)==n and len(sites)==n and names=={f'D{i:07}' for i in range(n)},'all files and uses retained')
    elif dimension=='uses':require(len(sites)==n and len({json.dumps(s['operation'],sort_keys=True) for s in sites})==n,'every occurrence survives')
    elif dimension=='layouts':require(len(decl)==1 and len(decl[0]['objects'])==n and len(sites)==1,'every FD record layout preserved')
    elif dimension=='aliases':require(names=={'ACCOUNTS'} and len(sites)==1 and 'FILE_MODEL_VALUE_REMAINDER' not in sites[0]['analysisReasons'],'last alias shares source value')
    elif dimension=='candidates':require(names=={f'F{i:07}' for i in range(n)} and len(sites)==1 and 'FILE_MODEL_VALUE_REMAINDER' not in sites[0]['analysisReasons'],'finite alternatives have no semantic cutoff')
    elif dimension=='units':require(len(r['sp']['units'])==n and len(sites)==n and len({s['owner']['localId'] for s in sites})==n,'all owners retain their local FILE')
    elif dimension=='sort':
        require(len(decl)==n+2 and names=={'OUTDD',*{f'D{i:07}' for i in range(n)}},'all SORT external participants')
        require(sum(b['role']=='input' for s in sites for b in s['bindings'])==n,'every SORT input role')
    require(all(c['supports'] for s in sites for c in s['candidates']),'source evidence survives scale')


def run(work,runtime,dimensions=None):
    work.mkdir(parents=True,exist_ok=False);c=json.loads(runtime.read_text());copybooks=work/'copybooks';copybooks.mkdir()
    for repo,pin in c['sources'].items():check_snapshot(Path(c['checkouts'][repo]),pin)
    report=[]
    for dimension in (dimensions or ('files','uses','layouts','aliases','candidates','units','sort')):
        for n in (1,8,32):
            path=work/f'{dimension}-{n}';r=one(path,c,source(dimension,n),copybooks);oracle(dimension,n,r)
            report.append({'dimension':dimension,'size':n,'result':'PASS','measurements':json.loads((path/'measurements.json').read_text())})
            (work/'scale.json').write_text(json.dumps(report,indent=2)+'\n');print('PASS',dimension,n,flush=True)
    for repo,pin in c['sources'].items():check_snapshot(Path(c['checkouts'][repo]),pin)
    print('PASS',len(report),'scale witnesses; observed time/RSS, no SLA or semantic maximum claimed.',flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--dimension',action='append');a=p.parse_args();run(a.work.resolve(),a.runtime.resolve(),a.dimension)
