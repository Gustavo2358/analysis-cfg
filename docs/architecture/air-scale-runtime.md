# Shared analysis runtime for large AIR

Status: implementation in progress, WORK-AIR-SCALE. This replaces the representations
that multiply frames by bodies, unrelated guard variables, entry topologies, historical
inputs, subjects, source inventories and materialized output. It covers the complete
AIR admission → dependencies route, not a single solver allocation site.

## Semantic obligations

The authority is AIR `4f09e8b1b496bf8de2e0fb62532e7aa0b97b9c6e`, especially local
control §7 and open control §6. The supported consumer profile remains explicit.
An operation inventory is an arbitrary graph; sequence order and source-language
procedure boundaries do not define control. Local calls share Unit memory.

The concrete configuration is (Unit activation, operation, local stack, abstract
domain input). Stack symbols retain invoke identity, return, ports, routes and guard
key. Ordinary edges preserve the stack. Invoke pushes; guarded invoke first tests
the pending stack for the same Unit-scoped key. Resume and matching boundary remove
exactly the top; selected routes never search lower frames or fall back to ordinary
return. Nonmatching boundary preserves the stack. Exact unwind either removes k or
produces underflow; unwind-all resets only the local stack and preserves memory.
Unit exit discards its local frames. Unknown control affects every destination
admitted by its declared scope, including interior points.

The symbolic representation denotes a set of these configurations. Each derived
transition must be justified by a concrete AIR action; every admitted concrete action
must remain represented. Structural reachability and domain evaluation are distinct.
For backward analysis, preimage is intersected with forward-reachable configurations.
An inverse pop cannot invent a caller or stack incompatible with the selected Entry.

Guard predicates are regular languages over stack symbols: key present and key absent.
Only predicates observed by a body and its reachable callees enter its specialization.
Removing a predicate requires proof that transfers/control in the region cannot
observe it. Continuation formalization retains return event and top observations;
binding a caller supplies its actual continuation. Equality of body labels alone is
not sufficient to share contextual results.

This is a correctness obligation for the new saturation rules, not a claim that an
unmodified PDS algorithm already handles all AIR extensions. The rule inventory,
independent explicit-stack oracle and counterexamples precede kernel integration.

## Domains and summary replacement

`AnalysisDefinition` requires monotone transfers and lawful joins, not distributivity.
Fact-wise delta execution is allowed only for domains/operations with a demonstrated
distributive finite-fact algebra. Other domains use compositional transformers where
lawful, or versioned relational equations. Joining correlated roots before replay can
invent combinations and is forbidden. For example, (A,1) and (B,2) must not become
the Cartesian choices A1/A2/B1/B2 at an observation that preserves correlation.

A summary key includes program equation/body, domain/profile, direction, observable
Entry/context, dependency-closed demand and canonical input when required. Its hash
must be congruent with semantic equivalence and collisions compare exact content.
The legacy arbitrary `equivalent` callback alone cannot supply such a hash or an
external codec. Managed providers adopt the stronger contract explicitly.

Bindings reference current versions and still-required contributions. A growing
input can replace a historical contribution only when monotonicity and context prove
subsumption; another caller, pending work or observation may still need that version.
Roots include demands, current bindings, pending work, SCC dependencies and active
observations. Marking reaches cycles; cache entries do not keep obsolete graphs alive.
Necessary incomparable inputs remain separate, even when that increases cost.

## Representation and ownership

One typed AIR snapshot and one indexed program belong to the session. Nodes/actions
use segmented primitive tables and indexed relations; Entry is a view, continuation
is a binding. Shared open scopes are relations, not mandatory origin×destination
edge arrays. Group propagation requires equivalent edge semantics; an export that
requests every edge pays for that output through a cursor.

Preparation, storage/effects/partition, source indexes and provider decisions are
shared by compatible demands. Source closure follows reverse dependency indexes and
a fair worklist, independent of assignment inventory order. Candidate-specific
support and premises remain attached to the correct value and authority. Persistent
states and proof sets share unchanged structure. Relational operations act directly
on factorized DAGs; enumeration occurs only when the requested result requires it.

The session owns budgets, stable handles, page stores, queues, roots, memoization and
leases. Required facts/unique indexes are not evictable caches. Reserve capacity and
scratch before allocation, including rehash peaks. Cold facts, indexes, pending work
and output runs can spill exactly. No cardinality-dependent directory may silently
remain unbounded in the heap. Adapter I/O stays outside core ports.

The public in-memory APIs remain explicit materialization routes. The managed CLI
uses paged AIR access, managed execution and a result cursor through encoding. One
large caller-owned Publication cannot be described as bounded input residency.

## Delivery and qualification

Equality is semantic: candidates, supports, premises, origins, outcomes, remainder,
coverage and admission/failure behavior. Work counters and serialization layout may
change. Repeated equivalent new executions must remain deterministic. Resource failure
is operational; it cannot produce COMPLETE, remove known dependencies or manufacture
semantic unknown/PARTIAL. Output is published atomically after successful completion.

AS-W00 contracts/oracles; AS-W01 runtime/resource ownership; AS-W02 official AIR access;
AS-W03 shared program; AS-W04 contextual control; AS-W05 equations/live summaries;
AS-W06 domains/proofs/factorization; AS-W07 planning/source indexes; AS-W08 observation
and output; AS-W09 complete external storage; AS-W10 qualification/legacy removal.

Acceptance includes all existing adversarial families and new semantic counterexamples.
Known finite OOM/timeout cases must complete with equivalent results in Java 21/G1,
`-Xms32m -Xmx1024m -Xss1m`, with an external 480-second stop. Completion below that
threshold alone does not suffice: counters/curves must demonstrate removal of the
avoidable products. Forced spill must preserve semantics and controlled residency.
No universal linear bound is asserted for conditional stacks or relational output.

Current baseline: CFG `fd010dc53d83b90e9d54b6dc4eef84976fb52067`, tree-equivalent to
the investigated `fb61e25d674dce236f9951c87fc498a58b18d82c`; consumed AIR sources
`c2f80b59b7c38fa6efb9a20f0fa79644c697d8a4`. New AIR commits require immutable repins.

## Foundations and limits

- [Carayol/Hague, saturation survey](https://arxiv.org/pdf/1405.5593): regular
  stack representations and saturation; AIR extensions require explicit rules/proofs.
- [Reps/Horwitz/Sagiv](https://research.cs.wisc.edu/wpis/papers/popl95.pdf): finite
  distributive fact analyses; not a universal replacement for relational domains.
- [Conditional PDS reachability](https://www.sciopen.com/article/10.1007/s11390-020-0541-z):
  regular stack tests can change complexity; simple-PDS bounds do not automatically apply.
- [Bryant](https://www.cs.cmu.edu/~bryant/pubdir/ieeetc86.pdf): canonical diagrams
  have worst-case growth; variable ordering alone does not establish bounded memory.

Implementation remains incomplete until the managed CLI, semantic oracles, structural
counters, forced-spill cases and required repository gates all pass. Checkpoints record
partial progress and preserve failed evidence.

## Managed page foundations

The [domain law inventory](domain-law-inventory.md) records which algebraic
assumptions need proof before domain admission and version retirement. Its scalar
CONCAT witness rejects blind replacement of old known outputs by latest-input
outputs. Storage correctness alone does not establish those transfer laws.

`AnalysisResources` reserves coarse capacities before allocation and distinguishes
resident/scratch heap, direct buffers, temporary disk, descriptors, work and output.
Staged capacity can transfer to a same-pool owner without recharging or briefly
releasing bytes. Cross-ledger, cross-pool, self and closed transfers are rejected.
`PageStore` is an exact core port. The adapter `FilePageStore` uses positional
FileChannel I/O, a fixed primitive cache directory, and checksums for payloads,
identities and the disk free list. Cache deletion closes probe clusters instead of
accumulating tombstones. A released physical slot is reusable; its new handle has
a new generation, so an old root cannot silently become another fact. The format
allows 2^32-1 physical slots and 2^31 generations per slot; exhausted generations
are retired rather than wrapped. Offset overflow is rejected before allocation.
There is no crash recovery or cross-session reopening of this temporary format.

`PagedLongArray` provides sparse 64-bit primitive indexing through a page-backed
radix directory. Its resident control state is fixed; directory height follows
the highest written index, bounded by 63 levels. Zero/unwritten ranges allocate
no pages. Nonzero counts release empty pages and directory paths as soon as cells
are cleared, and collapse unnecessary all-low prefixes. Teardown has a fixed
stack and releases only the array's own pages.
The shared store must still close after an operational failure interrupts cleanup.

`PagedWorklist` owns a FIFO of primitive points and a paged pending bitmap. Arrival
deduplication does not discard new abstract-state contributions: its consumer must
join those contributions before scheduling. Removal clears membership before
returning the point, so self-loops can enqueue it again. Consumed queue pages and
zero bitmap pages are released; scheduling failure aborts further use.

`PagedLongIndex` is an ordered B-tree for required canonical state, with binary
search within nodes, split/rotation/merge, physical page retirement on deletion
and a fixed teardown stack. It avoids a resident HashMap or rehash peak. Positive
values are canonical handles; zero means absent. Natural signed-long order is
the default. Custom order must compare complete immutable keys behind stable
handles; hash equality alone is insufficient. It must be transitive, deterministic
and bound to the owning arena/profile. The index owns no external key payload:
callers retain/release those keys with their graph roots. Comparator failure aborts
the index instead of certifying partial lookup or stable state.

Its ordered cursor leases a fixed traversal stack under the scratch quota, visits
records without materializing them, and checks the owning index version. Mutation
attempts invalidate active cursors; owner close releases all cursor reservations.
Closing or exhausting a cursor detaches its owner and traversal buffers.

`MemoryPageStore` is an explicit resident backend for the same port. It retains
reusable payload capacity under heap quota and reserves the old-plus-new metadata
growth peak together with the next payload before allocation. It claims no bounded
working set beyond that resident quota. The same primitive execution transcript
and independent ordered-map oracle run on it and the forced-eviction file backend.
Neither backend can revive an obsolete page generation.

`CanonicalTupleArena` stores immutable fixed-shape primitive keys and their declared
references in paged rows. Its ordered unique table compares every field, including
reference identities. Separate arenas bind different schemas/profiles. Root tokens
belong to individual callers and are never reused; releasing one token does not
release another caller's retention. An explicit iterative trace visits all current
roots and referenced records before a paged retirement sweep. The sweep enumerates
current records instead of issued history, and releases empty row/index pages.
The fixed staging key never retains a caller buffer. Constructor/collection failures
release reservations or abort access; the shared store remains the final cleanup
owner. No individual record/root adds a heap object.

These immutable references form a DAG (children exist before parents). This arena
does not collect mutable summary cycles, infer domain subsumption, or authorize
retirement of scalar versions whose laws remain unproved. Active observations,
pending work and bindings must explicitly retain their storage roots. A handle's
identity includes the owning arena; numeric handles are not global model IDs.

These primitives are not yet wired into production analysis. Passing their tests
does not establish bounded residency for AIR decoding, summaries, proofs, queues,
unique indexes or serialization. Those integration obligations remain open.

The [typed AIR producer repin](../sources/air-scale-access-repin.md) fixes the official
access/builder at `3bb2d55e7912ff34ee5185a2be51ab71a1a20b56`. `PagedAirStorage` bridges
its four primitive columns to this runtime without implementing a second codec.
The shared ledger funds all column directories and appender capacities. The large
typed-payload tests exceed managed heap quota through exact file pages, but their
input starts as a test-owned Publication. Official incremental decode and complete
paged Validator migration still precede production CLI integration.


## Indexed source demand and native-file admission

The resident source API now builds one directed `NominalDemandIndex` per supplied
nominal facts set instead of repeatedly scanning all assignments/conditions until
stability. Assignment receiver nodes point to their expression DAG; READ nodes
point to symbols. Condition expressions occupy a separate bidirectional incidence
layer: demanding any participating symbol demands all reads of the condition.
Separating the layers prevents shared assignment/condition ASTs from demanding
receivers that only read a symbol. A condition with K reads stores O(K) incidence,
not K² pairs. Iterative construction memoizes each AST identity once per layer.

A primitive fair traversal enqueues each reached vertex once and examines each
outgoing stored edge once. Cost is O(indexed input + demanded graph), independent
of assignment inventory order. Candidate/support/outcome evaluation is unchanged;
this graph computes demand only, never proves a candidate or discharges a premise.
`DemandStatistics` exposes actual reached node/edge visits and stored edges.
The small independent set-fixpoint oracle, reversed 131,072-edge chain, condition
incidence and shared-DAG/directedness countercases exercise the structural claim.

Native-file structural admission builds exact location → node-ID sets once, then
compares every supplied qualification set against the matching group. Namespace,
owner, references, complete alternatives and availability checks are preserved.
The node inventory is not rescanned once per file. A counted-list test falsifies
the previous product, and independent missing/wrong/dangling qualification cases
retain their rule-specific rejection.

These source changes are used by production callers of the resident APIs. They
are not the complete source rewrite: state/proof persistence, preparation lifetime,
full source correlation and managed paging remain open. Resident String/ordinal
indexes and primitive int arrays have explicit resident ownership; they do not gain
a spill guarantee by using the paged runtime elsewhere.
