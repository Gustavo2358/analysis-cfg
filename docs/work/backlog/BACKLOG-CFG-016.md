# BACKLOG-CFG-016 — Controle indireto e AIR-INDIRECT-CONTROL@2

**Estado:** `planned`. **Fase:** `indirect`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-011.

## Problema e objetivo observável

Construir CFG conservador de `control.indirect@1`/`indirect.jump` para futura
qualificação `AIR-INDIRECT-CONTROL@2`, sem dependência circular de RD.

## Escopo e estratégia

Interpretar `known(label(S))`, validar membership e projetar todos targets de S;
`unknown(known(label(S)))` mantém o universo, enquanto `unknown_type` não permite a
operação precisa. Guardar informação para refinamento futuro versionado.

## Critérios de aceitação

O-61/O-63; O-62 registrado opcional sem fingir execução; universo fora do tipo e
target `unknown_type` inválidos; sem inferir targets pela primeira atribuição.

## Evals e invariantes

EVAL-CFG-019, EVAL-CFG-012. Vincular invariantes específicos na promoção para work
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

Sem implementar ALTER COBOL, RD ou refinamento por valores nesta etapa.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
