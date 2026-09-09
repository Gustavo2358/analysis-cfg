# WORK-CFG-028 — Estado

## Onde estamos

W1 APPROVED pelo humano no HEAD b84389b6ccf94c259774b82a99bc7296278b65c0.
W2 STARTED / AUTHORIZED; authorized_wave=2; current_checkpoint=WAVE_2.
[Autorização append-only](../../evidence/WORK-CFG-028/wave-2/authorization.json).
Branch feat/cp5-dataflow-engine, mesmo PR #12 OPEN/DRAFT, sem auto-merge.
W3–W5 NOT_STARTED / NOT_AUTHORIZED.

## Verde conhecido

Baseline W1 aprovada: 165 testes Maven; gates W1 e regressões registrados na
[evidência histórica](../../evidence/WORK-CFG-028/wave-1/validation.md).
Implementação e gates W2 ainda não executados.

## Restante

Solver genérico, oracles forward/backward/recomposição/concreto, S4b/escala,
challenges, gates, push e CI do HEAD final; parar para review humano W2.

## Descobertas que afetam o plano

Siblings read-only, pins W1 preservados. Convergência matemática sem caps;
Entry não é frame local. Nenhuma semântica AIR específica nesta Wave.
