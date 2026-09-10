# Invariantes do CFG

Regras de projeto derivadas da direção aprovada e da IR fixada. Não substituem os
invariantes I-01–I-54 upstream. Cada alteração liga regra a eval e work item.

## INV-CFG-001 — Fronteira IR

O consumidor deriva controle apenas de fatos IR tipados; não consulta frontend, COBOL ou texto de exibição.

## INV-CFG-002 — Infraestrutura externa

Domínio/aplicação não conhecem I/O, transporte ou forma de execução; adapters usam a mesma porta.

## INV-CFG-003 — Publicação imutável

Não modificar Publication nem completar semântica por callback ao produtor. O CFG
pode reter referências imutáveis e índices próprios, mas não deep-copia toda a AIR
por padrão nem exige JSON para lifetime.

## INV-CFG-004 — Identidades fechadas

Usar namespace completo e domínio correto; referência interna pendente é INVALID_IR.

## INV-CFG-005 — Sequence explícita

Uma Sequence tem um único terminador final; nenhuma execução é inferida pela ordem física das sequences.

## INV-CFG-006 — Inventário íntegro

Preservar toda ocorrência coberta e pontos correlacionados; capability não limita cardinalidade.

## INV-CFG-007 — Alternativas rotuladas

Manter TRUE/FALSE, cases e outcomes mesmo com destinos iguais; default de dispatch é explícito.

## INV-CFG-008 — Sem reconvergência artificial

Return, raise, halt ou divergência não ganham fallthrough/join por proximidade.

## INV-CFG-009 — Invocações honestas

Normal significa retorno possível; preservar outcomes declarados e não fabricar pureza ou retorno obrigatório.

## INV-CFG-010 — Separação de análise

Construir CFG não executa RD/PV, storage inference ou target resolution.

## INV-CFG-011 — Origem preservada

Mapear operações/pontos/origins; nós sintéticos são derivados, sem span escrito inventado.

## INV-CFG-012 — Precisão dimensional

Validade, inventário, controle, valores, efeitos e capacidades suportadas permanecem distinguíveis.

## INV-CFG-013 — Controle aberto

Restante aberto influencia todo o ControlScope, inclusive reentrada; não é apenas sink terminal.

## INV-CFG-014 — Extensão única

Interpretar extensão, redução ou fallback uma única vez; sem intérprete/envelope, incompatibilidade explícita.

## INV-CFG-015 — Retorno contextual

Retorno preciso exige contexto correspondente; projeção plana não prova caminho realizável.

## INV-CFG-016 — Local boundary

Casar somente a porta do frame do topo; default não altera pilha; IDs de porta não são ocorrência.

## INV-CFG-017 — Sem unwind implícito

Jump não remove frames; resume/unwind inválidos preservam suas saídas excepcionais.

## INV-CFG-018 — Indireção limitada

CFG inicial usa todo o universo de labels contratual sem depender de valores propagados.

## INV-CFG-019 — Entradas e saídas

Não fundir entries, inicializações, saída normal, excepcional e término
silenciosamente. Normal exit preserva `UnitId` e o `EntryId`/entry scope pertinente;
não existe exit global único implícito para a Publication.

## INV-CFG-020 — Limites explícitos

CORE-SIZE-001: tamanho/recursos não justificam corte, rejeição ou precisão reduzida.
Convergência e abstração contextual são semânticas explícitas; falha externa não é
resultado semântico. [ADR-0014](decisions/ADR-0014.md) supersede a política anterior.

## INV-CFG-021 — Oracle independente

Esperado deriva do contrato antes da implementação; o builder não gera seu próprio golden.

## INV-CFG-022 — Algoritmo geral

Regra de produção não depende de corpus, nomes, primeiro/último match ou profundidade incidental.

## INV-CFG-023 — Revisões consistentes

Resultado identifica Publication/revisão, opções e premissas; não misturar IDs entre revisões.

## INV-CFG-024 — Conformidade não inflada

Só declarar perfil inteiro quando suas obrigações no papel alegado forem verificadas.

## INV-CFG-025 — Autorização e evidência

Backlog não autoriza; gate indisponível/não executado nunca recebe PASS de produto.

## INV-CFG-026 — Modelo compartilhável

Tipos AIR e `AirValidator` vêm do `air-java` fixado; não duplicar classes/validator
nem criar payload paralelo. Transporte não invade o modelo compartilhado nem a
porta `BuildCfg`.

## INV-CFG-027 — Conhecimento de tipo preservado

Representar e preservar `TypeRef` como `known(Type)` ou
`unknown_type(UncertaintyId)`. Tipo desconhecido não apaga entidade, ocorrência,
storage, controle ou dependência e não satisfaz precondição de domínio concreto.

## INV-CFG-028 — Prova de domínio não é dataflow

Preservar `Premise`, sujeitos, autoridade, origem, `sameDomain` e
`DomainProofScope`; validar fechamento e aplicabilidade pelos sites estáticos. A
prova não unifica lacunas, não iguala valores e não depende de CFG, reachability ou
ativação dinâmica.

## INV-CFG-029 — Predicate booleano explícito

`branch` aceita valor desconhecido somente com `known(bool)` e conserva TRUE/FALSE.
`unknown_type` não é booleano e não recebe default, coerção ou inferência do uso.

Fonte: [Analysis IR e inspirações](../sources/index.md). Evidência: [evals](../evals/index.md).

## INV-CFG-030 — Sessão e solver separados

Sessão/index downstream de BuildCfg; solver neutro e estado opaco; DAG e imports AIR diretos conforme ADR-0010.

## INV-CFG-031 — Propagação incremental contextual

Primeira publicação/mudança propaga raízes por arestas afetadas; acumulador inalterado não enfileira. Entry, first reach, dual backward e self-loop conforme ADR-0011. Recomposição só oracle test-only.

## INV-CFG-032 — Locations e valores honestos

Cell compartilhada e disjunção explícita; missing key desconhecido, bottom separado; strong write e open semântico conforme ADR-0012/ADR-0014; todos os candidatos finitos preservados.

## INV-CFG-033 — Compartilhamento com custo verificável

H4 protege propriedades de estado e métricas/retention, não Patricia/FIFO; k=8 foi removido por CORE-SIZE-001. Probes/challenges por Wave conforme ADR-0013.

## INV-CFG-034 — Observações estáveis e alcance do claim

Batch e indexed dispatch sem I×K ou replays por site; model scope separado da abertura de fonte. CP4E não prova exaustividade; resultado derivado mantém restante efetivo.


## Post-CP5 RESOURCE_LIMIT compatibility

CP5 W1–W5 are APPROVED / MERGED at 4229ec1cfd9c1d9f9e851f3cabe6993b4d4ed9b8.
WORK-CFG-029 is a separate POST_CP5_COMPATIBILITY_REMEDIATION. Current air-java pin
17029898fd0ee8fabcaaae89f7260148633d4b12 separates operational RESOURCE_LIMIT from
specific VALIDATION_LIMIT and generic INCOMPLETE_VALIDATION. Historical CP5 evidence
above retains its original pins/defaults. The new default codec no longer has a
16 MiB ceiling; explicit operational budgets still fail without any semantic result.
See [the current preflight contract](resource-limit-preflight.md).
