# Escala, terminação e determinismo

O cenário futuro inclui publicações derivadas de programas grandes. Construir o
core sem varrer todas as sequences a cada target e sem enumerar todos os caminhos.
Índices por identidade completa e passes simples precedem otimizações especulativas.

Para projeção direta, acompanhar fatos lidos, nós criados e transições emitidas;
a meta é O(input + output), não limite de milissegundos sem ambiente controlado.
Um dispatch com K cases contribui K alternativas. Fronteiras simbólicas e estado
contextual têm custos próprios. Não prometer custo linear para matching recursivo.

Testes de performance devem exercitar séries crescentes de chains, diamonds,
cycles, dispatch e units, incluindo casos com muitos destinos. Contadores internos
podem complementar medição e inspeção de algoritmo; não são prova isolada de big-O.
Benchmark de tempo/memória serve como evidência com ambiente declarado, não oracle
funcional frágil. Não cortar candidates, ocorrências ou pilhas silenciosamente.

Ordens de construção, hashing e IDs derivados devem ser determinísticos para mesma
publicação/opções. Não prometer persistência de ID depois de editar a IR. Navegação
não deve ordenar a coleção inteira a cada query. Builds equivalentes não levam
clock, UUID aleatório ou paths absolutos de máquina no produto semântico.

Recursão Java proporcional ao número de sequences é risco em input grande. Preferir
traversal iterativo onde aplicável; não confundir pilha de implementação com pilha
semântica de local frames. [CORE-SIZE-001](../architecture/decisions/ADR-0014.md)
proíbe orçamento de recursos como término semântico. Convergência é obrigação
matemática; exaustão de processo/infra não produz grafo ou resultado parcial válido.

BACKLOG-CFG-012 estabelece baseline no core estrutural; o controle local exige
novo relatório de custo em seu discovery. Sem limite de hardware inventado neste
harness e sem promessa de throughput antes de medição.

## BDD scratch rollback work law (pre-code)

A managed scratch scope records only cache slots written with a transient node
operand/result. Rollback visits this bounded set, not the complete computed table;
an allocation-free scope visits zero slots. A later overwrite by a surviving
computation must remain cached. Reused node IDs cannot observe a transient memo.
The unmanaged discardAfter seam preserves its full-table fallback. Collection
invalidates all memos independently. Scratch journal space is bounded by table
slots and scoped to the same per-execution BDD owner. Independent truth tables,
small-table collision/reuse laws and the allocation-free/sparse-work oracle are
mandatory; no condition, caller, root or query result is approximated.

## Source proof closure and empty observation laws (pre-code)

Unknown-control proof closure is exact OR reachability from every
CONTROL_POSSIBILITY proof through declared proof dependencies. Reverse edges and
a queue visit each proof/edge a bounded number of times, including cycles,
permuted inventories and colliding IDs; without a seed there is no reverse index.
All source proofs/certificates remain published and validated. A nominal provider
with no requested query retains no private statement/control/state indexes and
performs no control closure. An independent scalar repeated-scan oracle defines
proof closure, including disconnected cycles and a long reverse-ordered chain;
work counters govern only algorithm cost, not acceptance of any fact.

## Bounded small-snapshot representation law (pre-code)

All tuples are still built in exact primitive columns and canonical dictionaries.
When a unit snapshot has at most 4096 total node/derivation rows, expand those
rows once into immutable lists and release the column arrays; repeated consumers
then reuse immutable records. Above that fixed representation budget, keep only
columns/dictionaries, with no expanded row cache. This never changes cardinality,
admission, support/proof/context alternatives, order or wire. Expansion uses the
canonical dictionaries, so repeated large support/proof payloads are shared, not
copied per row. Extra row-object overhead is bounded per unit; intrinsic payload
size is unchanged. Boundary, collision, ownership and full-row equality laws plus
large constrained-heap qualification are mandatory. This is a physical storage
choice, not a fixture-specific fast path or an analysis cutoff.

## Shared admitted AIR composition laws (pre-code)

The full file pipeline may export CFG and dependencies in one worker process.
Read and strictly validate AIR exactly once and bind the entire source certificate
to that admitted byte digest before exporting. Both builders receive the same
immutable checked publication, but retain their existing distinct projection
policies; never substitute the partial dependency graph for the conservative CFG.
Release the export graph before starting dependency analysis. No graph/session or
model survives the invocation. Each existing writer retains atomic destination
replacement and failure cleanup; an earlier completed CFG is a complete product
if later dependency delivery fails, as in the separate-process pipeline.
Inputs and destinations must be distinct, including existing symlink aliases.
The new launcher is an outer composition root only: cfg-adapters is permitted
there, never in analysis-adapters, dataflow, kernel or values. Legacy CLIs remain
available. Qualification compares both entire byte products against independent
separate-reader routes, checks one read, invalid/incomplete AIR, source digest
mismatch, output failures and repeated invocations. Performance compares actual
three-process production composition against the frozen four-process pipeline
with all five full products; previous four-process regressions remain recorded.
