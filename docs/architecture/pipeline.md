# Pipeline de construção de CFG

## Entrada e pré-condições

O adapter entrega uma Publication imutável e fechada no modelo IR. A leitura de
bytes não faz parte deste pipeline. Dados semântico-estruturais são validados pelo
contrato IR; transporte inválido é tratado antes. Modelo compartilhado não implica
confiar cegamente em uma instância criada pelo caller.

## Passes propostos

1. Validar versionamento, namespaces, referências, units/entries, Sequence e
   terminador único; classificar capabilities/precisão. Distinguir publicação
   parcial válida de integridade inválida. Não reconstruir nomes.
2. Indexar labels, operations e entries uma vez por namespace. Não ordenar para
   inferir execução. Manter inventário completo, inclusive conteúdo sem predecessor.
3. Projetar inicialmente uma Sequence por nó CFG próprio, com correlação explícita.
   Preservar ordem de operações e pontos before/after(outcome).
4. Interpretar terminadores e materializar transições conhecidas com labels de
   branch/case/outcome. Não avaliar memória, inferir reachability por valores ou
   juntar alternativas distintas apenas porque têm o mesmo destino.
5. Publicar saídas tipadas e fronteiras abertas. Quando houver suporte contextual,
   preservar regras de invocação/retorno e escopo; caso contrário usar fallback ou
   incompatibilidade honesta. Divergência não vira saída normal alcançável.
6. Validar produto derivado; publicar grafo, índices de navegação, mapeamentos,
   capabilities utilizadas, premissas, gaps e precisão.

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

## Invariantes entre passes

Nenhum fato suportado desaparece. Nenhuma referência quebrada é reparada.
Nenhum UNKNOWN vira controle ordinário. Um refinamento posterior gera resultado
correlacionado/revisado; não muta IR de entrada nem deixa análises penduradas numa
revisão antiga. Fontes: AIR §01, §04–06 e §08, em [índice](../sources/index.md).
