# BACKLOG-CFG-012 — Escala, performance e gates de qualidade

**Estado:** `planned`. **Fase:** `quality`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-008.

## Problema e objetivo observável

Evitar desenho que funcione apenas em fixtures pequenas.

## Escopo e estratégia

Medir/corroborar índices e custo input+output com séries de chains, diamonds, cycles e dispatch; limites e memória; ativar gates reais de performance/full com evidência.

## Critérios de aceitação

Sem varredura global por target, path enumeration ou truncamento; os gates de produto executam testes não vazios; métricas com ambiente declarado; scopes avançados revisitados depois.

## Evals e invariantes

EVAL-CFG-007, EVAL-CFG-013, EVAL-CFG-014, EVAL-CFG-015. Vincular invariantes específicos na promoção para work
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

Sem threshold temporal arbitrário, otimização sem equivalência ou promessa de custo linear no controle contextual.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
