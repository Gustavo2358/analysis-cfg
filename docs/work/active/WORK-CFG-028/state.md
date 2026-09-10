# WORK-CFG-028 — Estado

## Onde estamos

W1 APPROVED em b84389b6ccf94c259774b82a99bc7296278b65c0.
W2 APPROVED em 0202c7424db04a1d83fb5e35fce055ea81f5b8fa.
W3 REQUEST_CHANGES / AUTHORIZED; authorized_wave=3; current_checkpoint=WAVE_3.
W4/W5 NOT_STARTED / NOT_AUTHORIZED. Mesma branch feat/cp5-dataflow-engine e PR #12
OPEN/DRAFT, sem auto-merge. A próxima Wave exige review humano explícito deste HEAD.

## Verde conhecido

[Evidência W3](../../evidence/WORK-CFG-028/wave-3/validation.md): 205 testes Maven,
136 de harness, 48 grafos/213 pontos com oráculos independentes, 28 mutantes
compiláveis distintos, escala até 200k operações/10k candidatos e memória externa.
Fast/architecture/semantic/integration/Maven PASS. Performance/full: W1/W2/W3 PASS,
UNAVAILABLE/3 para W4/W5. CI final e classificação de checkout no recibo do PR #12.

## Descobertas que afetam o plano

W1/W2 Java, CFG/transporte/CLI legados e pins preservados; siblings read-only.
Limites: scalar-text-direct@1, premissa multibase explícita, source conservador por
Unit e custo potencialmente quadrático de união crescente. Nenhum consumer/resolver
ou writer/CLI de dataflow implementado. Escopo encerrado para review humano W3.

## Restante

Corrigir W3-F1 (support/origin/premises) e W3-F2 (source gaps por Entry/Cell),
validar e atualizar o mesmo PR draft. Não iniciar W4.

Review focal recebido no HEAD 8cb55b86c83644e4727cc532787a518775d7e868;
[evidência](../../evidence/WORK-CFG-028/wave-3/review-f1-f2/review.json).
