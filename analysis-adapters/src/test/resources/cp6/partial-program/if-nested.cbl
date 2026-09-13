       IDENTIFICATION DIVISION.
       PROGRAM-ID. PARTIAL-PGM.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-A PIC X(8).
       01 WS-B PIC X(8).
       01 FLAG PIC X.
       PROCEDURE DIVISION.
       IF FLAG = 'Y'
        IF FLAG = 'N'
         MOVE 'PROGA' TO WS-A
        ELSE
         MOVE 'PROGB' TO WS-A
        END-IF
       ELSE
        MOVE 'PROGC' TO WS-A
       END-IF.
       CALL WS-A.
       GOBACK.
