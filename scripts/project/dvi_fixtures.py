"""Handwritten DVI candidates/proofs; no golden is derived from product output."""
VALUE="01 LIT-PGM PIC X(8) VALUE 'PROGA'.\n"
GROUP="01 WS-AREA.\n05 LIT-PGM PIC X(8) VALUE 'PROGA'.\n05 TAIL-PART PIC X(8).\n"
TARGET="01 TARGET-PGM PIC X(8).\n"
RUNTIME="01 INPUT-PGM PIC X(8).\n01 FLAG PIC X.\n"

def source(data,code,initial=False):
    text="IDENTIFICATION DIVISION.\nPROGRAM-ID. DVI-PROGRAM"+(' IS INITIAL' if initial else '')+".\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n"+data+"PROCEDURE DIVISION.\n"+code+"\nGOBACK.\n"
    return ''.join('       '+line+'\n' for line in text.splitlines())

def fixtures():
    cases={}
    def add(name,data,code,proof='DECLARATIVE_INVARIANT',names=('PROGA',),mode='unknown',initial=False,**kw):
        cases[name]=dict(source=source(data,code,initial),proof=proof,names=list(names),mode=mode,**kw)
    add('invariant-call',VALUE,'CALL LIT-PGM.')
    add('explicit-initial',VALUE,'CALL LIT-PGM.',proof='EXPLICIT_INITIAL',mode='initial')
    add('explicit-preserved',VALUE,'CALL LIT-PGM.',proof='EXPLICIT_PRESERVED',mode='preserved',names=())
    add('program-initial',VALUE,'CALL LIT-PGM.',proof='PROGRAM_INITIAL',initial=True)
    add('data-move-call',VALUE+TARGET,'MOVE LIT-PGM TO TARGET-PGM.\nCALL TARGET-PGM.',copy=True)
    for command in ['LINK','XCTL']:
        add('data-move-'+command.lower(),VALUE+TARGET,'MOVE LIT-PGM TO TARGET-PGM.\nEXEC CICS '+command+' PROGRAM(TARGET-PGM) NOHANDLE END-EXEC.',copy=True,command=command)
    add('runtime-branch',VALUE+TARGET+RUNTIME,"IF FLAG = 'Y'\nMOVE LIT-PGM TO TARGET-PGM\nELSE\nMOVE INPUT-PGM TO TARGET-PGM\nEND-IF.\nCALL TARGET-PGM.",open=True)
    add('runtime-cics',VALUE+TARGET+RUNTIME,"IF FLAG = 'Y'\nMOVE LIT-PGM TO TARGET-PGM\nELSE\nMOVE INPUT-PGM TO TARGET-PGM\nEND-IF.\nEXEC CICS XCTL PROGRAM(TARGET-PGM) NOHANDLE END-EXEC.",open=True,command='XCTL')
    add('direct-write',VALUE+RUNTIME,'CALL LIT-PGM.\nMOVE INPUT-PGM TO LIT-PGM.',proof='NONE',names=())
    add('disjoint-write',VALUE+TARGET,"MOVE 'OTHER' TO TARGET-PGM.\nCALL LIT-PGM.")
    add('group-write',GROUP+'01 INPUT-AREA PIC X(16).\n','MOVE INPUT-AREA TO WS-AREA.\nCALL LIT-PGM.',proof='NONE',names=())
    add('alias-write',GROUP+'01 ALIAS-AREA REDEFINES WS-AREA PIC X(16).\n01 INPUT-AREA PIC X(16).\n','MOVE INPUT-AREA TO ALIAS-AREA.\nCALL LIT-PGM.',proof='NONE',names=())
    add('renames-write',GROUP+'66 ALIAS-PGM RENAMES LIT-PGM.\n'+RUNTIME,'MOVE INPUT-PGM TO ALIAS-PGM.\nCALL LIT-PGM.',proof='NONE',names=())
    add('slice-disjoint',GROUP,"MOVE 'X' TO WS-AREA(9:1).\nCALL LIT-PGM.")
    add('slice-overlap',GROUP,"MOVE 'X' TO WS-AREA(8:1).\nCALL LIT-PGM.",proof='NONE',names=())
    add('slice-dynamic',GROUP+'01 POS PIC 9.\n',"MOVE 'X' TO WS-AREA(POS:1).\nCALL LIT-PGM.",proof='NONE',names=())
    add('unknown-effect',VALUE,'ACCEPT LIT-PGM.\nCALL LIT-PGM.',proof='NONE',names=())
    add('escape',VALUE,"CALL 'OTHER' USING LIT-PGM.\nCALL LIT-PGM.",proof='NONE',names=(),computed_only=True)
    add('loop-kill',VALUE+RUNTIME,"MAIN.\nMOVE 'OTHERPGM' TO LIT-PGM.\nIF FLAG = 'Y' GO TO MAIN END-IF.\nCALL LIT-PGM.",proof='PROGRAM_INITIAL',initial=True,names=('OTHERPGM',),killed=True)
    add('renamed-format',GROUP,"MOVE 'X' TO WS-AREA(9:1).\nCALL LIT-PGM.")
    cases['renamed-format']['source']=cases['renamed-format']['source'].replace('LIT-PGM','RENAMED-PGM').replace('WS-AREA','RENAMED-AREA').replace('MOVE','move').replace('CALL','call')
    add('incomplete-input','COPY DVI-MISSING.\n'+VALUE,'CALL LIT-PGM.',proof='NONE',names=())
    alias=GROUP+'01 ALIAS-AREA REDEFINES WS-AREA PIC X(16).\n01 INPUT-AREA PIC X(16).\n'
    add('two-blockers',alias,'ACCEPT TAIL-PART.\nMOVE INPUT-AREA TO ALIAS-AREA.\nCALL LIT-PGM.',proof='NONE',names=())
    add('remove-one-blocker',alias,'MOVE INPUT-AREA TO ALIAS-AREA.\nCALL LIT-PGM.',proof='NONE',names=())
    add('remove-all-blockers',alias,'CALL LIT-PGM.')
    return cases
