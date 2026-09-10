# WORK-CFG-028 — Estado

## Onde estamos

W1/W2/W3 APPROVED. W4-F1 IMPLEMENTED / W4 AWAITING_HUMAN_REVIEW;
authorized_wave=4, checkpoint=WAVE_4. REQUEST_CHANGES de 330d634 permanece no histórico.
[Validação focal](../../evidence/WORK-CFG-028/wave-4/review-f1/validation.md).
W5 NOT_STARTED / NOT_AUTHORIZED; mesmo PR #12 OPEN/DRAFT.

## Verde conhecido

243 testes Maven, 47 + 99 de harness; fast/architecture/semantic/integration PASS.
Performance W1–W4 PASS, agregado UNAVAILABLE/3 pela W5. Três oracles novos e três
mutantes compiláveis RED/restauração exata/segundo GREEN. Recibos de full e CI finais
registrados separadamente; não usar CI do HEAD 330d634 como prova da remediação.

## Descobertas que afetam o plano

SiteQuery registra binding antes dos matches; batch conhecido vazio materializa
COMPLETE pela W3. Query factory e consumer não são chamados sem sites. Declaração
inválida falha antes dos filtros; nenhuma sentinela. Produção muda só SitePlanner;
123 arquivos revisados preservados. W4-BINDING-01 RESOLVED; W3-PERF-01/W3-METRICS-01
continuam abertos, não bloqueantes. Não há novo blocker identificado nos gates locais.

## Restante

Novo review humano do HEAD final W4-F1, com recibos finais no PR #12 e em
.harness-results/WORK-CFG-028/wave-4/review-f1/remote-ci.json. W5 não autorizada.
