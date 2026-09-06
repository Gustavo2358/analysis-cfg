# Arquitetura — mapa curto

**A fundação Java/Maven e a boundary física pertencem a este checkpoint; o
algoritmo CFG continua não implementado.** [ADRs](docs/architecture/decisions/index.md).

```text
proleap-poc
    ↓
COBOL Semantic Product
    ↓ cobol-semantic-product.json
cobol-lower
    ↓
air-java Publication
    ↓
analysis-cfg
    ↓
CFG
```

`proleap-poc` termina no produto semântico COBOL-specific. `cobol-lower`, ainda
planejado upstream, traduz somente esse produto para a Analysis IR. O `analysis-cfg`
é consumer puro e nunca conhece construções COBOL, AST, symbols ou resolvers.

## Porta única

```text
file / AIR JSON adapter ─┐
                         ├→ air-java Publication → BuildCfg → CfgBuildResult
memory caller ───────────┘
```

Contrato conceitual: `BuildCfg(air-java Publication, BuildOptions) →
CfgBuildResult`. A porta não recebe `Path`, `InputStream`, JSON, COBOL ou Semantic
Product. O caso de uso executa `AirValidator`, preflight de versão/capabilities e só
então a semântica CFG. Trocar transporte não muda a porta nem o algoritmo.

O Analysis IR JSON Binding 1.0.0 pertence ao `analysis-ir`, targets AIR 2.0.0 e
permanece DRAFT no commit fixado. Ele não é implementado neste checkpoint. Um
futuro reader fica em infraestrutura; `air-java` permanece sem transporte e a
ausência de codec não bloqueia testes com `Publication` construída em memória.

## Dependências físicas decididas

| Unidade | Dependências permitidas |
| --- | --- |
| `air-java` | JDK; modelo/validator AIR, sem produtor, transporte ou CFG |
| `cfg-kernel` (domínio + aplicação/porta) | JDK 21 e `air-java` |
| `cfg-adapters` | kernel, `air-java` e bibliotecas de infraestrutura |
| `cfg-launcher` | adapters e kernel, somente composição/execução |

`analysis-cfg` não contém um segundo modelo AIR. O bootstrap Java 21/Maven prova
somente `Publication → AirValidator → boundary`; a porta `BuildCfg` e o algoritmo
permanecem para checkpoints posteriores.

## Primeiros marcos

`CFG-FIRST` é a prova executável mínima:

```text
entry(E) → node(sequence L, terminator Return) → normal exit(UnitId, EntryId)
```

A saída deriva de `Return`, nunca da posição física da Sequence. Uma Sequence
posterior não recebe fallthrough. Cada Sequence origina um nó CFG próprio; não há
leader detection nem coalescing obrigatório.

`MVP-CFG-01` permanece posterior e acrescenta operações lineares, `jump`, `halt`,
`branch` e IF/ELSE estrutural. CLI/arquivo são outro milestone posterior. `invoke`,
`raise`, `dispatch`, ciclos, controle aberto, `control.local@1` e
`control.indirect@1` entram em slices próprios. Nenhum subset recebe claim
`AIR-STRUCTURE@2/PRECISE_FOR_PROFILE` antes de todos os seus oráculos.

## Produto e lifetime

O CFG é derivado e imutável. Registra `PublicationId`, revisão/versão AIR, `UnitId`,
escopo de `EntryId`, IDs/origens e opções relevantes. Pode reter referências
imutáveis da `Publication` e criar índices próprios; não faz deep copy O(N) por
padrão, não muta AIR e não consulta o produtor por callback/lazy loading.

Detalhes: [fronteiras](docs/architecture/boundaries.md),
[portas e adapters](docs/architecture/ports-and-adapters.md),
[extensibilidade](docs/architecture/extensibility.md) e
[pipeline](docs/architecture/pipeline.md).
