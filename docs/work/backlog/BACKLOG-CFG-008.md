# BACKLOG-CFG-008 — CLI mínima e fechamento MVP arquivo/memória

**Estado:** `planned`. **Fase:** `mvp`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-004, BACKLOG-CFG-006, BACKLOG-CFG-007.

## Problema e objetivo observável

Demonstrar o pipeline de arquivo à porta e ao CFG sem acoplamento de infraestrutura.

## Escopo e estratégia

Composition root explícito, driver/CLI mínimo, exportador estruturado simples; teste E2E equivalente ao caso em memória; status/exit code observáveis.

## Critérios de aceitação

Arquivo→Publication→BuildCfg→resultado funciona; kernel testado sem adapters; nenhuma serialização no caminho direto; MVP-CFG-01 documentado sem claim AIR-STRUCTURE completo.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-003, EVAL-CFG-006, EVAL-CFG-007, EVAL-CFG-008, EVAL-CFG-013, EVAL-CFG-014. Vincular invariantes específicos na promoção para work
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

Sem UI/HTML elaborada, REST, container/cloud, banco, dataflow ou alteração do frontend.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
