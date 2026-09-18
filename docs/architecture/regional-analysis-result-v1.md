# Regional analysis result — ST-W5.3

W1 operational policy: logical-only by default; physical propagation requires explicit experimental opt-in. The wire shape and historical profile label below are unchanged. `metrics.logicalOnlyMode` / `metrics.experimentalPhysicalMode` identify the dependency execution policy; regional observation `statistics.values.logicalOnlyMode` identifies its policy. Zero physical work is observable in physicalGroupsApplied/physicalWritesApplied counters. Existing remainder/analysisReasons/logicalAlternatives express candidate support without physical completeness. See [logical-only](../product/logical-text-w1.md).


Status: API, composição e wire implementados; FAST de ST-W5.3 aprovado; qualificação W5/M3 pendente. Esta rota é separada do
resultado escalar [1.1.0](analysis-dataflow-result-v1.md), que permanece intacto.
O schema regional será fechado e testado por reader independente antes da
qualificação. Nenhuma declaração AIR é criada para atender uma consulta.

## Subject e resolução compartilhada

`StorageSubject.NamedObject(ObjectId)` consulta objeto/vista ou Cell lógica.
`StorageSubject.PhysicalRange(StorageId, StorageRange, Memory.Codec)` consulta
bytes com interpretação explicitamente escolhida. StorageRange usa BigInteger,
meio-aberto, unidade OCTET; fim ausente não é zero. A forma física exige Region,
nunca Cell, e deve caber no extent conhecido. Faixa aberta só cabe em extent
aberto. Zero length é distinto de unknown. A projeção pode recortar partições
existentes; não altera storage nem cria novas declarações, views ou ObjectIds.

StorageIndex é a autoridade compartilhada de resolução/admissão para RD e values.
A Unit da Entry deve existir. Objeto deve ser próprio ou explicitamente visível.
A faixa é acessível quando sua base pertence à Unit, é SHARED/EXTERNAL no modelo,
ou está contida numa vista própria/visível. Uma vista não concede acesso a bytes
fora de sua faixa. Base global PRIVATE sem vista visível não fica acessível apenas
porque seu StorageId existe. IDs, limites ou subjects incompatíveis produzem
UNSUPPORTED_SUBJECT, sem mutação de AIR, coerção para Cell ou valor vazio fechado.

`ReachingDefinitions.Execution.observeStorage` usa os mesmos estados estáveis e
BatchReplayer da API por ObjectId. `DefinitionFact` mantém sua forma, eventos,
intervalos sobreviventes, before/after/outcome e origem. O subject está na query.
Comparação de subjects usa owners completos, limites e codec discriminado; não
usa nome de exibição, toString de records ou ordinais internos. Cada logical
query é materializada uma vez, mesmo com pedidos duplicados.

## Produto e composição

RegionalAnalysis prepara um CFG e uma AnalysisSession para as Entries selecionadas,
executa ReachingDefinitions e RegionalValuesAnalysis uma vez cada, e materializa as
mesmas queries em dois batches. RegionalAnalysisResult contém somente records de
observação, inventário de referências e métricas. Não retém publicação, operações,
CFG, sessão, estados, stores, ByteImage, Trace ou roots do solver. Falha de admissão,
execução ou batch impede produzir um resultado COMPLETE. Query individual recusada
permanece explícita e não invalida as outras queries. Todos os estados são por Entry.

A API legada por ObjectId mantém sua admissão textual. A nova API aceita qualquer
codec declarado: bytes conhecidos podem permanecer visíveis com projeção textual
aberta quando o codec não é interpretado. Identificar um codec não o interpreta.
Referências de uncertainty e LabelType no codec de consulta devem existir na AIR
validada. A Cell lógica nunca recebe extent/offset físico por conveniência.

## Shape fechado: regional-analysis-result 1.0.0

Esta definição precede o writer. Todos os campos abaixo são obrigatórios, inclusive
os explicitamente anuláveis. Campos extras, discriminantes desconhecidos e IDs com
owners incoerentes são erro de contrato. Strings decimais usam `0|[1-9][0-9]*`, sem
sinal ou leading zero. Inteiros BigInteger nunca são números JSON. Arrays semânticos
não têm cap K. Objetos JSON são ordenados por chave; listas de queries usam point e
subject tipados; IDs, contribuições, alternativas, fragmentos e sets são estáveis.
Nenhuma lista ordenada de supports afirma ordem de execução.

```text
Result = {
 schema: "regional-analysis-result", version: "1.0.0", resultId: string,
 publicationId: Id(publication), profile: "regional-text-images@2",
 status: "COMPLETE", pathWitness: "NOT_PROVIDED",
 referenceAuthority: "VALIDATED_AIR_PUBLICATION",
 inventory: { ids: [Id], storages: [Storage], scopes: [SourceScope] },
 observations: [{ point: Point, subject: Subject,
   rd: {status: QueryStatus, reason: PointReason|null, fact: RdFact|null},
   values: {status: QueryStatus, reason: PointReason|null, fact: ValueFact|null} }],
 statistics: {composition: MetricMap, rd: MetricMap, values: MetricMap,
              rdObservation: MetricMap, valueObservation: MetricMap}
}
Storage = {storageId: Id(storage), owner: Id(unit)|null,
 lifetime: ACTIVATION|PERSISTENT|EXTERNAL, visibility: PRIVATE|SHARED|UNKNOWN,
 origin: Id(origin), kind: REGION|CELL, extent: decimal|null,
 extentUnknown: Id(uncertainty)|null}
SourceScope = {entryId: Id(entry), publicationInventory: COMPLETE|PARTIAL|UNAVAILABLE,
 unitInventory: COMPLETE|PARTIAL|UNAVAILABLE, publicationUncertainties: [Id(uncertainty)],
 unitUncertainties: [Id(uncertainty)], entryUncertainties: [Id(uncertainty)]}
Subject = {kind: "NAMED_OBJECT", objectId: Id(object)}
        | {kind: "PHYSICAL_RANGE", storageId: Id(storage), range: Range, codec: Codec}
Range = {unit: "OCTET", start: decimal, end: decimal|null}
Location = {storageId: Id(storage), activation: Id(entry)|null,
 kind: "BYTE_RANGE"|"WHOLE_CELL", range: Range|null}
Interpretation = {location: Location, codec: Codec|null}
RdFact = {reachability: Reachability, unknownRemainder: boolean|null,
 resolutionRemainder: boolean, sourceUnknownRemainder: boolean,
 definitions: [{definition: Event, contributedRanges: [Location]}],
 premiseRefs: [Id(premise)], provenanceRefs: [Id(origin)], uncertaintyRefs: [Id(uncertainty)]}
Event = {entryId: Id(entry), operationId: Id(operation)|null,
 destination: Id(operand)|null, slot: integer, outcome: Outcome|null,
 storageId: Id(storage), kind: DefinitionEvent.Kind, unknown: boolean,
 origin: Id(origin), premiseRefs: [Id(premise)], uncertaintyRefs: [Id(uncertainty)], reasons: [string]}
ValueFact = {reachability: Reachability, interpretations: [Interpretation],
 candidates: [string]|null, modelValueRemainder: boolean|null,
 sourceUnknownRemainder: boolean, effectiveUnknownRemainder: boolean,
 candidateSupports: [{candidate: string, producers: [{evidence: Id,
    origin: Id(origin), premiseRefs: [Id(premise)]}]}],
 premiseRefs: [Id(premise)], evidenceRefs: [Id], provenanceRefs: [Id(origin)],
 modelReasons: [string], alternatives: [{interpretation: Interpretation,
   candidate: string|null, fragments: [Fragment]}]}
Fragment = {location: Location, kind: KNOWN_BYTES|UNKNOWN_BYTES|LOGICAL_VALUE|UNKNOWN_LOGICAL|LOGICAL_CAPTURE,
 bytes: [integer 0..255]|null,
 producer: {definition: Event, contributedRange: Location}|null,
 unknownWriter: Event|null, captures: [Capture], sourceGaps: [SourceGap], modelReasons: [string]}
Capture = {definition: Event, before: Point, sourceRange: Location,
 destinationRange: Location, sourceContribution: Location, destinationContribution: Location}
SourceGap = {affectedLocation: Location, origin: Id(origin), uncertaintyRefs: [Id(uncertainty)]}
Codec = {kind: "IDENTITY_BYTES"}|{kind: "ASCII_TEXT"}
      | {kind: "BINARY", signed: boolean, width: decimal, order: LITTLE|BIG}
      | {kind: "EXTENSION", name: string, version: string, logicalType: Type}
      | {kind: "UNKNOWN", reason: Id(uncertainty), logicalType: Type}
Type = {kind: "UNKNOWN", uncertainty: Id(uncertainty)}
     | {kind: "BUILTIN", name: Types.Builtin}
     | {kind: "EXTENSION", name: string, version: string}
     | {kind: "LABELS", unitId: Id(unit), labels: [Id(label)]}
```

Id, Point e Outcome reutilizam exatamente o shape discriminado do wire escalar
1.1.0: domain/localId/publication/unit/owner conforme o domínio; Point contém
position/entryId/operationId/outcome; Outcome contém kind e tag somente para
exception. QueryStatus é VALUE ou UNSUPPORTED_POINT. PointReason é o enum
ObservationBatch.PointReason. Reachability é REACHABLE ou UNREACHABLE_IN_MODEL.
MetricMap contém nomes de métricas e inteiros não negativos; métricas medem trabalho,
não alteram semântica. Slot -1 é reservado ao evento RD de conteúdo de Entry não
especificado; slots de writes/condições são não negativos.

## Invariantes e autoridade das referências

O inventário é capturado diretamente da AIR validada, independentemente dos fatos
calculados. Contém IDs de publication, unit, entry, label, object, operation, operand,
storage, origin, premise e uncertainty, sem duplicatas. Os storages mantêm o extent
original (Cell usa ambos os campos de extent null); escopos descrevem somente Entries
selecionadas. O inventário permite verificar closure e bounds do produto. O conteúdo
de origens, premissas e lacunas permanece na AIR identificada pela publicationId;
IDs listados não são substitutos de provas nem certificados de completude fonte.
Queries recusadas podem conter IDs inexistentes e são preservadas como pedidos,
mas nenhum fact aceito pode usá-los. Uma falha de preparação não gera este wire.

UNSUPPORTED_POINT tem reason obrigatório e fact null. VALUE tem reason null e fact
presente. UNREACHABLE_IN_MODEL tem candidates/modelValueRemainder null e nenhuma
alternativa, suporte ou definição alcançada; source remainder pode continuar true.
Reached sem candidato tem modelValueRemainder true. Effective é model OR source.
Faixa de comprimento zero pode decodificar texto vazio com zero fragmentos; é distinta
de unknown. Fim aberto permanece null e conserva remainder de resolução.

Cada alternativa corresponde à projeção de uma imagem abstrata correlacionada.
Fragmentos físicos cobrem sua faixa sem buracos/overlap; o decoder usa o codec
explícito. Não fazer produto cartesiano de branches. Produtor indica origem literal
com intervalo original sobrevivente; writer desconhecido nunca vira produtor literal.
Capturas têm ponto BEFORE e contribuições contidas nas faixas do evento. RD do destino
aponta para a escrita COPY, enquanto values pode apontar para o literal original.
São relações diferentes, ligadas pelo mesmo DefinitionEvent, sem witness de path.
Em LOGICAL_CAPTURE os bytes explicam a origem lida e não constituem bytes físicos
da Cell; fragmentos podem compartilhar WHOLE_CELL. Source gaps locais e herdados
são independentes de bytes conhecidos e de modelReasons.

A fronteira terá reader Python independente estrito, golden manual, negativos de
shape/IDs/ranges/codec/remainder/provenance, round-trip e integração por arquivo.
FAST de fechamento e a qualificação W5/M3 continuam obrigatórios.

## CLI por arquivo

Entry points separados: `io.github.gustavo2358.analysis.launcher.RegionalAnalysis`
e a API `io.github.gustavo2358.analysis.dataflow.RegionalAnalysis`. A CLI recebe:

```text
regional-analysis input.air.json output.result.json --result-id stable-id
  --unit unit-id --entry entry-id --before operation-id
  --range storage-id 0 8 ascii
```

A chamada real usa uma única linha. `--object object-id` substitui `--range ...`.
Codecs físicos de CLI: `ascii`, `ibm1047`, `identity`; não há default de charset.
Faixas recebem start/end exclusivos, com `open` para fim desconhecido. Pontos:
`--before`, `--after`, `--outcome-normal` e `--at-entry -`. IDs locais são ligados
à publicationId lida do arquivo; referências inválidas continuam recusadas.
O comando seleciona todas as Entries da publicação e publica a query explícita.
A API em memória permite selecionar Entries e materializar batches arbitrários.

Saídas: 0 resultado COMPLETE (inclusive query individual recusada); 2 uso inválido;
3 transporte/input I/O; 4 AIR/perfil recusado; 5 falha de execução; 6 falha de
publicação; 7 limite/recurso/validação incompleta. A entrega usa arquivo temporário
no mesmo diretório e move atômico; falha preserva o destino anterior. Não sobrescreve
o próprio input. stdout não mistura diagnóstico com JSON. Origem, premissas e
lacunas continuam consultáveis na AIR original pelas referências completas.

O reader `scripts/project/regional_result_wire.py` é independente do writer Java.
Ele verifica schema fechado, closure, owners, ranges/BigInteger, contribuições,
remainders, supports e composição ASCII. A interpretação semântica de bytes IBM1047
é qualificada pelos oracles de storage separados; este reader confere o discriminante
exato daquele codec, sem alegar que valida todos os textos de todos os codecs.
O golden manual também rejeita texto fabricado que concorde com seus próprios bytes.
Os testes Python e Java pequenos estão no FAST; corpus e mutação de domínio são
obrigações adicionais da qualificação W5.

W7 simultaneous initialization retains the nonoverlapping-fragment contract.
For several equal initial origins on an interval, observations publish complete
fragment covers whose union retains every contribution. All covers describe the
same byte image; their inventory order is not an execution order. The domain
stores co-initial origins per span and does not enumerate initialization orders.


## RF-W4 — query by canonical Place occurrence

StorageSubject.PlaceOccurrence(OperandId) refers to an already indexed AIR Place,
owned by the query Entry's unit. It is resolved through the existing StorageIndex;
there is no invented object and no additional dataflow. Choices preserve all known
physical alternatives and their memory bound. Foreign IDs are unsupported subjects.
The result retains the OperandId, candidates, support, remainder and query point.

## EP-W1 — logical source evidence (schema 1.2.0)

The writer selects 1.2.0 when an observation contains logical source evidence.
An Event with `logicalObjectId: Id(object)` has `storageId: null` and an empty
`contributedRanges` list. It does not assert any physical allocation or range.
Physical events keep their existing shape and mandatory storage ID.

A ValueFact may additionally contain nonempty `logicalAlternatives`:
`[{objectId: Id(object), candidate: string, producers: [Support]}]`, where Support
has the existing `evidence`, `origin` and `premiseRefs` fields. Every alternative
must have source/analysis support, match the queried named object (versions 1.2–1.3), and retain
`modelValueRemainder: true`. Its text participates in `candidates` and its supports
participate in `candidateSupports`. Physical alternatives remain unchanged.
This representation does not invent a logical Cell or physical interpretation.

The independent reader checks these obligations and rejects the added shapes
under earlier versions. Existing physical-only output retains 1.0.0/1.1.0.

The writer uses schema **1.1.0** only when a PLACE_OCCURRENCE subject is present:
`{kind: "PLACE_OCCURRENCE", operandId: Id(operand)}`. Existing subjects retain the
1.0.0 wire unchanged. The independent Python reader accepts both and rejects the
new subject under 1.0.0. This does not change dependencies.json or CFG JSON.

## 1.3.0 — captured logical support without a physical source

A known fragment may carry `logicalCapture`: `objectId`, `before`, and nonempty
`producers` (evidence, origin, premiseRefs). It records the sending logical object
and source support captured BEFORE the fragment producer Assign. It has no source
storage or byte interval. The destination bytes still require successful explicit
codec interpretation; they are one supported possibility. The original Assign
event can remain unknown, and the value must retain model remainder. Source support
participates in candidateSupports exactly, alongside the destination producer.
Subsequent physical copies preserve this capture; later writes to the source do not
re-evaluate it. Only documents using this field negotiate 1.3.0. Versions 1.0–1.2
retain their closed shapes and existing producer requirements. Structural identities,
BEFORE instant, ownership, support completeness and remainder are independently
validated. This changes result transport only, not AIR or kill authority.

## 1.4.0 — explicit logical identities of canonical place occurrences

A result with logical RD contributions or logical value alternatives queried through
`PLACE_OCCURRENCE` negotiates 1.4.0. Every occurrence subject in that document has the
closed shape `{kind, operandId, explicitObjectIds}`. The new array contains unique,
canonically ordered full ObjectIds explicitly selected by the canonical Place:
ObjectPlace contributes its object; Choice contributes the union of its alternatives;
RegionSlice contributes none. Choice remainder, binding aliases, and address/length
expressions do not contribute logical identities. An unsupported occurrence has no
resolved identities. Named and physical-range subject shapes are unchanged.

Preparation detaches this identity projection from the validated AIR, using the same
shared projection as RD, Regional Values, and read/copy transfers. The writer does
not inspect AIR or reconstruct source declarations. The independent reader validates
object references, uniqueness, and membership: each `logicalAlternatives[].objectId`
and each logical RD definition must belong to the subject's `explicitObjectIds` (or
be the queried NamedObject). Physical ranges still cannot carry logical alternatives.
As with physical interpretations, correspondence with the original canonical AIR is
asserted by the validated-publication authority; a detached reader cannot reconstruct
that source graph. Executable source-to-result contrasts check that correspondence.

All previous requirements for current-state support, open remainder, BEFORE capture,
origins, and absence of fabricated physical source intervals remain. Logical capture
records the actual selected source object, including inside nested Choice; it does
not attribute one alternative's support to all others. Mixed physical/logical reads
preserve both forms and the existing per-alternative physical capture ranges.

Versions 1.0–1.3 retain their closed shapes and logical-subject restrictions. A reader
must reject a 1.4 occurrence shape under an older version and reject logical evidence
outside its explicit subject identities. Existing physical-only occurrences retain
1.1, and named logical/capture results retain 1.2/1.3 when no 1.4 feature is used.
