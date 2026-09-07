# BACKLOG-CFG-006 — Bifurcação e IF/ELSE estrutural

**Estado:** `active`. **Fase:** `mvp`. **Autorização:** `implementation` em
[WORK-CFG-006](../active/WORK-CFG-006/work-item.json), pelo prompt explícito do usuário.
Dependências: BACKLOG-CFG-022.

## Problema e objetivo observável

Completar o `MVP-CFG-01` com diamond e composição de branches, sem criar modelo IF
COBOL.

## Escopo e estratégia

Implementar `branch` com predicate `known(bool)`, alternativas rotuladas, join
explícito, ramo vazio, destinos iguais e branches nested. Não avaliar valores.

## Critérios de aceitação

M2–M5 dos vetores MVP corretos; nenhuma aresta entre irmãos; ramo terminante não
retorna ao join; número de branches não limitado; `unknown(known(bool))` conserva
dois destinos e `unknown_type` não satisfaz predicate bool, produzindo `INVALID_IR`.

## Evals e invariantes

EVAL-CFG-003, EVAL-CFG-004, EVAL-CFG-005, EVAL-CFG-014.
Vincular invariantes específicos na promoção para work
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

Não avaliar predicados por dataflow, presumir tipo pela posição, procurar
END-IF/ELSE na fonte, exigir JSON ou CLI.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
