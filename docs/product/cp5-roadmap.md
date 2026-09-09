# Roadmap CP5 — um work item, uma branch, um PR

[Lifecycle canônico](../work/cp5-lifecycle.json) registra branch, PR, checkpoint,
última aprovação humana e estados por Wave. [Manifesto](../work/active/WORK-CFG-028/work-item.json)
mantém o protocolo existente de cinco arquivos; Waves são checkpoints desse umbrella,
sem novos itens ativos nem hierarquia paralela.

CP4 completo no escopo aceito: 4D merge ec525cbbad96d70c9663faa88e2672148fa8ee71,
CP4E aceito pelo humano para DATA+MOVE+GOBACK; [baseline](../work/evidence/WORK-CFG-028/baseline.json).
A ordem antiga MOVE→IF→CALL→dataflow cede à decisão aprovada CP4→CP5→CP6 CALL→IF
futuro. Branch/Jump/ciclos AIR sintéticos já disponíveis não antecipam slices COBOL.
Roadmap dos siblings permanece inalterado.
[ADR-0014 / CORE-SIZE-001](../architecture/decisions/ADR-0014.md) governa todas as
Waves. Métricas são observações, nunca thresholds de admissão ou precisão.

| Checkpoint | Estado | Review necessário antes de iniciar |
| --- | --- | --- |
| Harness preparation + B1 | APPROVED | review humano registrado |
| Post-audit remediation | APPROVED at e86a57c | A–I/F1/F2/F3 aprovados |
| Core size-unbounded harness remediation | APPROVED at 4aeb4c0 | review humano registrado antes W1 |
| W1 — index/session | IMPLEMENTED / AWAITING_HUMAN_REVIEW | implementação e CI validadas; review humano do HEAD final antes de qualquer W2 |
| W2 — generic incremental solver | NOT STARTED / NOT AUTHORIZED | W1 + autorização explícita W2 |
| W3 — PossibleValues/state/query | NOT STARTED / NOT AUTHORIZED | W2 + autorização explícita W3 |
| W4 — shared planner/consumers | NOT STARTED / NOT AUTHORIZED | W3 + autorização explícita W4 |
| W5 — production/E2E | NOT STARTED / NOT AUTHORIZED | W4 + autorização explícita W5 |
| CP6 — CALL dependency slice | futuro, fora CP5 | review final CP5 e tarefa própria |

## Continuidade entre agentes/sessões

Começar na raiz do workspace e seguir AGENTS → índice → work item/state → lifecycle
→ must_read → Git/PR. Conferir status/HEAD/branch e PR no GitHub; evidência offline
não prova autorização ou estado remoto. Reusar `feat/cp5-dataflow-engine` e o PR
registrado. Se checkout ocupado, worktree seguro da mesma branch/estado acordado,
sem branch divergente que crie outro PR. Preservar trabalho legítimo.

Preparação até W5 usam **a mesma branch e o mesmo PR draft**. Nenhum merge intermediário
é requisito: a autorização deste pedido prevalece sobre sugestões históricas do
handoff/discovery sobre merge/PR por Wave. Cada Wave pode ter vários commits
focalizados. Sem squash/rebase/force-push para reduzir commits. PR permanece draft,
sem auto-merge e sem ready até autorização explícita. Nenhuma Wave inicia por gate verde.

Antes da próxima Wave, registrar review humano da anterior com fonte/data, SHA
revisado e conclusão; só então autorização específica. Atualizar current_checkpoint,
authorized_wave, manifesto com paths concretos, eval/gates/escopo e inventários.
Conservar approvals anteriores em registro append-only de review no evidence do
item; last_human_approval é ponteiro/resumo, não substituto do histórico.
Não inferir aprovação do silêncio, de CI, do status GitHub ou de outro agente.

## W1 — índice estrutural e sessão (EVAL-CFG-034)

Criar primeiro módulo analysis-kernel e shell/session sobre BuildCfg real; ordinais,
reverso, Sequence/operation offsets, buckets e adjacência por Entry. Não solver,
values, consumers ou CLI. AIR por identidade; CfgNodeId esparso e owners completos;
nenhuma ordem física, deep copy ou scan por referência/successor. Oracles manuais
com homônimos, órfãs, múltiplas Entries e membership inválido. S1/S2/S4/S8 estruturais,
ledger por coluna/pass e heap do índice. Hook performance real deve nascer com
contracasos, sem PASS documental. Maven direto air-java e inventory/javap/jdeps do
módulo. DoD: regressões CFG, RED/restore/segundo GREEN, custo auditado e review.
W1/S11 deve verificar membership canônico e completude do profile no índice, sem
reconstruir CFG ou repetir AirValidator. Corrigir CP5-F01 quando W1 for autorizada.
Handoff: view/cursor contextual e ownership aceitos para W2, sem alegar frames locais.

W1 nasce sem node/edge/operation/object/sequence count cap ou admission-visit budget.
S16 compara N/2N/4N válidos e suportados: todos admitidos. Grafo inválido, profile
sem suporte e violação de invariante continuam distintos; muitos nós não são motivo
de falha semântica. Overflow é defeito de implementação, com aritmética checada.

## W2 — solver incremental e extensão (EVAL-CFG-035)

SPI/solver generic de estado opaco, acumulação de raiz forward e dual backward,
EdgeTransfer monotônico, scheduling substituível, status/statistics e convergência. Sem
PossibleValues/CLI. Provar chain/diamond/cycle/self-loop, seeds, SCC sem saída,
primeira publicação bottom backward, edges paralelas e não identidade. Segunda
análise finita test-only com outro tipo de state e solver byte-idêntico; duas agendas
justas e oracle independente por recomposição concordam em todos IN/OUT. S4/S4b/S8,
Aprop e fila auditados. S12 prova replay backward a partir de OUT, em ordem reversa;
S13 acrescenta oracle concreto finito independente de transfer/join/worklist. DoD: S4b mata recomposição ~N² mesmo semanticamente correta;
OUT igual não propaga, join unchanged não enfileira, zero facts provisórios;
RED compilável/restore/segundo GREEN. Handoff: SPI/context/result e custo revisados.

W2 nasce sem maxIterations, maxWorklistPushes, maxJoins, work budget ou analysis
timeout. Termina por convergência/fixed point; se não convergir quando deveria, é
bug. S16 mede trabalho sem cortar execução ou reduzir cobertura.

## W3 — domínio, estado e queries (EVAL-CFG-036)

Criar analysis-values: profile scalar-text-direct, Cell/disjunção, boundary, texto finito por programa/open semântico, pool, estado esparso compartilhado, Assign/Nop e queries de
ponto/batch. Sem CALL, Read/havoc/regiões produtivos ou RD. Provar overwrite, aliases
de mesma Cell, recusa multibase sem premissa, diamond desconhecido, N/N+1 candidatos preservados, Unicode,
PARTIAL/modelScope e before/after/unsupported point. S1/S2/S3/S6/S7/S9 e S4b largo.
Medir container e cardinalidades crescentes com ledger, retention, GC/JFR e liberação;
Patricia é candidato sem obrigação nominal; k=8 deixa de ser opção produtiva. DoD inclui snapshot isolation,
missing-key correto, nenhuma coleção histórica e replay ≤ união de prefixos FORWARD ou sufixos BACKWARD. Handoff:
PossibleValues real e serviço de queries revisados para W4. Ampliar S12/S13 à API
real; S14 distingue solver estável de replay com falha controlada, S15 confronta custo e qualidade.
Effects semânticos precedem fixpoint; lógica de consumers não repara estado obsoleto.

W3 preserva todos os candidatos semanticamente produzíveis no programa finito.
Sem maxCandidates, k produtivo, CARDINALITY_LIMIT ou candidate-count saturation.
S7/S16 exercitam 9, 100, 10.000 valores e N/2N/4N; remainder só por incerteza
semântica. Domínios futuros infinitos precisam de convergência sem caps de máquina.

## W4 — planner e consumers (EVAL-CFG-037)

Registro/interesses, união de demandas, cache de runs e dispatch stable-only;
dois consumidores de teste independentes e consumidores sem análise. Sem resolvers
de negócio, runtime plugins ou fusão universal. Integra W3 real, sem encerrar com
mocks. S5/S6/S8: invariância do índice/runs ao aumentar K, matches reais inclusive
sobrepostos, mesmo batch sem replay repetido, config/Entry distintas não colidem.
DoD: fronteiras compiladas, segunda análise intacta, falhas de consumers tipadas,
S14 verifica dependências AnalysisKey/batch explícitas por consumer e preparação parcial; S15
inclui falhas de consumers sem ocultar qualidade. RED/restore/GREEN. Handoff: pipeline de extração pronta para composition root.

W4 não expõe maxConsumers/maxCandidateSites/maxQueries/maxObservationBatches como
política de admissão. Demanda adicional aumenta trabalho, preserva cobertura e
dependências explícitas; S16 verifica volumes crescentes.

## W5 — produção e E2E (EVAL-CFG-038)

Entrypoint AnalysisDataflow separado e writer local nos adapters, versão de resultado
revisada, defaults semânticos aprovados. AIR file→reader→BuildCfg→sessão→queries→resultado preparado;
writer recebe payload imutável e chamador registra DeliveryReceipt externo;
plano por destinos escritos/before terminator, sem nomes do fixture. Regressões CLI
CFG, errors input/build/analysis/output, determinismo, identidade e PARTIAL.
Consolidar S1–S9 e S10: dois runs reais desde COBOL CP4E, regressão CP3, JARs/pins/
hashes/comandos e equivalência memória/arquivo. Novos artefatos E2E em sibling exigem
autorização própria; esta preparação não os cria. DoD: gates/challenges/ledgers e
review final; limitações de transporte separadas; sem CP6 automático. S14 testa
DeliveryReceipt externo e falha de output sem invalidar payload/fixpoint; S15 mede
qualidade junto a tempo/memória. Conservar recibo real de checkout/evento/árvores.

W5 não introduz maximum output/AIR/result size ou query count dentro do CP5.
A composição não herda caps locais do reader/writer legado como política aprovada.
[Siblings e legado](../work/cp5-follow-ups.md#size-cap-debts) podem ainda impedir
a rota por arquivo; reportar EXTERNAL SIZE-CAP DEBT sem converter para outcome
semântico CP5 nem desabilitar validação. Remoção produtiva de caps locais exige
escopo autorizado antes da qualificação W5. Nenhum retry/ECS/streaming é decidido.

## Evidência por checkpoint

Gates locais, RED nominal, hashes de restauração, segundo GREEN, SHAs/pins/ambiente,
logs brutos, PR head/base, checkout SHA real, head/checkout tree SHA, run ID/evento
e conclusão no mesmo PR. Checkout literal do HEAD difere de merge sintético com
árvore idêntica (conteúdo-fonte equivalente ao HEAD). PASS local, PASS remoto,
UNAVAILABLE e NOT_APPLICABLE_YET são estados distintos. CI do SHA antigo não valida
HEAD novo. Não embutir o hash do próprio commit no arquivo commitado: recibo final
no PR permite descoberta pelo lifecycle sem ciclo de commits de metadados.

## Limites dos slices posteriores

CP6 é um CALL dependency slice, incluindo consumers e quaisquer contratos prévios
necessários de Invoke/effects em AIR/lowering/CFG; não basta adicionar CallResolver.
PERFORM/local control exige evolução própria de controle/contexto, local.invoke,
completion ports/resume/frames ou contrato equivalente AIR. activationEntry não
resolve pareamento universal de retorno; sobreaproximação deve ser declarada.
GRBE byte-slice exige storage/region/view/codec ou redução justificada; substring de
texto lógico não prova intervalo físico. Marginais de PossibleValues não provam
pares de campos: correlação pode exigir tuple/partition/refinement/query especializada.
Nenhum desses slices, regions ou domínio relacional geral entra no CP5 presente.
[Detalhe das remediações](../architecture/cp5-post-audit.md).
