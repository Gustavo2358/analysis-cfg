       IDENTIFICATION DIVISION.
       PROGRAM-ID. CALLER.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 FLAG PIC X.
       01 WS-TMP PIC X(8).
       01 WS-A PIC X(8).
       01 WS-B PIC X(8).
       PROCEDURE DIVISION.
       MAIN.
       PERFORM DEFINE-A.
       CALL WS-A.
       IF FLAG = 'Y'
           MOVE 'PROGB' TO WS-B
       ELSE
           MOVE 'PROGC' TO WS-B
       END-IF.
       CALL WS-B.
       CALL 'PROGD'.
       GOBACK.
       DEFINE-A.
       MOVE 'PROGA' TO WS-TMP.
       MOVE WS-TMP TO WS-A.
