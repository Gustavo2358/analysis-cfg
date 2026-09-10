# Plano

## Fatiamento

Preparação original (histórico):

1. Base main atualizada e merge CP4 verificados, branch focal única; lifecycle 027 arquivado.
2. Destilar arquitetura e semântica aprovada, ADRs, evals/métricas/probes/challenges.
3. Guardar Waves NOT STARTED/NOT AUTHORIZED, ausência Java/POM, inventários e resultado.
4. Gates existentes aplicáveis, adversariais de harness, revisão de diff/scope/manifest.
5. Commits focalizados, push, abrir um PR draft e registrar número/URL no lifecycle.
6. Último push: HEAD exato, gates locais e checks remotos; parar para review humano.

## Remediação autorizada após auditoria

1. Ler autoridades integralmente e confrontar código/AIR pinada; preservar HEAD real feb79d59.
2. Formalizar A–I e oracles/hooks nas Waves, acrescentar RED/restore/segundo GREEN de harness.
3. Atualizar lifecycle com histórico APPROVED anterior; manter authorized_wave=null.
4. Gates locais e scope focal, regressões Maven, diff/manifest; commits/push no PR #12 existente.
5. Registrar checkout/head/base/árvores/run/conclusão reais e parar para novo review.

## Dependências

Base main ec525cbbad96d70c9663faa88e2672148fa8ee71 (4D merge), runtime air-java
ce530a7e17ab12b23c48f29425f503ff920b09fb, AIR normativa no lock inalterado.
[Baseline/autoridades integrais](../../evidence/WORK-CFG-028/baseline.json).
Uma branch feat/cp5-dataflow-engine e um PR draft até W5; sem merge intermediário.
Waves são checkpoints de review, não unidades fixas de commits ou PRs.

## Correção focal F

Review F de e053f14: reproduzir RED; corrigir contratos/snapshots e validator;
GREEN e contracasos; full/integration/Maven/scope; evidência append-only; commit/push
no PR #12 draft e recibos CI do novo HEAD. Parar para review humano.

## CORE-SIZE-001 — histórico aprovado

1. Confirmar checkout limpo, ancestralidade e PR #12 remoto, ler autoridades integrais.
2. Auditar ocorrências por papel A–F; registrar dívidas externas/legadas sem editar produto.
3. Formalizar ADR-0014; RED de contratos, remover outcomes/budgets/caps, GREEN.
4. Contracasos parseáveis de papéis/wire e N/2N/4N, restore byte-exact e segundo GREEN.
5. Gates locais aplicáveis, inventário Java/POM, scope/manifest e evidência honesta.
6. Commits/push, descrição do mesmo PR draft e recibo CI real do HEAD final; parar.

## WAVE_1 — plano histórico aprovado

1. Confirmar HEAD aprovado/remoto, PR draft, pins e autorização append-only antes de produção.
2. Expected manual e RED comportamental da sessão; implementar índice e admissão estrutural.
3. Oracles de completude/contexto/identidade e S1/S2/S4/S8/S11/S16 W1 reais; medir
   trabalho e retenção com deduplicação por identidade e ledger por coleção.
4. Corrigir CP5-F01 e inventários Maven/javap/jdeps; ativar somente hooks W1.
5. Campanha compilável GREEN/RED/restore/segundo GREEN; preservar tentativas inválidas.
6. Regressões completas, scope/diff/manifest, commits e push no mesmo PR #12 draft;
   confrontar CI do HEAD publicado e registrar recibo; parar para review humano.

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
