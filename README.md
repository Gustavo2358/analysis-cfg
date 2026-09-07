# Analysis CFG — consumer AIR

**Entrega atual:** fluxo linear em memória: Entry → Sequence(instructions, Jump) →
Sequence(Return | Halt) → normal exit ou término por Halt,
com Java 21/Maven, `air-java` e seam explícito de capabilities.
**Data:** 06/09/2026. **Repositório:**
`Gustavo2358/analysis-cfg`.

Este projeto constrói CFGs a partir da **Analysis IR 2.0.0**, recebendo exatamente
a `Publication` do `air-java`, sem conhecer COBOL, Semantic Product, parser,
filesystem, CLI ou cloud no núcleo. `CFG-FIRST` prova Entry → Sequence(`Return`) →
normal exit em memória. WORK-CFG-022 acrescenta instructions, Jump e Halt;
`MVP-CFG-01` ainda depende de branch/IF-ELSE.

## Começar

Leia [START_HERE.md](START_HERE.md). Agentes começam por [AGENTS.md](AGENTS.md),
depois pelo [índice de trabalho](docs/work/index.md). Não carregue o harness inteiro.

```bash
bash scripts/harness/check-fast.sh
bash scripts/harness/check-architecture.sh
bash scripts/harness/check-semantic.sh
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
Branch/IF, dispatch, invoke/raise, controle aberto/local/indireto, JSON, CLI e
dataflow permanecem posteriores. BACKLOG-CFG-004/006 não foram iniciados.

## O que os gates significam hoje

`docs`, `harness` e `fast` verificam arquivos, referências, IDs, dependências de
backlog, work items e os próprios validadores documentais. `architecture` executa
Maven/testes e inspeciona dependências e bytecode do kernel. `semantic` executa
explicitamente os 17 testes de EVAL-CFG-025 e os 22 de EVAL-CFG-028; rejeita
suítes/métodos ausentes, extras, duplicados ou pulados.
`performance`, `integration` e, por consequência, `full` permanecem
**UNAVAILABLE / exit 3**; full executa fast, architecture e semantic antes de parar
em performance. CFG-FIRST não implica conformidade com um perfil AIR completo.

A especificação upstream é referenciada por commit e hashes de blobs. O pacote
contém um mapa de leitura e síntese, **não uma cópia integral da especificação**.
O utilitário opcional [cache_ir.py](scripts/harness/cache_ir.py) pode importar uma
cópia local verificada ou obter os arquivos públicos fixados; nunca troca por `main`.

`analysis-ir` contém o Analysis IR JSON Binding 1.0.0, ainda **DRAFT** no commit
fixado. Este projeto não o implementa neste checkpoint; um reader futuro será
adapter externo e não alterará a boundary em memória.
