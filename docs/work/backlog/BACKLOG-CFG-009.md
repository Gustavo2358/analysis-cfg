# BACKLOG-CFG-009 — Dispatch, ciclos e múltiplas entradas

**Estado:** `planned`. **Fase:** `structure`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-008.

## Problema e objetivo observável

Ampliar controle com primitives existentes para seleção e repetição progressivas.

## Escopo e estratégia

TDD para dispatch/default, loops pré/pós-teste, self-loop/irreducível, labels compartilhados e múltiplas units/entries.

## Critérios de aceitação

Cases semânticos duplicados inválidos; default mantido; zero iterações pré-teste e ao menos corpo no pós-teste; não exigir DAG; entradas não se fundem.

## Evals e invariantes

EVAL-CFG-010, EVAL-CFG-011, EVAL-CFG-014, EVAL-CFG-015. Vincular invariantes específicos na promoção para work
item. Ver [catálogo](../../evals/catalog.md) e [invariantes](../../architecture/invariants.md).
Antes de código, transformar expected em testes RED independentes; documentar o
resultado observado, não apenas intenção de TDD.

## Fronteiras e extensibilidade

Preservar Publication/CFG separados, porta em memória e dependências para dentro.
Nenhum nome COBOL entra na decisão do builder. Nova semântica usa capability IR
ou proposta upstream; novo transporte usa adapter. Mudança em regra central exige
ADR e avaliação de impacto sobre consumidores/fixtures/perfis.

## Discovery, checkpoints e handoff

Promover apenas este item para work item com paths concretos, must_read mínimo,
domínio, riscos, checkpoints e gates. Nas decisões não triviais, pesquisar fonte
primária e registrar candidatos/precondições antes de implementar. Ao atingir o
checkpoint autorizado, atualizar estado e parar para review; não avançar ao próximo
item porque ficou verde. Sem duplicar este plano em tasklist permanente.

## Fora de escopo

Sem EVALUATE/PERFORM parser, unrolling de caminho ou análise de valores do seletor.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
