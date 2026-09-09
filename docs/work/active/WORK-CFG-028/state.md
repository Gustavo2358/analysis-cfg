# WORK-CFG-028 — Estado

## Onde estamos

WAVE_1 IMPLEMENTED / AWAITING_HUMAN_REVIEW. Autorização humana limitada a índice/session
estrutural e CP5-F01, após aprovação de CORE-SIZE-001 no HEAD
4aeb4c0ad086ec4fc8911879b13139d84ca57bc9.
[Autorização append-only](../../evidence/WORK-CFG-028/wave-1/authorization.json).
Branch feat/cp5-dataflow-engine; mesmo PR #12 OPEN/DRAFT, sem auto-merge.
W2–W5 NOT_STARTED / NOT_AUTHORIZED; authorized_wave=1.

## Verde conhecido

Maven 165 testes; fast 132; arquitetura, semântica CFG/W1, integração/CLI e
performance W1 PASS. Full global UNAVAILABLE/3, com W1 PASS e W2–W5 ausentes.
Campanha de 15 mutantes + displayName adicional: compile/RED/restore/segundo GREEN.
CP5-F01 corrigido; pins/siblings preservados.
CI de push e pull_request SUCCESS para 318181c072710f89ac58c2bd387c1645de434cd6:
checkout exato no push, merge sintético com árvore idêntica no PR.
[Recibo versionado](../../evidence/WORK-CFG-028/wave-1/initial-remote-ci/receipt.json),
[evidência e limites](../../evidence/WORK-CFG-028/wave-1/validation.md).
O recibo do último HEAD publicado fica no mesmo [PR #12](https://github.com/Gustavo2358/analysis-cfg/pull/12)
conforme contrato I; CI anterior não valida novo HEAD.

## Restante

Review humano explícito do HEAD final W1. Nenhuma autorização W2–W5;
sem merge, auto-merge, ready ou início automático da próxima Wave.

## Descobertas que afetam o plano

Retenção mede objetos/arrays lógicos por identidade, não bytes de heap total.
Refs W1 cobrem ObjectPlace/CellBinding e owners/initialLabel; não interpretam effects,
aliases ou premissas de valores. Dívidas externas de capacidade continuam explícitas.
