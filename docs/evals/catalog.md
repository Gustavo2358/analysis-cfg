# Catálogo de evals

**Todos planejados; nenhum teste Java implementado ou executado neste ZIP.** Metadados verificáveis em [catalog.json](catalog.json).

## EVAL-CFG-001 — Integridade da Publication

Rejeitar ID duplicado, target interno pendente, terminador ausente/duplo e mistura de revisões. Distinguir inventário zero de indisponível.

Oráculos upstream: O-29, O-30, O-31, O-32. Invariantes: INV-CFG-003, INV-CFG-004, INV-CFG-005, INV-CFG-023. Estado: `planned`.

## EVAL-CFG-002 — Fluxo linear

Uma sequência com operações comuns e jump/return explícitos; preservar ordem e todas as ocorrências.

Oráculos upstream: O-01, O-09, O-10, O-18. Invariantes: INV-CFG-005, INV-CFG-006, INV-CFG-008. Estado: `planned`.

## EVAL-CFG-003 — Diamond

Dois ramos e continuação comum; nenhuma aresta entre irmãos nem avaliação de predicado necessária.

Oráculos upstream: O-02. Invariantes: INV-CFG-005, INV-CFG-007, INV-CFG-010. Estado: `planned`.

## EVAL-CFG-004 — Ramos vazios, nested e destino igual

False pode ir diretamente ao join; nested com terminações próprias; TRUE/FALSE preservados mesmo com target igual.

Oráculos upstream: O-03, O-27. Invariantes: INV-CFG-007, INV-CFG-008, INV-CFG-012. Estado: `planned`.

## EVAL-CFG-005 — Saídas distintas

Return e halt não têm fallthrough; raise preserva tag; nenhum ramo terminante reconverge por ordem textual.

Oráculos upstream: O-04, O-18, O-19. Invariantes: INV-CFG-008, INV-CFG-019. Estado: `planned`.

## EVAL-CFG-006 — Invoke por outcomes

Normal é possível; exceções, halt, diverge e restante não somem; resultados normais não contaminam outros outcomes.

Oráculos upstream: O-20, O-21, O-22. Invariantes: INV-CFG-009, INV-CFG-010, INV-CFG-012. Estado: `planned`.

## EVAL-CFG-007 — Isolamento Clean Architecture

Kernel compila/executa sem adapters, filesystem, serialização ou frontend; dependência proibida é detectada.

Oráculos upstream: propriedade arquitetural local. Invariantes: INV-CFG-001, INV-CFG-002, INV-CFG-026. Estado: `planned`.

## EVAL-CFG-008 — Mesmo caso de uso por arquivo e memória

Duas vias de ingresso produzem grafo e metadados semanticamente equivalentes sob mesmas opções.

Oráculos upstream: O-66. Invariantes: INV-CFG-002, INV-CFG-003, INV-CFG-023, INV-CFG-026. Estado: `planned`.

## EVAL-CFG-009 — Extensão sem editar orquestrador

Registro de capability sintética por seam; duplicata rejeitada; fallback uma vez; não suportado não vira vazio.

Oráculos upstream: O-47, O-48. Invariantes: INV-CFG-014, INV-CFG-022. Estado: `planned`.

## EVAL-CFG-010 — Dispatch

Cases e default, targets repetidos com condições próprias, duplicatas semânticas inválidas e seletor fora da tabela.

Oráculos upstream: O-05. Invariantes: INV-CFG-007. Estado: `planned`.

## EVAL-CFG-011 — Ciclos e múltiplas entradas

Pré/pós-teste, back-edge, self-loop, ciclo irreducível e conteúdo acessível por outra Entry; sem topological sort obrigatório.

Oráculos upstream: O-06, O-07. Invariantes: INV-CFG-005, INV-CFG-006, INV-CFG-019. Estado: `planned`.

## EVAL-CFG-012 — Controle aberto

Scope com label interior, known+remainder, nenhuma falsa prova de unreachable ou completude.

Oráculos upstream: O-33, O-34. Invariantes: INV-CFG-012, INV-CFG-013, INV-CFG-020. Estado: `planned`.

## EVAL-CFG-013 — Provenance e cardinalidade

IDs namespaced, origin derivada, clones distintos, inventário sem predecessor e operação não suportada mantida.

Oráculos upstream: O-41, O-42, O-43, O-45, O-46, O-49. Invariantes: INV-CFG-004, INV-CFG-006, INV-CFG-011. Estado: `planned`.

## EVAL-CFG-014 — Determinismo e metamorfismo

Permutation de sequences, alpha-renaming de IDs, split com jump e mudança só de display não alteram observações correlacionadas.

Oráculos upstream: O-09, O-10, O-44, O-66. Invariantes: INV-CFG-005, INV-CFG-021, INV-CFG-023. Estado: `planned`.

## EVAL-CFG-015 — Escala estrutural

Chains/diamonds/cycles/dispatch crescentes; índices únicos e custo proporcional a input+output; sem truncamento.

Oráculos upstream: O-49, O-50. Invariantes: INV-CFG-006, INV-CFG-020, INV-CFG-022. Estado: `planned`.

## EVAL-CFG-016 — Retorno ao callsite correto

Duas local.invoke da mesma entry com resume distintos; nenhuma continuação de C2 durante contexto C1.

Oráculos upstream: O-56. Invariantes: INV-CFG-015. Estado: `planned`.

## EVAL-CFG-017 — Portas e topo

Trecho curto/longo compartilhado; default com pilha vazia; mismatch do topo não busca frame externo.

Oráculos upstream: O-57, O-58. Invariantes: INV-CFG-015, INV-CFG-016. Estado: `planned`.

## EVAL-CFG-018 — Resume, unwind e recursão

Underflow é exceção; jump não desempilha; unwind remove n exatos; recursão não causa enumeração infinita silenciosa.

Oráculos upstream: O-59, O-60. Invariantes: INV-CFG-015, INV-CFG-017, INV-CFG-020. Estado: `planned`.

## EVAL-CFG-019 — Controle indireto inicial

Todos os labels de S sem RD; label fora de S inválido; refinamento opcional com revisão, sem mutar IR.

Oráculos upstream: O-61, O-62, O-63. Invariantes: INV-CFG-018, INV-CFG-023. Estado: `planned`.

## EVAL-CFG-020 — Integração modular e atualização de contrato

Mesmo modelo IR e porta, sem serialização intermediária; capabilities/revisões incompatíveis diagnosticadas.

Oráculos upstream: O-31, O-66, O-67, O-68. Invariantes: INV-CFG-001, INV-CFG-002, INV-CFG-023, INV-CFG-024, INV-CFG-026. Estado: `planned`.
