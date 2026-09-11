# Gap matrix — discovery only

Taxonomia: G0 já suportado; G1 transporte/codec/assessment da fronteira; G2 frontend
Semantic Product; G3 lowering; G4 norma/modelo AIR; G5 CFG/index/site planning;
G6 dataflow/perfil/efeitos; G7 consumer/contrato de saída. Um finding pode referir
outro, mas tem classificação primária explícita. Nenhum finding autoriza implementação.

| Finding | Classe | Estado no baseline | Evidência | Mudança mínima proposta / owner |
| --- | --- | --- | --- | --- |
| Parser CALL e modos | G0 | EXISTS | F01–F03; probes | Nenhuma gramática nova para os casos selecionados; proleap |
| CALL variável nominal + source site | G0 | EXISTS | F04–F06; dynamic-x8 | Conservar IDs/selected binding e provenance |
| Literal CALL no SP | G2 | MISSING | F05 callCapability; literal observed | Target literal tipado em SP, sem catálogo; proleap |
| Whole-item CALL operand | G2 | MISSING | DataReference ctor sem wholeItemAccess | Publicar prova escalar/acesso inteiro para target; proleap |
| Continuação normal CALL | G2 | MISSING | CallFact não tem campo; AstBuilder map só MOVE | Publicar successor sustentado pelo source, não readiness textual; proleap |
| USING/RETURNING/exception clauses no SP | G2 | PARTIAL/OUT_OF_SLICE | AST conserva; SP gaps, exceptionFlow achatado | Primeira admission recusa; expansão posterior preserva partições, efeitos e modos |
| MOVE literal X8/len5 | G2 | MISSING | ScalarMoveSemantics enum Copy; probe X8/X5 | Fitting/padding com prova publicada; proleap; lower consome sem PIC parsing |
| IF multi-path frontend | G2 | PARTIAL | Shape RELATION sem semântica completa; child continuation ausente | Controle THEN/ELSE/merge tipado; predicado bool conservador explicitamente publicado; proleap |
| Decoder wire CALL atual | G0 | EXISTS | Wire/Wire12 CallDocument | Não confundir transporte parcial com domínio |
| Payload perdido no adapter lower | G3 | MISSING | Materialize → OtherStatement | Domínio CallFact tipado e mapeamento; cobol-lower |
| CALL lowering/outcomes/effects | G3 | MISSING | Admission e assembler; cinco recusas | Handler/assembler de Invoke neutro sob slice delimitada; cobol-lower |
| AIR invocation normativa | G4 | EXISTS, sem gap necessário | N01–N07 | **AIR version change NONE**; Invoke é terminador |
| air-java Invoke/Target/effects | G4 | EXISTS, sem gap de tipo necessário | J01–J06 | Reutilizar modelos; sem CobolCall |
| Invocation estrutural e I-56 | G0 | EXISTS | J07,J08,J12; invoke-probe | Não apagar obrigação externa do validator |
| Invocation JSON mappings | G1 | MISSING | BindingReader/Writer; IMPLEMENTATION_LIMIT | Invoke + subconjunto transitivo de target/read/signature/outcomes/effects/origins; air-java |
| JSON assessment de obrigação | G1 | MISSING policy compatível | AirJson.validate; I-56 sempre; probe isolado | Review explícito de transporte com assessment; bloqueio mesmo após mappings; air-java |
| Branch/Jump/predicate JSON multi-path | G1 | MISSING | Writer Return/Literal only | Coverage necessária ao IF conservador real, sem expandir toda AIR |
| CFG Invoke | G5 | MISSING | C01,C02,C24; probe | Outcomes intraprocedurais e payload/saídas; analysis-cfg |
| Index Invoke | G5 | MISSING | IndexBuilder payload/arity/transitions | Atualização coerente com CFG; manter IDs/offsets |
| BEFORE interior operation | G0 | EXISTS comprovado | PointProbe; ReplayTest/ValuesTest | **ProgramPoint API change NONE** |
| SitePlanner generic registration | G0 | EXISTS | SiteInterest/Planner/PlanningExecution | Registrar consumer separado; nenhuma semântica CALL no planner |
| Scalar profile effects | G6 | MISSING, atualmente recusa | TextProfile; ValuesTest adversariais | Intérprete genérico de limits/havoc em perfil versionado; analysis-values |
| Modelo de memória para havoc amplo | G0 | EXISTS | Scopes.AllMemory/VisibleMemory; Candidates unknown/join | Nenhuma nova memória/lattice demonstrada |
| Solver generic fixpoint | G0 | EXISTS | PossibleValuesAnalysis transferBlock/edge, W2/W3 | **Solver change NONE** |
| Name interpretation/padding | G7 | MISSING published contract | N01 sem trim implícito; F09 só lookup nominal | Review da regra/dialeto; neutral expression projection ou policy extension negociada |
| Arbitrary target expression query | G6 | PARTIAL | PossibleValuesProvider só ObjectId | Interpretar expressão neutra sobre fatos ObjectId fora do solver; não alegar API Expression existente |
| Literal reachability | G5 | MISSING provider, SPI serve | SiteView STRUCTURAL; ValueFact só caminho dinâmico | Provider genérico de reachability sem executar values desnecessariamente |
| Candidate-specific supports | G0 | EXISTS | ValueFact.CandidateSupport; SupportSourceTest | Preservar associação e derivação no consumer |
| Source uncertainty no resultado | G0 | EXISTS, precisão ampla | TextProfile.sourceOpen; frozen E2E false/true/true | Preservar; precisão mais local é futuro separado, não requisito para inventar source=false |
| Dependency consumer e output | G7 | MISSING | W5 ResultJson/ObservedValueFact only | Produto por site e projeção por edge; consumer externo; sem result-v1 incompatível |

Referências: [catálogo fixado](source-catalog.md). Prioridades e dependências estão
no [plano futuro](future-waves-oracles.md). G4=EXISTS é uma descoberta positiva,
não autorização de ampliar modelo ou norma.
