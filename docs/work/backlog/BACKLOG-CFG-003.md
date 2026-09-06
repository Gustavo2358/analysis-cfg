# BACKLOG-CFG-003 — Fronteiras Clean e seam semântico de extensões

**Estado:** `planned`. **Fase:** `foundation`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-002.

## Problema e objetivo observável

Proteger a arquitetura antes do primeiro builder, com composição pequena e extensão controlada.

## Escopo e estratégia

Definir porta de caso de uso e contratos de transição tipados; preservar fatos V2
sem misturá-los ao transporte; manter potencial de contexto/open scope; criar provas
de dependência bytecode e registro de extensão sintética por capability e versão.

## Critérios de aceitação

Adicionar intérprete de teste não exige editar orquestrador; registro ambíguo falha; I/O em core e imports frontend são rejeitados; gate architecture se torna real com casos negativos.

## Evals e invariantes

EVAL-CFG-007, EVAL-CFG-008, EVAL-CFG-009. Vincular invariantes específicos na promoção para work
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

Não implementar local frames de produção, framework genérico de plugins, DI por
reflection nem factories por conveniência. Perfis `@2` não renomeiam extensões `@1`.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
