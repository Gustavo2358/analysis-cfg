# Arquitetura — mapa curto

**CFG-FIRST e fluxo linear estão implementados em memória: instructions ordenadas,
Jump explícito, Return para normal exit por Unit/Entry e Halt para término próprio,
com produto imutável e correlações AIR.**
[ADRs](docs/architecture/decisions/index.md).

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

Contrato físico: `BuildCfg.build(air-java Publication, BuildOptions) →
CfgBuildResult`, implementado pelo `CfgBuildCoordinator`. A porta não recebe `Path`,
`InputStream`, JSON, COBOL ou Semantic Product. O caso de uso executa
`AirValidator`, preflight de versão/capabilities e suporte ao slice; somente depois
projeta Entry/Jump/Return/Halt, preservando instructions. Trocar transporte preserva a porta.

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

`analysis-cfg` não contém um segundo modelo AIR. A foundation prova
`Publication → BuildCfg + BuildOptions → CfgBuildResult`, reutiliza `AirValidator`
e negocia intérpretes compostos explicitamente por capability/version. O resultado
`CFG_BUILT` contém `CfgGraph`; estados de falha têm `Optional.empty()` e conservam
diagnostics. `UNSUPPORTED_INPUT` recusa primitives/forma fora do slice.
O estado transitório `READY_FOR_CFG_PROJECTION` foi removido, sem significado duplo.

## Primeiros marcos

`CFG-FIRST` é a prova executável mínima implementada em WORK-CFG-005:

```text
entry(E) → node(sequence L, terminator Return) → normal exit(UnitId, EntryId)
```

A saída deriva de `Return`, nunca da posição física da Sequence. Uma Sequence
posterior não recebe fallthrough. Cada Sequence origina um nó CFG próprio; não há
leader detection nem coalescing obrigatório. `Return` não possui `entryScope` na
AIR fixada (§04.8): segue a Entry da ativação corrente. Transições RETURN do CFG
carregam `activationEntry`, condição que impede cruzar saídas de Entries distintas.
Não são arestas incondicionais; inventário não é prova de reachability.

WORK-CFG-022 implementa instructions lineares, `jump` e `halt` no único
`CoreCfgProjection` (antes `CfgFirstProjection`). Jump usa somente destination;
JUMP/HALT preservam activationEntry. HaltExit retém a ocorrência AIR, sem funcionar
como NormalExit. Self-loops explícitos são aceitos sem análise de alcance.

`MVP-CFG-01` ainda depende de `branch` e IF/ELSE estrutural. CLI/arquivo são
outro milestone posterior. `invoke`, `raise`, `dispatch`, demais cenários cíclicos,
controle aberto, `control.local@1` e
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
