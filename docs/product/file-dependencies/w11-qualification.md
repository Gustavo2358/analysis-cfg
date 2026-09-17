# FD-W11 — qualificação final N+C

IN_PROGRESS após W9 integralmente QUALIFIED_LOCAL. Autorização adicional humana:
qualificar como DAG; nenhuma W10/merge/release. Auditoria limpa dos seis worktrees
em `fd-w11/initial-clean-audit.json`. Base CFG381f55a, frontendd120036, lower HEAD
c31cd92/pin9c4e7a3 (somente documentação distinta), AIR5fe0224, IRfb153ae.

1. Barreira inicial: registrar SHAs/pins e construir um único bundle imutável de
   produtores; caches/outputs exclusivos. Congelar runtime consumer antes de
   qualquer clean concorrente. Falha invalida somente descendentes materiais.
2. Barreira curta: B-SP, B-AIR, reader/rejeições/projeção CALL, pins/determinismo
   e smoke CLI CALL+FILE+scope. Nenhum fan-out caro antes de verde.
3. Fan-out com no máximo 2–3 processos pesados: FAST→qualification-local dentro
   de cada repo; repositórios independentes podem paralelizar. Corpus CardDemo no
   pin59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e, métricas de tempo/memória/escala;
   SG1–SG5/CALL-X1–X6/MR1–MR9/mutantes e coortes CLI em runtime imutável separado.
   Reusar wrappers existentes; nenhum executor de campanha novo. Remoto FAST only.
4. Junção: matriz N+C por linha, semântica e determinismo reais; NEW/REUSED/
   NOT_RUN explícitos, PARTIAL/gaps reais, comandos/exit/pins e handoff final.

Full e corpus se justificam pela qualificação global mandatória W11 e pelas
fronteiras acumuladas SP/memória/controle/codec/unidades. Evidência W9 preservada;
nenhum output antigo vale como novo PASS. Mutantes D ficam NOT_AUTHORIZED.
W11 permanece em execução até todos os gates obrigatórios fecharem ou existir
bloqueio material comprovado. STOP final para revisão humana, sem W10.

## Barreira curta — PASS

Bundle `fd-w11/pipeline` via carddemo_setup.py, cache privado `fd-w11/m2`;
345 arquivos de runtime hasheados, quatro checkouts limpos nos pins W9. Corpus
CardDemo clonado isoladamente no SHA exigido. Consumer congelado381f55a, sem uso
de targets mutáveis dos worktrees no corpus/E2E.

B-SP frontend21 e lower FastAdapterSuite (inclui todas as famílias FILE/scope),
AIR offline187model/127codec, consumer manual28, reader18, smoke GLOBAL READ +
CICS computed contido2×2: PASS exit0. Lower tentativa1 falhou por recurso de teste
relativo ao cwd; tentativa2 montou somente fixtures read-only em output isolado.
Selector consumer inicial executou9 testes por três nomes incorretos; corrigido
para28 antes da barreira, sem alegar cobertura dos nomes ausentes.

Runner CardDemo existente adaptado para compilation1.0/SP2.28, flags de perfil e
RSS/CPU por processo via GNU time; REDs independentes (unidade parcial/versão,
exit4 com métricas, entryInventory PARTIAL) e25 testes GREEN. Smoke real conserva
KEEP/supports e owners pai/filho com estados PARTIAL e métricas das quatro etapas.
A extensão é do runner de avaliação; não muda código produtivo do analisador.

Fan-out caro autorizado após esses resultados: frontend FAST→Q; lower FAST→Q;
corpus completo no snapshot, no máximo três processos pesados. Outros ramos
usam slots liberados. CFG FAST/Q após estabilizar os testes/relatórios W11.

## Achados do primeiro corpus / remediação em execução

Primeiro corpus completou73 tentativas: frontend71PARTIAL/2BLOCKED; lower18PARTIAL/
53BLOCKED/2NOT_REACHED; CFG14PARTIAL/4BLOCKED/55NOT_REACHED; dependências14PARTIAL/
59NOT_REACHED. Esses números são observação da tentativa1, não qualificação.

Regressão demonstrada no adapter lower Materialize: negociação LOGICAL_SOURCE
incluía somente SP2.19/2.20 e perdeu herança nas versões FD2.21–2.28. Evidência fonte
POSSIBLE_LITERAL_BYTES com layout aberto passou a ser exigida como prova física.
Oracle independente versiona a fixture unknown-base em todas as oito versões FD;
RED real em2.21, mais negativo2.18 preservando o requisito BOUNDED_PHYSICAL.
Correção mínima em curso; B-SP/FAST/Q e consumidores/corpus descendentes exigem
novos pins. Frontend e AIR inalterados preservam qualificação W11 executada.

Outro achado é limitação prévia do exportador CFG estrito para logical copy com
precondição não descarregada. AnalysisDependencies usa explicitamente o reader
partial-analysis existente e recebe AIR diretamente. Runner opcionalmente mede
os dois ramos, mantendo CFG BLOCKED enquanto tenta dependências; não altera
aceitação produtiva nem converte erro em PASS. RED/GREEN de26 testes protege a
independência; probe real CBTRN01C/02C em execução. CardDemo source continua intacto.

Frontend W11 FAST352/Q945+normalizer PASS (um skip histórico). AIRFAST187/127/40 e
qualification-local187/127 via Maven clean verify PASS; tentativa Maven1 falhou
antes dos testes por plugin ausente/rede e tentativa2 usou cache privado/rede
permitida. Lower FAST/Q tentativa1 PASS, mas a correção de versão ainda precisa
fechar seus nós invalidados. W11 permanece IN_PROGRESS.

Remediação focal lower GREEN: SP2.21–2.28 conserva LOGICAL_SOURCE, layout aberto
não vira física inventada; SP2.18 continua BOUNDED_PHYSICAL/rejeita falta de prova.
EvidencePreservingEntrySuite + PossibleEntrySuite (seis negativos legados) +
FileScopeSuite PASS; build-red1 erro de referência de classe do teste, red2 falha
semântica real2.21, green1 usava helper que exige sucesso no negativo, green2
corrigiu só o helper e passou. FAST2→Q2 do lower em andamento antes de novo pin.
As53 recusas lower da tentativa1 têm exatamente esse diagnóstico; rerodar sem
alterar raw evidence. Probe CBTRN01C/02C PASS para seis external names de cada
fonte, CEE3ABD/supports e PARTIAL pelo consumer existente; CFG BLOCKED preservado.

## Checkpoint intermediário W11 — snapshot2 / achados preservados

Lower ee876281 publicado após FAST2/Q2 PASS. Snapshot2 recompilado em bundle/cache
privados; smoke scope2×2 e CODATE01/COACTVWC real PASS. E-SELECTED79 fontes×2
(scope8/native33/CICS38) PASS; também native14/memory6/control8×2 para os sete
verbos, handlers e CALL-X. MR1–MR9 PASS. SG1–SG5 core PASS, incluindo strace real
sem abertura do arquivo de negócio; primeira tentativa ptrace recusada pelo
sandbox, segunda com permissão de tracing. Nenhuma variável de ambiente resolveu
nomes. SG2 path-literal D continua NOT_AUTHORIZED.

Escala21 witnesses:1/8/32 arquivos, usos, layouts, aliases, candidatos, unidades e
participantes SORT. Todos os fatos/candidatos/owners/papéis esperados preservados;
medidas por estágio em scale-1 e scale-3. Tentativas candidates com EVALUATE/número
expuseram limites prévios de controle; foram preservadas. O witness final usa IF
textual suportado, conserva32 alternativas e fechamento no modelo; source continua
PARTIAL. Não é SLA nem teto de capability.

Mutação focal encontrou um teste insuficiente: remover participantes input de
SORT sobrevivia ao teste manual A6, embora nomes/edges permanecessem. Asserções
independentes resource-0/resource-1 input e resource-2 output acrescentadas.
Baseline18 testes PASS; cinco mutantes KILLED em overlays compilados isolados:
BEFORE→AFTER, fundir owner, perder record-object, perder SORT input, afirmar DDNAME.
Primeira execução teve dependência JUnit ausente apenas ao formatar falha;
INVÁLIDA, nunca KILLED. Segunda mostrou sobrevivente; terceira fechou o oracle.

Corpus2:73 tentados; frontend71PARTIAL/2BLOCKED; lower70PARTIAL/1BLOCKED/2NOT_REACHED;
CFG64PARTIAL/6BLOCKED/3NOT_REACHED; dependências70PARTIAL/3NOT_REACHED.255 usos
nativos com nomes conhecidos e22 CICS computed sem candidatos/prova no ponto.
119 CALL sites; vetores comparáveis da tentativa1 (11) e baseline histórico (22)
inalterados em nomes, suportes/provenance, remainder e alcance. Novos/não comparáveis
são dimensões separadas, sem afirmar equivalência ou precisão global.

A recusa lower restante CBSTM03A.CBL é regressão bilateral real: WRITE no fim de
parágrafo exportava ordinary em ObservedStatement.normalContinuation, que é
intrínseca ao parágrafo. Teste reduzido independente RED frontend e RED lower;
remediação em curso, mantendo ordinary no plano FILE. Gates/pins descendentes
serão invalidados proporcionalmente. Tentativa2 e seus erros continuam brutos.

A amostra COACTVWC usa READ DATASET em três locais (727/776/826): preservados como
OBSERVED/EMBEDDED_LANGUAGE, não publicados como FILE. C-FC selecionado W8 autoriza
DATASET→FILE somente em SET, conforme SPI5.6p672. API5.6 não documentou o alias READ
na consulta; buscas acharam CICS TX e manuais históricos, que NÃO foram usados como
autoridade do perfil. Limite de sintaxe/autoridade explícito para revisão, sem
alegar que esses usos foram reconhecidos, sem mover a pendência para D e sem
inventar identidade DSNAME. A qualificação core deve reportar precisamente esse
limite e a cobertura da forma FILE documentada; nenhuma completude CICS universal.

Remediação parágrafo: frontend32 focais PASS; lower FileControlSuite positivo e
13 negativos existentes mais contraprova de intrinsic cruzando parágrafo PASS.
Probe de CBSTM03A real com classes atuais passa frontend/lower/CFG/dependency,
todos PARTIAL, sem alterar o source. FAST→Q em andamento nos produtores e CFG.
A amostra manual de corpus2 fecha CBACT01C10/CBACT02C3/CBTRN01C15/CBTRN02C21 usos
nativos com nomes/owners/ações/CALL/supports e COCRDSLC2 READ FILE com binding e
provenance, valores computed ainda desconhecidos. COACTVWC READ DATASET registrado
NOT_QUALIFIED; não convertido em PASS por contagem/crash-free.

## Checkpoint de código W11 / preparação do snapshot3

Frontend aaecf8c1: FAST352/Q946+normalizer PASS (um skip histórico); lower
24ee0ef9: FAST core2340+adapters/Q244296 semânticos+39215 performance/arquitetura
PASS. Contraprova KNOWN cruza parágrafo e é recusada pela regra STRUCTURE exata.
CFG FAST1/Q1 PASS exit0, incluindo reader18, runner26 e métricas5; nenhuma
produção Java CFG alterada desde381f55a. Cinco mutantes manuais mortos após
fortalecer SORT A6. Esses gates são REUSED para pins novos no que não depende
dos produtores; novo bundle e E2E/corpus validam as fronteiras alteradas.

Snapshot3 usa esses produtores, AIR5fe0224 e o checkpoint Git deste código CFG.
O pin do consumer será registrado em commit documental posterior para evitar
autorrefência; igualdade do código produtivo pode ser verificada por git diff.
C06 READ DATASET permanece NOT_QUALIFIED por autoridade não fechada, sem redução
do requisito nem afirmação de completude universal. W11 continua IN_PROGRESS.
