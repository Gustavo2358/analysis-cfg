# WORK-CFG-028 — Estado

## Onde estamos

W1/W2/W3/W4 APPROVED. W4 reviewed head 21d65d08512f1fb8a945009c2919946a61566eed.
W4-F1 e W4-BINDING-01 RESOLVED; reviews REQUEST_CHANGES preservados.
W5 IMPLEMENTED / AWAITING_HUMAN_REVIEW; authorized_wave=5, checkpoint=WAVE_5.
CP5 AWAITING_FINAL_HUMAN_REVIEW. Mesmo PR #12 OPEN/DRAFT;
CP6 NOT_STARTED / NOT_AUTHORIZED.

## Verde conhecido

261 testes Java no Maven clean verify (243 preservados + 18 W5); fast 47+101+14.
Scope/fast/architecture/semantic/integration/performance/full e diff-check PASS.
CP4E A/B, CP3 e generic-overwrite frescos; memória/arquivo semanticamente e em bytes
iguais. Campanha final: 19 mutantes válidos, RED, restore byte-exact e segundo GREEN.
Escala N/2N/4N, output 75.508.329 bytes, GC/JFR externo e liberação observados.
[Validação e evidência bruta](../../evidence/WORK-CFG-028/wave-5/validation.md).

## Restante

Recibo remoto do HEAD final será publicado no mesmo PR #12 depois de commits/push
com os dois checkouts/trees efetivamente coletados. Evidência local não afirma CI
remoto ainda não executado; o comentário final é a referência sem autorreferência.
Próximo checkpoint: review humano final W5/CP5. Nenhuma aprovação automática,
merge/auto-merge/ready ou início de CP6.

## Descobertas que afetam o plano

Reader W5 sem pre-read cap local; codec AIR de 16 MiB e limites AirValidator
continuam EXTERNAL SIZE-CAP DEBT. Writer W5 não herda o cap CFG de 64 MiB.
W3-PERF-01 e W3-METRICS-01 seguem abertos e não bloqueantes.
Snapshots before/after dos cinco siblings idênticos, inclusive untracked prévios
em artefatos-e2e. Pins e produção/testes/POMs anteriores preservados, exceto adição
exata de três módulos no parent POM.
