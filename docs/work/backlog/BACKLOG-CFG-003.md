# BACKLOG-CFG-003 — Fronteiras Clean e seam semântico de extensões

**Estado:** `completed` em [WORK-CFG-003](../history/WORK-CFG-003.md). **Fase:**
`foundation`. **Autorização executada:** implementação explicitamente autorizada em
06/09/2026 somente para este checkpoint, até PR aberto sem merge.
Dependências: BACKLOG-CFG-002.

## Problema e objetivo observável

Fechar a porta `BuildCfg` e proteger a arquitetura antes do primeiro builder, com
composição pequena e extensão controlada.

## Escopo e estratégia

Definir `BuildCfg(air-java Publication, BuildOptions) → CfgBuildResult`, suas opções
e seu envelope de boundary; preservar fatos V2 sem misturá-los ao transporte; criar
registry explícito por capability/version, negociação de presença/incompatibilidade
e provas de dependência bytecode com uma extensão sintética.

O desenvolvimento refinou a frase inicial que incluía contratos de transição
tipados: destino local, saída, open control e ação contextual foram deliberadamente
adiados até o primeiro slice semântico concreto. Esses tipos exigem uma operação de
interpretação e não são necessários para fechar esta foundation; criá-los aqui seria
abstração especulativa.

## Critérios de aceitação

Não existe segundo modelo semântico de entrada. Adicionar intérprete de teste não
exige editar orquestrador; registro ambíguo falha; I/O em core e imports frontend
são rejeitados; gate architecture se torna real com casos negativos.

## Evals e invariantes

EVAL-CFG-007, EVAL-CFG-024 e EVAL-CFG-027. EVAL-CFG-009 permanece `planned` para
interpretação/fallback sob seus oráculos upstream. Vincular invariantes específicos
na promoção para work item. Ver [catálogo](../../evals/catalog.md) e
[invariantes](../../architecture/invariants.md).
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

Não implementar local frames de produção, framework genérico de plugins, DI por
reflection nem factories por conveniência. Perfis `@2` não renomeiam extensões `@1`.

## Evidência de conclusão

[WORK-CFG-003](../history/WORK-CFG-003.md) registra contrato compilado, 18 testes,
gate arquitetural ampliado, RED → GREEN, falsificações e validação em Maven repo
isolado. EVAL-CFG-027 foi implementado para o registry local; EVAL-CFG-009 permanece
`planned`. Nenhum contrato de alternativa de controle, CFG, capability de controle
real ou perfil AIR foi declarado implementado.
