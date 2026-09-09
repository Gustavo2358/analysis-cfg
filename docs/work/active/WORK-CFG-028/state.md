# WORK-CFG-028 — Estado

## Onde estamos

W1 APPROVED pelo humano no HEAD b84389b6ccf94c259774b82a99bc7296278b65c0.
W2 IMPLEMENTED / AWAITING_HUMAN_REVIEW; authorized_wave=2; current_checkpoint=WAVE_2.
[Autorização append-only](../../evidence/WORK-CFG-028/wave-2/authorization.json).
Branch feat/cp5-dataflow-engine, mesmo PR #12 OPEN/DRAFT, sem auto-merge.
W3–W5 NOT_STARTED / NOT_AUTHORIZED.

## Verde conhecido

183 testes Maven, zero failures/errors/skips; fast 134 testes de harness.
Architecture, semantic, integration PASS. Performance/full executam W1/W2 PASS,
retornam UNAVAILABLE/3 por W3–W5. W1 fontes/bytecode e pins preservados.
26 mutantes válidos: compilação, RED nominal, restore byte-exact, segundo GREEN;
23 produtivos e 3 dos oracles sintéticos. Tentativas anteriores preservadas.
[Relatório, métricas, corpus e logs](../../evidence/WORK-CFG-028/wave-2/validation.md).

## Restante

Recibo de CI do HEAD final publicado no mesmo PR #12, conforme política de árvore
exata; CI anterior não valida novo HEAD. Após o recibo, review humano explícito W2.
Nenhuma autorização W3–W5; sem merge, auto-merge, ready ou início automático.

## Descobertas que afetam o plano

Siblings read-only, JARs/pins W1 byte-exact. Convergência matemática sem caps;
Entry não é frame local. Nenhuma semântica AIR específica nesta Wave.
Retenção conta raízes/slots/handles lógicos; não são bytes físicos de heap.
Um mutante inicialmente sobrevivente exigiu contracaso permanente de edges paralelas.
