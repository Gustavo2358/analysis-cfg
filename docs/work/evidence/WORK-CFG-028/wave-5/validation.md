# CP5 WAVE 5 — implementação pronta para review humano final

W5 IMPLEMENTED / AWAITING_HUMAN_REVIEW; CP5 AWAITING_FINAL_HUMAN_REVIEW.
W1–W4 APPROVED; W4-F1/W4-BINDING-01 RESOLVED. CP6 NOT_STARTED / NOT_AUTHORIZED.
Este registro comprova execução local; o recibo remoto do HEAD final é publicado
no mesmo PR depois do último push, sem commit que inclua seu próprio SHA.

## Identidade, autorização e preservação

- Repo: analysis-cfg; branch `feat/cp5-dataflow-engine`; mesmo [PR #12](https://github.com/Gustavo2358/analysis-cfg/pull/12), OPEN/DRAFT, sem auto-merge/ready/merge.
- Baseline W4 aprovado: `21d65d08512f1fb8a945009c2919946a61566eed`.
- Primeiro commit lógico de autorização: `ffdf2807d39186f2f204d2d53f70196a5d735ddd`, anterior à produção W5.
- Commit de produção e testes W5: `e4e3eb2d337a146c3984385001349868d386952e`.
- Base main: `ec525cbbad96d70c9663faa88e2672148fa8ee71`.
- Pin air-java/air-json: `ce530a7e17ab12b23c48f29425f503ff920b09fb`.
- Pin analysis-ir: `122ce54e1b9ef9b00646f93ece409ca8b63bc933`.
- [Autorização](authorization.json), [baseline](baseline.json), [before](siblings-before.json), [after](siblings-after.json).

| Sibling | SHA before = after | Branch | Estado preservado |
| --- | --- | --- | --- |
| proleap-poc | `8722945cc4cd2052c6091533f6ee6989278aa2f8` | `main` | clean |
| cobol-lower | `2329993ce61b33fd7105759e211a1861ca6cb217` | `feat/lower-scalar-move` | clean |
| air-java | `17029898fd0ee8fabcaaae89f7260148633d4b12` | `main` | clean |
| analysis-ir | `51b4d9a8ae0364232bd97103cd73a77e1a34996c` | `main` | clean |
| artefatos-e2e | `2484b76f9174a79ea702f17453f04955cd46a755` | `main` | untracked prévios checkpoint-4e/ e cp5/ |

Os checkouts locais air-java/analysis-ir são mais novos que os pins consumidos;
isso está registrado e não constitui repin. Produtores foram construídos em
exports imutáveis `git archive` sob `/tmp`, nunca nos siblings. A rodada anterior preservada em producer-build/ tinha o log do primeiro comando
do lowering sobrescrito pelo segundo; seus hashes continuam no recibo, mas ela
não prova retenção de ambos os logs. A correção usa um arquivo por comando;
producer-build-sealed/ e e2e-sealed/ são a rodada final, com todos os logs presentes.
O build de binários
não é claim de execução dos testes dos siblings. [Comandos, SHAs e classpaths](producer-build-sealed/producers.json)
e logs brutos estão preservados. A reconstrução final do JAR frontend alterou
metadados do ZIP; os payloads de todas as entradas são idênticos ao primeiro build.
Os hashes abaixo são dos binários efetivamente executados na rodada final.

W1–W4 e CFG legado mantêm Java, testes e POMs byte-exact; somente o parent POM
recebe três módulos. Nenhuma alteração aditiva W1–W4 foi necessária. O coletor
`ci_source_receipt.py` permanece byte-exact. Contratos design 1.0.0 anteriores
foram arquivados, ainda verificados contra os blobs Git aprovados.

## Composição e plano produtivo

`AnalysisDataflow.analyze(Publication, resultId)` entrega o PreparedAnalysisResult
W4 em memória; `prepare` captura o wrapper detached necessário ao transporte.
BuildCfg real usa registry explícito vazio e defaults atuais. Invalid AIR, profile
não suportado, build incompleto e limitação upstream são falhas distintas; nenhuma
sessão/fixpoint é fabricada. AnalysisSession seleciona Entries canônicas e reutiliza
W1; AnalysisRegistry registra PossibleValuesProvider explicitamente; SitePlanner /
PlanningExecution usam W4, solver W2 e profile scalar-text-direct W3. A execução é
fechada antes de devolver o wrapper; nenhuma cópia de solver/replay/semântica W3.

O plano visita o bucket indexado de Assign uma vez, agrupa ObjectPlace por
Unit/Sequence e deduplica `(EntryId, SequenceId, ObjectId)`. Observa BEFORE o
terminador real Return/Jump/Branch/Halt. Registra um consumer genérico por Entry,
com referências explícitas ao batch/query e SequenceId completo. Não examina
nomes, COBOL, PROGA, WS-PGM ou CALL. Sequence sem writes não gera query. Programa
sem writes gera preparação completa vazia, zero análises/batches/consumers e nenhum
STABLE inventado. O caso órfão preserva UNREACHABLE.

## Wire e observações

`prepared-analysis-result` e `analysis-dataflow-result` usam **1.1.0**;
`analysis-delivery-receipt` permanece **1.0.0**. A revisão em relação ao desenho
1.0.0 explicita analyses versus batches, sourceScope, IDs de owners, suporte por
candidato e fatos genéricos referenciados. [Contrato normativo](../../../../architecture/analysis-dataflow-result-v1.md)
e snapshots atuais são guardados separadamente do histórico.

A codificação é manual, sem reflexão sobre DTOs. IDs incluem domain e toda a
hierarquia de owner, inclusive Operand owner. VALUE conserva candidates,
modelValueRemainder, sourceUnknownRemainder, OR efetivo, candidateSupports e cada
producer com evidence/origin/premiseRefs; agregados evidence/provenance/premises
continuam presentes. Não há transitive closure, reaching-definitions ou path
witness inferido. UNSUPPORTED não inventa campos semânticos; UNREACHABLE não vira
Candidates vazio. PARTIAL não é promovido a exaustivo.

Um profile real recusado W3 mantém prepared INCOMPLETE, batch NOT_STARTED com
DEPENDENCY_UNAVAILABLE e consumer dependente indisponível. Sua entrega local pode
ser COMPLETE sem promover preparação/análise. O teste nominal real expôs perda
do batch.reason no wire; RED compilável e correção estão preservados, e o 19º
mutante impede regressão. A entrada analysis-only sem batch conserva NOT_STARTED
sem inventar falha. F3/EXPLICIT_PARTIAL_BY_DEPENDENCY e consumers independentes W4
continuam exercitados pelos gates consolidados.

O parser Python independente rejeita versão/status/owners inválidos, suporte
perdido/trocado, precision/remainder incoerentes, receipt embutido, duplicatas e
números não finitos. Verifica resultId/hash/destino e byte alterado contra receipt.
Ele é um leitor nominal do resultado, não um segundo AirValidator.

## Writer, receipt e CLI

UTF-8 estrito, strings sem normalização Unicode, controles escapados e um LF final.
Chaves e conjuntos têm ordem explícita independente de input/registration order.
Writer streaming não materializa byte array do resultado completo; ordenação exige
coleções transitórias e strings existentes continuam parte do payload. Hash SHA-256
incide sobre os bytes efetivamente escritos; tamanho mede UTF-8 escapado entregue.
Temp no mesmo diretório, close, move ATOMIC_MOVE + REPLACE_EXISTING. Sem fallback
não atômico, retry, fsync ou garantia de durabilidade contra crash.

Receipt é externo. COMPLETE exige finalização bem-sucedida, resultId do chamador,
SHA completo e destino absoluto normalizado. FAILED distingue ENCODING_FAILED,
WRITE_FAILED e FINALIZATION_FAILED; falha antes de encode+close completos tem hash
null, nunca hash de prefixo. Finalization failure pode conservar hash completo.
Arquivo final anterior permanece íntegro; cleanup é best effort com contador.
Falha do writer não muta prepared/facts/fixpoint; OutOfMemoryError controlado
propaga como Error, sem receipt/estado semântico de limite.

CLI: `io.github.gustavo2358.analysis.launcher.AnalysisDataflow input.air.json output.result.json --result-id stable-id`.
stdout transporta um receipt externo quando houve tentativa de entrega; stderr
explica erros esperados sem stack trace. Exit: 0 completo; 2 usage; 3 input
IO/transport/version; 4 AIR inválido/profile BuildCfg; 5 execução/preparação incompleta;
6 entrega/stdout; 7 capacidade upstream. CFG CLI/reader/writer anteriores intactos,
com seus testes reais no clean verify/integration.

## CORE-SIZE-001 e limites honestos

Reader W5 usa readAllBytes sem pre-read cap local e mantém codec/validação reais.
Isso ainda custa um array de input: não é claim de streaming AIR. O codec pinado
continua com 16 MiB, depth128 (máximo configurável256); AirValidator conserva
nesting/entities/issues limits. AIR válido mais 17 MiB de whitespace resulta exit7
EXTERNAL SIZE-CAP DEBT e nenhum output, não unsupported/INVALID_INPUT CP5.
Não houve repin ou bypass de validade.

Writer W5 não tem cap 64 MiB: uma publicação real em memória com literal de 36 MiB
produziu **75.508.329 bytes**, receipt COMPLETE. Queries/facts/operations não têm cap
local. N/2N/4N mantém todas as respostas e suporte final, sem perda de precisão.
Isso não qualifica grandes AIRs pela rota de arquivo. Programa de 117k não foi
executado nem usado como gate obrigatório; generalização ampla não é alegada.

## E2E fresco e hashes executados

[Recibo E2E final](e2e-sealed/receipt.json) registra quatro casos, PIDs, argv/cwd,
exit codes, tempos, input/output/stdout/stderr hashes e todos os JARs reais.
Cada stage executa processo, exige output novo, sela bytes e confere a fronteira
antes do stage seguinte; snapshots esperados nunca substituem outputs produtivos.
[Índice de arquivos brutos](archive-index.json) registra hashes antes/depois de gzip.
Receipts arquivados mantêm o destino original: arquivar evidência não é nova entrega.

| Binário efetivamente executado | SHA-256 |
| --- | --- |
| antlr-parse-tree-explorer-1.0.0-SNAPSHOT.jar | `eb55e277426e0d894c5edce403b0344c4c4a5d5a2a276bde42563737aa986ee9` |
| cobol-lower-adapters-0.1.0-SNAPSHOT.jar | `6d268df76c0c77386d618a05cf0e8c31e277409f3757b78157d688d036913ef2` |
| cfg-kernel-0.1.0-SNAPSHOT.jar | `ff1f689433050d8385ec9367a0efe1f18649cfd9551ed5d8546fb882489ba762` |
| analysis-kernel-0.1.0-SNAPSHOT.jar | `a66876db4f0fa7b5563f60da5484ab740f95099b33433049693c43bbb9699fce` |
| analysis-values-0.1.0-SNAPSHOT.jar | `e00325110ec9ab826d7ee531fd6c91eac1f69bbce48f25463974992b945b0229` |
| analysis-dataflow-0.1.0-SNAPSHOT.jar | `39356658e10b43270ff638b421db96b262a00a0fd08c095e7617afdd67c2c404` |
| analysis-adapters-0.1.0-SNAPSHOT.jar | `356b6120df21997ef450cefc953c131ef93b25098f7297cb7dac84d722fe9049` |
| analysis-launcher-0.1.0-SNAPSHOT.jar | `a7bd521d902bf8d99e7bd009f54f30905d572dda42c0f73a534d4d80ca781f6c` |
| air-java-0.1.0-SNAPSHOT.jar | `1765d3ade2882f92dca9f588143b71928e7852c4ee39ecf922b8e846e21904dd` |
| air-json-0.1.0-SNAPSHOT.jar | `3816358bbe1052e3dfc5674461ae998082323be38e48bc13567b2052a753e539` |

### cp4e-a

- cobolSha256: `3a35079c4954548bca08b4b298583c2b29a336d73432b529cac939d9b9013113`.
- spSha256: `468e3207f578e428ace89a311eadbd6e27c670331b739675b761479352adc7af`.
- airSha256: `dd3bb4819282e609a97937ea01b7e202786e2c2d9ba9c8a1821a8b982eeb8788`.
- resultSha256: `aacb6e6cedbd7d1dbbd44e7a476b398df3eeff51f1a63b90257d4dc412705dec`.
- Stages frontend/lower/analysis/memory: exit0; memoryFile `SEMANTIC_AND_BYTE_IDENTICAL`.
- Candidato: `PROGA`; receipt `COMPLETE`, resultId `canonical-cp4e`.
- Destino efetivamente entregue: `/home/gustavo/workspace/teste-e2e/analysis-cfg/.harness-results/w5-e2e-sealed/cp4e-a/result.json`.

### cp4e-b

- cobolSha256: `3a35079c4954548bca08b4b298583c2b29a336d73432b529cac939d9b9013113`.
- spSha256: `468e3207f578e428ace89a311eadbd6e27c670331b739675b761479352adc7af`.
- airSha256: `dd3bb4819282e609a97937ea01b7e202786e2c2d9ba9c8a1821a8b982eeb8788`.
- resultSha256: `aacb6e6cedbd7d1dbbd44e7a476b398df3eeff51f1a63b90257d4dc412705dec`.
- Stages frontend/lower/analysis/memory: exit0; memoryFile `SEMANTIC_AND_BYTE_IDENTICAL`.
- Candidato: `PROGA`; receipt `COMPLETE`, resultId `canonical-cp4e`.
- Destino efetivamente entregue: `/home/gustavo/workspace/teste-e2e/analysis-cfg/.harness-results/w5-e2e-sealed/cp4e-b/result.json`.

### cp3

- cobolSha256: `218b07a63098838ccd00c5aa5139620110045230975a20f1d5c285237e5ba67a`.
- spSha256: `eab4f4c3902cd1661779d2a57e526b9d88999068cf35c92531f9df888f3c9954`.
- airSha256: `46919c1429db4aa310e66fc9df9374eeba53fd98e50a287fdd005c17622f33ad`.
- resultSha256: `34d21be2b0daf6b2dd7673a599dbf95d1dccbeb9610283be34840ca0e75568b0`.
- Stages frontend/lower/analysis/memory: exit0; memoryFile `SEMANTIC_AND_BYTE_IDENTICAL`.
- Candidato: `None`; receipt `COMPLETE`, resultId `canonical-cp3`.
- Destino efetivamente entregue: `/home/gustavo/workspace/teste-e2e/analysis-cfg/.harness-results/w5-e2e-sealed/cp3/result.json`.

### generic-overwrite

- cobolSha256: `7e7002a4d2528916fa22b9c88109922cead966d09c4629801c28a0d5cfcdab31`.
- spSha256: `8dcb91eb0310e58feb8cc4cae6a7b69ab3b26b5f377c2f8d103afcc28120a0bc`.
- airSha256: `14f5e554d5259a29265897b68f84abd3606d2ee627e09266bf86c36e57e4d7de`.
- resultSha256: `3917538fb90c4c12bc2d4d85a5170fb6d2bc80b8cdec51840ad383e046fc4109`.
- Stages frontend/lower/analysis/memory: exit0; memoryFile `SEMANTIC_AND_BYTE_IDENTICAL`.
- Candidato: `NEWER`; receipt `COMPLETE`, resultId `canonical-generic`.
- Destino efetivamente entregue: `/home/gustavo/workspace/teste-e2e/analysis-cfg/.harness-results/w5-e2e-sealed/generic-overwrite/result.json`.

CP4E A/B usam os mesmos SHAs/JARs acima e produzem bytes COBOL/SP/AIR/result iguais.
PROGA tem producer Assign `e52226b01b4afc8f6c6214aafd63e5ab`, origin
`6e3b8590805b3e79519781a3f9a725c6`, BEFORE Return
`5ed7c27833651ab320a3b56d90ed923c`, publication
`77ae4cc3ed75332c64011efce81c431b`, unit `unit`. IDs completos estão no raw wire.
Premises vazias; model remainder false, source/effective true; model fechado,
source OPEN/PARTIAL. Evidence/provenance agregados correspondem ao suporte, sem
alegar dependência CALL. CP3 usa a fixture real entry-goback indicada pelo frontend/
roadmap: zero writes/queries/analyses/batches/consumers, complete vazio. O caso
renomeado ALT-MOVE/ALT-VALUE faz OLDER→NEWER, duas atribuições, uma query e apenas
NEWER com producer final. MemoryOracle em JVM separada confronta projeção semântica
independente a partir de DTOs, além de byte equality da serialização.

## Probes consolidados, qualidade e custo

Os gates W5 acrescentam composição/transporte; o full executa W1–W4 novamente.
Os probes antigos conservam seus próprios workloads, oracles e limites declarados;
W5 não reivindica ter reimplementado todos os perfis no plano default.

| Probe | Evidência e conclusão |
| --- | --- |
| S1 | W1/W3 long Sequence + W5 Assign visits N, um CFG/session/run e um replay de Sequence |
| S2 | W1/W3 declarations + W5 index/resolution linear observado; sem Object scan por query |
| S3 | W3 sparse state/sharing + W5 fatos N detached, liberação e GC/JFR |
| S4 | W1/W2 chains/diamonds/cycles e adjacência/convergência preservados |
| S4b | W2 predecessor contributions e W3 wide-state gates; nenhuma recomposição inserida por W5 |
| S5 | W4 sparse overlapping consumers/cache preservado; consumer W5 genérico |
| S6 | W3/W4 batch/replay + W5 Q=N, um replay; overwrite duas writes → uma query |
| S7 | W3 finite-candidate scale preservada; wire adversarial A/B com supports distintos |
| S8 | W1/W2/W4 Entry/key context + W5 owners completos, múltiplas Entries e ordens permutadas |
| S9 | W3 text bytes + W5 Unicode, hash/escaping e output >64 MiB |
| S10 | CP4E A/B + CP3 + generic-overwrite, stages reais, parser e equivalência memória/arquivo |
| S14 | W3/W4 phases preservadas; encode/write/finalize failure e receipt externo W5 |
| S15 | Candidates/supports/remainders/refusals/falhas medidos junto ao custo, sem threshold |
| S16 | W1–W5 N/2N/4N, >64 MiB completo, Error não convertido em resultado semântico; debt upstream explícita |

[W1](metrics/w1-performance.json), [W2](metrics/w2-performance.json),
[W3](metrics/w3-performance.json), [W4](metrics/w4-performance.json),
[W5](metrics/w5-performance.json) contêm valores reais, sem limites de admissão.

| N writes/objects/queries/facts | bytes de resultado | objetos lógicos retidos | tempo ns | closedInModel | failures |
| --- | --- | --- | --- | --- | --- |
| 1000 | 1754081 | 16254 | 228522574 | 1000 | 0 |
| 2000 | 3509089 | 32254 | 236140922 | 2000 | 0 |
| 4000 | 7011090 | 64254 | 276637649 | 4000 | 0 |

Cada N verifica exatamente `value-(N-1)`, suporte da última Assign, candidateCardinality1,
producerOccurrences=N e todas as queries respondidas. Nesse workload, model/source/
effective open=0, unsupported/not-materialized=0, fases e delivery sem falhas.
CP4E separadamente tem source/effective open=1. Refusal real e falhas controladas
são cenários distintos; não somamos cenários incompatíveis num percentual de qualidade.

## RED, challenges e retenção

[Campanha final](challenges-2/receipt.json): **19 mutantes válidos KILLED**, cada um
com baseline GREEN, compile exit0, RED nominal real, restauração byte-exact de todo
inventário e segundo GREEN. Zero tentativas inválidas nessa campanha.
A [primeira campanha](challenges-1/receipt.json) tem 18 válidos e permanece histórica;
não somamos 18+19 como mutantes distintos. O mutante com nome legacy-cfg-writer
substitui o schema em runtime (não invoca a classe legada); guards source/jdeps
verificam separadamente que CfgJsonWriter/CfgJsonBytes não são usados por W5.
O mutante golden-substitution substitui um stage por cópia de snapshot parseável e
é rejeitado pela prova de execução/PID. OOM é sinal controlado, não exaustão real.

[Logs de desenvolvimento](development/) preservam REDs nominais e tentativas de
compilação/seletores inválidas, sem promovê-las a RED semântico. A primeira tentativa
[memory-1](memory-1/) falhou no attach jcmd por namespace sandbox e não é PASS.
[memory-2](memory-2/) e a rodada [memory-final](memory-final/receipt.json) executaram
GC/JFR externamente nos próprios JVMs, com três escalas e exit0.

Memória: Java21 Temurin21.0.12.1, -Xms64m/-Xmx768m, compressed refs e alinhamento8,
zero warmup; AIR→prepared→delivered→released com fullGC por fase. Histograma é
shallow class bytes, não retained heap exclusivo/RSS; JFR é amostragem de alocação,
não censo. O ledger separa estimativas, objetos lógicos, histograma e bytes wire.
Prepared/delivered não retêm AnalysisSession, ProgramIndex, solver result, ExecutionPlan,
planning execution, ValueUniverse/TextProfile ou writer buffers. ValueFacts/fatos
selecionados sobrevivem enquanto o resultado é mantido; após release, fatos,
supports e receipt desaparecem. Lambdas estáticas do runtime não implicam retenção
de instâncias das classes excluídas. A caminhada de identidade test-only verifica
a fronteira detached; o parser Python só lê arquivos JSON após a JVM produtiva sair.

## Gates locais e regressões

Java21 sem preview; Maven3.9.16; warnings fatais por -Xlint:all/-Werror.
`mvn -B -ntp clean verify`: **261 testes Java, 37 classes, zero failures/errors/skips**
(243 preservados +18 novos). Fast: 47 testes de harness, 101 CP5, 14 do leitor.
Todos os comandos/tempos/hashes estão no [recibo local](local-gates/receipt.json).

| Gate | Exit | Tempo ns | Log bruto |
| --- | --- | --- | --- |
| scope | 0 | 1021933702 | [scope.log.gz](local-gates/scope.log.gz) |
| fast | 0 | 111375777487 | [fast.log.gz](local-gates/fast.log.gz) |
| architecture | 0 | 67491564688 | [architecture.log.gz](local-gates/architecture.log.gz) |
| semantic | 0 | 35313154548 | [semantic.log.gz](local-gates/semantic.log.gz) |
| integration | 0 | 47136045242 | [integration.log.gz](local-gates/integration.log.gz) |
| performance | 0 | 56709956729 | [performance.log.gz](local-gates/performance.log.gz) |
| maven-clean-verify | 0 | 30525836574 | [maven-clean-verify.log.gz](local-gates/maven-clean-verify.log.gz) |
| diff | 0 | 15972763 | [diff.log.gz](local-gates/diff.log.gz) |
| full | 0 | 314638394529 | [full.log.gz](local-gates/full.log.gz) |

O full consolidado rodou sobre o source working tree ligado por hashes à campanha
final/source inventory; o HEAD Git então ainda era o commit de autorização.
Não é alegado que o commit de autorização contenha produção. A rodada E2E final
adicional usa os produtores formalmente reconstruídos e os mesmos JARs analysis-cfg
compilados por clean verify/full. [Scope/fast/architecture após consolidação](local-final/receipt.json) também passaram.
Manifesto/scope/diff são selados novamente antes/depois do commit de evidência.
CI remoto validará exatamente a árvore do último commit.

O diff staged inicialmente encontrou whitespace nativo nos logs brutos Maven.
[Compressão de logs](development/compression.json) preserva os bytes e hashes
originais sem sanitizar evidência; o diff completo W4→HEAD é novamente verificado.

## CI final e checkpoint seguinte

O workflow executa push e pull_request, preservando o coletor original de source
receipt. Depois do último push, conferir PR head/base, head tree, actual checkout,
checkout tree e parents; classificar EXACT_COMMIT_CHECKOUT ou
SYNTHETIC_MERGE_IDENTICAL_TREE somente com prova. DIFFERENT_TREE não qualifica o
HEAD. IDs/run/event/conclusion e SHAs finais pertencem ao comentário final do
[mesmo PR #12](https://github.com/Gustavo2358/analysis-cfg/pull/12). Este commit
não antecipa PASS remoto ainda não observado.

W3-PERF-01 (união crescente potencialmente quadrática) e W3-METRICS-01
(classificação textual de recusa) seguem abertos/não bloqueantes. Caps de codec/
validator, amplificação de heap e qualificação ampla são dívidas externas ou
follow-ups, não resolvidos aqui. Sem sibling changes, repin, domain resolver,
CP6, merge, ready ou auto-merge. Próxima ação é review humano final W5/CP5;
mesmo futura aprovação não autoriza merge ou CP6 automaticamente.
