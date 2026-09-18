       IDENTIFICATION DIVISION.
       PROGRAM-ID. IFPROBE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAGS.
          05 FLAG PIC X.
       01 WS-PGM PIC X(8).
       PROCEDURE DIVISION.
           IF FLAG = 'Y'
               MOVE 'PROGA' TO WS-PGM
           ELSE
               MOVE 'PROGB' TO WS-PGM
           END-IF
           CALL WS-PGM.
           GOBACK.
