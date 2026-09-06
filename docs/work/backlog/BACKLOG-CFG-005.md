# BACKLOG-CFG-005 — CFG-FIRST: Entry, Return e normal exit

**Estado:** `completed` em [WORK-CFG-005](../history/WORK-CFG-005.md). **Fase:** `mvp`.
**Autorização:** implementação explícita no pedido de 06/09/2026, apenas CFG-FIRST
e novo PR para review humano sem merge.
Dependências: BACKLOG-CFG-002, BACKLOG-CFG-003.

## Problema e objetivo observável

Produzir o primeiro CFG de uma `air-java Publication` em memória com a menor
semântica executável: Entry → Sequence(`Return`) → normal exit.

## Escopo e estratégia

TDD a partir de EVAL-CFG-025. Executar `AirValidator`, preflight do slice, índice
namespaced de Unit/Entry/Sequence, uma Sequence por nó e saída normal correlacionada
por PublicationId, UnitId e EntryId/entry scope. Inventariar todas as Sequences,
inclusive sem predecessor, sem usar posição física para criar edges.

## Critérios de aceitação

Entry/initialLabel válido alcança seu nó; `Return` deriva normal exit e nunca
fallthrough; outra Sequence posterior não recebe aresta; permutação física não muda
transições; label pendente é `INVALID_IR`; Sequence sem terminador não é reparada;
entries/exits não são fundidos; AIR permanece imutável e correlacionada.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-007, EVAL-CFG-024 e EVAL-CFG-025. Vincular invariantes
específicos na promoção. Expected manual/independente precede builder; um mutante que
adiciona fallthrough após `Return` deve falhar.

## Fronteiras e extensibilidade

`BuildCfg` recebe a `Publication` do `air-java`. Nenhum nome COBOL, transporte ou
callback entra no kernel. Índices derivados pertencem ao CFG e não alteram a AIR.
Capability fora do slice é recusada/fallback explícito, nunca ignorada.

## Discovery, checkpoints e handoff

Promover somente após os itens de foundation concluídos e autorização explícita.
Observar RED, implementar o caso mínimo, GREEN, refatorar, challenge e parar para
review. Não consumir automaticamente o slice linear seguinte.

## Fora de escopo

Sem `jump`, `halt`, branch, IF, múltiplas instructions lineares, JSON, arquivo, CLI,
lowerer, dataflow, leader detection ou coalescing.

## Evidência de conclusão

EVAL-CFG-025 implemented: 19 testes semânticos, oracle independente anterior ao
builder, mutante de fallthrough morto e metamorfismos. Architecture/semantic/fast
PASS, clean test/verify com 37 testes; CI Temurin 21 verde. Histórico registra
comandos/exit codes, limites e challenge. CFG-FIRST implementado, nenhum perfil AIR
completo. Entrega em novo PR para review humano, sem merge nem próximo backlog.
