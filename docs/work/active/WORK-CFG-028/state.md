# WORK-CFG-028 — Estado

## Onde estamos

WAVE_1 STARTED / AUTHORIZED. Review humano aprova CORE-SIZE-001 no HEAD
4aeb4c0ad086ec4fc8911879b13139d84ca57bc9 e autoriza somente índice/session e CP5-F01.
[Autorização append-only](../../evidence/WORK-CFG-028/wave-1/authorization.json).
Branch feat/cp5-dataflow-engine; PR #12 confirmado OPEN/DRAFT nesse mesmo HEAD.
W2–W5 NOT_STARTED / NOT_AUTHORIZED; authorized_wave=1.

## Verde conhecido

W1 implementada e validada localmente: Maven 165 testes; fast 132; arquitetura,
semântica CFG/W1, integração/CLI e performance W1 PASS. Full global UNAVAILABLE/3
com W1 PASS e W2–W5 ausentes. Campanha de 15 mutantes + displayName adicional:
compile/RED/restore/segundo GREEN. CP5-F01 corrigido; pins/siblings preservados.
[Evidência e limites](../../evidence/WORK-CFG-028/wave-1/validation.md).

## Restante

Publicar este conteúdo no PR #12 draft, confrontar CI real e registrar o recibo.
Após CI, marcar W1 IMPLEMENTED/AWAITING_HUMAN_REVIEW e validar o HEAD final publicado.
Nenhuma autorização W2–W5; sem merge, auto-merge ou ready.

## Descobertas que afetam o plano

Retenção mede objetos/arrays lógicos por identidade, não bytes de heap total.
Refs W1 cobrem ObjectPlace/CellBinding e owners/initialLabel; não interpretam effects,
aliases ou premissas de valores. Dívidas externas de capacidade continuam explícitas.
