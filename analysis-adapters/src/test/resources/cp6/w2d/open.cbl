       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALLER.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAG   PIC X.
       01 WS-PGM PIC X(8).

       PROCEDURE DIVISION.
           IF FLAG = 'Y'
               MOVE 'PROGA' TO WS-PGM
           END-IF

           CALL WS-PGM.
           GOBACK.
