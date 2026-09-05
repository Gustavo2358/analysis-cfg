# Arquitetura — mapa curto

**Decidido para este projeto; ainda não implementado.** [ADRs](docs/architecture/decisions/index.md).

```text
AGORA: fixture em arquivo → adapter de leitura/decodificação ─┐
                                                            ├→ porta BuildCfg
TESTES: Publication criada em memória ────────────────────────┤       ↓
FUTURO: CobolLower em módulo Maven ───────────────────────────┘  caso de uso
                                                                  ↓
                                                         semântica de CFG
                                                                  ↓
                                                           CfgBuildResult
                                                                  ↓
                                              caller / adapter JSON-DOT / CLI
```

A porta recebe dados semânticos, não `Path`, `InputStream`, JSON, URL ou source code.
O caso de uso coordena validação, negociação de capacidades, projeção e publicação;
o domínio contém regras de transição. O composition root conecta implementações.
A troca de transporte não muda a porta e nem o algoritmo.

A IR e o CFG são produtos distintos. O CFG referencia publicação, units, entries,
sequences, operations, program points e origins por identidades próprias/correlação.
Preservar uma Sequence por nó é a política inicial, não afirmar que SequenceId é
BlockId ou que o bloco é máximo. Coalescing fica adiado.

## Unidades de dependência pretendidas

| Unidade | Dependências permitidas |
| --- | --- |
| Modelo compartilhado de IR | JDK, sem produtor, transportes ou CFG |
| `cfg-kernel` (domínio + aplicação/portas) | JDK e modelo semântico IR aprovado |
| `cfg-adapters` | kernel, modelo IR e bibliotecas de infraestrutura |
| `cfg-launcher` | adapters e kernel, apenas composição/execução |

Não existem módulos/POMs nesta entrega. O primeiro discovery decide coordenadas e
ownership do modelo IR; não se criarão modelos privados incompatíveis no lowerer e
no CFG. Subpackages separam domínio e aplicação mesmo dentro do mesmo módulo.

## Pipeline de domínio

Validação/negociação → índices → nós de Sequence → transições por terminador →
saídas/fronteiras/contexto → validação derivada → resultado imutável.

Controle local não é apenas grafo de adjacência: o contrato exige retornos pareados
ou aproximação declarada. A API não deve prometer que todo successor é incondicional.
O MVP-CFG-01 termina em fluxo linear + IF/ELSE; `invoke` e capacidades avançadas
entram em slices posteriores. O MVP pode não suportar `control.local@1`, mas não
pode apagar essa capability.

Detalhes: [fronteiras](docs/architecture/boundaries.md), [portas e adapters](docs/architecture/ports-and-adapters.md),
[extensibilidade](docs/architecture/extensibility.md), [pipeline](docs/architecture/pipeline.md).
