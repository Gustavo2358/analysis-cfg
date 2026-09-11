# AGENTS.md

## Propósito e fronteira

Construir um consumidor Java da Analysis IR que publique CFGs rastreáveis,
determinísticos e honestos sobre cobertura e precisão. O núcleo recebe uma
publicação IR em memória; não sabe quem a produziu nem de onde veio.
A fundação Java 21/Maven e o preflight com `air-java` já existem. Não criar ou
ampliar algoritmo/tipos CFG, `BuildCfg`, adapters ou CLI sem checkpoint de
implementação explicitamente autorizado.

## Regras universais

- A IR fixada em `docs/sources/sources.lock.json` governa semântica. Exemplos,
  corpus e conversa são evidência/contexto, não autorização para alterá-la.
- Prefira algoritmos canônicos e gerais. Aproximação conservadora justificada não
  é heurística. Heurística de corpus/texto não entra no core; dúvida exige discovery.
- Não interpretar nomes, linhas, ordem de sequences, `observedKind`, JSON bruto ou
  grafias COBOL para descobrir successors. Não completar fatos pelo frontend.
- Uma Sequence já tem terminador. Sem fallthrough intersequence implícito; sem
  leader detection necessário no MVP; uma Sequence pode originar um nó derivado.
- Preservar todos os fatos cobertos, operandos, outcomes, IDs, origens e lacunas.
  Capability limita formas suportadas, nunca quantidade de ocorrências.
- Separar controle conhecido, contexto de retorno, controle aberto e fatos de dados.
  Não calcular reaching definitions, values ou targets dinâmicos na construção CFG.
  O subsistema CP5 separado segue work item e Wave explicitamente autorizada.
- Dependências apontam para dentro. Core/aplicação não conhecem arquivos, REST,
  serialização, frameworks, CLI, processo, rede, nuvem ou biblioteca de apresentação.
- Trocar adapter preserva a porta. Integração Maven futura não pode exigir JSON
  intermediário nem cópia de modelos IR concorrentes.
- TDD: regra → classes → adversariais → oracle independente → teste RED → código →
  GREEN → refatoração → challenge. Não regenerar esperado a partir do builder.
- Não enfraquecer teste, fixture, invariantes ou gate para tornar o diff verde.
- Backlog não autoriza execução. Respeitar work item, escopo, checkpoint e review.
- Não declarar gate não executado como passado, nem subset como perfil IR completo.

## Roteamento mínimo

Leia [índice de trabalho](docs/work/index.md), depois `work-item.json` e `state.md`
do item autorizado. Carregue somente `must_read` e amplie por dependência real.

| Tema | Rota |
| --- | --- |
| MVP e limites | [missão](docs/product/mission-and-slices.md) |
| Arquitetura | [ARCHITECTURE.md](ARCHITECTURE.md) → [fronteiras](docs/architecture/boundaries.md) |
| Arquivo/memória/Maven | [portas](docs/architecture/ports-and-adapters.md) |
| Extensões | [extensibilidade](docs/architecture/extensibility.md) |
| Operações/CFG | [controle core](docs/domain/core-control.md) |
| Local frames/retorno | [controle local](docs/domain/local-control.md) |
| Unknown/outcomes | [controle aberto](docs/domain/open-control.md) |
| Algoritmo não trivial | [política semântica](docs/engineering/semantic-policy.md) |
| TDD/oráculos | [testes](docs/engineering/testing.md) e [evals](docs/evals/index.md) |
| Gates/lifecycle | [gates](docs/engineering/gates.md) e [protocolo](docs/engineering/work-item-protocol.md) |
| CP5: arquitetura e estado autorizado | [arquitetura CP5](docs/architecture/cp5-dataflow.md), [WORK-CFG-028](docs/work/history/WORK-CFG-028/work-item.json), [lifecycle/branch/PR](docs/work/cp5-lifecycle.json), [Waves](docs/product/cp5-roadmap.md) |
| CP5: gates e resultado | [performance](docs/engineering/cp5-performance.md), [challenges](docs/engineering/cp5-challenges.md), [snapshot de resultado](docs/architecture/analysis-dataflow-result-v1.md) |
| Estado do frontend | [baseline upstream](docs/sources/upstream-state.md) |

[Índice geral](docs/index.md) é mapa, não leitura obrigatória integral.
História, referências completas e backlog de outras fases ficam fora do contexto
padrão. Não manter cópias de regras em tasklists transitórias.

## Handoff

Informe escopo concluído, regras preservadas, testes executados e não executados,
resultados, lacunas e checkpoint seguinte. Atualize `state.md` quando mudar estado
material. Rode os gates exigidos; para código atual do kernel, `check-fast.sh` e
`check-architecture.sh`.
Não faça merge, push ou publicação de pacote sem autorização aplicável.
