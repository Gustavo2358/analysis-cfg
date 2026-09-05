# BACKLOG-CFG-021 — CI dos gates e higiene do harness

**Estado:** `planned`. **Fase:** `quality`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-001.

## Problema e objetivo observável

Levar os mesmos entrypoints locais para CI sem inventar verdes.

## Escopo e estratégia

Configurar workflow docs/harness antes do Java e, quando implementados, jobs de produto; fixar actions/toolchains e permissões mínimas; ausência de suite deve ser explícita.

## Critérios de aceitação

CI chama scripts existentes; statuses não confundem documentação e produto; sem zero-test pass; testa alteração indevida de work item/invariante e estado de gates.

## Evals e invariantes

EVAL-CFG-007. Vincular invariantes específicos na promoção para work
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

Não adicionar deploy, credenciais, serviços cloud ou iniciar backlog por execução do workflow.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
