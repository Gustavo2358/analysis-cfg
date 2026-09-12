# analysis-cfg-json 2.0.0 — extensão de controle Invoke

Decisão explícita de WORK-CFG-033, remediação do PR #18: o wire de Invoke usa
`schema: analysis-cfg-json`, `schemaVersion: 2.0.0`. A expansão dos conjuntos
fechados de tokens é incompatível com leitores estritos do contrato
[1.0.0](cfg-json-v1.md); por isso a decisão é uma **major**, não uma minor nem
uma alteração retroativa do domínio v1. O contrato v1 e seus goldens permanecem
com os bytes originais.

## Domínios e diferenças

| Campo | 1.0.0 | 2.0.0 |
| --- | --- | --- |
| `terminator.kind` | JUMP, BRANCH, RETURN, HALT | JUMP, BRANCH, RETURN, HALT, **INVOKE** |
| `transition.kind` | ENTRY, JUMP, BRANCH_TRUE, BRANCH_FALSE, RETURN, HALT | ENTRY, JUMP, BRANCH_TRUE, BRANCH_FALSE, RETURN, HALT, **INVOKE_NORMAL** |
| Envelope, IDs, correlações, ordem e encoding | Contrato v1 | Mesmas regras v1, com `schemaVersion: 2.0.0` |

INVOKE é um terminador com `operation: OperationId` da operação AIR original.
INVOKE_NORMAL é uma transição com `from`, `to` e `activationEntry` nas mesmas
formas e ordem dos demais kinds. A transição representa somente o outcome
`Normal(label)` conhecido da slice autorizada em [controle core](../domain/core-control.md).
Não representa chamada ao callee nem materializa arestas para controle aberto.
A admissão do grafo permanece no kernel; a versão do wire não amplia essa slice.

Todos os campos, domínios de IDs, campos obrigatórios, ordem das arrays e
propriedades, escaping, UTF-8 sem BOM/newline, limites operacionais e regras de
publicação de arquivo do contrato v1 são incorporados em v2 sem outras mudanças.
Não há campos novos ou remoções. Novos tokens/versões futuras exigem outra decisão
explícita: v2 não é um escape para qualquer terminador AIR.

## Seleção determinística no writer

O writer seleciona o **menor contrato necessário pelo produto CFG**:

1. Mapeia os terminadores de todos os SequenceNodes e os kinds de todas as
   transições, incluindo nós órfãos e grafos sem transições.
2. Cada mapping explícito declara o token e se exige a extensão v2. Esses mesmos
   mappings governam a seleção e a emissão, evitando duas tabelas independentes.
3. Se algum token exige a extensão, emite 2.0.0; caso contrário, emite 1.0.0.
4. Domínio não suportado falha explicitamente; nunca é omitido nem rotulado v1.

A inspeção é O(nodes + transitions), com estado adicional constante. Não depende
de nomes, arquivos, callers, flags, locale, texto COBOL, estado anterior do writer,
reachability ou da simples presença de Invoke na Publication AIR retida. Somente
o domínio dos elementos efetivamente publicados no grafo decide a versão.

O writer não oferece downgrade forçado: um produto com Invoke não pode escolher
1.0.0. Um grafo inteiramente representável em v1 continua emitindo v1, byte-exact,
inclusive quando produzido pelo mesmo writer logo após um grafo v2. Consumidores
selecionam as versões que suportam explicitamente; v2 preserva as formas antigas,
mas consumidores v1 podem legitimamente recusar o envelope v2.

## Fronteira de produto e evidência

O CFG continua sendo topologia e correlação. Target names, `PROGA`, interpretação,
ValueFact e arestas de dependência pertencem ao produto separado
[analysis-dependency-result 1.0.0](analysis-dependency-result-v1.md). A mudança de
versão CFG não modifica esse contrato, PossibleValues, solver, lattice ou planner.

O commit inicial W1D `1ab16bdeae8d8af23e723d0b239ba191a695764a` emitiu Invoke
incorretamente rotulado 1.0.0. Seus receipts/bytes permanecem evidência histórica
da execução, **não conformidade ao contrato v1**. Os novos testes e receipts da
[remediação](../work/evidence/WORK-CFG-033/remediation/README.md) registram a correção
sem transformar retrospectivamente artifacts em v2.
