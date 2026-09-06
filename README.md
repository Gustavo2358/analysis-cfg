# Analysis CFG — harness de desenvolvimento

**Entrega:** harness documental e operacional, sem implementação Java. **Data:** 06/09/2026.
**Repositório:** `Gustavo2358/analysis-cfg`. O harness é a primeira baseline do projeto; ainda não há implementação Java de CFG.

Este projeto construirá CFGs a partir da **Analysis IR 2.0.0**, recebendo exatamente
a `Publication` do `air-java`, sem conhecer COBOL, Semantic Product, parser,
filesystem, CLI ou cloud no núcleo. `CFG-FIRST` prova Entry → Sequence(`Return`) →
normal exit em memória; `MVP-CFG-01` acrescenta linear/jump/halt e branch/IF-ELSE.

## Começar

Leia [START_HERE.md](START_HERE.md). Agentes começam por [AGENTS.md](AGENTS.md),
depois pelo [índice de trabalho](docs/work/index.md). Não carregue o harness inteiro.

```bash
bash scripts/harness/check-fast.sh
```

Requisitos deste pacote: Bash e Python 3.9+; sem instalação de dependências.
Python serve **somente para validar o harness**, não para implementar CFG.
A aplicação futura será Java 21 sem preview, Maven, com o modelo/validator AIR do
`io.github.gustavo2358:air-java:0.1.0-SNAPSHOT` fixado no SHA documentado e
fronteiras Clean/Hexagonal testáveis. Não há `pom.xml`, `.java`, parser de IR,
grafo ou testes Java nesta entrega.

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

O backlog é plano, não autorização. `WORK-CFG-001` concluiu as decisões pré-Java;
`BACKLOG-CFG-002` está somente pronto para autorização. Implementação exige novo
work item explícito e não começa por consequência deste PR.

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

`analysis-ir` possui o binding JSON normativo, ainda inexistente no commit fixado;
um reader futuro será adapter e não bloqueia `CFG-FIRST`. O fechamento documental
não autoriza Java, Maven ou implementação de CFG.
