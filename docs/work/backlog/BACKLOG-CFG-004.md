# BACKLOG-CFG-004 — Fixtures AIR JSON e adapter de transporte

**Estado:** `planned`. **Fase:** `mvp`. **Autorização:** backlog não autoriza execução.
Dependências: BACKLOG-CFG-002, BACKLOG-CFG-003. Não bloqueia BACKLOG-CFG-005.

## Problema e objetivo observável

Permitir leitura de fixtures AIR em arquivo sem contaminar `air-java`, porta ou
kernel, depois que existir binding normativo upstream.

## Escopo e estratégia

Consumir o binding JSON versionado pelo `analysis-ir`; implementar reader/codec em
infraestrutura e fixtures closed/malformed/invalid/unsupported; manter builders de
teste em memória. Se o binding ainda não existir, registrar dependência upstream em
vez de inventar schema local ou serializar records automaticamente.

## Critérios de aceitação

Arquivo e objeto equivalente materializam a mesma `air-java Publication`; todos os
campos do binding são tratados explicitamente; erro de I/O não é `INVALID_IR`;
exemplos `.air` e `cobol-semantic-product.json` não são tratados como AIR JSON;
kernel compila/testa sem classes de adapter.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-008, EVAL-CFG-013 e EVAL-CFG-014. Vincular invariantes
específicos na promoção. Ver [catálogo](../../evals/catalog.md) e
[invariantes](../../architecture/invariants.md). Nenhum expected CFG é gerado pelo
builder ou pelo codec.

## Fronteiras e extensibilidade

`analysis-ir` possui/versiona o binding; `air-java` permanece transport-independent;
o reader depende da porta/modelo, nunca o contrário. Novo formato substitui adapter
sem alterar `BuildCfg`.

## Discovery, checkpoints e handoff

Promover somente após autorização e confirmação do binding upstream, com paths,
schema/revisão e testes negativos concretos. Parar para review antes de CLI ou
integração ampla.

## Fora de escopo

Não definir binding AIR unilateralmente neste repo, modificar `analysis-ir`,
consumir Semantic Product, inferir controle no codec ou tornar arquivo prerequisite
de CFG-FIRST/MVP-CFG-01.

## Evidência de conclusão

Binding pinado, testes de round-trip/equivalência e erros, gates de arquitetura e
integração, comandos/exit codes e review. Até lá permanece planejado.
