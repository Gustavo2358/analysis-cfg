# CORE-SIZE-001 — classificação da auditoria

Base aprovada: e86a57c1f744bd499dd47326ebcb84d23c61ab2d. Nova decisão humana
posterior supersede somente capacidade. [Busca original](size-audit-before.txt)
preserva 209 matches sem alteração; [índice de classificação](baseline-classification.json)
cobre cada linha. Passagens mistas são A quando continham cláusula de capacidade;
as obrigações semânticas/performance adjacentes continuam preservadas.
[Busca posterior ampliada](size-audit-after.txt) e [comando/hash](audit-search.json)
incluem os contratos ativos, scripts e Java de produção, inclusive maximumBytes
não encontrado pela primeira lista de termos. Match não equivale a política vigente.

| Classe | Uso e evidência revisada | Decisão |
| --- | --- | --- |
| A — capacity/size policy | ADMISSION_LIMIT, ANALYSIS_LIMIT, LIMIT de admission/analysis/observation/consumer/delivery; WORK/QUERY/ADMISSION_BUDGET nos contratos F, snapshots, validators e testes | Removidos dos outcomes admitidos. Run STABLE/UNSUPPORTED/INVALID_INPUT; fases e recibo sem LIMIT. Falha de recurso não produz resultado semântico, nem FAILED parcial disfarçado. |
| A | resourceBudgets em AnalysisKey, configurações de pool/work/query/memory e budget como término | options={} no profile atual; solver termina por ponto fixo. Manifest fechado por papel e contracasos rejeitam reintrodução. |
| A | maxCandidates, k=8, bounded small sets, CARDINALITY_LIMIT/Saturated por contagem em ADR12/13, values, S7, challenges, wire e validação | Retirados do produto CP5. Todos os candidatos finitos preservados. Histórico humano/evidência antiga permanece append-only, com supersessão explícita. |
| A | maxNodes/Edges/Operations/Objects/Sequences, maxQueries/Facts/Visits/Iterations/Worklist, consumer/site/batch counts, AIR/output/result bytes | Não são critérios de admissão, interrupção ou precisão. Novos papéis/probes/challenges obrigatórios nas Waves; não existem hooks de engine nesta sessão. |
| A | maximumDocumentBytes no reader, maximumBytes no writer CFG e propagação VALIDATION_LIMIT em BuildCfg | Dívida local legada, ainda executável e explicitamente não aprovada para CP5. Java/POM e testes produtivos não podem mudar nesta sessão. Documentação de portas/CFG JSON/gates passa a apontar a dívida. |
| B — semantic convergence | Inclusão de Candidates(S,open), U(P) finito, join monotônico, boundary fixo, agenda justa; finite oracle S13; domínio sintético max(rank) em S4b | Mantidos. U(P) cresce com o programa; não é configuração da máquina. Cadeia infinita futura exige abstração/widening com justificação matemática. |
| B | INVALID_INPUT para JSON/IDs/owner/CFG contraditórios; UNSUPPORTED para effects/storage/profile insuficientes; checks de overflow, arrays/índices/casts | Mantidos. Overflow real é defeito de implementação, sem contrato de tamanho suportado. Perfil semântico limita formas, não quantidades. |
| C — metric/telemetry | maxWorklistSize, maxSparseBindings, nodes/edges/operations indexed, pushes/joins/visits, allocations/retained bytes, candidateCardinality, elapsed/heap/GC; ausência de threshold arbitrário de qualidade | Mantidos como observações. Desigualdades de ledger verificam custo/comportamento do algoritmo; não autorizam abortar análises. Métricas analysisLimits/saturations saem porque seus eventos deixam de existir. observationFailures permanece controlada. |
| D — synthetic probe cardinality | S1–S9: N/2N, 1k/2k/10k nodes/predecessors, 100k instructions, K consumidores, U valores; S7 inclui 8/9,100,10000; S16 N/2N/4N | Tamanhos de fixtures, sem máximo produtivo. N/2N/4N do mesmo profile devem ser admitidos; todos os candidatos exatos da fixture fechada sobrevivem. Review snapshot é NOT_EXECUTED, não medição. |
| E — test/CI infrastructure | cache_ir.py: download de fonte normativa pinada, timeout25s/2.000.000 bytes; timeouts de subprocess/fixtures, smoke sem threshold temporal, CI limits | Mantidos fora do core. Falha de aquisição/CI é falha do teste/infra; não muda classificação do programa. Não houve modificação do workflow/coletor I. |
| E com dívida A | check_transport_architecture exige reader físico bounded; testes existentes input/output-limit e CLI | Regressão byte-exact do legado, não gate desejado para CP5. Migração precisa de alteração produtiva e adaptação dos checks na tarefa autorizada. Não se apagou proteção sem alterar/validar produto. |
| F — external sibling debt | air-java@ce530a7e: AirJson.Limits default16MiB/depth128, max depth256; ValidationOptions default2M entities/10k issues/depth128, max depth512 | EXTERNAL SIZE-CAP DEBT. Fontes lidas; nenhum sibling alterado. Elevar teto não resolve a política. W5 reporta a dependência e não a converte em outcome CP5. |
| F | cobol-lower32MiB/1.5M/250k/depth64 citados no handoff | Dívida reportada pela autoridade externa, sem nova verificação de implementação nesta sessão. Não afirmar pipeline completa size-unbounded. |

O limite de 400 caracteres do stderr da CLI apresenta diagnóstico; não trunca facts
ou candidatos. Não é cobertura semântica. Nomes/IDs longos no hot path continuam
motivando identidade densa e custos auditados, sem teto por comprimento.

## Supersessão focal e dívida produtiva

H4 preserva sparse state, snapshot sharing/isolamento, nenhuma cópia integral por
instruction, nenhuma matriz node×location, lookup/update eficiente, retenção e
identidade densa. Somente caps e budgets de capacidade saem; Patricia/FIFO continuam
experimentais. `unbounded-values` dá lugar a `non-convergent-semantic-domain`:
conjunto finito grande é permitido; domínio sem argumento de convergência não é.

F1 mantém inválido versus não suportado. F2 mantém run ≠ prepared ≠ receipt;
F3 mantém planos/dependências e consumers independentes. Falha controlada de
consumer não descarta sucesso independente; esgotamento da máquina não é causa de
partialidade. A–E/G/I machine-readable e workflow/coletor são comparados ao HEAD
aprovado pelo scope guard. Os demais contratos continuam adversarialmente testados.

[Dívidas e ownership](../../../cp5-follow-ups.md#size-cap-debts). A decisão é
normativa para analysis-cfg/cfg-kernel/futura engine. Este checkpoint corrige o
desenho antes de W1; não remove caps do runtime legado nem do E2E. W1–W5 permanecem
NOT_STARTED/NOT_AUTHORIZED, sem implementação de streaming/spill/infra/retry/ECS.
