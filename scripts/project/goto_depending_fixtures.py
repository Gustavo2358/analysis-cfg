"""Finite multiplicity is a semantic invariant, including hundreds of destinations."""
CARDINALITIES=(1,2,5,40,100,200,255)

def source(body,integer='9(4)'):
    raw='IDENTIFICATION DIVISION.\nPROGRAM-ID. DEPENDTEST.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n'
    raw+=f'01 WS-IDX PIC {integer}.\n01 WS-PGM PIC X(8).\n01 FLAG PIC X.\nPROCEDURE DIVISION.\n'+body
    return ''.join('       '+line+'\n' for line in raw.splitlines())

def dispatch(targets=('TARGET-A','TARGET-B','TARGET-C'),selector='WS-IDX'):
    return 'GO TO\n'+'\n'.join(targets)+'\nDEPENDING ON '+selector+'.\n'

def targets():
    return "TARGET-A.\nMOVE 'PROGA' TO WS-PGM.\nGO TO JOIN-PARA.\nTARGET-B.\nMOVE 'PROGB' TO WS-PGM.\nGO TO JOIN-PARA.\nTARGET-C.\nMOVE 'PROGC' TO WS-PGM.\n"
JOIN='JOIN-PARA.\nCALL WS-PGM.\nGOBACK.\n'
FALL="MOVE 'FALLPGM' TO WS-PGM.\nGO TO JOIN-PARA.\n"

def fixtures():
    result={}
    def add(name,body,order=('TARGET-A','TARGET-B','TARGET-C'),values=('FALLPGM','PROGA','PROGB','PROGC'),partial=False,integer='9(4)',**extra):
        result[name]=dict(source=source(body,integer),order=list(order),values=None if values is None else sorted(values),partial=partial,**extra)
    base='MAIN-PARA.\n'+dispatch()+'FALLTHROUGH.\n'+FALL+targets()+JOIN
    add('d1',base)
    add('d2',base.replace('TARGET-A.\n',"DEAD-PARA.\nMOVE 'BADPROG' TO WS-PGM.\nGO TO JOIN-PARA.\nTARGET-A.\n",1))
    add('d3','MAIN-PARA.\n'+dispatch(('TARGET-C','TARGET-A','TARGET-B'))+FALL+targets()+JOIN,order=('TARGET-C','TARGET-A','TARGET-B'))
    add('d4','MAIN-PARA.\n'+dispatch(('TARGET-A','TARGET-B','TARGET-A','TARGET-C'))+FALL+targets()+JOIN,order=('TARGET-A','TARGET-B','TARGET-A','TARGET-C'))
    add('d5','MAIN-PARA.\n'+dispatch(('TARGET-A','UNKNOWN','TARGET-C'))+FALL+targets()+JOIN,order=('TARGET-A','UNKNOWN','TARGET-C'),values=None,partial=True)
    add('d6',base.replace('DEPENDING ON WS-IDX','DEPENDING ON UNKNOWN-IDX'),values=None,partial=True,selector=False)
    add('d7',base,integer='S9(4)')
    add('d8',base.replace('MAIN-PARA.\n','MAIN-PARA.\nMOVE 2 TO WS-IDX.\n'))
    nested=dispatch().rstrip('.\n')+'\n'
    add('d9',"MAIN-PARA.\nIF FLAG = 'Y'\n"+nested+'END-IF.\n'+FALL+targets()+JOIN)
    add('d10',"MAIN-PARA.\nEVALUATE FLAG\nWHEN 'Y'\n"+nested+'WHEN OTHER CONTINUE\nEND-EVALUATE.\n'+FALL+targets()+JOIN,values=None)
    # Every branch contains supported statements; OTHER writes a real value, then normal FALL overwrites it.
    result['d10']['source']=result['d10']['source'].replace('WHEN OTHER CONTINUE',"WHEN OTHER MOVE 'OTHER' TO WS-PGM")
    result['d10']['values']=sorted(('FALLPGM','PROGA','PROGB','PROGC'))
    add('d11','MAIN-PARA.\nGO TO DISPATCH-PARA.\n'+targets()+'GO TO JOIN-PARA.\nDISPATCH-PARA.\n'+dispatch()+FALL+JOIN)
    add('d12',"MAIN-PARA.\nGO TO DISPATCH-PARA.\nTARGET-A.\nMOVE 'PROGA' TO WS-PGM.\nGO TO JOIN-PARA.\nTARGET-C.\nMOVE 'PROGC' TO WS-PGM.\nGO TO JOIN-PARA.\nDISPATCH-PARA.\n"+dispatch(('TARGET-A','TARGET-B','TARGET-C','TARGET-D'))+FALL+"TARGET-B.\nMOVE 'PROGB' TO WS-PGM.\nGO TO JOIN-PARA.\nTARGET-D.\nMOVE 'PROGD' TO WS-PGM.\n"+JOIN,order=('TARGET-A','TARGET-B','TARGET-C','TARGET-D'),values=('FALLPGM','PROGA','PROGB','PROGC','PROGD'))
    add('cycle',"MAIN-PARA.\nMOVE 'BASE' TO WS-PGM.\nGO TO DISPATCH-PARA.\nTARGET-A.\nMOVE 'PROGA' TO WS-PGM.\nGO TO DISPATCH-PARA.\nTARGET-B.\nGO TO JOIN-PARA.\nDISPATCH-PARA.\n"+dispatch(('TARGET-A','TARGET-B'))+FALL+JOIN,order=('TARGET-A','TARGET-B'),values=('BASE','PROGA','FALLPGM'))
    for n in (*CARDINALITIES,256):
        order=tuple('TARGET-'+str(i) for i in range(n))
        add('capacity-'+str(n),'MAIN-PARA.\n'+dispatch(order)+FALL+''.join(p+".\nGO TO JOIN-PARA.\n" for p in order)+JOIN,order=order,values=None,partial=n>255)
    basic="MAIN-PARA.\nPERFORM BODY-PARA.\nCALL WS-PGM.\nGOBACK.\nBODY-PARA.\nMOVE 'PROGA' TO WS-PGM.\n"
    add('basic-incoming',basic+'DISPATCH-PARA.\n'+dispatch(('BODY-PARA','OUT-PARA'))+'GOBACK.\nOUT-PARA.\nGOBACK.\n',order=('BODY-PARA','OUT-PARA'),values=None,perform='basic-partial')
    ran="MAIN-PARA.\nPERFORM RANGE-A THRU RANGE-C.\nCALL WS-PGM.\nGOBACK.\nRANGE-A.\n"
    inner=dispatch(('RANGE-B','RANGE-C'))+"MOVE 'FALLPGM' TO WS-PGM.\nGO TO RANGE-C.\nRANGE-B.\nMOVE 'PROGB' TO WS-PGM.\nGO TO RANGE-C.\nRANGE-C.\nMOVE 'PROGC' TO WS-PGM.\n"
    add('range-internal',ran+inner,order=('RANGE-B','RANGE-C'),values=('PROGC',),perform='range-precise')
    add('range-escape',ran+inner.replace('RANGE-B\nRANGE-C\n','RANGE-B\nOUT-PARA\n')+'OUT-PARA.\nGOBACK.\n',order=('RANGE-B','OUT-PARA'),values=None,perform='range-partial')
    add('range-partial-target',ran+inner.replace('RANGE-B\nRANGE-C\n','RANGE-B\nUNKNOWN\n'),order=('RANGE-B','UNKNOWN'),values=None,partial=True,perform='range-partial')
    add('range-incoming',ran+"MOVE 'PROGA' TO WS-PGM.\nRANGE-C.\nMOVE 'PROGC' TO WS-PGM.\nDISPATCH-PARA.\n"+dispatch(('RANGE-A','OUT-PARA'))+'GOBACK.\nOUT-PARA.\nGOBACK.\n',order=('RANGE-A','OUT-PARA'),values=None,perform='range-partial')
    add('empty',base.replace('TARGET-B.\nMOVE \'PROGB\' TO WS-PGM.\nGO TO JOIN-PARA.\n','TARGET-B.\n'),values=None,partial=True)
    add('section','MAIN-PARA.\n'+dispatch(('TARGET-A','SEC-B'))+'GOBACK.\nTARGET-A.\nGOBACK.\nSEC-B SECTION.\nB-PARA.\nGOBACK.\n',order=('TARGET-A','SEC-B'),values=(),partial=True)
    add('ambiguous','MAIN-PARA.\n'+dispatch(('TARGET-A','AMBIG'))+'GOBACK.\nTARGET-A.\nGOBACK.\nSEC-B SECTION.\nAMBIG.\nGOBACK.\nSEC-C SECTION.\nAMBIG.\nGOBACK.\n',order=('TARGET-A','AMBIG'),values=(),partial=True)
    add('alter',base.replace('MAIN-PARA.\n','MAIN-PARA.\nALTER OLD-PARA TO PROCEED TO TARGET-A.\n')+'OLD-PARA.\nGO TO TARGET-B.\n',values=None,partial=True)
    add('no-fallthrough','MAIN-PARA.\nGO TO DISPATCH-PARA.\nTARGET-A.\nGOBACK.\nDISPATCH-PARA.\n'+dispatch(('TARGET-A',)),order=('TARGET-A',),values=(),partial=True,fallthrough=False)
    add('selector-text',base,integer='X(4)',values=None,partial=True,selector=False)
    add('cross-unit','MAIN-PARA.\n'+dispatch(('TARGET-A','CHILD-TARGET'))+'GOBACK.\nTARGET-A.\nGOBACK.\nIDENTIFICATION DIVISION.\nPROGRAM-ID. CHILD.\nPROCEDURE DIVISION.\nCHILD-TARGET.\nGOBACK.\nEND PROGRAM CHILD.\nEND PROGRAM DEPENDTEST.\n',order=('TARGET-A','CHILD-TARGET'),values=(),partial=True)
    return result
