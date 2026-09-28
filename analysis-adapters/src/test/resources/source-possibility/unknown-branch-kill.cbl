       IDENTIFICATION DIVISION.
       PROGRAM-ID. PROBE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAG-X PIC X.
       01 PGM PIC X(8).
       PROCEDURE DIVISION.
           IF FLAG-X = 'Y'
               EXEC SQL UPDATE T SET C = 1 END-EXEC
               MOVE 'FIRST' TO PGM
           ELSE
               MOVE 'SECOND' TO PGM
           END-IF
           MOVE 'FINAL' TO PGM
           CALL PGM
           GOBACK.
