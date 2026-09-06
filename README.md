# Analysis CFG — fundação do consumer AIR

**Entrega atual:** Java 21/Maven, porta física com `air-java` e seam explícito de
capabilities, ainda sem algoritmo CFG. **Data:** 06/09/2026. **Repositório:**
`Gustavo2358/analysis-cfg`.

Este projeto construirá CFGs a partir da **Analysis IR 2.0.0**, recebendo exatamente
a `Publication` do `air-java`, sem conhecer COBOL, Semantic Product, parser,
filesystem, CLI ou cloud no núcleo. `CFG-FIRST` prova Entry → Sequence(`Return`) →
normal exit em memória; `MVP-CFG-01` acrescenta linear/jump/halt e branch/IF-ELSE.

## Começar

Leia [START_HERE.md](START_HERE.md). Agentes começam por [AGENTS.md](AGENTS.md),
depois pelo [índice de trabalho](docs/work/index.md). Não carregue o harness inteiro.

```bash
bash scripts/harness/check-fast.sh
bash scripts/harness/check-architecture.sh
```

Requisitos: Bash, Python 3.9+, Maven e JDK 21 ou mais novo. O bytecode é sempre
compilado com `--release 21`, sem preview; o CI executa com JDK 21. Python valida
somente o harness e o gate arquitetural, não implementa CFG.

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
registry por capability/version. Nós, arestas e interpretação de controle continuam
fora deste checkpoint.

## O que os gates significam hoje

`docs`, `harness` e `fast` verificam arquivos, referências, IDs, dependências de
backlog, work items e os próprios validadores documentais. `architecture` executa
Maven/testes e inspeciona dependências e bytecode do kernel. `semantic`,
`performance`, `integration` e, por consequência, `full` permanecem
**UNAVAILABLE / exit 3**. Nenhum verde deste checkpoint significa CFG pronto nem
conformidade com um perfil AIR.

A especificação upstream é referenciada por commit e hashes de blobs. O pacote
contém um mapa de leitura e síntese, **não uma cópia integral da especificação**.
O utilitário opcional [cache_ir.py](scripts/harness/cache_ir.py) pode importar uma
cópia local verificada ou obter os arquivos públicos fixados; nunca troca por `main`.

`analysis-ir` contém o Analysis IR JSON Binding 1.0.0, ainda **DRAFT** no commit
fixado. Este projeto não o implementa neste checkpoint; um reader futuro será
adapter externo e não alterará a boundary em memória.
