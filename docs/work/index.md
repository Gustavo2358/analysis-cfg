[Tabulação de ativações e lower unificado — DONE / MERGED](../engineering/lower-unification.md#fechamento-aprovado--2026-10-03): CFG #62; 560 casos com correspondência auditável e sem diferenças inexplicadas.

[Limpeza do snapshot de fontes](../engineering/source-snapshot.md): evidência bruta histórica preservada no Git; fixtures e baselines executáveis mantidos.

[JSON Zstandard — fechamento aprovado](../engineering/json-zstd.md#qualificação-e-fechamento): PR #60; qualificação, compatibilidade e benchmark completo. O PR registra a integração.

[Integração atual — DONE / MERGED](air-codec-latency.md): corpos compartilhados #57 e latência #58, com produtores em main. As entradas anteriores abaixo são históricas.

[CardDemo values/control — DONE / MERGED](carddemo-values-control-integration.md): capabilities 1–4 and review corrections integrated; point 5 is a separate campaign.

# Trabalho

[CardDemo FILE e W0–W8 — DONE / MERGED](carddemo-control-integration.md): implementação qualificada e integrada; limites e campanhas futuras separados.


[POSITIVE_MEMORY_TOPOLOGY — W1](../campaigns/positive-memory-topology/W1-REPORT.md): IN_PROGRESS / W1 READY_FOR_REVIEW em DRAFT #45; W2/W3 não iniciadas.

[FILE-DEPENDENCIES — CORE N+C CLOSED](../product/file-dependencies/closeout.md): H0–H4/W0–W9/W11 DONE; merges e main smoke PASS; W10 opcional/deferred, não faz parte do core.

[EP-R2 — Order-Independent Entry & Factorized Recall](ep-r2-handoff.md): aprovado e integrado em main pelo merge 6ef181d; semântica preservada como requisito da reconciliação File Dependencies.

[WORK-STORAGE-W6-W8](WORK-STORAGE-W6-W8.json): W6/W7 G2 and W8 G3 qualified; IN_PROGRESS pending human review/merge. Extends the accepted W0–W5 work item without reopening it.

Campanha atual: [WORK-STORAGE-CFG-001 — Storage Semantics ST-W0..ST-W5](active/WORK-STORAGE-CFG-001.yaml), IN_PROGRESS; [contrato](../domain/storage-semantics.md). Promove somente os residuais de storage/RD/values do CP5 e preserva os marcos históricos abaixo.

Atual: [WORK-CFG-039](active/WORK-CFG-039.yaml), full CardDemo baseline e análise consultiva. WORK-CFG-038: DONE / MERGED #24. [Invariantes permanentes](../architecture/compositional-partial-lowering.md).

[WORK-CFG-033](history/WORK-CFG-033.md): CP6 W1D e CP6 W1 APPROVED / MERGED / CLOSED, PR #18. [Baseline W1 congelado](cp6-lifecycle.json) para a próxima wave. [WORK-CFG-034](history/WORK-CFG-034.md): CP6 W2D COMPLETED / MERGED, PR #20; W2 APPROVED / MERGED / CLOSED.

[WORK-CFG-035](history/WORK-CFG-035.yaml): CP6 MOVE→MOVE DONE / MERGED, PR #21; [escopo e validação](history/WORK-CFG-035.md).

[WORK-CFG-036](active/WORK-CFG-036.yaml): CP6 PERFORM BASIC, DONE / MERGED #22, `e200b101455fa7addf59413ce5e6548c03d9116f`.

CP5 = APPROVED / MERGED / CLOSED. The verified frozen component authorities are synchronized. Final source/artifact identity is recorded by the post-merge protocol in `artefatos-e2e/pre-cp6-baseline/receipt.json` in the workspace.

[WORK-CFG-032](history/WORK-CFG-032.md): discovery APPROVED / MERGED. O lifecycle CP5 conserva o closeout anterior; o trabalho corrente é avaliação full CardDemo em WORK-CFG-039, autorizada na sessão e registrada no item Lean.

## Itens concluídos

- [WORK-CFG-030](history/WORK-CFG-030.md): final baseline synchronization and CP5 closeout.
- [WORK-CFG-031](history/WORK-CFG-031.md): registry and javap diagnostic harness fixes, merged PR14.
- [WORK-CFG-029](history/WORK-CFG-029.md): RESOURCE_LIMIT compatibility, merged PR13.
- [WORK-CFG-028](history/WORK-CFG-028.md): CP5 W1–W5 approved and merged PR12.

[Registry](registry.json), [backlog](backlog.md), [lifecycle](cp5-lifecycle.json), [follow-ups](cp5-follow-ups.md). Historical STOPs are investigation history; W3-PERF-01/W3-METRICS-01, 117k qualification, broad heap/streaming optimization, lower amplification and IMPLEMENTATION_LIMIT naming remain nonblocking backlog.

- WORK-CFG-036: DONE / MERGED #22 (`e200b101455fa7addf59413ce5e6548c03d9116f`); record retained in active path for stable links.
- [WORK-CFG-037](active/WORK-CFG-037.md): CP6 multi-CALL program coverage, DONE / MERGED; baseline for WORK-CFG-038.

- [WORK-CFG-038](active/WORK-CFG-038.md): CP6 compositionality and conservative partial lowering, DONE / MERGED #24 (`18a78a6599bfad2d983a4a32d7515d379a9bac60`).

NEXT: **HUMAN PRIORITIZATION USING FULL CARDDEMO BASELINE**. O corpus fornece evidência consultiva; não seleciona nem determina a próxima capability. [Baseline](../evals/cp6/carddemo-full-baseline.md).

[WORK-FD-HARNESS](active/WORK-FD-HARNESS.json): IN_PROGRESS; preparação para revisão, sem merge.

[FD-W0–W11 — itens TODO e dependências](../product/file-dependencies/waves.md): execução futura após revisão H4.

## Composite FILE control — review

[Scope and contract](file-composite-control.md); [qualification](file-composite-qualification.md).
SP2.51 per-use control for OPEN/CLOSE and SORT/MERGE without procedure callbacks.
IN_PROGRESS: implemented and qualified locally, awaiting review; no merge.

- [Stage5 JSON GENERATE — checkpoint 4](stage5-json-generate.md): authority repin, source alternatives and qualification.

[Prioridade 2 — valores numéricos e texto](numeric-text-values.md): revisão aprovada; o merge do PR #64 efetiva DONE. [Integração](priority2-integration.md).
