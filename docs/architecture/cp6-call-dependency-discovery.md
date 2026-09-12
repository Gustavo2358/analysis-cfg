# CP6 — dynamic COBOL CALL dependency discovery

> Registro histórico: discovery APPROVED / MERGED. O conteúdo abaixo preserva o diagnóstico do baseline anterior. A implementação W1D tem [contrato próprio](analysis-dependency-result-v1.md).

**DISCOVERY / NOT IMPLEMENTED — DISCOVERY_COMPLETE / AWAITING_HUMAN_REVIEW.**
Work item: [WORK-CFG-032](../work/history/WORK-CFG-032/work-item.json).
Implementação: **NOT_STARTED / NOT_AUTHORIZED**. Este documento propõe responsabilidades
e critérios de aceite; não congela APIs, perfis, wire formats nem decisões de implementação.

## 1. Recomendação e limites encontrados

É viável reutilizar o solver e o planejamento genéricos. O baseline, porém, **não
executa CALL → dependency**. Não é uma lacuna apenas no consumer: existem perdas no
Semantic Product (SP), no adapter do lower, no codec AIR, na projeção de controle e
na admissão de efeitos. O exemplo exato `PIC X(8)` também excede a prova de MOVE
atualmente publicada. Os cinco probes COBOL produziram SP e foram recusados pelo
lower; nenhuma AIR de CALL foi produzida.

A menor direção recomendada é preservar o CALL e sua continuação no frontend,
materializar o **Invoke neutro já existente**, completar seu transporte e projeção
intraprocedural, interpretar efeitos conservadores em um perfil de valores e criar
um consumer externo que consulte valores **BEFORE a operação identificada**. A
interpretação de nomes de programas pertence à fronteira de linguagem/consumer,
nunca ao solver. O plano de execução W4 pode compartilhar as consultas por AnalysisKey.

Há uma correção obrigatória da hipótese inicial: **AIR 2.0 define Invoke como
terminador**, inclusive para chamada síncrona que pode retornar. A Sequence termina
em Invoke; seu outcome normal aponta explicitamente para outra Sequence. Transformá-lo
em instruction contrariaria a autoridade congelada. Não se recomenda essa mudança.

| Pergunta de encerramento | Resposta factual/recomendação |
| --- | --- |
| SP preserva CALL? | **PARTIAL**: variável com identidade nominal e origem; literal vira observed sem o valor; argumentos/resultados/branches excepcionais não são publicados |
| AIR normativa possui invocation? | **YES**: invoke neutro, targets literal/computed/internal, efeitos e outcomes |
| Modelo air-java suporta? | **YES**: Operations.Invoke e Interactions completos para esse conceito; isso não certifica perfil completo |
| air-json suporta? | **NO**: IMPLEMENTATION_LIMIT; há também bloqueio independente de obrigações semânticas |
| lower suporta? | **NO**: lê o wire CALL, descarta payload em OtherStatement e recusa a slice |
| CFG precisa mudar? | Projeção/admissão dos outcomes de Invoke, kinds/payloads de transições e validação/indexação correspondente; sem edge para CFG do callee |
| Solver precisa mudar? | **NONE**: não falta propriedade genérica demonstrada; profile/effect interpreter e admissão precisam evoluir |
| W4 SitePlanner serve? | **YES**, depois da admissão de Invoke; selection por classe/filtro e queries por site já existem |
| Onde fica o resolver? | Consumer de aplicação externo ao kernel/solver e a PossibleValues; proposta de módulo de dependências no analysis-cfg |
| Primeira vertical? | CALL literal e CALL de WS scalar text, sem USING/RETURNING/exception handlers; MOVE literal com fitting explícito; saída por site |
| Repos a modificar nessa vertical futura? | proleap-poc, cobol-lower, air-java, analysis-cfg; artefatos-e2e para evidência integrada, conforme autorização própria |
| Ordem? | Review das decisões abaixo; vertical singleton/literal completa; depois IF real com multi-candidate/open, mantendo o solver |

Três decisões exigem review **antes de implementação**: política de transporte de
AIR estruturalmente válida com obrigações semânticas, interpretação do nome após
padding e contrato de efeitos/outcomes admitido. Alternativas em §10. Não foram
decididas silenciosamente. Não há necessidade demonstrada de nova versão AIR,
modelo de memória, lattice, solver interprocedural ou mudança incompatível do
resultado W5.

## 2. Autoridades e evidências

| Repositório | Commit exato | Tree exata |
| --- | --- | --- |
| proleap-poc | 8722945cc4cd2052c6091533f6ee6989278aa2f8 | 63eba137162aa47e38d4858ff522058a8678a8dd |
| cobol-lower | 18016f16b4f63149eb1bb4ca13db7e12593d8909 | 0139edc52e34c703b643ff484a33e70871ef091d |
| analysis-ir | 51b4d9a8ae0364232bd97103cd73a77e1a34996c | 11b2361a40cb5ae9b6c201f0157f30ef2387d1b8 |
| air-java | 3bafe3978f0f392e842038ad5628e85dfd91d00d | 201096ee137a897fb43b22e4c831d82e948d6f81 |
| analysis-cfg | e9daaed9f8a2f2df3926a624f1067aba3d53297c | 30f854c3c5dc626d2e2185611efa5fd254c6a502 |

Receipt técnico preservado byte a byte: SHA-256
`4f61539c7bc09120ef05abf9efb818e61870097b506e173eaed38735bba331e0`.
O receipt runtime original cita analysis-cfg `89ee2ca3f922f021f149d4ad7273056d0c402b9f`.
O adendo PRECP6-F1 registra o closeout documental até `e9daaed`, alterando somente
lifecycle/manifest, sem mudança runtime. Não se reescreveu o receipt para esconder
essa diferença. O [receipt do discovery](../work/evidence/WORK-CFG-032/discovery-baseline.json)
registra os cinco objetos efetivamente inspecionados e essa cadeia.

Checkouts ocupados foram preservados. Quatro fontes foram extraídas por `git archive`
dos SHAs; analysis-cfg usa worktree isolada, branch documental
`discovery/cp6-call-dependency`, criada em e9daaed. Fetch/prune não mudou a autoridade
do discovery. O [catálogo](../work/evidence/WORK-CFG-032/source-catalog.md) contém
links por SHA, arquivo, símbolo/campo e linha de navegação. As referências F/L/N/J/C
nas seções seguintes remetem a esse catálogo.

Entregas complementares:

- [Information trace](../work/evidence/WORK-CFG-032/information-trace.md).
- [Gap matrix](../work/evidence/WORK-CFG-032/gap-matrix.md).
- [Waves e oráculos futuros](../work/evidence/WORK-CFG-032/future-waves-oracles.md).
- [Probes, resultados e limitações](../work/evidence/WORK-CFG-032/README.md).

## 3. ProLeap → AST → Semantic Product → JSON

O frontend usa os contextos da gramática ProLeap vendorizada; não depende de um
ASG CALL externo para publicar esses fatos. `Cobol.g4.callStatement` admite
identifier/literal, USING, GIVING/RETURNING, ON OVERFLOW, ON EXCEPTION e NOT ON
EXCEPTION. As regras de argumentos distinguem REFERENCE (default), CONTENT e VALUE,
incluindo as variantes que a gramática suporta. Isso comprova a sintaxe observada,
não suporte a todo CALL de qualquer dialeto. [F01](../work/evidence/WORK-CFG-032/source-catalog.md#f01)

`AstBuilder.buildCall` cria `Ast.CallStatement(meta,targetSyntax,target,arguments,
returning,exceptionFlow)`. Literal vira `ProgramReference` com texto sem aspas;
variável vira `DataReference`. `CallArgument` conserva modo, kind, expressão e
writtenText; o coletor gera ocorrências CALL_TARGET, argumentos e escrita de
RETURNING. Os modos BY REFERENCE/BY CONTENT/BY VALUE foram observados no probe.
`exceptionFlow` é uma lista achatada de statements: não conserva, nessa forma,
partições distintas ON/NOT ON/OVERFLOW. O parse tree ainda tem contextos, mas a
forma semântica já perdeu essa distinção antes do JSON SP.
[F02–F03](../work/evidence/WORK-CFG-032/source-catalog.md#f02),
[F08](../work/evidence/WORK-CFG-032/source-catalog.md#f08).

`Meta`/`AstBuilder.meta` conservam SourceSpan com posições/offsets, ParseTreeOrigin
com nó/regra e `sourceMap.provenance` com localização original, expandida e cadeia
de inclusão. O SP publica `StatementHeader.id/programPoint/containment/provenance`
e provenance do operando, portanto há site estável no produto publicado. Os IDs
são identidades no escopo da publicação/unidade, não promessa de estabilidade
entre edições arbitrárias do source.
[F03–F04](../work/evidence/WORK-CFG-032/source-catalog.md#f03)

| Pergunta SP | Campo/fato no baseline |
| --- | --- |
| CALL dinâmico conservado? | CallFact(header,syntax,operand,runtimeTarget,runtimeUncertaintyCode) |
| Identidade do target? | DataReference.operandId + NominalBinding.selected: DataItemId, candidates e status/reason; não somente WS-PGM como texto |
| Endereçamento completo? | **Não**: projector usa construtor de DataReference sem wholeItemAccess; até WS-PGM simples sai com esse campo null |
| Literal versus variável? | AST distingue. SP aceita apenas CallSyntax.IDENTIFIER_OR_EXPRESSION; literal vira ObservedStatement(CALL_LITERAL_TARGET), sem PROGA |
| USING? | AST conserva; SP não tem lista de argumentos em CallFact; CALL_ARGUMENTS_NOT_PROJECTED |
| RETURNING? | AST conserva; SP CALL_RETURNING_NOT_PROJECTED |
| Exceções? | AST parcialmente achatado; SP CALL_EXCEPTION_FLOW_NOT_PROJECTED; children observados não substituem outcomes tipados |
| Continuação? | CALL não publica normalContinuation tipada; readiness textual de fallthrough não é contrato de successor |
| Valores runtime? | RuntimeTargetKnowledge.UNKNOWN e lacuna; nominal binding não resolve conteúdo de WS-PGM |
| JSON? | SemanticProductJsonWriter.CallDocument conserva os campos disponíveis, inclusive binding/provenance; não recupera os já perdidos |

Esses fatos estão em `CobolSemanticProduct`, `callCapability`, projeção CALL,
`addUnprojectedCallSurfaceGaps`, `callReadiness` e `SemanticProductJsonWriter`.
[F04–F06](../work/evidence/WORK-CFG-032/source-catalog.md#f04)

No probe exato X(8), o CALL tem `statement:1`, target `operand:1:0`, binding
selecionado `data:0`, origem original linha 8, colunas 11–21; target colunas 16–21.
São posições reportadas, sem redefinir a convenção de fim de span. O MOVE anterior
é `statement:0`, linha 7, e publica continuação conhecida para statement:1. O CALL
não publica sua própria continuação ao GOBACK. `wholeItemAccess` do target é null.
O JSON é schema `cobol-semantic-product`, versão `1.2.0`.

### 3.1 O oracle X(8) não cabe na prova escalar congelada

`ScalarMoveSemantics` só publica `FULL_IDENTITY` quando o literal lógico tem a mesma
extensão do item scalar text admitido. PROGA tem 5 caracteres; WS-PGM tem 8.
O probe exato publica `Copy.UNAVAILABLE`/`MOVE_IDENTITY_NOT_PROVEN`; o controle
explicitamente diferente X(5) publica FULL_IDENTITY, mas ainda falha pelo CALL no
lower. Trocar o oracle principal para X(5) esconderia um gap real.
[F07](../work/evidence/WORK-CFG-032/source-catalog.md#f07)

O MOVE alfanumérico para recebedor maior preenche à direita com espaços. Isso
impede representar o conteúdo de X(8) como o literal de cinco caracteres sem uma
derivação explícita. A primeira slice deve publicar prova de fitting/padding e
derivá-la para um Assign cujo valor efetivo seja `PROGA   `, ou usar `fit_text`
neutro. Para literal conhecido, materializar o resultado ajustado no lower a partir
de fatos semânticos publicados evita expandir o avaliador de expressões de Assign
na primeira vertical. Não é permitido ao lower reinterpretar PIC ou source text.
[IBM Language Reference 6.4, alinhamento/MOVE](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf),
[N07](../work/evidence/WORK-CFG-032/source-catalog.md#n07).

Converter esse valor em reference name `PROGA` é outra obrigação: AIR não faz trim
ou uppercase implícitos. `ProgramNameCanonicalizer.dynamicExternal` existe no
frontend, mas é policy de lookup nominal, não fato publicado de interpretação do
target dinâmico. Copiar suas regras para o solver seria incorreto. A política
exata de padding/nome deve ser revisada com autoridade de dialeto explícita; este
discovery não certifica regras de loader nem assume que todos os dialetos removem
espaços da mesma maneira. [F09](../work/evidence/WORK-CFG-032/source-catalog.md#f09)

### 3.2 IF real

O SP conserva IF, filhos THEN/ELSE, referências da condição e shape RELATION, mas
não a relação/comparador/literal completos; registra CONDITION_SEMANTICS_NOT_AVAILABLE.
No probe multi-path, os MOVEs internos também não têm normalContinuation publicada.
`AstBuilder.normalContinuations/addSentenceContinuations` cobre MOVE nas listas de
sentenças de nível considerado, não resolve automaticamente o controle dos filhos.
Assim, o diamond já existente em testes AIR não prova que o COBOL IF atravessa o
lower. Essa expansão deve preservar successors sem inferi-los pela ordem JSON.
Uma Branch com predicado explicitamente desconhecido `known(bool)` pode conservar
os dois caminhos; só é válida com contrato/origem do produtor, não com booleano
fabricado nem recuperação textual no CFG.

## 4. cobol-lower

| Etapa | Estado | Evidência e perda |
| --- | --- | --- |
| SP JSON input CALL | EXISTS | Wire.CallDocument e Wire12.CallDocument tipados |
| Decoder | PARTIAL | SpJsonDecoder aceita/deserializa variante, mas não a torna lowerable |
| Materialize | PARTIAL | CallDocument vira OtherStatement(header,Variant.CALL); operand/syntax/runtime payload descartados |
| Modelo semântico | MISSING | SpInput.StatementFact só oferece GobackFact, MoveFact, OtherStatement; não há CallFact tipado |
| Admission/lower | MISSING | ScalarMoveAdmission só permite MOVE tipado raiz e GOBACK terminal; não ignora CALL silenciosamente |
| Emissão AIR | MISSING | ScalarSequenceAssembler gera uma Sequence de Assigns e Return; nenhum Invoke |
| USING/RETURNING/handlers futuros | OUT_OF_SLICE | Não elevar payload ausente a semântica de efeitos vazios |

[L01–L07](../work/evidence/WORK-CFG-032/source-catalog.md#l01).
`MoveHandler` e `SourceOrigins` já oferecem o padrão de ObjectId, operand IDs,
origens derivadas do statement e correlação com o SP. Origem original e expandida
são ligadas por regra `sp-provenance/original-expanded@1`; não se deve substituir
por linha inventada ou reparse. Esse mecanismo pode servir ao Invoke e às
derivações de fitting, preservando suas lacunas existentes.
[L08–L10](../work/evidence/WORK-CFG-032/source-catalog.md#l08)

Há ainda uma distinção na identidade do **caller**: `SpInput.UnitKey` conserva
`canonicalProgramName`; `CanonicalRevision` incorpora essa chave na identidade da
publicação. `ScalarMoveLowerer` cria UnitId(publication,"unit"), e
[Unit AIR](https://github.com/Gustavo2358/air-java/blob/3bafe3978f0f392e842038ad5628e85dfd91d00d/air-model/src/main/java/io/github/gustavo2358/air/model/Unit.java#L5)
não tem campo de nome de programa. `LoweringResult.EntryLink/StatementLink` conserva
UnitKey do source junto dos IDs AIR em memória, mas `CobolLower.run` exporta somente
Publication pelo AirFileOutput, sem essas correlações. Portanto UnitId identifica
o caller, mas a string CALLER não está automaticamente disponível no AIR JSON.
Recomenda-se caller UnitId obrigatório e nome de exibição opcional, obtido somente
de correlação explícita do produtor (receipt/sidecar ou composição em memória),
nunca de parsing de IDs, de origin lines ou de catálogo global. Se a primeira CLI
de dependency precisar exibir CALLER, esse transporte de correlação pertence à W1.
Não exige nova AIR. [L11–L14](../work/evidence/WORK-CFG-032/source-catalog.md#l11)

A mudança mínima futura atravessa wire → domain → admission → assembler/handler:
CallFact com target tipado, whole-item proof quando aplicável, origin, continuação,
restrições e fatos de assinatura/efeitos/outcomes. LiteralResource para literal;
ComputedResource de expressão text para variável. Não se preenche target por
`observedKind`, grafia, parsing de diagnósticos ou catálogo de programas.

## 5. AIR normativa versus implementação Java/JSON

| Aspecto | AIR 2.0 normativa | Java model | Validator | JSON implementado |
| --- | --- | --- | --- | --- |
| Invocation | invoke, terminador | Operations.Invoke implements Terminator | invocation() valida estrutura e I-56 | **Não** |
| Target literal | LiteralResource(category,namespace,name,NamePolicy,origin) | LiteralTarget | refs/policy/origin/signature | **Não** |
| Target variável | ComputedResource(name: Expression known(text)) | ComputedTarget + Read(ObjectPlace) | CALL_TARGET + tipo TEXT | **Não**; Read também fora do codec atual |
| Place/Object direto como target | Não é variante; usar read(place) em expressão | ObjectPlace(ObjectId), Read | fechamento/roles/tipos | ObjectPlace existe no Assign; expressão Read não |
| Argumentos | value(expr), reference(place), copy(expr), posição | Value/Reference/CopyArgument | roles/domínio/transmissão | **Não** para invoke |
| Resultados | Place[] escritos no retorno normal | Invoke.results | outcomes e assinatura | **Não** |
| Assinatura | entry ou external(Signature); inventários independentes com remainder | EntrySignature/ExternalSignature | checa identidades/cardinalidade/domínio | **Não** para invoke |
| Effects | limites de leitura/escrita/mustOverwrite, gerais/por outcome | ForeignEffects, EffectBound, OutcomeEffects, effectOperands | refs/effects/contrato | **Não** |
| Controle | normal, exceptional/anyException, halt, diverge, open | InvocationOutcomes | fechamento/duplicatas/labels | **Não** |
| Provenance | operação, operandos e fatos com origens/evidências | Header/Operand.Header, target.origin, signature.origin, ContractRef.evidence | refs e obrigações | infraestrutura parcial já existente; falta mapear subtree Invoke |
| Binding normativo | analysis-ir-json 1.0.0 DRAFT especifica invoke | não equivale ao modelo | não equivale ao codec | BindingReader conhece fields mas recusa operação; Writer só Return como terminador |

[N01–N07](../work/evidence/WORK-CFG-032/source-catalog.md#n01),
[J01–J12](../work/evidence/WORK-CFG-032/source-catalog.md#j01).

Não criar `CobolCall`, nova AIR ou capability COBOL para representar um core Invoke.
As capabilities existentes no Java não são um certificado de cobertura do codec;
não existe perfil CALL downstream implementado. Uma política especializada de nome
via ExtensionName exigiria manifesto/semântica negociados. A preferência é normalizar
com expressões neutras já normativas e manter a autoridade dessa tradução no
frontend/lower.

### 5.1 Duas recusas independentes no codec

O probe usa a fixture literal de `ContractSuite` do SHA congelado, com KnownContract,
e registra:

```text
AirValidator = STRUCTURALLY_VALID; SEMANTIC_OBLIGATION I-56 = 1
AirJson.encode = IMPLEMENTATION_LIMIT: Operation invoke not implemented
AirJson.validate isolado por reflection diagnóstica = INCOMPLETE_VALIDATION
CfgBuild = UNSUPPORTED_INPUT / UNSUPPORTED_TERMINATOR
```

`OperationChecks.invocation` emite I-56 incondicionalmente: validação estrutural não
prova verdade de efeitos/assinatura/outcomes externos. `ValidationResult.status`
permite STRUCTURALLY_VALID com essa obrigação. `AirJson.validate`, entretanto,
recusa qualquer SEMANTIC_OBLIGATION. Logo, completar reader/writer não basta.
KnownContract não é bypass. O probe por reflection apenas isolou a política
existente; não alterou codec, validator ou publicação. A compatibilidade entre
transporte e assessment precisa de review próprio, preservando diagnósticos e
sem permitir INVALID_IR ou traversal incompleto. [J07–J12](../work/evidence/WORK-CFG-032/source-catalog.md#j07)

## 6. CFG, ponto de observação e planejamento

`CoreCfgProjection` aceita Return/Jump/Branch/Halt como terminadores; Invoke é
recusado. Instructions são conservadas na Sequence original. `IndexBuilder` valida
o mesmo domínio, aridades/transições e offsets das operações. A expansão precisa
ser coerente entre CFG, snapshot/adapter se publicado, index e inventários de
gates; só liberar uma classe em um allowlist não basta.
[C01–C04](../work/evidence/WORK-CFG-032/source-catalog.md#c01)

Representação recomendada:

```text
Entry -> Sequence S0
           Assign(WS-PGM, fitted literal)
           Invoke(call, computed/read target)   [terminador, site K]
               normal -> Sequence S1 -> Return
               nonreturn outcomes -> saídas intraprocedurais explícitas
```

Não é necessário criar um nó para cada instruction. Invoke ocupa o final de S0 por
semântica AIR; os outcomes exigem representação genérica de retorno normal e saídas
não normais, conservando tags e controle aberto quando houver. Não rebatizar normal
de Invoke como Jump e perder o site/outcome. Não há edge caller CFG → callee CFG.
Open control/reentry não pode ser ignorado: a primeira admissão deve recusar formas
cujo efeito sobre reachability intraprocedural não esteja suportado.

`ProgramPoint.before(entry,operation)` já referencia qualquer operação indexada.
`BatchReplayer` usa offset de BEFORE como fronteira e reaplica somente o prefixo,
partindo do IN estabilizado. O probe com Assigns first/middle/last encontrou
`BEFORE middle = A`, `AFTER middle = B`, `BEFORE Return = C`, cada valor com seu
support; uma Sequence replayed, três operações replayed, nenhuma query unsupported.
Os testes existentes também cobrem instruções internas, AFTER terminator rejeitado
e OUTCOME_UNAVAILABLE. Portanto a infraestrutura do ponto é **G0**; não há API de
Invoke executável no baseline porque suas admissões anteriores falham.
[C05–C06](../work/evidence/WORK-CFG-032/source-catalog.md#c05),
[C21–C22](../work/evidence/WORK-CFG-032/source-catalog.md#c21)

No futuro a query deve carregar **o ID de Invoke**, não “fim da Sequence” escolhido
por conveniência. Nesse modelo ela coincide com BEFORE terminator porque Invoke
é terminador, mas o critério de seleção é o invocation site real.

W4 já oferece SiteInterest(kind,entry,filter,queries), ConsumerRegistration,
ObservationRequest, AnalysisKey e PlanningExecution. Um consumer pode selecionar
Operations.Invoke, filtrar action/category, mapear ComputedTarget/Read/ObjectPlace
para PointQuery(ObjectId, BEFORE K), declarar dependência do provider de valores e
compartilhar um run por key/Entry. A key inclui implementação, versão, perfil,
direção, política de precisão e opções. FactSink preserva atomicidade de publicação
por consumer. Nenhuma dessas classes precisa conhecer COBOL CALL.
[C11–C16](../work/evidence/WORK-CFG-032/source-catalog.md#c11)

`DefaultValuePlan` é um plano de writes Assign por Sequence, observado antes de seu
terminador. Não deve virar CALL planner. A composição CP6 precisa registrar o novo
consumer separadamente. W5 `AnalysisDataflow`/`ResultJson` está tipado para
ObservedValueFact: reutilizar W4 não significa que W5 já serialize dependencies.
Recomenda-se produto/output separado e estável, sem modificar incompativelmente
`analysis-dataflow-result-v1`.
[C17](../work/evidence/WORK-CFG-032/source-catalog.md#c17),
[C19](../work/evidence/WORK-CFG-032/source-catalog.md#c19)

SiteView.Presence é **STRUCTURAL**, não prova reachability. O caminho literal requer
um provider genérico de reachability por Entry, que pode caminhar o CFG sem resolver
valores. O SPI de provider não exige solver. Selecionar Invoke estruturalmente e
emitir edge executável diretamente produziria dependências de órfãos. No caminho
dinâmico ValueFact já fornece reachability. Providers separados evitam que a falha
de uma análise de valores irrelevante bloqueie o controle literal.
[C18](../work/evidence/WORK-CFG-032/source-catalog.md#c18)

## 7. Efeitos e soundness

### 7.1 Comportamento atual

O perfil `scalar-text-direct@1` aceita Assign(ObjectPlace,Literal Text) e operações
sem write modelado Nop/Return/Jump/Branch/Halt. Qualquer outra operação, incluindo
Invoke, HavocMay e HavocMust, é recusada por UNSUPPORTED_EFFECT_PROFILE. Não existe
interpretação dos limites AIR de effects nesse perfil. `transferOperation` só
produz no-op para operações previamente admitidas sem write. Assim, **o baseline
não ignora CALL silenciosamente**; acrescentar Invoke ao allowlist sem transfer
introduziria esse erro. [C07–C08](../work/evidence/WORK-CFG-032/source-catalog.md#c07)

### 7.2 BEFORE não elimina efeitos de chamadas anteriores

```text
MOVE PROGA TO WS-PGM
loop:
  CALL WS-PGM USING WS-PGM
  ... backedge para loop ...
```

Na primeira iteração o target pode ser PROGA; depois da chamada, escrita por
referência pode alterar WS-PGM. O backedge leva esse estado ao próximo BEFORE.
Havoc deve participar do fixpoint; fazê-lo somente na apresentação AFTER é tarde.
Mesmo sem USING, a ausência de argumentos não demonstra ausência de efeitos sobre
memória compartilhada ou controle. COPY/VALUE também não certificam pureza.
AIR já exige o maior escopo potencialmente visível quando não há limite conhecido.
O comportamento BY REFERENCE e a distinção de cópia são consistentes com a
[documentação IBM de passagem de dados](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=data-passing)
e com [N01](../work/evidence/WORK-CFG-032/source-catalog.md#n01).

### 7.3 Direção recomendada: D, usando limites B quando demonstrados

**Proibir USING na primeira slice reduz o escopo, mas não é a prova de soundness.**
Recomenda-se consumir os limites neutros de efeitos AIR em um perfil genérico
sucessor de scalar-text-direct@1. Unknown effects aplicam may-havoc a todas as
células modeladas potencialmente visíveis; se não for possível provar exclusão,
usar todas as células admitidas. Não limitar apenas aos argumentos conhecidos.
`Scopes.AllMemory`/`VisibleMemory` já tornam esse limite representável. Não é
necessário novo modelo de memória nem havoc global fora do contrato AIR.
[J06](../work/evidence/WORK-CFG-032/source-catalog.md#j06)

Uma implementação conservadora pode fazer join com estado desconhecido nas células
afetadas: mantém candidatos anteriores como possibilidades, preserva seus supports
e abre remainder. Must-overwrite, somente com prova, substitui por unknown e elimina
supports antigos como definições vigentes. `Candidates.withOpen/join` e o estado
unknown já representam isso; não se muda o lattice. Se efeitos por outcome forem
unidos no transferBlock, isso é conservador, embora menos preciso; distinguir efeitos
por edge pode vir depois com SPI genérico existente. Results normais não entram
na primeira slice. O target é avaliado BEFORE esses efeitos.
[C08–C10](../work/evidence/WORK-CFG-032/source-catalog.md#c08)

O perfil novo deve recusar scopes/aliases/controle que não saiba delimitar, conservar
source uncertainty e manter os gates do perfil antigo. A escolha de novo profile
ID/versão exige review; não se modifica @1 silenciosamente. **Solver change = NONE.**
Também não se adicionam funções como resolveCobolCall/programNames/callTargets ao
motor. O efeito é definido por memória e outcomes, não por nome do programa.

### 7.4 Condições de admissão da primeira vertical

Recomenda-se um programa pure COBOL, entrada primária, CALL síncrono que pode
retornar, sem USING, RETURNING, ON/NOT ON EXCEPTION/OVERFLOW, nested special lookup,
CICS/IMS/DB2/GRBE. Target literal alfanumérico ou item WS standalone scalar text
com prova de identidade e acesso inteiro; sem REDEFINES/RENAMES/subscripts/refmod,
EXTERNAL/GLOBAL/pointers e construções não admitidas pelo perfil de memória.

O frontend deve provar sucessor normal local e ausência, nessa slice, de mecanismos
que tornem necessário controle aberto/reentry intraprocedural não modelado. Callee
desconhecido conserva possibilidades não excluídas: retorno normal, término,
divergência e propagação excepcional conforme contrato do dialeto. Sem ON EXCEPTION
não se pode inventar “sempre retorna”. Se a autoridade do produtor não sustentar
esse envelope, a entrada é UNSUPPORTED com diagnóstico, não um CFG otimista.

A soundness é condicional à admissão explícita, fitting/name interpretation
corretos e efeitos conservadores aplicados antes da propagação. Não está certificada
por execução de um CP6 inexistente. O primeiro fixture linear pode fechar model
remainder no BEFORE mesmo que haja efeitos desconhecidos **depois**; loops e chamadas
anteriores precisam de oráculos adversariais de abertura na mesma wave.

## 8. Consumer e contrato de dependency fact propostos

### 8.1 Responsabilidade

Nome conceitual: `CallTargetResolver`, em consumer de aplicação externo aos kernels,
com portas para fatos genéricos. Sua regra é “Invoke com action call e category
program referencia possíveis nomes”; não decide controle, memória ou execução de
outro programa. Preferir composition/module externo de dependências em analysis-cfg,
dependendo de W4 + analysis-values + AIR, com serialização em adapter externo.
Nome de módulo/classes ainda não é contrato aprovado.

O consumer recebe site/Entry/operand e:

1. LiteralTarget: conserva o nome/policy/origin diretamente e consulta reachability
   genérica. Não exige PossibleValues apenas para converter um literal conhecido.
2. ComputedTarget com Read(ObjectPlace): solicita PossibleValues(ObjectId, BEFORE K).
3. Expressão neutra de interpretação, como TrimRight(Read(...)): um componente de
   avaliação/projeção de expressão mapeia fatos genéricos, preservando supports por
   resultado. PossibleValuesProvider hoje aceita ObjectId, não Expression arbitrária.
   Essa extensão pertence ao interpreter/consumer genérico, nunca ao solver.
4. Nome/policy não interpretável: publica target aberto/unsupported, sem fabricar
   programa. InternalTarget, quando admitido futuramente, conserva EntryId; não
   refaz lookup por nome.

O caminho preferido de normalização é frontend/lower explicitar a interpretação
com operações neutras (por exemplo trim_right com origem e regra de dialeto), mantendo
ExactName no resultado interpretado. Uma policy externa versionada é alternativa
de review; não se admite trim invisível. O fato deve conservar raw text e a derivação
até reference name, inclusive quando vários textos convergem para o mesmo nome.

### 8.2 Alternativas de saída

| Alternativa | Vantagem | Risco/obrigação |
| --- | --- | --- |
| A: um fato por site com candidates + remainder | Preserva unknown-only, unreachable, remainders e contexto; natural para evidence packs | Graph/CSV precisa derivar linhas/edges dos candidatos |
| B: um fato por edge caller → candidate, com site uncertainty separado | Conveniente para graph/Neptune/visualização e CSV | Sem registro de site, perde chamada sem candidato e repete/omite incerteza; agregação pode destruir supports |

**Recomendar A como produto primário**, B como projeção com referência obrigatória
ao mesmo site-level uncertainty. Não implementar persistência/Neptune neste CP6.
Fact IDs precisam ser determinísticos por publicação/revisão, caller/Entry/site e
versão do consumer; não prometer estabilidade cross-revision baseada em linhas.

Contrato mínimo conceitual, sem definir sintaxe wire de produção:

```text
DependencySiteFact
  schema/consumer/profile versions; publication/revision
  caller: UnitId (+ optional source PROGRAM-ID display with producer correlation)
  entry: EntryId
  site: OperationId, Sequence/offset, operation origin
  target: literal | computed; operand/expression IDs; ObjectId when applicable
  observation: BEFORE(site) for computed; direct-literal for literal
  reachability: REACHABLE | UNREACHABLE_IN_MODEL
  status: RESOLVED_CANDIDATES | OPEN_TARGET | UNSUPPORTED | UNREACHABLE
  candidates[]:
    referenceName, category, namespace, namePolicy
    derivations[]:
      rawTextValue
      supports[]: evidenceId, origin, premises
      interpretation rule/version/origin
  modelValueRemainder: boolean | unavailable
  sourceUnknownRemainder: boolean (+ reason/evidence refs)
  effectiveUnknownRemainder: derived OR when value assessment available
  interpretation remainder/status (independent, if needed)
  site/target origins; premises; retained gaps/obligations
```

Não reutilizar “remainder=false” para query recusada. Ausência de avaliação exige
status e valor indisponível; nunca vazio fechado. Para ValueFact alcançável,
effective = model OR source, conforme a regra W3. Se houver incerteza adicional
de interpretação, preservá-la separadamente e abrir a avaliação de dependency;
não falsificar os três campos originais para esconder a causa.

Para literal, model candidate é diretamente conhecido; model remainder false é
condicional a policy interpretada, e source remainder deriva da evidência fonte/
controle. Não copiar o conjunto global de supports dinâmicos. O support é o target
literal no CALL, com origem do statement/operando, não um Assign inventado.

### 8.3 Provenance específica por candidato

W3 `ValueFact.CandidateSupport(candidate,producers)` e `Support(evidence,origin,
premises)` já preservam a associação. O consumer deve mapear cada candidato com
seus próprios producers. Para o diamond:

```text
PROGA -> derivação(raw='PROGA   ', supports=[Assign/Move A])
PROGB -> derivação(raw='PROGB   ', supports=[Assign/Move B])
```

Não converter para `{PROGA,PROGB}, supports={A,B}` indistinto. Se a interpretação
produzir colisão de nomes, manter derivations separadas dentro do candidato
convergente; se houver várias definições do mesmo valor, conservar todas. Origem
do CALL, do target, do MOVE, do literal e de cada derivação são papéis distintos.
[C10](../work/evidence/WORK-CFG-032/source-catalog.md#c10),
[C20](../work/evidence/WORK-CFG-032/source-catalog.md#c20)

### 8.4 Unknown, unreachable e limites do fato

Unknown source sem candidato conhecido produz candidates vazio + model open,
nunca programa `UNKNOWN`. Join PROGA + unknown produz PROGA como possível e
remainder aberto; não como target exato. Support de PROGA não é support de todo o
restante. O W3 já conserva source remainder independente: coverage e claims de
controle/storage/effects/values podem abrir a resposta mesmo com modelo fechado.
O cálculo atual de sourceOpen é amplo por unidade; o E2E congelado já admite a
combinação model=false/source=true/effective=true. Não prometer source=false para
CP6 antes de evidência do produtor. [C07–C10](../work/evidence/WORK-CFG-032/source-catalog.md#c07)

Para UNREACHABLE_IN_MODEL, recomendar fato diagnóstico explícito com site/origins
e sem candidatos executáveis; a projeção de grafo **não emite edge**. Isso distingue
“CALL observado mas órfão no modelo” de “CALL não extraído”. Source partial não vira
prova de unreachable no runtime. Modos normativos STRUCTURAL/OBSERVED/MAY_EXECUTE/
ENUMERATED_TARGET são dimensões de evidência, não escala de confiança.
[N04](../work/evidence/WORK-CFG-032/source-catalog.md#n04)

Literal e dinâmico convergem no mesmo tipo de fato e no mesmo edge derivado
`CALLER → PROGA`, com provenance diferente. Não exigir que o source de PROGA esteja
no corpus. O fato inicial diz: **esta invocação no source tem X como referência de
target estático possível**. Não diz que o runtime obrigatoriamente carrega um binário
X. STATIC/DYNAMIC linkage, STEPLIB/JOBLIB, aliases, LE/runtime search path, nested
lookup e inventário de deployment permanecem fora. Reference discovery é separado
de reference resolution against corpus. Não há confidence score.

## 9. Futuro sem motor centrado em CALL

W4 permite consumidores diferentes selecionarem sites e propriedades: target,
argumento #1/#N, arquivo, tabela SQL ou propriedade arbitrária. As análises pedidas
continuam genéricas. CICS LINK PROGRAM, IMS, DB2 EXEC SQL CALL e GRBE podem convergir
em Invoke/operandos/efeitos neutros quando seus frontends/lowerings publicarem esses
fatos. Isso é extensibilidade do desenho, não declaração de suporte atual. Capturas
REFERENCE/COPY/VALUE, memória indireta e interprocedural exigirão perfis/intérpretes
próprios, sem colocar dispatch COBOL no solver.

## 10. Decisões para review e parada

| Decisão | Alternativas e recomendação de discovery |
| --- | --- |
| Transporte + I-56 | Manter API atual estrita e acrescentar caminho explícito de encode/decode com assessment preservado; ou revisar política padrão de transporte estrutural. Recomendar caminho explícito compatível, com consumidor obrigado a tratar obligations. Não remover I-56 nem aceitar INVALID_IR/RESOURCE_LIMIT. Até review, gap bloqueia file E2E |
| PIC X(8)/nome | Publicar fitting semântico do MOVE e interpretação de nome via operações neutras; alternativa policy extension versionada com manifesto. Recomendar primeira opção, com regra/dialeto comprovados. Não usar X(5) como substituto e não importar canonicalizer nominal silenciosamente |
| Effects/outcomes | Perfil genérico conservador com unknown may-havoc sobre escopo visível/modelado, outcomes locais/não locais explícitos e recusa de open control não suportado; alternativa proibir toda situação sem pureza provada. Recomendar conservador, porque no USING não prova pureza |
| Resultado | Novo produto de dependência por site, projeções por edge; evitar mudança incompatível em result-v1 de W5 |
| Invoke instruction | Rejeitar proposta para essa slice: conflita com AIR congelada. Usar terminador normativo |

As três primeiras são fronteiras de review semântico e de compatibilidade; a
implementação não deve começar antes de escolhas explícitas. Se exigir nova versão
normativa, memória/lattice novo, solver interprocedural ou wire incompatível, abrir
novo discovery autorizado em vez de ampliar esta slice.

CP5 permanece APPROVED / MERGED / CLOSED. PRE-CP6 BASELINE permanece FROZEN /
VERIFIED. Os findings anteriores resolvidos não são reabertos. O snapshot histórico
`cp5-lifecycle.json` conserva CP6 NOT_STARTED/NOT_AUTHORIZED exigido pelo harness;
o estado **do discovery** está no work item ativo e não autoriza implementação.
Encerramento deste trabalho: documentos/evidências, gates e PR **DRAFT**, aguardando
review humano. Sem merge/auto-merge e sem início automático da primeira wave.
