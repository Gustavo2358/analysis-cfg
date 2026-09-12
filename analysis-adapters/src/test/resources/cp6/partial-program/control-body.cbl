       IDENTIFICATION DIVISION.
       PROGRAM-ID. PARTIAL-CONTROL.
       PROCEDURE DIVISION.
       MAIN.
           PERFORM DEFINE-A.
           CALL 'AFTER'.
           GOBACK.
       DEFINE-A.
           CALL 'INNER'.
