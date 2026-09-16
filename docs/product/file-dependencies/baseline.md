# FD-H0 — baseline reconciliada em 16/09/2026

Inspeção local + `gh api .../branches/main` e `gh pr list --state open`:
mains remotas iguais às locais; nenhum PR aberto nos cinco produtos antes de FD.
Os cinco SHAs do discovery permanecem atuais. Leitura de código não é PASS de
capability; nenhum build/teste de produção foi executado em H0.

| Repo / responsabilidade | HEAD observado |
| --- | --- |
| proleap-poc — COBOL/nominal/storage/SP | `2c838d47858957a779f185e3df265ee8b2891d8d` |
| cobol-lower — admission/decoder/SP → AIR | `051973f92d53b4e84741b17ce7d762d8161cabb3` |
| air-java — modelo/validator/codec | `a33097e091f4c7fc7b181a279f10f709e52ddaf7` |
| analysis-ir — contrato normativo | `e86ef0e468bbb9f703f4e939847356b771137139` |
| analysis-cfg — CFG/efeitos/RD/valores/dependency/JSON | `194fac2af6cfc54053318164275e682feac5c750` |
| artefatos-e2e — evidência local; branch docs/cp6-w2-discovery | `bd4dafa6f5a89807d4b9bef350e923bcf3a5c331` |

## Concorrência e isolamento

Produtos originais limpos. Worktrees históricos/concomitantes de storage, CICS,
EP, recall-first e GO TO permanecem intactos. `exec-dli/proleap-poc` tem
`docs/work/exec-dli-discovery.md` não rastreado. E2E original tem não rastreados
`carddemo-blockers-20260914/`, `checkpoint-4e/`, `cp5/`,
`dvi-escape-20260914/display-probe/`, `plano-cleanup-imd.md`.
Nenhum status local é interpretado como aprovação/merge; índices de campanhas
antigas estão desatualizados em relação ao Git, sem serem reescritos por FD.

FD usa worktrees dedicados para frontend/lower/AIR/CFG/E2E na branch
`feat/file-dependencies`. `analysis-ir` fica somente leitura em H0–H4.
Não há Git novo na raiz, nem remote no E2E, nem repin nesta preparação.

## Pins realmente consumidos

| Fronteira | Pin / versão |
| --- | --- |
| lower SRC-SP e CFG proleap_poc | `b5f626f5b42b5168af29a740aace60a1f0a993d1`; SP 2.20.0 / storage 1.7.0 |
| lower/CFG air-java | `5b8a5c231958b62de34583d871cbb10b473d53e8`; 0.1.0-SNAPSHOT |
| AIR/lower/CFG analysis-ir | `6b8ce96f5b1020199e3fcceedf81d931a3b8d2ff`; AIR 2.0.0 / binding 1.0.0 DRAFT |
| CFG cobol_lower | `69c78c5f696e1351c514f16c1fdab0c9d6cca524` |
| lower referência CFG (não dependência de execução) | `63c66d6a5cc1a7a9d30bbfc70ce4f12426242cfd` |

Autoridade: `cobol-lower/docs/sources/sources.lock.json`,
`air-java/docs/sources.lock.json`, `analysis-cfg/docs/sources/sources.lock.json`.
Comparação Git pin→HEAD: frontend muda docs/estado (inclusive redação do contrato),
AIR e IR mudam apenas docs de EP; comparar conteúdo consumido, não igualdade de
SHA. A evidência H0 registra também a comparação do pin lower. Não repinar por
divergência nominal. Qualificação futura registra o conjunto exato usado.

## Estado confirmado por camada

Paths abaixo são relativos ao repo indicado; nomes de classe têm busca única.

| Camada / pontos de entrada | Existente | Gap e consequência |
| --- | --- | --- |
| Frontend `AstBuilder`, `Ast`, `Cobol.g4` | SELECT/ASSIGN e FD/SD reconhecidos; assignment textual | FD/SD sem discriminante tipado; cláusulas não publicadas como inventário FILE |
| `SymbolTableBuilder.buildFileEntities`; `ProcedureFileProgramReferenceResolverTest` | SELECT+FD já reunidos por entidade; FILE, GLOBAL, ancestor lookup/shadowing testados | reutilizar; record ownership público precisa de contrato, não reconstrução no lower |
| `CobolSemanticProduct.StatementFact`, writer | MOVE/CALL/CICS LINK-XCTL/IF/GOBACK/PERFORM/EVALUATE/GO TO/Observed | nenhum fact nativo de I/O; observado/modelado não implica dependency-ready |
| `StorageLayoutSemantics` | perfil IBM Enterprise 6.4 DISPLAY/1047, regiões e aliases | auditar FILE SECTION e múltiplos layouts; não pressupor buffers independentes |
| `CobolLowerer` → `PartialProgramLowerer` / assembler / admission / `SpJsonDecoder` | composicional é prioritário; regiões, origem e gaps; uma unit/entry selecionada | resources/artifactRelations vazios; alterar caminho corrente, não profiles históricos |
| AIR `Interactions`, `Artifacts.Relation` / spec 01/04 | file, LiteralTarget/ComputedTarget, efeitos/outcomes, declarações estruturais | Resource sem owner tipado; relação parte de ArtifactId e não expressa toda associação unit/record/conector |
| AIR `BindingWriter`/`BindingReader` | invoke transportado | inventários resources/artifactRelations exigidos vazios: gap concreto de codec, ainda que modelo exista |
| CFG/dataflow `DependencyAnalysis`, `CallDependencyPlan`, AnalysisSession/ValuesPlan | inventário parcial e queries BEFORE, providers regionais compartilhados | seleção só category=program, namespaces cobol.program/cics.program; consumer FILE inexistente |
| `DependencyResult`, `DependencyJson` | sites/edges, supports, origem, remainders; wire 1.1.0/1.2.0 | artifacts é provenance; não inventário de arquivos; seção nova requer versão/reader explícitos |
| `scripts/project/dependency_wire.py` | reader independente com chaves e versões fechadas | rejeita adição de fileDependencies nas versões correntes; W1 deve negociar |

O discovery subestimava a associação nominal já existente. Seus cuidados sobre
codec e readers agora têm gaps concretos. O perfil 6.4 já selecionado em storage
justifica a autoridade nativa de FD, em vez de importar automaticamente exemplos 6.3.

## Infraestrutura e limites

Gates correntes: `lean.py docs/fast/qualification-local`; remoto FAST apenas.
`scripts/project/prepare_w2d_producers.py` aceita `--lock` e builds isolados;
`e2e_partial.py` contém teto histórico de versões SP até 2.8 e não é gate FD pronto.
`artefatos-e2e/run-e2e.sh` usa paths/produtos CP3 fixos e remove seu output corrente:
não executar para FD. Reutilizar composição CLI/build e diretórios novos, conforme
[verificação](verification.md), sem declarar wrappers históricos compatíveis.

Decisões fechadas: [scope lock](brief.md) e [ADR](../../architecture/decisions/ADR-0015.md).
Decisões abertas, donos e ondas bloqueadas: [contratos](contracts.md#decisoes-abertas).
