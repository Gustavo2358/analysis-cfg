# Índice de conhecimento

Comece por [AGENTS](../AGENTS.md) e pelo [trabalho](work/index.md).
Esta página roteia leitura; não exige carregar todos os documentos.

| Necessidade | Contexto |
| --- | --- |
| Missão/MVP | [missão](product/mission-and-slices.md) |
| Arquitetura | [mapa curto](../ARCHITECTURE.md), [pipeline](architecture/pipeline.md), [fronteiras](architecture/boundaries.md) |
| I/O e integração | [portas/adapters](architecture/ports-and-adapters.md), [CFG JSON v1](architecture/cfg-json-v1.md), [Java/Maven](engineering/toolchain-and-modules.md) |
| Evolução | [extensibilidade](architecture/extensibility.md), [ADRs](architecture/decisions/index.md), [invariantes](architecture/invariants.md) |
| Contrato | [fronteira IR](domain/ir-boundary.md), [controle](domain/core-control.md), [produto](domain/graph-product.md) |
| Casos avançados | [local](domain/local-control.md), [aberto](domain/open-control.md) |
| Rigor | [semântica](engineering/semantic-policy.md), [testes](engineering/testing.md), [performance](engineering/performance.md) |
| Sessão e higiene | [work items](engineering/work-item-protocol.md), [documentação](engineering/documentation-policy.md), [impacto](engineering/downstream-impact.md) |
| Operação | [gates](engineering/gates.md), [segurança/observabilidade](engineering/security-and-observability.md) |
| Review do harness | [parecer de 05/09/2026](engineering/harness-review-2026-09-05.md) |
| Evidência | [evals](evals/index.md), [baseline documental histórica](harness-validation.md) |
| Fontes | [índice e autoridade](sources/index.md), [baseline upstream](sources/upstream-state.md), [adaptação do harness](sources/harness-adaptation.md) |
| Iniciar agente | [discovery](prompts/start-discovery.md), [checkpoint](prompts/implement-checkpoint.md) |
| Modelos documentais | [templates](templates/README.md) |

[Backlog](work/backlog.md) é o mapa de trabalho futuro. Nenhum arquivo em história
ou referência não consultada substitui regra canônica da versão em uso.

## CP5

[CP5 arquitetura](architecture/cp5-dataflow.md), [lifecycle](work/cp5-lifecycle.json) e [roadmap](product/cp5-roadmap.md).

- [CFG JSON 2.0.0 e preservação v1](architecture/cfg-json-v2.md).
- [Fast CI e Full Qualification explícita](engineering/qualification.md).
