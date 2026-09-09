# W3 — matriz de provas

Todas as Publications destas provas passam pelo BuildCfg real, exceto as leis do
domínio isolado. Solver e replay usam a mesma função de transferência produtiva.
As expectativas não são regeneradas a partir do builder.

| Obrigação | Prova nominal |
| --- | --- |
| Subject ObjectId, mesmo display e Cells distintas | ValuesTest.diamondsMissingPathsStrongUpdatesAndOtherCells, C em object-0 / B em object-1 |
| Dois Objects da mesma Cell | ValuesTest.sameCellAliasesAndDisjointnessAreSemanticAdmission, ambos B após overwrite |
| Disjunção não inferida de ID/transitividade | Mesmo teste: sem prova e provas fragmentadas recusadas |
| Bottom ≠ reached/missing; strong update | DomainTest.missingStrongUpdateAndJoinHaveIndependentExpected |
| ACI, upper bounds, monotonicidade | DomainTest.finiteLatticeLawsAndAssignmentMonotonicity; 50 estados do universo finito independente |
| AVL imutável, sharing sem full clone | DomainTest.persistentUpdatesShareAndRetainNoHistory; 10k bindings, overwrite <64 nós novos, >9900 compartilhados |
| Convergência/candidatos sem cap | DomainTest.allFiniteCandidatesSurviveAndUnionConverges; 8/9/100/1k/2k/4k/10k |
| Linear literal PROGA antes de Return | ValuesTest.realVerticalMixedBatchHasOneObservationPerLogicalQuery |
| Before/entry unknown, after Assign fechado, after Return unsupported | Mesmo teste, lote com dedup e campos ausentes nos unsupported |
| Outcome AIR normal/exceção/tag/outros | Mesmo teste; Control.OutcomeKey preservado, indisponíveis explícitos |
| Model/source/effective separados | ValuesTest.partialSourceIsSeparateFromExactModelAndUnknownWitness; PARTIAL real e unknown real |
| Diamante definido/definido e definido/missing | ValuesTest.diamondsMissingPathsStrongUpdatesAndOtherCells: {A,B}/closed e {A}/open |
| Effects recusados antes de solver | ValuesTest.unsupportedReadAndIndirectStorageAreNotIdentity: Assign(Read), HavocMust, HavocMay, CopyBytes e AliasBinding |
| Entry seeds/contexto/orphan/Unicode | ValuesTest.entrySeedsContextsUnreachableAndUnicodeKeepTheirIdentity |
| Âncora OUT e ordem backward | ReplayTest.backwardReplayUsesStableOutAndReverseSuffixOnce, def/use e uso antes de definição |
| Batch falha atomicamente; stable run preservado | ReplayTest.controlledObservationFailureIsAtomicAndPreservesStableRun |
| Result de outra sessão não vira unreachable | ReplayTest.foreignStableRunCannotMasqueradeAsUnreachableInAnotherSession; RED/GREEN preservados |
| Recomposição abstrata independente | ValueOracleTest.generatedRealAirMatchesIndependentRecompositionAndConcreteOracle |
| Oracle concreto finito e critério de precisão | Mesmo teste + concreteAndPrecisionOraclesRejectSharedWrongOrAlwaysUnknownAnswers |

Corpus: 48 grafos gerados de seed 27001, 213 pontos; branches, joins, ciclos e órfãos.
O oracle abstrato usa sets/recomposição em arrays derivados do gerador, sem domínio,
worklist ou transfer produtivos. O concreto explora estados (node, valor) num universo
A/B/UNMODELED_INITIAL, sem consultar answer abstrata para produzir o esperado.
Além de inclusão sound, sets fechados precisam de precisão exata; always-unknown
passa a inclusão, mas falha o critério de precisão. Esse universo concreto é uma
abstração finita de teste, não uma declaração de completude do perfil AIR.

A camada de controle continua sem filtrar Branch pelo valor do predicate. Cada arco
conhecido do CFG pertence ao modelo. As provas não reivindicam path feasibility,
correlações entre Objects, reaching definitions ou extração de dependências.

Escala: ValuesScaleTest contém 7 métodos, 30 linhas W3_METRICS e 3 W3_STAGGERED.
S1 10k/20k/100k/200k operations; S2/S16 1k/2k/4k/10k Objects com um binding;
S3 sequence/chain com 1k/2k/4k bindings vivos; S7 8/9/100/1k/2k/4k/10k candidatos;
S4b wide e escalonado; S6 batches 1k/2k/4k queries únicas com requests duplicados;
S9 texto com 1k/2k/4k escalares não BMP. Não há threshold de admission/precision.
