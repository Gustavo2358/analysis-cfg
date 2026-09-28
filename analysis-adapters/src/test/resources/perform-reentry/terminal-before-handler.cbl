       IDENTIFICATION DIVISION.
       PROGRAM-ID. PROBE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 AREA-X PIC X(80).
       01 RESP-CD PIC S9(9) COMP.
       01 TRANS-ID PIC X(4).
       01 FLAG-X PIC X.
       PROCEDURE DIVISION.
           EXEC CICS HANDLE ABEND LABEL(HP) END-EXEC
           PERFORM P
           CALL 'DEAD'
           GOBACK.
       P.
           GOBACK.
           PERFORM P.
       HP.
           GOBACK.
       UNUSED-ABEND.
           EXEC CICS ABEND NODUMP END-EXEC.
