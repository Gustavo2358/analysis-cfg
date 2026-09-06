# Estado — WORK-CFG-002

## Onde estamos

Item promovido com autorização de implementação. `main` local estava limpa e
sincronizada com `origin/main`; trabalho isolado em
`codex/feat/cfg-java-air-boundary`.

## Verde conhecido

Antes das mudanças, `bash scripts/harness/check-fast.sh` passou com 38 testes do
harness. O gate architecture retornou `UNAVAILABLE` com exit 3, como esperado na
fase docs-only. Os dois refs `main` upstream foram confirmados diretamente.

## Restante

Atualizar fontes/harness, criar build/preflight/testes/CI, observar RED, obter
GREEN, revisar diff, arquivar este item e abrir um único PR.

## Descobertas que afetam o plano

O JSON Binding 1.0.0 agora existe no `analysis-ir`, mas permanece explicitamente
DRAFT e fora do código. O `air-java` reconciliado removeu `contracts[]` de
`Publication`; assinatura, outcomes e `ContractRef` são fatos materializados no
site de invoke.
