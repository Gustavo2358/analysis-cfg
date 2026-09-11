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

## Remediação PR #18 — wire e orquestração

Baseline desta correção: `1ab16bdeae8d8af23e723d0b239ba191a695764a`, tree
`ba8416dbe4d38f725d76b0a13278e5acb5296340`; branch existente alinhada ao remoto,
working tree inicialmente limpa e PR OPEN / DRAFT, sem auto-merge.

O contrato v1 permanece byte-exact; a nova decisão declara analysis-cfg-json 2.0.0
para os elementos Invoke efetivamente presentes no CFG. O writer é a única fonte
de produção alterada nesta remediação. RED/GREEN do wire está preservado; os cinco
casos novos/fortalecidos e todos os 42 métodos do transporte passaram localmente.

PR CI = FAST. FULL QUALIFICATION = EXPLICIT / LOCAL OR MANUAL REMOTE.
O estado de qualification desta remediação é determinado pelo receipt do HEAD
final, produzido após o commit e antes do push, conforme o
[protocolo](../../../engineering/qualification.md). A entrega e o PR registram
HEAD/tree, caminho/hash e resultado observado; este arquivo não antecipa PASS.

A evidência W1D anterior permanece íntegra, inclusive o CFG indevidamente rotulado
v1; seu sucesso de execução não deve ser confundido com conformidade wire.
O objetivo final permanece IMPLEMENTED / QUALIFIED / AWAITING_HUMAN_REVIEW, com
PR OPEN / DRAFT. W2 permanece NOT_STARTED / NOT_AUTHORIZED.

Fast local observado: PASS em 245.893 s, 286 métodos, zero skips, todos os
boundaries. Challenge de versão: três mutantes compiláveis detectados por
assertions (3/3/1 failures), restauração byte-exact e segundo GREEN. Receipts e
primeiro Fast falho estão preservados no pacote de remediação.
