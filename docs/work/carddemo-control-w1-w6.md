# CARDDEMO-CONTROL — checkpoint W1–W6

Status: IN_PROGRESS / implementação qualificada, Drafts em revisão. Autorização encerra antes de W7; nenhum merge. Worktrees próprios em `.carddemo-control/worktrees/`.

## Resultado por onda

| Onda | Regra implementada | Frontend / lower / CFG |
| --- | --- | --- |
| W1 | Possibilidade de controle fonte com premissas explícitas; candidatos literais/calculados/FILE preservados sem criar arestas executáveis; kills demonstrados mantidos | #67 / #42 / #52 |
| W2 | Comentários EXEC com provenance e aliases FILE/DATASET do catálogo explícito | #68 / #43 / — |
| W3 | Controle normal separado da precisão de memória; INITIAL e DECLARE TABLE não bloqueiam alocação ordinária; área BMS implícita resolvida no produtor | #69 / #44 / — |
| W4 | ASKTIME, FORMATTIME, ASSIGN, INQUIRE PROGRAM, SEND TEXT, WRITEQ TD; operandos completos pelo parser canônico | #70 / #45 / — |
| W5 | SQL estático e DL/I CHKP/REPL/ISRT/DLET: retorno possível, hosts READ/WRITE e efeitos abertos sem MUST | #71 / #46 / — |
| W6 | NEXT SENTENCE, SEARCH ALL, STOP RUN, EXIT PROGRAM contextual e natureza declarativa de ENTRY | #72 / #47 / repin #52 |

Frontend publica até SP2.56 conforme as capacidades usadas. Qualified-source-dependencies1.1 / dependencies2.7 conservam autoridade e premissas. AIR e sua especificação permanecem inalteradas. O consumer não reconhece nomes COBOL/CICS/SQL para inferir controle.

## Qualificação executada

- Replay final real: **560/560**, quatro etapas por entrada: 331 fixtures frontend, 73 CardDemo (44 app + 29 UniKix), 39 PERFORM, 48 Chaos, 14 aliases, 25 PERFORM adversariais e 30 contratos CICS. Exit zero é verificação de execução; oráculos e auditorias abaixo são verificações semânticas separadas.
- Oráculos finais: **PERFORM39/39; Chaos48/48 + 28 mutações negativas; aliases14/14; PERFORM adversarial25/25**. Chaos usa o oracle versionado em W1 que verifica possibilidades condicionais além dos expected executáveis históricos; estes não foram enfraquecidos.
- Auditoria wire/candidatos/provenance física dos **560**: zero perdas de candidatos ou supports físicos. **72 acréscimos por ocorrência**: CardDemo22 PROGRAM +44 FILE; Chaos5 PROGRAM; fixture DISPLAY com handler1 PROGRAM. Não são 72 nomes únicos.
- CardDemo W5→W6: zero perda e zero candidato novo; **70 supports condicionais preservados**, zero migração ou sem correspondência.
- AIR→CFG: **120.239 alternativas de destino conhecidas**, em73/73, correspondem às transições originais; retornos conferidos. Reachability de dependencies corresponde à travessia do CFG. Isso não prova completude COBOL.
- W6:18 sondagens reais +18 fixtures permanentes do lower,25 rejeições/mutações de contrato. Cobrem salto de sentença em IF/EVALUATE/SEARCH/PERFORM, dois chamadores com e sem handlers, ausência de retorno cruzado, match/miss, AT END ausente, STOP terminal, EXIT contextual e ENTRY sem entrada artificial.
- FAST frontend:646 testes, PASS. Qualification-local frontend:1.220 testes, zero falhas/erros,1 skip histórico (`SemanticConditionContextDiscoveryTest.requiredSemanticOraclesForFutureImplementation`, propriedade opt-in ausente), normalização/source-map e naming PASS.
- FAST lower:PASS, incluindo FileCompositeFlow195 checks, FileTopologyAuthority409, CicsCatalogue25/120 mutações, DatabaseControl24/120 e CobolControl18/25. Qualification-local:semantic205.184 checks; performance244.391 checks,11 casos de capacidade e determinismo; arquitetura PASS. Contagens de checks não equivalem a fixtures independentes.
- FAST analysis-cfg:PASS no conteúdo final de produção. Pins/documentação finais verificados separadamente; FAST remoto acompanha os HEADs publicados.

As execuções finais usam o runtime imutável `w6-development-03`. Os470 fontes Java (incluindo ANTLR gerado) e18 resources do frontend foram comparados com os checkouts finais: conteúdo igual. Commits posteriores de testes, documentação e pins não alteram essa evidência.

## Deltas investigados

Os ganhos CardDemo são os explicados em W1–W5:21 candidatos PROGRAM conservadores,38 FILE por aliases,6 FILE por separação controle/memória e1 PROGRAM em COTRTLIC após isolamento da declaração SQL. A fixture `partial-program/display-handler.cbl` recupera o literal AFTER com autoridade SOURCE_CONTROL_POSSIBLE; o literal e sua localização permanecem na evidência qualificada. Não recebe reachability executável fabricada. Chaos conserva cinco hipóteses fonte condicionais; ALTER executável continua fora.

W5→W6:22 SPs idênticos;41 mudam apenas a referência de prova do destino entre sentenças (o destino e as provas do outcome permanecem);10 têm a mudança semântica prevista. A organização por sentenças conserva a prova de input da região para esse destino. A comparação de proofs é documentada separadamente da igualdade de controle; não se afirma byte-equivalence dos51 produtos alterados.

O gate completo encontrou três oráculos desatualizados: dois counts de inventário não incluíam o entrypoint gramatical já classificado em W4, e um teste FILE confundia efeito NO_OP de EXIT PROGRAM com seu controle. Corrigidos com checagens explícitas de classificação/controle, sem mudança de produção. Logs da primeira falha e dos reruns preservados.

## CardDemo: alcance e limites

| Medida | Baseline FILE | Após W6 |
| --- | ---: | ---: |
| Nós | 87.447 | 110.058 |
| Transições | 97.266 | 122.927 |
| Nós alcançáveis | 45.534 | 82.397 |
| Nós inalcançáveis | 41.913 | 27.661 |
| Statements com alguma operação alcançável | 13.602 | 15.847 |
| Statements somente com operações inalcançáveis | 489 | 0 |
| Statements sem operação | 2.921 | 1.165 |

66/73 grafos têm todos os nós alcançáveis. COACTUPC app5037/5037 e UniKix4968/4968. Não há meta de eliminar todos os nós sem entrada.

Dos27.661 nós inalcançáveis,27.655 estão em COACCT01/CODATE01, que mantêm limites de ativação recursiva reservados a W7. Há ainda duas saídas NORMAL_EXIT em programas com STOP RUN e quatro sequences em contextos/auxiliares de COPAUA0C/CSUTLDTC; os statements desses quatro possuem outras operações alcançáveis. Não apagar esses nós nem ligar contextos sem autoridade. A ausência de1.165 statements na AIR não é automaticamente defeito: persistem trechos sem entrada principal, handlers e limites de modelagem. Inventários/remainders continuam PARTIAL.

ENTRY alternativo não se torna raiz da entrada principal. SEARCH ALL resume a busca interna e conserva o índice desconhecido. STOP RUN tem controle terminal fechado e finalização de memória/recursos aberta. SQL dinâmico/WHENEVER, instruções fora dos catálogos fechados e contexto runtime indisponível continuam explícitos. Modelo IBM não fornece runtime VALUE/kill físico.

**Parada antes de W7:** não foi implementada recursão PERFORM, ALTER executável, múltiplas entradas de programa ou UI. A qualificação ampla comprova este checkpoint; não declara a campanha inteira/W8 encerrada.

## Revisão e evidência

PRs empilhados sobre FILE frontend#66/lower#41/CFG#51; cada onda seguinte depende da anterior. Integrar somente após revisão e autorização, frontend→lower com repins efetivamente mergeados; CFG#52 depende dos contratos finais. Nenhum PR foi mergeado.

Evidência local imutável: `.carddemo-control/evidence/` — `w6-final-replay/results.json`, `w6-control-oracles.json`, `w6-chaos-oracle.json`, `w6-preservation.json`, `w6-conditional-audit.json`, `w6-census/{audit.json,programs.csv}`, `w6-projection-check.json`, `w6-sp-delta-classification.json`, `w6-production-equivalence.json` e logs `w6-*-qualification.log`/`w6-*-fast*.log`. Fontes, comandos, hashes e produtos estão nos diretórios de cada caso. Os work items W1–W6 descrevem as regras, fontes primárias e testes de cada onda.

Reutilizado explicitamente: baselines FILE/discovery, auditorias focais W1–W5 e matriz física W3 (8 execuções). Não houve novo full de AIR/analysis-ir, pois não mudaram; UI e W7 não executados. Não substituir reports históricos por resultados novos.
