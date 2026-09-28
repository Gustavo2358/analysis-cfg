       IDENTIFICATION DIVISION.
       PROGRAM-ID. PROBE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAG-X PIC X.
       01 PGM PIC X(8).
       PROCEDURE DIVISION.
           MOVE 'BEFORE' TO PGM
           PERFORM P
           CALL PGM
           GOBACK.
       P.
           IF FLAG-X = 'Y'
             PERFORM P
             MOVE 'AFTERREC' TO PGM
           END-IF.
