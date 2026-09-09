# CP5 — equações, contexto e propagação incremental

Contrato aceito por H1/H4/H5/R1; [ADR-0011](../architecture/decisions/ADR-0011.md).
Pseudocódigo abaixo é especificação, não engine implementada.

## Contexto e equações

Uma execução usa snapshot, configuração, direção e Entry fixos. Somente arestas
com activationEntry correspondente pertencem à view. Inventário não é reachability.
Forward usa bottom de ponto inalcançado `⊥p`, diferente de alcançado desconhecido:

```text
IN[e,n]  = Boundary[e,n] ⊔ join(EdgeTransfer[t](OUT[e,p]))
           para t:p→n na Entry e
OUT[e,n] = BlockTransfer[n](IN[e,n])
```

Boundary contribui uma vez no EntryNode, nunca é reaplicado na Sequence inicial em
back-edge. BlockTransfer/EdgeTransfer forward preservam ⊥p. Primeiro alcance mesmo
sem bindings é mudança e deve ser processado. Dois braços de Branch são possíveis,
inclusive predicado literal e destinos iguais; sem análise de viabilidade de condições.

## Protocolo obrigatório

```text
ao retirar P:
    limpar membership(P)
    next = BlockTransfer(P, IN[P])
    se já publicado e semanticamente equivalente a OUT[P]: encerrar pop
    publicar OUT[P] = next (primeira publicação ou mudança semântica)
    para cada edge P → S da view:
        contribution = EdgeTransfer(edge, OUT[P])
        newIn, changed = joinInto(IN[S], contribution)
        se changed:
            IN[S] = newIn
            enqueueIfAbsent(S)
```

joinInto produz raiz isolada e change semântico; não muta raízes expostas. Pop lê o
acumulador. OUT igual após primeira publicação não propaga; joinInto unchanged não
tenta enqueue. Membership é removido antes do transfer para permitir self-loop
reenfileirar P com o IN novo; essa versão não pode ser marcada como já consumida.
Conservar handle antigo quando semanticamente equivalente; pointer/hash não são
prova suficiente de desigualdade/igualdade. Duas referências de fronteira por ponto
ativo, sem versões históricas por edge nem snapshots por instruction.

Proibida como baseline produtivo: reler OUT de todos predecessors e recompor IN[S]
a cada pop de S. Essa recomposição existe apenas como oracle lento independente,
com mapas/conjuntos simples e bound matemático em grafos pequenos de teste.
Delta de bindings, retractions, narrowing, alterações de grafo/filtros durante run,
sumários e agendas WTO ficam fora do contrato CP5.

## Leis e convergência

Join associativo, comutativo, idempotente e upper-bound; equivalência coerente com a
ordem; BlockTransfer **e** EdgeTransfer monotônicos; boundary fixo; agenda justa;
domínio de altura finita ou contrato futuro explícito de convergência. Se OUT_old ≤
OUT_new, monotonicidade da aresta faz a contribuição antiga ser subsumida. Acumular
as novas raízes converge ao mesmo menor ponto fixo das equações por recomposição;
não requer distributividade. Kills internos de strong Assign não invalidam essa
ascensão entre iterações, nem autorizam unir OUT novo a valores mortos.

Budget interrompido retorna ANALYSIS_LIMIT com causa/fase. Estados provisórios não
são upper bounds do ponto fixo ainda desconhecido e não saem como facts finais.
STABLE afirma solução do modelo admitido, sem prometer exaustividade da fonte.

## Backward e prova de extensão

Backward usa o dual: OUT acumulado de successors, BlockTransfer reverso publica IN
e entrega contribuições aos predecessors apenas na primeira publicação/mudança.
Reachability de execução continua forward por Entry. Todos os pontos alcançáveis
começam no bottom do domínio backward e são ativados, inclusive SCC sem saída.
Boundary de saída aplica-se onde a análise declarar. Primeira publicação bottom
pode gerar fatos via EdgeTransfer não identidade: não suprimi-la.

W2 prova outra análise finita test-only de máscara gen/kill e join OR, com state de
tipo diferente, duas agendas justas, backward em loop sem exit e EdgeTransfer
monotônico não identidade. Adicionar a segunda análise conserva bytes do solver.
Não apresentar a prova como RD/Liveness de produto. Oracle independente por rodadas
compara todos IN/OUT por Entry/direção, além de expected manuais/metamorfismos.

## Proteção falsificável

S4b força `P1,J,P2,J,...`: N fontes publicam uma vez, domínio finito max(rank) de
estado constante. Esperado **N contribuições a J e zero predecessorContributionReads**;
recomposição custa ~N² leituras. Uma árvore de Branches binárias alcança as fontes;
arestas auxiliares são contadas separadamente, não um terminador AIR inventado.
Chegadas coalescidas, edges paralelas, primeiras publicações, contribuições
subsumidas e múltiplas publicações têm casos próprios. W3 adiciona facts largos:
joinEntriesVisited/alocações podem dominar mesmo com arestas incrementais.
[Probes](../evals/cp5/probes.json) e [métricas](../engineering/cp5-performance.md).
