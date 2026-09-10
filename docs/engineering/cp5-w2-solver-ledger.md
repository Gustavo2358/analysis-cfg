# W2 — solver: contratos, custo e retenção

[API/engine](../../analysis-kernel/src/main/java/io/github/gustavo2358/analysis/solver/DataflowSolver.java),
[SPI](../../analysis-kernel/src/main/java/io/github/gustavo2358/analysis/solver/AnalysisDefinition.java),
[provas executáveis](../../scripts/project/check_w2.py).
W1 APPROVED em b84389b6; W2 autorizada separadamente. W3–W5 não autorizadas.

## Equações e domínio aceito

IN e OUT identificam respectivamente antes/depois do bloco na execução do programa.
FORWARD acumula IN, aplica block(IN) e publica OUT; cada publicação entrega
edge(OUT) aos successors. BACKWARD acumula OUT, aplica reverseBlock(OUT) e publica
IN; cada publicação entrega edge(IN) aos predecessors. Arestas mantêm a identidade
e orientação originais do programa na chamada da SPI.

A definição é configuração fixa: direction, bottom, boundaries, joinInto,
equivalent, transferBlock e transferEdge. Join é ACI e upper-bound; equivalência
é coerente com a ordem; ambos transfers são monotônicos. O domínio tem altura
finita ou garantia de convergência explicitamente justificada. Essas são
EXPLICIT_CONTRACT, não propriedades deduzidas de um callback arbitrário. Grafo e
configuração são fixos; agenda justa; sem retraction, narrowing ou atualização de
CFG durante solve. A engine não verifica automaticamente uma prova de convergência.

Bottom não implica empty map, unreachable ou reached-empty. O domínio define
essas distinções. Todas as raízes são não nulas e isoladas: callbacks não podem
mutar entradas, raízes já retornadas nem fatos posteriormente entregues. Imutáveis,
persistent/sharing, interning e copy-on-write cabem nessa regra, sem deepCopy imposto.
joinInto.changed significa mudança semântica; unchanged conserva a raiz anterior.
A comparação de publicações também preserva a raiz antiga se equivalente, mesmo
quando o domínio devolve um objeto diferente.

A primeira publicação possui flag própria e sempre entrega contribuição, inclusive
bottom. Uma nova publicação substitui a raiz anterior, sem unir valores mortos pelo
transfer. Pela monotonicidade, publicação antiga ≤ nova; EdgeTransfer mantém essa
ordem. Portanto uma nova contribuição subsume a antiga e basta acumulá-la com join:
nenhum previousContribution[edge], subtração ou histórico é necessário.

## Pontos efetivos, boundaries e agenda

SolverTopology deriva diretórios densos uma vez dos cursores W1. Em cada ContextView
selecionada, a descoberta percorre successors desde seu EntryNode; reachability do
modelo é sempre forward, inclusive em análises backward. Inventário órfão W1 não
vira ponto ativo. SCC alcançável sem saída permanece incluída. Isso não prova
reachability da fonte ou pairing de frames locais. Entry é contexto de ativação.

Uma AnalysisPoint opaca por par efetivo ContextView/Node; IDs/ordinais externos não
indexam states. Boundary pode estar em qualquer desses pontos e pode ter várias
contribuições, na mesma raiz ou em múltiplos roots. Cada contribuição é unida uma
vez ao acumulador da direção. Boundary estrangeira/fora do conjunto selecionado é
erro de configuração, sem resultado. Não há regra Entry-only/NormalExit-only na SPI.

Todos os pontos efetivos começam em bottom e entram uma vez na agenda para assegurar
primeira avaliação/publicação, inclusive gen local backward em SCC sem exit e
EdgeTransfer(bottom) não identidade. Depois, somente join changed tenta enqueue.
A engine limpa queued antes do transfer, permitindo que self-loop reenfileire o
próprio ponto. A fila int circular e boolean[P] realizam enqueueIfAbsent. Crescimento
e contadores têm aritmética checada. FIFO é implementação atual; a porta de agenda
é package-private e os testes injetam outras agendas justas, sem opção semântica pública.

## Ledger de coleções e lifetime

P = soma dos pontos efetivos nas views selecionadas; Ectx = arestas entre esses
pontos, preservando edges paralelas. Ksel = contexts selecionados. A retenção abaixo
é adicional à sessão W1 e às raízes/internos do domínio.

| Estrutura | Cardinalidade / criação | Retenção, ownership e liberação |
| --- | --- | --- |
| directory ContextView → identity Node → AnalysisPoint | Ksel mapas e P entradas, descoberta uma vez | lookup de boundary/result por identidade; result retém, sem hashes AIR profundos |
| AnalysisPoint | P objetos, ordinal privado e refs W1 | result retém via directory; nenhum novo Node/AIR/CFG payload |
| points[] | P referências, freeze da descoberta | solve apenas; array liberável ao retornar |
| topology edges[] | Ectx referências às CfgTransitions originais | solve apenas; não é novo CFG semântico, nenhuma contribuição/state por edge |
| from/to/forwardNext/backwardNext | 4 int[Ectx], um encadeamento reverso | solve apenas; operações por edge usam ordinais, sem Maps de state no hot path |
| forwardHead/backwardHead | 2 int[P], inicializados a -1 | solve apenas; nenhuma busca global de predecessors no pop |
| discovery lists | P points, Ectx edges e 2 Ectx ordinais boxed | scratch de construção; ArrayList/maps podem ter capacidade excedente; soltos após freeze |
| IN/OUT arrays | 2 Object[P], preenchidos com a raiz bottom | transferidos ao result após convergência, sem clone/snapshot adicional |
| published / queued | 2 boolean[P] | solve apenas; flags distinguem primeira publicação e membership |
| queue int[] | capacidade inicial max(1,P); size ≤ P sob enqueueIfAbsent | solve apenas; cresce por representação se necessário, sem limite semântico |
| boundary iterable | B contribuições produzidas pela definição, uma passagem | não retido pela engine; raiz eventualmente compartilhada com states |
| candidate / contribution / Join | uma referência local por chamada | somente raiz atual retida; intermediários antigos liberáveis; Join é retorno do domínio |
| DomainWork / Run / metrics | quantidade fixa de counters checked | scratch do run; snapshot imutável de métricas fica no result |
| DataflowResult | 1 objeto, directory + duas root arrays + métricas | construtor package-private; só após worklist vazia; sem callbacks/provisional snapshots |

O result preserva context/node e, por eles, a sessão W1 pode continuar alcançável.
Não promete facts desacoplados da sessão; essa API é por bloco, sem observation replay.
Soltar result e handles/roots mantidos pelo chamador permite liberar o run. Não há
cache static, pool global ou retenção de AnalysisDefinition/agenda/histórico.

A auditoria test-only percorre campos realmente retidos por result, deduplica por
identidade, conta dois arrays/2P slots/P handles e rejeita SolverTopology/Run/fila
retidos. Para nesses roots opacos e nas refs W1: não mede internals do domínio,
backing nodes/tables dos IdentityHashMaps nem bytes físicos totais. Os números são
objetos/slots lógicos observados. Não há claim de zero allocation; listas, boxing,
wrappers, callbacks e domínio podem alocar. Nenhum Node×Object/Cell/Value state array.

## Trabalho e instrumentação

Preparação expected/amortized O(P+Ectx), incluindo diretórios por identidade; scratch
O(P+Ectx). Custo de hashing/comparação textual dos IDs pertence à W1/borda. Não há
produto eager Entries×Nodes: somente pontos descobertos das views selecionadas.

Aprop = soma de (primeira publicação + publicações alteradas) × grau afetado.
Solve = preparação + boundaries + soma dos pops × (transfer de bloco + comparação)
+ soma das Aprop contribuições × (transfer de edge + joinInto + enqueue quando muda).
Custos do estado J, BT, ET e Cstate são parte da equação. Não se reivindica O(V+E)
universal: ciclos/altura de domínio e custo de join/comparação podem dominar.

Os counters de Run incrementam nos eventos reais. publication(point, contributionRead)
separa leitura do próprio slot para equivalência de releitura de root de vizinho
para recomposição: esta última incrementa predecessorContributionReads em forward
ou successorContributionReads em backward. O caminho incremental não faz essa
releitura; envia candidate diretamente. O mutante S4b usa o mesmo accessor com papel
de contribuição e relê efetivamente cada predecessor, conservando semântica. O gate
confronta zero, contagens, bytecode e esse RED; não deduz custo por regex ou tempo.

DomainWork mede operações sintéticas, entradas examinadas por join e equivalência
nos callbacks dos testes. Forward conta valuations finitas examinadas; backward
e rank contam um escalar/máscara por join ou comparação. É responsabilidade do
domínio reportar trabalho interno;
engine opaca não pode inventar esses números. edgeContributionJoins = deliveries =
edgeTransferInvocations; joinIntoCalls total = edgeContributionJoins + boundaryJoins.
Métricas públicas usam os nomes canônicos de metrics.json, com aliases documentados.

S4b usa árvore de Branches binárias real → N fontes → J. Agenda de teste prioriza J
entre fontes, expondo N chegadas separadas. Contador do callback mede entregas a J;
arestas auxiliares são separadas. O oracle lento por recomposição faz N(N+1) reads
em J (inclui avaliação inicial bottom); o produtivo entrega N contribuições a J.
As outras agendas podem coalescer chegadas e alterar trabalho, preservando facts.
Elapsed nanos é observação; não há SLA temporal, maxIterations, timeout, budgets ou
catch OOM. Falhas de execução não produzem resultado STABLE, recusa por tamanho,
ANALYSIS_LIMIT, remainder ou resultado parcial.

## Limite da prova

Oracles manuais, recomposição independente e máquina concreta finita verificam as
análises sintéticas, incluindo monotone-but-wrong e always-TOP. A W2 prova o solver
relativo às funções sob as leis declaradas. Não prova soundness de semântica AIR ou
COBOL. PossibleValues, production RD/Liveness, consumers, ObservationPlan, codecs e
CLI de dataflow continuam ausentes. Replay futuro: IN/crescente forward;
OUT/decrescente backward, mantendo before/after na ordem do programa.
