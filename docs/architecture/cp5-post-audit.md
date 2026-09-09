# CP5 — remediações arquiteturais pós-auditoria

Checkpoint `CP5_POST_AUDIT_HARNESS_REMEDIATION`, WORK-CFG-028, branch
`feat/cp5-dataflow-engine`, PR #12 OPEN/DRAFT. Discovery, preparação anterior e B1
estão APPROVED; esta remediação aguarda novo review humano. Nenhuma Wave autorizada.
[Lifecycle](../work/cp5-lifecycle.json) conserva o histórico B1 e a autorização focal.
[Obrigações verificáveis](../evals/cp5/post-audit-contracts.json) complementam H1–H7/R1/R2
sem reabrir host, separação cfg/analysis, algoritmo incremental, H4 ou cinco Waves.
Patricia/radix, FIFO e k=8 continuam experimentais.

Autoridade de findings: `artefatos-e2e/cp5/cp5-architectural-audit.md`, no workspace
irmão, SHA-256 `32d4542adcf31445cf35196a5f6d2496e8e3962c0bfa9d10c26615f6ceea5bc1`.
Foi lida integralmente após handoff/discovery. A auditoria recomenda manter a
arquitetura com remediações focais; a semântica continua subordinada à AIR pinada
`122ce54e1b9ef9b00646f93ece409ca8b63bc933` e ao código confrontado na
[evidência](../work/evidence/WORK-CFG-028/architectural-audit-remediation/validation.md).

<a id="a"></a>

## A — Integridade da projeção recebida

W1 deverá admitir resultados da fronteira BuildCfg suportada, correlacionados ao
mesmo snapshot imutável, e verificar a integridade do profile durante a construção
do índice. `CFG_BUILT` não é certificado de procedência ou completude: seus
construtores são públicos. A política vale também para um resultado manual.
Não basta confiar no status ou na validade individual das arestas sobreviventes.

A decisão é verificar membership por identidade das instâncias canônicas da
Publication, Unit, Entry, Sequence e ocorrência Halt, resolvidas uma vez no índice.
Uma Sequence substituída, inclusive com mesmo label/owner e conteúdo aparentemente
igual, não é a instância-fonte desse snapshot. Correlacionar metadados do resultado,
profile/options, owners, entradas e destinos; rejeitar fonte estrangeira e duplicata.
A sessão deve receber o snapshot esperado por correlação explícita, e não deduzi-lo
somente do ID textual declarado pelo grafo. A origem suportada não é uma credencial
criptográfica nem autoriza ignorar essas verificações.

Verificar o inventário completo de papéis exigido pelo profile core admitido:
um SequenceNode por Sequence, incluindo órfãos; um EntryNode e NormalExit por Entry;
um HaltExit por ocorrência Halt. Para cada Entry, uma transição ENTRY ao label
inicial e as transições de cada Sequence sob essa activationEntry: Jump ao destino,
Branch TRUE e FALSE aos respectivos destinos (mesmo quando iguais), Return ao
NormalExit daquela Entry, Halt ao HaltExit da ocorrência. Nada ausente, extra,
duplicado ou com contexto/destino trocado pode ser admitido como análise precisa.
Completude aqui é fidelidade ao inventário AIR publicado; não fecha CONTROL/PARTIAL
ou prova reachability/fonte completa.

Usar contagens/conjuntos de papéis e lookup das fontes no índice em custo linear no
inventário AIR + CFG recebido, com orçamento de admissão. Isso verifica as
obrigações de projeção; não materializa outro grafo, não repete BuildCfg/CoreCfgProjection,
AirValidator, traversal de fixpoint ou deep comparison de toda AIR. Falta estrutural
é INVALID_INPUT/admission REJECTED, sem solver; profile sem suporte é UNSUPPORTED.
Um limite de admissão deve ser explicitado como executionStatus UNSUPPORTED, admission REJECTED
com motivo ADMISSION_BUDGET,
sem atribuir ANALYSIS_LIMIT a solver que não iniciou.

S11/W1 e seus cinco challenges exigem grafo íntegro aceito e rejeitam missing Branch
edge, missing required Sequence, foreign/replaced Sequence source, foreign
Publication/Entry e duplicated/wrong contextual edge. Hooks ainda indisponíveis;
W1 deverá vincular testes reais da sessão e verificar o custo junto a S1/S4/S8.

<a id="b"></a>

## B — Observação orientada pela direção

| Direção | Âncora estável | Ordem de replay | Trabalho compartilhado |
| --- | --- | --- | --- |
| FORWARD | IN da Sequence | primeira → última operação | união dos prefixos até os pontos solicitados |
| BACKWARD | OUT da Sequence | última → primeira operação | união dos sufixos até os pontos solicitados |

`before(operation)` e `after(operation,outcome)` conservam os mesmos pontos do
programa nas duas direções. Em backward, o estado antes do transfer reverso é
`after`; após aplicar esse transfer, é `before`. Em forward ocorre a sequência
usual inversa. Se houver transfer por outcome, respeitar a fronteira declarada sem
aplicá-lo duas vezes; ponto sem estado posterior admitido permanece UNSUPPORTED_POINT.
Direction muda a reconstrução abstrata, nunca a identidade ou o alcance do ponto.

Oracle manual S12: operações `def X; use X`, transfer test-only de gen/kill,
OUT={} → after use={} → before use={X} = after def → before def={}.
Esse caso discrimina ordem; para isolar âncora, S12 também exige `use Y; def X;
use X`, com IN={Y}, OUT={}, after last use={} e before first use={Y}. Usar IN
como âncora produz Y indevido depois do último use. Nenhum dos dois mutantes pode
ser aceito por comparar somente extremos ou usar fixture com âncoras iguais. W2 implementa essa testemunha
sintética com a análise alternativa backward; W3 a aplica à API de consultas e ao
batching. Não é Liveness de produto. Replay/materialização exigem fixpoint já estável.

<a id="c"></a>

## C — Storage/effects, análise e consumers

As responsabilidades são distintas: storage/effect semantics resolve localizações,
may/must writes e outcomes; a análise/domínio usa isso no preparation/transfer antes
do fixpoint; fact consumers interpretam sites e fatos estáveis depois. O solver
permanece neutro quanto a operações concretas e espécies de dependência.

Um CallResolver, GrbeResolver ou FileResolver não pode reparar posteriormente um
estado propagado com efeitos incorretos. No futuro, `MOVE 'PROGA' TO WS-PGM;
CALL 'ALTERA' USING BY REFERENCE WS-PGM; CALL WS-PGM` não preserva valor fechado
PROGA após a primeira interação sem prova de ausência de escrita. Efeito possível
abre o valor afetado; sobrescrita obrigatória pode eliminar o antigo. BY REFERENCE
não prova que escreveu, e ausência de contrato não prova efeito vazio. AIR 04 §7
materializa assinatura, efeitos/outcomes e premissas; lookup de nomes não os completa.

W3 protege efeitos do slice admitido; W4 protege consumers de fatos e proíbe reparo
ou execução oculta de solver. Invoke não pertence ao CFG core atual: os challenges
`consumer-repairs-stale-state` e `unknown-invoke-treated-as-no-effect` ficam no hook
POST_CP5_INVOKE_EFFECTS, sem Wave CP5 fictícia. CP6 é um CALL dependency slice,
incluindo consumers e quaisquer contratos prévios de Invoke/effects necessários
em AIR/lowering/CFG. Não inserir CALL, FILE, DB2, CICS ou GRBE no solver.

<a id="d"></a>

## D — Limites futuros de GRBE

Logical value não é byte layout. A Cell AIR não declara tamanho físico. Interpretar
“bytes 5–12 de PARM1” como substring(TextValue) exige prova de relação entre valor,
bytes, offset, extent e codec; texto lógico não fornece essa prova. GRBE byte-range
exigirá storage/region/view/codec apropriado ou redução semanticamente justificada
(AIR 03). O profile textual CP5 permanece válido, sem regions nesta entrega.

PossibleValues não relacional pode perder correlação: caminhos (CALL, PROGA) e
(QUERY, PROGB) geram marginais function={CALL,QUERY}, program={PROGA,PROGB}; isso
não demonstra os quatro pares concretos. Candidatos são possibilidades abstratas,
não testemunhos de caminhos (AIR 08 §5). Consumidor que precise correlacionar campos
pode exigir tuple domain, partition/refinement por discriminator ou query relacional
especializada. Nenhum domínio relacional geral entra no CP5. W3/W4 preservam essa
limitação nas consultas/consumers; hooks byte-range e correlação ficam para GRBE.

<a id="e"></a>

## E — Entry e invocação local

`activationEntry` seleciona contexto de ativação; EntryId não é frame de invocação
local nem prova pareamento de retorno. AIR 05 §7 distingue `local.invoke`, portas
de conclusão, resume e frames dentro da mesma ativação. PERFORM/local control terá
evolução própria: a entrada não reinicializa storage ao voltar por back-edge nem
resolve múltiplas continuações locais. Uma análise context-insensitive futura pode
sobreaproximar retornos, declarando essa precisão. W1/W2/W3 devem conservar Entry sem
prometer contexto universal. CP5 não implementa PERFORM ou pilha semântica.

<a id="f"></a>

## F — Conclusão por fase

O [resultado](analysis-dataflow-result-v1.md) acrescenta `completion`. executionStatus
continua representando admissão/solver; queryStatus conserva VALUE/UNSUPPORTED_POINT.
Há estados distintos de admissão, análise, materialização, cada consumer e publicação.
`pipelineStatus=COMPLETE` exige todas as fases solicitadas concluídas e publicação
confirmada. Solver STABLE sozinho nunca significa pipeline completa.

A política é EXPLICIT_PARTIAL_BY_PHASE: lote de observações atômico e cada consumer
atômico independentemente. Se replay excede budget, descartar aquele lote, publicar
observations=[], observation LIMIT com motivo, executionStatus STABLE/limitReason=null
e pipeline INCOMPLETE. O plano solicitado independente demonstra que o lote não
sumiu; queries não materializadas são contadas. Não converter budget em
UNSUPPORTED_POINT. B1 continua exigindo respostas completas, inclusive recusas,
quando observation COMPLETE; uma query recusada não limita o lote.

Consumer A concluído e B falho mantêm status separados, com o ID de cada consumer
solicitado. Payload parcial de B não pode sair; resultados concluídos de A e o lote
estável podem ser publicados sob INCOMPLETE explícito. O plano de consumers deve ser
comparado com a coleção de status, inclusive falhas; coleção vazia significa nenhum
consumer solicitado, não falhas omitidas. O futuro wire de facts correlacionará cada
bundle ao consumer concluído, sem novo framework nesta preparação.

Falha de output não reescreve solver, observation ou consumer. Publication FAILED/LIMIT
significa que não existe confirmação de entrega completa; bytes truncados não são
artefato válido. Recibo de entrega é externo ao payload preparado, registrado pelo
chamador mesmo quando o writer falha. O snapshot modela esse recibo conceitualmente;
um writer não pode colocar COMPLETE em bytes e tomar isso como confirmação de sua
própria entrega. Publicação parcial é somente de bundles concluídos em envelope
INCOMPLETE, nunca de fatos provisórios ou stream truncado.

W1: admissão; W2: analysis limits; W3/S14: replay; W4/S14: consumer isolation; W5/S14:
output e recibo. Testes atuais validam envelopes hipotéticos; não são runtime/writer.

<a id="g"></a>

## G — Três evidências independentes

W2/W3 exigem, onde aplicável, expected manual + solver por recomposição independente
+ pequeno oracle semântico concreto finito. Comparar dois solvers abstratos pode
esconder o mesmo transfer errado. S13 deve incluir esse defeito proposital e observar
que o oracle concreto rejeita o resultado mesmo quando ambos os solvers concordam.

O oracle é test-only e usa linguagem sintética mínima, sem COBOL: estados finitos,
constantes/cópias, gen/kill e bifurcação finita em fixtures pequenos. Enumera transições
concretas e estados alcançáveis até esgotar pares finitos de ponto/estado; não usa
solver produtivo nem copia transfer/join/worklist abstratos. Compara inclusão de
todos os comportamentos concretos na concretização do resultado entregue, por
Entry/ponto. Para backward gen/kill, enumerar usos antes de redefinição em trajetos
finitos e ciclos do modelo finito. Expected manual também impede abstração sempre
unknown de passar como resultado preciso. Bound do fixture deriva do espaço finito,
não de truncar caminhos arbitrariamente; fixtures fora dele são recusados pelo teste.

Esse hook continua NOT_AVAILABLE_UNTIL_IMPLEMENTED, para W2 e ampliado em W3; não
criamos segundo framework nem tratamos os experimentos relatados pela auditoria
como execução do código Java. S4b continua protegendo o custo incremental escolhido.

<a id="h"></a>

## H — Qualidade junto de custo

S15 acompanha S3/S6/S9/S10, além dos contadores por Wave em metrics.json. Registrar
requests brutos, queries únicas, respondidas VALUE, recusadas, não materializadas,
recusas de storage/effects, saturations, remainders de modelo/fonte/efetivos,
closed-in-model, analysis/observation limits, consumer/publication failures. Incluir
escopo, corpus, profile, k, budgets e denominadores; métrica unavailable não é zero.

Para lote completo, uniqueQueries = queriesAnswered + unsupportedQueries; no lote
atômico limitado, queriesNotMaterialized cobre as queries sem resposta comprometida.
Custo de trabalho descartado continua medido. Contadores de abertura podem se
sobrepor; não somá-los como categorias exclusivas. Closed-in-model não fecha fonte.
Comparar custo somente com a qualidade do mesmo corpus/configuração: early saturation
ou unsupported-everything devem aparecer no oracle esperado e nos contadores. W2
ativa analysis limits, W3 qualidade de queries, W4 consumers, W5 publicação. S15
começa em W3 e adiciona somente as métricas disponíveis a cada Wave. Não há SLA,
threshold percentual ou alegação de ganho antes de corpus e evidência.

<a id="i"></a>

## I — Procedência do checkout CI

O coletor `scripts/project/ci_source_receipt.py` usa somente Git local e o payload do
evento: PR head/base SHA, actual checkout SHA, head/checkout tree SHA, run ID/event.
No evento push, base de PR não é fornecida e fica null; a conclusão remota e o PR
atual são acrescentados no recibo final após o run. SHA do metadata do run não prova
checkout literal do head. O workflow conserva pull_request e seu merge sintético.

EXACT_COMMIT_CHECKOUT exige checkout SHA = head SHA e árvores iguais. Se o checkout
é o merge sintético do evento e as árvores são iguais, classificar
SYNTHETIC_MERGE_IDENTICAL_TREE: **conteúdo-fonte equivalente ao HEAD**. Outra árvore
ou referência obsoleta não certifica o conteúdo do HEAD. Ausência de checkout/tree
evidence não autoriza claim exact-head; o validador do recibo deve falhar. O coletor
imprime e salva JSON para artifact de CI, sem consulta remota no hot path. O recibo
não prova que dependências, ambiente ou runtime futuros foram executados: os gates
nominais e suas limitações permanecem registrados separadamente.
