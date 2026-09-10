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

## WAVE_2 — checkpoint autorizado atual

W1 APPROVED em b84389b6. Implementar solver genérico incremental, direction-aware,
IN/OUT na ordem de execução, boundaries declarativas, primeira publicação,
contextos isolados e agenda justa. Domínios forward/backward, oracle por recomposição
e oracle concreto finito são test-only; S4b, escala N/2N/4N, retenção e challenges
compiláveis com restore byte-exact. Gates locais, Maven, push e CI do HEAD final.
Sem W3–W5, sem repin ou alterações de siblings. Parar para review humano W2.

### Evidência W2 executada

[Validação](../../evidence/WORK-CFG-028/wave-2/validation.md): solver/oracles,
26 mutantes válidos e gates locais executados. W2 IMPLEMENTED / AWAITING_HUMAN_REVIEW.
Recibo CI do HEAD final no mesmo PR #12; W3–W5 não autorizadas.

## WAVE_3 — checkpoint autorizado atual

W2 APPROVED no HEAD 0202c7424db04a1d83fb5e35fce055ea81f5b8fa. Autorização append-only em
[wave-3/authorization.json](../../evidence/WORK-CFG-028/wave-3/authorization.json),
registrada antes de produção. Implementar analysis-values scalar-text-direct@1,
Cell/disjunção, domínio sparse sem cap, strong Assign e queries batch por direção.
Preservar fontes W1/W2 e pins; oracles reais/concretos, escala, retenção, mutantes,
gates locais e CI do último HEAD. W4/W5 NOT_STARTED / NOT_AUTHORIZED.


### Evidência W3 executada

W3 IMPLEMENTED / AWAITING_HUMAN_REVIEW. [Validação e limites](../../evidence/WORK-CFG-028/wave-3/validation.md).
Gates locais e 28 mutantes compiláveis distintos; CI final no mesmo PR draft #12.
W1/W2 aprovadas, W4/W5 indisponíveis e não autorizadas.


## Remediação W3-F1/W3-F2

Review do HEAD 8cb55b86c83644e4727cc532787a518775d7e868 solicita somente support de
candidato com AIR producer/origin/premises e abertura da fonte por EntryState e
alias relevante da mesma Cell. Preservar W1/W2, AVL, solver e replay. Testes RED
compiláveis antes da correção; provar strong kill, join de suportes, seeds/premissas,
Entry gaps e aliases. Repetir gates/escala/retention e mutations focais, CI do HEAD
final no mesmo PR draft. W4/W5 não autorizadas.


### Remediação focal executada

[Validação W3-F1/F2](../../evidence/WORK-CFG-028/wave-3/review-f1-f2/validation.md):
11 testes W3 novos, 18 mutantes compiláveis detectados, suporte até 10k produtores,
retenção externa até 200k writes e gates locais/regressões. Estado IMPLEMENTED /
AWAITING_HUMAN_REVIEW; REQUEST_CHANGES original preservado no histórico.
CI do HEAD final no mesmo PR #12 draft. W4/W5 não autorizadas.


## WAVE_4 — autorização vigente

W3 APPROVED em 855628200fba3851493991cec869dee899e82299; W3-F1/W3-F2 RESOLVED.
[Registro anterior à produção](../../evidence/WORK-CFG-028/wave-4/authorization.json).
Implementar planning genérico por buckets W1, AnalysisKey completo, providers explícitos
e binding inseparável, reuse de runs e união de batches W3. ConsumerPlan imutável,
dependências locais, SPI restrita e FactSink transacional por consumer.
Provar S5/S6/S8/S14/S15/S16, vertical PossibleValues real, determinismo, retenção,
mutantes compiláveis com RED/restauração/segundo GREEN e regressões W1–W3.
Atualizar hooks W4, executar gates/Maven/full, publicar CI do HEAD final no PR #12 draft.
W5 NOT_STARTED / NOT_AUTHORIZED; nenhum resolver, writer/CLI ou sibling alterado.

## W4 implementada

[Validação e limites](../../evidence/WORK-CFG-028/wave-4/validation.md): planning por
buckets, provider/key, reuse, dependências e staging; 24 testes W4, 26 mutantes
válidos, escala/GC/JFR e regressões. W4 aguarda review humano; W5 não autorizada.

## Remediação focal W4-F1

[Review](../../evidence/WORK-CFG-028/wave-4/review-f1/review.json): registrar batch de
SiteQuery antes dos matches. Dois oracles (kind ausente/filtro rejeita todos) e
validação antecipada de declarations. Mutante de retorno ao comportamento antigo,
restauração byte-exact/segundo GREEN e regressões dos gates; nenhuma outra produção
além de SitePlanner. Preservar histórico W4 e W1–W3. W5 não autorizada.

## WAVE_5 — autorização vigente

W4 APPROVED em 21d65d08512f1fb8a945009c2919946a61566eed; W4-F1/W4-BINDING-01 RESOLVED.
[Autorização](../../evidence/WORK-CFG-028/wave-5/authorization.json) registrada antes de produção.
Compor Publication → BuildCfg → AnalysisSession → default plan → PlanningExecution
→ PreparedAnalysisResult, wire explícito/parser, writer streaming local e receipt externo,
CLI separada. Provar CP4E duas vezes e CP3 com stages reais, equivalência/determinismo,
S1–S10/S14/S15/S16, falhas, challenges e retenção. Preservar W1–W4, pins e siblings.
Gates locais/Maven/full, push e CI do HEAD final; parar para review humano W5/CP5.
CP6, merge, ready, auto-merge e repins não autorizados.
