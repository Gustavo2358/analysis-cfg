# Evals CP5 — roteamento

| Eval | Wave/estado | Oracles e evidência devidos |
| --- | --- | --- |
| EVAL-CFG-033 | harness executável | validate_cp5.py + test_cp5_harness.py, scope/manifest, docs; nenhum resultado de engine |
| EVAL-CFG-034 | W1 executável | índice/identidade/contexto, arquitetura módulo, S1/S2/S4/S8 estruturais |
| EVAL-CFG-035 | W2 executável | solver incremental/direção/leis, extensão test-only, S4/S4b/S8 |
| EVAL-CFG-036 | W3 executável | storage/valores/claims/query/retention, S1/S2/S3/S4b/S6/S7/S9 |
| EVAL-CFG-037 | W4 executável | consumers reais com values W3, cache/dispatch/batch, S5/S6/S8 |
| EVAL-CFG-038 | W5 planned | seam produtivo, regressões, S1–S9 + S10 E2E |

[Roadmap/DoDs](../../product/cp5-roadmap.md), [lifecycle](../../work/cp5-lifecycle.json),
[probes](probes.json), [métricas](metrics.json), [challenges](challenges.json),
[arquitetura](architecture.json), [gate plan](gate-plan.json),
[contrato de review](result-contract.json), [exemplo](result-review.json),
[inventário Java/POM de preparação](preparation-source-inventory.json).

W1/W2/W3/W4 usam wave_hooks e gates produtivos; hooks W5 permanecem nulos; W4 tem gates reais.
Performance/semântica de solver/arquitetura de módulos ausentes não recebem PASS.
`python3 scripts/project/check_cp5_gate.py performance --wave 1` executa os probes reais.
Ativação parcial por Wave não fecha nenhuma parcela futura.

Métricas por probe incluem as fases posteriores; em uma ativação estrutural W1,
somente as métricas cujo available_by_wave já chegou são devidas. Isso não anuncia
as demais como medidas ou fecha o probe W3. S4b em W2 usa estado finito constante;
W3 acrescenta dimensão de bindings/J/alocações, sem congelar container.

[Obrigações pós-auditoria A–I](post-audit-contracts.json): S11–S15 e desafios futuros
Invoke/effects, com hooks indisponíveis até a implementação autorizada.

[Witness de completion F3](phase-review.json): preparação parcial preserva consumer
estrutural independente; o recibo de entrega não faz parte do payload.

[CORE-SIZE-001](../../architecture/decisions/ADR-0014.md), [manifest por papel](core-size-contract.json)
e [oracle de review N/2N/4N](core-size-review.json): S16 roteado para W1–W5.
Guard atual verifica contratos e wire; nenhum desses snapshots é medição de engine.
