# Pipeline de construção de CFG

## Entrada e pré-condições

O caller entrega uma `air-java Publication` imutável. A leitura de bytes não faz
parte deste pipeline. O preflight chama `AirValidator` também para objetos recebidos
em memória; transporte inválido é tratado antes. Modelo compartilhado não implica
confiar cegamente em uma instância criada pelo caller.

## Pipeline e fronteira implementada

1. Executar `AirValidator` sem mutar a Publication; propagar invalidade e validação
   incompleta com seus diagnósticos. Não duplicar as regras AIR localmente.
2. Verificar versão e capabilities; aplicar ProjectionPolicy das opções e suporte
   ao slice. KNOWN_SUBSET default admite COMPLETE/PARTIAL; STRICT exige COMPLETE.
   Publicação parcial válida é distinta de integridade inválida. Não reconstruir nomes.
3. Indexar labels, operations e entries uma vez por namespace. Não ordenar para
   inferir execução. Preservar todo o inventário publicado, inclusive conteúdo sem predecessor.
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

CFG-FIRST, WORK-CFG-022 e WORK-CFG-006 executam os passos necessários para Entry, instructions,
Jump, Branch, Return e Halt. CoreCfgProjection substitui CfgFirstProjection como único
caminho de produção; indexa labels namespaced uma vez por Unit e retém AIR imutável.
Entries usam initialLabel; Jump usa destination; Branch usa trueDestination e
falseDestination, sem acessar valores do predicate; Return/Halt produzem resultados
distintos. Regras de terminador conservam activationEntry, inclusive em órfãs.
Instructions permanecem na Sequence original, com ordem explícita e correlação dos
pontos. Não há classe adicional de ProgramPoint, reachability ou fallthrough físico.
Outros terminadores e inventário/corpo indisponível são recusados antes do produto.
PARTIAL é admitido somente em KNOWN_SUBSET, conservando o snapshot e suas lacunas;
nenhuma consulta sobre ausência/reachability é autorizada por essa admissão.
A negociação reconhece memory.regions@1 precisamente só para controle, registrando
esse modo no grafo e preservando fatos de memória. Os demais passes acima são futuros.

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

O core ordena Units, Entries e Sequences por IDs apenas para determinismo da
representação: O(N log N + T), com T incluindo duas regras por Branch × Entry, uma por outro terminador × Entry
da Unit e uma regra de entrada por Entry. HaltExit é materializado
uma vez por ocorrência. Memória derivada O(N + T), sem deep copy da AIR.
Esse custo é da projeção e validação do produto, não uma promessa sobre o custo
interno do AirValidator upstream. Essa expansão é explícita
no tamanho de saída, não enumeração de ativações dinâmicas; performance permanece
sem gate implementado.

## Invariantes entre passes

Nenhum fato suportado desaparece. Nenhuma referência quebrada é reparada.
Nenhum UNKNOWN vira controle ordinário. Um refinamento posterior gera resultado
correlacionado/revisado; não muta IR de entrada nem deixa análises penduradas numa
revisão antiga. Fontes: AIR §01, §04–06 e §08, em [índice](../sources/index.md).
