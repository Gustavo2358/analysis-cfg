       IDENTIFICATION DIVISION.
       PROGRAM-ID. FILETEST.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT F ASSIGN TO CLIENTDD.
           SELECT G ASSIGN TO OTHERDD.
       DATA DIVISION.
       FILE SECTION.
       FD F.
       01 R PIC X(8).
       FD G.
       01 S PIC X(8).
       PROCEDURE DIVISION.
           IF R = 'A'
               OPEN INPUT F OUTPUT G
           ELSE
               OPEN OUTPUT G INPUT F
           END-IF.
           CLOSE F G.
           GOBACK.
