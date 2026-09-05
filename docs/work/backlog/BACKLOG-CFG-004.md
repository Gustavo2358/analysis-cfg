# BACKLOG-CFG-004 — Fixtures independentes e adapter de transporte

**Estado:** `planned`. **Fase:** `mvp`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-002, BACKLOG-CFG-003.

## Problema e objetivo observável

Permitir leitura de fixtures de arquivo sem contaminar o modelo ou core.

## Escopo e estratégia

Definir schema técnico versionado conforme decisão anterior; criar fixtures fechadas mínimas, malformed/invalid/unsupported; codec separado e builders de teste em memória.

## Critérios de aceitação

Arquivo e objeto equivalente produzem a mesma Publication; todos campos semânticos exigidos materializados; erro de I/O não é INVALID_IR; exemplos .air não tratados como gramática oficial.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-008, EVAL-CFG-013, EVAL-CFG-014. Vincular invariantes específicos na promoção para work
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

Não consumir semantic-product.json nem inferir controle no codec; nenhum esperado CFG gerado pelo builder.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
