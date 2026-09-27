       IDENTIFICATION DIVISION.
       PROGRAM-ID. CICS-CONTROL.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY DFHAID.
       01 BUFFER-AREA.
         05 PART-A PIC X(8) VALUE 'BEFORE01'.
         05 PART-B PIC X(8).
       01 RC PIC S9(8) COMP.
       01 RC2 PIC S9(8) COMP.
       01 MAP-NAME PIC X(8) VALUE 'TESTMAP'.
       01 SENTINEL PIC X.
       PROCEDURE DIVISION.
       MAIN.
           PERFORM OUTER-P.
           CALL 'RESUMED1'.
           GOBACK.
       OUTER-P.
           PERFORM IO-P THRU IO-X.
           CALL 'RESUMED2'.
       IO-P.
           EXEC CICS RECEIVE MAP(MAP-NAME) INTO(BUFFER-AREA)
            RESP(RC) RESP2(RC2) END-EXEC.
           CALL PART-A.
       IO-X.
           EXIT.
       OUTSIDE-P.
           CALL 'OUTSIDE1'.
           GOBACK.
