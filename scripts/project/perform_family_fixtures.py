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


def until():
    body="A.\nMOVE 'NEWPROG' TO WS-PGM.\n"
    def main(control):return "MOVE 'OLDPROG' TO WS-PGM.\nPERFORM A "+control+".\nCALL WS-PGM."
    cases={
        'until-before':source(main("WITH TEST BEFORE UNTIL FLAG = 'Y'"),body),
        'until-default':source(main("UNTIL FLAG = 'Y'"),body),
        'until-after':source(main("WITH TEST AFTER UNTIL FLAG = 'Y'"),body),
        'until-constant':source("MOVE 'Y' TO FLAG.\n"+main("UNTIL FLAG = 'Y'"),body),
        'until-mixed':source("PERFORM Z.\nCALL WS-PGM.\nPERFORM A THRU B.\nCALL WS-PGM.\nPERFORM U WITH TEST AFTER UNTIL FLAG = 'Y'.\nCALL WS-PGM.",
            "A.\nMOVE 'PROGA' TO WS-PGM.\nB.\nMOVE 'PROGB' TO WS-PGM.\nU.\nMOVE 'NEWPROG' TO WS-PGM.\nZ.\nMOVE 'BASICPGM' TO WS-PGM.\n"),
        'until-branches':source(main("WITH TEST AFTER UNTIL FLAG = 'Y'"),"A.\nIF FLAG = 'X' MOVE 'PROGA' TO WS-PGM ELSE MOVE 'PROGB' TO WS-PGM END-IF.\n"),
        'until-thru':source(main("THRU C WITH TEST AFTER UNTIL FLAG = 'Y'"),"A.\nMOVE 'PROGA' TO WS-PGM.\nB.\nMOVE 'PROGB' TO WS-PGM.\nC.\nMOVE 'NEWPROG' TO WS-PGM.\n"),
        'until-unresolved':source(main("UNTIL MISSING = 'Y'"),body),
        'until-unsupported':source(main("UNTIL FUNCTION RANDOM > 0"),body)}
    for n in (1,2,5,40):cases['until-'+str(n)]=source('\n'.join([main("WITH TEST AFTER UNTIL FLAG = 'Y'")]*n),body)
    for name in ('incoming','escape','cycle','recursive','partial-end','unknown-body'):
        # Fixture construction only. Production never parses or rewrites source text this way.
        text=thru()[name].replace('PERFORM A THRU C.','PERFORM A THRU C UNTIL FLAG = \'Y\'.').replace('PERFORM A THRU MISSING.','PERFORM A THRU MISSING UNTIL FLAG = \'Y\'.')
        cases['until-'+name]=text
    return cases


def times():
    body="A.\nMOVE 'NEWPROG' TO WS-PGM.\n"
    def make(control, tail=body):
        return source("MOVE 'OLDPROG' TO WS-PGM.\nPERFORM A "+control+".\nCALL WS-PGM.",tail).replace('01 FLAG PIC X.','01 FLAG PIC X.\n01 WS-N PIC S9(9).')
    cases={'times-identifier':make('WS-N TIMES'), 'times-one':make('1 TIMES'),
           'times-positive':make('5 TIMES'), 'times-large':make('1000000 TIMES'),
           'times-thru':make('THRU C 5 TIMES',"A.\nMOVE 'PROGA' TO WS-PGM.\nB.\nMOVE 'PROGB' TO WS-PGM.\nC.\nMOVE 'NEWPROG' TO WS-PGM.\n"),
           'times-unresolved':make('MISSING TIMES'),
           'times-noninteger':make('FLAG TIMES'),
           'times-zero':make('0 TIMES')}
    for n in (1,2,5,40):
        cases['times-'+str(n)]=source('\n'.join(['PERFORM A 5 TIMES.\nCALL WS-PGM.']*n),body)
    for name in ('incoming','escape','cycle','recursive','partial-end','unknown-body'):
        cases['times-'+name]=thru()[name].replace('PERFORM A THRU C.','PERFORM A THRU C 5 TIMES.').replace('PERFORM A THRU MISSING.','PERFORM A THRU MISSING 5 TIMES.')
    return cases


if __name__=='__main__':
    FIXTURES.mkdir(parents=True,exist_ok=True)
    for name,text in {**thru(),**until(),**times()}.items():(FIXTURES/(name+'.cbl')).write_text(''.join('       '+part+'\n' for line in text.splitlines() for part in textwrap.wrap(line,width=65,break_long_words=False,break_on_hyphens=False)))
