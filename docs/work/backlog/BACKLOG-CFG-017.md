# BACKLOG-CFG-017 — Aceitação bilateral com CobolLower

**Estado:** `planned`. **Fase:** `integration`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-008.

## Problema e objetivo observável

Confrontar fixtures sintéticas com publicação real sem relaxar o contrato.

## Escopo e estratégia

Quando houver lowerer conforme à Analysis IR 2.0.0, comparar suas publicações com
capabilities, `TypeRef`, premises e escopos esperados; criar regressões IR isoladas
e registrar gaps upstream sem ensinar COBOL ao CFG.

## Critérios de aceitação

Controle de exemplos equivalentes e fatos V2 preservados; fonte→IR e IR→CFG
validados separadamente; readiness parcial não é promovida; testes continuam
rodando sem repos vizinhos.

## Evals e invariantes

EVAL-CFG-008, EVAL-CFG-013, EVAL-CFG-020, EVAL-CFG-021, EVAL-CFG-022.
Vincular invariantes específicos na promoção para work
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

Sem transformar bloqueio upstream em requisito de esperar todo frontend; sem reparsing no consumer.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
