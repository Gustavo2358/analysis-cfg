# BACKLOG-CFG-020 — Análises posteriores — fronteira reservada

**Estado:** `deferred`. **Fase:** `deferred`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-011.

## Problema e objetivo observável

Reservar evolução para effects/storage, RD, values e dependency facts sem implementá-los agora.

## Escopo e estratégia

Quando autorizado, criar discovery independente com insumos CFG, modelo de memória e perfil aplicável; preservar contratos do consumidor CFG.

## Critérios de aceitação

Planejamento reconhece fatos necessários sem colocar GEN/KILL/PV no CFG; nenhuma classe de análise criada como preparação especulativa.

## Evals e invariantes

EVAL-CFG-020. Vincular invariantes específicos na promoção para work
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

Todo dataflow, reaching definitions, storage solver, CDG, dominância, slicing e dependency extraction fora do escopo atual.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
