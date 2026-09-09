# CP5 WAVE_1 — IMPLEMENTED / AWAITING_HUMAN_REVIEW

Baseline humano e remoto: `4aeb4c0ad086ec4fc8911879b13139d84ca57bc9`.
[Autorização anterior à implementação](authorization.json), [baseline/pins](baseline.json),
[dependência isolada](dependency-environment.json). CORE-SIZE-001, Discovery,
preparação, B1 e A–I/F1/F2/F3 aprovados; somente W1 autorizada. Nenhum review
histórico foi sobrescrito. PR #12 permanece a rota única draft.

## Produto e escopo

analysis-kernel (6 fontes / 14 classfiles) implementa AnalysisSession/ProgramIndex,
handles densos privados/reversos, offsets/Site por OperationId, buckets AIR,
Object/Cell e ObjectPlace, diretórios forward/backward contextuais e seleção de Entry.
Admite snapshot core íntegro sem repetir BuildCfg/CoreCfgProjection/AirValidator,
sem segundo CFG ou deep comparison/copy de AIR. Valida papéis completos inclusive
órfãos, canonical membership, endpoints/destinos/contexto e duplicatas semânticas.
Inventário não é reachability ou completude de CONTROL/fonte.

CP5-F01: FIXED_W1. Launcher declara air-java e air-json diretamente, detector estrito
não tem exceção residual. Seu Java/comportamento é byte-exact. DAG core não aponta
para analysis. [Scope](scope.json), [siblings/pins](sibling-integrity.json): nenhum
sibling modificado; nenhum pin alterado; nenhuma produção W2+.

## Oracles e gates

| Prova | Resultado executado |
| --- | --- |
| Baseline fast | 47 originais + 83 CP5, GREEN |
| TDD inicial | compilou; realBuildIsAccepted deu RED comportamental contra shell INVALID_INPUT |
| Admissão e estrutura W1 | 20 métodos nominais; identidade, completude, contexto, sparse IDs, offsets, orphans e metamorfismos |
| W1 performance | 26 métodos nominais, 25 cenários medidos; S1/S2/S4/S8/S11/S16 W1 PASS |
| Fast atualizado | 47 originais + 85 CP5 = 132, PASS |
| Architecture | CFG/transport preservados; W1 sources/classfiles/Maven/javap/jdeps, PASS |
| CFG semantic | 84 métodos nominais EVAL-025/028/029/030, PASS |
| Integration | 37 métodos de transporte/CLI + regressões, PASS |
| Maven clean verify | 16 suítes / 165 testes, zero failures/errors/skips |
| Full global | W1 executado PASS; W2–W5 UNAVAILABLE, exit 3 esperado |

[Comandos, exits, tempos e hashes de logs](local/gates.json),
[contagens por suíte](test-counts.json). Full inclui fast/architecture/semantic e
performance W1; integration é executado separadamente. Gate W1 architecture e
semantic também foram executados explicitamente. Gzip conserva logs brutos sem perda.
Não se usa CI antiga como prova do HEAD futuro.

## Challenges

[Campanha principal](challenges/receipt.json): 15 classes de mutação, compilation=0,
RED nominal, restauração de todos os hashes Java/POM e segundo GREEN; final clean/test
GREEN. Inclui mutilações, identidade por fragmento local, activationEntry, scans
semanticamente corretos, dependência direta removida e caps node/edge/operation/object.
[Campanha adicional](challenge-identity-by-display-name/receipt.json) colapsa
literalmente displayName e confirma o oracle; mesmas garantias e final GREEN.
Os hashes de fontes da campanha adicional conferem com o inventário final W1.

A primeira tentativa de missing-required-edge compilou, mas o seletor Surefire
não encontrou teste no cfg-kernel antes de atingir W1. [Tentativa inválida preservada](attempt-1-invalid-selector/receipt.json),
**não contada como RED semântico**. O seletor foi corrigido sem alterar expected;
a campanha válida inclui BuildCfgContractTest no reactor. Falha intermediária do
harness por remoção de review histórico também foi preservada; o validator agora
rejeita ausência com diagnóstico, mantendo as provas de CORE-SIZE-001 e W1.

## Custo, retenção e CORE-SIZE-001

[Ledger por coleção/pass/estrutura/lifetime](../../../../engineering/cp5-w1-index-ledger.md)
e [25 medições](metrics.json). S1: 10k/20k/100k/200k instructions, V=3/E=2 constantes.
S2: 1k/2k/10k Objects com I/R fixos. S4: 1k/2k/10k Sequences. S8: 1/2/20 Entries,
seleção de somente 1/2. S16: 1k/2k/4k em nodes/edges, operations, objects e references,
todos ACCEPTED, sem mudança de classificação por tamanho.

Construção expected/amortized linear em U+K+S+O+D+C+A+R+T+J+V+E+B (definidos no
ledger), sem pass Entries×Sequences para validar um grafo mutilado. Consultas por
handle usam diretórios primitivos; leituras seguem degree da Entry consultada.
Counters reais e fórmulas independentes concordam; não há thresholds semânticos.

RetentionWalk mede por identidade estruturas lógicas realmente retidas depois de
subtrair AIR+CFG: V Node handles, O Sites, Kselecionado ContextViews, arrays/slots,
zero payloads AIR/CFG adicionais. Não são bytes físicos ou uma estimativa de heap
total; backing internals de Map/List não são contados. G1/default JVM flags foram
inspecionadas separadamente; não há warmup calibrado ou claim de SLA.

Nenhum maxNodes/maxEdges/maxOperations/maxObjects ou admission work budget de produto.
Arithmetic checked; overflow/falha de execução não vira classificação semântica.
Upstream VALIDATION_LIMIT/INCOMPLETE_VALIDATION não satisfaz a entrada da sessão e
não é reclassificado como recusa por tamanho. Dívidas AirJson/Validator/transport
legadas permanecem: não se afirma que a pipeline completa é size-unbounded.

## CI e checkpoint

[Recibo remoto versionado](initial-remote-ci/receipt.json), acompanhado dos artifacts,
respostas da API e logs brutos: HEAD de implementação
`318181c072710f89ac58c2bd387c1645de434cd6`; base
`ec525cbbad96d70c9663faa88e2672148fa8ee71`.

| Evento | Run | Conclusão | Checkout real | Classificação |
| --- | --- | --- | --- | --- |
| push | [34403317341](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34403317341) | SUCCESS | 318181c072710f89ac58c2bd387c1645de434cd6 | EXACT_COMMIT_CHECKOUT |
| pull_request | [34403320170](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34403320170) | SUCCESS | 5af110dbe70915ba5d588b9ed58f29744d59626a | SYNTHETIC_MERGE_IDENTICAL_TREE |

Head tree e as duas checkout trees:
`1012f3741b747b51cc5f5dbb2d30a580b10d8969`. SHA/árvore/pais conferidos contra
artifacts do collector canônico, Git local e API GitHub. Cada workflow executou
fast, scope, arquitetura, semântica, integração e performance W1, sem steps de
produto omitidos. Esta evidência permite registrar o estado IMPLEMENTED; não é
review humano e não valida automaticamente o commit documental seguinte.

O recibo do **último HEAD publicado**, após este registro, fica no mesmo
[PR #12](https://github.com/Gustavo2358/analysis-cfg/pull/12) e na cópia local ignorada
`.harness-results/WORK-CFG-028/wave-1/remote-ci.json`, evitando hash autorreferente.
Esse último push exige novas execuções de push e pull_request e confronto com o
HEAD final, incluindo checkout e árvores; o encerramento reporta somente esse recibo.
W1 = IMPLEMENTED / AWAITING_HUMAN_REVIEW; authorized_wave=1. W2–W5 =
NOT_STARTED / NOT_AUTHORIZED. Sem merge/auto-merge/ready ou início automático de W2.
