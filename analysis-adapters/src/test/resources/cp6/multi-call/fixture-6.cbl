       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALLER.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAG PIC X.
       01 WS-PGM PIC X(8).
       PROCEDURE DIVISION.
       MAIN.
       IF FLAG = 'Y'
           MOVE 'PROGA' TO WS-PGM
       ELSE
           MOVE 'PROGB' TO WS-PGM
       END-IF.
       CALL WS-PGM.
       PERFORM FORCE-C.
       CALL WS-PGM.
       GOBACK.
       FORCE-C.
       MOVE 'PROGC' TO WS-PGM.
