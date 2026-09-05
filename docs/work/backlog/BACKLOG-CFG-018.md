# BACKLOG-CFG-018 — Integração em monólito modular Maven

**Estado:** `planned`. **Fase:** `integration`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-008.

## Problema e objetivo observável

Trocar o driving adapter de arquivo por chamada em memória sem modificar algoritmo.

## Escopo e estratégia

Agregar módulos e dependências no reactor; ligar lowerer/producer sintético e CFG ao mesmo artefato IR; manter CLI opcional e boundaries.

## Critérios de aceitação

Mesma porta/modelo/capability; sem JSON intermediário, temp file ou cycles entre módulos; contratos e resultados continuam correlacionados por revisão.

## Evals e invariantes

EVAL-CFG-007, EVAL-CFG-008, EVAL-CFG-020. Vincular invariantes específicos na promoção para work
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

Sem colapsar módulos em main monolítico, duplicated classpath ou infraestrutura dentro do kernel.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
