       IDENTIFICATION DIVISION.
       PROGRAM-ID. DISPATCH-PROBE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 TARGET PIC X(8) VALUE 'INITIAL1'.
       01 FLAG-VALUE PIC X.
       PROCEDURE DIVISION.
       MAIN.
           IF FLAG-VALUE = 'Y'
             EXEC CICS HANDLE ABEND LABEL(ERR-A) END-EXEC
             MOVE 'FIRST001' TO TARGET
             PERFORM FAULT-P
           ELSE
             EXEC CICS HANDLE ABEND LABEL(ERR-B) END-EXEC
             MOVE 'SECOND02' TO TARGET
             PERFORM FAULT-P
           END-IF.
           CALL 'RESUME01'.
           GOBACK.
       FAULT-P.
           EXEC CICS ABEND END-EXEC.
       ERR-A.
           CALL TARGET.
           GOBACK.
       ERR-B.
           CALL TARGET.
           GOBACK.
