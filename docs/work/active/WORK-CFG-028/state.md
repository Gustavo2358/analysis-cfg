# WORK-CFG-028 — Estado

## Onde estamos

W1/W2/W3 APPROVED. Review humano do HEAD 330d63427c0905e8ef140924b643e9fb012c73de:
W4 REQUEST_CHANGES, blocker único W4-F1. authorized_wave=4, checkpoint=WAVE_4.
[Review focal](../../evidence/WORK-CFG-028/wave-4/review-f1/review.json).
W5 NOT_STARTED / NOT_AUTHORIZED; mesmo PR #12 OPEN/DRAFT.

## Verde conhecido

O baseline revisado conserva 240 testes Maven, 144 de harness, 26 mutantes e CI
push/pull_request verdes. Essa evidência não cobre zero matches em SiteQuery.

## Descobertas que afetam o plano

Registrar o binding declarado por SiteQuery antes da seleção de sites. Bucket
vazio ou filtro que rejeita todos produz batch conhecido vazio, sem sentinela.
Escolha: executar a análise declarada e materializar batch COMPLETE vazio via W3;
consumer sem sites completa sem callbacks/facts, conforme dependências.

## Restante

Não reabrir arquitetura/cache/binding/F3/FactSink/fundação/probes/caps. W4-BINDING-01
RESOLVED; W3-PERF-01 e W3-METRICS-01 não bloqueantes. Provar RED, correção, challenge,
gates e CI do HEAD final; depois parar para review humano, sem W5.
