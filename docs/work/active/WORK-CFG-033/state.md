# WORK-CFG-033 — state

## Onde estamos
W1D = IMPLEMENTED / AWAITING_HUMAN_REVIEW. CP6 W1 = IMPLEMENTED / AWAITING_HUMAN_REVIEW. W1A/W1B/W1C APPROVED / MERGED per user authority. W2 NOT_STARTED / NOT_AUTHORIZED. O work item permanece active, sem aprovação inferida.

## Verde conhecido
34 nominal W1D tests, four strict independent wire tests and 20 mutations passed; byte-exact restoration and second GREEN observed. Canonical full passed continuously: docs/fast, architecture/boundaries, CFG oracles, W1–W5 semantics/performance and CP3/CP4E/overwrite integration. The final real vertical ran twice with identical bytes, including the original MOVE-line oracle, literal zero-values and no-MOVE/USING negatives. The published CLI wrapper produced identical dependency bytes.
analysis-cfg origin/main SHA and tree match required baseline; branch created from exact SHA. Four sibling sources exported at required SHAs; occupied checkouts clean and unchanged.

[Handoff local e evidências](../../evidence/WORK-CFG-033/README.md), [gates](../../evidence/WORK-CFG-033/validation-receipt.json). As três tentativas anteriores de full e suas causas permanecem preservadas; não contam como PASS. As correções mantêm os seletores históricos e seus testes, os hashes dos fixtures e a exigência de pins exatos.

## Restante
Revisão humana. Commit/HEAD/tree, Draft PR e CI remota são registrados após congelar esta árvore, no handoff de entrega externo e na descrição do PR. O estado de testes locais não substitui esse receipt remoto. Não fazer merge, auto-merge ou iniciar W2.

## Descobertas que afetam o plano
Solver code lives in analysis-kernel/solver in this reactor (no separate analysis-solver module). Protect those actual sources byte-exact. Maven dependency download requires network-enabled execution.
