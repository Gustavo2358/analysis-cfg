       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALLER.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAG PIC X.
       01 WS-A PIC X(8).
       01 WS-B PIC X(8).
       PROCEDURE DIVISION.
       IF FLAG = 'Y'
           MOVE 'PROGA' TO WS-A
       ELSE
           MOVE 'PROGB' TO WS-A
       END-IF.
       CALL WS-A.
       MOVE 'PROGC' TO WS-B.
       CALL WS-B.
       GOBACK.
