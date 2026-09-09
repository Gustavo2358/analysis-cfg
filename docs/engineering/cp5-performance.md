# CP5 — contrato de performance, métricas e retenção

H4/R1/R2 aceitos; [ADR-0013](../architecture/decisions/ADR-0013.md).
**Engine não implementada nem medida.** O gate performance/full existente continua
UNAVAILABLE/exit 3; validar estes contratos é PASS somente de harness.
[Manifest dos probes](../evals/cp5/probes.json) especifica ativação, N/2N, regressão
e métricas. [Lifecycle](../work/cp5-lifecycle.json) não permite ativação automática.

## H4 é propriedade, não container

Obrigatórios: state sparse; sem matriz node×location; sem clone integral por
instruction; partes inalteradas compartilhadas; raízes expostas imutáveis/isoladas;
lookup/update com bound explícito; retenção e lifecycle mensuráveis; small sets
limitados; saturation/remainder explícitos; budgets de trabalho/memória/pool/query/
saída verificados antes da alocação. Sem cadeia de deltas cujo lookup cresce com
histórico nem coleção pesada por singleton no hot path.

“Persistent” significa preservar snapshots compartilhados/isolados sem cópia
integral. Não exige classe PersistentMap. Patricia/radix, FIFO e k=8 são candidatos
experimentais falsificáveis, **não constraints**. W2 compara duas agendas justas;
W3 mede container/default de k. Alternativa que preserve propriedades pode substituir
candidato por review normal com ledger/probes; não exige reabrir H4. Inventário de
classes da implementação escolhida não muda esse contrato.

## Dimensões e custo

V/E: nós/arestas já contextuais; I: instructions+terminadores; D: Objects/bases;
R: referências; P: membros de premises/scopes; B: bytes textuais; Ve/Ee: view da Entry;
F: bindings vivos; U: valores distintos; k: bound por Cell; C: sites; M: matches;
K: consumers; Qraw/Q: pedidos/brutos únicos; Fout/Bout: facts/bytes de saída.

```text
Aprop = Σ (firstPublication(v) + semanticChanges(v)) × affectedDegree(v)
Tindex = O(V+E+I+D+R+P+B) com passes declarados
Tsolve = init + Σ pops × (pop + blockTransfer + comparePublished)
         + Σ contributions × (edgeTransfer + joinInto + enqueueIfChanged)
Tquery = O(Qraw + Q log Q) + custo da união de prefixos FORWARD ou sufixos BACKWARD + Q × lookup
Textract = O(C+M+Q) + trabalho específico + O(Fout log Fout + Bout)

run completo: edgeContributionJoins = edgeTransferInvocations = Aprop
accumulatorStatesChanged ≤ edgeContributionJoins
worklistAttempts ≤ initializationAttempts + accumulatorStatesChanged
worklistPushes ≤ worklistAttempts; maxWorklistSize ≤ pontos ativos
forward produtivo: predecessorContributionReads = 0
```

Boundary joins têm contador separado. FirstPublication não é mudança posterior.
Budget pode cortar entrega: status e métricas parciais explícitos. Backward usa IN
publicado/OUT acumulado e affectedDegree dos predecessors. S4b isola contribuições
a J: N, com join escalar constante; recomposição relê ~N² slots. Join de raiz inteira
pode ser caro: J=joinEntriesVisited, Z=stateCompareEntries e alocações são medidos
separadamente; Aprop não prova J linear. Não prometer O(V+E) geral de solver/build.

Para um container escolhido, declarar custo de lookup/update/merge/compactação por
F/D/k; distinguir worst-case e amortizado. Se trie for escolhida, seu d e O(d) por
write entram no ledger. Não usar d como gate universal de outras representações.
Crescimento O(N log D) pode cumprir H4; clone O(N×F) não. Budget/threshold absoluto
só após calibração reproduzível e review; nenhuma meta em milissegundos ou SLA de LOC
foi aprovada nesta preparação.

## Ledger obrigatório por componente na Wave que o introduzir

Cada loop/coleção: owner, fase, coleção percorrida, cardinalidade, passes, custo por
evento, allocation, retenção, liberação e ponto de instrumentação. Nova coleção O(I)
exige entrada. Separar decode/validator, BuildCfg (preflight/sorting/Entry expansion),
índice (nodes/declarations; payload uma vez; edges em passes CSR), preparação de
domínio (buckets/R/P), solver (revisitas), observação (prefixo/sufixo por lote conforme direção), consumers
(C+M) e encoding. Não inserir terceira validação nem contar openSession como prova
de uma única travessia física.

```text
Madditional = index + contextViews + 2×Ve×rootReference + queue
              + liveSharedState + valuePool + smallSets
              + selectedObservations(Q) + output(Fout) + scratch
```

AIR/CFG entram no baseline do processo uma vez. Duas raízes de fronteira por ponto
ativo, sem histórico por edge/instruction. Liberação de sessão/run solta índices,
pools e views; facts selecionados podem sobreviver sem segurar raízes de solver.
Diferenças reais entre blocos ainda custam memória; não prometer sharing universal.
U=N em N overwrites distintos mesmo se F=1; k não limita U. Não internar globalmente.

## Métricas auditáveis

O [manifest de métricas](../evals/cp5/metrics.json) define incremento/unidade/owner.
Nomes seguem vocabulário do discovery, podendo ser mapeados ao estilo Java da Wave
sem perder dimensões. Counters inteiros long, overflow tratado como limite, sem
logging por operação. Separar sessão, AnalysisKey/Entry, observation epoch e extração.

Estado retido/bytes requer auditoria fora do hot path: factories instrumentadas,
walk das estruturas realmente retidas com dedup por identidade e/ou heap/JFR. Bytes
calculados por layout são **estimativa**, não medição física; declarar headers,
alinhamento, compressed refs. Não fixar counter em zero para esconder predecessor
reads: instrumentar a leitura real e confrontar mutante/ledger/bytecode.

JVM isolada: registrar JDK/GC/heap/flags, warmup, input counts, baseline AIR+CFG,
índice, solve, observação e liberação; preservar JFR/GC logs/class histogram como
checagem externa. Sampling não é contagem exata de objetos. O mutante quadrático
precisa falhar na engine/propriedade, sem OOM incidental do builder.

## Ativação e evidência

W1 ativa probes estruturais S1/S2/S4/S8 com execução real e contracasos; W2 S4/S4b/S8;
W3 S1/S2/S3/S4b largo/S6/S7/S9 com retenção; W4 S5/S6/S8 com W3 real; W5 consolida
S1–S9 e S10 de produção. Ativações parciais não fecham probes de outras Waves.
Script `scripts/project/check_cp5_gate.py performance --wave N` hoje retorna
UNAVAILABLE. Antes de ligar um hook: definir gerador/oracle, relatório bruto e
parser nominal que rejeite teste ausente/skip/contador omitido, executar GREEN,
mutante compilável RED, restore byte-exact, segundo GREEN, registrar SHA/ambiente.

Performance PASS exige hook produtivo executado para **todos** probes devidos na
Wave, bounds do ledger e contracasos. Markdown, flag implemented, exit 0 vazio ou
report pré-fabricado não são evidência. Gate de preparação exige todos hooks nulos;
a ativação posterior exige mudança revisada do harness junto à Wave autorizada.
S1–S9 in-memory não qualificam AIR >16 MiB; S10 mínimo não qualifica programas grandes.
BACKLOG-LOWER-017/018 permanecem dependências externas para essa qualificação.

## Pós-auditoria: qualidade e fases

[Regras F/H](../architecture/cp5-post-audit.md) exigem métricas de qualidade junto ao
custo: requests/unique/answered/unsupported/not-materialized, profiles storage/effect
recusados, saturations, remainders, closed-in-model, limits e falhas por fase. S15
acompanha S3/S6/S9/S10 com mesmo corpus/profile/k/budgets e denominadores explícitos.
Uma otimização por early saturation ou unsupported-everything deve falhar no expected
manual de qualidade, mesmo passando tempo/memória. Nenhum threshold percentual novo.
Overflow de contador/budget tem causa na fase que ocorreu; observação limitada não
vira ANALYSIS_LIMIT depois do fixpoint. Métricas não disponíveis continuam null.
S11 mede integridade no índice W1; S12 direção W2/W3; S13 oracle semântico concreto
W2/W3; S14 completion W3–W5; S15 qualidade W3–W5. Ativar hooks somente nas Waves.
