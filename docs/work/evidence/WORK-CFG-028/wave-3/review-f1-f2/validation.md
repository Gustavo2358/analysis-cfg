# CP5 W3 — remediação W3-F1/W3-F2

O [review humano](review.json) solicita mudanças focais no HEAD
8cb55b86c83644e4727cc532787a518775d7e868, preservando a aprovação W1/W2 e a fundação W3.
Registro anterior à produção: 78bc899. Correção/testes/harness: 74331d7b92644e2f5193ad37d3a7f2015116b0b1.
O HEAD final e a CI ficam no recibo do mesmo PR #12, sem hash autorreferente neste
arquivo. W3 IMPLEMENTED / AWAITING_HUMAN_REVIEW; W4/W5 não autorizadas.

## W3-F1 — suporte, origem e premissas

O domínio preserva ordinais finitos de produtores junto aos candidatos. Cada
produtor aponta um valor, referência AIR, OriginId e PremiseIds. A observação
materializa apenas os suportes alcançados no ponto; existência no pool não basta.
ValueFact.candidateSupports conserva a associação por candidato; evidence,
provenance e premises agregam refs dos produtores, além da disjunção utilizada.

Assign fornece seu OperationId e header.origin. InitialCondition literal fornece
OperandId do place, condition.origin e condition.premises. Ponto/Entry/subject
permanecem no Observation.query e a Cell no ValueFact. Nenhum Return é apresentado
como produtor do literal. As listas expostas são imutáveis e não retêm execution,
root do estado ou ValueUniverse.

Join une suportes inclusive para o mesmo literal. Strong update elimina suportes
anteriores, mesmo para um literal igual, e mata premissas do seed substituído.
Conditions simultâneas iguais na mesma Cell preservam ambos os suportes/premissas.
Equivalência inclui suporte: mudanças sem novo valor propagam pelo W2 e por ciclos.
O domínio cresce em conjuntos finitos de valores e produtores do programa, sem
path tree, RD completo, cap, saturação ou resource budget de convergência.

## W3-F2 — fonte por Entry e Cell

EntryState.uncertainties não vazio abre sourceUnknownRemainder daquela Entry.
O literal inicial continua modelado fechado: BOOT, model=false, source=true,
effective=true. Outra Entry sem a lacuna permanece fechada. A abertura é conservadora
sem analisar texto/código informal da uncertainty e não é apagada por Assign.

A preparação agrega coverage/precision storage ou values abertas de todos os
Objects por Cell. Write através de B e query através de A preservam a abertura
quando compartilham a Cell. Controles negativos: gap em outra Cell não contamina A;
precision apenas dependencies, com storage/values EXACT e coverage MODELED, não
abre values. Coverage de Publication/Unit/operations mantém a regra anterior.

## Provas nominais e mutações

O primeiro RED compilável contém seis testes, cinco falhas comportamentais e zero
erros: produtor/origem, join de suporte, premissa do seed, Entry gap e alias gap.
O controle negativo já passava. O log original está em
[development/w3-f12-red.log.gz](development/w3-f12-red.log.gz); os GREEN subsequentes
ficam preservados. Nenhum erro de compilação é contado como RED semântico.

SupportSourceTest acrescenta 11 métodos aos 22 W3 existentes. Há witnesses de
strong update entre blocos, mesmo literal reatribuído, diamante com suportes iguais
ou candidatos distintos, operação órfã no pool, ordem de inventário invertida,
ciclo que recebe novo suporte do mesmo valor, leis ACI de suporte, condições
iniciais sobrepostas compatíveis, gaps por Entry/alias e controles de escopo/dimensão.
O gate semantic executa 26 métodos nominais W3; performance executa os 33.

A [campanha focal](challenges-attempt-1/receipt.json) executou 18 mutantes distintos,
todos com baseline GREEN, compilação bem-sucedida, RED pela propriedade nominal,
restauração byte-exact e segundo GREEN. Zero tentativas inválidas nessa campanha.
Inclui ignorar EntryState.uncertainties, consultar precisão só do Object da query,
substituir evidência pelo Return, perder origin/premise, ignorar mudança de suporte
na equivalência, weak support update, misturar candidatos/suportes, usar o pool como
suporte e impor cap de suporte. Hashes, comandos, diffs e logs gzip acompanham recibo.
A campanha histórica W3 permanece intacta; não foi reexecutada como uma nova campanha.

## Escala, alocação e retenção

Os probes anteriores permanecem: 10.000 candidatos, 10.000 Objects com um binding,
200.000 writes e batches de milhares de queries sem solver/replay por query.
As 30 medições e três S4b escalonadas foram repetidas. O novo fan-in conserva um
candidato e todos os 1.000/2.000/4.000/10.000 produtores de suporte. Medições brutas
extraídas dos testes: [support-measurements.json](support-measurements.json).

| Produtores | Suportes finais | Entradas visitadas em unions | Bytes de suporte alocados, estimativa |
| ---: | ---: | ---: | ---: |
| 1.000 | 1.000 | 500.499 | 4.063.928 |
| 2.000 | 2.000 | 2.000.999 | 16.127.928 |
| 4.000 | 4.000 | 8.001.999 | 64.255.928 |
| 10.000 | 10.000 | 50.004.999 | 400.639.928 |

W3-PERF-01 segue aberto: união crescente por arrays pode apresentar trabalho e
alocação acumulados quadráticos, agora também explícitos para suporte. A representação
é substituível e precisa de profiling antes da qualificação ampla de produção.
Os números acima não são heap retido. O estado e o replay não retêm histórico.

[GC.class_histogram/JFR](memory-attempt-2/receipt.json) após full GC, N=10k/20k/200k:
solved mantém três PossibleValuesState (72 shallow bytes) e um AVL Node (40 bytes).
A preparação mantém N suportes singleton, N Support DTOs, N Producer records e N
Candidates suportados. Para 200k, SupportSet = 4.800.024 shallow bytes (inclui EMPTY),
Support = 4.800.000, Producer = 4.800.000 e Candidates = 6.400.096. Write permanece
4.800.000. Esses custos O(I) do profile ficam explícitos, separados dos roots vivos.

Batch-only libera profile/execution/universe/nodes e todos os SupportSets de programa;
restam os singletons globais vazios e um Support DTO (24 bytes) do fato. Ao liberar
o batch, ValueFact e esse Support desaparecem. Não há referência reversa ao diretório.
Flags: Java 21, -Xms64m/-Xmx768m, compressed oops/class pointers, alinhamento 8.
Histogramas medem shallow bytes por classe, não tamanho retido exclusivo total;
JFR amostra alocação. RetentionAudit conta sharing por identidade e distingue arrays
compartilhados de candidatos/suportes. [Ledger](../../../../../engineering/cp5-w3-values-ledger.md).

A primeira medição de memória permanece em memory-attempt-1. Seus histogramas e
assertions passaram, mas o manifesto files incluía um digest obsoleto do próprio
receipt.json, que era regravado no final. O script passou a excluir esse arquivo
autorreferente; a medição foi repetida integralmente em memory-attempt-2, com todos
os digests dos outputs brutos conferidos. A tentativa anterior não foi reescrita.

## Gates, escopo e checkpoint

[Recibo local](local-gates-attempt-1/receipt.json): fast, architecture, semantic,
integration, mvn clean verify e git diff --check PASS/0. Performance e full retornam
UNAVAILABLE/3 após W1/W2/W3 PASS, pois W4/W5 continuam indisponíveis. Integration
foi executado separadamente; full para antes dele no UNAVAILABLE esperado.
Maven: 216 testes, zero falhas/erros/skips (139 CFG/transporte, 26 W1, 18 W2, 33 W3).
Fast: 47 + 92 testes de harness. [Medições completas](measurements.json) e
[S4b escalonado](staggered-measurements.json) conservam outputs e hashes brutos.

O [baseline congelado](baseline.json) verifica todo analysis-kernel (solver, índice,
query/replay e testes), AVL, POMs e sources.lock contra o HEAD revisado. Não houve
mudanças em CFG/transporte/CLI, siblings ou pins. analysis-values tem nove fontes
produtivas e conserva suas dependências diretas analysis-kernel/cfg-kernel/air-java,
sem dependência reversa. Inventário compilado, javap e jdeps foram atualizados e verificados.

Pins: air-java ce530a7e17ab12b23c48f29425f503ff920b09fb;
analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933. Sem repin.
Limites preservados: scalar-text-direct@1, multibase com premissa explícita, fonte
conservadora por Unit/Entry/Cell, sem correlação de caminhos ou subjects. Follow-ups
não bloqueantes registrados: W3-PERF-01, W3-METRICS-01 (RefusalReason tipado ainda
pendente) e W4-BINDING-01 (planner/AnalysisKey, não autorizado). Nenhum consumer,
resolver, cache/planner compartilhado ou writer/CLI de dataflow foi implementado.

Review REQUEST_CHANGES original permanece append-only no lifecycle. O estado da
remediação é IMPLEMENTED / AWAITING_HUMAN_REVIEW, nunca APPROVED automaticamente.
W1/W2 APPROVED; W4/W5 NOT_STARTED / NOT_AUTHORIZED. CP5 global permanece incompleto.
CI final usa o collector inalterado e recibo no mesmo PR #12 OPEN/DRAFT, com SHA
head/base/checkout, árvores, run/event/conclusion/classification. Cópia local:
.harness-results/WORK-CFG-028/wave-3/review-f1-f2/remote-ci.json. Sem merge/ready/auto-merge.
