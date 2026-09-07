# WORK-CFG-022 — estado

## Onde estamos

Promovido com autorização implementation após verificação GitHub/local do 005.
PR #5 MERGED em 2026-09-07T00:02:50Z; WORK/BACKLOG-005 completed e active=[].
Base limpa main=origin/main: 0b0d02a5f913bf90e84730b1e458bac7edb69ee5.
Branch nova feat/cfg-linear-jump-halt. 005 não foi reaberto.
Mains verificadas: IR 122ce54e1b9ef9b00646f93ece409ca8b63bc933;
air-java 6a4091e5394fc22b3d2ada9abbdb530eb3572a58, ambos iguais aos pins.

## Verde conhecido

Air-java pinado clean install PASS (172 checks), Maven repo isolado
/tmp/analysis-cfg-work-cfg-022-m2. Fast promoção PASS (41 harness).
RED observado por API ausente antes de produção, commit 6908ff9, exit 1;
falhas de download não contaram como RED. GREEN: 20 testes do 025 + 22 do 028,
zero skips, exit 0. Semantic atualizado PASS (42 métodos e 61 fixtures negativas).
Mutantes A/B/C/D e E-reordenação/E-perda: cada um exit 1 com assertion failure,
zero errors, mesmo sem guarda de endpoints; arquivos restaurados em finally.
O 002 permanece planned por O-01-STRUCT/X-01 com invoke. 028 implemented.
Gates finais locais: docs/harness/fast/architecture/semantic, clean test/verify e
 diff --check exit 0. Performance/integration exit 3; full exit 3 após
fast/architecture/semantic PASS. 60 testes Java, 42 semânticos, zero skip.
Runtime local Temurin 25.0.4, target 21 sem preview; CI 21 ainda pendente.
Challenge: skipTests fez semantic falhar (exit 1); Branch injetado por nome
qualificado passou compilação/testes mas architecture rejeitou bytecode (exit 1).
Tudo restaurado antes dos gates finais. Review do diff sem artefatos build/IDE,
upstreams/repos Maven ou temporários. Navegação O(1), registry intacto e nenhum
controle por posição/nome, perda de contexto, branch/dataflow ou claim de perfil.

## Restante

Commit/push da implementação, CI Temurin 21, encerramento documental e PR novo.

## Descobertas que afetam o plano

Referência à Sequence e ordem de Instruction satisfazem correlação deste slice.
CopyBytes requer memory.regions@1; decisão restrita ao produto CFG em spec.
Três recusas históricas do 025 precisam evoluir sob autorização; CF1 é preservado.

O-01-STRUCT exige a,b,k do X-01, onde k é invoke. EVAL-CFG-002 permanece
planned com evidência parcial; somente 028 poderá certificar o novo slice.
