# analysis-dataflow-result / 1.0.0 — snapshot de review

**Design/harness somente.** Nome/versão do futuro artefato derivado; nenhum writer,
codec, CLI ou resultado calculado existe nesta entrega. H6 aprova separação do
resultado/CLI; este snapshot permite review dos campos antes W5. Não é binding AIR,
CFG JSON v2 ou schema normativo do upstream.

[Exemplo CP4E](../evals/cp5/result-review.json) usa envelope `design` que declara
NOT_EXECUTED; `result` contém o expected conceitual. Não reportar seu executionStatus
STABLE como execução real. Statistics ficam unavailable/null, sem counters inventados.
[Contrato de campos](../evals/cp5/result-contract.json) é validado pelo harness junto
às relações de owner, scopes e remainder; não é um codec nem um validator AIR.

| Campo | Significado/obrigação |
| --- | --- |
| schema/version | analysis-dataflow-result / 1.0.0, evolução local explícita |
| analysisKey | implementação/versão, profile, direção, precisão, options, Entry; cache pertence à sessão/snapshot |
| publicationId/unitId/entryId | identidade completa, sem ordinais internos exportados |
| executionStatus | STABLE, ADMISSION_LIMIT, ANALYSIS_LIMIT, UNSUPPORTED ou INVALID_INPUT do solver/run; recusa isolada de query não altera esse status |
| modelScope/sourceScope | KNOWN_GRAPH_ENTRY separado da abertura CONTROL/Unit da fonte |
| observations | um resultado por query única (point, subject) do plano em run STABLE e observation COMPLETE, inclusive queries recusadas |
| queryStatus/queryReason | VALUE com motivo null, ou UNSUPPORTED_POINT com motivo explícito não vazio; status por query separado de executionStatus |
| point | OperationId, Entry, before/after e outcome solicitado; after fronteira não admitida é UNSUPPORTED_POINT; outcome pode ser null nesse caso |
| subject/place/storage | ObjectPlace/ObjectId consultado e Cell/Storage base; Object não é memória exclusiva |
| reachability | REACHABLE/UNREACHABLE_IN_MODEL para VALUE; null em UNSUPPORTED_POINT, que não afirma inalcançabilidade |
| value | known(text), Candidates/Saturated, enumerated e modelValueRemainder; null se inalcançável no modelo ou query recusada, distinguidos por queryStatus/reachability |
| sourceUnknownRemainder/effectiveUnknownRemainder | abertura pertinente de fonte e OR com remainder de modelo |
| saturationReason/limitReason | cardinalidade local diferente de budget interrompido; causa/fase explícitas |
| precision | exatidão somente no modelo declarado; sem claim de testemunho de caminho ou fonte completa |
| premises/evidence/provenance | refs pertinentes da AIR/snapshot/regra, sem path tree ou causalidade inventada |
| statistics | métricas por fase/run/Entry/epoch; unavailable neste exemplo, medidas na implementação |

IDs usam domínio/localId e owners completos de Publication/Unit quando pertinentes.
A forma estrutural é proposta de transporte local, sem exigir codec AIR para validar
resultado. A prova futura de resolução dos refs usa a AIR correlacionada. IDs/hashes
CP4E só entram em fixtures/evidências, nunca constantes de produção.

O exemplo before(Return) conserva modelValue {PROGA} fechado, fonte CONTROL aberta,
restante efetivo true e precisão condicionada ao modelo. Nenhum gap text fecha scope.
O mesmo lote contém after(Return) recusado, preservando o primeiro resultado:

| executionStatus | point | queryStatus | value | queryReason |
| --- | --- | --- | --- | --- |
| STABLE | before(Return) | VALUE | {PROGA}, fechado no modelo | null |
| STABLE | after(Return) | UNSUPPORTED_POINT | null | estado de memória posterior não admitido |

`VALUE` identifica uma query admitida, inclusive um ponto UNREACHABLE_IN_MODEL,
cujo value é null conforme o contrato de alcance. Em `UNSUPPORTED_POINT`, value,
reachability, sourceUnknownRemainder, effectiveUnknownRemainder e precision são
null: a recusa não calcula valor, alcance ou precisão nesse ponto. O sourceScope
global continua declarado. Refs permanecem listas de identidades completas e só
podem justificar a recusa; não constituem evidência de valor. queryReason é texto
explicativo, não inferência de semântica a partir do nome de operação ou de gaps.

BEFORE usa outcome=null. AFTER admitido exige outcome explícito; after(Return/Halt)
sem estado posterior usa outcome=null e UNSUPPORTED_POINT, sem inventar aresta.
point e subject reproduzem a query solicitada, mesmo quando recusada. No run STABLE com observation COMPLETE,
o plano externo de queries deve ser comparado com os resultados: exatamente um por
par único de (point completo, subject completo), sem omissões, duplicações ou
substituições. Requests repetidos compartilham o resultado e a ordem da resposta
não é semântica. O snapshot guarda esse plano em `design.requestedQueries`, separado
das observations; ele é oracle manual de review, não campo adicional do transporte.
`validate_result(result, requested_queries)` verifica a cobertura quando recebe o
plano; validar só a forma de um resultado não demonstra cobertura do lote.

Não enumerado por saturação mantém restante e motivo; não alcançado no modelo usa
value=null, nunca Candidates({},false). Run limitado/recusado como um todo continua
com observations=[] e não publica facts provisórios; limitReason não pode coexistir
com STABLE. A recusa de after(Return/Halt) sozinha não é falha do solver/run e não
pode promover executionStatus a UNSUPPORTED nem apagar outras queries válidas.

O harness exige o lote misto do snapshot e rejeita aborto global e desaparecimento
da query recusada. Os challenges `unsupported-query-aborts-batch` e
`unsupported-query-disappears` ativam em W3/S6 contra implementação real; nesta
preparação a prova é apenas do contrato/validator, sem execução de engine.

W5 deve congelar por review o wire definitivo, budgets/defaults, parser nominal de
reports e falhas antes do writer. A CLI separada usará AIR file → reader → BuildCfg
→ sessão → PossibleValues → plano padrão → writer. Plano padrão observa destinos
escritos por Sequence em before(terminator), sem selecionar primeiro Object/WS-PGM/
PROGA ou varrer todos Objects por Sequence. CLI CFG existente mantém contrato próprio.

## Completion do run e preparação com consumers

`AnalysisDataflowResult.completion` contém **somente** admission, analysis e
observation, cada qual com status/reason. Não contém consumers nem publication.

| executionStatus externo | admission | analysis | observation |
| --- | --- | --- | --- |
| ADMISSION_LIMIT | LIMIT | NOT_STARTED | NOT_STARTED |
| UNSUPPORTED / INVALID_INPUT | REJECTED | NOT_STARTED | NOT_STARTED |
| ANALYSIS_LIMIT | COMPLETE | LIMIT | NOT_STARTED |
| STABLE | COMPLETE | STABLE | COMPLETE / LIMIT / FAILED / NOT_STARTED |

ADMISSION_LIMIT e ANALYSIS_LIMIT exigem limitReason não vazio, igual ao motivo da
fase respectiva. Demais executionStatus têm limitReason=null. REJECTED/LIMIT/FAILED
exigem motivo; demais fases têm reason=null. ADMISSION_BUDGET nunca é UNSUPPORTED.
Observation não COMPLETE mantém apenas seu lote vazio; budget de replay não altera
STABLE nem vira UNSUPPORTED_POINT. B1 continua aplicado por batch completo.

`PreparedAnalysisResult` é envelope de preparação com schema/version, resultId,
publicationId, results, consumerPlan, consumers, preparationStatus e partialPolicy.
results liga cada observationBatchId único a um AnalysisDataflowResult. Batches da
mesma AnalysisKey podem compartilhar run; seus status de análise/admissão e scopes
precisam concordar. Não é autorização para repetir solver por query/consumer.

ConsumerPlan contém consumerId, requiredAnalysisKeys completos e
requiredObservationBatchIds. Todo batch requerido deve ser resolvido e seu
AnalysisKey listado. Outcomes dos consumers têm id/status/reason e devem cobrir
exatamente o plano independente, sem omissões ou alteração silenciosa de dependências.

Somente dependências indisponíveis forçam NOT_STARTED/DEPENDENCY_UNAVAILABLE.
Dependências satisfeitas permitem COMPLETE (reason=null), FAILED/LIMIT com motivo,
ou NOT_STARTED com motivo explícito caso o trabalho não tenha sido executado.
StructuralConsumer pode ter ambas listas vazias e completar com results=[]; não
inventar run STABLE. SiteView estrutural já deve ter sido admitida pelo contrato W1.
Um consumer que requer somente AnalysisKey estável não depende de batch limitado.

partialPolicy=EXPLICIT_PARTIAL_BY_DEPENDENCY: cada batch e cada consumer é atômico.
preparationStatus COMPLETE exige todos os runs/batches solicitados completos e todos
os consumers COMPLETE; caso contrário INCOMPLETE. Status e resultados concluídos de
consumers independentes permanecem disponíveis. A preparação não afirma entrega.
O wire dos bundles de facts continua para W4/W5; esta remediação especifica somente
identidades, dependências e completion, sem implementação de consumers.

## DeliveryReceipt externo ao payload

Recibo separado: schema=analysis-delivery-receipt/version=1.0.0, resultId,
resultSha256, destination, status COMPLETE/FAILED/LIMIT e reason. Correlacionar ao
resultId preparado e destino da tentativa. COMPLETE exige hash dos bytes completos
entregues e confirmação do chamador; FAILED/LIMIT exige motivo, e pode usar hash=null
quando a falha ocorreu antes do hash completo. Se houver hash, deve identificar os
bytes completos pretendidos, não prefixo truncado. Não impor buffer integral ou
codec/serialização nesta revisão; W5 vincula esses campos ao writer real.

O recibo é criado após a tentativa, armazenado/retornado fora do payload e nunca
usado para reescrevê-lo. Falha de encoding/escrita/finalização preserva o resultado
preparado e o fixpoint. Uma nova tentativa gera novo recibo. O sucesso end-to-end
exige preparationStatus COMPLETE **e** receipt COMPLETE correlacionado. Um recibo
COMPLETE de payload INCOMPLETE confirma transporte, sem promover preparação parcial.
Sem recibo externo não há confirmação de entrega, mesmo com preparação completa.

O snapshot de [batch B1](../evals/cp5/result-review.json) continua NOT_EXECUTED.
O [witness de dependências F3](../evals/cp5/phase-review.json) conserva StructuralConsumer
COMPLETE após query-batch LIMIT. Testes verificam ADMISSION_LIMIT, writer failure,
hash/identity mismatch, self-certification, batches e consumers independentes.
[Decisão F](cp5-post-audit.md#f). Nenhum runtime, workflow engine ou writer implementado.
