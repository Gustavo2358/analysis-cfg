       IDENTIFICATION DIVISION.
       PROGRAM-ID. COMPOSE.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-A PIC X(8).
       01 FLAG PIC X.
       PROCEDURE DIVISION.
       MAIN.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       MOVE 'PROGA' TO WS-A.
       CALL WS-A.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       IF FLAG = 'Y'
        MOVE 'PROGA' TO WS-A
       ELSE
        MOVE 'PROGB' TO WS-A
       END-IF.
       PERFORM DEFINE-A.
       CALL WS-A.
       PERFORM DEFINE-A.
       CALL WS-A.
       PERFORM DEFINE-A.
       CALL WS-A.
       PERFORM DEFINE-A.
       CALL WS-A.
       PERFORM DEFINE-A.
       CALL WS-A.
       GOBACK.
       DEFINE-A.
       MOVE 'PROGA' TO WS-A.
