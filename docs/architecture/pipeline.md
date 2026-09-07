# Pipeline de construção de CFG

## Entrada e pré-condições

O caller entrega uma `air-java Publication` imutável. A leitura de bytes não faz
parte deste pipeline. O preflight chama `AirValidator` também para objetos recebidos
em memória; transporte inválido é tratado antes. Modelo compartilhado não implica
confiar cegamente em uma instância criada pelo caller.

## Pipeline e fronteira implementada

1. Executar `AirValidator` sem mutar a Publication; propagar invalidade e validação
   incompleta com seus diagnósticos. Não duplicar as regras AIR localmente.
2. Verificar versão, capabilities, opções e escopo aceitos pelo consumer. Distinguir
   publicação parcial válida de integridade inválida. Não reconstruir nomes.
3. Indexar labels, operations e entries uma vez por namespace. Não ordenar para
   inferir execução. Manter inventário completo, inclusive conteúdo sem predecessor.
4. Projetar inicialmente uma Sequence por nó CFG próprio, com correlação explícita.
   Preservar ordem de operações e pontos before/after(outcome).
5. Interpretar terminadores e materializar transições conhecidas com labels de
   branch/case/outcome. Não avaliar memória, inferir reachability por valores ou
   juntar alternativas distintas apenas porque têm o mesmo destino.
6. Publicar saídas tipadas e fronteiras abertas. Quando houver suporte contextual,
   preservar regras de invocação/retorno e escopo; caso contrário usar fallback ou
   incompatibilidade honesta. Divergência não vira saída normal alcançável.
7. Validar produto derivado; publicar grafo, índices de navegação, mapeamentos,
   capabilities utilizadas, premissas, gaps e precisão.

`CFG-FIRST` está implementado e executa os passos necessários para
Entry/Sequence/Return e normal exit, inventariando todas as sequences e recusando capability
fora do slice de modo explícito. Não cria fallthrough para completar o grafo.
`CfgFirstProjection` indexa labels namespaced uma vez por Unit e retém fatos AIR
imutáveis. Entries usam somente initialLabel; Return emite transições condicionadas
por Entry da ativação. Instructions, outros terminadores e inventário/corpo
indisponível são recusados antes de publicar produto. Os demais passes acima
continuam direção futura, sem claim de implementação.

Leaders não precisam ser redescobertos: cada label já inicia Sequence e não pode
entrar em seu interior. Blocos máximos, remoção de nós e coalescing ficam adiados.
BFS/DFS para consulta futura de alcance não é pré-requisito para emitir todas as
arestas locais conhecidas. Não exigir topological sort: a entrada admite ciclos.

## Ordem e custo

Para projeção estrutural com targets finitos materializados, meta de custo:
O(quantidade de fatos lidos + transições emitidas), sob lookup indexado. Não chamar
isso de O(N) omitindo dispatch com muitos cases ou expansão de fronteira aberta.
Preservar escopos abertos simbolicamente evita cross-product obrigatório.
Controle contextual tem custo próprio, a justificar no respectivo discovery.

CFG-FIRST ordena Units, Entries e Sequences por IDs apenas para determinismo da
representação: O(N log N + T), com T incluindo uma regra Return por Sequence/Entry
da Unit. Memória derivada O(N + T), sem deep copy da AIR. Essa expansão é explícita
no tamanho de saída, não enumeração de ativações dinâmicas; performance permanece
sem gate implementado.

## Invariantes entre passes

Nenhum fato suportado desaparece. Nenhuma referência quebrada é reparada.
Nenhum UNKNOWN vira controle ordinário. Um refinamento posterior gera resultado
correlacionado/revisado; não muta IR de entrada nem deixa análises penduradas numa
revisão antiga. Fontes: AIR §01, §04–06 e §08, em [índice](../sources/index.md).
