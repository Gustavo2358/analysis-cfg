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

## Primeiro slice

`CFG-FIRST` interpreta apenas uma Entry cujo `initialLabel` referencia uma Sequence
terminada por `return`. Emite entrada, nó correlacionado e saída normal escoped por
Unit/Entry. Outras sequences permanecem inventariadas; nenhuma posição física cria
aresta. Label pendente é rejeitado no preflight, e terminador ausente não recebe
fallthrough reparador. `halt` permanece diferente de `return` e só entra em slice
posterior.

## Branch e seleção

`branch` exige predicate com `TypeRef=known(bool)`. Não é necessário resolver seu
valor para construir os dois destinos: `unknown(known(bool),...)` preserva TRUE e
FALSE. `unknown_type(...)` não pode ser presumido booleano e torna esse uso
`INVALID_IR`; não há default, coerção ou inferência a partir do terminador.
`EXACT` na regra local de controle não significa que todos os caminhos combinados
são concretamente viáveis. O MVP não poda branches por propagação de constantes.
Cases duplicados são inválidos; case order não estabelece prioridade. Dispatch
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
