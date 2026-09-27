       IDENTIFICATION DIVISION.
       PROGRAM-ID. PERFORM-ADVERSARY.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 PGM PIC X(8).
       01 FLAG PIC X.
       01 I PIC 9(4).
       01 J PIC 9(4).
       01 K PIC 9(4).
       01 N PIC 9(4).
       PROCEDURE DIVISION.
       MAIN.
       PERFORM S THRU Q
       CALL PGM
       GOBACK.
       S SECTION.
       MOVE 'LIVE0001' TO PGM.
       Q.
       MOVE 'LIVE0002' TO PGM.
       R.
       CALL 'DEAD0001'.
