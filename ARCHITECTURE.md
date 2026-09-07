# Arquitetura — mapa curto

**O core estrutural está implementado em memória: instructions ordenadas,
Jump e Branch explícitos, Return para normal exit por Unit/Entry e Halt para término próprio,
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
`AirValidator`, preflight de versão/capabilities, policy de inventário e suporte ao slice;
somente depois
projeta Entry/Jump/Branch/Return/Halt, preservando instructions. Trocar transporte preserva a porta.

O Analysis IR JSON Binding 1.0.0 pertence ao `analysis-ir`, targets AIR 2.0.0 e
permanece DRAFT no commit fixado. WORK-CFG-026 consome esse snapshot experimental por
decisão humana explícita. `AirJsonFileReader` em cfg-adapters lê bytes limitados e
chama exclusivamente `air-json::AirJson.decode`; o artefato de modelo `air-java`
permanece sem transporte. O writer implementa o
[contrato CFG local](docs/architecture/cfg-json-v1.md), separado do binding AIR.

## Dependências físicas decididas

| Unidade | Dependências permitidas |
| --- | --- |
| `air-java` | JDK; modelo/validator AIR, sem produtor, transporte ou CFG |
| `cfg-kernel` (domínio + aplicação/porta) | JDK 21 e `air-java` |
| `cfg-adapters` | kernel, `air-java`, `air-json` e filesystem/encoding CFG do JDK |
| `cfg-launcher` | adapters e kernel, somente composição/execução |

`analysis-cfg` não contém um segundo modelo AIR. A foundation prova
`Publication → BuildCfg + BuildOptions → CfgBuildResult`, reutiliza `AirValidator`
e negocia intérpretes compostos explicitamente por capability/version. O resultado
`CFG_BUILT` contém `CfgGraph`; estados de falha têm `Optional.empty()` e conservam
diagnostics. `UNSUPPORTED_INPUT` recusa primitives/forma fora do slice.
O estado transitório `READY_FOR_CFG_PROJECTION` foi removido, sem significado duplo.
WORK-CFG-024 (0A) torna a admissão explícita: KNOWN_SUBSET default aceita fatos suportados
de inventário PARTIAL; STRICT exige COMPLETE na Publication e em cada Unit.
CFG_BUILT não afirma completude; coverage/evidence permanecem na Publication original.
[Contrato da policy](docs/architecture/ports-and-adapters.md#política-de-projeção).

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

WORK-CFG-006 acrescenta `branch`: BRANCH_TRUE/BRANCH_FALSE conservam as duas
alternativas explícitas e activationEntry, mesmo com destinos iguais. Predicate é
validado pelo preflight e retido pela Sequence, sem avaliar valores nem detectar joins.
`MVP-CFG-01` local está implementado, com CF1 e M1–M5 provados.
WORK-CFG-026 implementa CLI/arquivo GOBACK via codec pinado;
o writer cobre todos os tipos atuais do CFG. `invoke`, `raise`, `dispatch`, demais cenários cíclicos,
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

## Topologia física do 2B

```text
cfg-launcher → cfg-adapters → cfg-kernel → air-java
                    └──────→ air-json ──→ air-java
```

Launcher também depende diretamente do kernel para a composição. Os três módulos
estão no reactor; não há módulo frontend, parser AIR próprio nem dependência kernel→adapters.
Architecture inspeciona fontes/classes/DAG exatos e a chamada compilada AirJson.decode.
Integration executa a cadeia de arquivos e compara o resultado com oracle manual.
A ligação real com 2A/cobol-lower e o E2E cross-repo continuam fora deste checkpoint.
