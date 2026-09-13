       IDENTIFICATION DIVISION.
       PROGRAM-ID. EVALTEST.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-X PIC X.
       01 FLAG PIC X.
       01 WS-PGM PIC X(8).
       PROCEDURE DIVISION.
       EVALUATE WS-X
        WHEN 'A'
         CALL 'PROGA'
        WHEN 'B'
         CALL 'PROGB'
        WHEN OTHER
         CALL 'PROGC'
       END-EVALUATE.
       GOBACK.
