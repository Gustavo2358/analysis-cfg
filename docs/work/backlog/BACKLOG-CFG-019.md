# BACKLOG-CFG-019 — Evolução de contrato e otimizações opcionais

**Estado:** `deferred`. **Fase:** `deferred`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-011.

## Problema e objetivo observável

Dar destino controlado a novas capacidades ou otimizações sem ampliar o MVP.

## Escopo e estratégia

Work item próprio por atualização IR, coalescing, consulta adicional ou padrão de controle; revisar semântica, compatibilidade, fontes e mapeamento de pontos antes de código.

## Critérios de aceitação

Cada evolução tem ADR/oracle e prova de equivalência ou perda de precisão explícita; atualização lock somente após review; blocos fundidos preservam observabilidade.

## Evals e invariantes

EVAL-CFG-014, EVAL-CFG-020, EVAL-CFG-015. Vincular invariantes específicos na promoção para work
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

Sem backlog genérico virar autorização para implementar todos recursos futuros ou
modificar unilateralmente o contrato Analysis IR fixado.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
