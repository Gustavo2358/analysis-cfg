# BACKLOG-CFG-005 — Núcleo CFG: projeção linear e saídas

**Estado:** `planned`. **Fase:** `mvp`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-002, BACKLOG-CFG-003.

## Problema e objetivo observável

Produzir primeiro grafo de Publication em memória com fluxo explícito.

## Escopo e estratégia

Testes RED antes do código; índices namespaced, Sequence→nó próprio, operações e
fatos V2 preservados, `jump`, `return`/`halt` e produto imutável com evidência.

## Critérios de aceitação

Permutação física não muda relação; target anterior funciona; unidade com label
pendente falha; saída não gera fallthrough; inventário sem predecessor permanece;
`TypeRef`/premises não são apagados nem transformados em análise de valores.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-002, EVAL-CFG-005, EVAL-CFG-007, EVAL-CFG-013,
EVAL-CFG-014. Vincular invariantes específicos na promoção para work
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

Sem leader finder, coalescing, constant propagation, novas expressões COBOL ou file access.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
