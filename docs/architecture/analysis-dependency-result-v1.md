# CP6 W1D — dependency site facts e transporte 1.0.0

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
computed `Read(ObjectPlace)` e formas não suportadas. Cada query computed usa o
`ObjectId` nominal do target e `BEFORE(EntryId, Invoke.OperationId)`. O literal
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
`scalar-text-effects@1` admite `reads/writes` NoMemory ou AllMemory, sem
`mustOverwrite`, `perOutcome` ou results. Escopos menores não recebem tratamento
implícito: retornam `UNSUPPORTED_EFFECT_PROFILE`. `ForeignEffectTransfer` interpreta
somente `EffectBound` e o índice imutável de Cells preparado uma vez. NoMemory
preserva o estado. AllMemory may-write preserva cada candidato e seus produtores,
abrindo o remainder das Cells modeladas. Não faz strong kill nem varredura de
objetos por Cell. O mesmo transfer é usado no solver e no replay, de modo que um
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
