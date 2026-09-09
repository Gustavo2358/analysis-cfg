# CORE SIZE-UNBOUNDED — evidência de remediação do harness

Checkpoint CP5_CORE_SIZE_UNBOUNDED_HARNESS_REMEDIATION, WORK-CFG-028.
[Baseline](baseline.json): checkout limpo na branch feat/cp5-dataflow-engine,
PR #12 OPEN/DRAFT, sem auto-merge, HEAD local/remoto
`e86a57c1f744bd499dd47326ebcb84d23c61ab2d`. Esse HEAD recebeu aprovação humana
A–I/F1/F2/F3, registrada append-only no lifecycle junto da nova autorização
CORE_SIZE_UNBOUNDED_AUTHORIZED_FOR_HARNESS_REMEDIATION_ONLY. Base CP4/main:
`ec525cbbad96d70c9663faa88e2672148fa8ee71`. Nenhum review anterior foi sobrescrito.

## Decisão e escopo

[ADR-0014 / CORE-SIZE-001](../../../../architecture/decisions/ADR-0014.md):
**Program size is not a semantic admission criterion.** Válido + suportado + grande
continua válido + suportado. [Auditoria A–F](audit-classification.md) distingue
capacidade de convergência, métricas, tamanhos sintéticos, infra de teste e dívida
externa; inclui os matches brutos antes/depois e classificação por linha de baseline.

Saem do desenho: ADMISSION_LIMIT, ANALYSIS_LIMIT e LIMIT de fase/consumer/entrega,
resourceBudgets, work/query/admission budgets e maxCandidates/k/CARDINALITY_LIMIT/
Saturated por contagem. AnalysisKey.options={} no CP5; PossibleValues preserva todo
S ⊆ U(P), finito por programa. Unknown/remainder é semântico. Convergência exige
contrato matemático e fixpoint, sem orçamento de trabalho. Overflow é defeito de
representação a corrigir, sem supported-size contract ou aritmética insegura.

H4 preserva sparse state, sharing/isolamento, nenhuma cópia integral por instruction
ou matriz node×location, lookup/update eficiente, identidade densa e retenção
mensurável. Patricia/FIFO continuam experimentais; k=8 deixa o design produtivo.
N/2N/4N e cardinalidades 9/100/10000 passam a proteger cobertura invariável por tamanho.

INVALID_INPUT e UNSUPPORTED semânticos permanecem; A–E/G/I não são reabertos.
F2/F3 conservam run ≠ prepared ≠ receipt, consumerPlan/dependências explícitas,
batches atômicos e EXPLICIT_PARTIAL_BY_DEPENDENCY por falha controlada independente.
O batch B1 misto conserva before(Return)=VALUE e after(Return)=UNSUPPORTED_POINT.
Consumer independente pode completar sem o batch falho; esgotamento de recurso não
produz partialidade, unknown ou resultado semântico. Process/container/host/OOM/
timeout/disco externo ficam na fronteira de execução, sem framework ou retry.

## Provas locais e limites

[Recibos e logs](gates.json) incluem comando, exit, SHA-256 raw/gzip e logs íntegros
comprimidos sem perda. [Ambiente](environment.log): Temurin 21.0.12.1+1,
Maven 3.9.16, Python 3.14.4; [dependência isolada e pin](dependency-environment.json).
Nenhum cache/toolchain/build foi versionado.

| Gate/prova | Resultado observado |
| --- | --- |
| Baseline fast | GREEN, 47 originais + 72 CP5 |
| TDD inicial | RED: quatro testes, sete subcasos nominalmente incompatíveis com o contrato antigo |
| Iteração intermediária | Um mismatch textual de diagnóstico; guard rejeitou o mutante corretamente. Log preservado; expectativa textual corrigida |
| Fast final, dentro de full | PASS, 47 originais + 83 CP5 = 130 testes |
| Architecture, dentro de full | PASS, 102 kernel + transporte, fontes/classfiles/Maven/bytecode/pin |
| Semantic, dentro de full | PASS, 84 métodos nominais, zero skips |
| Integration separado | PASS, cinco suítes/37 métodos nominais de transporte |
| Maven clean verify | PASS, 139 testes, zero failures/errors/skips |
| Scope/manifest/diff | PASS; baseline Java/POM, pin/fixtures, escopo focal e histórico preservados |
| Performance | UNAVAILABLE / exit 3: hook produtivo ausente |
| Full | UNAVAILABLE / exit 3: fast/architecture/semantic passaram; parou em performance |

Contracasos de manifests parseáveis comprovam baseline GREEN → RED esperado →
restauração byte-exact → segundo GREEN para caps de node/edge/operation/object,
query/consumer/visits/worklist, options/resourceBudgets, cardinality TOP, open por
N+1, candidato perdido, enums LIMIT e OOM→ANALYSIS_LIMIT. Asserções adicionais
rejeitam resource exhaustion renomeado como FAILED e admissão inválida/não suportada
motivada por volume no witness. Papéis fechados verificam decisões, não grep por
`max`; máximos métricos continuam permitidos e obrigatórios.

Essas provas executam validators de contratos/snapshots, **não uma engine**.
core-size-review declara inputs/expected N/2N/4N separadamente e execution=NOT_EXECUTED.
S7/S16, 12 novos challenges e gates de W1–W5 permanecem
NOT_AVAILABLE_UNTIL_IMPLEMENTED, hook/target null. As Waves deverão vincular os
oracles ao produto real com o ritual de mutação compilável. Não há PASS CP5 completo.

## Dívidas e integridade

[Follow-ups](../../../cp5-follow-ups.md#size-cap-debts): AirJson16MiB/depth e
ValidationOptions externos ainda podem barrar E2E; reader/propagação de validação
no kernel e writer CFG64MiB locais são dívida de runtime legado. Gates atuais
continuam regressões desses bytes, sem aprovar seus caps para CP5. Remoção produtiva
precisa de tarefa/escopo posterior, antes de qualificar composição W5 sem cap local.
Não afirmar pipeline inteira size-unbounded. CP5-F01 segue NONBLOCKING_FOLLOW_UP_W1.

[Integridade dos siblings](sibling-integrity.json) verifica HEAD/branch/status e
hashes das três autoridades, preservando os arquivos não versionados preexistentes.
[Áreas alteradas](files-changed.json): somente docs, scripts/harness, scripts/project
e MANIFEST. Nenhum Java/POM, fixture, source pin, sibling ou workflow/coletor I alterado.

## Recibo do push final e parada

O commit não pode conter seu próprio SHA. Após push, registrar no PR #12 o recibo
do último HEAD com base, checkout real, head tree, checkout tree, run ID, evento,
conclusão e classificação EXACT_COMMIT_CHECKOUT / SYNTHETIC_MERGE_IDENTICAL_TREE /
DIFFERENT_TREE. Cópia local ignorada:
`.harness-results/WORK-CFG-028/core-size-unbounded/remote-ci.json`.
O coletor e o workflow I aprovados permanecem byte-exact. CI verde certifica harness
e regressões CFG, sem certificar solver/performance da engine ausente.

Entrega aguardando review humano. authorized_wave=null; W1–W5
NOT_STARTED/NOT_AUTHORIZED. Sem engine, Java/POM produtivo, siblings, retry/ECS,
streaming/spill ou interfaces novas. Mesmo PR OPEN/DRAFT, sem merge/auto-merge/ready.
A primeira autorização W1 só pode ocorrer em tarefa posterior à aprovação humana.
