# BACKLOG-CFG-022 — Fluxo linear, jump e halt

**Estado:** `active` em [WORK-CFG-022](../active/WORK-CFG-022/state.md).
**Fase:** `mvp`. **Autorização:** implementação explícita pelo usuário em 06/09/2026.
Dependências: BACKLOG-CFG-005.

## Problema e objetivo observável

Preservar a intenção anterior de BACKLOG-CFG-005 e ampliar CFG-FIRST com fluxo
intrassequência, `jump` explícito e `halt`, antes de branch.

## Escopo e estratégia

TDD para múltiplas instructions ordenadas, targets anteriores/posteriores, jump e
saída halt própria. Reutilizar índices/identidades do primeiro core; não inferir
successor por ordem física.

## Critérios de aceitação

Operações e program points permanecem correlacionados; permutação de Sequences não
muda edges; jump usa somente LabelId explícito; conteúdo sem predecessor permanece;
`return` e `halt` não têm fallthrough e produzem resultados distintos.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-002, EVAL-CFG-005, EVAL-CFG-013, EVAL-CFG-014 e EVAL-CFG-028. Expected
independente e mutantes de fallthrough/ordem precedem produção.

## Fronteiras e extensibilidade

Entrada continua sendo `air-java Publication`; nenhum COBOL, JSON ou frontend entra
no kernel. Capability limita formas aceitas, não a quantidade de instructions.

## Discovery, checkpoints e handoff

Promover somente após CFG-FIRST concluído e autorização explícita. Parar para review
antes de BACKLOG-CFG-006; backlog não encadeia execução automaticamente.

## Fora de escopo

Sem branch/IF, invoke, raise, dispatch, JSON, CLI, dataflow, leader detection ou
coalescing.

## Evidência de conclusão

Evals/mutantes focalizados, comandos e exit codes, gates e review. Nenhum perfil AIR
é alegado por concluir este subset.
