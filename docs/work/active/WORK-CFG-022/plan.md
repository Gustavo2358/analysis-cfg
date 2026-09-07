# WORK-CFG-022 — plano

## Fatiamento

1. Lifecycle anterior/Git/upstreams e API real; promover somente 022.
2. Expected próprio, classes/adversariais, testes RED observados; commit focal.
3. Evoluir projector/nós/transições e negociação restrita ao controle; GREEN.
4. Mutantes A–E, challenge, inventários exatos dos gates e docs duráveis.
5. Gates locais, commit/push/CI; arquivar somente com critérios satisfeitos.
6. Commit final, HEAD verde, PR novo e human review; sem merge/004/006.

## Dependências e superfície

Foundation/CFG-FIRST concluídos. Kernel domain/application, testes em memória,
gates Python explícitos, workflow existente e documentação vinculada.
Nenhum adapter ou upstream alterado. Sem nova classe de ProgramPoint.

## Migração e artefatos

CoreCfgProjection substitui CfgFirstProjection. As três recusas históricas de
Jump/Halt/instructions em EVAL-CFG-025 devem evoluir para asserts positivos sob
esta autorização, preservando os outros 17 métodos e todas as obrigações CF1.
EVAL-CFG-028 estreito cobre obrigações de Halt/contexto que evals amplos não isolam.
EVAL-CFG-002 só será implemented se seus oráculos integrais puderem ser sustentados.
005/013/014 não recebem completion pelo linkage; 004/006 permanecem planned.
