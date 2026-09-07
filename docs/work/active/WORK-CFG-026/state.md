# WORK-CFG-026 — Estado

## Onde estamos
Implementação autorizada somente 2B; contrato/oracles antes de produção. PR #9 reconciliado e 025 encerrado. Baseline b614712fda55fef12639cbe18fd90793faa1fb3b, branch feat/air-json-cfg-cli.

## Verde conhecido
Git limpo e atualizado no baseline; merge real confirmado via gh e ancestralidade local. Nenhum gate de produto 2B executado ainda.

## Restante
RED, implementação, GREEN, gates/challenges, revisão, commits/push/PR, CI no head e parada humana.

## Descobertas que afetam o plano
Roadmap tem snapshot antigo; autorização atual do usuário define 2B e libera DRAFT pinado. Upstream irmão não tem o commit de merge; cópia isolada /tmp evita modificá-lo. Erro de limite físico será tipado próprio, pois construtores de AirJsonException não são públicos; exceções reais do codec atravessam intactas.
