# CARDDEMO-CONTROL — W7 e W8

> Registro histórico de qualificação, preservado. O trabalho deste repositório está DONE / MERGED; consulte o [fechamento da integração](carddemo-control-integration.md). Os estados de Draft/parada/sem merge abaixo descrevem o checkpoint original.


Status: IN_PROGRESS / implementação e qualificação concluídas, aguardando revisão dos Drafts. Nenhum merge. Worktrees próprios por repositório. Este relatório complementa o checkpoint W1–W6 preservado no histórico.

## Resultado e revisão de autoridade

W7 explicita a reentrada indefinida de PERFORM e preserva possibilidades de dependência depois dessa fronteira. W8 qualificou as quatro etapas em 560 entradas, incluindo todos os 73 CardDemo. Não houve perda de candidato ou support na comparação com W6. Dois candidatos condicionais foram recuperados em fixtures recursivas.

A premissa original de W7 foi revista: a [referência IBM Enterprise COBOL 6.4](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-basic-perform) proíbe um PERFORM causar sua própria reexecução e considera o resultado imprevisível. A [orientação IBM 6.3](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=v6-using-perform) confirma a restrição. A AIR §05.7 oferece invocação local, mas §05.7.5 exige correspondência com a semântica fonte. Portanto, retornos executáveis de pilha para essa construção não foram implementados. O critério original de recursão executável não está cumprido; o tratamento qualificado aqui é o da incerteza sob a autoridade IBM.

## Causas e implementação

1. O SP não distinguia falta de suporte do consumer de reentrada sem semântica definida no dialeto. SP **2.57** publica `Binding.reentryPolicy`; o frontend usa `SOURCE_UNDEFINED` nos bindings PERFORM. Contratos históricos e o construtor tipado anterior mantêm `UNSPECIFIED`. Repetição sequencial não constitui reentrada ativa.
2. A fronteira antiga podia conservar o footprint vazio do PERFORM isolado. O lower agora publica `LOCAL_REENTRY_SOURCE_UNDEFINED` com memória inteira, ambiente e recursos abertos, sem MUST-overwrite, kill, retorno, halt ou divergência provados.
3. O menor ponto fixo da qualificação fonte não produzia resumo de conclusão na recursão incondicional. Isso fazia a ausência de retorno no modelo suprimir dependências posteriores. O tabulador fonte detecta ciclos entre contextos finitos e deriva uma hipótese de conclusão `CONTROL_POSSIBILITY`, com causa `SOURCE_REENTRY_UNDEFINED`, provas do binding e provenance do caller. Os subscribers e a conjunção `callerPremise` preservam a associação entre cada conclusão e seu chamador. A hipótese só existe na análise fonte.
4. Qualified-source-dependencies **1.2** transporta a nova causa; versões anteriores a rejeitam. O consumer usa a autoridade genérica existente. A auditoria W8 encontrou e corrigiu também o serializador que anunciava dependencies 2.6 para evidência 1.2: esse caso agora publica **dependencies 2.7**, preservando `controlRemainder` e os campos de possibilidade fonte.

Não há reconhecimento downstream de nomes de programa, linhas ou comandos COBOL para obter controle. AIR/CFG não receberam arestas para ligar os grafos. Modelos IBM continuam sem fornecer VALUE de runtime, layout físico ou kill forte.

## Matriz D1–D9

| Defeito | Regra / camada | Evidência final e limite |
| --- | --- | --- |
| D1 — comentários EXEC | W2, normalização canônica e SourceMap no frontend | Normalizador completo, testemunhas W2 e 73 CardDemo; fonte original preservado |
| D2 — controle bloqueado por memória | W3, separar sucessor provado de efeito aberto | Famílias CICS/FILE, matriz física e corpus; binding desconhecido não vira NOP |
| D3 — alocação/área implícita | W3, INITIAL/DECLARE e BMS no produtor | Casos INITIAL e RECEIVE implícito em dois estados; sem concessão de valor/layout |
| D4 — aliases FILE/DATASET | W2, catálogo explícito por comando | Suites FILE/aliases e CardDemo; opção não autorizada continua gap |
| D5 — comandos CICS | W4, catálogo de controle e operandos | 25 casos/120 mutações do lower, corpus final; comandos fora do catálogo permanecem abertos |
| D6 — SQL/DL/I | W5, retorno possível e efeitos host/recursos | 24 casos/120 mutações, corpus final; SQL dinâmico/WHENEVER fora da capacidade |
| D7 — controle COBOL | W6, NEXT SENTENCE/SEARCH ALL/STOP/EXIT/ENTRY | 18 casos/25 mutações, PERFORM/Chaos/FILE; ENTRY alternativo não vira entrada principal |
| D8 — reentrada PERFORM | W7, política do produtor e possibilidades fonte finitas | 22 casos/110 mutações, handlers reais, capacidade e corpus; recursão executável IBM e custo exponencial AIR permanecem limites |
| D9 — candidatos suprimidos pela falta de prova | W1 + W7, qualificação e valores condicionais | 560 comparações, zero perdas físicas, 109 supports condicionais preservados; terminação/kill provados mantidos |

As regras W1–W6 e seus REDs estão no relatório histórico e nos work items dessas ondas. Os adversariais novos estão em `PerformReentrySuite` (lower) e `QualifiedSourceContractTest` (CFG), com fixtures reais geradas pelo frontend.

## Validação executada

| Verificação | Resultado |
| --- | --- |
| Replay real SP → AIR → CFG → dependencies | **560/560**, 2.240 etapas com exit zero |
| CardDemo inteiro | **73/73**, 44 app e 29 UniKix, mesmo perfil UNSPECIFIED do baseline |
| Fixtures frontend | **331/331** etapas executadas; preservam PARTIAL quando aplicável |
| PERFORM | **39/39** oracle semântico |
| Chaos | **48/48** + 28 mutações negativas |
| Aliases / PERFORM adversarial | **14/14 / 25/25** |
| Contratos/fronteiras CICS | **30/30** execuções |
| W7 focal | **22/22** verticais e **110** rejeições/mutações de wire; roundtrips, ordem de worklist e controle |
| Preservação dos 560 + wire independente | **0 perdas** de candidatos/supports físicos; **2 acréscimos** investigados |
| Supports condicionais anteriores | **109 preservados**, zero migrações ou sem correspondência |
| AIR → CFG CardDemo | **120.239** alternativas conhecidas e endpoints de retorno conferidos |
| Igualdade dos grafos W6 → W7 | **73/73**, nós/transições completos; normalizado apenas namespace publication |
| Perfis/estados adicionais | **8 execuções W7 novas**, confrontadas com 8 baselines W6 imutáveis; candidatos/supports preservados |

Os 22 adversariais cobrem ciclos diretos/mútuos/condicionais, dois chamadores, ranges, SECTION, laços sequenciais, alvos calculados, FILE, registro e ingresso real de handler. GOBACK, STOP e GO TO antes do ciclo não ganham continuação; seção vizinha e DEADFAULT permanecem negativos. Nenhum retorno executável é acrescentado pela hipótese fonte. A comparação do controle com a política histórica é explícita, além da checagem de candidatos.

A matriz adicional usa o perfil físico `ibm-enterprise-6.4-fixed-display-1047@1`, texto lógico desativado, estados initial/new-logical-level e unknown/unknown. Casos: valores na reentrada, FILE, RETRIEVE INITIAL e RECEIVE BMS implícito. Esses resultados não se misturam com o censo UNSPECIFIED.

### Gates

- **Frontend FAST:** 648 testes, PASS. **Qualification-local:** 1.222 testes, zero falhas/erros, um skip opt-in histórico; normalização/source-map e naming PASS.
- **Lower FAST:** PASS, incluindo FILE composto (195 checks), autoridade FILE (409), CICS/SQL/COBOL. O focal final 22/110 passou separadamente após os últimos testes de handler/controle. **Qualification-local:** PASS; semantic 205.184 checks, rodada ampliada 244.391 incluindo 39.207 checks de performance, 11 casos de capacidade/determinismo; arquitetura PASS. Checks não equivalem a fixtures independentes.
- **analysis-cfg FAST final:** **640 métodos** obrigatórios, zero skips, arquitetura e leitores independentes PASS.

O contrato/publisher do CFG mudou; não houve alteração no solver de CFG/valores. FAST, wire estrito, verticais e replay geral cobrem essa fronteira. Não foi repetida a qualification-local histórica inteira do CFG nem o full de AIR/analysis-ir; estes dois últimos não mudaram. UI não foi modificada nem testada. Não se reportam esses gates como executados.

### Reexecução seletiva e falhas investigadas

O replay completo usou `w7-development-03`. O leitor independente recusou sete dependencies que anunciavam 2.6 com campos 2.7. O teste de regressão reproduziu a falha antes da correção. `w7-development-04` difere do jar anterior somente em `DependencyJson.class`; front/lower/CFG e resources são iguais. Foram republicadas as sete etapas de dependencies afetadas, mantendo SP/AIR/CFG/links e seus hashes. Os sete documentos mudam apenas o campo `version`. As outras 553 etapas de dependencies são reutilizadas pela equivalência da regra para evidência 1.0/1.1. O manifesto final é `w8-final-04/results.json`; o replay anterior permanece intacto. Os 22 adversariais também tiveram o publisher final e wire revalidados.

O gate de arquitetura detectou a nova dependência Java `Set` no serializador. O inventário compilado foi atualizado somente nessa aresta, após conferência independente com jdeps; nenhuma restrição arquitetural foi removida. Um helper de teste que percorria apenas destinos normais foi corrigido para incluir alternativas excepcionais de handler já publicadas. Isso não altera a produção. Tentativas locais com classpath Maven inadequado e uma execução interrompida foram substituídas por gates corretos; os logs anteriores permanecem disponíveis.

Os dois acréscimos são `PROGC` em `perform-family/recursive.cbl` e `times-recursive.cbl`. Ambos derivam do MOVE no fim do range após a reentrada indefinida, sob `UNKNOWN_CONTROL_CAN_COMPLETE`. Conservam provenance do assignment, `controlRemainder`, `valueRemainder` e autoridade condicional; seus sites executáveis continuam UNREACHABLE_IN_MODEL. Não houve acréscimo de candidato CardDemo em W7, nem regressão sem explicação.

## CardDemo: estatísticas e resíduos

A tabela dos 73 programas acompanha este relatório em `carddemo-control-w7-w8-carddemo.csv`. Contém alcance, componentes, órfãos, gaps e contagens de ocorrências de dependencies. O detalhe por ocorrência, identidades e hashes fica no censo local final.

| Medida | Após W6 | Após W7/W8 |
| --- | ---: | ---: |
| Nós / transições | 110.058 / 122.927 | 110.058 / 122.927 |
| Alcançáveis / inalcançáveis | 82.397 / 27.661 | 82.397 / 27.661 |
| Grafos com todos os nós alcançáveis | 66/73 | 66/73 |
| Statements com operação alcançável | 15.847 | 15.847 |
| Statements somente com operações inalcançáveis | 0 | 0 |
| Statements sem operação | 1.165 | 1.165 |

COACTUPC: app **5.037/5.037**, UniKix **4.968/4.968**. O conjunto tem 150 ocorrências PROGRAM, 145 com candidatos; targets indisponíveis continuam sem nomes inventados. As visões de FILE executável e fonte permanecem separadas, sem somar ocorrências duplicadas entre visões. Todos os inventários relevantes permanecem PARTIAL.

- **27.655 nós inalcançáveis:** COACCT01/CODATE01, expansão de contextos e fronteiras de reentrada. Não foram removidos nem reconectados.
- **2 NORMAL_EXIT:** programas com STOP RUN; não implicam falta de continuação legítima.
- **4 sequences:** contextos/auxiliares em COPAUA0C e CSUTLDTC; os respectivos statements possuem outras operações alcançáveis. A existência dessas cópias não demonstra perda de uma ocorrência fonte.
- Dos **1.165 statements sem AIR**, **950** têm somente caminhos de prova fonte condicionais; **215** não pertencem à projeção fonte selecionada. Esta última classe inclui paragraphs sem chamador e código após término, mas não é prova universal de código morto. **Zero** casos com qualificação fonte positiva e ausência de AIR nesta classificação. A auditoria respeita a conjunção de `callerPremise`; não achata retornos em um grafo global.

Não há objetivo de zerar órfãos. SQL dinâmico/WHENEVER, ENTRY como entrada alternativa, comandos fora dos catálogos e contextos runtime ausentes mantêm limites próprios. ALTER e UX continuam fora do escopo.

## Capacidade e terminação

O tabulador fonte opera sobre bindings, estados abstratos de handler, ingressos e pontos de programa finitos. Inserção monotônica de nós/supports, subscribers por contexto e detecção de ciclo produzem terminação sem cutoff de profundidade. A verificação de ciclos percorre o grafo de contextos por callee, O(C(C+E)) por rodada de saturação. Há teste de independência da ordem da worklist.

Famílias medidas: anéis de 1/2/4/8 e ramificações de 1/2/3/4, com 16 execuções W6/W7. Todas terminam e preservam sequências/frontiers AIR. A família ramificada cresce para 17/47/119/287 sequências e 6/16/40/96 fronteiras. **A expansão materializada AIR continua potencialmente exponencial.** Não foi implementado um novo materializador por pushdown/resumos.

| Programa | W6 tempo / RSS KiB | W7 tempo / RSS KiB | Sequências AIR | Nós fonte W6 → W7 |
| --- | --- | --- | ---: | ---: |
| COACCT01 | 24,86 s / 2.416.528 | 27,10 s / 2.482.264 | 32.143 | 496 → 719 |
| CODATE01 | 19,25 s / 1.910.464 | 17,81 s / 1.965.232 | 17.438 | 409 → 610 |

Oito hipóteses de conclusão fonte foram acrescentadas em cada programa. Medições sob carga concorrente são observações, não thresholds ou garantia de performance. O custo de materialização e a ausência de semântica executável recursiva permanecem os limites principais de D8.

## Evidência e revisão

Evidência preservada em `.carddemo-control/evidence/`: manifests `w8-final-03`/`w8-final-04`, `w7-probes-final`, `w8-preservation.json`, `w8-conditional.json`, `w8-control-oracles-final.json`, `w8-chaos-oracle-final.json`, `w8-census-final`, `w8-absences.json`, `w8-graph-equivalence.json`, `w8-projection-check.json`, `w7-capacity-final`, `w7-capacity-carddemo`, `w8-profile-matrix-final` e logs de gates. Baselines W6/FILE, REDs e execuções intermediárias não foram substituídos. O censo antigo registra pais de build; o censo final nomeia-os `runtimeBaseHeads`, sem confundi-los com os HEADs de revisão.

A runtime usa AIR `59df1f7d6f3523b21b172a3ea4b5a0dc95128faa`; os harnesses fixam `d760b07b0fac42a106d09342ee9d5b8445ccf630`. A diferença entre esses commits é exclusivamente `docs/engineering/air-json.md`; produção equivalente. IR: `2c7f31f19efbe3211a2aea5bbda90173a9666fe2`. A equivalência dos fontes/resources finais e os HEADs exatos constam da evidência final e dos Drafts.

Drafts novos, empilhados nas ondas anteriores: [frontend #73](https://github.com/Gustavo2358/proleap-poc/pull/73), [lower #48](https://github.com/Gustavo2358/cobol-lower/pull/48), [analysis-cfg #53](https://github.com/Gustavo2358/analysis-cfg/pull/53). Bases: frontend #72, lower #47, CFG #52. A integração futura exige revisão e autorização, com repins para SHAs reais de merge. Não houve merge nesta execução.
