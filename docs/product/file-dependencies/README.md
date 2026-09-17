# FILE-DEPENDENCIES — entrada da campanha

Estado: **H4 aprovado; execução core N+C autorizada; W0/W1 QUALIFIED_LOCAL; W2 IN_PROGRESS.**
W0–W9/W11 avançam pelos gates sem nova autorização. W10 TODO / NOT_AUTHORIZED.
PRs persistentes permanecem Draft e unmerged. STOP final após W11.

Leia somente [brief](brief.md), [estado](state.md) e a wave atual no
[grafo de trabalho](waves.md). O discovery bruto de 16/09/2026 é referência
histórica opcional, fora do contexto obrigatório de W0.

| Necessidade | Documento canônico |
| --- | --- |
| Filosofia, glossário, non-goals | [brief](brief.md) |
| Código atual, repos, pins, gaps | [baseline H0](baseline.md) |
| Decisões arquiteturais | [ADR-0015](../../architecture/decisions/ADR-0015.md) |
| Contratos e exemplos bilaterais | [contratos](contracts.md) |
| Perfis/autoridade COBOL | [perfis](profiles.md) |
| SELECT/ASSIGN/FD e demais famílias | [matriz N/C/D](coverage.md) |
| Wave, pré-requisitos, repins | [waves](waves.md) → item executável |
| Gates e regressão de CALL | [verificação](verification.md) |
| Casos/oráculos | [catálogo](test-catalog.md) |
| Decisões abertas com dono e prazo | [contratos: decisões abertas](contracts.md#decisoes-abertas) |
| Retomada/revisão humana | [estado e checkpoint atual](state.md) |

Esta pasta usa a política [Lean Harness](../../engineering/lean-harness.md):
documentos de produto, work items mínimos no índice existente e gates existentes.
Não há novo executor, registry paralelo ou receipt de liberação por checkpoint.

## Repositórios e navegação local

| Camada | Página local na branch persistente `feat/file-dependencies` |
| --- | --- |
| Frontend/SP | `proleap-poc/docs/domain/file-dependencies.md` |
| Lower | `cobol-lower/docs/domain/file-dependencies.md` |
| Modelo/codec | `air-java/docs/domain/file-dependencies.md` |
| CFG/dataflow/dependency/JSON | esta pasta, depois [contratos](contracts.md) |
| AIR normativa | `analysis-ir/README.md`; somente se W1 demonstrar extensão indispensável |
| Integração local | `artefatos-e2e/file-dependencies-20260916/README.md` |

Worktrees: `<workspace>/.file-dependencies/worktrees/<repo>`; não trocar branches
dos checkouts originais. O repositório E2E continua exclusivamente local.
