# FD-W7 — nomes computados no ponto CICS

QUALIFIED_LOCAL; lean IN_PROGRESS até merge. Entrada CFG1f39b38, frontend4f63f10/SP2.27, lower4e8e129;
AIR/IR W1, todos os pins e worktrees conferidos. W6 QUALIFIED_LOCAL.

## D-DYNAMIC/core — decisão antes da produção

CLOSED no core; oráculos, FAST e qualification-local concluídos. CICS TS z/OS5.6,
[API Reference](https://www.ibm.com/docs/SSGMCP_5.6.0/pdf/api-reference_pdf.pdf),
edição aplicável a5.6, copyright2023, consultada2026-09-17, SHA256
`3c3295d5b7f013a559b1cc742a7e810c0247bfc0bd3790b3545ab08327bc3c5b`.
Chapter1 pp3–4, Data areas/data values e COBOL argument values: sender entrega
valor para processar o comando; FILE(filename) aceita nome1–8 A–Z,0–9,$,@,#.
Literal COBOL menor é preenchido por espaços; área exige tamanho correto, bytes
faltantes não são conhecidos. Consulta BEFORE Invoke e seus efeitos, pelo contrato
AIR/ProgramPoint existente. Alteração posterior não reescreve site anterior.

EXPLICIT_CONTRACT: `cics-ts.file@1` descreve essas regras, distinto de política
CALL/CICS PROGRAM. Nomes válidos retiram somente espaços finais; raw é preservado.
Nada de uppercase, trim inicial, filesystem, ambiente ou interpretação de ASSIGN.
Computed exige prova de área física8bytes/codecIBM1047; shape/tamanho/codec não
provados conservam desconhecimento. Expressions.Read de objeto/slice/choice usa
StorageSubject e providers gerais; expressão não admitida conserva site/remainder.
Literal não registra values. Candidatos têm supports próprios; unknown/remainders
não viram conjunto vazio conhecido nem desaparecem por truncamento.

Algoritmo implementado: registrar consultas BEFORE por entry/site no planner geral;
StorageValuesProvider compartilha um run por AnalysisKey e replay por batch. FILE
continua consumer próprio, lookup-only; não chama consumer/planner CALL. Reusa a
sessão/cache geral inclusive com CALL quando as chaves coincidem. Forma física
ampla pode exigir provider diferente do CALL escalar; medir, não alegar um único
run universal. Não alterar solver/CFG/norma nem contrato wire sem necessidade.
Custo adicional linear no inventário/consultas/candidatos+suportes; fixpoint e
terminação são os providers gerais existentes. Sem cutoff novo.

Oráculos independentes A4/T37: literal zero-values; a/FILE(X)/b/FILE(X); join
fechado, parcial com remainder, entrada unknown; ciclo finito; alias/refmod;
mutação sobre área target depois do site; efeitos disjuntos; nomes inválidos,
política errada, região/tamanho/codec não provados; CALL+FILE e métricas de sharing.
Arquivo manual passa validator/codec/writer/reader. W8 integra esse contrato aos
facts CICS do SP; W7 não antecipa seu catálogo nem ASSIGN DYNAMIC/W10.

Gates: C-VALUES,C-DEP,B-AIR/B-WIRE,FAST e Q-SHARED CFG. Fronteira SP/lower sem
alteração tem evidência W6 REUSED; E-SELECTED revalida consumidores nos mesmos pins.
Qualificação integral e FAST final PASS; próximo checkpoint W8.

D-WIRE refinement: reader2.1 recusa candidatos COMPUTED e qualquer política FILE
interpretada. W7 exige versão2.2 / file-values@1, preservando shape CALL e sites
locais2.1; reader2.1 congelado rejeitará2.2. Supports COMPUTED são VALUE_PRODUCER,
com origem/premissas próprias; literal conserva FILE_LITERAL. Quatro estados não
exigem novos campos; unknownRemainder agrega gaps de prova e motivos permanecem.

## Oráculos e implementação corrente

RED inicial: seis casos sem candidatos computados/interpretação literal; teste
negativo ajustado para declarar somente capabilities efetivamente usadas (manifesto
inválido não prova regra de nomes). Nove testes agora cobrem timing, quatro estados,
ciclos, alias/refmod, efeitos posteriores, compartilhamento CALL/FILE e políticas.
Segundo RED focal: literal CICS com ExactName foi indevidamente aceito; somente
cics-ts.file@1 interpreta CICS FILE. Assignment-name IBM continua ExactName no seu
namespace e não entra no solver. CALL nunca importa FileNamePolicy.

Focal C-DEP39 PASS antes dos dois últimos probes, focal FILE9 final PASS;
C-VALUES17 PASS; reader14 PASS antes do último refinamento de política, FAST final
PASS. Inventário bytecode atualizado com FileValueQuery/FileNamePolicy e
referências ao provider/facts/StorageSubject; nenhuma regra de fronteira relaxada.
Production em analysis-dependencies e writer apenas; providers/solver/CALL/IR/AIR
sem mudança. FileValueQuery roteia Read de ObjectPlace/RegionSlice/Choice e exige
área8bytes com codecIBM1047. Choice sem prova de tipo/tamanho fica desconhecido.
Todos os candidatos consultados preservam supports individuais. Valores inválidos
mantêm remainder/motivo, sem fabricar nome ou usar política CALL.

Sharing medido: duas consultas FILE em um entry selecionam1 provider; literal0.
CALL e FILE físicos compartilham o mesmo AnalysisKey: FILE recebe cache hits e
executa0 análises adicionais. Queries em pontos distintos continuam separadas.
CALL escalar/regional pode usar outra key; a métrica não soma runs fictícios.
`possibleValuesPreparations` conta providers selecionados na fase FILE;
`analysis.analysisRuns/analysisCacheHits` registra trabalho novo/reutilizado.

W6 pipeline mantém limites de controle externo CALL; o fechado manual W7 isola a
regra de nomes. Integração fonte→CICS FILE fica W8 conforme item; W7 revalida a coorte
real nativa nos mesmos pins, e não declara frontend CICS FILE antecipadamente.

## Qualificação em andamento

FAST497 e reader14 PASS (`fast-2.log`), zero skips. O primeiro Q-SHARED parou
em cinco ScaleTest: a caminhada de retenção contava o enum ProjectionPolicy,
entrada explícita da sessão, como payload adicional. Reproduzido no snapshot
isolado do HEAD W6 (`baseline-scale.log`); diagnóstico mostrou somente esse enum
AIR/CFG adicional. Corrigido o conjunto de raízes de entrada do oracle, mantendo
a proibição de cópias. Novo controle injeta cópia de Sequence e exige sua detecção.
ScaleTest7 PASS (`scale-fixed.log`); produção kernel/CFG/provider permanece intacta.
Q-SHARED foi reiniciado; a primeira falha e a reprodução ficam preservadas.

Revisão B-WIRE encontrou reader2.2 aceitando literal CICS inválido que o produtor
já recusava. Oracle adversarial preserva edge/site coerentes e altera caixa,
espaço inicial, hífen, comprimento9 ou espaços finais não interpretados: RED real
(`reader-literal-red.log`). Reader exige alfabeto/tamanho e retirada apenas dos
espaços finais; reader15 PASS (`reader-3.log`). Readers históricos congelados
permanecem intactos. A versão2.2 não herda normalização CALL.

Q2 passou Java542/arquitetura; falhou no gate de composição histórico que fazia
clean e omitia três produtores do reader regional. Seleção nominal ampliada com
LogicalEntryWireTest/LogicalChoiceTargetTest/PhysicalChoiceTargetTest; nenhum skip
ou exigência removida; gate focal semantic passou. Q2 bruto preservado.

Pré-checagem dos cinco E2Es do full revelou allowlists antigas SP<=2.8 e oráculos
anteriores ao controle aberto atual de CALL. Regra independente: Invoke com
outcome remainder AllControl e may-write AllMemory pode revisitar BEFORE CALL
após escrita desconhecida; target computado mantém remainder, literal independe
da memória. Expectativa de fechamento não é válida para esse AIR. W6 compilado
isoladamente e W7 produziram CALL sites/edges idênticos no mesmo AIR de diamond
(`baseline-call.log/json`). Oráculos passam a exigir esses bounds e o remainder,
sem relaxar candidatos, supports, provenance ou determinismo. Admission usa versão
SP exatamente pinada; modelos AIR fechados continuam exigindo fechamento nos
W2dModelTest/MoveCopyModelTest/PerformBasicModelTest, preservados no FAST/full.

O snapshot real também admite PROGB: após CALL, o bound AllControl permite entrar
na cópia WS-A→WS-PGM depois da escrita PROGB. Oracle exige exatamente A+B e seus
supports distintos; contraprova normal-only sobre o mesmo AIR exige somente A e
model remainder=false (`call-control-counterproof/`). Essa variante explícita
foi incorporada ao E2E MOVE. Multi-call6/7 exigem os conjuntos manuais sob o mesmo
bound, incluindo retorno de FORCE-C ou repetição da cópia; os demais conjuntos
não mudam. Não há mudança produtiva CALL, storage, solver ou lower nesta correção.

Pré-gates E2E corrigidos PASS: diamond2×2 + permutação; MOVE4 + snapshot
normal-only; PERFORM3×2 + permutação; multi-call7×2 + três W1×2; parcial22×2
+ permutações. Candidatos/supports exatos continuam exigidos. AIR manual FILE10×2
via CLI PASS (quatro estados, timing/alias/refmod/ciclo/efeitos, sharing e reader2.2).
Nova execução integral de qualification-local e FAST final PASS.

## Resultado integral

`python3 -B scripts/harness/lean.py qualification-local` exit0 em qualification-3.log:
suíte completa542, zero skips, boundaries, gates semânticos, cinco gates de escala,
integração arquivo/memória/CLI, produtores reconstruídos nos pins W6 e cinco E2Es
finais PASS. Inventário nominal de ScaleTest inclui o novo controle de cópia.
A escala é observação, sem SLA/claim de precisão ou recall. E-SELECTED nativo33×2
PASS em e2e-1.log; manual FILE10×2 PASS em manual-cli-1.log. Fronteiras SP/lower/AIR
inalteradas REUSED W6; fonte CICS FILE/W8 e escopos/W9 ainda NOT_RUN. Nenhum blocker.

FAST final497 + reader15 PASS, exit0 (`fast-3.log`,81.955s observados). H-DOCS e
diff-check PASS. Testes geraram novamente os18 casos manuais: mesmos36 arquivos
AIR/dependency do primeiro FAST estável. Nenhum build antigo usado. FAILs/REDs
anteriores preservados; corpus/qualificação final N+C NOT_RUN até W11; W10
NOT_AUTHORIZED. PR38 permanece Draft/unmerged, sem alteração de pins upstream.
