# File Dependencies CORE N+C — closeout

**CLOSEOUT AUTORIZADO.** H0–H4 PREPARATION COMPLETE; W0–W9 QUALIFIED / PENDING_MERGE;
W11 QUALIFIED_LOCAL_POST_EP_R2 / PENDING_MERGE. Sem feature work pendente no core N+C.
W10 DEFERRED / OPTIONAL_EXTENSION / NOT_PART_OF_CORE; requer nova autorização de produto.
Merges explícitos topológicos e smoke em main autorizados; sem auto-merge/release.
A autorização humana de closeout substitui o STOP de revisão anterior.

## Sequência

analysis-ir #7 → air-java #19 → proleap-poc #54 → cobol-lower #30 → analysis-cfg #38.
Cada merge exige HEAD/CI/reviews/base/clean-state conferidos. Repins com prova de
conteúdo equivalente conservam evidência semântica; smoke final usa clones limpos
e SHAs reais de main. DONE lean somente após merges e smoke PASS.

## Produto

Core: IBM Enterprise COBOL z/OS6.4 + CICS TS z/OS5.6. D-AIR/D-WIRE/D-EFFECT e
D-DYNAMIC/core fechadas; EP-R2 preservado; C06 PASS. W10 opcional não é gap core.
DSNAME resolution, JCL correlation, runtime allocation e external lookup estão
OUT OF PRODUCT; ausência não é UNKNOWN/PARTIAL/GAP.

## Evidência de entrada

[Reconciliação qualificada](post-ep-r2.md); CFG9871cec, frontend17323f4,
lower5565e10, AIR5fe0224e, IRfb153ae. Auditoria nova dos seis worktrees limpa;
bases remotas ancestrais qualificadas; review threads zero. FAST remoto PASS
nos quatro repos executáveis; IR sem workflow, qualificação bilateral registrada.
Logs novos em `.harness-results/fd-closeout/`. Merges e smoke ainda NOT_RUN.
