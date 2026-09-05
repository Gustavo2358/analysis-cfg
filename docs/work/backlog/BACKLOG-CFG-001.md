# BACKLOG-CFG-001 — Discovery de fronteiras e plano executivo

**Estado:** `ready_for_authorization`. **Fase:** `discovery`. **Autorização:** backlog não autoriza execução.
Dependências: nenhuma.

## Problema e objetivo observável

Fechar ownership do modelo IR, módulos e transporte de fixture antes de Java.

## Escopo e estratégia

Ler capítulos normativos mínimos, baseline upstream e ADRs; decidir assinatura semântica da porta, versão técnica do codec e matriz de suporte inicial.

## Critérios de aceitação

ADRs propostos aceitos/substituídos; regra de controle local não achatada na API; plano com paths e testes executáveis para o próximo checkpoint; revisão humana separada.

## Evals e invariantes

EVAL-CFG-007, EVAL-CFG-008, EVAL-CFG-009, EVAL-CFG-020. Vincular invariantes específicos na promoção para work
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

Nenhum Java, POM, decoder, grafo ou dependência instalada. Não mudar a especificação IR para facilitar implementação.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
