# FD-W8 — CICS File Control

**QUALIFIED_LOCAL**. Produtores, integração e Q-SHARED CFG PASS.
W0–W8 qualificados; próxima W9. W10 NOT_AUTHORIZED; nenhum merge/release. Entrada: CFGbf8f8cd,
frontend4f63f10/lower4e8e129/AIRd215d2ba; worktrees exclusivos conferidos.

## Autoridade e regras

CICS TS for z/OS **5.6**, PDFs com edição5.6/copyright2023, consultados2026-09-17:

- [API Reference](https://www.ibm.com/docs/SSGMCP_5.6.0/pdf/api-reference_pdf.pdf),
  SHA256 `3c3295d5b7f013a559b1cc742a7e810c0247bfc0bd3790b3545ab08327bc3c5b`.
  Cap1pp3–4 nomes/papéis, p9 LENGTH/NOLENGTH, p10 RESP/RESP2/NOHANDLE;
  DELETEp115, ENDBRp149, READp351, READNEXTp362, READPREVp372,
  RESETBRp419, REWRITEp436, STARTBRp542, UNLOCKp569, WRITEp674.
- [System Programming Reference](https://www.ibm.com/docs/SSGMCP_5.6.0/pdf/systemprogramming-reference_pdf.pdf),
  SHA256 `417fe5ed2dfb3c5c630919e3e9abbd1358ad3ed25f162f59b4b29a99c0021f4d`.
  INQUIRE FILEp359, SET FILEpp672–673; browse pp19–21.

Textos/PDFs locais: fd-w7/authority(API), fd-w8/authority(SPI), sem versionar PDFs.
Regras6.x/TX/5.5 não usadas. DATASET é alias de FILE em SET, OBJECTNAME de DSNAME;
nenhuma universalização do alias para outro comando sem autoridade. Atributos
DSNAME administrativos nunca são target FILE nem resolução externa.

INQUIRE NEXT recebe FILE8; não lê o valor anterior como nome de entrada. START/END
administram browse sem nome individual fornecido; END pode conservar as áreas.
FILE input/literal/computed não exige SELECT/FD. READQ/WRITEQ/journal/RETURN não
são FILE. Program Control conserva contribuição e classificação próprias.

INTO é buffer; SET é ponteiro. FROM e argumentos de entrada são leituras, sem
inventar escrita ou dependência READ do FD proprietário. READpp353–355 permite
overlay além da área quando LENGTH excede o destino. **LENGTH implícito depende
de NOLENGTH**; o contrato SP não certifica essa opção do translator. Portanto a
mera ausência de LENGTH não prova bound no INTO/FROM. Width/layout/key não
provados mantêm bound conservador. RESP/RESP2 são fullword output no retorno;
MUST somente no NormalOutcome e nos quatro bytes exatamente provados. Demais
outcomes mantêm MAY; nenhum MUST pelo verbo. RESP implica NOHANDLE, RESP2 sozinho
não. Continuations locais preservadas; condições não fechadas ficam explícitas,
sem transformar handlers em fluxo incondicional.

## Contratos e implementação

SP2.28/fileInventory1.6/storage1.8: variante fechada CICS_FILE_CONTROL com catálogo,
target mode INPUT/OUTPUT/BROWSE_START/BROWSE_END, roles, options, host references,
origens, condições e continuations. Scanner de opções compartilhado, gramática
COBOL canônica para host operands, facts FILE separados. Projector só transporta.
Lower admite pelas portas wire/memory, rejeitando papéis/owners/contradições.
Algoritmos proporcionais aos fatos/opções; nenhuma reinterpretação downstream.

AIR usa norma existente: KnownContract `cics-ts.file-control@1`, categoria `file`,
namespace `cics.file`, política `cics-ts.file@1`. Argumentos VALUE:
`(selection: TEXT DEFAULT/EXPLICIT, SYSID: TEXT)`, seguidos de REQID INT em browse
(default0; host não provado fica unknown). Ausência é `(DEFAULT, "")`; EXPLICIT
vazio inválido não vira ausência. Signature tipada e operandos/efeitos preservados.
Codec passou a transportar KnownParameters e EffectBound.perOutcome (todos os
cinco OutcomeKey normativos). **Sem mudança de modelo/norma analysis-ir em W8**;
limitação de codec não foi tratada como perda de representação. D-AIR permanece
fechada. UnknownMode/bindings/results não cobertos mantêm limites explícitos.

SYSID computado usa quatro bytes IBM1047, FILE oito, ambos consultados BEFORE no
StorageValuesProvider geral. Nomes seguem APIp4; somente espaços finais removidos.
FILE/SYSID mantêm projeções, supports e remainders independentes, sem afirmar
correlação ou produto cartesiano. DEFAULT não prova localidade e não cria gap por
configuração externa. Literal não demanda solver; CALL/FILE/SYSID compartilham
preparação quando a key coincide. Não há solver FILE nem lookup externo.

Wire **2.3.0 / file-values-context@1** acrescenta contexto em FILE site/edge
(null para native). Reader2.2 congelado rejeita2.3; reader atual fechado exige
coerência de seleção, nomes, supports, BEFORE e projeção edge. CALL sites/edges
históricos não foram redefinidos. D-WIRE estendida pelo oracle antes da emissão.

## Pins e gates

| Repo | Pin / estado |
| --- | --- |
| frontend PR54 | `4356722155b83966d716b47e1d8a918e4a7f9649` |
| lower PR30 | `dcb6f49e5edc3e6b06e9ccb7fb35e4dd09bd5307` |
| AIR PR19 | `0035c5af165c90973a3645bae8a7e286511470e5` |
| norma PR7 | `fb153ae50f343022db45d20d627e1afac85de916`, REUSED W1 |
| CFG PR38 | commit desta wave no Git; SHA final no handoff local E2E |

Comandos nos logs `.harness-results/fd-w8` de cada repo; exit0 nos PASS.

| Estado | Gate / propriedade |
| --- | --- |
| PASS | Frontend F-CICS/declarativo/composição45 + projector36; FAST345; Q-SHARED938 (um skip histórico previsto), normalizer full/naming |
| PASS | AIR codec parâmetros/efeitos/outcomes, negativos independentes; FAST model187/codec126/harness40 |
| PASS | Lower B-SP33 byte a byte, 12comandos/6wire+24memory+target-role negativos, codec e composição1/2/5/40; FAST2340 core+adapters; Q-SHARED244296 semântica/39215 performance/arquitetura |
| PASS | CFG C-DEP35, C-VALUES17, AIR manual FILE7/computed9/context5 (14 na contraprova final), reader18; FAST5 502 testes e fronteiras |
| PASS | E-SELECTED38 CICS×2 +33native×2, quatro produtos e determinismo nos pins acima; e2e-cics-3/e2e-native-1, exit0 |
| PASS | qualification-local CFG, exit0; suíte547 sem skips, fronteiras, cinco gates semânticos/escala, transporte e E2Es CALL/MOVE/PERFORM/parcial nos pins finais |
| NOT_RUN | corpus/performance final/metamórficos/mutantes de W11; escopos W9; D/W10 não autorizado |
| BLOCKED | nenhum |

Oráculos manuais precederam produção: catálogo/negativos, quatro estados/timing,
SYSID distintos, alias/refmod/ciclo, INTO/SET/FROM/RESP, SPI output e anti-heurística.
FAILs/REDs preservados: versão/tipo ausente; KEY reservado em fixture; LENGTH OF
alocava AST descartado (corrigido no bridge canônico); projector findFirst trocado
por assertion de unicidade; papel FILE WRITE indevido rejeitado; generator de
composição converte CALL_TARGET legado para READ. Oracle preliminar de LENGTH
implícito foi corrigido pela contraprova normativa p9 antes da qualificação.

FAST CFG: warning JVM/hsperfdata contaminou javap, diff dos mesmos classfiles foi
vazio e baseline não mudou por isso. Gates seguintes identificaram novos tipos e
arestas FILE/contexto esperados; inventários W5/W1D atualizados somente nesses
pontos após diff, preservando CALL/providers. E2E inicial recusou classpath ausente
após clean; classpath runtime reconstruído. Nenhum gate foi relaxado.

Limites intraprogram explícitos: efeitos sem width/layout provado, outcomes não
fechados, nomes/computações fora da política e ausência de correlação FILE/SYSID.
Esses limites não incluem DSNAME/JCL/configuração externa. Escala é observação,
sem SLA ou alegação de precisão/recall. PRs persistentes OPEN/DRAFT/UNMERGED.

### Contraprova de fechamento do fonte

E-SELECTED encontrou candidatos corretos em REDEFINES/ciclo e nos demais nomes
computados, mas o oracle inicial exigia fechamento efetivo apesar de SP
PRIMARY_ONLY/PARTIAL e cobertura AIR PARTIAL. A infraestrutura geral preserva
essa abertura do fonte, separada de FILE_MODEL_VALUE_REMAINDER. Contraprova
manual usa instruções idênticas e altera somente Coverage COMPLETE→PARTIAL com
gap explícito: candidatos/supports/modelo se conservam, remainder do fonte abre
para FILE e SYSID. Não foi alterado solver/producer nem ocultada uncertainty.

O oracle integrado continua exigindo todos os conjuntos manuais, distinguindo
closed/partial/unknown **no modelo**, e agora exige também a abertura efetiva
publicada pelo fonte. O conjunto efetivamente fechado continua provado pelos
AIR manuais completos de W7/W8. Limite atual da pipeline: cobertura primária e
controle/efeitos CICS parciais podem abrir globalmente o remainder do fonte, mesmo
quando o conjunto no modelo é fechado. Isso é parcialidade intraprogram explícita,
sem atribuí-la a JCL/DSNAME/configuração externa. Tentativas preservadas.
