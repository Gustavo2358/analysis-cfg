# spec

## Problema
A porta atual para no preflight e ainda não publica CFG.

## Objetivo
Materializar EVAL-CFG-025 com IDs CFG próprios, correlações AIR e inventário imutável.

## Domínio de entrada suportado
Publication AIR 2.0.0 válida, em memória; Sequences sem instructions com Return.
Múltiplas Units/Entries e Sequences órfãs devem ser preservadas. AIR §04.8 e Operations.Return(Header, List<Expression>) não possuem entryScope.
A Entry da ativação governa a saída. Cada transição RETURN do CFG é condicionada
por activationEntry; inventariar todas não afirma alcance. Uma Sequence compartilhada
continua um só nó. Valores retornados são retidos, nunca avaliados pelo CFG.

## Premissas
IR_GUARANTEED: initialLabel explícito, terminador único, namespaces e imutabilidade
conforme air-java. EXPLICIT_CONTRACT: apenas CFG-FIRST autorizado neste prompt.
Nenhum fato observado apenas em fixture governa produção.

## Comportamento esperado
Entry aponta ao nó de initialLabel; Return deriva saída normal por escopo AIR.
Não há ordem de execução entre Sequences pela posição física. Falha não tem graph.

## Comportamento diante de incerteza
Preservar preflight, invalid/unsupported/limit/incomplete; recusar formas fora do
slice explicitamente. Não reparar referências nem completar semântica.

## Fora de escopo
Jump, Halt, branch/IF, dispatch, invoke, raise, controle local/indireto, operações
lineares como feature, JSON, filesystem, CLI, DOT, lowerer, dataflow, leaders e
coalescing. Nenhum perfil AIR completo é reivindicado.
