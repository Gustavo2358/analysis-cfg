# BACKLOG-CFG-001 — Discovery de fronteiras e plano executivo

**Estado:** `completed`. **Fase:** `discovery`. **Autorização:** somente o work item delimitou o checkpoint.
Dependências: nenhuma.

## Problema e objetivo observável

Fechar ownership do modelo IR, toolchain, porta, lifetime e ownership de transporte
antes de Java.

## Escopo e estratégia

Ler capítulos normativos mínimos, baseline upstream e ADRs; decidir assinatura
semântica da porta, ownership/política de versionamento da boundary de transporte e
sua relação com adapters, sem definir codec/schema local; delimitar a matriz de
suporte inicial.

## Critérios de aceitação

ADRs propostos aceitos/substituídos; `air-java` e Java 21 fixados; CFG-FIRST e seus
oráculos delimitados; regra de controle local não achatada; revisão humana separada.

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

[WORK-CFG-001 concluído](../history/WORK-CFG-001.md), com fontes verificadas,
challenge, comandos/exit codes e escopo negativo. Nenhum perfil recebeu claim e
nenhum item de implementação foi iniciado.
