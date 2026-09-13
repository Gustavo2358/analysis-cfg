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
         IF FLAG = 'Y'
          MOVE 'PROGA' TO WS-PGM
         ELSE
          MOVE 'PROGB' TO WS-PGM
         END-IF
        WHEN OTHER
         MOVE 'PROGC' TO WS-PGM
       END-EVALUATE
       CALL WS-PGM.
       GOBACK.
