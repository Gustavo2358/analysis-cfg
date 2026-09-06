# BACKLOG-CFG-011 — Qualificação AIR-STRUCTURE@2 e hardening de regressão

**Estado:** `planned`. **Fase:** `structure`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-007, BACKLOG-CFG-009, BACKLOG-CFG-010.

## Problema e objetivo observável

Provar todas obrigações estruturais do perfil antes de anunciar conformidade.

## Escopo e estratégia

Transformar a matriz upstream em testes executáveis com sub-requisitos `STRUCT`
delimitados; challenge adversarial, determinismo, provenance, `TypeRef`, provas de
domínio e inconsistência de revisões.

## Critérios de aceitação

As 52 obrigações de `AIR-STRUCTURE@2` estão mapeadas: 29 projeções anteriores e
`O-69-STRUCT` a `O-91-STRUCT`. Todos os asserts estruturais exigidos passam; RD/PV,
efeitos escalares e storage ficam fora do papel; gap restante impede claim indevido.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-002, EVAL-CFG-003, EVAL-CFG-004, EVAL-CFG-005,
EVAL-CFG-006, EVAL-CFG-009, EVAL-CFG-010, EVAL-CFG-011, EVAL-CFG-012,
EVAL-CFG-013, EVAL-CFG-014, EVAL-CFG-021, EVAL-CFG-022, EVAL-CFG-023. Vincular invariantes
específicos na promoção para work
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

Não implementar dataflow para satisfazer sub-requisito `SCALAR`/`REGION` de outro
consumer; não pular a projeção `STRUCT` nem declarar o perfil antes dos oráculos.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
