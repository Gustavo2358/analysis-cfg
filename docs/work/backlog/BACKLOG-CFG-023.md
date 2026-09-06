# BACKLOG-CFG-023 — E2E mínimo Semantic Product → AIR → CFG

**Estado:** `planned`. **Fase:** `integration`. **Autorização:** backlog não autoriza execução.
Dependências internas: BACKLOG-CFG-005. Dependência externa: `cobol-lower` mínimo e
facts de entrada/terminal do COBOL Semantic Product, ambos ainda inexistentes.

## Problema e objetivo observável

Provar o pipeline mínimo entre produtos sem esperar branch, CLI, adapter AIR JSON ou
o perfil AIR-STRUCTURE@2 inteiro.

## Escopo e estratégia

Orquestrar `cobol-semantic-product.json` mínimo → `cobol-lower` → `Publication` do
`air-java` → `BuildCfg`/CFG-FIRST. O JSON publicado é a entrada cross-repo; o
lowerer não importa `CobolSemanticPort` nem outros tipos Java internos do frontend.
O motivador COBOL é `PROCEDURE DIVISION. GOBACK.`, mas a fronteira do CFG começa
somente na Publication com `Return`. Verificar separadamente lowering e projeção
CFG, com oráculos que nenhum dos dois componentes gera para si próprio.

## Critérios de aceitação

O lowerer consome somente `cobol-semantic-product.json`, sem port Java do frontend
nem AST/symbols/resolution/source; produz a `Publication` compartilhada e válida;
`AirValidator` passa no preflight; o CFG observa `Return → normal exit` com
Unit/Entry scope; nenhuma classe/teste do kernel conhece GOBACK, JSON ou tipos do
frontend.

## Evals e invariantes

EVAL-CFG-020, EVAL-CFG-025 e EVAL-CFG-026. Evidência de cada boundary é separada;
readiness upstream não é fabricada dentro deste backlog.

## Fronteiras e extensibilidade

`proleap-poc` publica `cobol-semantic-product.json`; `cobol-lower` consome esse
artefato e produz `Publication`; `analysis-cfg` consome `Publication`. Somente a
boundary `Publication → BuildCfg` pode ser direta em memória. AIR JSON não é
prerequisite do micro-E2E, mas o Semantic Product JSON é a boundary real entre os
dois repositórios upstream. Nenhum componente retroconsulta o anterior.

## Discovery, checkpoints e handoff

Só promover quando a dependência externa existir e houver autorização bilateral
aplicável. Registrar commits/APIs reais então observados; não criar lowerer dentro
do `analysis-cfg` nem marcar prerequisite externo como concluído por documentação.

## Fora de escopo

Sem IF/branch, jump/halt, CLI, AIR JSON adapter, AIR-STRUCTURE@2 completo, MOVE,
CALL, dataflow ou redefinição de BACKLOG-CFG-017.

## Evidência de conclusão

Pins dos componentes, gates reais de ambos os lados, expected independente,
comandos/exit codes e review. Enquanto o lowerer não existe, o item permanece
planejado.
