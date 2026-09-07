# Catálogo de evals

**A fundação implementa EVAL-CFG-007, EVAL-CFG-024 e o eval local
EVAL-CFG-027; EVAL-CFG-025 implementa CFG-FIRST e EVAL-CFG-028 prova o slice
linear/Jump/Halt; EVAL-CFG-029 prova Branch e satisfaz EVAL-CFG-004.** Metadados
verificáveis em [catalog.json](catalog.json).

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

Oráculos upstream: O-03-STRUCT, O-27. Invariantes: INV-CFG-007, INV-CFG-008, INV-CFG-012. Estado: `implemented`.
Provas no 029: emptyFalseArmUsesExplicitJoinWithoutSyntheticNodesOrOperations
(O-03-STRUCT), unknownBooleanRetainsPredicateDependenciesReasonTypeAndOriginByIdentity
(O-27: leitura explícita, dois destinos, nenhuma escrita/chamada implícita),
nestedBranchesUseTheirOwnExplicitDestinations e sameDestinationPreservesTwoAlternativesAndThePredicate.

## EVAL-CFG-005 — Saídas distintas

Return e halt não têm fallthrough; nenhum ramo terminante reconverge por ordem textual.

Oráculos upstream: O-04-STRUCT, O-18-STRUCT, O-19-STRUCT. Invariantes: INV-CFG-008, INV-CFG-019. Estado: `planned`.

## EVAL-CFG-006 — Invoke por outcomes

Normal é possível; exceções, halt, diverge e restante não somem; resultados normais não contaminam outros outcomes.

Oráculos upstream: O-20-STRUCT, O-21-STRUCT, O-22-STRUCT. Invariantes: INV-CFG-009, INV-CFG-010, INV-CFG-012. Estado: `planned`.

## EVAL-CFG-007 — Isolamento Clean Architecture

Kernel compila/executa sem adapters, filesystem, serialização ou frontend; dependência proibida é detectada.

Oráculos upstream: propriedade arquitetural local. Invariantes: INV-CFG-001,
INV-CFG-002, INV-CFG-026. Estado: `implemented` na fundação, com evidência em
[WORK-CFG-002](../work/history/WORK-CFG-002.md).

## EVAL-CFG-008 — Mesmo caso de uso por arquivo e memória

Duas vias de ingresso produzem grafo e metadados semanticamente equivalentes sob mesmas opções.

Oráculos upstream: O-66. Invariantes: INV-CFG-002, INV-CFG-003, INV-CFG-023, INV-CFG-026. Estado: `planned`.

## EVAL-CFG-009 — Extensão sem editar orquestrador

Registro de capability sintética por seam; duplicata rejeitada; fallback uma vez; não suportado não vira vazio.

Oráculos upstream: O-47-STRUCT, O-48-STRUCT. Interpretação/fallback e representação
executável exigem slice semântico posterior. Invariantes: INV-CFG-014,
INV-CFG-022. Estado: `planned`; O-47-STRUCT e O-48-STRUCT não foram reivindicados
pela foundation.

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

## EVAL-CFG-024 — Boundary compartilhada do air-java

O kernel compila contra a `Publication` e o `AirValidator` do `air-java` fixado,
sem declarar cópias locais de tipos AIR. O preflight preserva diagnósticos
estruturais e distingue a versão `0.1.0-SNAPSHOT` da biblioteca da AIR 2.0.0; não
transforma validação AIR em claim de conformidade CFG.

Oráculos upstream: boundary física local. A bateria normativa
O-69-STRUCT–O-91-STRUCT permanece obrigação futura do perfil; este smoke não
reivindica nenhum desses oráculos. Invariantes: INV-CFG-003, INV-CFG-004,
INV-CFG-026, INV-CFG-027, INV-CFG-028. Estado: `implemented` na fundação, com
evidência em [WORK-CFG-002](../work/history/WORK-CFG-002.md).

## EVAL-CFG-025 — CFG-FIRST: Entry, Return e normal exit

Uma `air-java Publication` válida em memória projeta Entry/initialLabel para o nó
da Sequence terminada por `Return` e para normal exit correlacionado por Publication,
Unit e Entry scope. Missing label ou terminador ausente não são reparados; `Return`
não cai na Sequence fisicamente seguinte; permutação não muda transições; Sequence
sem predecessor permanece inventariada. O oracle é escrito independentemente do
builder. `CFG-FIRST` não implementa `Halt`.

Oráculos upstream: O-18-STRUCT, O-30-STRUCT. Invariantes:
INV-CFG-003, INV-CFG-004, INV-CFG-005, INV-CFG-006, INV-CFG-008, INV-CFG-019,
INV-CFG-021, INV-CFG-023, INV-CFG-026. Estado: `implemented`, com 17 testes em
[EvalCfg025Test](../../cfg-kernel/src/test/java/io/github/gustavo2358/analysis/cfg/domain/EvalCfg025Test.java)
e seleção obrigatória no semantic gate.

## EVAL-CFG-026 — E2E mínimo Semantic Product → AIR → CFG

Um `cobol-lower` externo e conforme consome `cobol-semantic-product.json`, sem
importar tipos Java do frontend, e produz a `air-java Publication` de CFG-FIRST. As
boundaries JSON→AIR e `Publication`/AIR `Return`→normal exit são verificadas
separadamente. O kernel CFG recebe somente `Publication`; não recebe, reconhece nem
testa GOBACK ou JSON.

Oráculos upstream: O-18-STRUCT, O-66. Invariantes: INV-CFG-001, INV-CFG-002,
INV-CFG-003, INV-CFG-019, INV-CFG-023, INV-CFG-026. Estado: `planned`.

## EVAL-CFG-027 — Registry explícito de capability/version

A composição registra intérpretes explicitamente por capability/version;
duplicata ou conflito falha, ordem não cria prioridade, versões distintas coexistem
e ausência produz incompatibilidade explícita. O eval não executa interpretação,
redução ou fallback.

Oráculos upstream: nenhum; prova arquitetural local. Invariantes: INV-CFG-014 e
INV-CFG-022, limitadas à composição e à incompatibilidade explícita. Estado:
`implemented`, com evidência em
[WORK-CFG-003](../work/history/WORK-CFG-003.md).

## EVAL-CFG-028 — Fluxo linear, Jump e Halt

Instructions ordenadas preservadas; Jump usa LabelId explícito anterior/posterior
e self-loop; Return e Halt sem fallthrough e distintos; órfãs inventariadas;
ordem física irrelevante; activationEntry preservado, inclusive Halt compartilhado.

Oráculos upstream: nenhum; eval local estreito, sem certificar invoke/branch.
Invariantes: INV-CFG-003, INV-CFG-004, INV-CFG-005, INV-CFG-006, INV-CFG-008,
INV-CFG-011, INV-CFG-015, INV-CFG-019, INV-CFG-021. Estado: `implemented`,
22 testes em [EvalCfg028Test](../../cfg-kernel/src/test/java/io/github/gustavo2358/analysis/cfg/domain/EvalCfg028Test.java),
obrigatórios junto aos 17 do 025 no semantic gate.

## Evidência parcial de WORK-CFG-022

EVAL-CFG-001/002/005/013/014 permanecem planned. O 022 prova no seu domínio
instructions ordenadas, Jump/Return/Halt, referências pendentes, namespaces,
provenance, órfãs, permutação, alpha rename, display e split. Isso não conclui:

- 001: todas as formas inválidas e composição de revisões;
- 002: O-01-STRUCT inclui a,b,k do X-01; k é invoke, fora deste slice;
- 005: ramo terminante e continuação de invocador dos cenários upstream;
- 013: todas as observações de interação/inventário parcial exigidas;
- 014: O-66 inclui equivalência de ingressos/transportes ainda não implementada.

EVAL-CFG-025 permanece o oracle estável do CFG-FIRST, com 17 regressões.
As antigas recusas de Jump/Halt/instructions descreviam o limite da implementação
naquele checkpoint e não permanecem executáveis após a expansão legítima do produto.
A cobertura positiva dessas formas pertence exclusivamente ao EVAL-CFG-028, com 22 testes.
Nenhum perfil AIR, performance ou integration foi promovido.

## EVAL-CFG-029 — Branch estrutural e conclusão do MVP-CFG-01

Estado: `implemented`, 25 testes em
[EvalCfg029Test](../../cfg-kernel/src/test/java/io/github/gustavo2358/analysis/cfg/domain/EvalCfg029Test.java).
Eval local sem oráculo upstream amplo atribuído; obrigatório no semantic gate com
025=17 e 028=22. Expected manual de M2–M5, contexto e inventário, enums/records
próprios. Prova TRUE/FALSE, literal true/false sem pruning, unknown(known(bool))
com read preservado, unknown_type/role/targets inválidos, ramo vazio/nested,
Halt/Return terminantes, mesmo destino, permutation, alpha rename, display/origin,
split, duas Entries, órfã e 258 branches; validação interna de endpoints/contexto.
Invariantes: INV-CFG-003/004/005/006/007/008/010/011/015/019/021/022/027/029.

## Reavaliação integral em WORK-CFG-006

- 003 permanece planned: diamond satisfaz O-02-STRUCT; o contracaso predicate
  unknown_type e a variante booleana cobrem apenas parte de O-74-STRUCT. Esse
  oráculo também exige concat/not/read com tipo desconhecido, inclusive provas
  sameDomain, e dependência de tipo desconhecido na variante válida X-33.
- 004 implemented: expectativa completa e O-03-STRUCT/O-27 demonstrados no 029.
- 005 permanece planned: M4 e 025/028/029 provam saídas sem fallthrough e O-04-STRUCT
  no controle suportado. O-19-STRUCT exige chamador/callee com continuação normal;
  Invoke não é implementado e esse cenário não recebe claim por inferência.
- 014 permanece planned: Branch acrescenta permutation/alpha rename/display/split
  correlacionados; O-66/equivalência de ingressos continua sem transporte.

029 é owner do checkpoint local; concluir M1–M5 não conclui esses evals amplos,
AIR-STRUCTURE@2, performance ou integration. 025 permanece byte a byte intacto;
028 mantém 22 métodos e a fixture mista, com adaptação exclusiva do switch e da
recusa obsoleta de Branch, sem absorver testes positivos do 029.

## EVAL-CFG-030 — Política de projeção KNOWN_SUBSET/STRICT

Implementado em WORK-CFG-024 (0A), com 20 métodos em
[EvalCfg030Test](../../cfg-kernel/src/test/java/io/github/gustavo2358/analysis/cfg/domain/EvalCfg030Test.java).
Matriz COMPLETE/PARTIAL/UNAVAILABLE pela porta, subjects tipados de STRICT, grafo Return
manual, coverage items/premises/evidence por identidade, zero inventado e metadados
sem poder semântico. Recusas de terminador órfão, body, capabilities, validação incompleta,
limites e AIR inválida permanecem. Caso parcial misto prova TRUE/FALSE no mesmo destino,
activationEntry múltipla, Return, Halt, self-loop e determinismo. Seleção exata pelo semantic gate.
EVAL-CFG-025 conserva seus 17 métodos: recusa antiga de PARTIAL agora seleciona STRICT;
UNAVAILABLE nos dois modos é provado pelo 030. Oráculos 028/029 permanecem intactos.
Nenhum claim de perfil nem de independência das lacunas para dataflow/reachability.

## EVAL-CFG-031 — Arquivo AIR → CFG JSON e CLI

Planejado em WORK-CFG-026: fluxo real pelo shared AirJson e BuildCfg, golden manual independente, falhas sem publicar output, determinismo e coverage honesta.
