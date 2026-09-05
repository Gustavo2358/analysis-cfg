# BACKLOG-CFG-013 — Discovery de representação e matching local

**Estado:** `planned`. **Fase:** `local-discovery`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-009, BACKLOG-CFG-010.

## Problema e objetivo observável

Escolher algoritmo/representação de controle local sem contaminar core com COBOL.

## Escopo e estratégia

Ler IR §05.7, X-23/24 e O-56–60; estudar literatura de caminhos realizáveis, pushdown/representação contextual; comparar custo, recursão, soundness e fallback.

## Critérios de aceitação

ADR especifica diferença entre projeção plana e consulta contextual, regras push/pop/guard, terminação/limites e contraexemplos; fixtures de curto/longo/topo previstas; parar para review.

## Evals e invariantes

EVAL-CFG-016, EVAL-CFG-017, EVAL-CFG-018. Vincular invariantes específicos na promoção para work
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

Sem implementar solução especulativa, criar bound de profundidade implícito, alterar IR ou estudar todo dataflow.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
