# W4 — planejamento, providers e consumers

[Autorização](../work/evidence/WORK-CFG-028/wave-4/authorization.json) posterior ao
review APPROVED de W3 em 855628200fba3851493991cec869dee899e82299. A W4 acrescenta
`analysis-kernel/application`, descritores imutáveis em `analysis-kernel/plan` e a
SPI restrita em `analysis-kernel/consumers`. O adapter `PossibleValuesProvider`
pertence a `analysis-values`. Nenhum módulo/POM novo, consumer de negócio, resolver,
writer, CLI ou DeliveryReceipt. W5 permanece não autorizada.

## Identidade e binding

AnalysisKey inclui implementation, version, profile, direction, precisionPolicy,
options semânticas e EntryId completo. Igualdade de record inclui todos os campos;
ORDER compara os campos e pares ordenados de options, sem concatenação ambígua.
Mapa é copiado/imutável. IDs completos são usados na borda; nenhum hash profundo de
Publication/CFG, hashCode do grafo, endereço ou displayName decide identidade.

AnalysisRegistry é um registro explícito, imutável, por implementação/versão. Cada
provider declara profiles aceitos, nomes de opções semânticas, projeção e tipos de
subject/fact. Options desconhecidas são erro de planejamento. PossibleValues aceita
somente scalar-text-direct@1 / FORWARD / FINITE_PROGRAM_TEXT_VALUES, options={};
worker count, heap, timeout e caps não configuram o run. VariantProvider test-only
prova perfis/opções distintos com outputs distintos e solver W3 real. Esse provider
não amplia o profile de produto. Os providers atuais são independentes: nenhuma
API de dependências recursivas ou fixpoint entre análises foi introduzida.

W4-BINDING-01: o application só chama prepare/execute/observe do provider registrado.
Prepared.key e Run.outcome.key precisam coincidir exatamente com a key pedida.
BoundRun é privado ao runtime: não há entrada pública de DataflowResult, direction,
transfer ou materializer separados. O provider real cria uma Execution W3 e mantém
seu observe vinculado à mesma instância. Batch ID também vincula key, projection e
tipos; colisão de ID com outro binding é erro antes do solver. A correção semântica
do provider é contrato explícito da SPI, assim como as leis do domínio W2; não se
pretende provar automaticamente funções arbitrárias fornecidas pelo chamador.

A única modificação de fonte W1–W3 é o método aditivo AnalysisSession.selectEntries.
W3 execute resolve todos os contexts de sua sessão; executar uma key por Entry
exigiu um escopo com somente essa Entry. A nova seleção só pode estreitar a seleção
do owner e compartilha o ProgramIndex já admitido; custa O(Entries selecionadas),
sem open/reindex/BuildCfg/AirValidator. Cria ContextViews próprias e não modifica
views/runs anteriores. Nenhuma linha anterior de AnalysisSession mudou; o guard
remove **somente a adição exata** e confronta o hash do arquivo com o baseline W3.
Todos os demais Java produtivos W1–W3, solver, domínio, suportes e replay conservam
bytes. O provider real prepara o profile W3 no escopo e executa uma boundary/Entry;
18 Entries não selecionadas não geram pontos nos witnesses de keys.

## Planejamento e despacho

ConsumerRegistration contém ConsumerPlan explícito, interesses estáticos, pedidos
fixos opcionais e FactConsumer. ConsumerPlan normaliza keys e batch IDs em listas
imutáveis ordenadas; duplicatas equivalentes de dependências são deduplicadas.
Duplicar consumerId, inclusive registro equivalente, é erro declarado. O mesmo
batchId só pode identificar exatamente o mesmo binding em todo o plano.

SiteInterest seleciona uma classe Operation AIR real e uma Entry, com Predicate
puro sobre SiteView. SiteQuery opcional declara a query a partir do site selecionado
na fase de planejamento. Filtros e fábricas são determinísticos, sem efeitos ou
referências externas ao programa. Consumer não recebe sessão, índice ou grafo.
SiteView contém somente EntryId, LabelId, offset e a Operation AIR imutável daquele
site (campos tipados/IDs/origem); não retém Sequence, Unit ou índice. Presence é
STRUCTURAL: presença de um site órfão não afirma MAY_EXECUTE ou reachability da fonte.

SitePlanner agrupa interesses por classe AIR, depois por Unit owner. Cada bucket
selecionado é lido uma vez; apenas filtros pertinentes àquele bucket/owner são
avaliados. TreeSet por consumer deduplica matches por (Entry, OperationId completo).
Consumer recebe uma chamada consume por match distinto, em ordem canônica de
consumerId / Entry / OperationId. Zero matches significa zero callbacks; dependências
explicitamente declaradas continuam requeridas e podem ter trabalho mesmo sem matches.

Não há loop global de operações por consumer nem broadcast de callbacks I×K.
Filtros arbitrários de um mesmo bucket ainda podem exigir várias avaliações por
candidato; esse custo H é medido, sem prometer que seja sempre proporcional a M.
A seleção esparsa depende de buckets apropriados, não de elapsed time.

Requests por consumer precisam listar explicitamente sua AnalysisKey e batch ID.
Batch requerido inexistente, provider desconhecido, Entry não selecionada, query com
Entry errada ou colisão de binding falham no planning, antes de qualquer run.
SiteQuery registra seu binding antes de procurar sites e antes de avaliar filtros,
validando provider/projection/tipos e dependências explícitas. O binding existe mesmo
com bucket vazio ou filtro que rejeita todos. Nenhuma ObservationRequest sentinela
é exigida nem fabricada. Para batch declarado com zero queries, o run declarado
continua requerido e W3 materializa COMPLETE vazio: requests/uniqueQueries/grupos/
operações/observations = zero. Consumer sem matches completa sem callbacks/facts,
desde que suas dependências concluam; preparação também pode ser COMPLETE.
Um batch desconhecido continua sendo erro de planning. Registro antecipado custa
uma validação/inserção por declaração SiteQuery; não executa sua função de query.

Pedidos equivalentes são unidos por batch + PointQuery completo. A lista final de
queries usa ProgramPoint.ORDER e o comparador de subject do provider. Requests
fixos e SiteQueries alimentam a mesma união; todos os pedidos brutos são contados.

ExecutionPlan é imutável e ligado por token privado ao PlanningExecution owner.
Registros, interests e queries são copiados; não há registro durante consume.
Um plano pode executar uma vez. Novo pedido requer plan() explícito e novo número
de epoch; os runs estáveis da mesma key podem ser reutilizados nesse lifetime.
Objetos são sequenciais, sem contrato de acesso concorrente ou scheduler.
Callbacks/facts devem ser determinísticos para o mesmo input. O runtime ordena
consumers, sites, keys e batches; a ordem interna de emissão de um consumer é seu
contrato, pois o runtime não interpreta nem serializa o tipo genérico F.

## Execução, completion e FactSink

PlanningExecution possui cache por AnalysisKey; cada consulta corresponde a uma
dependência explícita de consumer. O primeiro pedido admitido invoca execute uma
vez; os seguintes usam o mesmo run. Recusa de admissão é cacheada separadamente,
sem analysisRuns. Provider retorna UNSUPPORTED/INVALID_INPUT somente para motivos
semânticos W3; configuração desconhecida é erro de planning. Falha interna do solve
propaga sem publicar STABLE. Não existe cache static/global.

Cada batch explícito usa o run estabilizado e sua união de queries em uma chamada
observe. O provider reutiliza BatchReplayer W3: prefixo FORWARD ou sufixo BACKWARD
pela direção do run. O provider produtivo atual só suporta FORWARD. Batches distintos
são independentes, inclusive com a mesma query; podem repetir replay intencionalmente,
mas compartilham solver. Novo epoch reobserva explicitamente; não há cache de batches
entre epochs nem replay implícito em lookup. Ausência de observationCacheHits reflete
essa política, não um contador inventado em zero.

BoundRun verifica cobertura completa do plano no retorno do provider e tipos dos
facts. Batch controladamente falho é vazio; ObservationException é a única captura
de falha de observação. Run continua STABLE. PreparedFacts oferece apenas run outcome
desacoplado e lookup de queries declaradas **por aquele consumer**. Query declarada
por outro consumer no mesmo batch continua NOT_REQUESTED para quem não a pediu.
O lookup não contém factory/solver/replayer. Facts e observations imutáveis podem
ser compartilhados pela mesma instância.

Dependências são verificadas localmente: estrutural zero análises; analysis-only
exige somente run STABLE; query consumer também exige seus batches COMPLETE. F3:
run STABLE + batch FAILED/OBSERVATION_ERROR preserva structural/analysis-only e o
consumer de batch independente; bloqueia só o dependente com
NOT_STARTED/DEPENDENCY_UNAVAILABLE. Resultado agregado INCOMPLETE não apaga sucessos.

FactSink é staging local de todo o consumer, abrangendo todos os sites. Apenas após
sucesso de todos os callbacks o bundle imutável é committed no resultado. Uma
ConsumerException descarta o bundle inteiro e produz FAILED/CONSUMER_ERROR; consumers
independentes prosseguem. Sink fecha tanto no sucesso quanto na falha; emit tardio
é erro de programação. A SPI exige F imutável e detached. Não há sink de filesystem,
publicação externa nem estado intermediário exposto.

PreparedAnalysisResult retém somente IDs/keys, outcomes, observations/facts e
métricas imutáveis, com partialPolicy=EXPLICIT_PARTIAL_BY_DEPENDENCY. COMPLETE exige
todos os runs STABLE, batches COMPLETE e consumers COMPLETE. Source PARTIAL fica
nos ValueFacts e não muda completion da execução do modelo. COMPLETE de preparação
não certifica entrega. O tipo runtime não congela schema/version/wire W5.

Somente exceções controladas específicas viram falha de fase. Outros RuntimeException,
OOM e erros de infraestrutura propagam sem resultado parcial. Nenhum tamanho,
contador, timeout ou resource policy define admissão, precisão ou término.
ValueFact é entregue intacto: candidateSupports, Assign/InitialCondition producer,
OriginId, PremiseId, reachability e remainders de modelo/fonte/efetivo. Supports são
suportes abstratos; não são caminhos executáveis nem prova de viabilidade.

## Dimensões e custo

I = operações inventariadas; B = candidatos nos buckets selecionados; K = consumers;
L = interesses; H = avaliações de filtros pertinentes; M = pares site/context/consumer
realmente selecionados; A = keys únicas; Qraw = pedidos brutos; Q = queries únicas
por batch; R = operações reexecutadas nas uniões; F = facts emitidos. Custos de
comparação/hashing de IDs e opções incluem seus comprimentos, não são hashes profundos.

Index W1 custa seu ledger e acontece uma vez. Planning custa, em tabelas hash
expected/amortized, O(K log K + L log L + B + H + M log(M+1) + Qraw + Q log(Q+1)
+ A log(A+1)), com cópias/ordenação proporcionais às dependências declaradas.
SiteView temporária é alocada por avaliação H; somente matches M sobrevivem no plano.
Shared-query sets retêm Q e as memberships por consumer (até Qraw), sem states.
Comparadores puros precisam definir ordem total consistente com identidade do subject.

Execução custa requests de keys + Σ trabalho do provider por key única + Σ batches
(materialização W3 da união Q/R) + M callbacks + F staging/commit. Admission W3 ainda
percorre o profile global por run A; isso não é nova travessia por consumer K.
Providers de keys diferentes não compartilham automaticamente a preparação semântica.
Solver, join de sets/suportes e replay mantêm os custos/limites do ledger W3;
W3-PERF-01 continua aberto. Não se afirma O(I+K) nem O(V+E) universal.

## Retention ledger

| Estrutura | Cardinalidade / criação | Owner, lifetime, release |
| --- | --- | --- |
| consumer registrations | K + L + Qraw, cópias ao declarar | chamador e ExecutionPlan; soltam ao liberar plano/inputs |
| compiled interests | L em buckets e owners | scratch SitePlanner; liberado ao retornar plano |
| selected SiteViews | M, com Operation AIR original | plano; sem Unit/Sequence/index; soltam com plano |
| AnalysisKeys/dependencies | A + dependências por consumer | plano/cache/outcomes detached; mapas semantic-only |
| run cache / BoundRun | A por execution lifetime | PlanningExecution; clear no close e coletável com owner |
| provider prepared profile/Execution | profile W3 + 2P roots por run admitido | provider BoundRun; solta com cache; nenhum consumer recebe |
| observation plans | Q + memberships por consumer até Qraw | ExecutionPlan; plan release; sem state snapshots |
| batches e lookup indexes | Q references/facts por batch | scratch do execute e resultados detached; indexes liberados após callbacks |
| ConsumerPlan | K + dependências explícitas | resultado pode sobreviver; sem consumer instance |
| staged fact bundle | até facts daquele consumer | Staging temporário; clear em commit/failure; sink fechado |
| committed fact bundles | F referências imutáveis | PreparedAnalysisResult/chamador; compartilhamento de ValueFacts contado por identidade |
| PreparedAnalysisResult | A outcomes, batches Q, K consumers, F facts | chamador; nenhuma referência a sessão, solver, ValueUniverse ou consumer |
| epoch identity/executed numbers | 1 token + quantidade de epochs executados | PlanningExecution; sem referências reversas ao plano; clear no close |
| counters | mapas pequenos por fase e snapshots | resultado detached; checked increments, nunca políticas |

Walk test-only percorre os objetos realmente alcançáveis com dedup por identidade.
Contagens de Map/List são lógicas; seus nós/tabelas internos e capacidade excedente
não são heap físico. Auditoria externa GC/JFR complementa essa contagem. O contrato
de detach pressupõe que F fornecido pelo consumer obedece à SPI: o runtime genérico
não usa reflexão para copiar/sanitizar objetos arbitrários.

## Métricas e oracles

Index: operationsIndexed/structuralVisits do snapshot W1, separado do planning.
Planning: candidateSites = elementos únicos dos buckets percorridos; structuralVisits
= leituras reais desses elementos; filterEvaluations = predicates aplicados;
siteMatches = pares distintos; planningCallbacks = SiteQuery executada; analysisRequests,
queryRequests, uniqueQueries, observationBatchesPlanned, planningEpochs.
Analysis: analysisRuns = execute admitido realmente iniciado; analysisCacheHits =
dependência atendida por run STABLE existente; analysisAdmissionCacheHits separado.
Detalhes prepare_/solve_ e SolverMetrics ficam nos outcomes por key.
Observation: batches executados, requests brutos W4 (antes da união), queries únicas,
grupos/operações W3 e qualidade. Batches failed conservam trabalho medido pela W3;
falha controlada anterior ao materializer registra zero replay e todas queries não
materializadas. Batches NOT_STARTED também conservam seu denominador de queries.
Consumer: consumerInvocations por callback de site; complete/not-started/failure,
factsStaged/Committed/Discarded; factsEmitted é alias de committed; notRequested
conta consultas recusadas sem replay. Nenhum counter é inferido de elapsed time.

S5: I=100000, 30 Assign/20 Return/10 Branch candidatos, K=1/2/20, B=60,
M=60K, analysisRuns=1. Nop não participa do dispatch. Há Returns órfãos e executáveis.
S6: 1000/2000/4000 instructions, Q=I/2 ou I, K=1/2/20, um grupo e R=Q;
ValueFacts retidos=Q independentemente de K. Qualidade exige todos os candidatos.
S8: oito keys (2 Entries × 2 profiles × 2 semantic options), 16 consumers, oito
runs/hits, três pontos por run; 18 Entries não selecionadas sem runs fantasmas.
S14: F3, batches independentes, falha/staging local, structural/analysis-only.
S15: candidatos/suportes/premissas/abertura e denominadores junto ao custo.
S16: N=32/64/128 consumers/matches/queries/facts, com batch compartilhado ou N
batches independentes. Todos COMPLETE; batch independente custa R=N(N+1)/2
intencionalmente, solver=1. Nenhum cap é usado para fabricar melhor custo.

Gates enumeram métodos/suítes e rows nominais, rejeitando ausência, duplicação,
skip, métricas ausentes, perda de qualidade e custo incorreto. Mutantes compiláveis
precisam RED nominal, restore byte-exact e segundo GREEN. Resultados executados e
limites finais ficam na evidência W4; nenhum hook W5 é ativado por esses testes.
