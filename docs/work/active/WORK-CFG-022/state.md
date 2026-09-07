# WORK-CFG-022 — estado

## Onde estamos

Promovido com autorização implementation após verificação GitHub/local do 005.
PR #5 MERGED em 2026-09-07T00:02:50Z; WORK/BACKLOG-005 completed e active=[].
Base limpa main=origin/main: 0b0d02a5f913bf90e84730b1e458bac7edb69ee5.
Branch nova feat/cfg-linear-jump-halt. 005 não foi reaberto.
Mains verificadas: IR 122ce54e1b9ef9b00646f93ece409ca8b63bc933;
air-java 6a4091e5394fc22b3d2ada9abbdb530eb3572a58, ambos iguais aos pins.

## Verde conhecido

Fast da promoção PASS (exit 0, 41 testes harness). air-java clean install PASS
(exit 0, 172 checks) em /tmp/analysis-cfg-work-cfg-022-m2.
Oracle EVAL-CFG-028 escrito antes de produção: 22 métodos. RED observado:
`mvn -B -ntp -Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-022-m2
-pl :cfg-kernel -Dtest=EvalCfg028Test test`, exit 1, símbolos HaltExit,
JUMP/HALT, haltExits e preciseControlCapabilities ausentes. Falha anterior de
download Maven não foi contada como RED. Logs temporários fora do repo.

## Restante

Implementação/GREEN, mutantes/challenge, gates, CI e PR.

## Descobertas que afetam o plano

Referência à Sequence e ordem de Instruction satisfazem correlação deste slice.
CopyBytes requer memory.regions@1; decisão restrita ao produto CFG em spec.
Três recusas históricas do 025 precisam evoluir sob autorização; CF1 é preservado.

O-01-STRUCT exige a,b,k do X-01, onde k é invoke. EVAL-CFG-002 permanece
planned com evidência parcial; somente 028 poderá certificar o novo slice.
