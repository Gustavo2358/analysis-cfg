# CP5 W3 — implementação e evidência

W1 e W2 têm aprovação humana prévia; W3 é o checkpoint autorizado. A autorização
foi commitada em c45eb13947f16643063e5363e7d82926cd53ff2e antes da produção W3.
Baseline W2: 0202c7424db04a1d83fb5e35fce055ea81f5b8fa. Produção W3 em 96880b1;
harness e cobertura ampliada em 327737b. O recibo remoto final fica no mesmo PR #12,
correlacionado ao HEAD final; não há SHA autorreferente inventado neste documento.

## Implementação e fronteiras

analysis-values contém 8 fontes produtivas. analysis-kernel acrescenta 4 fontes
de query; as 15 fontes structure/solver W1/W2 conservam os hashes do [baseline](baseline.json).
Não houve finding que exigisse modificar W2. Dependências diretas: analysis-kernel,
cfg-kernel (SPI aprovada expõe CfgTransition/CfgNode) e air-java. Não há aresta inversa.
Os inventários exatos de fonte, classfiles, jdeps/javap e Maven estão em docs/evals/cp5.

[Ledger de domínio/ownership/custo](../../../../engineering/cp5-w3-values-ledger.md)
e [matriz nominal](semantic-matrix.md) descrevem as provas completas. Profile
scalar-text-direct@1 exige Objects textuais diretos, Cell canônica, mesma Cell para
aliases e premissa única de disjunção cobrindo bases distintas. Aceita Assign literal
textual, Nop e controle Jump/Branch/Return/Halt. Assign(Read), HavocMust/May, CopyBytes
e storage indireto são recusados antes do solver. Não há semântica de resolver.

Estado sparse AVL imutável, cópia de caminho O(log B), sem full-copy por instruction.
Missing reached é unknown/open; unreachable é bottom separado. Sets preservam todos
os valores; join une candidatos e OR remainder; Assign forte elimina candidatos
anteriores. Convergência decorre do universo finito do programa, não de limites.
Source remainder acompanha evidência original; effective = model OR source.

No fixture vertical real, Entry/before Assign são unknown; after Assign e before
Return são {PROGA}/model closed; after Return é UNSUPPORTED_POINT sem value fields.
A versão PARTIAL conserva {PROGA}/model closed/source open. Diamante A/B produz
{A,B}/closed; A/missing produz {A}/open. Múltiplos Objects/contextos não se confundem.

Queries têm Entry obrigatório, subject ObjectId e OutcomeKey AIR. ENTRY/BEFORE e
AFTER instruction normal são materializáveis; AFTER terminator e OUTCOME indisponível
são explícitos. Mixed batch completo preserva unsupported e deduplica queries.
Forward usa IN em ordem crescente; backward usa OUT em ordem decrescente. Cada
contexto/Sequence tem uma união de prefixos/sufixos, usando offsets W1. Batch não
reexecuta o solver nem retém roots. Falha controlada é FAILED/OBSERVATION_ERROR
atômica, distinta de run STABLE. F2/F3 e o collector de CI permanecem byte-exact.

## Oráculos e escala

22 métodos W3: 4 domain, 6 valores verticais, 3 replay, 2 oracles, 7 scale.
Leis ACI/upper bounds e monotonicidade em 50 estados. Corpus independente: 48 grafos,
213 pontos, seed 27001; recomposição abstrata e exploração concreta separadas do
solver/transfer produtivo. Always-unknown é rejeitado pelo critério de precisão.

30 medições de escala e 3 escalonadas. Até 200k writes mantêm um binding; 10k Objects
não alterados não materializam matriz ponto×Object. Sets 8/9/100/1k/2k/4k/10k preservam
N candidatos sem abrir remainder. Q=1k/2k/4k, requests=2Q: uma Sequence replayed,
R=Q, todos os Q facts fechados. S4b escalonado entrega N contribuições reais em J,
N+1 transfers e zero predecessorContributionReads. Join work depende de B e C.

Retenção lógica percorre referências reais e conta sharing por identidade. A medição
externa HotSpot full-GC/JFR usa -Xms64m/-Xmx768m, compressed oops/class pointers,
alinhamento 8. Em 10k/20k/200k writes: solved tem 3 state roots/72 shallow bytes e
1 AVL Node/40 shallow bytes; batch-only libera Node/profile/execution/universe;
released elimina o ValueFact. Permanecem apenas singletons sem payload de programa.
O precompute de 200k Write custa 4.8 MB shallow, explicitamente separado do estado.
Estimativas de bytes de alocação não são chamadas heap observado; JFR é amostragem.

## Challenges e tentativas preservadas

Campanha consolidada: 28 mutantes PRODUCTION distintos, todos compilam, ficam RED
pela propriedade nominal, restauram byte-exact e têm segundo GREEN. A campanha
inicial com 27 e a repetição focal de effects após ampliar fixtures também ficam
preservadas. Hashes antes/depois, diffs, comandos e logs gzip estão nos receipts.
Nenhum erro de compilação é contado como mutante morto.

Tentativas de desenvolvimento inválidas: dois seletores Maven sem testes no módulo
anterior; uma compilação do probe externo com imports incorretos; primeira tentativa
de attach jcmd com exit 1 (stderr não capturado); teste de harness iniciado durante
mutação, portanto baseline inválida. Tudo preservado em development/ e nas pastas
originais. O RED comportamental foreign-session foi reproduzido e corrigido em W3.
A primeira rodada local completa detectou inventário central de arquitetura ainda
restrito a W1/W2; o dispatcher foi ampliado explicitamente e os gates repetidos.
No fechamento documental, o gate recusou state.md sem seus três headings obrigatórios;
os headings foram restaurados e fast repetido.

## Escopo e limites

Pins imutáveis: air-java ce530a7e17ab12b23c48f29425f503ff920b09fb;
analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933. Sources.lock, CFG/transporte/CLI
legados e siblings não foram alterados. Observações read-only dos siblings estão
registradas separadamente e não substituem os pins de autoridade.

Limites explícitos: somente scalar-text-direct@1; multibase exige uma prova cobrindo
todas as bases; source remainder é conservador por Unit; sem path feasibility/RD,
correlações entre subjects ou causalidade completa. Union por arrays crescentes pode
fazer trabalho/alocação quadráticos. Não há cap, saturação, budget de convergência,
consumers, planner/cache compartilhado, resolver ou writer/CLI de dataflow.
W4/W5 permanecem NOT_STARTED/NOT_AUTHORIZED. CP5 global continua incompleto.


## Gates executados e checkpoint

| Gate local | Resultado |
| --- | --- |
| fast | PASS/0 |
| architecture | PASS/0 |
| semantic | PASS/0 |
| integration | PASS/0 |
| performance | UNAVAILABLE/3 após W1/W2/W3 PASS; W4/W5 ausentes |
| full | UNAVAILABLE/3 após W1/W2/W3 PASS; W4/W5 ausentes |
| maven-verify | PASS/0 |
| diff-check | PASS/0 |

[Medições](measurements.json), [S4b escalonado](staggered-measurements.json),
[recibo local](local-gates-attempt-2/receipt.json),
[campanha consolidada](challenges-attempt-2/receipt.json),
[effects ampliados](challenges-effects/receipt.json),
[heap/JFR final](memory-final/receipt.json). Logs brutos gzip e hashes acompanham
cada recibo. Fast: 47 + 89 testes do harness. Maven: 205 testes, zero falhas/skips
(139 CFG/transporte, 26 W1, 18 W2, 22 W3). Integration roda separadamente porque
full para no performance UNAVAILABLE e não alcança seu passo integration.

W3 IMPLEMENTED / AWAITING_HUMAN_REVIEW; authorized_wave=3. CI remota final deve ser
correlacionada ao último HEAD através do collector inalterado. Recibo no mesmo PR #12
registra head/base, actual checkout, árvores, run/event/conclusion/classification;
a cópia local fica em .harness-results/WORK-CFG-028/wave-3/remote-ci.json.
Esse recibo final evita commit de evidência que torne o próprio SHA obsoleto.
O PR permanece OPEN/DRAFT, sem merge/auto-merge/ready. Não iniciar W4.
