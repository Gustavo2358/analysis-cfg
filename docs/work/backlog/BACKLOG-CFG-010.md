# BACKLOG-CFG-010 — Envelopes abertos e compatibilidade de extensões

**Estado:** `planned`. **Fase:** `structure`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-007, BACKLOG-CFG-009.

## Problema e objetivo observável

Consumir parcialidade sem inventar grafo completo.

## Escopo e estratégia

Projetar ControlEnvelope/ControlScope e negociação precise/reduced/conservative/unsupported; preservar influência sobre queries e limites conhecidos.

## Critérios de aceitação

O-33/O-34/O-47/O-48 no escopo estrutural; scope com label interior não vira sink terminal; fallback uma vez; unknown e invalid distinguíveis.

## Evals e invariantes

EVAL-CFG-006, EVAL-CFG-009, EVAL-CFG-012, EVAL-CFG-013. Vincular invariantes específicos na promoção para work
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

Sem interpretar payload por texto, limitar scope ao exit por conveniência ou declarar precisão local contextual via fallback.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
