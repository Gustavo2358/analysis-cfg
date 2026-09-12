       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALLER.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAG-A PIC X.
       01 FLAG-B PIC X.
       01 WS-A PIC X(8).
       01 WS-B PIC X(8).
       PROCEDURE DIVISION.
       IF FLAG-A = 'Y'
           MOVE 'PROGA' TO WS-A
       ELSE
           MOVE 'PROGB' TO WS-A
       END-IF.
       CALL WS-A.
       IF FLAG-B = 'Y'
           MOVE 'PROGC' TO WS-B
       ELSE
           MOVE 'PROGD' TO WS-B
       END-IF.
       CALL WS-B.
       CALL 'PROGE'.
       GOBACK.
