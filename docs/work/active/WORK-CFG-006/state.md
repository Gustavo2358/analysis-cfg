# state

## Onde estamos

WORK-CFG-006 active, implementation autorizada. Promoção/oracle RED commitados em
93dff9b após main limpa 50847a27628e90665eaa04871c58d0c27bcc4836 e PR #6 MERGED.
Branch feat/cfg-structural-branch. Índice reconciliado; histórico/estado do 022 intactos.
CoreCfgProjection projeta Branch por BRANCH_TRUE/BRANCH_FALSE e CfgGraph valida
endpoints/contexto. Sem tipo adicional, type checker ou avaliação de valores.

## Verde conhecido

Upstreams sem delta: analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933;
air-java 6a4091e5394fc22b3d2ada9abbdb530eb3572a58. clean install pinado: exit 0,
172 checks, repo /tmp/analysis-cfg-work-cfg-006-m2.
RED 029: exit 1 por kinds ausentes antes de produção. GREEN: 25 testes/zero skip.
Mutantes A–G: cada um exit 1, uma assertion failure, zero errors, guarda de endpoints
desativada temporariamente; fontes restauradas. Metamorfismos 1–4 verdes.
Fast: exit 0 (41 harness); semantic: exit 0, 17+22+25=64 testes, 95 fixtures do detector.

## Restante

Bateria local final concluída: docs/harness/fast/architecture/semantic, clean test,
clean verify e diff --check: exit 0. Performance/integration/full: exit 3; full
passou fast/architecture/semantic e parou em performance. Architecture: 82 testes,
13 fontes/21 classfiles. Restam push/PR, CI e lifecycle; MVP local ainda não promovido.

## Descobertas que afetam o plano

A expansão de Kind quebra o switch exaustivo do 028; sua recusa de Branch tornou-se
obsoleta. Adaptação mínima sob a autorização de Branch: mesmos 22 métodos/fixtures,
switch rejeita Branch no observer local e lista de unsupported conserva exatamente
Dispatch/Invoke/Raise/Opaque. Pergunta opcional não respondida; interpretação comunicada
antes do ajuste. 025 byte a byte intacto. Toda prova positiva Branch no 029.
EVAL-CFG-004 satisfeito por O-03-STRUCT/O-27; 003/005/014 continuam planned, pois
O-74-STRUCT amplo, invocador de O-19-STRUCT e O-66 não estão integralmente provados.

Challenge dos gates: skipTests em semantic → exit 1; Dispatch qualificado sem import
compila e passa testes, mas architecture rejeita bytecode → exit 1. Restaurado.
Nenhum finding de semântica, invariantes ou fronteiras ficou sem correção.
