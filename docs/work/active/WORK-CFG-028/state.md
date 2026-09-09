# WORK-CFG-028 — Estado

## Onde estamos

W1 APPROVED em b84389b6. W2 APPROVED em 0202c7424db04a1d83fb5e35fce055ea81f5b8fa.
W3 STARTED / AUTHORIZED; authorized_wave=3; current_checkpoint=WAVE_3.
[Autorização append-only](../../evidence/WORK-CFG-028/wave-3/authorization.json).
Mesma branch feat/cp5-dataflow-engine e PR #12 OPEN/DRAFT, sem auto-merge.
W4/W5 NOT_STARTED / NOT_AUTHORIZED.

## Verde conhecido

Checkout inicial limpo e HEAD aprovado/remoto coincidentes. Evidências W1/W2
preservadas; gates W3 ainda não executados. Pins permanecem os da W2.

## Restante

Implementação, testes, escala, challenges, gates locais e CI do HEAD final;
parar para review humano W3.

## Descobertas que afetam o plano

Subject ObjectId resolve Cell canônica; aliases compartilham estado. Multibase
exige DisjointStorage cobrindo todas as bases. Siblings read-only.
