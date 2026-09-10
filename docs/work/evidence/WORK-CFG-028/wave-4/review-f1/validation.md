# W4-F1 — SiteQuery com zero matches

Remediação focal do review REQUEST_CHANGES de
`330d63427c0905e8ef140924b643e9fb012c73de`, no mesmo PR #12 draft e branch
feat/cp5-dataflow-engine. [Review original](review.json) registrado antes da correção.
W1/W2/W3 continuam APPROVED; W4-BINDING-01 continua RESOLVED. W5 não autorizada.

## Correção

Antes, SitePlanner só conhecia o batch de SiteQuery após executar a função de query
para um match. Bucket vazio ou filtro que rejeita todos deixava uma dependência
explicitamente declarada sem binding e produzia erro de planning.

Agora o planner registra o binding de cada SiteQuery ao compilar os interesses,
antes de percorrer buckets ou avaliar filtros. A mesma função privada bindBatch
valida declarações e requests concretos: provider, key/profile/options, projection,
subject/fact types, dependências explícitas do consumer e colisões de batch ID.
Nenhuma função de query é chamada para registrar metadados. Nenhuma
ObservationRequest vazia é exigida ou fabricada como sentinela.

A política de dependências permanece intacta: a análise declarada é executada;
um batch declarado com zero queries passa pelo materializer W3 existente e retorna
COMPLETE vazio. Consumer com zero matches fica COMPLETE sem callbacks/facts,
desde que suas dependências concluam. Preparação é COMPLETE nessas condições.
Batch desconhecido ou declaração inválida continua sendo erro de planning.

Produção alterada somente em SitePlanner, sem API pública nova. Implementação:
`7cf1b397b14c25b91489fb8e49faf65835f923d2`.
[Preservação](preservation.json): 123 arquivos Java/POM/pin revisados byte-exact,
inclusive runtime/provider/cache/FactSink, testes antigos, AnalysisSession e W1–W3.
O [baseline](baseline.json) é conferido contra os blobs Git do HEAD revisado pelo
gate de scope. A validação de lifecycle exige o review e seu blocker explícito.

## TDD e oracles

[RED inicial compilado](development/initial-red.log.gz): três testes falharam antes
da correção. Os dois casos positivos falharam com a exceção original de dependência
sem batch. O terceiro detectou que o filtro era chamado antes de rejeitar a
declaração inválida. [GREEN focal](development/focal-green.log.gz): os três passaram
sem mudar suas expectativas.

| Oracle | Input/seleção | Resultado verificado |
| --- | --- | --- |
| absentKindKeepsDeclaredEmptyBatch | Assign + Return; interesse Branch | candidatos=0, matches=0; batch conhecido vazio COMPLETE |
| rejectingFilterKeepsDeclaredEmptyBatch | Return; interesse Return, filtro false | candidatos/visitas/filtros=1, matches=0; mesmo batch vazio COMPLETE |
| zeroMatchDeclarationsValidateBindingsBeforeSelection | declarations inválidas com função de filtro que falharia se chamada | rejeita falta de key/batch declarado, projection errada, fact type errado e options desconhecidas antes do filtro |

Nos dois casos positivos: requests=uniqueQueries=observations=sequencesReplayed=
operationsReplayed=0; análise STABLE executada uma vez; batch materializado uma vez
pela W3 real; consumerInvocations=factsStaged=factsCommitted=factsDiscarded=0;
consumer e preparação COMPLETE. Query factory e consumer falham deliberadamente
se forem chamados. A registration contém requests=[] e nenhuma sentinela.

## Challenge compilado

[Recibo](challenges-attempt-1/receipt.json): GREEN de todos os 27 testes W4 →
mutante compilável → RED nominal → restauração byte-exact → segundo GREEN.
Três mutantes executados, todos válidos, nenhuma tentativa inválida nesta campanha:

- site-query-zero-match-drops-required-batch: remove o registro antecipado e
  reproduz o comportamento anterior; RED em absentKindKeepsDeclaredEmptyBatch.
- wrong-batch-bound-to-analysis-key: preserva a proteção de binding após extrair
  a validação para o helper compartilhado.
- resource-cap-on-sites: continua detectado no probe de escala. Sua âncora passou
  à posição após seleção, pois a criação do mapa de batches agora ocorre antes dela.

Os recibos originais dos 26 mutantes W4 permanecem intactos; não se declara uma
nova execução integral daquela campanha. O inventário agora contém 27 mutantes.
[Transport](transport.json) mapeia diffs comprimidos sem alterar bytes brutos;
logs gzip e hashes de compile/RED/segundo GREEN foram conferidos.

## Gates e limites

[Gates locais](local-gates-attempt-1/receipt.json): fast, architecture, semantic,
integration e mvn clean verify PASS/0. Maven: 243 testes, zero falhas/erros/skips
(240 anteriores + 3 novos); harness: 47 + 99 testes. Inventário compilado W4
permanece byte-exact; nenhum update de classes/javap/jdeps/API foi necessário.
Performance retornou UNAVAILABLE/3 após W1/W2/W3/W4 PASS; W5 continua indisponível.
[Full final](final-local/receipt.json): UNAVAILABLE/3 após todos os gates locais e
W1/W2/W3/W4 PASS; somente W5 indisponível. Log bruto e hash preservados.
Os testes antigos são preservados e os três novos métodos entram no inventário
nominal de semantic/performance; não há suite ignorada ou teste habilitado só localmente.

A mudança não altera retenção, solver, replay, qualidade, cache ou política de
recusas. GC/JFR da W4 original não foi reexecutado nesta remediação; seus recibos
continuam históricos. S5/S6/S8/S14/S15/S16 são regressões dos gates existentes.
W3-PERF-01 e W3-METRICS-01 continuam abertos, não bloqueantes. Nenhum cap, resolver
de domínio, writer/CLI, dependência, repin ou modificação de sibling.

## Checkpoint

W4-F1 IMPLEMENTED para novo review; W4 IMPLEMENTED / AWAITING_HUMAN_REVIEW.
O REQUEST_CHANGES humano permanece no histórico; nenhuma aprovação automática.
Full concluído; a CI do HEAD remoto final é verificada antes do handoff.
Recibo CI final no mesmo PR #12 draft e cópia local em
.harness-results/WORK-CFG-028/wave-4/review-f1/remote-ci.json. O recibo remoto é
publicado após o último push, sem criar commit autorreferente.
W5 NOT_STARTED / NOT_AUTHORIZED; parar para review humano.
