# WORK-CFG-028 — Estado

## Onde estamos

CP5 harness preparation implementada / awaiting human review. Branch
feat/cp5-dataflow-engine, [PR #12 draft](https://github.com/Gustavo2358/analysis-cfg/pull/12); última aprovação no [lifecycle](../../cp5-lifecycle.json).
As cinco Waves estão NOT STARTED; W1 está NOT AUTHORIZED. Nenhuma Wave automática.

## Verde conhecido

Arquitetura H1–H7/R1/R2 aprovada pelo pedido humano de 09/09/2026.
Ver [evidência de validação](../../evidence/WORK-CFG-028/validation.md) para comandos,
resultados, SHAs e limites; aprovação arquitetural não é PASS de engine.

Review humano do HEAD 6675b1f9ff9d11e8f405f6dcf49c5acb0096346a pediu somente B1:
resultado por query incapaz de expressar lote misto. A correção acrescenta
queryStatus/queryReason, oracle before(Return) + after(Return), cobertura do plano
e contracasos de aborto/omissão. [Recibo B1](../../evidence/WORK-CFG-028/review-b1/validation.md)
com gates e limites; correção aguarda novo review, sem autorização de Wave.

## Restante

Review humano desta preparação e autorização explícita W1 em tarefa separada,
na mesma branch e no mesmo PR draft. Sem merge, auto-merge ou ready for review.

## Descobertas que afetam o plano

027 roteava review apesar do merge confirmado; arquivado preservando o registro.
020/012/019 reaproveitados; debts externos 017/018 permanecem fora do escopo.
Performance/full continuam UNAVAILABLE, sem hooks engine antes das Waves.
CP5-F01 foi reconhecido pelo review como dívida preexistente não bloqueante;
dependência AIR direta do launcher permanece para decisão/execução autorizada em W1.
