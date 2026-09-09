# WORK-CFG-028 — correção focal do review F

Review humano de e053f14f8f5dc7b0b80b7bbbe015fde684522835: REQUEST CHANGES somente
F1/F2/F3. A/B/C/D/E/G/H/I APPROVED, CP5-F01 NONBLOCKING/follow-up W1. Histórico
prévio/B1/auditoria preservado no lifecycle. Checkpoint continua
CP5_POST_AUDIT_HARNESS_REMEDIATION; nenhuma Wave autorizada ou iniciada.

HEAD/branch remotos conferidos após fetch: e053f14f8f5dc7b0b80b7bbbe015fde684522835,
feat/cp5-dataflow-engine, PR #12 OPEN/DRAFT, auto_merge=null, base
ec525cbbad96d70c9663faa88e2672148fa8ee71. Checkout inicial limpo.

## Correção

F1: ADMISSION_LIMIT/admission LIMIT é distinto de UNSUPPORTED/REJECTED;
analysis/observation não iniciam após admissão limitada.
F2: completion do run não contém consumidores ou entrega. PreparedAnalysisResult
reúne resultados/dependências/outcomes; DeliveryReceipt externo correlaciona
identidade, destino, hash quando disponível e confirmação de tentativa. Falha de
writer mantém payload e solver. Sem buffer integral obrigatório para produzir hash
em falha; recibo COMPLETE exige hash/ack. Wire/runtime continuam para W5.
F3: ConsumerPlan lista AnalysisKeys e batch IDs exigidos. Lote limitado é atômico e
bloqueia apenas dependentes; StructuralConsumer pode completar com zero análises;
consumer de solver estável não precisa de replay; batches independentes são testados.

[Contrato](../../../../architecture/analysis-dataflow-result-v1.md),
[regra F](../../../../architecture/cp5-post-audit.md#f) e
[witness F3](../../../../evals/cp5/phase-review.json). A única alteração na seção A é
a classificação de admission budget explicitamente rejeitada em F1; nenhuma regra
CFG foi reaberta. Scope guard compara os demais contratos machine-readable e
probes/metrics/CI aprovados byte-exact com o HEAD revisado.

## Validação e limites

TDD inicial: 62 testes, 3 failures nominais para F1/F2/F3. A suite corrigida contém
72 testes CP5, com guards contra perda do contrato, self-certification e barreira
global, além de identidades/dependências estrangeiras, omissões, writer failure,
structural-only e batches independentes. Testes de harness não executam engine/writer.
[Recibos](gates.json) preservam comandos/exits e logs raw comprimidos sem perda,
com hashes raw/gzip. [Ambiente](environment.log): Temurin 21.0.12.1+1, Maven 3.9.16,
Python 3.14.4; Maven isolado com air-java pinado ce530a7e17ab12b23c48f29425f503ff920b09fb.

| Gate | Resultado observado |
| --- | --- |
| fast | PASS: 47 testes originais + 72 CP5 = 119 |
| architecture | PASS: sources/classfiles/Maven, 102 testes kernel |
| semantic | PASS: 84 métodos nominais, zero skips |
| integration | PASS: 37 métodos, executado separadamente |
| Maven clean verify | PASS: 139 testes, zero falhas/erros/skips |
| scope/manifest/diff | PASS: baseline CP4 e contratos não-F preservados |
| performance/full | UNAVAILABLE, exit 3; hook de engine ausente |

O full executou fast/architecture/semantic e parou na ausência de performance.
Os contracasos de harness restauram bytes e exigem segundo GREEN; os três novos
challenges produtivos permanecem NOT_AVAILABLE_UNTIL_IMPLEMENTED, em W1/W4/W5.
[Integridade dos siblings](sibling-integrity.json) compara HEAD/branch/status e bytes
versionáveis, inclusive arquivos previamente não versionados. [Arquivos alterados](files-changed.json)
registra o delta focal. Logs originais adicionais permanecem em /tmp/cp5-review-f.

## Recibo do último push

O commit não pode incluir seu próprio SHA final. O recibo remoto será acrescentado
no PR #12 após push, com head/base, checkout real, árvores, run ID/event/conclusion.
Cópia local ignorada: .harness-results/WORK-CFG-028/review-f/remote-ci.json.
CI literal de HEAD será distinguida de merge sintético com árvore equivalente,
preservando o contrato I já aprovado e sem modificar workflow/coletor nesta rodada.

Nenhum Java/POM, fixture, pin ou sibling modificado. authorized_wave=null;
W1–W5 NOT_STARTED/NOT_AUTHORIZED. Performance/full continuam UNAVAILABLE sem hook
produtivo. Último commit/CI serão registrados no mesmo PR após push, com checkout
real e comparação de árvores conforme o contrato I já aprovado. Parar para review.
