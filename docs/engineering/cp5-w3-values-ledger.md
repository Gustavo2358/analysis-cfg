# W3 — PossibleValues, estado e queries

Profile `scalar-text-direct@1`; autorização humana em
[wave-3/authorization.json](../work/evidence/WORK-CFG-028/wave-3/authorization.json).
W1/W2 Java permanecem byte-exact. `analysis-values` implementa AnalysisDefinition;
`analysis-kernel/query` acrescenta replay genérico, sem alterar solver/SPI/structure.
A dependência direta adicional cfg-kernel é necessária pelas assinaturas existentes
AnalysisDefinition.transferEdge(CfgTransition) e AnalysisPoint.node().source().
Não há aresta inversa, novo solver, cache, planner, consumer, resolver ou writer.

## Domínio e precondições

IR_GUARANTEED: TextValue contém Unicode escalar válido, Expression é pura,
ObjectPlace usa ObjectId completo e CellBinding aponta uma Cell inteira.
EXPLICIT_CONTRACT: snapshot/CFG admitido por W1, profile inteiro admitido por W3,
premissas de disjunção fornecidas pelo produtor, join/transfer monotônicos W2.
ARCHITECTURE_GUARANTEED: transfer comum no solver e replay; resultado W2 só STABLE.

Subject é ObjectId completo. Estado usa ordinal privado da Cell: dois Objects na
mesma Cell compartilham valor. Bases distintas exigem uma DisjointStorage que cubra
todas as Cells admitidas; pares fragmentados não são combinados por transitividade.
Verificação percorre membros em O(D+P), com tabelas hash expected O(1).
Todo Object do snapshot deve ser known(text)/CellBinding com Cell known(text).
AliasBinding, views, regions e demais associações são recusados; isso limita formas,
nunca número de Objects. Identidades/owners são resolvidos pelo índice W1.

Admite Assign(ObjectPlace, Literal(TextValue)), Nop, Jump, Branch, Return e Halt.
Expressões de controle/retorno são puras e não alteram memória; Branch preserva os
dois outcomes CFG sem filtrar predicado. Outros writes/expressões de Assign são
UNSUPPORTED_EFFECT_PROFILE, antes de executar solver. Nenhum unknown write vira Nop.
Entry seeds literais textuais são strong updates uma única vez na boundary; demais
initial values permanecem unknown. InitialCondition original continua na sessão,
conservando causas Preserve/ExternalUnknown/Uninitialized/ParameterInitial; valores
contraditórios da mesma Cell são recusados, combinações ambíguas fora do profile.

Estado: bottom de ponto separado de reached + bindings explícitos. Ausência reached
é Candidates({},true). Cada binding tem Candidates(S,open); join une S e OR do open.
Bottom é identidade; caminho reached sem binding abre o resultado do outro caminho.
Assign substitui o binding, inclusive depois de open; não une candidatos mortos.
Nunca há Candidates({},false) publicado para ponto reached.

U(P) contém literais de Assign e seeds textuais do programa finito admitido.
S está em P(U(P)); open tem false ≤ true. Produto por Cells finitas e bottom separado
tem altura finita, com equivalência que preserva evidência enumerada mesmo sob open.
Strong updates constantes são monotônicos; W2 agenda justa converge. Nenhum contador,
resource budget, cardinalidade ou timeout garante terminação ou abre remainder.

## Representações e alternativas

Bindings usam AVL imutável com cópia apenas do caminho atualizado e rotações locais.
Cada nó guarda key ordinal, Candidates, filhos, height e size. A invariância de
balanceamento limita lookup/update ao pior caso O(log(B+1)), sem cadeia de deltas.
Subárvores não alteradas conservam identidade. Não há referência ao root anterior.
Persistência mantém versões acessíveis sem mutar raízes expostas; ver
[Okasaki, discussão de persistência](https://www.cs.cmu.edu/~rwh/students/okasaki.pdf).
O argumento de altura/rotações segue a invariância AVL apresentada nas
[notas de Cornell](https://www.cs.cornell.edu/courses/cs2112/2021fa/lectures/lecture.html?id=avl).
A escolha é local e substituível; Patricia/HAMT não são obrigações do gate.
Full HashMap copy e listas de deltas foram rejeitadas pelas propriedades H4.

Pool por preparação: TextKey calcula hash sobre escalares Unicode e compara texto
exato, sem trim/case/normalização ou String.intern. Reusa o TextValue original e um
singleton por valor; não duplica chars. Ordinais não escapam nas observações.
Singleton guarda um int, sem array; sets maiores são arrays int ordenadas imutáveis.
Union usa merge linear O(Ca+Cb) e scratch desse tamanho; preserva todos os candidatos.
É deliberadamente simples: fan-in com conjuntos crescentes pode alocar O(N²) elementos.
Não há interning de todos os sets históricos. Otimização futura depende de medição,
sem reduzir precisão. A ordenação textual ocorre só na materialização externa.

## Ledger de ownership e lifetime

P=pontos contextuais W2; E=edges contextuais; B=bindings explícitos por root;
C=candidatos do fact; I=operações AIR; D=Objects/Cells inventariados;
U=valores distintos; T=escalares textuais; Qraw/Q=requests brutos/únicos;
R=operações efetivamente reexecutadas na união de prefixos/sufixos.

| Estrutura/fase | Criação e cardinalidade | Sharing, owner, liberação |
| --- | --- | --- |
| subjects | O(D) uma vez, ObjectId → Location ordinal/Cell | preparation; Cell AIR original, shared entre aliases; solta com definition/execution |
| cells temporário / premise members | O(D+P) membership, sem pares | scratch de admission, liberado antes solve |
| visible / sourceOpen | um set/diretório por Unit | preparation; lookup de query sem scan de Objects/visibilidade |
| admitted / writes | O(I) identidades originais de Operation, uma Write por Assign | preparation; não reindexa OperationId; compartilhado solver/replay |
| ValueUniverse | U TextValues originais, U singletons, U entries | preparation/execution; sem static/global/historical set pool |
| TextKey transitório | uma chave/hash por literal/seed | T escalares processados uma vez por ocorrência na preparation; sem texto no hot transfer |
| boundaries | uma sparse root por Entry selecionada, somente seeds explícitos | configuration; unknown não materializa D bindings |
| PossibleValuesState | root + reachability; uma por atualização semântica | immutable; raiz anterior não retida pelo estado novo |
| PersistentBindings.Node | O(log B) novos por write; B por root lógico | filhos/subárvores compartilhados; factory conta cada alocação real |
| Candidates | singleton int ou array ordenada + open | pool somente singleton; unions não guardam versões antigas |
| DataflowResult | 2P referências IN/OUT e diretórios W2 | raízes atuais; sem estado por instruction/edge/iteração; owner execution |
| join/equivalent | percorrem B explícitos, lookup O(log B), union por C | scratch: visitor/accumulator fixos e arrays de union; não duplicam todo estado por Assign |
| query set/list | Qraw dedup → Q ordenadas | temporários do batch; identidade point+subject completa |
| replay groups | Q selections agrupadas por ContextView/Node opacos | sem hash profundo da Sequence; offsets resolvidos em W1; uma passagem por grupo |
| replay state | um root corrente por grupo | prefixo/sufixo avança sem conservar histórico; facts selecionados apenas |
| ObservationBatch | Q Observation/ValueFact + listas de candidatos/evidence | sem referência à sessão, execution, state ou universe; pode sobreviver ao run |

Solve mantém o custo W2 de publicações + transfers + joins. Um join custa
O((Ba+Bb) log(Ba+Bb+1) + Σ unions(Ca,Cb)); equivalente compara B keys/sets.
Pointer equality evita trabalho quando roots/sets já são os mesmos. Compare de
sets pode ler seus elementos; work counters de binding e union não são instruções CPU.
Arrays scratch de union são contabilizadas na estimativa de alocação.

Batch: O(Qraw + Q log Q + R log(B+1) + Σ Qlookup log(B+1) + Σ materialized C log C).
Lookup/hash de IDs completos ocorre na borda; custos de comprimento de IDs e texto
não são ocultados por O(1). R é a união de prefixos forward ou sufixos backward;
Q requests não implicam Q replays. Custos de materializar listas de C por fact são
honestos; não se guarda Q×B estados. Sem alegação universal O(V+E).

## Pontos e completion

Point inclui EntryId, kind ENTRY/BEFORE/AFTER/OUTCOME, OperationId e outcome quando
pertinente. OutcomeKey é o tipo AIR fixado (inclusive ExceptionOutcome/tag), sem enum paralelo.
A identificação contextual é própria da query: Entry obrigatório e OUTCOME solicitado
não acrescentam semântica a Control.ProgramPoint. AFTER instruction usa NormalOutcome; AFTER qualquer terminator é recusado.
OUTCOME conserva a identidade solicitada, mas não é materializado neste profile.
BEFORE Return é admitido. ENTRY usa IN da Entry, após aplicação da boundary W2.
Queries inválidas para contexto/subject são UNSUPPORTED_POINT com reason enumerado;
nenhum campo VALUE é preenchido. Query estruturalmente órfã válida é VALUE com
UNREACHABLE_IN_MODEL e sem candidates/model remainder.

Forward: âncora IN, ordem crescente. Backward: OUT, ordem decrescente, incluindo
terminator no sufixo. BEFORE/AFTER conservam a posição no programa. Replay genérico
recebe direction/transfer da mesma definição usada no solve (contrato explícito);
DataflowResult W2 não é alterado. Correlaciona context handles com result antes do
batch: resultado de outra sessão nunca é convertido em unreachable.

Materialização só recebe DataflowResult STABLE. Dedup por point/subject e ordenação
completa determinística. Um UNSUPPORTED_POINT não aborta lote. ObservationException
é a única falha controlada capturada: batch FAILED/OBSERVATION_ERROR vazio, métricas
de trabalho descartado preservadas e solver STABLE intacto. Outros erros de execução
propagam; não há captura de OOM/resource exhaustion como resultado semântico.
F2/F3/wire permanecem byte-exact. W3 não cria PreparedAnalysisResult/DeliveryReceipt.

Fonte e modelo: coverage PARTIAL/UNAVAILABLE ou claims pertinentes abertos mantêm
sourceUnknownRemainder. Strong Assign pode ser fechado no modelo simultaneamente.
Effective = model OR source. Não há whitelist por texto de gap. Object precision é
considerada na projeção; sourceOpen de Unit é conservador para o snapshot inteiro.
Facts não são causality/RD/path witnesses ou pares correlacionados. Evidence refs
identificam query, Entry, Object e Cell; premissa de disjunção acompanha o fact.
Literal no pool isolado nunca é usado para concluir alcançabilidade.

## Métricas e prova externa

ValuesWork tem counters checked por preparation/solve/replay separados. Aliases:
stateAllocations = roots + persistentNodes; stateRootsRetained vem do walk do result;
bytesHashed reporta `unicodeScalarsHashed` (escalares, não bytes UTF-8);
candidateCardinality é high-water, não policy. Query metrics e quality conservam
requests/unique/answered/unsupported/not-materialized e remainders com denominadores.

RetentionAudit percorre referências realmente alcançáveis com dedup por identidade,
parando nos payloads AIR/CFG e sessão W1. Conta roots, nodes, sets, arrays/slots e
observations; não mede backing internals de Map/List ou bytes físicos totais.
`stateBytesAllocatedEstimate`: hipótese explícita 12-byte headers, 4-byte refs/int,
alinhamento 8, compressed oops/class pointers; root 24, AVL Node 40, Candidates 24,
array int align8(16+4n). Só vale sob esses flags; nunca chamado heap observado.
JFR/GC/histogram e release são verificados externamente à transferência.
Gates exigem medições reais, oracles nominais e mutantes; não inferem PASS de Markdown.

Observação externa inicial: em 10k/20k/200k writes sobre uma Cell e dois valores,
GC.class_histogram mediu 3 PossibleValuesState/72 shallow bytes e 1 Node/40 bytes
na fase solved. Batch-only preservou somente os dois singletons globais de estado
(bottom/reached), o singleton UNKNOWN e um ValueFact; nenhum Node, execution ou
ValueUniverse. Soltar o batch removeu o ValueFact. Singletons sem payload de programa
e lambdas sem captura não são vazamento de roots. Esses são shallow bytes físicos
por classe, não tamanho retido exclusivo nem soma total do heap.
A preparação é O(I): 200k Write precomputadas ocupam 4.800.000 shallow bytes;
essas Write desaparecem junto com execution/profile. Não se esconde esse custo
sob a retenção pequena dos estados. JFR registra amostras de alocação e GC; não
se interpreta amostragem como censo de todas as alocações.

S4b escalonado, com scheduler W2 injetado pelo seam de teste existente: N=1k/2k/4k,
B=8/16/32, N contribuições reais no join e N+1 transfers; joinEntriesVisited foi
31.968/127.936/511.872. predecessorContributionReads permaneceu zero. O crescimento
é coerente com visitar bindings explícitos a cada chegada; não se atribui ao solver
um custo constante de domínio. A coleção crescente de candidatos ainda pode exigir
trabalho quadrático de union. Essa limitação de custo permanece aberta para futura
otimização equivalente, sem constituir cap, degradação de precisão ou promessa O(V+E).
