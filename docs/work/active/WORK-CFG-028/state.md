# WORK-CFG-028 — Estado

## Onde estamos

CP5_CORE_SIZE_UNBOUNDED_HARNESS_REMEDIATION implementada / awaiting human review.
Pós-auditoria A–I/F1/F2/F3 APPROVED no HEAD e86a57c1f744bd499dd47326ebcb84d23c61ab2d.
[Lifecycle append-only](../../cp5-lifecycle.json) preserva os reviews anteriores e
registra a nova autorização, somente harness. Branch feat/cp5-dataflow-engine,
[PR #12 OPEN/DRAFT](https://github.com/Gustavo2358/analysis-cfg/pull/12).
authorized_wave=null; W1–W5 NOT_STARTED / NOT_AUTHORIZED.

## Verde conhecido

[Evidência desta remediação](../../evidence/WORK-CFG-028/core-size-unbounded/validation.md)
registra testes de contratos, gates aplicáveis e limitações. Aprovação arquitetural
não é PASS de engine. [Evidência F anterior](../../evidence/WORK-CFG-028/review-f/validation.md)
é histórica e permanece intacta; seus outcomes de capacidade foram supersedidos.

## Restante

Review humano de CORE-SIZE-001 e autorização explícita W1 em tarefa separada,
na mesma branch/PR. Sem merge, auto-merge ou ready; nenhuma Wave automática.

## Descobertas que afetam o plano

Performance/full CP5 continuam UNAVAILABLE; não há hooks de engine implementados.
CP5-F01 permanece NONBLOCKING/follow-up W1, sem POM modificado. [Dívidas de
capacidade](../../cp5-follow-ups.md#size-cap-debts): upstream codec/validator e
adapters/propagação legados ainda têm caps. A futura engine nasce sem size-based
admission; a pipeline inteira ainda não pode receber esse claim. Migração produtiva
é posterior, sem desabilitar validação, streaming/retry/ECS nesta sessão.
