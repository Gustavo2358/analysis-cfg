"""Handwritten CICS acceptance sources and expected names, independent of producers."""


def source(data, code):
    text = "IDENTIFICATION DIVISION.\nPROGRAM-ID. CICSTEST.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n" + data + "\nPROCEDURE DIVISION.\n" + code
    assert all(len(line) <= 65 for line in text.splitlines()), 'fixed-format fixture width'
    return ''.join('       ' + line + '\n' for line in text.splitlines())


def fixtures():
    d = '01 WS-PGM PIC X(8).'
    def one(code, names=('PROGA',), **extra):
        return dict(source=source(d, code), names=list(names), books={}, mode='unknown', **extra)
    result = {
        'link-literal': one("EXEC CICS LINK PROGRAM('PROGA') END-EXEC.\nCALL 'AFTER'.\nGOBACK.", after=True),
        'before-overlap': one("MOVE 'PROGA' TO WS-PGM.\nEXEC CICS LINK PROGRAM(WS-PGM)\nCOMMAREA(WS-PGM) NOHANDLE END-EXEC.\nGOBACK.", closed_value=True),
        'after-overlap': one("MOVE 'PROGA' TO WS-PGM.\nEXEC CICS LINK PROGRAM(WS-PGM)\nCOMMAREA(WS-PGM) NOHANDLE END-EXEC.\nCALL WS-PGM.\nGOBACK.", call_open=True),
        'group': dict(source=source('01 WS-GROUP.\n05 P1 PIC X(4).\n05 P2 PIC X(4).', "MOVE 'PROGA' TO WS-GROUP.\nEXEC CICS LINK PROGRAM(WS-GROUP) NOHANDLE END-EXEC.\nGOBACK."), names=['PROGA'], books={}, mode='unknown'),
        'overlay': dict(source=source('01 WS-GROUP.\n05 P1 PIC X(4).\n05 P2 PIC X(4).\n01 WS-ALIAS REDEFINES WS-GROUP PIC X(8).', "MOVE 'XXXXXXXX' TO WS-GROUP.\nMOVE 'PROG' TO P1.\nMOVE 'A   ' TO P2.\nEXEC CICS LINK PROGRAM(WS-ALIAS) NOHANDLE END-EXEC.\nGOBACK."), names=['PROGA'], books={}, mode='unknown'),
        'renames': dict(source=source('01 WS-GROUP.\n05 P1 PIC X(4).\n05 P2 PIC X(4).\n66 WS-ALIAS RENAMES P1 THRU P2.', "MOVE 'PROGA' TO WS-GROUP.\nEXEC CICS LINK PROGRAM(WS-ALIAS) NOHANDLE END-EXEC.\nGOBACK."), names=['PROGA'], books={}, mode='unknown'),
        'if': dict(source=source('01 FLAG PIC X.\n'+d, "IF FLAG = 'Y'\nMOVE 'PROGA' TO WS-PGM\nELSE MOVE 'PROGB' TO WS-PGM\nEND-IF.\nEXEC CICS LINK PROGRAM(WS-PGM) NOHANDLE END-EXEC.\nGOBACK."), names=['PROGA','PROGB'], books={}, mode='unknown', closed_value=True),
        'duplicate': one("EXEC CICS LINK PROGRAM('PROGA') PROGRAM('PROGB')\nEND-EXEC.\nGOBACK.", names=(), gap='CICS_PROGRAM_DUPLICATED'),
        'unknown-option': one("EXEC CICS LINK PROGRAM('PROGA') MYSTERY(WS-PGM)\nEND-EXEC.\nGOBACK.", gap='CICS_UNMODELED_OPTION'),
        'digit-name': one("EXEC CICS LINK PROGRAM('1PGM') END-EXEC.\nGOBACK.", names=('1PGM',)),
        'case-preserved': one("EXEC CICS LINK PROGRAM('aBc') END-EXEC.\nGOBACK.", names=(), raw='aBc'),
        'mixed': one("CALL 'BEFORE'.\nEXEC CICS LINK PROGRAM('PROGA') NOHANDLE END-EXEC.\nEXEC CICS XCTL PROGRAM('PROGB') NOHANDLE END-EXEC.\nCALL 'AFTER'.\nGOBACK.", names=('PROGA','PROGB'), count=2, calls=2, after=True),
    }
    for suffix, option, mode, reached in [('default','','new-logical-level',False),('unknown','','unknown',True),('nohandle','NOHANDLE','new-logical-level',True),('resp','RESP(RC)','new-logical-level',True),('resp2','RESP2(RC)','new-logical-level',True)]:
        result['xctl-'+suffix]=dict(source=source(d+'\n01 RC PIC S9(8) COMP.', "EXEC CICS XCTL PROGRAM('PROGA')\n"+option+" END-EXEC.\nCALL 'AFTER'.\nGOBACK."),names=['PROGA'],books={},mode=mode,after=reached)
    result['handler']=one("EXEC CICS HANDLE CONDITION ERROR(ERR-PARA) END-EXEC.\nEXEC CICS XCTL PROGRAM('PROGA') END-EXEC.\nCALL 'AFTER'.\nGOBACK.\nERR-PARA.\nGOBACK.",after=True)
    for command in ['LINK','XCTL']:
        result['perform-'+command.lower()]=one("MAIN.\nPERFORM BODY-PARA THRU BODY-PARA.\nCALL 'AFTER'.\nGOBACK.\nBODY-PARA.\nEXEC CICS "+command+" PROGRAM('PROGA') NOHANDLE END-EXEC.\nMOVE 'PROGB' TO WS-PGM.",after=True,perform=True)
    result['link-paragraph']=dict(one("MAIN-PARA.\nEXEC CICS LINK PROGRAM('PROGA') END-EXEC.\nAFTER-PARA.\nCALL 'AFTER'.\nGOBACK.", after=True, link_return=True), mode='new-logical-level')
    result['link-unavailable']=dict(result['link-paragraph'],link_return=False,missing_return=True)
    for command in ['LINK','XCTL']:
        key='perform-last-'+command.lower()
        result[key]=one("MAIN.\nPERFORM BODY-PARA THRU BODY-PARA.\nCALL 'AFTER'.\nGOBACK.\nBODY-PARA.\nEXEC CICS "+command+" PROGRAM('PROGA') NOHANDLE END-EXEC.\nUNPERFORMED.\nCALL 'OUTSIDE'.\nGOBACK.", after=True, perform=True, activation_return=True)
        result[key+'-disabled']=dict(result[key], mode='disabled', names=[], count=0, perform=False, perform_disabled=True, observed=1)
    for suffix, data, target in [('short','01 WS-PGM PIC X(5).','WS-PGM'),('dynamic',d+'\n01 IDX PIC 9.','WS-PGM(IDX:8)'),('codec','01 WS-PGM PIC N(8).','WS-PGM')]:
        result[suffix]=dict(source=source(data,"EXEC CICS LINK PROGRAM("+target+") END-EXEC.\nGOBACK."),names=[],books={},mode='unknown',unreadable=True)
    result['slice']=dict(source=source('01 WS-PGM PIC X(10).',"MOVE 'XXPROGA   ' TO WS-PGM.\nEXEC CICS LINK PROGRAM(WS-PGM(3:8)) NOHANDLE END-EXEC.\nGOBACK."),names=['PROGA'],books={},mode='unknown',closed_value=True)
    result['qualified-copy']=dict(source=source('COPY CICSDEF.',"MOVE 'PROGA' TO WS-PGM OF FIRST-GROUP.\nCOPY CICSBODY.\nGOBACK."), names=['PROGA'], mode='unknown',copy=True,books={
        'CICSDEF.cpy':'       01 FIRST-GROUP.\n       05 WS-PGM PIC X(8).\n       01 SECOND-GROUP.\n       05 WS-PGM PIC X(8).\n',
        'CICSBODY.cpy':'           EXEC CICS LINK PROGRAM(WS-PGM OF FIRST-GROUP)\n           NOHANDLE END-EXEC.\n'})
    result['renamed-format']=one("move 'PROGA' to RENAMED-PGM.\nexec cics link NOHANDLE\nPROGRAM(RENAMED-PGM) COMMAREA(RENAMED-PGM) end-exec.\nGOBACK.",closed_value=True)
    result['renamed-format']['source']=result['renamed-format']['source'].replace('01 WS-PGM','01 RENAMED-PGM')
    result['disabled']=dict(result['mixed'],mode='disabled',names=[],count=0,calls=2)
    return result
