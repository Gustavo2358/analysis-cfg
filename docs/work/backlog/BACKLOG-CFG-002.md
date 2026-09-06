# BACKLOG-CFG-002 — Bootstrap Java/Maven e modelo IR mínimo compartilhável

**Estado:** `planned`. **Fase:** `foundation`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-001.

## Problema e objetivo observável

Criar a fundação tipada da Analysis IR 2.0.0 e build reprodutível, sem algoritmo CFG.

## Escopo e estratégia

Implementar apenas o modelo aprovado suficiente ao MVP com evolução/unsupported
explícitos; `TypeRef = Known(Type) | UnknownType(UncertaintyId)`, `Premise`,
`sameDomain` e `DomainProofScope` pertencem à fundação desde o primeiro modelo.
Java 17, separação de módulos, construtores/validações e testes TDD.

## Critérios de aceitação

Não há classes IR privadas duplicadas nem `Optional<Type>` substituindo `TypeRef`;
kernel depende só do contrato; namespaces, imutabilidade, terminador único, escopos
de prova e lacunas `TYPE_UNKNOWN` são testados; build não passa com zero testes.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-007, EVAL-CFG-013, EVAL-CFG-020, EVAL-CFG-021,
EVAL-CFG-022. Vincular invariantes específicos na promoção para work
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

Sem frontend/ANTLR, solver CFG, dataflow, CLI ou APIs cloud. Modelo não acoplado ao
JSON e sem API antecipada além das formas exigidas pela V2.

## Evidência de conclusão

Revisão/commit, diff explicado, testes/gates com exit codes, falsificação adversarial,
capabilities/precisão realmente entregues e limitações. Até existir essa evidência,
o estado permanece planejado e nenhum perfil recebe claim por antecipação.
