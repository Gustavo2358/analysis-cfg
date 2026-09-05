# BACKLOG-CFG-015 — Consultas pareadas e conformidade de controle local

**Estado:** `planned`. **Fase:** `local`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-014, BACKLOG-CFG-012.

## Problema e objetivo observável

Provar que a representação realmente impede retornos ao callsite errado.

## Escopo e estratégia

Implementar consultas/semântica contextual aprovadas; tratar recursão e limites; distinguir projeção conservadora de modo preciso; qualificar AIR-LOCAL-CONTROL.

## Critérios de aceitação

O-56–60 e L1–L5; matching efetivo nas consultas, não apenas metadata; terminação documentada; sem enumerar pilhas infinitas; ANALYSIS_LIMIT quando aplicável.

## Evals e invariantes

EVAL-CFG-016, EVAL-CFG-017, EVAL-CFG-018, EVAL-CFG-015. Vincular invariantes específicos na promoção para work
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

Sem vender adjacência plana como paths exatos nem iniciar reaching definitions.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
