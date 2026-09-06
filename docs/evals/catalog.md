# Catálogo de evals

**Todos planejados; nenhum teste Java implementado ou executado neste ZIP.** Metadados verificáveis em [catalog.json](catalog.json).

## EVAL-CFG-001 — Integridade da Publication

Rejeitar ID duplicado, target interno pendente, terminador ausente/duplo e mistura de revisões. Distinguir inventário zero de indisponível.

Oráculos upstream: O-29-STRUCT, O-30-STRUCT, O-31-STRUCT, O-32-STRUCT. Invariantes: INV-CFG-003, INV-CFG-004, INV-CFG-005, INV-CFG-023. Estado: `planned`.

## EVAL-CFG-002 — Fluxo linear

Uma sequência com operações comuns e jump/return explícitos; preservar ordem e todas as ocorrências.

Oráculos upstream: O-01-STRUCT, O-09-STRUCT, O-10-STRUCT, O-18-STRUCT. Invariantes: INV-CFG-005, INV-CFG-006, INV-CFG-008. Estado: `planned`.

## EVAL-CFG-003 — Diamond

Dois ramos e continuação comum; o predicate deve ser `known(bool)`, sem exigir seu
valor. `unknown(known(bool))` preserva ambos; `unknown_type` é inválido.

Oráculos upstream: O-02-STRUCT, O-74-STRUCT. Invariantes: INV-CFG-005,
INV-CFG-007, INV-CFG-010, INV-CFG-029. Estado: `planned`.

## EVAL-CFG-004 — Ramos vazios, nested e destino igual

False pode ir diretamente ao join; nested com terminações próprias; TRUE/FALSE preservados mesmo com target igual.

Oráculos upstream: O-03-STRUCT, O-27. Invariantes: INV-CFG-007, INV-CFG-008, INV-CFG-012. Estado: `planned`.

## EVAL-CFG-005 — Saídas distintas

Return e halt não têm fallthrough; nenhum ramo terminante reconverge por ordem textual.

Oráculos upstream: O-04-STRUCT, O-18-STRUCT, O-19-STRUCT. Invariantes: INV-CFG-008, INV-CFG-019. Estado: `planned`.

## EVAL-CFG-006 — Invoke por outcomes

Normal é possível; exceções, halt, diverge e restante não somem; resultados normais não contaminam outros outcomes.

Oráculos upstream: O-20-STRUCT, O-21-STRUCT, O-22-STRUCT. Invariantes: INV-CFG-009, INV-CFG-010, INV-CFG-012. Estado: `planned`.

## EVAL-CFG-007 — Isolamento Clean Architecture

Kernel compila/executa sem adapters, filesystem, serialização ou frontend; dependência proibida é detectada.

Oráculos upstream: propriedade arquitetural local. Invariantes: INV-CFG-001, INV-CFG-002, INV-CFG-026. Estado: `planned`.

## EVAL-CFG-008 — Mesmo caso de uso por arquivo e memória

Duas vias de ingresso produzem grafo e metadados semanticamente equivalentes sob mesmas opções.

Oráculos upstream: O-66. Invariantes: INV-CFG-002, INV-CFG-003, INV-CFG-023, INV-CFG-026. Estado: `planned`.

## EVAL-CFG-009 — Extensão sem editar orquestrador

Registro de capability sintética por seam; duplicata rejeitada; fallback uma vez; não suportado não vira vazio.

Oráculos upstream: O-47-STRUCT, O-48-STRUCT. Invariantes: INV-CFG-014, INV-CFG-022. Estado: `planned`.

## EVAL-CFG-010 — Dispatch

Cases e default, targets repetidos com condições próprias, duplicatas semânticas inválidas e seletor fora da tabela.

Oráculos upstream: O-05-STRUCT. Invariantes: INV-CFG-007. Estado: `planned`.

## EVAL-CFG-011 — Ciclos e múltiplas entradas

Pré/pós-teste, back-edge, self-loop, ciclo irreducível e conteúdo acessível por outra Entry; sem topological sort obrigatório.

Oráculos upstream: O-06-STRUCT, O-07-STRUCT. Invariantes: INV-CFG-005, INV-CFG-006, INV-CFG-019. Estado: `planned`.

## EVAL-CFG-012 — Controle aberto

Scope com label interior, known+remainder, nenhuma falsa prova de unreachable ou completude.

Oráculos upstream: O-33-STRUCT, O-34-STRUCT. Invariantes: INV-CFG-012, INV-CFG-013, INV-CFG-020. Estado: `planned`.

## EVAL-CFG-013 — Provenance e cardinalidade

IDs namespaced, origin derivada, clones distintos, inventário sem predecessor e operação não suportada mantida.

Oráculos upstream: O-41-STRUCT, O-42-STRUCT, O-43-STRUCT, O-45-STRUCT,
O-46-STRUCT, O-49. Invariantes: INV-CFG-004, INV-CFG-006, INV-CFG-011. Estado: `planned`.

## EVAL-CFG-014 — Determinismo e metamorfismo

Permutation de sequences, alpha-renaming de IDs, split com jump e mudança só de display não alteram observações correlacionadas.

Oráculos upstream: O-09-STRUCT, O-10-STRUCT, O-44-STRUCT, O-66. Invariantes: INV-CFG-005, INV-CFG-021, INV-CFG-023. Estado: `planned`.

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

Todos os labels de S sem RD; `unknown(known(label(S)))` conserva esse universo;
`unknown_type` e label fora de S são inválidos. Refinamento opcional exige revisão e
não muta a IR.

Oráculos upstream: O-61, O-62, O-63. Invariantes: INV-CFG-018, INV-CFG-023. Estado: `planned`.

## EVAL-CFG-020 — Integração modular e atualização de contrato

Mesmo modelo IR e porta, sem serialização intermediária; capabilities/revisões incompatíveis diagnosticadas.

Oráculos upstream: O-31-STRUCT, O-66, O-67, O-68. Invariantes: INV-CFG-001, INV-CFG-002, INV-CFG-023, INV-CFG-024, INV-CFG-026. Estado: `planned`.

## EVAL-CFG-021 — TypeRef e predicate booleano

Preservar `known(Type)` e `unknown_type(UncertaintyId)` em entidades, usos,
assinaturas e envelopes. Aceitar `branch unknown(known(bool))` com TRUE/FALSE e
rejeitar predicate `unknown_type`, sem coerção, default ou consulta ao frontend.

Oráculos upstream: O-69-STRUCT a O-81-STRUCT. Invariantes: INV-CFG-003,
INV-CFG-004, INV-CFG-027, INV-CFG-029. Estado: `planned`.

## EVAL-CFG-022 — sameDomain e DomainProofScope

Preservar `Premise`, sujeitos, autoridade, origem e escopo; validar fechamento,
sites estáticos e interseções. Não unificar lacunas, igualar valores nem depender de
reachability, CFG ou ativações dinâmicas.

Oráculos upstream: O-77-STRUCT, O-82-STRUCT a O-85-STRUCT. Invariantes:
INV-CFG-004, INV-CFG-023, INV-CFG-027, INV-CFG-028. Estado: `planned`.

## EVAL-CFG-023 — Raise e saída excepcional

`raise(tag, values)` avalia e preserva tag, valores, identidade e origem, encerra a
ativação por saída excepcional e não possui fallthrough local nem saída normal. O
destino vem da interação invocadora ou é a saída excepcional raiz.

Oráculos upstream: requisito direto de `AIR-STRUCTURE@2`, sem ID de oráculo
enumerado específico. Invariantes: INV-CFG-004, INV-CFG-008, INV-CFG-011,
INV-CFG-019. Estado: `planned`.

## EVAL-CFG-024 — Fundação mínima da Analysis IR V2

O modelo em memória distingue `Known(Type)` de `UnknownType(UncertaintyId)` e
representa `Premise`, `sameDomain`, sujeitos, autoridade, origem e
`DomainProofScope` como conceitos fechados. Não usa `Optional<Type>` nem exige
signatures, choices, regiões, invokes/envelopes, regras contextuais completas de
aplicabilidade/interseção ou qualquer cálculo de CFG/dataflow.

Oráculos upstream: foundation local de representabilidade; a bateria normativa
O-69-STRUCT–O-85-STRUCT pertence ao EVAL-CFG-021/EVAL-CFG-022. Invariantes:
INV-CFG-003, INV-CFG-004, INV-CFG-026, INV-CFG-027, INV-CFG-028. Estado: `planned`.
