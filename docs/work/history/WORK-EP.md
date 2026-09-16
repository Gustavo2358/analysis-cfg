# EP — Evidence-Preserving Partial Analysis

Status do work item: `completed`. **APPROVED / MERGED / CLOSED**.

PR [#37](https://github.com/Gustavo2358/analysis-cfg/pull/37) mergeado por
merge commit `1910445eb85dc21b8d69d2e2112e84622bdd629e`, com proteção pelo
source HEAD aprovado `533a31eff9097237eaf9e243094c2fb2ca73d236`.
Fast CI #245 passou nesse source HEAD após a remediação F1/F2.

RD, Regional Values e cópias recuperam suporte corrente por identidades
explicitamente referidas em NamedObject/ObjectPlace/Choice. Regional Result
1.4 transporta essa projeção sem inferir aliases, storage ou disjunção.
KillAuthority mantém MUST comprovado como única autoridade genérica de kill.
[W0](../ep-w0-authority.md), [W1](../ep-w1-qualification.md),
[W2](../ep-w2-qualification.md), [W4](../ep-w4-qualification.md),
[W5](../ep-w5-qualification.md) e
[remediação](../ep-r1-representation.md) preservam REDs, gates e limites.

Os cinco PRs da campanha foram aprovados e mergeados em 2026-09-16.
O FAST final da remediação passou 478 testes / 83 suítes; os 49 E2Es W5
foram reutilizados nos seus snapshots e não relabelados como rerun.
REAL CASE = NOT AVAILABLE; nenhuma alegação 4/4.
