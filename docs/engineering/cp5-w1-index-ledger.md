# W1 — índice estrutural: contrato e ledger

Escopo: successful core CfgBuildResult, AIR 2.0.0 canônica, policy explícita e
seleção de Entries. Sem solver, state, valores, effects ou reachability.
[Implementação](../../analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/IndexBuilder.java),
[manifest de provas](../evals/cp5/gate-plan.json),
[evidência](../work/evidence/WORK-CFG-028/wave-1/validation.md).

## Admissão e identidade

O resultado mantém as instâncias Publication/Unit/Entry/Sequence/Halt/Operation e
Object/Storage originais. Membership usa `==` para payloads e IDs completos para
resolver referências. Não usa equals/hashCode profundos da AIR/CFG nem nomes de
apresentação. `CfgNodeId.ordinal` externo nunca indexa array. O par privado
(Entry ordinal, Node ordinal) é uma chave long sem alocação ou texto no hot path.

A sessão correlaciona metadata/policy, verifica o profile de controle core, indexa
os owners e exige exatamente cada papel canônico de nó. Para cada aresta verifica
endpoints/contexto/destino/kind e unicidade do papel (Entry, source, kind).
Se A(u)=Σ aridade do terminador em cada Sequence de u (Branch=2; outros=1),
então Eesperado=Σ Entries(u)×(1+A(u)). Toda aresta é um membro válido e único desse
universo; igualdade de cardinalidade prova completude sem gerar outro grafo ou
iterar Entries×Sequences para verificar um CFG truncado. Órfãs contam igualmente.
A multiplicação é checada. Completude do inventário não fecha CONTROL da fonte.

Aritmética de ordinais/contadores usa Math.*Exact. Hash mixing é deliberadamente
modular; não gera IDs nem contagens. Crescimento/array/overflow/OOM falham como
execução/implementação, fora de Admission. Não há catch OOM ou status de capacidade.
VALIDATION_LIMIT/INCOMPLETE_VALIDATION herdado do BuildCfg não satisfaz a precondição
para análise: IllegalStateException, sem converter em INVALID_INPUT/UNSUPPORTED.
A dívida upstream continua explícita; não se reexecuta nem desabilita AirValidator.

## Dimensões e passes

U=Units; K=Entries; S=Sequences; O=instructions+terminadores; D=Objects;
C=Storage (Cells e Regions); V=nós CFG; E=transições CFG já expandidas por Entry;
A=ocorrências de Operand; R=referências resolvidas nesta faceta (abaixo);
T=capabilities requeridas; J=condições iniciais; B=trabalho de hashing/comparação de
IDs completos na borda. Kind count Q≤9 no profile atual.

| Coleção / fase | Passes e visitas observáveis | Estruturas alocadas / retidas | Lifetime / lookup |
| --- | --- | --- | --- |
| Units, declarations | U, com D Objects | mapa UnitId→Unit e ObjectId→Object; payload não copiado | sessão; hash expected O(1), custo B na borda |
| Storage | C + owners presentes | StorageId→Storage; contador Cell estrutural | sessão; expected O(1) |
| Units, referências | U + containing/visible refs + D | mapa ObjectId→Cell somente CellBinding; sem estado por Entry | sessão; expected O(1) |
| Units, payload | U + S + O + K + J | temporários Label→Sequence e OperandId set; Entry→Entry/ordinal; um Site por Operation, mapa ID→Site e buckets | membership temporário solto; sites/buckets na sessão |
| Operands | cada ocorrência A e ObjectPlace R uma vez | deque temporário por raiz; mapa OperandId→Object só ObjectPlace | deque não retido; lookup futuro sem scan |
| CFG nodes | V uma vez | Node[V], um handle/node; ID→Node; mapas Sequence/Entry/Exit | payload original; ordinais privados; lookup e reverso O(1) após resolução |
| CFG transitions | E uma vez + E no encadeamento reverso | edge[E] de refs, 5 int[E] (from/to/context/nextF/nextB); 2 diretórios primitivos de heads | sessão; próximo edge O(1) |
| Papéis de aresta | E junto da validação | diretório primitivo temporário de máscaras por (Entry,node) | liberado após construir; sem segundo CFG |
| Freeze de buckets | Q entradas de mapa, nenhuma cópia dos sites | Q wrappers de listas não modificáveis | ownership transferido, builder não escapa |
| Seleção | Kselecionado pedidos; duplicatas compartilham view | uma ContextView por Entry selecionada, mapa ordenado | sem Node/Cell/Operation cópias por contexto |
| Métricas | número fixo de coleções instrumentadas | snapshot dos contadores e mapa de labels | observação apenas; não entra na admissão |

Mapas Java retêm entradas/tabelas e boxing de ordinais Entry; estes custos são
lineares nas suas cardinalidades e não são payload AIR copiado. ArrayList buckets
crescem amortizadamente e retêm capacidade excedente, sem copiar operações. Os
arrays Node[V] e edge[E] duplicam referências ao inventário para tradução ordinal,
não os objetos. LongIntDirectory mantém duas arrays por diretório; fator de carga
até 1/2, crescimento ×2 e rehash amortizado. Arrays antigas são scratch coletável.
O hash tem colisões tratadas por probing; tests confrontam 10 mil chaves esparsas.

A contagem structuralVisits cobre os acessos explícitos a elementos nas fases
listadas. Não é contagem de instruções CPU, leituras internas de HashMap, rehash ou
alocações da JVM. Operands.roots/children são traversal não semântico do runtime
AIR pinado e criam listas temporárias: O(O+A) refs transitórias, registradas aqui,
sem retenção no índice. Não se usa Sequence.operations() para materializar payload.
A medição de retenção abaixo complementa os contadores de trabalho.

Construção: expected/amortized O(U+K+S+O+D+C+A+R+T+J+V+E+B), incluindo as tabelas
hash e sua manutenção. Não se promete pior caso linear contra colisões patológicas
de hashing. Não se inclui custo anterior de BuildCfg/AirValidator (que expande
Entries e ordena coleções). Memória adicional retida O(U+K+S+O+D+C+R+V+E), mais
Kselecionado; scratch O(A+S+E) por membership/traversal/role directories. Nenhuma
Entries×Nodes×Locations eager matrix ou cache global.

## Faceta de referências e limites

W1 resolve ObjectPlace (inclusive aninhado e condições iniciais), CellBinding,
Storage owner, Unit containing/visibleObjects e initialLabel. R mede exatamente
essas ocorrências resolvidas; arestas CFG ficam contadas em edgesIndexed/visitas
próprias. IDs/owners de todas operações e operands percorridos são conferidos.
Todos Objects e Storage têm lookup completo e preservam binding original. Não se
interpreta Alias/Alternatives/View/UnknownBinding, scopes, premises ou effects.
Essas entidades continuam no snapshot original; isto não é claim de reference
validation integral AIR nem de admissão semântica de valores. W3 decide localização,
visibilidade/aliases/disjunção do seu profile. Cells compartilhadas conservam mesma
instância; locationsIndexed conta Cells estruturais, sem matriz ou estado por Entry.

## Consultas e determinismo

Node/operation/object ID lookup é uma operação de borda. Reutilizar handles Node e
Site no hot path evita hashing textual repetido. Site guarda Sequence, Unit e offset;
operation() devolve a instruction ou terminator original em O(1). Nenhum wrapper por
consulta de OperationId; mesma chave devolve mesmo Site. Buckets são listas
imutáveis por classe AIR, ordem de Units/Sequences/operations no snapshot.

Adjacência: obter cursor usa dois ordinais e diretório primitivo esperado O(1);
cada advance lê somente a próxima aresta contextual em O(1). Cursores são alocados
por consulta, sem wrappers por edge. edgesVisited instrumenta leituras reais do
cursor, separado dos contadores imutáveis de construção. A mutação de scan global
conta cada edge examinado e conserva os resultados, mas falha no oracle de custo.

A ordem de edges/nodes é a do CFG recebido; sites preservam a ordem AIR declarada.
Reordenar coleções não altera controle; não se promete ordenar publicamente por ID.
Mesma entrada produz mesmos mapeamentos/contagens/ordens declaradas. Nenhuma ordem
observável deriva de HashMap, identityHashCode ou endereço de memória.

## Retenção medida

RetentionWalk (test-only) percorre as estruturas realmente alcançáveis da sessão,
deduplica por identidade e subtrai o grafo de objetos original AIR+CFG. Mede objetos
lógicos adicionais por classe, arrays reais e suas quantidades de slots: V handles
Node, O handles Site, Kselecionado ContextViews, zero payloads AIR/CFG novos.
Contagens de mapas/listas excluem seus nós/tabelas internos da JVM; **não são bytes
medidos nem estimativa de heap total**. Strings extras são labels fixos das métricas,
não duplicatas de IDs por linha. Não há root histórico/state/values pool nesta Wave.

A sessão possui índices e views; cursores/sites conservados pelo chamador mantêm
seus owners/payloads vivos. Soltar a sessão e esses handles permite coletar índices.
Não há static cache, String.intern, allocator global ou thread de background.
Elapsed nanos é medido apenas ao redor de open, sem limite semântico. O harness
registra contagens e ambiente, e não transforma tempo/heap em gate de admissão.
