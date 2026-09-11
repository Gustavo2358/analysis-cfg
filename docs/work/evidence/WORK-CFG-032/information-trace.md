# Cross-repo information trace

**DISCOVERY / NOT IMPLEMENTED.** Autoridade: [baseline](discovery-baseline.json).
Símbolos F/L/N/J/C: [fontes por SHA](source-catalog.md).
Oracle: `MOVE 'PROGA' TO WS-PGM`, `WS-PGM PIC X(8)`, `CALL WS-PGM`, `GOBACK`.
“Sim” descreve fatos existentes nessa etapa, não sucesso downstream.

| Stage | Repository | Representation | Identity preserved? | Origin preserved? | Target preserved? | Arguments preserved? | Already implemented? | Gap | Required change |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ProLeap parse | proleap-poc | Cobol.g4.callStatement: identifier/literal, using/giving/exception contexts | Contexto sintático, não DataItemId | Tokens/spans | Sim, sintaxe diferenciada | Sim, modos da gramática | EXISTS (F01) | Sem promessa de todos os dialetos | Nenhuma sintaxe nova para o caso principal |
| AST + resolução | proleap-poc | Ast.CallStatement; DataReference/ProgramReference; CallArgument; occurrence CALL_TARGET | Nó+ocorrência, binding DATA nominal selecionado | Meta/SourceSpan/sourceMap original+expanded | Sim | Sim; returning também | EXISTS/PARTIAL (F02,F03,F08) | exceptionFlow achatado; nominal não é runtime | Preservar partições somente ao ampliar handlers; não confundir lookup com values |
| Semantic Product CALL | proleap-poc | CallFact.header/syntax/operand/runtimeTarget | operandId + NominalBinding.selected data:0 | Header e operando | Variável sim; literal não | Não; gaps explícitos | PARTIAL (F04,F05) | wholeItemAccess null, CALL sem normalContinuation; literal observed perde PROGA | Target sum literal/data, prova acesso, continuação e restrições/contrato neutro publicável |
| Semantic Product MOVE | proleap-poc | MoveFact + scalar copy/access/continuation | statement:0, destination data:0 | Statement/operand | Valor lógico PROGA preservado, não resultado ajustado | N/A | PARTIAL (F07) | X8/len5 não FULL_IDENTITY | Publicar fitting/padding semântico; não trocar fixture por X5 |
| SP JSON | proleap-poc | schema cobol-semantic-product 1.2.0; CallDocument | Binding/IDs presentes | Sim | Somente payload do CallFact existente | Não | PARTIAL (F06) | Transporta perdas anteriores; sem novo target literal | Evoluir contrato SP e writer compativelmente com decoder |
| cobol-lower input | cobol-lower | Wire/Wire12.CallDocument + SpJsonDecoder | Wire lê header/ReferenceDocument | Wire lê provenance | Wire lê operand atual | Ausentes no input | PARTIAL (L01–L03) | Decoder não implica lower suporte | Ler campos novos com versão/capability explícita |
| Lower semantic representation | cobol-lower | Materialize → OtherStatement(header,CALL) | Header sim; DataItemId do target perdido | Header sim; operand origin perdido | Não | Não | MISSING para CALL tipado (L04,L05) | Payload descartado | CallFact de domínio; mapeamento completo de target/acesso/origem/continuação |
| Lower AIR emission | cobol-lower | ScalarMoveAdmission/ScalarSequenceAssembler | Assign/Return IDs existentes | SourceOrigins já existe | CALL recusado | OUT_OF_SLICE | MISSING (L06–L10) | Perfil só MOVE raiz+GOBACK; sem Invoke; X8 também recusado | Fitting sob prova SP; Invoke neutro, continuations, efeitos/outcomes; admission explícita |
| AIR normative | analysis-ir | invoke terminator, ComputedResource(Expression text) | OperationId/operand/ObjectId via read | Exigida por contrato | Sim, literal/computed/internal | value/reference/copy | EXISTS como norma (N01–N07) | Nenhum conceito novo necessário | Nenhuma nova versão AIR; obedecer contrato atual |
| air-java model | air-java | Operations.Invoke, ComputedTarget(Read(ObjectPlace)), LiteralTarget | Tipos IDs completos | Header/operand/target/contract | Sim | Sim | EXISTS (J01–J06) | Cobertura de modelo não é codec/perfil | Reutilizar, não criar CobolCall |
| AirValidator | air-java | invocation/refs/signature/effects/outcomes | Valida fechamento | Valida referências | Valida CALL_TARGET known(TEXT) | Valida modos/transmissão | EXISTS/PARTIAL assurance (J07,J08,J12) | I-56 obrigação externa sempre presente | Preservar obrigação; decidir transporte/assessment, não removê-la |
| air-json | air-java | BindingReader/Writer + AirJson.validate | N/A para Invoke recusado | N/A | Não | Não | MISSING (J09–J11) | IMPLEMENTATION_LIMIT; depois I-56 → INCOMPLETE_VALIDATION | Coverage mínima e política explícita de assessment; Read e normalização necessárias |
| CfgBuild | analysis-cfg | CoreCfgProjection Sequence node | Retém operações admitidas | Retém AIR original | Invoke recusado | N/A | MISSING para Invoke (C01,C02,C24) | UNSUPPORTED_TERMINATOR | Projeção neutra de outcomes + adaptação de transições/snapshot; sem caller→callee |
| ProgramIndex/SitePlanner | analysis-cfg | IndexBuilder; ProgramIndex buckets; SiteInterest | Operação original+Entry+offset | Via AIR original | Campos disponíveis depois de admitir operação | Campos genéricos acessíveis | PARTIAL (C03,C04,C11–C15) | Index rejeita terminador; selector genérico já serve | Ampliar index/control validation; registro consumer, não planner COBOL |
| Exact point | analysis-cfg | ProgramPoint.before(entry,operation); prefix replay | Sim | Query aponta site original | Subject ObjectId | N/A | EXISTS (C05,C06) | Invoke ainda não admitido | API point/solver: NONE; usar ID de Invoke após fronteiras verdes |
| PossibleValues | analysis-cfg | scalar-text-direct@1; ObjectId → ValueFact | Cell/Object/Assign supports | Support.origin + candidate association | Valores text genéricos | Não requer tracing de args | PARTIAL (C07–C10,C16) | Invoke/Havoc recusados; expressão de nome não é ObjectId direto | Novo perfil/intérprete genérico de effects; projeção neutra de expressão se usada |
| Call consumer | analysis-cfg, futuro módulo externo | CallTargetResolver conceitual | Deve conservar publication/Entry/site/operand | Deve separar site/target/support/derivation | Interpreta valores como referências | OUT_OF_SLICE na primeira vertical | MISSING | G7; literal precisa reachability independente | Consumer W4 + provider reachability + mapeamento candidates/supports/remainders |
| Dependency fact | analysis-cfg, futuro adapter | Site fact primário; edges derivados | IDs escopados/revisão e consumer version | Por candidato e site | Known candidates + open | OUT_OF_SLICE | MISSING | Wire W5 atual só valores | Produto separado compatível; sem persistência nem catálogo obrigatório |

## Controles observados

| Probe | SP observado | Lower observado |
| --- | --- | --- |
| dynamic-x8, oracle exato | CALL tipado/binding resolvido; MOVE_IDENTITY_NOT_PROVEN; target wholeItemAccess null | Exit 4, UNSUPPORTED_SLICE: MOVE não FULL_IDENTITY + CALL fora de perfil |
| dynamic-x5-control | MOVE FULL_IDENTITY; CALL preservado parcialmente | Exit 4, CALL fora de perfil |
| literal | ObservedStatement CALL_LITERAL_TARGET; valor PROGA ausente da representação SP | Exit 4, observed CALL fora de perfil |
| using-modes | AST tem REFERENCE X/CONTENT Y/VALUE Z e returning; SP gaps de arguments/returning/exception | Exit 4, slice recusada |
| multi-path | IF/THEN/ELSE e bindings; condição parcial; child MOVEs sem continuação e sem fitting X8 | Exit 4, IF/CALL/shape/proof fora de perfil |

Logs e bytes originais estão no [arquivo de probes](raw/frontend-lower-probes.tar.gz).
Nenhuma AIR CALL nem dependency fact foi injetada após o lower para simular E2E.
