# BACKLOG-CFG-024 — Checkpoint 0A: política de projeção

Estado: completed. Autorização implementation no WORK-CFG-024, pedido explícito 07/09/2026.
Dependência: BACKLOG-CFG-006. EVAL-CFG-030.

KNOWN_SUBSET default admite inventário PARTIAL projetável; STRICT opt-in exige COMPLETE.
AIR/evidence, preflight e recusas de semântica não suportada preservados. Sem mudança de
transporte, upstream, operações ou algoritmo. Matriz e regressões verdes; gates locais
fast/architecture/semantic e CI Temurin 21 passaram. [PR #8](https://github.com/Gustavo2358/analysis-cfg/pull/8)
aberto para review humano, sem merge.

Discovery, RED/GREEN, mutantes e limites em
[WORK-CFG-024](../history/WORK-CFG-024.md). Não promove E2E BACKLOG-CFG-023 nem outro checkpoint.
