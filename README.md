# Analysis CFG — harness de desenvolvimento

**Entrega:** harness documental e operacional, sem implementação Java. **Data:** 05/09/2026.
**Repositório:** `Gustavo2358/analysis-cfg`. O harness é a primeira baseline do projeto; ainda não há implementação Java de CFG.

Este projeto construirá CFGs a partir da **Analysis IR 2.0.0**, sem conhecer COBOL,
Semantic Product, parser, filesystem, CLI ou cloud no núcleo. Primeiro fecha um
MVP deliberadamente estreito de fluxo linear, `jump`, `branch`/IF-ELSE e saídas;
depois amplia `invoke`, seleção, ciclos e capacidades de controle, preservando
incompletude e preparando controle local contextual.

## Começar

Leia [START_HERE.md](START_HERE.md). Agentes começam por [AGENTS.md](AGENTS.md),
depois pelo [índice de trabalho](docs/work/index.md). Não carregue o harness inteiro.

```bash
bash scripts/harness/check-fast.sh
```

Requisitos deste pacote: Bash e Python 3.9+; sem instalação de dependências.
Python serve **somente para validar o harness**, não para implementar CFG.
A aplicação futura será Java 17, Maven, com tipos IR compartilháveis e fronteiras
Clean/Hexagonal testáveis. Não há `pom.xml`, `.java`, parser de IR, grafo ou testes
Java nesta entrega. Bibliotecas e versões serão fixadas no bootstrap autorizado.

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

O backlog é plano, não autorização. O primeiro work item está em discovery
documental autorizado; este checkpoint sincroniza a IR V2 e para antes de Java.
Implementação exige novo checkpoint autorizado após as decisões físicas restantes.

## O que os gates significam hoje

`docs`, `harness` e `fast` verificam arquivos, referências, IDs, dependências de
backlog, work items e testes adversariais dos validadores documentais.
`architecture`, `semantic`, `performance`, `integration` e `full` retornam
**UNAVAILABLE / exit 3** enquanto não houver implementações aprovadas desses gates.
Verde documental não significa CFG pronto nem conformidade com a IR.

A especificação upstream é referenciada por commit e hashes de blobs. O pacote
contém um mapa de leitura e síntese, **não uma cópia integral da especificação**.
O utilitário opcional [cache_ir.py](scripts/harness/cache_ir.py) pode importar uma
cópia local verificada ou obter os arquivos públicos fixados; nunca troca por `main`.

Este repositório foi inicializado com o harness após revisão contra os objetivos de
arquitetura, extensibilidade, TDD e isolamento definidos para o CFG. A sincronização
do contrato não autoriza Java, Maven ou implementação de CFG.
