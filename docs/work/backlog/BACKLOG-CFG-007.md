# BACKLOG-CFG-007 — Invoke, raise e resultados de controle delimitados

**Estado:** `planned`. **Fase:** `structure`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-006.

[WORK-CFG-032](../history/WORK-CFG-032.md) concluiu discovery. [WORK-CFG-033](../active/WORK-CFG-033/work-item.json) autoriza somente a slice W1D de Invoke Normal, effects e CALL dependency. O restante do escopo amplo abaixo continua planned e não autorizado.

## Problema e objetivo observável

Preservar sites de chamada, outcomes e saídas excepcionais sem resolver targets ou
efeitos.

## Escopo e estratégia

Implementar projeção local de `invoke` terminador com normal, tags/propagate,
halt/diverge e resultado unsupported quando open scope ainda não implementado.
Implementar `raise(tag, values)` como encerramento excepcional da ativação, com
destino derivado da interação invocadora ou saída excepcional raiz.

## Critérios de aceitação

Normal significa possibilidade; ausência de normal não vira fallthrough;
before/after(outcome) distinguíveis; metadata e operandos preservados; contrato
ausente não vira pureza. `raise` preserva tag/valores/origem, não produz saída normal
e nunca ganha fallthrough local.

## Evals e invariantes

EVAL-CFG-005, EVAL-CFG-006, EVAL-CFG-013, EVAL-CFG-023. Vincular invariantes específicos na promoção para work
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

Sem expansão de callee, resolução de programa, state effect summary ou reaching definitions.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
