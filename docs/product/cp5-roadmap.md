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

| Checkpoint | Estado | Review necessário antes de iniciar |
| --- | --- | --- |
| Harness preparation | implemented / awaiting human review | entrega presente |
| W1 — index/session | NOT STARTED / NOT AUTHORIZED | preparação + autorização explícita W1 |
| W2 — generic incremental solver | NOT STARTED / NOT AUTHORIZED | W1 + autorização explícita W2 |
| W3 — PossibleValues/state/query | NOT STARTED / NOT AUTHORIZED | W2 + autorização explícita W3 |
| W4 — shared planner/consumers | NOT STARTED / NOT AUTHORIZED | W3 + autorização explícita W4 |
| W5 — production/E2E | NOT STARTED / NOT AUTHORIZED | W4 + autorização explícita W5 |
| CP6 — CALL consumer | futuro, fora CP5 | review final CP5 e tarefa própria |

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
Handoff: view/cursor contextual e ownership aceitos para W2.

## W2 — solver incremental e extensão (EVAL-CFG-035)

SPI/solver generic de estado opaco, acumulação de raiz forward e dual backward,
EdgeTransfer monotônico, scheduling substituível, status/budgets/statistics. Sem
PossibleValues/CLI. Provar chain/diamond/cycle/self-loop, seeds, SCC sem saída,
primeira publicação bottom backward, edges paralelas e não identidade. Segunda
análise finita test-only com outro tipo de state e solver byte-idêntico; duas agendas
justas e oracle independente por recomposição concordam em todos IN/OUT. S4/S4b/S8,
Aprop e fila auditados. DoD: S4b mata recomposição ~N² mesmo semanticamente correta;
OUT igual não propaga, join unchanged não enfileira, zero facts provisórios;
RED compilável/restore/segundo GREEN. Handoff: SPI/context/result e custo revisados.

## W3 — domínio, estado e queries (EVAL-CFG-036)

Criar analysis-values: profile scalar-text-direct, Cell/disjunção, boundary, bounded
text/open/saturation, pool, estado esparso compartilhado, Assign/Nop e queries de
ponto/batch. Sem CALL, Read/havoc/regiões produtivos ou RD. Provar overwrite, aliases
de mesma Cell, recusa multibase sem premissa, diamond desconhecido, k/k+1, Unicode,
PARTIAL/modelScope e before/after/unsupported point. S1/S2/S3/S6/S7/S9 e S4b largo.
Calibrar container/default k e budgets com ledger, retention, GC/JFR e liberação;
Patricia e 8 são candidatos, sem obrigação nominal. DoD inclui snapshot isolation,
missing-key correto, nenhuma coleção histórica e replay ≤ prefixos unidos. Handoff:
PossibleValues real e serviço de queries revisados para W4.

## W4 — planner e consumers (EVAL-CFG-037)

Registro/interesses, união de demandas, cache de runs e dispatch stable-only;
dois consumidores de teste independentes e consumidores sem análise. Sem resolvers
de negócio, runtime plugins ou fusão universal. Integra W3 real, sem encerrar com
mocks. S5/S6/S8: invariância do índice/runs ao aumentar K, matches reais inclusive
sobrepostos, mesmo batch sem replay repetido, config/Entry distintas não colidem.
DoD: fronteiras compiladas, segunda análise intacta, falhas de consumers tipadas,
RED/restore/GREEN. Handoff: pipeline de extração pronta para composition root.

## W5 — produção e E2E (EVAL-CFG-038)

Entrypoint AnalysisDataflow separado e writer local nos adapters, versão de resultado
revisada, defaults/budgets aprovados. AIR file→reader→BuildCfg→sessão→queries→resultado;
plano por destinos escritos/before terminator, sem nomes do fixture. Regressões CLI
CFG, errors input/build/analysis/output, determinismo, identidade e PARTIAL.
Consolidar S1–S9 e S10: dois runs reais desde COBOL CP4E, regressão CP3, JARs/pins/
hashes/comandos e equivalência memória/arquivo. Novos artefatos E2E em sibling exigem
autorização própria; esta preparação não os cria. DoD: gates/challenges/ledgers e
review final; limitações de transporte separadas; sem CP6 automático.

## Evidência por checkpoint

Gates locais, RED nominal, hashes de restauração, segundo GREEN, SHAs/pins/ambiente,
logs brutos, exact HEAD e recibo remoto no mesmo PR. PASS local, PASS remoto,
UNAVAILABLE e NOT_APPLICABLE_YET são estados distintos. CI do SHA antigo não valida
HEAD novo. Não embutir o hash do próprio commit no arquivo commitado: recibo final
no PR permite descoberta pelo lifecycle sem ciclo de commits de metadados.
