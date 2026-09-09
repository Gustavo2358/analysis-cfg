# Evals e gates

## O que prova corretude

EVAL-CFG-033 verifica harness: manifestos/IDs/links, ordem/autorização das Waves,
ADRs e backlog, métricas/probes/challenges completos, snapshot de design consistente,
inventário Java/POM byte-exact e ausência de implementação CP5. Não prova dataflow.
EVAL-CFG-034–038 permanecem planned/NOT AVAILABLE até Waves correspondentes.

## Casos adversariais

Remover probe/S4b/métrica/ADR; iniciar ou autorizar Wave; inserir Java/POM, inclusive
fora do módulo esperado; promover performance; remover dépendência AIR direta;
resultado PARTIAL sem resto efetivo; confundir limit/saturation/unreachable; hook
falso; challenge incompleto. Mutação em cópias temporárias; nenhum produto modificado.

## Regressão e limites

fast/docs/harness + architecture + semantic + integration e Maven verify preservam
CFG/4D. scope/manifest contra base CP4. performance/full retornam UNAVAILABLE/3,
resultado esperado e explicitamente registrado. CI roda fast (inclui CP5 harness),
scope e gates de produto existentes; não há CI de solver/performance CP5 implementada.

## Self-validation pós-auditoria

Remover cada obrigação A–I (incluindo integridade, direção, effects, fases, oracle
concreto e qualidade) exige RED nominal. Alterar witness backward, retirar contagem
de recusas, afirmar pipeline completa com replay/consumer/output limitado, omitir
consumer solicitado ou declarar checkout literal sem hashes/árvores também falha.
Git temporário testa HEAD literal, merge sintético com árvore igual, árvore diferente
e recibo stale. Provas produtivas continuam NOT_AVAILABLE_UNTIL_IMPLEMENTED.

## Correção focal F

F1: ADMISSION_LIMIT aceito e UNSUPPORTED rejeitado para admission budget.
F2: payload e fixpoint preservados em writer failure; recibo externo com
identidade/hash/destino não pode contradizer o outcome observado.
F3: StructuralConsumer COMPLETE + QueryConsumer bloqueado por batch LIMIT;
zero análises, análise sem replay e batches independentes não sofrem barreira global.
