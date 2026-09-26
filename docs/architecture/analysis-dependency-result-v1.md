# Dependency site facts — transporte 1.1.0 e parcialidade 1.2.0

W1 operational policy: logical-only by default; physical propagation requires explicit experimental opt-in. The wire shape and historical profile label below are unchanged. `metrics.logicalOnlyMode` / `metrics.experimentalPhysicalMode` identify the dependency execution policy; regional observation `statistics.values.logicalOnlyMode` identifies its policy. Zero physical work is observable in physicalGroupsApplied/physicalWritesApplied counters. Existing remainder/analysisReasons/logicalAlternatives express candidate support without physical completeness. See [logical-only](../product/logical-text-w1.md).


## EP-W4 — contrato atual de parcialidade

Resultados com preparação semântica incompleta usam `version=1.2.0`,
`analysisStatus=PARTIAL` e `analysisReasons` (lista ordenada sem duplicatas).
Todos os sites dessa versão têm `analysisStatus=COMPLETE|PARTIAL` e
`analysisReasons`; COMPLETE exige lista vazia, PARTIAL exige motivo não vazio.
Os fatos completos de outros sites conservam seus candidatos e suportes.
Resultados sem essa limitação mantêm a representação 1.1.0 vigente.

Um computed sem observação de valores disponível usa
`targetStatus=ANALYSIS_INCOMPLETE`, nenhum candidato inventado e remainder aberto.
Uma recusa de perfil/preparação deixa de causar exit 5 com perda global do produto.
Erros estruturais reais da AIR e falhas operacionais continuam sendo falhas.

Quando o CFG não pode ser projetado por limite semântico, o mesmo produto pode
conservar ocorrências AIR de CALL literal com `reachability=UNKNOWN`,
`openControlRemainder=true` e `modelScope=STRUCTURAL_AIR_OCCURRENCES`. Esses fatos
não afirmam execução provada. Suas arestas são possibilidades com `openSite=true`.
Um site provadamente inalcançável continua sem candidatos/arestas. Na ausência de
um grafo, expressões computadas não são avaliadas e VALUE não é resemeado.

O mapper e o leitor independente verificam status/motivos, suporte, projeção de
arestas, escopo e remainder. Campos/valores novos sob versão antiga são rejeitados.
As seções históricas abaixo descrevem a fundação; esta seção e as evoluções
posteriores governam o comportamento atual. Ver também
[decisão EP-W4](../domain/partial-dependency-analysis.md).

Contrato interno autorizado por [WORK-CFG-033](../work/history/WORK-CFG-033/work-item.json).
`INTERNAL-CONTRACT-DEV-001`: a primeira versão do produto é explícita; não há mecanismo
de compatibilidade especulativo. O resultado W5 `analysis-dataflow-result` permanece
byte-exact, com seu mapper, snapshots e consumidores anteriores preservados.

## Fronteiras e execução

`analysis-dependencies` depende de AIR, CFG, structure/query/planning e values. Não
conhece JSON, filesystem, frontend, lower, corpus ou runtime COBOL. A composição
`DependencyAnalysis` faz preflight/CFG, abre uma `AnalysisSession`, declara os
interesses W4 e executa a preparação. `CallDependencyConsumer` recebe somente
`SiteView`, `PreparedFacts` e `FactSink`; não recebe sessão, índice, solver ou replay.
Os descritores reconstruídos no consumer são chaves iguais às declaradas pela
query factory: `PreparedFacts.lookup` não cria demanda nem executa análise tardia.

`CallDependencyPlan` visita uma vez o bucket indexado de `Operations.Invoke`,
selecionando `category=program`, `namespace=cobol.program`. Registra grupos literal,
computed `Read(ObjectPlace)`, `Read(RegionSlice)` constante e formas não suportadas.
Cada query computed usa `BEFORE(EntryId, Invoke.OperationId)`. Para ObjectPlace,
`subject` é o ObjectId nominal. Para faixa física, `subject` é null e `valuePoint`
permanece BEFORE: a operação AIR referenciada conserva region/offset/length/codec.
Não se fabrica declaração nominal para a faixa. A combinação null/BEFORE é coberta
pelo perfil regional; null/null continua exigindo forma não consultável ou unreachable.
O literal
declara apenas reachability. Um teste executa esse plano sem sequer instalar um
`PossibleValuesProvider`. Dois consumidores e requests duplicados reutilizam a
mesma `AnalysisKey` e os batches W4, com uma execução de valores por chave.

Reachability usa BFS uma vez por Entry sobre o `ContextView` já construído:
O(V+E), sem outro CFG e sem solver de valores. As sequências órfãs continuam no
índice; estar indexado não torna um site executável. O resultado é relativo ao
grafo conhecido e ao Entry selecionado. `caller` é o `UnitId` AIR completo;
`PROGRAM-ID` de exibição não é inferido de nomes de arquivo ou de IDs opacos.

## Controle e effects

`Invoke` permanece terminador. A slice CFG admite exatamente um `Normal(label)`
conhecido, com `NoControl` ou `WithinControl(AllControl)` como remainder. A aresta
`INVOKE_NORMAL` termina naquela label local e conserva `activationEntry`.
Não há fallthrough, aresta de callee, expansão de controle aberto ou reordenação
semântica por posição física. O `CfgGraph` retém a publicação e operações AIR
originais. Halt, Diverge, handlers e outros conjuntos finitos são recusados na
admissão dessa slice, inclusive em órfãos.

`scalar-text-direct@1` conserva a recusa de Invoke. O novo
`scalar-text-effects@1` admite `reads/writes` NoMemory, AllMemory,
ObjectsMemory, StorageMemory ou uma união finita desses escopos, sem
`mustOverwrite`, `perOutcome` ou results. VisibleMemory e formas não suportadas
continuam com `UNSUPPORTED_EFFECT_PROFILE`. `ForeignEffectTransfer` interpreta
somente `EffectBound` e o índice imutável de Cells preparado uma vez. NoMemory
preserva o estado. Um may-write abre o remainder apenas das Cells selecionadas
pelo escopo, preservando cada candidato e seus produtores. Não faz strong kill
nem promove automaticamente ObjectsMemory a AllMemory. O mesmo transfer é usado no solver e no replay, de modo que um
loop pode abrir o valor BEFORE no fixpoint por efeito da iteração anterior.

O domínio, lattice, `ValueUniverse`, `Candidates`, `PossibleValuesState`, solver
W2 e `DefaultValuePlan` não mudam. A abertura conserva monotonicidade e finitude
do domínio preparado. Não há limite semântico por número de sites, objetos,
candidatos, produtores ou tamanho do documento. O custo do efeito é uma visita
por Cell mais os custos do estado persistente; interpretação é linear no tamanho
total dos textos. Os probes N/2N são observações model-level, não qualificação ampla.

## Produto primário

O JSON tem `schema=analysis-dependency-result`, `version=1.0.0`,
`airVersion=2.0.0`, `valuesProfile=scalar-text-effects@1`,
`interpretationProfile=cobol-zos-dynamic-call-minimal@1` e
`modelScope=KNOWN_GRAPH_ENTRY`. `publicationInventory` preserva a cobertura de
entrada; o fixture real continua `PARTIAL` mesmo com candidato conhecido.

Cada elemento de `sites` publica:

| Campos | Contrato |
| --- | --- |
| `caller`, `entry`, `sequence`, `operation`, `offset` | Identidades completas e offset real do terminador; owners/publicação não se perdem |
| `siteOrigin`, `targetOrigin` | Origem da operação e do target, distintas de produtores de valores |
| `targetKind` | `LITERAL` ou `COMPUTED` |
| `subject`, `valuePoint` | ObjectId e BEFORE para computed Read; ambos null para literal ou expressão não modelada |
| `reachability` | `REACHABLE` ou `UNREACHABLE_IN_MODEL` |
| `targetStatus` | `RESOLVED_CANDIDATES`, `OPEN_TARGET`, `UNREACHABLE_IN_MODEL`, `UNSUPPORTED_TARGET_EXPRESSION`, `UNSUPPORTED_INVOCATION_SHAPE` |
| `rawCandidates` | Texto exato e lista de suportes de cada candidato |
| `candidates` | `referenceName`, `rawValue` e os mesmos suportes associados ao valor |
| `evidence`, `provenance`, `premises`, `uncertaintyRefs` | Referências rastreáveis, sem confidence score |

Cada suporte tem `kind` (`VALUE_PRODUCER` ou `CALL_LITERAL`), `producer`, `origin`
e `premises`. Um computed recebe os `candidateSupports` de `ValueFact`: não combina
o conjunto global de produtores de valores diferentes. Para literal, o suporte é
o próprio Invoke/target; não é inventado Object ou Assign. Os catálogos `origins`
e `artifacts` permitem seguir o DAG de origens até o MOVE original. O catálogo
`sourceUncertaintyRefs` conserva os IDs das uncertainties da publicação original.

Remainders são separados:

| Campo | Significado |
| --- | --- |
| `modelValueRemainder` | Remainder do valor no modelo; false para literal alcançável; null para site inalcançável ou forma não modelada |
| `sourceValueRemainder` | Limites de cobertura/precisão da AIR relevantes à observação |
| `interpretationUnknownRemainder` | Política de nome incompleta, desconhecida ou transformação fora da slice |
| `effectiveUnknownRemainder` | OR dos três anteriores, tratando model null como ausência de conclusão |
| `openControlRemainder` | Existência de remainder de controle no Invoke; não materializa arestas |

Um órfão emite site com status próprio, listas de candidatos vazias e model null.
O dynamic sem MOVE é alcançável, sem candidato inventado e model aberto. Um valor
não canônico permanece raw, sem candidato exato, com interpretação aberta. Um site
com candidatos pode continuar source/interpretation-open.

## Projeção secundária e bytes

`edges` deriva exclusivamente de candidatos de sites alcançáveis: `caller`,
`entry`, `site`, `candidate`, `openSite`. Não é um call graph resolvido nem prova
de linkage. Há no máximo uma linha por associação site/candidato raw; não há
dependência de corpus, leitura de callee ou nomes hardcoded.

`DependencyJson` é um mapper explícito separado no adapter. IDs mantêm domínio,
publicação e owner; enums possuem tokens literais, sem `Enum.name` ou reflexão.
Chaves dos objetos são ordenadas; sites seguem Entry/Operation, candidatos seguem
reference/raw, suportes e referências têm ordenação nominal completa. Unicode é
UTF-8; LF final único; nenhuma normalização Unicode ou remoção de padding raw.
`scripts/project/dependency_wire.py` é um parser/oráculo Python independente que
recusa campos desconhecidos/ausentes, chaves duplicadas, enums/versões/IDs inválidos,
null incorreto, OR inconsistente, suportes não associados, edges inventados e bytes
não canônicos. O consumer Java não usa esse parser para produzir seu resultado.

`DependencyFileWriter` codifica em temporário no diretório de destino e faz
substituição atômica somente após fechar a saída completa. Falha de encoding,
escrita ou finalização remove o temporário e preserva o destino anterior. Não há
fallback silencioso para substituição não atômica.

## CLI e reprodução

Após instalar a AIR exata e construir o reactor, execute:

```sh
mvn -B -ntp install -DskipTests
mvn -B -ntp -pl analysis-launcher org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies
scripts/analysis-dependencies input.air.json output.dependencies.json
python3 -B scripts/project/dependency_wire.py output.dependencies.json
```

Exits: 0 sucesso, 2 uso/path, 3 input/codec/IR inválida ou incompleta, 4 CFG fora
da slice, 5 perfil de análise não suportado, 6 falha de saída, 7 RESOURCE_LIMIT
operacional, 8 consumer incompleto. Erros JVM não são convertidos em incerteza
semântica. Nenhuma falha publica um dependency parcial.

O gate focal é `python3 -B scripts/project/check_w1d.py`. As duas execuções reais
usam `prepare_w1d_producers.py --work <novo-diretório>` e
`e2e_w1d.py --work <novo-diretório> --producers <receipt/producers.json>`.
As autoridades W5 antigas continuam no script próprio de CP5, sem repinar seus
fixtures históricos para obter PASS. Receipts registram comandos, exits, bytes,
hashes, árvores e estado dos checkouts ocupados. W2 permanece NOT_STARTED /
NOT_AUTHORIZED.

A versão do CFG correlacionado segue [CFG JSON 2.0.0](cfg-json-v2.md) quando há Invoke. Isso não muda a versão nem os bytes deste produto de dependência.

WORK-CFG-038 preserva este wire. O CFG correlacionado usa [v3](cfg-json-v3.md) quando há Opaque; a incerteza localizada chega pelos campos de remainder existentes.


## W3 source dependencies — current wire 2.4.0

The additive closed source inventory and compatibility policy are defined in
[source dependencies W3](../product/source-dependencies-w3.md). CALL/FILE fields
retain their 2.3 semantics; source dependencies do not invoke their solvers.

W8 possible TEXT values treat foreign read scopes as independent of stored-value transfer: even a visible-memory read bound leaves cells unchanged when the write bound is `NoMemory`. Finite object/storage write scopes affect only matching cells. Per-outcome effects and `mustOverwrite` outside the current profile still require explicit admission rather than silent approximation.

## R9 qualified source evidence — explicit wire 2.6.0

The optional source-evidence input adds a separate `sourceQualifiedDependencies`
section under result version 2.6.0. Without this input the result remains 2.5.0.
See the [non-executable contract](qualified-source-dependencies-v1.md) for typed
source identities, correlated alternatives, guards/proofs and version rejection.
This section neither uses nor changes executable `DependencySiteFact` reachability.

## Unified program projection (pre-release additive field)

Current writers add `dependencies.programs`, the canonical program occurrence
inventory. Existing `sites`, `edges`, files, DB2 and source certificate fields keep
their contracts. Historical 2.5/2.6 documents may omit this additive field; this is
an in-place pre-release extension, not a new semantic version for old behavior.

Each row carries a full optional source occurrence identity, caller, technology,
name profile, target kind (LITERAL, COMPUTED or UNAVAILABLE), qualification refs,
existing executable operation IDs and site evidence. Candidates retain raw and
interpreted names, real value/literal producer supports and source qualifications.
One occurrence can have EXECUTABLE_FLOW and SOURCE_QUALIFIED authorities; an
unproved executable reachability is labeled EXECUTABLE_OCCURRENCE instead.

`valueRemainder`, `interpretationRemainder` and `analysisReasons` stay explicit.
Unreachable executable queries cannot manufacture values for source-only execution.
The legacy `sourceQualifiedDependencies` field remains the unmodified R9 certificate
and its literal-only certificate view; computed results belong to the shared
canonical inventory. No separate source value solver populates either view.

See [unified target resolution](unified-target-resolution.md) and
[explicit input](dependency-input-v1.md) for correlation and provider admission.


## Candidatos condicionais no inventário unificado

`dependencies.programs[].candidates[].conditionalSupports` é opcional. Contém
`provider=nominal-source-text@1`, `analysisBoundary=NON_EXECUTABLE_SOURCE`,
`evidence` (tipo, referência e proveniência), `assumptions` e `uncertainties`.
As premissas `NOMINAL_DECLARATIONS_PRESERVE_MEANING`,
`NO_UNMODELED_STORAGE_INTERFERENCE` e `DECLARATIVE_INITIAL_VALUES_APPLY` descrevem
hipóteses, não garantias. Cada candidato mantém `referenceName` e `rawValue`;
`valueRemainder=true` indica que o conjunto permanece aberto. A autoridade
`CONDITIONAL_SOURCE_VALUES` e o motivo `CONDITIONAL_NOMINAL_VALUE_EVIDENCE`
identificam essa contribuição. O estado agregado permanece PARTIAL.

Uma consulta executável fechada substitui essa aproximação; dependências
incompatíveis podem desaparecer quando chegam informações que resolvem a dúvida.
Uma cópia nominal explícita respeita o valor anterior da origem e sobrescreve o
receptor no modelo nominal. Condições usam os fatos publicados, sem nova análise
de controle. Veja [requisito e limites](conditional-dependency-candidates.md).

Motivos `CONDITIONAL_*` tornam a análise parcial, mas pertencem à fonte.
Eles preservam o `modelScope` da análise AIR, inclusive quando não há sites AIR.
O escopo estrutural continua sendo determinado pelas ocorrências e limitações
da análise executável; hipóteses de valores não alteram esse escopo.
