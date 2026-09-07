# WORK-CFG-006 — Bifurcação e IF/ELSE estrutural

Status: `completed`. Data: 06/09/2026. Autorização: `implementation`, pelo prompt
explícito para promover/implementar BACKLOG-CFG-006, concluir o MVP local somente
com evidência, fazer push normal/PR e parar para human review, sem merge/auto-merge.

## Base, lifecycle e fontes

Main limpa=origin/main: `50847a27628e90665eaa04871c58d0c27bcc4836`.
Branch nova: `feat/cfg-structural-branch`.
[PR #6](https://github.com/Gustavo2358/analysis-cfg/pull/6) confirmado MERGED em
2026-09-07T01:43:08Z, merge commit igual à base. WORK-CFG-022/BACKLOG-CFG-022
já completed, registry.active=[] e 006 planned/work_item=null antes da promoção.
Índice corrente stale reconciliado; história do 022 e seu handoff sem merge ficam
byte a byte intactos. Sem reabertura, alterações locais alheias, reset, stash ou
reutilização da branch anterior.

Commit `93dff9b` criou os cinco arquivos do WORK-CFG-006 com must_read, paths,
ADRs/invariantes e autorização implementation; sincronizou registry/index/backlog
e registrou oracle RED. O work permaneceu active durante implementação/gates/CI.
O encerramento promove conhecimento durável, arquiva este resumo e remove active.

Mains verificadas via git ls-remote, iguais aos pins, sem delta nem alteração:

- Analysis IR: `122ce54e1b9ef9b00646f93ece409ca8b63bc933`;
- air-java: `6a4091e5394fc22b3d2ada9abbdb530eb3572a58`.

Checkout detached `/tmp/work-cfg-006-air-java`; clean install da raiz upstream no
repo Maven novo/isolado `/tmp/analysis-cfg-work-cfg-006-m2`: exit 0, 172 checks.
Nenhum source/JAR/repo Maven vendorizado ou upstream modificado.
Lidos Branch, Expression/Unknown, Known/UnknownType, BoolValue, PREDICATE,
AirValidator/TypeResolver/OperationChecks e AIR §04.6/8, §08, O-02/03/04/18/19-STRUCT,
O-27 e O-74-STRUCT. Must_read do prompt/protocolo e kernel/testes/gates/CI inspecionados.

## Regra e produto

```text
Entry(E) --ENTRY--> entry(Branch)
                    ├─ BRANCH_TRUE  → yes --JUMP--> join
                    └─ BRANCH_FALSE → no  --JUMP--> join
                                                   └─ RETURN → NormalExit(E)
```

Mudança produtiva restrita a CoreCfgProjection, CfgTransition.Kind e CfgGraph.
O único projector emite TRUE para trueDestination e FALSE para falseDestination.
Kinds tipados BRANCH_TRUE/BRANCH_FALSE preservam igualdade/hash distintos mesmo
com target igual. CfgGraph exige source Sequence/Branch, target Sequence/LabelId,
Entry inventariada e namespace Unit correto; guardas ENTRY/JUMP/RETURN/HALT intactas.
Nenhum nó/algoritmo IF/ELSE/join, leader detection ou posição física define controle.

Preflight upstream valida PREDICATE, known(bool) e fechamento dos dois targets.
O CFG não reimplementa type checking, não avalia predicate e não poda por literal.
unknown(known(bool)) conserva dois braços; unknown_type é INVALID_IR, sem produto.
Diagnostics e obrigação de pureza do validator são preservados sem promoção a prova.
Branch/predicate/TypeRef/dependencies/read/remainingReads/reason/headers/origins
permanecem nos mesmos objetos AIR retidos por SequenceNode.source, sem storage
adicional, string de condição, deep copy ou mutação da Publication.

Regras por Sequence × Entry incluem órfãs; não filtram reachability nem fundem
Entries. Uma Sequence por nó. M2 reconverge só por Jump; M3 falso direto não fabrica
nó/operação e nested usa destinos próprios; M4 Halt/Return não reconvergem; M5
preserva dois braços para o mesmo destino. 258 Branches conservam 516 alternativas.
Custo da projeção/validação do produto continua O(N log N + T), memória O(N + T);
T inclui duas regras por Branch × Entry. Sem claim sobre custo do AirValidator,
benchmark ou performance gate.

## Oracle, RED/GREEN e regressões

EVAL-CFG-029: 25 métodos, expected manual anterior ao builder em records/enums
próprios de nós/arestas/arm/contexto. Não é gerado por CfgGraph/CfgTransition,
CoreCfgProjection, DOT, toString ou JSON. M2–M5 usam conjuntos exatos e assertions
negativas, preservação por identidade, targets inválidos, role errado, duas Entries,
órfãs, cardinalidade e invariantes do produto.

RED antes de produção:
`mvn -B -ntp -Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-006-m2 -pl :cfg-kernel
-Dtest=EvalCfg029Test test`: exit 1 por BRANCH_TRUE/BRANCH_FALSE ausentes.
Tentativa anterior falhou por download de plugin e não contou como RED.
GREEN pelo mesmo comando normal: exit 0, 25 testes/zero skips.
Execução intermediária isolada do 029, enquanto o switch do 028 era reconciliado,
não foi contabilizada como gate; a bateria normal completa foi executada depois.

EVAL-CFG-025 permanece byte a byte intacto, 17 métodos. EVAL-CFG-028 conserva seus
22 métodos/fixtures e inventário manual. Única reconciliação: switch exaustivo
rejeita os novos kinds fora do observer linear; recusa obsoleta de Branch deixa a
lista de unsupported. A fixture mista ainda contém Branch, Dispatch, Raise, Invoke
e Opaque, e continua exigindo graph absent por todos os quatro unsupported.
Nenhuma prova positiva Branch foi transferida ao 028; todas pertencem ao 029.
Porta/preflight/registry, source lock e profile-obligations.json intactos.

## Mutantes e metamorfismos

Cada seleção abaixo executada com `mvn -B -ntp
-Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-006-m2 -pl :cfg-kernel
-Dtest=EvalCfg029Test#<método> test`: exit 1, uma assertion failure, zero errors/skip.
Guarda de endpoints desativada temporariamente para provar força do expected
independente. Fontes/guardas restauradas em finally antes dos gates.

| Mutante | Método |
| --- | --- |
| A — TRUE/FALSE invertidos | diamondMatchesManualOracleWithoutSiblingOrImplicitJoinEdges |
| B — unknown sem FALSE | unknownBooleanRetainsPredicateDependenciesReasonTypeAndOriginByIdentity |
| C — posição física | physicalPermutationPreservesCorrelatedControlAndCfgIds |
| D — destino igual colapsado | sameDestinationPreservesTwoAlternativesAndThePredicate |
| E — Halt reconvergente | terminatingHaltArmNeverReconvergesOrFallsThrough |
| F — literal true com pruning | literalTrueDoesNotPruneFalseAlternative |
| G — nested usando join externo | nestedBranchesUseTheirOwnExplicitDestinations |

MR-BRANCH-1 permutation adversarial conserva inclusive IDs CFG; 2 alpha rename
mapeia Unit/Entry/Label/Operation/Operand explicitamente; 3 display/origin presentation
não escolhe destino e permanece observável; 4 split com Jump preserva ocorrências
e pontos originais na continuação Branch. Todos verdes, sem apagar IDs/origins.

Challenge confirmou ambas alternativas, tipos/targets inválidos sem reparo,
Halt/Return sem reconvergência, contexto/órfãs/cardinalidade/identidade preservados,
ausência de nós IF/ELSE, frontend e demais primitives, e claims restritos.
Semantic com MAVEN_ARGS=-DskipTests=true falhou (exit 1). Dispatch injetado por nome
Java qualificado sem import compilou/passou testes, mas architecture rejeitou sua
dependência bytecode (exit 1). Restaurado antes da bateria final.
Detector semantic rejeita 95 fixtures: suíte/método ausente/extra/duplicate/foreign,
zero, skip, failure e error. Inventário manual 025=17, 028=22, 029=25.
Architecture acrescenta somente Operations$Branch à lista exata de operações;
Dispatch/Invoke/Raise/Opaque/Local*/IndirectJump seguem proibidos. Sem findings pendentes.

## Evals e milestone

EVAL-CFG-029 implemented, local sem oráculo upstream amplo atribuído.
EVAL-CFG-004 implemented: ramo falso direto sem escrita fabricada (O-03-STRUCT),
unknown com read conhecido e sem escrita/chamada implícita (O-27), nested/destino igual.
Foundation e 025/028 permanecem implemented. Avaliação integral mantém planned:

- 003: diamond/O-02-STRUCT e predicate unknown_type são evidência parcial;
  O-74-STRUCT inclui concat/not/read unknown_type com sameDomain e dependência
  unknown_type da variante X-33, não integralmente exercitados;
- 005: M4 e 025/028/029 provam saídas sem fallthrough e O-04-STRUCT no controle
  suportado; falta chamador/callee com continuação normal exigido por O-19-STRUCT;
- 014: metamorfismos de Branch são parciais; falta O-66/equivalência de ingressos.

MVP-CFG-01 (local) = implemented: CF1/025, M1/028, M2/M3/M4/M5/029 e limites
estruturais pertinentes de M7. Dangling LabelId e unknown_type invalidam o input;
terminador ausente não é construível/reparado; unsupported é explícito e falhas
não viram empty CFG. Transporte/JSON/CLI não são prerequisite deste marco.
AIR-STRUCTURE@2, AIR-LOCAL-CONTROL@2 e AIR-INDIRECT-CONTROL@2 não implementados;
implemented_profiles=[] intacto. Sem dataflow, invocação, controle aberto/local/
indireto, JSON/CLI, lowerer, performance/integration ou próximo backlog promovido.

## Gates e CI

Bateria executada com MAVEN_OPTS=-Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-006-m2;
Maven 3.9.16/runtime local Temurin 25.0.4, bytecode Java 21 sem preview:

| Comando | Exit | Resultado |
| --- | --- | --- |
| bash scripts/harness/check-docs.sh | 0 | PASS |
| bash scripts/harness/check-harness.sh | 0 | 41 testes |
| bash scripts/harness/check-fast.sh | 0 | PASS |
| bash scripts/harness/check-architecture.sh | 0 | 82 testes, 13 fontes/21 classfiles; jdeps/javap |
| bash scripts/harness/check-semantic.sh | 0 | 64 métodos, 95 fixtures do detector, zero skip |
| mvn -B -ntp clean test | 0 | 82 testes |
| mvn -B -ntp clean verify | 0 | 82 testes |
| git diff --check | 0 | sem erro |
| bash scripts/harness/check-performance.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-integration.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-full.sh | 3 | fast/architecture/semantic PASS; para em performance |

CI Temurin 21 confirmada success para `66972196d158fc3cdbc872887d2a179fee0149b3`
antes do encerramento: [run 34075672048](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34075672048).
Passos de SHA, instalação air-java/repo isolado, fast, architecture e semantic
concluídos com sucesso. O HEAD documental final terá CI conferida no PR antes do
handoff; a evidência final fica vinculada ao commit/check desse PR.
Após arquivamento, fast e diff --check passaram novamente (exit 0), validando lifecycle.
Todos os comandos exigidos foram executados. Performance/integration não oferecem
provas de produto; os evals amplos/perfis acima não foram executados integralmente.

## Handoff

WORK-CFG-006/BACKLOG-CFG-006 completed; registry.active=[].
[PR #7](https://github.com/Gustavo2358/analysis-cfg/pull/7) contra main para human review.
BACKLOG-CFG-004/007 e posteriores não iniciados. Sem merge, auto-merge ou publicação
de pacote. Próximo checkpoint exclusivamente review humano deste PR.
