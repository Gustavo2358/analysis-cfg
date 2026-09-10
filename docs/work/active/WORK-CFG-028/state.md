# WORK-CFG-028 — Estado

## Onde estamos

W1/W2/W3 APPROVED; W3 reviewed head 855628200fba3851493991cec869dee899e82299.
W3-F1/W3-F2 RESOLVED por review humano. W4 IMPLEMENTED / AWAITING_HUMAN_REVIEW;
authorized_wave=4, current_checkpoint=WAVE_4. W5 NOT_STARTED / NOT_AUTHORIZED.
[Validação W4](../../evidence/WORK-CFG-028/wave-4/validation.md).

## Verde conhecido

240 testes Maven, 47 + 97 testes de harness, architecture/semantic/integration PASS.
Performance W1–W4 PASS, agregado UNAVAILABLE/3 pela W5. 26 mutantes compiláveis
RED/restauração exata/segundo GREEN; 27 linhas S5/S6/S16; S8/F3 e GC/JFR reais.
Evidências históricas W1–W3 preservadas. Full também retornou UNAVAILABLE/3 após W1–W4 PASS; scope/manifest/diff verificados.
CI final tem recibo próprio no PR #12 e .harness-results/WORK-CFG-028/wave-4/remote-ci.json.

## Descobertas que afetam o plano

W3-PERF-01 e W3-METRICS-01 continuam não bloqueantes e abertos.
W4-BINDING-01 implementado para review. Única adição à fundação: seleção estreita de
Entries sobre o mesmo índice W1; solver/domínio/replay W2/W3 byte-exact.

## Restante

Aguardar review humano do HEAD final da W4 no mesmo PR #12 draft.
Nenhum trabalho W5 está autorizado; não iniciar a próxima Wave.
