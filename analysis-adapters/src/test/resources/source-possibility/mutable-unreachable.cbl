       IDENTIFICATION DIVISION.
       PROGRAM-ID. PROBE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 AREA-X PIC X(80).
       01 RESP-CD PIC S9(9) COMP.
       01 TRANS-ID PIC X(4).
       01 FLAG-X PIC X.
       PROCEDURE DIVISION.
           GO TO ROUTE.
       UNUSED.
           ALTER ROUTE TO PROCEED TO DEST.
       ROUTE.
           GO TO LOOP.
       LOOP.
           GO TO LOOP.
       DEST.
           CALL 'DEAD'
           GOBACK.
