#!/usr/bin/env python3
"""Deterministic focal sources; never used as semantic authority."""
from pathlib import Path
import textwrap
ROOT=Path(__file__).resolve().parents[2]
FIXTURES=ROOT/'analysis-adapters/src/test/resources/cp6/perform-family'


def source(main,body):
    return ('IDENTIFICATION DIVISION.\nPROGRAM-ID. FAMILY.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n'
            '01 WS-PGM PIC X(8).\n01 FLAG PIC X.\nPROCEDURE DIVISION.\nMAIN.\n'+main+'\nGOBACK.\n'+body)


def thru():
    body="A.\nMOVE 'PROGA' TO WS-PGM.\nB.\nMOVE 'PROGB' TO WS-PGM.\nC.\nMOVE 'PROGC' TO WS-PGM.\n"
    main='PERFORM A THRU C.\nCALL WS-PGM.'
    cases={'t1':source('PERFORM A THRU B.\nCALL WS-PGM.',"A.\nMOVE 'PROGA' TO WS-PGM.\nB.\nMOVE WS-PGM TO WS-PGM.\n"),
           't2':source(main,body),'through':source(main.replace('THRU','THROUGH'),body),
           't3':source(main+'\n'+main,body),
           'if-body':source(main,"A.\nIF FLAG = 'Y' MOVE 'PROGA' TO WS-PGM ELSE MOVE 'PROGB' TO WS-PGM END-IF.\nB.\nMOVE WS-PGM TO WS-PGM.\nC.\nMOVE WS-PGM TO WS-PGM.\n"),
           'evaluate-body':source(main,"A.\nEVALUATE FLAG WHEN 'Y' MOVE 'PROGA' TO WS-PGM WHEN OTHER MOVE 'PROGB' TO WS-PGM END-EVALUATE.\nB.\nMOVE WS-PGM TO WS-PGM.\nC.\nMOVE WS-PGM TO WS-PGM.\n"),
           'local-jump':source(main,"A.\nMOVE 'PROGA' TO WS-PGM.\nGO TO C.\nB.\nMOVE 'BADPROG' TO WS-PGM.\nC.\nMOVE WS-PGM TO WS-PGM.\n"),
           'terminal':source(main,"A.\nMOVE 'PROGA' TO WS-PGM.\nGOBACK.\nC.\nMOVE 'BADPROG' TO WS-PGM.\n"),
           'call-body':source(main,"A.\nMOVE 'PROGA' TO WS-PGM.\nCALL WS-PGM.\nC.\nMOVE 'PROGC' TO WS-PGM.\n"),
           'unknown-body':source(main,body.replace("MOVE 'PROGB' TO WS-PGM.","DISPLAY 'UNSUPPORTED'.")),
           'incoming':source(main+'\nGO TO B.',body),
           'escape':source(main,body.replace("MOVE 'PROGB' TO WS-PGM.",'GO TO OUTSIDE.')+"OUTSIDE.\nMOVE 'BADPROG' TO WS-PGM.\n"),
           'overlap':source('PERFORM A THRU C.\nPERFORM B THRU D.\nCALL WS-PGM.',body+"D.\nMOVE 'PROGD' TO WS-PGM.\n"),
           'recursive':source(main,body.replace("MOVE 'PROGB' TO WS-PGM.",'PERFORM A THRU C.')),
           'cycle':source(main,body.replace("MOVE 'PROGB' TO WS-PGM.",'GO TO A.')),
           'partial-end':source(main.replace('THRU C','THRU MISSING'),body),
           'partial-start':source(main.replace('PERFORM A','PERFORM MISSING'),body),
           'reverse':source(main.replace('A THRU C','C THRU A'),body),
           'empty':source(main,body.replace("MOVE 'PROGB' TO WS-PGM.\n",''))}
    for n in (1,2,5,40):cases['thru-'+str(n)]=source('\n'.join([main]*n),body)
    return cases


if __name__=='__main__':
    FIXTURES.mkdir(parents=True,exist_ok=True)
    for name,text in thru().items():(FIXTURES/(name+'.cbl')).write_text(''.join('       '+part+'\n' for line in text.splitlines() for part in textwrap.wrap(line,width=65,break_long_words=False,break_on_hyphens=False)))
