# Fontes fixadas do discovery

Todos os links apontam para os SHAs congelados, não para branches. Campos/símbolos indicam o que foi inspecionado; a âncora é somente o ponto inicial de navegação.

## F01

[proleap-poc: src/main/antlr4/Cobol.g4:1164](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/antlr4/Cobol.g4#L1164)

callStatement, callUsingPhrase, callByReference/Content/ValuePhrase, callGivingPhrase, exception clauses.

## F02

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/Ast.java:210](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/Ast.java#L210)

CallStatement.targetSyntax/target/arguments/returning/exceptionFlow; CallArgument.passingMode/kind/value/writtenText; Meta.

## F03

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/AstBuilder.java:663](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/AstBuilder.java#L663)

buildCall; normalContinuations/addSentenceContinuations; meta/sourceMap.provenance.

## F04

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/CobolSemanticProduct.java:551](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/CobolSemanticProduct.java#L551)

CallFact; DataReference/NominalBinding; StatementHeader; Provenance; IfFact.

## F05

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/projection/CobolSemanticProductProjector.java:262](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/projection/CobolSemanticProductProjector.java#L262)

callCapability, CALL projection, addUnprojectedCallSurfaceGaps, callReadiness.

## F06

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/transport/SemanticProductJsonWriter.java:130](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/semanticproduct/transport/SemanticProductJsonWriter.java#L130)

CallDocument and call serialization; dataReference.wholeItemAccess.

## F07

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/ScalarMoveSemantics.java:13](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/ScalarMoveSemantics.java#L13)

scalar admission; Copy.FULL_IDENTITY/UNAVAILABLE; analyze length equality; continuation.

## F08

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/ReferenceOccurrenceCollector.java:85](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/ReferenceOccurrenceCollector.java#L85)

CALL_TARGET / arguments / returning occurrence roles.

## F09

[proleap-poc: src/main/java/io/github/gustavo2358/cobolexplorer/ProgramNameCanonicalizer.java:29](https://github.com/Gustavo2358/proleap-poc/blob/8722945cc4cd2052c6091533f6ee6989278aa2f8/src/main/java/io/github/gustavo2358/cobolexplorer/ProgramNameCanonicalizer.java#L29)

external/nested/dynamicExternal; policy-dependent keys, not dependency resolver.

## L01

[cobol-lower: adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/Wire12.java:37](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/Wire12.java#L37)

typed input CALL wire.

## L02

[cobol-lower: adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/Wire.java:68](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/Wire.java#L68)

prior supported input CALL wire.

## L03

[cobol-lower: adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/SpJsonDecoder.java:38](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/SpJsonDecoder.java#L38)

decoder variants and unsupported variants reporting.

## L04

[cobol-lower: adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/Materialize.java:42](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/adapters/src/main/java/io/github/gustavo2358/lower/adapters/sp/Materialize.java#L42)

CALL/IF -> OtherStatement, loss of payload.

## L05

[cobol-lower: core/src/main/java/io/github/gustavo2358/lower/domain/SpInput.java:20](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/core/src/main/java/io/github/gustavo2358/lower/domain/SpInput.java#L20)

StatementFact, Variant, OtherStatement.

## L06

[cobol-lower: core/src/main/java/io/github/gustavo2358/lower/application/ScalarMoveAdmission.java:67](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/core/src/main/java/io/github/gustavo2358/lower/application/ScalarMoveAdmission.java#L67)

typed root statements/continuation/shape/proof admission.

## L07

[cobol-lower: core/src/main/java/io/github/gustavo2358/lower/application/ScalarSequenceAssembler.java:8](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/core/src/main/java/io/github/gustavo2358/lower/application/ScalarSequenceAssembler.java#L8)

Entry.start + explicit normalContinuation; Assign + Return.

## L08

[cobol-lower: core/src/main/java/io/github/gustavo2358/lower/application/MoveHandler.java:9](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/core/src/main/java/io/github/gustavo2358/lower/application/MoveHandler.java#L9)

ObjectPlace + LiteralText and correlations.

## L09

[cobol-lower: core/src/main/java/io/github/gustavo2358/lower/application/SourceOrigins.java:13](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/core/src/main/java/io/github/gustavo2358/lower/application/SourceOrigins.java#L13)

source/derived original+expanded provenance.

## L10

[cobol-lower: core/src/main/java/io/github/gustavo2358/lower/application/OutputAssessment.java:1](https://github.com/Gustavo2358/cobol-lower/blob/18016f16b4f63149eb1bb4ca13db7e12593d8909/core/src/main/java/io/github/gustavo2358/lower/application/OutputAssessment.java#L1)

validation status mapping.

## N01

[analysis-ir: especificacao/04-operacoes.md:81](https://github.com/Gustavo2358/analysis-ir/blob/51b4d9a8ae0364232bd97103cd73a77e1a34996c/especificacao/04-operacoes.md#L81)

operations, Invoke terminator, target/arguments/effects/contract.

## N02

[analysis-ir: especificacao/05-controle-e-invocacoes.md:7](https://github.com/Gustavo2358/analysis-ir/blob/51b4d9a8ae0364232bd97103cd73a77e1a34996c/especificacao/05-controle-e-invocacoes.md#L7)

outcomes, local labels, propagation, halt/diverge/open.

## N03

[analysis-ir: especificacao/06-incompletude-e-proveniencia.md:27](https://github.com/Gustavo2358/analysis-ir/blob/51b4d9a8ae0364232bd97103cd73a77e1a34996c/especificacao/06-incompletude-e-proveniencia.md#L27)

coverage, source/derived/contract origins, envelope.

## N04

[analysis-ir: especificacao/08-contrato-de-consumidores.md:1](https://github.com/Gustavo2358/analysis-ir/blob/51b4d9a8ae0364232bd97103cd73a77e1a34996c/especificacao/08-contrato-de-consumidores.md#L1)

consumer dependency modes and before invocation.

## N05

[analysis-ir: especificacao/09-extensibilidade-e-compatibilidade.md:1](https://github.com/Gustavo2358/analysis-ir/blob/51b4d9a8ae0364232bd97103cd73a77e1a34996c/especificacao/09-extensibilidade-e-compatibilidade.md#L1)

extensions/profile limits do not authorize noop.

## N06

[analysis-ir: bindings/json-v1.md:234](https://github.com/Gustavo2358/analysis-ir/blob/51b4d9a8ae0364232bd97103cd73a77e1a34996c/bindings/json-v1.md#L234)

normative JSON full invoke, operands, scopes, contracts/outcomes.

## J01

[air-java: air-model/src/main/java/io/github/gustavo2358/air/model/Operations.java:103](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/model/Operations.java#L103)

Invoke implements Terminator; full payload.

## J02

[air-java: air-model/src/main/java/io/github/gustavo2358/air/model/Interactions.java:28](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/model/Interactions.java#L28)

Targets, NamePolicy, arguments, signatures, effects, contract.

## J03

[air-java: air-model/src/main/java/io/github/gustavo2358/air/model/Expressions.java:22](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/model/Expressions.java#L22)

Read/FitText/TrimRight/Unknown types.

## J04

[air-java: air-model/src/main/java/io/github/gustavo2358/air/model/Places.java:11](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/model/Places.java#L11)

ObjectPlace ObjectId and operand Header.

## J05

[air-java: air-model/src/main/java/io/github/gustavo2358/air/model/Operands.java:40](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/model/Operands.java#L40)

roots of computed target/arguments/results/effectOperands.

## J06

[air-java: air-model/src/main/java/io/github/gustavo2358/air/model/Scopes.java:55](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/model/Scopes.java#L55)

NoMemory/WithinMemory/VisibleMemory/AllMemory.

## J07

[air-java: air-model/src/main/java/io/github/gustavo2358/air/validation/OperationChecks.java:209](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/validation/OperationChecks.java#L209)

invocation validation and unconditional I-56 obligation.

## J08

[air-java: air-model/src/main/java/io/github/gustavo2358/air/validation/ValidationResult.java:23](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/validation/ValidationResult.java#L23)

SEMANTIC_OBLIGATION independent of STRUCTURALLY_VALID.

## J09

[air-java: air-json/src/main/java/io/github/gustavo2358/air/json/BindingReader.java:154](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-json/src/main/java/io/github/gustavo2358/air/json/BindingReader.java#L154)

known invoke fields; mapping only Return, unsupported limit.

## J10

[air-java: air-json/src/main/java/io/github/gustavo2358/air/json/BindingWriter.java:1](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-json/src/main/java/io/github/gustavo2358/air/json/BindingWriter.java#L1)

terminator, instruction, expression coverage.

## J11

[air-java: air-json/src/main/java/io/github/gustavo2358/air/json/AirJson.java:51](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-json/src/main/java/io/github/gustavo2358/air/json/AirJson.java#L51)

coverage first; every semantic obligation rejected.

## J12

[air-java: air-model/src/test/java/io/github/gustavo2358/air/validation/ContractSuite.java:174](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/test/java/io/github/gustavo2358/air/validation/ContractSuite.java#L174)

existing invocation positive/negative tests; literalInvokeFixture.

## C01

[analysis-cfg: cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java:28](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java#L28)

terminator allowlist; sequences and intraprocedural successors.

## C02

[analysis-cfg: cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CfgTransition.java:23](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CfgTransition.java#L23)

transition kinds and payload.

## C03

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/IndexBuilder.java:70](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/IndexBuilder.java#L70)

terminator admission, arity, operation offsets and transitions.

## C04

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/ProgramIndex.java:64](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/ProgramIndex.java#L64)

generic operation class buckets.

## C05

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/query/ProgramPoint.java:18](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/query/ProgramPoint.java#L18)

BEFORE entry+operation identity.

## C06

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/query/BatchReplayer.java:23](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/query/BatchReplayer.java#L23)

prefix replay at operation offset; BEFORE/AFTER/OUTCOME.

## C07

[analysis-cfg: analysis-values/src/main/java/io/github/gustavo2358/analysis/values/TextProfile.java:84](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/TextProfile.java#L84)

admission, transferOperation, sourceOpen.

## C08

[analysis-cfg: analysis-values/src/main/java/io/github/gustavo2358/analysis/values/PossibleValuesAnalysis.java:44](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/PossibleValuesAnalysis.java#L44)

generic flow execution invokes profile; transferEdge.

## C09

[analysis-cfg: analysis-values/src/main/java/io/github/gustavo2358/analysis/values/Candidates.java:31](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/Candidates.java#L31)

finite candidates, supports and open remainder.

## C10

[analysis-cfg: analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ValueFact.java:16](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ValueFact.java#L16)

reachability and candidate-specific supports; three remainders.

## C11

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/plan/SiteInterest.java:11](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/plan/SiteInterest.java#L11)

operation class/filter/query factories.

## C12

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/SitePlanner.java:12](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/SitePlanner.java#L12)

generic bucket selector and requests.

## C13

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/ConsumerRegistration.java:8](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/ConsumerRegistration.java#L8)

dependencies/interests/requests/consumer.

## C14

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/PlanningExecution.java:11](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/PlanningExecution.java#L11)

keyed providers/batch sharing and atomic FactSink.

## C15

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/plan/AnalysisKey.java:8](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/plan/AnalysisKey.java#L8)

implementation/version/profile/direction/precision/options/Entry.

## C16

[analysis-cfg: analysis-values/src/main/java/io/github/gustavo2358/analysis/values/PossibleValuesProvider.java:12](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/PossibleValuesProvider.java#L12)

ObjectId -> ValueFact, not arbitrary Expression.

## C17

[analysis-cfg: analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/DefaultValuePlan.java:13](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/DefaultValuePlan.java#L13)

Assign write observation before Sequence terminator.

## C18

[analysis-cfg: analysis-kernel/src/main/java/io/github/gustavo2358/analysis/consumers/SiteView.java:16](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/consumers/SiteView.java#L16)

STRUCTURAL only, not reachability.

## C19

[analysis-cfg: analysis-adapters/src/main/java/io/github/gustavo2358/analysis/adapters/ResultJson.java:16](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-adapters/src/main/java/io/github/gustavo2358/analysis/adapters/ResultJson.java#L16)

W5 existing result serialization.

## C20

[analysis-cfg: analysis-values/src/test/java/io/github/gustavo2358/analysis/values/SupportSourceTest.java:18](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/test/java/io/github/gustavo2358/analysis/values/SupportSourceTest.java#L18)

candidate-to-producer association and cyclic supports.

## C21

[analysis-cfg: analysis-values/src/test/java/io/github/gustavo2358/analysis/values/ValuesTest.java:22](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/test/java/io/github/gustavo2358/analysis/values/ValuesTest.java#L22)

existing point/unknown/join/admission/unreachable tests.

## C22

[analysis-cfg: analysis-values/src/test/java/io/github/gustavo2358/analysis/values/ReplayTest.java:33](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/test/java/io/github/gustavo2358/analysis/values/ReplayTest.java#L33)

existing interior instruction points.

## C23

[analysis-cfg: analysis-values/src/test/java/io/github/gustavo2358/analysis/values/PlanningContractTest.java:50](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/analysis-values/src/test/java/io/github/gustavo2358/analysis/values/PlanningContractTest.java#L50)

existing site/query/planning contracts.

## C24

[analysis-cfg: cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/CfgBuildCoordinator.java:35](https://github.com/Gustavo2358/analysis-cfg/blob/e9daaed9f8a2f2df3926a624f1067aba3d53297c/cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/CfgBuildCoordinator.java#L35)

structural preflight and unsupported operation behavior.

## N07

[analysis-ir: especificacao/02-tipos-valores-e-operandos.md:145](https://github.com/Gustavo2358/analysis-ir/blob/51b4d9a8ae0364232bd97103cd73a77e1a34996c/especificacao/02-tipos-valores-e-operandos.md#L145)

fit_text/trim_right/read, tipos e papéis de operandos.
