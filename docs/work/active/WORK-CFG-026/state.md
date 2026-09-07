# WORK-CFG-026 — Estado

## Onde estamos

2B implementado, revisado e validado localmente; preparando commit/push/PR. PR #9 reconciliado e 025 encerrado.
Baseline b614712fda55fef12639cbe18fd90793faa1fb3b, branch feat/air-json-cfg-cli.
Contrato e RED por API ausente commitados em 7d22315 antes da produção.

## Verde conhecido

Segundo GREEN em Temurin 21: docs/fast (47), architecture (102 kernel + 31 transporte),
semantic (84), integration (31), Maven clean verify (133) e execução CLI/golden passaram.
17 challenges RED, restauração byte a byte. Performance/full executados com exit 3
UNAVAILABLE. Scope/manifest/diff check passaram. [Evidência](../../evidence/WORK-CFG-026/validation.md).

## Restante

Commit/push/PR, confirmar CI no head exato e parar para review humano. Não fazer
merge/auto-merge. E2E cross-repo não iniciado.

## Descobertas que afetam o plano

Upstream obtido em cópia isolada /tmp, sem alterar repo irmão. Exceções AirJson
atravessam intactas; limite físico tem tipo próprio porque construtores do upstream
não são públicos. O modelo pinado já recusa surrogate isolado nos IDs; o writer
também protege sua primitiva UTF-8. Fallback de move é testado sem alegar atomicidade.
Memória/arquivo provam observações de controle/coverage do v1; não duplicam toda a AIR.
