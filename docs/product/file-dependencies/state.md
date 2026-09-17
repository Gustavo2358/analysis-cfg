# File Dependencies — estado final

**CORE N+C CLOSED / MERGED / COMPLETE.** IBM Enterprise COBOL z/OS6.4 + CICS TS z/OS5.6.
H0–H4 DONE; W0,W1,W2,W3,W4,W5,W6,W7,W8,W9,W11 DONE após merges e smoke PASS.
EP-R2 PRESERVED; C06 PASS; open core blockers NONE.

W10 DEFERRED / OPTIONAL_EXTENSION / NOT_PART_OF_CORE / REQUIRES_NEW_PRODUCT_AUTHORIZATION.
Não é feature pendente nem gap core. O status técnico TODO no YAML é somente a
enumeração suportada pelo Lean; a classificação de produto governa o adiamento.

[Handoff final, merges e capability](closeout.md); [pins do smoke](closeout-pins.json).
Cinco PRs #7/#19/#54/#30/#38 MERGED. Sem auto-merge ou release.
D-AIR/D-WIRE/D-EFFECT/D-DYNAMIC core CLOSED. D-D-AUTH reservado à extensão opcional.
DSNAME/JCL/runtime allocation/external lookup OUT OF PRODUCT, nunca gap/partial.

[Qualificação pós-EP-R2](post-ep-r2.md) e [C06](c06-read-dataset.md) são checkpoints
históricos. Evidências brutas preservadas no E2E local e em .harness-results.
STOP: campanha encerrada. Não iniciar W10 nem nova wave sem autorização.
