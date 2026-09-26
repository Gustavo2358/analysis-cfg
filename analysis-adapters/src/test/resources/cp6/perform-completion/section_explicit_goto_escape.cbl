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
       PERFORM S
       CALL 'DEAD0001'
       GOBACK.
       S SECTION.
       P.
       GO TO ESCAPED-P.
       OUTSIDE-S SECTION.
       ESCAPED-P.
       CALL 'LIVE0001'
       GOBACK.
