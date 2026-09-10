# CP5 WAVE 4 — implementação para review humano

Repo `analysis-cfg`, branch `feat/cp5-dataflow-engine`, mesmo
[PR #12](https://github.com/Gustavo2358/analysis-cfg/pull/12), OPEN/DRAFT.
W3 foi aprovada em `855628200fba3851493991cec869dee899e82299`; a autorização W4
foi registrada antes da produção no commit `e2d709d`. Implementação Java e oracles:
`bad1b9a`; ativação de harness/CI: `e2c1425`. O HEAD final e os recibos remotos são
publicados no próprio PR após o último commit, evitando recibo autorreferente.
Este documento não substitui nem antecipa a aprovação humana da W4.

[Baseline/pins](baseline.json), [autorização](authorization.json),
[ambiente isolado](dependency-environment.json), [preservação de fontes](foundation-preservation.json),
[ledger completo](../../../../engineering/cp5-w4-planning-ledger.md),
[inventário compilado](../../../../evals/cp5/w4-inventory.json) e
[inventário Java/POM](../../../../evals/cp5/w4-source-inventory.json).
Pins continuam air-java `ce530a7e17ab12b23c48f29425f503ff920b09fb` e
analysis-ir `122ce54e1b9ef9b00646f93ece409ca8b63bc933`.

## Arquitetura, key e W4-BINDING-01

Application contém SitePlanner, ExecutionPlan, AnalysisRegistry e PlanningExecution.
O pacote plan declara identidades/dependências imutáveis; consumers contém somente
SiteView, PreparedFacts, FactConsumer e FactSink. PossibleValuesProvider fica em
analysis-values; nenhum módulo/POM mudou. Gates verificam fontes exatas, classfiles
Java 21, assinaturas javap, referências jdeps e dependências Maven efetivas.

AnalysisKey inclui implementation, version, profile, direction, precisionPolicy,
options semânticas e EntryId completo. Igualdade inclui todos os campos, inclusive
o mapa copiado e ordenado; ORDER é total, sem concatenação ambígua de opções.
PossibleValues só aceita scalar-text-direct@1, FORWARD,
FINITE_PROGRAM_TEXT_VALUES e options={}. Opções de capacidade e desconhecidas são
rejeitadas, sem converter tamanho em outcome semântico.

O cache pertence à instância PlanningExecution vinculada à sessão e é liberado no
close. IDs iguais em sessões distintas não permitem reutilizar cache nem plano.
Cada plano tem epoch imutável/token privado e executa uma vez. Pedidos tardios
retornam NOT_REQUESTED; um novo plan explícito cria novo epoch e reutiliza runs
estáveis da mesma key, sem replay escondido.

W4-BINDING-01 = IMPLEMENTED/RESOLVED para review: o registro liga provider, perfil,
direção, projeção e tipos; prepare/execute/observe pertencem à mesma execução.
Prepared.key, outcome.key e batch.analysisKey precisam coincidir. Não há API de
injeção separada de DataflowResult/direction/transfer/materializer. Colisões de ID
de batch com outro binding são erro de planejamento. O adapter produtivo chama o
observe da mesma Execution W3 estabilizada. Mutantes de key errada, cache incompleto,
provider errado e associação de batch são detectados. Providers são SPI semântica
confiável; não se pretende verificar automaticamente funções arbitrárias.

A única adição à fundação W1–W3 é AnalysisSession.selectEntries: estreita a seleção
do owner compartilhando ProgramIndex, sem open/reindex/projeção/validação. Isso
permite um run por Entry sem resolver as demais Entries da sessão. O teste S8
confirma três pontos por run e nenhuma execução de 18 Entries não selecionadas.
Todas as linhas anteriores de AnalysisSession e todas as demais fontes/testes/POMs
aprovados são preservadas por hashes; solver W2 e domínio/replay W3 são byte-exact.
O [guard focal](../../../../../scripts/project/check_w4_scope.py) remove apenas a
adição exata antes de comparar o hash antigo. Não houve refactor da fundação.

## Planejamento, reuse e testemunhas reais

Interesses usam classes Operation AIR, Entry e filtro puro. O planner agrupa por
kind e Unit owner e percorre cada bucket escolhido uma vez. SiteView só contém o
site AIR e IDs; presença STRUCTURAL não afirma executabilidade. Matches são
pares distintos (consumer, Entry, operation). ConsumerId duplicado é erro.
Planejamento ordena consumers/sites/keys/batches/queries; testes permutam registros,
interesses e queries. A ordem interna dos facts de um consumer é seu contrato
explícito de emissão determinística.

ConsumerPlan declara requiredAnalysisKeys e requiredObservationBatchIds. Requests
fixos e por site são unidos por batch antes de executar. PreparedFacts restringe
lookup aos pedidos daquele consumer, inclusive quando outro consumer compartilha o
batch. Queries de outro consumer não ampliam implicitamente a autorização local.

A vertical real usa AIR in-memory com Assign de TextValue("PROGA") a Object/Cell,
Return e query Before(Return). BuildCfg/AnalysisSession/PossibleValues W3 executam
realmente. Dois consumers pedem a mesma query, um deles duas vezes: Qraw=3, Q=1,
analysisRuns=1, analysisCacheHits=1, um batch, um grupo, uma operação reexecutada.
Ambos recebem a mesma observation imutável e o candidate PROGA com evidence do
Assign, OriginId e candidate support preservados. A variante fonte PARTIAL mantém
sourceUnknownRemainder/effectiveUnknownRemainder sem abrir artificialmente o modelo.
O teste de join com premissa conserva PremiseId e supports abstratos; isso não é
prova de caminho executável. O loop/overwrite entrega somente o estado final B.

Testes nominais: PlanningRuntimeTest (11), PlanningContractTest (8), PlanningTest
(2), PlanningScaleTest (3). Fixtures/reference consumers são test-only, sem
CALL/File/DB2/CICS/GRBE ou interpretação de nomes/JSON do frontend.

## F3/S14 e atomicidade do FactSink

No witness F3, um run real permanece STABLE. Batch A-failed recebe
FAILED/OBSERVATION_ERROR vazio; B-good executa independentemente no mesmo run.
Consumer estrutural S e analysis-only A ficam COMPLETE; Qbad fica
NOT_STARTED/DEPENDENCY_UNAVAILABLE; Qgood fica COMPLETE. Preparação agregada
INCOMPLETE: três consumers completos, um não iniciado, uma falha de observação,
uma query não materializada. Não há barreira global que apague sucessos independentes.

Estrutural sozinho executa zero análises; analysis-only executa um run e zero
batches. Cada consumer usa staging que abrange todos os seus callbacks. Commit só
ocorre ao terminar com sucesso. No teste de falha, A emite dois facts e falha;
os dois são descartados, A fica FAILED/CONSUMER_ERROR, B continua e comita um fact.
Contadores: staged=3, discarded=2, committed=1. Nenhum bundle parcial do consumer
falho aparece no resultado. Sink fecha após sucesso/falha e rejeita emit tardio.

PreparedAnalysisResult é detached/in-memory e usa
EXPLICIT_PARTIAL_BY_DEPENDENCY. COMPLETE exige todas as dependências e consumers
completos; não certifica delivery. Somente ConsumerException/ObservationException
controladas viram falha de fase; OOM e demais erros de infraestrutura propagam sem
fabricar resultado parcial. Tipo F deve ser imutável/detached, conforme a SPI.

## S5/S6/S8/S14/S15/S16 e CORE-SIZE-001

As [27 medições](scale.json) são extraídas do output bruto do gate performance;
nenhuma depende de limiar de tempo. Todos os casos abaixo executam W3 real.

| Probe | Dimensões | Trabalho/resultado observado |
| --- | --- | --- |
| S5 | I=100000; K=1/2/20 | 60 candidatos/visitas de bucket constantes; M=60/120/1200; run=1; hits=0/1/19; Qraw=20/40/400, Q=20 |
| S5 replay | Returns consultados sem instructions locais | 11 grupos, R=0; inclui sites estruturais órfãos; não é o witness de replay denso |
| S6 | N=1000/2000/4000; Q=N/2 ou N; K=1/2/20 | um run, um batch/grupo; R=Q; retained ValueFact=Q, independentemente de K |
| S6 maior | N=4000, K=20, Q=4000 | Qraw=80000, R=4000, 80000 facts completos compartilhando 4000 ValueFacts |
| S8 | 2 Entries × 2 profiles × 2 options; K=16 | 8 keys/runs, 8 hits, 3 pontos/run; 18 Entries não selecionadas sem run |
| S14 | falha de batch/consumer independente | F3 e staging acima; falha não invalida o run estável |
| S15 | custo acompanhado de qualidade | nenhum unsupported artificial, candidato/suporte/premissa/remainder preservados; denominadores completos |
| S16 shared | N=32/64/128 consumers/matches/queries/facts | run=1, batch/grupo=1, R=N; tudo COMPLETE |
| S16 batches | N=32/64/128 batches explícitos | run=1, grupos=N, R=528/2080/8256; replay repetido intencional entre batches independentes |

Sem maxConsumers, maxSites, maxQueries, maxFacts ou maxBatches no produto. Os
mutantes que introduzem esses limites são RED. N/2N/4N verifica completude junto
com custo, sem clipping nem outcome de capacidade. Opções JVM da auditoria são
ambiente externo OBSERVATION_ONLY, nunca política de admissão/precisão.

Métricas reais estão separadas em index, planning, analysis, observation e consumer.
O snapshot por key conserva SolverMetrics e prepare_/solve_; os batches conservam
replay_/qualidade. candidateSites, structuralVisits, filterEvaluations, siteMatches,
analysisRequests, queryRequests e uniqueQueries têm eventos explícitos no ledger.
Contadores usam incrementos checked. Custos incluem filtros pertinentes H,
dedup/ordenação, memberships locais Qraw e facts F; não se promete O(I+K) universal.

## Retenção e limites

[GC/JFR válido](memory-attempt-2/receipt.json): Java 21, I=10001 fixo,
K=1000/2000/4000. Um run e um ValueFact durante execução, K registrations no plano.
Após manter somente resultado: zero AnalysisSession, ProgramIndex, DataflowResult,
PlanningExecution, ExecutionPlan, ConsumerRegistration, ValueUniverse, TextProfile
ou PossibleValuesAnalysis.Execution; um ValueFact/support e K TestFacts.
Depois de liberar resultado, ValueFact/support/TestFact chegam a zero. Histograms,
flags, GC logs e JFR foram preservados. São instâncias e shallow bytes após full GC;
JFR usa amostras de alocação, não censo nem heap retido exclusivo.

O walk de identidade test-only complementa os histogramas. Map/List do walk são
contagens lógicas; seus arrays/nós internos não viram alegação de bytes físicos.
A primeira tentativa de attach falhou no sandbox e está preservada em
[memory-attempt-1](memory-attempt-1/receipt.json) e no log de desenvolvimento.
A segunda executou a mesma JVM diagnóstica com attach autorizado fora do sandbox.
[transport.json](transport.json) registra gzip sem mudar bytes descomprimidos.

## Challenges e gates

[Resumo dos challenges](challenges-summary.json): 26 mutantes compiláveis válidos,
cada um com RED nominal, restauração byte-exact e segundo GREEN de todos os 24
testes W4. Cobrem solver no consumer, travessia/broadcast por consumer, cache
incompleto/global, solver por consumer/batch, replay por consumer, falha global,
dependências ausentes, binding errado, lookup tardio, caps, perda de candidatos e
supports, unsupported-everything e fatos provisórios.

Três tentativas anteriores permanecem inválidas no histórico: cache barrado antes
do diagnóstico esperado; colisão de batch sobrevivendo ao teste inicial, reforçado
para declarar ambas as keys e remover mascaramento pela validação de dependências;
fato provisório barrado antes pelo contador de replay, depois verificado pelo
oráculo de estado final do loop. Nenhuma compilação inválida foi contada como RED.

[Recibo local dos gates](local-gates-attempt-2/receipt.json): architecture, semantic,
integration e mvn clean verify PASS/0. Maven: 240 testes, zero falhas/erros/skips:
139 CFG/transporte, 26 W1, 18 W2, 33 W3 e 24 W4. Fast passou com 47 + 97 testes
([log bruto](development/fast-attempt-3.log.gz)). Performance retorna UNAVAILABLE/3
após W1/W2/W3/W4 PASS; W5 não existe. A primeira tentativa de architecture detectou
o contador antigo de etapas Maven isoladas em CI (7 em vez de 8); guard ajustado
para exigir também a etapa W4. Sem mudança de código de produto para obter PASS.

[Full final](final-local/receipt.json) executado: exit 3 após fast, architecture,
semantic e performance W1/W2/W3/W4 PASS; W5 UNAVAILABLE. Integration foi validado
separadamente. Scope/manifest e diff também passaram, com rechecagem após empacotar
os recibos. Não se declara CP5 global completo nem perfil AIR completo.

## Escopo, follow-ups e checkpoint

[Siblings read-only](siblings-readonly.json): nenhum checkout sibling foi alterado
ou usado para compilar W4. artefatos-e2e conserva seus untracked preexistentes e Git
local sem origin. Não houve repin, dependência nova, produção CFG/CLI alterada,
consumer/resolver de domínio, writer, delivery ou código W5.

W3-PERF-01 continua aberto e não bloqueante: unions de conjuntos/suportes crescentes
podem ser quadráticas. W4 compartilha runs, não muda representação/domínio W3.
W3-METRICS-01 continua aberto: classificação tipada de RefusalReason ainda pendente.
W3-F1/F2 foram resolvidos por review humano; W4-BINDING-01 foi implementado aqui e
aguarda o review W4. Não há outro finding bloqueante identificado nos gates locais.

Lifecycle: W1/W2/W3 APPROVED; W4 IMPLEMENTED/AWAITING_HUMAN_REVIEW,
authorized_wave=4, current_checkpoint=WAVE_4; W5 NOT_STARTED/NOT_AUTHORIZED.
Mesmo PR #12 draft; sem merge, auto-merge, ready, rebase ou force-push. O recibo CI final é publicado no PR, com cópia local em
.harness-results/WORK-CFG-028/wave-4/remote-ci.json, após o último push. Ele correlaciona
HEAD/base/checkout/trees e os dois runs finais; evita um commit autorreferente de
recibo remoto. Nenhuma CI antiga serve como prova desse HEAD. Parar para review
humano. W5 exige nova
autorização explícita após revisão do HEAD final da W4.
