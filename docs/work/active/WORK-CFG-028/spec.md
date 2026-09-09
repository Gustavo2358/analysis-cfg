# CP5 — preparação do harness

## Problema

Fixture PROGA não basta para provar engine genérica/eficiente. Discovery aprovado
precisa de contratos e gates duráveis, preservando seus limites.

## Objetivo

Formalizar H1–H7/R1/R2 em arquitetura/domínio, quatro ADRs coesos, métricas/ledgers,
probes/challenges, lifecycle e snapshot de resultado; validar somente o harness.
BACKLOG-CFG-020 é reaproveitado como umbrella, com cinco checkpoints sequenciais
no mesmo manifesto/branch/PR. [Estado canônico](../../cp5-lifecycle.json).

## Fora de escopo

Qualquer Java/POM novo ou alterado, módulo analysis-kernel/analysis-values, engine,
CLI/writer, mutantes produtivos e implementações W1–W5. Siblings read-only.
Sem solver Python alternativo. Performance ausente não é PASS ou falha de preparação.
