# WORK-CFG-028 — Estado

## Onde estamos

WAVE_1 STARTED / AUTHORIZED. Review humano aprova CORE-SIZE-001 no HEAD
4aeb4c0ad086ec4fc8911879b13139d84ca57bc9 e autoriza somente índice/session e CP5-F01.
[Autorização append-only](../../evidence/WORK-CFG-028/wave-1/authorization.json).
Branch feat/cp5-dataflow-engine; PR #12 confirmado OPEN/DRAFT nesse mesmo HEAD.
W2–W5 NOT_STARTED / NOT_AUTHORIZED; authorized_wave=1.

## Verde conhecido

Baseline fast: 47 testes originais + 83 CP5; nenhuma implementação W1 validada ainda.
[Baseline e pins](../../evidence/WORK-CFG-028/wave-1/baseline.json).

## Restante

Implementar W1 com oracles, challenges compiláveis, métricas/ledger/retenção;
regressões, gates W1, commits/push e recibo CI do HEAD publicado. Parar para review.

## Descobertas que afetam o plano

CfgGraph protege arestas individuais, mas permite inventário mutilado e payload
substituído com IDs iguais. W1 verifica completude e instâncias canônicas.
CP5-F01 ainda aberto até correção produtiva e gate estrito. Pins inalterados;
upstream/transport size-cap debts continuam fora desta Wave.
