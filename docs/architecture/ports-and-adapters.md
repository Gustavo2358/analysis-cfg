# Porta estável, transportes substituíveis

## Porta de entrada

Contrato Java implementado no package
`io.github.gustavo2358.analysis.cfg.application`:

```text
BuildCfg.build(air-java Publication, BuildOptions) → CfgBuildResult
BuildCfg.buildChecked(AirValidator.CheckedPublication, BuildOptions) → CfgBuildResult
```

`Publication` é exatamente `io.github.gustavo2358.air.model.Publication`, do
artefato `io.github.gustavo2358:air-java`. A porta não define DTO/modelo semântico
concorrente e não aceita `Path`, `InputStream`, bytes, JSON, COBOL Semantic Product,
AST ou source code. `BuildOptions(ValidationOptions, ProjectionPolicy)` separa limites
de validação da admissão de inventário. `BuildOptions.defaults()` e o construtor
anterior de um argumento escolhem `KNOWN_SUBSET`; `STRICT` é opt-in. Não há flags
de dialeto, inferência, fallthrough ou suporte presumido. A Publication
inteira cruza a porta, sem pressupor Entry única. Seleção de entries só será
adicionada quando um caso de uso concreto exigir. O resultado é tipado, imutável e
não contém callback para completar fatos.

`build` continua sendo o único método abstrato. O método default `buildChecked`
preserva implementações existentes; o coordenador reutiliza a execução do
Validator apenas para a mesma publicação e opções. Mudanças no budget exigem
revalidação. O wrapper é criado pelo próprio `AirValidator` e retém também
resultados inválidos/incompletos; capability negotiation e admissão do consumidor
continuam obrigatórias. [Qualificação e limites](../work/air-codec-latency.md).

O caminho paginado do coordenador recebe `CfgProgram.AdmittedSnapshot` e o
`SnapshotValidator.CheckedSnapshot` exato retido pelo adapter. A identidade do
witness (não somente PublicationId, bytes ou resultado de validação) deve ser a
mesma, com ambos os owners abertos e opções idênticas. Um programa residente ou
certificado de outra entrada é recusado antes da projeção, inclusive se os IDs
de publicação coincidirem. O adapter continua responsável por mapear fielmente
os fatos do snapshot; a testemunha não certifica uma implementação arbitrária
da porta. `SnapshotProgram` é o adapter de produção e não reconstrói Publication.

`CfgProgram.NodeStore` is the projection's append/seal port. Entry/Sequence views carry
optional opaque source handles (zero for resident callers), never wire offsets interpreted
by the core. A snapshot backend may retain compact descriptors and role ordinal tapes,
returning `CfgNodeInventory` views over the exact immutable input. Their count and all
ordinal-to-node mappings remain invariant after seal. Repeated projection cannot mutate
an existing inventory. The program owns these descriptors; the graph only borrows them.
Graph admission validates the role indexes against the complete node scan, including
wrong kinds, omissions, duplicate positions and out-of-range ordinals.

`CfgBuildResult` registra `PublicationId`, versão AIR, options, o
`ValidationResult` integral, capabilities requeridas sem intérprete, issues tipados
da projeção e `Optional<CfgGraph>`. `CFG_BUILT` exige produto presente;
`INVALID_IR`, `UNSUPPORTED_CAPABILITY`, `UNSUPPORTED_INPUT`, `RESOURCE_LIMIT`, `VALIDATION_LIMIT` e
`INCOMPLETE_VALIDATION` exigem ausência de grafo. O antigo
`READY_FOR_CFG_PROJECTION` foi removido. O construtor rejeita envelope com produto
em falha ou com metadata/preflight incompatíveis. `CfgBuildCoordinator` delega a
regra mínima ao domínio após preflight.

`UNSUPPORTED_INPUT` identifica terminador diferente de Jump/Branch/Return/Halt, body
indisponível, inventário recusado pela policy ou necessidade de semântica de extensão
ainda ausente. Seus subjects são IDs AIR; não opcodes textuais.

## Política de projeção

| Inventário de Publication e de cada Unit | KNOWN_SUBSET (default) | STRICT (opt-in) |
| --- | --- | --- |
| COMPLETE | admite | admite |
| PARTIAL com AIR válida e fatos suportados | admite fatos conhecidos | INCOMPLETE_INVENTORY |
| UNAVAILABLE | INCOMPLETE_INVENTORY | INCOMPLETE_INVENTORY |

`ProjectionPolicy` pertence ao domínio; BuildOptions transporta a opção ao
coordinator e ao único `CoreCfgProjection.unsupported`. A policy decide somente
admissão de inventário, após preflight/capabilities. Nenhuma decisão lê código de
gap, mensagem, nome de arquivo ou produtor. Nenhum caminho pula o preflight.

KNOWN_SUBSET projeta todo o controle publicado suportado, inclusive órfãs, usando
apenas Entry.initialLabel e terminadores AIR. Não inventa nós/arestas para lacunas,
não infere ausência de fato não publicado nem afirma todo o controle possível.
STRICT exige inventário COMPLETE nos escopos concretos da Publication e de todas
as Units recebidas. Não exige cobertura de toda uma linguagem e não converte uma
obrigação semântica externa em prova. Seleção de escopo menor não existe nesta API.

`CFG_BUILT` afirma que há produto. A parcialidade é observável em
`result.graph().orElseThrow().publication().coverage()` e na coverage de cada Unit.
O mesmo snapshot preserva coverage items, uncertainties, premises e origins;
headers mantêm precision/gaps por dimensão. Não há cópia/resumo que promova PARTIAL
para COMPLETE, nem status redundante CFG_BUILT_PARTIAL. As opções efetivas ficam
em `result.options()`.

Inventário PARTIAL sem Units pode produzir zero nós em KNOWN_SUBSET, mantendo PARTIAL:
isso enumera zero fatos publicados, sem provar que não existem Units/controle.
UNAVAILABLE continua recusado. O grafo não calcula reachability, fechamento global
ou independência causal das lacunas para análises futuras.

AIR inválida/referências quebradas, validation limits/incomplete validation,
capability sem intérprete, extensão ainda sem semântica, body indisponível e qualquer
terminador não suportado continuam bloqueando o build. Não se publica grafo que
omita silenciosamente uma operação AIR conhecida não suportada.

O caller pode ser teste, módulo de integração, CLI ou adapter. Todos entregam o
mesmo objeto semântico à mesma porta.

## Preflight do caso de uso

```text
Publication
    ↓
air-java AirValidator
    ↓
version/capability/options preflight
    ↓
ProjectionPolicy + supported core slice admission
    ↓
CoreCfgProjection
```

A validação não fica exclusivamente no adapter de arquivo: uma instância recebida
em memória também passa por `AirValidator`. `INVALID_IR` não é reparada. Status de
validação incompleta/capability incompatível é tratado de forma explícita antes do
builder. O consumer não reimplementa um validator AIR divergente.

`INCOMPLETE_VALIDATION`, `RESOURCE_LIMIT`, `VALIDATION_LIMIT` e `UNSUPPORTED_CAPABILITY` não são
convertidos em sucesso por conveniência. Uma extensão requerida conhecida pelo
registry não apaga `UNSUPPORTED_CAPABILITY` tipada emitida pelo validator upstream;
o seam não certifica a semântica da extensão. `SEMANTIC_OBLIGATION` preserva uma
obrigação cuja verdade externa não foi provada; o CFG não a promove a fato.

`AirValidator` verifica a estrutura que sua versão suporta. Ele não substitui evals
de conformidade do consumer: os evals CFG provam que `Return`, branches, outcomes e
outros fatos são interpretados corretamente.

## Memória primeiro

`CFG-FIRST` constrói uma `Publication` diretamente com `air-java` e invoca a porta,
sem filesystem, codec ou lowerer. Na integração futura, `cobol-lower` devolve o
mesmo tipo por chamada Java. Não criar `MemoryReader` ou repository para transportar
um objeto pronto e não serializar para JSON só para restabelecer lifetime.

## Arquivo e binding JSON no 2B

Esta seção descreve o transporte legado ainda executável. Seus caps de bytes e
profundidade são [dívidas de capacidade](../work/cp5-follow-ups.md#size-cap-debts),
supersedidos como política para CP5 por [CORE-SIZE-001](decisions/ADR-0014.md).
A preservação dos bytes Java nesta remediação não aprova esses caps para a futura
composição. Permanecem a validação de corretude e a autoridade única do codec AIR.

```text
AIR JSON/file → bounded reader → CheckedPublication → BuildCfg.buildChecked
memory caller → Publication → BuildCfg.build
```

O binding JSON normativo pertence e é versionado pelo `analysis-ir`, de modo
independente da linguagem. O Analysis IR JSON Binding 1.0.0 existe no commit
fixado, targets AIR 2.0.0 e permanece DRAFT. A decisão humana do WORK-CFG-026 autoriza
consumir esse snapshot experimental sem promoção. `cfg-adapters::AirJsonFileReader`
usa um único AirJson.Limits para leitura física limitada e shared AirJson.decodeChecked.
O método legado `read` devolve a publicação da mesma leitura checked.
Não há parser AIR/DTO concorrente. `air-java` modelo continua sem JSON/filesystem.

Falhas AirJsonException mantêm code/path/issues intactos; limite físico próprio
antes do codec é IMPLEMENTATION_LIMIT com maximumDocumentBytes. INPUT_IO distingue
filesystem. Nenhuma falha anterior à Publication vira UNSUPPORTED_INPUT do kernel.
[Contrato e limites CFG JSON](cfg-json-v1.md), [CLI/exit codes](../../README.md).

## Lifetime e retenção

`Publication` é um snapshot imutável no adapter residente. O CFG não faz deep copy
O(N) nem retém esse snapshot; conserva IDs AIR e fatos estruturais compactos.
`CfgBuildResult` já registra `PublicationId`, versão/revisão, opções e preflight.
`CfgGraph` retém `CfgSource` e uma testemunha fraca somente para admissão residente;
EntryNode/SequenceNode não retêm objetos AIR completos. Operands, origins e metadata
permanecem sob a porta `ProgramStore`.
NormalExit sintético registra PublicationId/UnitId/EntryId sem fabricar origem.
HaltExit retém OperationId/HaltKind por ocorrência; activationEntry fica na
transição. SequenceNode conserva a ordem pelos OperationIds, sem payload AIR.
`preciseControlCapabilities()` registra o consumo de memory.regions@1 apenas para
controle; registry/preflight de extensões continuam explícitos.
Nenhum índice muda a AIR. Não há consulta lazy ao produtor nem
dependência de um arquivo continuar aberto.

## Portas de saída

Construir CFG não exige persistência: devolver `CfgBuildResult` basta. O writer
JSON é implementado em cfg-adapters; DOT permanece futuro. Exportadores consomem
esse resultado fora do core. Uma futura porta de
publicação/armazenamento só nasce de necessidade real, não para completar um desenho
hexagonal abstrato.

## Prova de substituição

Depois do binding/adapter, a mesma `Publication` deve chegar por arquivo e por
construção em memória. Com mesmas opções e correlações, os resultados semânticos são
equivalentes: nós, outcomes, gaps, origins, precisão, `TypeRef`, premises e escopos.
Essa prova é posterior a `CFG-FIRST`; não compara texto de console e não altera a
porta nem o algoritmo (INV-CFG-002; EVAL-CFG-008).


EVAL-CFG-031 executa o reader/porta/writer e compara bytes com o golden CFG manual.
Uma Publication construída independentemente em memória mantém as mesmas correlações,
topologia e inventários e gera os mesmos bytes CFG. Seus fatos AIR não transportados
(origins/precision) diferem deliberadamente: essa prova não substitui todo o escopo
futuro de EVAL-CFG-008 nem a qualificação de perfil AIR.


A [taxonomia operacional atual](resource-limit-preflight.md) preserva RESOURCE_LIMIT
por diagnóstico tipado e contagens totais, sem produzir grafo ou resultado semântico.

## Full file pipeline composition

`analysis-pipeline <AIR> <CFG> <dependencies> [--source-evidence <source>]`
is an outer launcher with direct cfg-kernel/cfg-adapters dependencies. It admits
one strict immutable AIR snapshot and the complete digest-bound source evidence
before exporting. CFG uses conservative defaults; dependencies retain their
partial-analysis projection. Export graph ownership ends before analysis begins.
Each destination uses its existing atomic writer. If dependency delivery later
fails, the completed CFG remains, matching the sequential file pipeline. Exit
status remains nonzero; workers cannot certify the program as complete.
Distinct input/destination paths and aliases are required. Compressed transports
are supported through the existing adapters; bundles and incomplete AIR remain
on the separate dependency CLI. No process-global snapshot or execution cache.
