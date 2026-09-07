# Analysis CFG — consumer AIR

**Entrega atual:** CFG estrutural em memória: Entry, instructions, Jump,
Branch TRUE/FALSE, Return/NormalExit e Halt/HaltExit,
com Java 21/Maven, `air-java` e seam explícito de capabilities.
O checkpoint 2B acrescenta AIR JSON file → shared AirJson → Publication → BuildCfg
→ CFG JSON file, em `cfg-adapters`/`cfg-launcher`, sem modificar o kernel.
**Data:** 07/09/2026. **Repositório:**
`Gustavo2358/analysis-cfg`.

Este projeto constrói CFGs a partir da **Analysis IR 2.0.0**, recebendo exatamente
a `Publication` do `air-java`, sem conhecer COBOL, Semantic Product, parser,
filesystem, CLI ou cloud no núcleo. `CFG-FIRST` prova Entry → Sequence(`Return`) →
normal exit em memória. WORK-CFG-022 acrescenta instructions, Jump e Halt;
WORK-CFG-006 acrescenta Branch e conclui **MVP-CFG-01 local**, com M2–M5 provados.
Nenhum perfil AIR normativo é reivindicado.

A projeção usa `KNOWN_SUBSET` por default: inventário `PARTIAL` válido pode gerar
CFG dos fatos conhecidos, preservando coverage, gaps e evidence na Publication original.
Para exigir `COMPLETE` na Publication e em todas as Units, selecione `STRICT`:

```java
BuildCfg builder = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
CfgBuildResult known = builder.build(publication, BuildOptions.defaults());
BuildOptions strict = new BuildOptions(ValidationOptions.defaults(), ProjectionPolicy.STRICT);
CfgBuildResult gated = builder.build(publication, strict);
```

`ProjectionPolicy` está no package `domain`; BuildCfg, BuildOptions, CfgBuildCoordinator
e CfgBuildResult estão em `application`. `CFG_BUILT` não afirma completude global.
AIR inválida, semântica não suportada e validação incompleta continuam bloqueando o build.
[Semântica e limites da policy](docs/architecture/ports-and-adapters.md#política-de-projeção).

## Começar

Leia [START_HERE.md](START_HERE.md). Agentes começam por [AGENTS.md](AGENTS.md),
depois pelo [índice de trabalho](docs/work/index.md). Não carregue o harness inteiro.

```bash
bash scripts/harness/check-fast.sh
bash scripts/harness/check-architecture.sh
bash scripts/harness/check-semantic.sh
bash scripts/harness/check-integration.sh
```

Requisitos: Bash, Python 3.9+, Maven e JDK 21 ou mais novo. O bytecode é sempre
compilado com `--release 21`, sem preview; o CI executa com JDK 21. Python valida
o harness e inspeciona a execução dos testes Java; o CFG é implementado em Java.

O SNAPSHOT `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT` precisa ser instalado
antes do build a partir do source SHA fixado. A sequência reproduzível local e de
CI está em [Java, Maven e integração](docs/engineering/toolchain-and-modules.md).
Nenhum JAR ou modelo AIR é copiado para este repositório.

## O que já está aqui

| Área | Entrada |
| --- | --- |
| Objetivo e MVP | [missão e slices](docs/product/mission-and-slices.md) |
| Arquitetura e adapters | [mapa curto](ARCHITECTURE.md) |
| Regras semânticas | [invariantes](docs/architecture/invariants.md) |
| Fontes e estado real do frontend | [fontes](docs/sources/index.md) |
| Planejamento executável por agentes | [backlog](docs/work/backlog.md) |
| TDD e oráculos | [evals](docs/evals/index.md) |
| Verificação do próprio harness | [gates](docs/engineering/gates.md) |

O backlog é plano, não autorização. `WORK-CFG-001` concluiu as decisões pré-Java,
`WORK-CFG-002` materializou `Publication → AirValidator → CfgPreflight`, e
`WORK-CFG-003` fechou `BuildCfg(Publication, BuildOptions) → CfgBuildResult` mais o
registry por capability/version. WORK-CFG-005 implementa o primeiro produto:
`CfgGraph` imutável, nós e IDs próprios, correlação AIR, transições ENTRY/RETURN e
saídas por Unit/Entry. `CfgBuildResult.CFG_BUILT` contém o CFG; falhas não têm grafo.
WORK-CFG-022 amplia o único projector para `CoreCfgProjection`: instructions
originais ordenadas, JUMP contextual por LabelId e HALT para HaltExit por ocorrência.
`entries()`, `normalExits()` e `haltExits()` retornam inventários imutáveis em O(1).
Branch usa exclusivamente trueDestination/falseDestination, com BRANCH_TRUE e
BRANCH_FALSE distintos mesmo quando os destinos são iguais. O predicate original
é preservado; literal bool e unknown(known(bool)) mantêm ambos os braços.
unknown_type é INVALID_IR no preflight, sem grafo parcial. Não há avaliação de
valores nem detecção de join. Dispatch, invoke/raise, controle aberto/local/indireto e dataflow permanecem posteriores. Transporte/CLI
GOBACK estão em WORK-CFG-026; não promovem integralmente os backlogs históricos
004/008 nem iniciam o E2E externo com cobol-lower.

## O que os gates significam hoje

`docs`, `harness` e `fast` verificam arquivos, referências, IDs, dependências de
backlog, work items e os próprios validadores documentais. `architecture` executa
Maven/testes e inspeciona dependências e bytecode do kernel. `semantic` executa
explicitamente os 17 testes de EVAL-CFG-025, 22 de EVAL-CFG-028, 25 de EVAL-CFG-029
e 20 de EVAL-CFG-030; rejeita
suítes/métodos ausentes, extras, duplicados ou pulados.
`integration` executa 31 métodos nominais em três suítes: arquivos reais, codec,
porta, golden, equivalência de controle/coverage em memória e processo CLI.
`performance` e, por consequência, `full` permanecem
**UNAVAILABLE / exit 3**; full executa fast, architecture e semantic antes de parar
em performance. CFG-FIRST não implica conformidade com um perfil AIR completo.

A especificação upstream é referenciada por commit e hashes de blobs. O pacote
contém um mapa de leitura e síntese, **não uma cópia integral da especificação**.
O utilitário opcional [cache_ir.py](scripts/harness/cache_ir.py) pode importar uma
cópia local verificada ou obter os arquivos públicos fixados; nunca troca por `main`.

`analysis-ir` contém o Analysis IR JSON Binding 1.0.0, ainda **DRAFT** no commit
fixado. 2B usa esse snapshot experimental por decisão humana explícita,
exclusivamente via shared `air-json`; sem codec AIR local, promoção normativa ou
claim de interoperabilidade universal. O [CFG JSON v1](docs/architecture/cfg-json-v1.md)
é contrato próprio do produto analysis-cfg.

## Executar AIR JSON → CFG JSON

Após instalar o upstream pinado no repositório Maven isolado conforme a
[preparação](docs/engineering/toolchain-and-modules.md), use o mesmo MAVEN_OPTS:

```bash
mvn -B -ntp package dependency:copy-dependencies -DincludeScope=runtime
bash scripts/analysis-cfg \
  cfg-adapters/src/test/resources/air/goback.canonical.json \
  /tmp/goback.cfg.json
```

A preparação copia as dependências runtime para target/dependency, sem fat JAR.
O script pode ser chamado por path absoluto de qualquer diretório; argumentos de
arquivo relativos são relativos ao caller. Java deve estar no PATH. Equivalente:

```bash
java -cp 'cfg-launcher/target/classes:cfg-launcher/target/dependency/*' \
  io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg input.air.json output.cfg.json
```

Dois argumentos posicionais, sem flags de policy. `run` é testável sem sair da JVM;
`main` propaga seu exit code. Sucesso não escreve stdout/stderr. Erros esperados têm
stderr conciso, com code/path/issues do AirJson ou status/issues/capabilities do kernel,
sem stack trace normal. Bugs inesperados propagam.

| Exit | Significado |
| --- | --- |
| 0 | CFG_BUILT e arquivo escrito |
| 2 | uso/paths posicionais inválidos |
| 3 | input físico ou falha AirJson; IMPLEMENTATION_LIMIT físico é explícito |
| 4 | build não CFG_BUILT, sem publicação |
| 5 | limite/forma/encoding de serialização CFG |
| 6 | filesystem de saída |

A [fixture AIR](cfg-adapters/src/test/resources/air/PROVENANCE.md) vem do golden
estático upstream b78f4068, independente de 2A. O
[golden CFG manual](cfg-adapters/src/test/resources/cfg/goback.manual.json) tem
Entry → ENTRY → Sequence(Return) → RETURN → NormalExit, correlations e activationEntry,
com source inventories PARTIAL e policy KNOWN_SUBSET. UTF-8 sem BOM/newline final,
ordem estável e nenhum metadado de máquina. Limites default: input 16 MiB/depth 128,
output 64 MiB. Bytes completos precedem temp/move; fallback sem ATOMIC_MOVE não
reivindica atomicidade. Input/build/serialização falhos preservam destino existente.

Ainda não implementados: E2E cross-repo com cobol-lower, orquestrador, dataflow,
possible values, fact projection e CFG JSON reader. O próximo E2E não foi iniciado.
