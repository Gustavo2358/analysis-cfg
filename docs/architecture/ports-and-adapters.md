# Porta estável, transportes substituíveis

## Porta de entrada

Contrato Java implementado no package
`io.github.gustavo2358.analysis.cfg.application`:

```text
BuildCfg.build(air-java Publication, BuildOptions) → CfgBuildResult
```

`Publication` é exatamente `io.github.gustavo2358.air.model.Publication`, do
artefato `io.github.gustavo2358:air-java`. A porta não define DTO/modelo semântico
concorrente e não aceita `Path`, `InputStream`, bytes, JSON, COBOL Semantic Product,
AST ou source code. `BuildOptions` continua contendo apenas
`ValidationOptions`, política operacional já executada pelo preflight; não possui
flags de dialeto, inferência, fallthrough ou suporte presumido. A Publication
inteira cruza a porta, sem pressupor Entry única. Seleção de entries só será
adicionada quando um caso de uso concreto exigir. O resultado é tipado, imutável e
não contém callback para completar fatos.

`CfgBuildResult` registra `PublicationId`, versão AIR, options, o
`ValidationResult` integral, capabilities requeridas sem intérprete, issues tipados
da projeção e `Optional<CfgGraph>`. `CFG_BUILT` exige produto presente;
`INVALID_IR`, `UNSUPPORTED_CAPABILITY`, `UNSUPPORTED_INPUT`, `VALIDATION_LIMIT` e
`INCOMPLETE_VALIDATION` exigem ausência de grafo. O antigo
`READY_FOR_CFG_PROJECTION` foi removido. O construtor rejeita envelope com produto
em falha ou com metadata/preflight incompatíveis. `CfgBuildCoordinator` delega a
regra mínima ao domínio após preflight.

`UNSUPPORTED_INPUT` identifica terminador diferente de Return, instructions, body
indisponível, inventário parcial/indisponível ou necessidade de semântica de extensão
ainda ausente. Seus subjects são IDs AIR; não opcodes textuais. Uma publicação
com inventário completo e zero Units produz um inventário CFG realmente vazio,
sem servir como placeholder para publicação desconhecida ou recusada.

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
Build CFG semantics
```

A validação não fica exclusivamente no adapter de arquivo: uma instância recebida
em memória também passa por `AirValidator`. `INVALID_IR` não é reparada. Status de
validação incompleta/capability incompatível é tratado de forma explícita antes do
builder. O consumer não reimplementa um validator AIR divergente.

`INCOMPLETE_VALIDATION`, `VALIDATION_LIMIT` e `UNSUPPORTED_CAPABILITY` não são
convertidos em sucesso por conveniência. Uma extensão requerida conhecida pelo
registry ainda preserva o `INCOMPLETE_VALIDATION` que o validator upstream emitiu;
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

## Arquivo e binding JSON depois

```text
AIR JSON/file → infrastructure reader → air-java Publication → BuildCfg
memory caller ────────────────────────────────────────────────┘
```

O binding JSON normativo pertence e é versionado pelo `analysis-ir`, de modo
independente da linguagem. O Analysis IR JSON Binding 1.0.0 existe no commit
fixado, targets AIR 2.0.0 e permanece DRAFT; este checkpoint não implementa seu
reader. Uma autorização posterior poderá criar o adapter em `cfg-adapters` sem
alterar a porta; `air-java` continua sem Jackson/Gson/JSON. Não derivar o binding
automaticamente da organização de records Java e não usar a notação `.air` ou
`cobol-semantic-product.json` como schema.

Erro de arquivo/encoding/JSON é `INPUT_ERROR` do adapter; AIR com referência
pendente é `INVALID_IR`; capability legítima fora do slice é
`UNSUPPORTED_CAPABILITY` ou fallback sustentado. Essas classes não são trocadas para
disfarçar falha. A ausência do binding não bloqueia o core em memória.

## Lifetime e retenção

`Publication` é snapshot imutável compartilhado. O CFG não faz deep copy O(N) por
padrão; pode reter referências/IDs AIR e manter índices derivados próprios.
`CfgBuildResult` já registra `PublicationId`, versão/revisão, opções e preflight.
`CfgGraph` retém exatamente a Publication original; EntryNode/SequenceNode
retêm os objetos AIR originais, incluindo IDs, operands, origins e metadata.
NormalExit sintético registra PublicationId/UnitId/EntryId sem fabricar origem.
Nenhum índice muda a AIR. Não há consulta lazy ao produtor nem
dependência de um arquivo continuar aberto.

## Portas de saída

Construir CFG não exige persistência: devolver `CfgBuildResult` basta. Exportadores
JSON/DOT são adapters que consomem esse resultado fora do core. Uma futura porta de
publicação/armazenamento só nasce de necessidade real, não para completar um desenho
hexagonal abstrato.

## Prova de substituição posterior

Depois do binding/adapter, a mesma `Publication` deve chegar por arquivo e por
construção em memória. Com mesmas opções e correlações, os resultados semânticos são
equivalentes: nós, outcomes, gaps, origins, precisão, `TypeRef`, premises e escopos.
Essa prova é posterior a `CFG-FIRST`; não compara texto de console e não altera a
porta nem o algoritmo (INV-CFG-002; EVAL-CFG-008).
