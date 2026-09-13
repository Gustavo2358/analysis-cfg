       IDENTIFICATION DIVISION.
       PROGRAM-ID. FAMILY.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-PGM PIC X(8).
       01 FLAG PIC X.
       PROCEDURE DIVISION.
       MAIN.
       PERFORM A THRU C.
       CALL WS-PGM.
       GOBACK.
       A.
       MOVE 'PROGA' TO WS-PGM.
       GO TO C.
       B.
       MOVE 'BADPROG' TO WS-PGM.
       C.
       MOVE WS-PGM TO WS-PGM.
