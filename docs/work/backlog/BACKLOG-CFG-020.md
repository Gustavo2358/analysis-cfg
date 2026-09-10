# BACKLOG-CFG-020 — Análises posteriores — fronteira reservada

**Estado:** `active` somente na preparação CP5. **Fase:** `analysis`. **Autorização:** backlog não autoriza execução.
Dependência focal CP5: BACKLOG-CFG-027. A dependência histórica BACKLOG-CFG-011 continua aplicável à qualificação de análises gerais, não ao profile CP5 aprovado.

## Problema e objetivo observável

Formalizar e desenvolver CP5 em cinco Waves no WORK-CFG-028; autorização atual cobre somente harness. Demais análises continuam adiadas.

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

Nesta preparação: toda implementação dataflow/values. Após CP5 autorizado por Waves, continuam fora: RD completo, storage geral, CDG/dominância/slicing e consumers de negócio.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.

## Recorte CP5 aprovado em 09/09/2026

[Lifecycle](../cp5-lifecycle.json), [roadmap](../../product/cp5-roadmap.md) e [follow-ups](../cp5-follow-ups.md). Reutilização deste item evita backlog CP5 duplicado. Nenhuma Wave nem follow-up foi iniciado.
