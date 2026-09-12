# Semântica estrutural do core

Fonte normativa: Analysis IR §01, §04, §05 e §08 no [índice de fontes](../sources/index.md).
Não importar semântica LLVM ou COBOL apenas por semelhança de nomes.

| Operação | Projeção exigida |
| --- | --- |
| Operação comum | Prossegue dentro da Sequence; preserve ocorrência e operandos |
| `jump` | Transfere somente ao label explícito |
| `branch` | TRUE e FALSE, preservando papéis mesmo quando o destino é igual |
| `dispatch` | Um case por literal semanticamente único e um default explícito |
| `invoke` | Outcomes materializados; normal, exceções, halt, diverge e open conforme contrato |
| `return` | Saída normal da ativação; sem fallthrough local |
| `raise` | Saída excepcional, preservando tag/propagação |
| `halt` | Término da execução pertinente; não retorno de subrotina |
| `opaque` | Alternativas e restante do ControlEnvelope; nunca default inventado |

A ausência de terminador é INVALID_IR. A ausência de um intérprete não transforma
terminador legítimo em erro de source nem autoriza pular a operação.

## Slices implementados

CFG-FIRST (WORK-CFG-005) implementou Entry/Return. WORK-CFG-022 amplia o core
para instructions lineares, Jump e Halt; WORK-CFG-006 acrescenta Branch.
O slice requer Units com corpo disponível e inventário admitido pela
[ProjectionPolicy](../architecture/ports-and-adapters.md#política-de-projeção):
KNOWN_SUBSET default aceita COMPLETE/PARTIAL; STRICT exige COMPLETE. UNAVAILABLE
permanece recusado. A parcialidade original não é elevada e nenhum fato desconhecido
recebe uma aresta apresentada como precisa sem prova. Cada Entry usa
initialLabel; cada Sequence permanece um único nó, inclusive
órfãs. Label pendente é INVALID_IR no preflight; falta de terminador não é reparada.

Assign, HavocMust, HavocMay, Nop e CopyBytes permanecem na Sequence original:
todas as ocorrências, ordem, operandos, headers, origins, precisão e gaps são retidos.
Nenhum efeito de memória é executado ou usado para escolher controle.
JUMP usa somente Jump.destination/LabelId, inclusive backward e self-loop; conserva
activationEntry. Nenhuma posição física, nome ou texto cria successor.

HALT alcança HaltExit próprio por ocorrência AIR, distinto de NormalExit. Tanto
NORMAL quanto ABNORMAL são termination, nunca normal completion da ativação.
O objeto Operations.Halt original é retido; não há origem sintética fabricada.
Uma ocorrência pode ser compartilhada por Entries: o contexto fica na transição
HALT. O destino não tem transições de saída. NormalExits continuam inventariados
por Entry mesmo quando nenhum Return os utiliza; isso não afirma alcançabilidade.

A slice [W1D](../architecture/analysis-dependency-result-v1.md) admite Invoke com um Normal conhecido e remainder NoControl/AllControl, por aresta INVOKE_NORMAL. Outros outcomes continuam fora.

Dispatch, Raise e Local*/IndirectJump permanecem fora
do slice, inclusive em órfãs. Recusa é explícita e correlacionada, sem produto que omita
essas ocorrências. Isso é distinto de projetar fatos suportados de inventário PARTIAL.
As demais linhas da tabela são direção futura.

WORK-CFG-038 consome Opaque com alternativas Jump/Normal/Return conhecidas e
remainder de controle. O CFG conserva o terminador original e suas arestas
OPAQUE_JUMP/OPAQUE_RETURN. A ContextView enumera destinos possíveis dentro do
contexto selecionado a partir do scope AIR, em ambos os sentidos, sem matriz densa.
OPAQUE_UNKNOWN é uma aresta transitória dessa consulta, não uma aresta precisa
publicada no CFG. Invoke sem Normal conhecido também conserva seu bound aberto.
Escopos que permitem saídas além dos nós internos mantêm control uncertainty;
um successor normal conhecido não certifica ausência de saída anormal.

AIR §04.8 e `Operations.Return(Header, List<Expression>)` não possuem seletor
`entryScope`. O retorno segue a Entry da ativação. Por isso a transição RETURN
carrega a condição `activationEntry` e alcança apenas o NormalExit dessa Entry.
Todos os retornos são inventariados para cada Entry da Unit, inclusive órfãos; isso
não afirma que todas essas regras são alcançáveis. Operandos retornados são
preservados pelo mesmo objeto AIR, sem avaliar seus valores.

## Branch e seleção

Implementado no único CoreCfgProjection por duas regras tipadas: BRANCH_TRUE usa
somente trueDestination e BRANCH_FALSE usa somente falseDestination. Ambas preservam
activationEntry, inclusive em órfãs e com destino igual; não há filtro de reachability.
Diamond, vazio, nested e ramo terminante compõem essas mesmas primitives. Join é
Sequence com target explícito, sem nó próprio, fallthrough ou detecção por proximidade.
CfgGraph verifica source Branch, target Sequence/LabelId e contexto na mesma Unit.

`branch` exige predicate com `TypeRef=known(bool)`. Não é necessário resolver seu
valor para construir os dois destinos: `unknown(known(bool),...)` preserva TRUE e
FALSE. `unknown_type(...)` não pode ser presumido booleano e torna esse uso
`INVALID_IR`; não há default, coerção ou inferência a partir do terminador.
`EXACT` na regra local de controle não significa que todos os caminhos combinados
são concretamente viáveis. O MVP não poda branches por propagação de constantes.
Dispatch permanece planejado. Cases duplicados são inválidos; case order não estabelece prioridade. Dispatch
mantém default inclusive para seletor fora dos valores listados.

## Chamadas

`invoke` **é terminador**, não uma operação comum seguida de fallthrough implícito.
No grafo local, o resultado normal é uma possibilidade de retorno, não promessa de
retorno obrigatório. Não inventar `effects=none`, ausência de exceção ou target
resolvido. Outras units podem ser declaradas sem corpo; análise interprocedural de
corpos não é exigência do MVP. Preservar EntryRef/ResourceRef quando conhecidos.

## Ciclos e conteúdo sem predecessor

Ciclos são relações explícitas de labels; não desenrolar loops, exigir aciclicidade
ou deduzir back-edge pela ordem textual. Um bloco sem predecessor da entrada
selecionada permanece no inventário. A prova de inalcançabilidade é posterior,
escoped e sensível a outras entries e fronteiras abertas.

## Risco de graph simplification

Duas alternativas podem ligar os mesmos nós com labels diferentes. Manter
multiarestas semânticas ou agrupar condições explicitamente sem perder distinções.
Um `Set<(from,to)>` que elimina TRUE/FALSE, cases ou exception tags é insuficiente.
`SequenceId`, `CfgNodeId` e `ProgramPoint` não são intercambiáveis.

## memory.regions@1 no papel CFG

AIR §04.5, §08.2/3 e §09.3/6 separam controle de storage/efeitos. Após validação
integral, o core interpreta precisamente a continuação intrassequence de CopyBytes
e conserva todos os seus intervalos, operandos, length e fallback sem executá-lo.
Isso não afirma semântica de memória implementada. O produto declara a capability
exata em `preciseControlCapabilities()`; nenhuma outra versão é presumida.
A regra é core, sem intérprete fictício no SemanticInterpreterRegistry. Capability
local/indireta/desconhecida continua dependendo da negociação existente; registrar
identidade não implementa semântica. O manifesto obrigatório nunca é dispensado.

O transporte de Invoke declara explicitamente [analysis-cfg-json 2.0.0](../architecture/cfg-json-v2.md); grafos do domínio anterior continuam em v1.
