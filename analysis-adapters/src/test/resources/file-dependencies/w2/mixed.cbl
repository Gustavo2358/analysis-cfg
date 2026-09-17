       IDENTIFICATION DIVISION.
       PROGRAM-ID. FILETEST.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT F ASSIGN TO CLIENTDD.
       DATA DIVISION.
       FILE SECTION.
       FD F.
       01 R PIC X(8).
       PROCEDURE DIVISION.
           CALL 'BEFORE'.
           OPEN INPUT F.
           READ F.
           CALL 'AFTER'.
           CLOSE F.
           GOBACK.
