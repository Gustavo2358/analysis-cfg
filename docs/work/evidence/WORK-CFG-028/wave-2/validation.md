# CP5 WAVE_2 — IMPLEMENTED / AWAITING_HUMAN_REVIEW

W1 aprovada pelo humano no HEAD `b84389b6ccf94c259774b82a99bc7296278b65c0`.
[Autorização W2](authorization.json) registrada antes do código no commit
`9a732a2709cdb4e3b5ab6d7ad2ddb3af50849200`; reviews anteriores preservados.
[Baseline/pins](baseline.json), [JARs byte-exact W1](dependency-environment.json).
Implementação Java no commit `70ff866`; inventário de hashes completo identifica
os bytes efetivamente testados, inclusive nas execuções anteriores ao commit.
Uma branch `feat/cp5-dataflow-engine`, um [PR #12 draft](https://github.com/Gustavo2358/analysis-cfg/pull/12).

## Produto e fronteiras

9 fontes / 14 classfiles W2 no analysis-kernel. AnalysisDefinition é SPI de estado
opaco; DataflowSolver compartilha um núcleo com direction. SolverTopology deriva
ordinais contextuais privados dos cursores W1. IN/OUT mantêm ordem do programa:
forward acumula IN/publica OUT; backward acumula OUT/publica IN. Boundary declara
pontos/raízes múltiplos, é unida uma vez; transfer de bloco e de edge são obrigatórios.
Primeira publicação possui flag independente de bottom/equivalência; mudanças
posteriores propagam somente novas raízes. Self-loop reenfileira após limpar membership.

Uma raiz IN e OUT por ponto efetivo; queue int + queued boolean; primeira avaliação
de todo ponto alcançável na view, inclusive SCC backward sem exit. Lookup externo
por ContextView/Node; arrays de states/adjacência por ordinais no hot path. Diretórios
são demandados por contexto, sem matriz all Entries×all Nodes ou Node×Object/Cell.
Sem histórico por edge/iteração/operação, clone de state imposto, retractions ou
recomposição produtiva no pop. Resultado é construído somente com fila vazia; falha
lança erro e não produz STABLE. Sem AnalysisKey/cache/planner/replay de produto.

[Contratos matemáticos, ownership, todas as coleções, custo e retenção](../../../../engineering/cp5-w2-solver-ledger.md).
Join ACI/upper-bound, equivalência coerente, transfers monotônicos, grafo/config/boundary
fixos e convergência justificada são precondições. FIFO é substituível; não é contrato
público. Publicação nova subsume contribuição antiga pela monotonicidade da edge.
Solver não importa tipos AIR concretos; W1/CFG/transport e pins permanecem byte-exact.
[Scope](scope.json), [inventário W2](../../../../evals/cp5/w2-inventory.json).

## Três oracles e corpus

Forward test-only: conjunto finito de valuations sintéticas, constantes e alteração
local; bottom vazio distinto das valuations alcançadas. Backward test-only: máscara
Long de usos antes de definição, reverse gen/kill. Nenhum interpreta Operations AIR.
O witness `use Y; def X; use X` exige IN={Y}, OUT={}, estados de replay de teste
[{Y},{},{X},{}]; `def X; use X` discrimina ordem. Backward propaga IN a OUT do predecessor.

Oracle por recomposição é uma implementação test-only por rodadas, com mapas e
releitura de todos vizinhos. Máquina concreta usa tabelas de transição sobre quatro
valuations e enumera pares finitos ponto/valor. Oracle backward busca usos antes de
redefinição no espaço finito ponto/offset/variável. Não chamam transfer/join/solver
abstratos. O transfer errado SET_X→SET_Y faz ambos solvers abstratos concordarem,
mas falha na inclusão concreta. Always-TOP passa inclusão e falha na precisão mínima.

[Corpus bruto](generated-corpus.json): 80 grafos, seeds 19073–19152, chains, branches,
diamonds/fan-in/fan-out, self-loops, SCCs com/sem saída, múltiplos ciclos/roots/contextos.
1.284 pontos contextuais somados; 2 direções × 4 agendas × 80 = **640 comparações**
de todos IN/OUT e 640 verificações concretas. FIFO, prioridade ordinal, ordem reversa
e permutação determinística concordam semanticamente. Mesmo input/config/schedule
repete métricas determinísticas; agendas diferentes não precisam ter counters iguais.

15 métodos semânticos nominais mais 3 de escala: boundaries múltiplas nos dois lados,
first-bottom, kill/gen, bloco não idempotente, edges não identidade, isolamento,
falha sem resultado, slots órfãos ausentes, roots imutáveis e seleção vazia.

## S4b, escala e métricas reais

[23 medições completas](metrics.json), com elapsed nanos e contagem lógica de retenção.
Árvore binária de Branches real alimenta N fontes de rank escalar e um join J;
agenda adversa alterna publicação de fonte e processamento de J. Não há edge AIR
n-ária inventada. Entregas a J são medidas no callback; auxiliares separadas.

| N | Entregas a J | Entregas totais / edge joins | Pushes = pops | Reads produtivos de predecessor | Reads lentos em J |
| --- | --- | --- | --- | --- | --- |
| 1.000 | 1.000 | 4.000 | 4.002 | 0 | 1.001.000 |
| 2.000 | 2.000 | 8.000 | 8.002 | 0 | 4.002.000 |
| 4.000 | 4.000 | 16.000 | 16.002 | 0 | 16.004.000 |
| 10.000 | 10.000 | 40.000 | 40.002 | 0 | 100.010.000 |

O oracle lento faz N(N+1) reads em J, incluindo avaliação inicial bottom. Mede também
reads/joins totais e pushes/pops próprios. Produção: deliveries=edgeTransferInvocations
=edgeContributionJoins; boundaryJoins separado. joinIntoCalls total soma os dois.
Não se afirma solver O(V+E) universal: mudanças de state, joins, comparação e
transferências entram no custo. maxWorklistSize é observação, sem cap.

S4/S16: linear e cycle em 1k/2k/4k/10k, forward e backward, todos STABLE e facts
esperados preservados. S8: 1/2/20 Entries, só 1/2 selecionadas, sem slots para órfãos.
O walk do resultado mede 2 root arrays, 2P slots e P AnalysisPoints; rejeita retenção
de Run, queue ou SolverTopology. Não mede bytes físicos ou internals do domínio/mapas.
Nenhum maxIterations/work budget/resource outcome/catch OOM; CORE-SIZE-001 preservado.
Dívidas externas de capacidade dos pins W1 continuam explícitas, sem repin paralelo.

## Campanha adversarial

[Recibo consolidado e logs](challenges/receipt.json): **26 mutantes únicos válidos**,
23 produtivos e 3 test-only (replay âncora/ordem e transfer concreto errado).
Todos compilam, produzem RED nominal, restauram byte-exact e têm segundo GREEN.
Hashes de todos Java/POM restaurados conferem com as fontes finais. A campanha
principal comprova 23; três execuções focais completam a mesma matriz. Último GREEN
executa novamente todos os 18 métodos W2.

Tentativas preservadas sem contar como sucesso: dois qualificadores de diagnóstico
esperavam uma asserção posterior, mas o mutante falhou antes no mesmo witness;
seletores corrigidos e reexecutados. Um mutante (`enqueue-unchanged-join`) sobreviveu
porque o diamond não exercitava contribuição subsumida. O teste foi fortalecido
permanentemente com Branch de mesmo destino/arestas equivalentes, e o mutante morreu.
Nenhum erro de compilação foi contado como RED. Logs e recibos originais mantidos.

## Gates locais executados

[Comandos, exits, duração e hashes](local/gates.json), [suítes Maven](test-counts.json).

| Gate | Resultado |
| --- | --- |
| fast | PASS — 47 regressões + 87 CP5 = 134 testes Python |
| architecture | PASS — W1/CFG/transporte preservados; W2 sources/classfiles/javap/jdeps/DAG |
| semantic | PASS — 84 CFG + 20 W1 + 15 W2 nominais |
| integration | PASS — regressões de arquivo/CLI e reactor real |
| performance | W1 PASS + W2 PASS; global UNAVAILABLE/3 por W3–W5 |
| full | W1/W2 executadas PASS; global UNAVAILABLE/3 por W3–W5 |
| mvn clean verify | PASS — 20 suítes / 183 testes, zero failures/errors/skips |
| git diff --check | PASS |

A primeira integração com o harness revelou metadata de evidência ausente no eval,
um teste de routing que precisava mockar os novos hooks para manter fast offline,
e a contagem CI de repositórios isolados que passou de cinco a seis com o passo W2.
Falhas brutas preservadas; gates reexecutados depois das correções. Não houve
relaxamento de teste de produto ou alteração de oracle para acomodar resultado errado.

## CI final, limites e parada

O workflow preserva push e pull_request, ambos executando W2 real além dos gates
anteriores. O collector canônico permanece byte-exact e guarda actual checkout,
PR head/base, árvores e evento. O recibo do **último HEAD publicado** fica no mesmo
[PR #12](https://github.com/Gustavo2358/analysis-cfg/pull/12) e na cópia local ignorada
`.harness-results/WORK-CFG-028/wave-2/remote-ci.json`. Isso evita hash autorreferente
em novo commit de evidência. CI antiga não prova HEAD novo; classificação distingue
EXACT_COMMIT_CHECKOUT de SYNTHETIC_MERGE_IDENTICAL_TREE e DIFFERENT_TREE.
A confirmação remota final é posterior a este documento e é requisito do handoff.

A prova é de solver relativo às funções e contratos declarados, com soundness/precisão
somente nas máquinas sintéticas test-only. Não certifica um analisador AIR/COBOL.
Não há PossibleValues, production RD/Liveness, consumers/resolvers, observation
replay de produto, writer ou CLI de dataflow. Siblings não foram alterados; nenhum
pin foi atualizado. W1 APPROVED; W2 IMPLEMENTED / AWAITING_HUMAN_REVIEW;
W3–W5 NOT_STARTED / NOT_AUTHORIZED. Mesmo PR draft, sem merge/auto-merge/ready.
