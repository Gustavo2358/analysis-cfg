# WORK-CFG-028 — Estado

## Onde estamos

W1 APPROVED em b84389b6ccf94c259774b82a99bc7296278b65c0.
W2 APPROVED em 0202c7424db04a1d83fb5e35fce055ea81f5b8fa.
W3 IMPLEMENTED / AWAITING_HUMAN_REVIEW; authorized_wave=3; current_checkpoint=WAVE_3.
Remediação focal W3-F1/W3-F2 executada. O review REQUEST_CHANGES do HEAD
8cb55b86c83644e4727cc532787a518775d7e868 permanece append-only no lifecycle.
W4/W5 NOT_STARTED / NOT_AUTHORIZED. Mesma branch feat/cp5-dataflow-engine e PR #12
OPEN/DRAFT, sem auto-merge. A próxima Wave exige review e autorização humana explícita.

## Verde conhecido

[Validação focal](../../evidence/WORK-CFG-028/wave-3/review-f1-f2/validation.md):
216 testes Maven, 139 de harness, 11 novos métodos W3 e 18 mutantes compiláveis
focais detectados com restauração byte-exact e segundo GREEN. Escala preserva
10k candidatos, 10k suportes do mesmo valor, 10k Objects com um binding e 200k writes.
GC/JFR comprova liberação de execução/estados/suportes mortos após materialização.
Fast/architecture/semantic/integration/Maven PASS. Performance/full: W1/W2/W3 PASS,
UNAVAILABLE/3 para W4/W5. CI final e classificação de checkout no recibo do PR #12.
A evidência W3 anterior permanece histórica e intacta.

## Descobertas que afetam o plano

Suporte por candidato inclui produtor AIR, origem e premissas condicionais. Strong
Assign elimina suporte anterior; join e equivalência propagam suporte novo mesmo
sem mudança de valor. Entry uncertainties e gaps relevantes de aliases da mesma
Cell mantêm source open separado de model open. Sem heurística de texto de gap.
Todo analysis-kernel, AVL, POMs, CFG/transporte/CLI e pins preservados; siblings
read-only. Limites: scalar-text-direct@1, disjunção multibase explícita, fonte
conservadora por Unit/Entry/Cell e custo quadrático possível de unions crescentes.
W3-PERF-01 e W3-METRICS-01 registrados; W4-BINDING-01 depende de autorização futura.

## Restante

Review humano do HEAD final remediado. Não iniciar W4/W5 nem implementar
consumer/resolver, planner/cache compartilhado ou writer/CLI de dataflow.
