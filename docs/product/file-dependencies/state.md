# Estado / handoff curto — FD-H0–H4

**Preparação para revisão humana. FD-W0–W11 TODO / NOT_STARTED.**
O estado da implementação é separado da prontidão documental. O work item lean
WORK-FD-HARNESS permanece IN_PROGRESS enquanto os PRs não forem mergeados.

## Campaign state

| Checkpoint | Estado / evidência |
| --- | --- |
| H0 | concluído: baseline local/remota, pins, dirty trees e gaps de código reconciliados |
| H1 | concluído: brief, fontes/perfis, contratos e rotas por camada |
| H2 | concluído: 12 itens TODO; grafo, precondições, limites, aceitação e repins |
| H3 | concluído: matriz N/C/D, T01–T54, CALL-X, scope guards e custo de gates |
| H4 | concluído: auditoria estrutural e de escopo PASS; pacote pronto para revisão humana |

## Retomada

Worktrees: `<workspace>/.file-dependencies/worktrees/<repo>`.
Branch persistente: `feat/file-dependencies`. PRs/SHA finais serão registrados
nesta revisão; nenhum merge, auto-merge ou repin em H. E2E exclusivamente local.
O [baseline](baseline.md) contém os SHAs de entrada; o Git fornece os checkpoints.

## Auditoria como próximo agente

| Pergunta | Resposta mínima |
| --- | --- |
| Filosofia da capability? | [brief](brief.md), scope lock e analogia CALL |
| DSNAME fora de escopo? | brief, primeiro bloco; SG1–SG5 no [catálogo](test-catalog.md) |
| Repos que mudam? | [baseline](baseline.md), [grafo](waves.md), campo repos do item |
| Ordem/consumers/pins? | grafo, seção de pins e preconditions do item |
| Aceitação W0? | [FD-W0](../../work/active/FD-W0.yaml), acceptance |
| Comandos? | item → [verificação](verification.md), F-DECL/L-INPUT/B-SP |
| Regressões? | CALL-X e tests/regressions do item; resolver FILE existente |
| SELECT/ASSIGN/FD? | [matriz](coverage.md) N01–N10 e página local frontend |
| Decisões abertas? | [contratos](contracts.md#decisoes-abertas), dono e wave |
| Preciso reler discovery para W0? | **Não**, salvo evidência histórica/detalhe adicional |

## Open decisions

Somente D-AIR, D-WIRE, D-EFFECT, D-DYNAMIC e D-D-AUTH, com prazo no documento de
contratos. Nenhuma exige resolver JCL/DSNAME. Nenhuma bloqueia W0 declarativo SP;
D-AIR/D-WIRE são pré-condições internas W1. H4 não afirma que já foram decididas.

## Validation

Docs gates executados nos quatro produtos: 12 + 13 + 12 + 14 contracasos lean,
todos PASS DOCS_ONLY. Contracasos usam stubs; suas mensagens CODE_CHANGE não são
build/teste de produção. Auditoria final PASS: 59 links locais, 12 itens TODO/campos obrigatórios, grafo sem ciclos, 48 linhas N/C/D, 54 casos T, 6 CALL-X e 5 SG. Classificador dos quatro repos: DOCS_ONLY. CI remoto será observado após publicação dos Drafts.
NOT_RUN: testes semânticos, Maven, technical FAST, qualification-local, E2E/corpus,
mutação/performance e exemplos AIR manuais. Delta H apenas docs/work items;
nenhum pin, contrato wire, teste de aplicação ou produção mudou. Nenhum PASS
histórico foi reapresentado como execução FD.

Achado H4 corrigido: mover os itens para `docs/work/active/`, conforme classificador
vigente; nenhum gate foi relaxado. Corrigida também reserialização incidental de
texto histórico no registry lower. Logs novos em
`artefatos-e2e/file-dependencies-20260916/validation/`.

## Readiness for FD-W0 / STOP

Conteúdo suficiente para executar W0 **após revisão humana H4 e autorização de
implementação**. O percurso é AGENTS frontend → página local → brief → FD-W0 →
gates/perfil necessários, sem reconstruir discovery. Readiness não certifica
nenhuma funcionalidade FILE e não dispensa as decisões das waves seguintes.

**STOP — não iniciar FD-W0. Aguardar revisão humana.**
