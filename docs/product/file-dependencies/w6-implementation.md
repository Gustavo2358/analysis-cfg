# FD-W6 — auditoria N-LR / contrato auxiliar

QUALIFIED_LOCAL (lean IN_PROGRESS enquanto PRs unmerged). Entrada nos pins W5 do estado.

## Regra e oráculos antes da produção

Autoridade: IBM Enterprise COBOL 6.4, SC27-8713-03, atualização 2026-04-28,
PDF/hash pinados em profiles.md; texto local conferido nesta wave.
I-O-CONTROL/RERUN pp154–156; SAME pp156–158; MULTIPLE/APPLY p158;
RESERVE p146; PADDING/DELIMITER p148; PASSWORD p152;
BLOCK/RECORD pp181–188; LABEL/VALUE/DATA RECORDS pp188–189;
LINAGE pp189–190 e special register p23; RECORDING/CODE-SET pp191–192.

| Cláusula | Decisão source-only e oracle independente |
| --- | --- |
| RERUN sem EVERY | Nome de checkpoint derivado da assignment-name, sem SELECT próprio; trigger SORT_MERGE; origem e owner; não significa acesso incondicional |
| RERUN EVERY n RECORDS OF F / END REEL OF F | Trigger e binding F preservados, nome/checkpoint distinto; CLOCK e compromisso não promovidos a N |
| SAME RECORD AREA | Alias de registros já modelado W3, identidade de recursos distinta |
| SAME AREA | INDEXED/RELATIVE provam VSAM e alias; SEQUENTIAL com AS- prova VSAM; S-/sem AS- prova QSAM documental; mistura não provada conserva gap |
| SAME SORT / SORT-MERGE AREA | Documental; nunca alias de registros |
| MULTIPLE FILE TAPE | Documental; posições preservadas; nenhuma fusão de recursos |
| APPLY WRITE-ONLY / RESERVE | Otimização/alocação de buffers, sem mutação dos dados COBOL; restrições QSAM preservadas |
| BLOCK / RECORDING MODE | Formato/alocação; sem mutação adicional de registros; VSAM ignora RECORDING MODE |
| LABEL / VALUE OF / DATA RECORDS / PADDING / DELIMITER | Documental conforme LR; DATA RECORDS pode nomear 01 inexistente |
| CODE-SET | Conversão de entrada/saída, buffer já desconhecido em READ; SD documental; sem lookup de dispositivo |
| LINAGE | Controle de página para FD sequencial; SD documental; parâmetros são lidos, não sobrescritos; contador não vira constante provada |
| PASSWORD | Dado lido para verificação no OPEN; não mutado; sem consulta de senha/runtime |
| RECORD DEPENDING | Efeitos READ/RETURN e comprimento de saída W3/W5 reutilizados |

Contrato implementado: SP2.27/fileInventory1.6 com inventário auxiliar tipado,
classificação explícita, referências nominais/operandos preservados e provenance.
Argumentos documentais nunca são reinterpretados pelo lower. Declaração não cria
execução. Lower valida forma e identidades bilateralmente, traduzindo checkpoint
somente quando houver gatilho operacional provado; nenhuma extensão AIR prevista.

Oráculos: RERUN sem EVERY e sem SELECT, EVERY e negativos; cada família declarativa;
SAME indexed vs sequential vs sort; homônimos, origem/COPY, CALL disjunto e contador.
B-SP negativos de campo removido/contradição em memória; E-SELECTED com CLI real.
Gates: F-DECL/F-STORAGE, novos focais, FAST produtores, B-SP/E-SELECTED; Q-SHARED
executado nos dois produtores por alteração de alias e leituras implícitas compartilhadas.
W10/N04/N18 continuam NOT_AUTHORIZED.

## Refinamentos de implementação

RERUN usa ResourceDeclaration neutra `cobol.checkpoint`; invoke `checkpoint`
condicionado ao gatilho tipado, com zero ou mais ocorrências e contrato aberto.
Nenhuma consulta de input externo. Nomes de ASSIGN não são nomes físicos.
Forma END_VOLUME cujo primeiro operando também nomeia FILE preserva ambiguidade
assignment/file-name (`RERUN_END_VOLUME_TARGET_FORM_NOT_PROVEN`), no perfil N,
sem alvo literal inventado. Isto é limite de prova dessa forma; não classificação D.
LINAGE-COUNTER tem valor não provado no motor geral; MOVE conserva sobrescrita
do receptor e CALL disjunto. Documentação não significa omitir parâmetros/origens.

Frontend final: focal230, FAST336 e Q-SHARED929 PASS, com um skip histórico
condicionado. A primeira qualification detectou manifesto gramatical não atualizado
para APPLY; correção explícita preservou a igualdade de conjuntos. Lower: 19 SPs
reais, negativos wire/memory e Q-SHARED semântica244296/performance39215 PASS;
FAST final PASS; E-SELECTED33 fontes×2 PASS. Tentativas RED e falhas intermediárias
continuam preservadas em `.harness-results/fd-w6` dos respectivos worktrees.

### N05 / classificação de método de acesso — correção do oracle

Auditoria final consultou o formato ASSIGN p142–143 e tabela de organizações p138,
LR exata: o campo AS- é obrigatório para VSAM sequencial, ausente para indexed e
relative; QSAM permite S- omitido. LINE SEQUENTIAL é formato IBM core (pp141/146–147),
independente das extensões D. A premissa inicial de método indeterminado para toda
organização SEQUENTIAL era conservadora, mas perdia informação sintática disponível.
Novos oráculos antes do delta: SAME AREA com AS- compartilha registros; S-/sem AS-
é documental QSAM; classificação por referência publicada e admitida sem parsing
no lower. Nome externo terminal e ausência de bindingMechanism permanecem.
A primeira implementação W6 ccfce0e é preservada, seguida por este refinamento.

## Qualificação integrada e limites de prova

Frontend `4f63f10c697feb76bf26ba8eb0fa663bb94b9b71`, lower
`4e8e1299314e965f0f0fc18de7acb9cd00f4652d`; AIR/IR pins W1 inalterados.
SP2.27/fileInventory1.6/storage1.8; wire2.1 inalterado. 19 fixtures bilaterais
idênticas; 7 mutantes wire e2 contradições memory rejeitados. Produtores e consumer
usam builds limpos, isolados, reconstruídos nesses pins, sem jars de outra campanha.

O oracle inicial exigia valor CALL disjunto fechado em toda integração. O mesmo
fonte sem RERUN reproduziu remainder: a projeção parcial do CALL externo permite
controle/efeitos não fechados. Não foi regressão do checkpoint; candidato e suporte
são preservados. O oracle final exige esses suportes e a alocação/alias source-level;
não afirma singleton exato em um programa com esse controle aberto. O1–O5 e um
novo AIR manual com controle fechado provam separadamente que leitura/checkpoint
opcional repetido não altera memória; o mutante com escrita abre remainder.
LINAGE-COUNTER é valor não provado; MOVE dele mata o antigo valor do receptor,
preservando suporte do CALL disjunto. Não se afirma cálculo exato do contador.

Auditoria declarativa N01–N03/N05–N17: declarações/assignment/componentes, keys,
status, FD/SD/records/COPY, visibilidade e cláusulas auxiliares têm representação
com teste; N10 runtime scope/captures fica explicitamente W9. Formas de checkpoint
END_VOLUME cuja primeira referência também é FILE conservam target-form não provada;
métodos mistos SAME conservam gap. Não se promove essa incerteza a perfil D.
N04/N18, ASSIGN DYNAMIC, Report Writer e APIs continuam W10/NOT_AUTHORIZED.
Não há dimensão de resolução física, nem obrigação DSNAME/JCL.

| Gate / estado | Comando e propriedade |
| --- | --- |
| PASS | frontend focais230; `lean.py fast`336; `lean.py qualification-local`929, um skip histórico condicionado; AST/grammar/projection/storage/CALL |
| PASS | lower `mvn -DskipTests test-compile` + `FileAuxiliarySuite`; FAST2340 core+adapters; qualification-local244296/39215, contratos/controle/arquitetura |
| PASS | B-SP19 exports byte a byte no pin final; decoder/admission nas duas portas |
| PASS | CFG FAST487 (produção inalterada); focal15 FileCheckpointOracleTest/FileIoOutcomeOracleTest/FileUseControlOracleTest/FileDependencyTest, zero skips |
| PASS | `python3 -B scripts/project/test_dependency_wire.py`,12; reader2.1/CALL sem delta |
| PASS | `python3 -B scripts/project/e2e_file_auxiliary.py --work .harness-results/fd-w6/e2e-2 --producers .harness-results/fd-w6/producers/producers.json`; 11aux+6memory+16sort, duas vezes, quatro produtos determinísticos |
| REUSED | AIR/IR/provider/solver/CFG sem delta produtivo; FAST CFG antecede somente o novo oracle manual |
| NOT_RUN | qualification-local CFG, corpus/escala ampla; sem delta compartilhado CFG em W6, qualificação final W11 |
| FAIL preservado | REDs declarativos/alias/leituras; manifesto APPLY; oracle integrado excessivamente forte; tentativas mecânicas de fixture/codec. Nenhum log foi reescrito |
| BLOCKED | nenhum identificado |

Todos os PASS terminaram exit0. Logs e outputs brutos em `.harness-results/fd-w6`
e no handoff local E2E da wave. Não há medida de precisão/recall ou SLA.
