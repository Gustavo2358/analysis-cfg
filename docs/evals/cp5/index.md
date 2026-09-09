# Evals CP5 — roteamento

| Eval | Wave/estado | Oracles e evidência devidos |
| --- | --- | --- |
| EVAL-CFG-033 | harness executável | validate_cp5.py + test_cp5_harness.py, scope/manifest, docs; nenhum resultado de engine |
| EVAL-CFG-034 | W1 planned | índice/identidade/contexto, arquitetura módulo, S1/S2/S4/S8 estruturais |
| EVAL-CFG-035 | W2 planned | solver incremental/direção/leis, extensão test-only, S4/S4b/S8 |
| EVAL-CFG-036 | W3 planned | storage/valores/claims/query/retention, S1/S2/S3/S4b/S6/S7/S9 |
| EVAL-CFG-037 | W4 planned | consumers reais com values W3, cache/dispatch/batch, S5/S6/S8 |
| EVAL-CFG-038 | W5 planned | seam produtivo, regressões, S1–S9 + S10 E2E |

[Roadmap/DoDs](../../product/cp5-roadmap.md), [lifecycle](../../work/cp5-lifecycle.json),
[probes](probes.json), [métricas](metrics.json), [challenges](challenges.json),
[arquitetura](architecture.json), [gate plan](gate-plan.json),
[contrato de review](result-contract.json), [exemplo](result-review.json),
[inventário Java/POM de preparação](preparation-source-inventory.json).

Hooks nulos e NOT_AVAILABLE_UNTIL_IMPLEMENTED são obrigatórios nesta preparação.
Performance/semântica de solver/arquitetura de módulos ausentes não recebem PASS.
`python3 scripts/project/check_cp5_gate.py performance --wave 1` devolve exit 3;
ativação futura requer harness atualizado e implementação/provas reais na Wave.

Métricas por probe incluem as fases posteriores; em uma ativação estrutural W1,
somente as métricas cujo available_by_wave já chegou são devidas. Isso não anuncia
as demais como medidas ou fecha o probe W3. S4b em W2 usa estado finito constante;
W3 acrescenta dimensão de bindings/J/alocações, sem congelar container.
