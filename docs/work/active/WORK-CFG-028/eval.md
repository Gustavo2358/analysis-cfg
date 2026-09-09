# Evals e gates

## O que prova corretude

EVAL-CFG-033 verifica harness: manifestos/IDs/links, ordem/autorização das Waves,
ADRs e backlog, métricas/probes/challenges completos, snapshot de design consistente,
inventário Java/POM byte-exact e ausência de implementação CP5. Não prova dataflow.
EVAL-CFG-034–038 permanecem planned/NOT AVAILABLE até Waves correspondentes.

## Casos adversariais

Remover probe/S4b/métrica/ADR; iniciar ou autorizar Wave; inserir Java/POM, inclusive
fora do módulo esperado; promover performance; remover dépendência AIR direta;
resultado PARTIAL sem resto efetivo; confundir cardinalidade/desconhecido/unreachable; hook
falso; challenge incompleto. Mutação em cópias temporárias; nenhum produto modificado.

## Regressão e limites

fast/docs/harness + architecture + semantic + integration e Maven verify preservam
CFG/4D. scope/manifest contra base CP4. performance/full retornam UNAVAILABLE/3,
resultado esperado e explicitamente registrado. CI roda fast (inclui CP5 harness),
scope e gates de produto existentes; não há CI de solver/performance CP5 implementada.

## Self-validation pós-auditoria

Remover cada obrigação A–I (incluindo integridade, direção, effects, fases, oracle
concreto e qualidade) exige RED nominal. Alterar witness backward, retirar contagem
de recusas, afirmar pipeline completa com replay/consumer/output com falha controlada, omitir
consumer solicitado ou declarar checkout literal sem hashes/árvores também falha.
Git temporário testa HEAD literal, merge sintético com árvore igual, árvore diferente
e recibo stale. Provas produtivas continuam NOT_AVAILABLE_UNTIL_IMPLEMENTED.

## CORE-SIZE-001 e F2/F3 preservados

TDD: baseline GREEN do harness anterior; novos casos RED para candidatos grandes,
capacity statuses e options. Novo contrato remove caps e preserva INVALID_INPUT,
UNSUPPORTED, reachable unknown, unreachable, PARTIAL e batch B1 misto.
Contracasos de manifests introduzem decisões por node/operation/query/worklist
count, resource options, cardinality TOP e OOM mapping; todos devem dar RED nominal.
N/2N/4N e N+1 usam inputs/expected de review independentes; nenhum solver é simulado.

F2: payload e fixpoint preservados em writer failure; recibo externo com
identidade/hash/destino não contradiz outcome observado. F3: StructuralConsumer
COMPLETE + QueryConsumer bloqueado por batch FAILED controlado; zero análises,
análise sem replay, consumer independente com falha e batches independentes.
Partialidade por recursos é proibida. Provas de runtime continuam indisponíveis.

## WAVE_1 — provas reais

EVAL-CFG-034 agora é executável: AdmissionTest e StructureTest fornecem expected
manual e contracasos; ScaleTest executa BuildCfg e a sessão real para S1/S2/S4/S8/S16.
S11 é a admissão adversarial da primeira suíte. O gate nominal lista explicitamente
métodos esperados e rejeita ausência/skip/falha ou medições omitidas.

check_w1.py architecture inspeciona sources/classfiles, javap, jdeps e Maven;
challenge_w1.py aplica 15 mutantes compiláveis e exige motivo RED nominal,
restauração byte-exact e segundo GREEN. Testes de harness preservam contratos
W2–W5 e recusam ativação automática, inventário oculto e hooks vazios.
Não há thresholds semânticos de tempo/heap. Full global conserva UNAVAILABLE/3
para W2–W5 mesmo depois de executar os probes W1.
