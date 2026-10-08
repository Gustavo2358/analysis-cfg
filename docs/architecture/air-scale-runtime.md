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

The kernel `ResidentPageStore` is an explicit resident backend for the same port;
`MemoryPageStore` delegates to it without a second implementation. It retains
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

Source state/support execution now uses these primitives through production callers.
Passing storage tests alone does not establish bounded residency for AIR decoding,
activation summaries, scalar/regional states, source dictionaries or serialization.
Those integration obligations remain open.

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
of assignment inventory order. This graph computes demand only, never proves a candidate or discharges a premise.
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
are not the complete source rewrite: shared preparation lifetime, full source
correlation, input/dictionary paging and managed output remain open. Resident String/ordinal
indexes and primitive int arrays have explicit resident ownership; they do not gain
a spill guarantee by using the paged runtime elsewhere.


## Primitive persistent state/proof maps

`PersistentLongMap` represents immutable versions in a compressed binary trie over
complete signed 64-bit keys. A write creates the changed leaf and copies only its
search path, at most 64 branches; unchanged subtrees are shared. Deletion collapses
the now-redundant parent. Canonical arena keys make equal contents independent of
insertion order. Present zero differs from absence. Primitive values and referenced
value/proof roots have distinct kinds; references are traced by arena collection.

The shared six-field arena schema reserves references at columns 2,4,5; the view
checks that schema before claiming its own capacity. It may share that arena with
other domain record kinds. Roots are snapshot/arena-local handles, never external
AIR/source IDs. The view does not infer equivalence between domains or retire any
solver version. Callers retain all active state/proof roots explicitly.

The view claims 4,096 resident bytes before allocating fixed path/staging/cache
buffers. A bounded 32-slot exact `(root,key)` cache memoizes lookup without rooting
old versions; each hit first checks root liveness. A same-value write returns the
original root without rebuilding its path. Ordered cursors claim 2,048 scratch
bytes (256 when empty), retain their root, walk each tree node once and release
both leases at exhaustion/close. View close closes active cursors but does not close
the borrowed arena/store. Stale handles cannot revive collected facts.

Map path work is independent of full state size; canonical interning still pays
the arena's full-tuple ordered-index cost. This is no universal O(1) solver claim.
Source now uses this primitive for states, candidate maps and proof sets. Scalar and
regional domain migrations remain open. Every integration must preserve joins, weak
writes, model assumptions, outcomes, candidates and proof fields before qualification.
Tests compare old/live versions against independent TreeMaps across resident and
forced-spill stores, signed extremes, zero, reference rooting, quotas, cursor lifetime
and many single-write versions with bounded shared-record growth.


Pointwise map join aligns trie prefixes and skips equal immutable subtrees. It
preserves unmatched entries and delegates collisions to the supplied associative,
commutative, idempotent value join; those laws remain the domain owner's obligation.
A 1,024-key version with one changed value invokes the value join once. Primitive
max-union tests challenge disjoint, overlapping, signed/random keys and canonical
commutativity/idempotence against an independent TreeMap oracle.

Each active join claims 4,096 scratch bytes before allocating its fixed 65-frame
iterative buffers. Reentrant joins receive separate scratch, so state → candidate
→ proof joins cannot corrupt an enclosing traversal. A fixed representative-key
cache avoids repeating trie prefix walks without retaining obsolete roots. Branch
leaf counts provide constant field access to cardinality. Referenced value join
and collection tests check nested maps, callback reentry and lease release. This
checkpoint establishes the primitive's behavior, not the unresolved scalar domain
join law or complete cross-domain migration.


## Persistent source states and complete proof sets

`SourceValuesProvider` stores each reachable before-state as a positive canonical
state record, referencing a primitive persistent map of symbol overrides. Unreachable
bottom is zero. Ordinary UNKNOWN and model UNKNOWN defaults are implicit, so large
demand inventories do not create a full initial state. First arrival at bottom copies
its incoming root; joins between reachable states align trie branches. An unmatched
override joins its missing counterpart's default by adding OPEN, preserving every
candidate/support and existing model/table flag. Model-symbol overrides always carry
MODEL: model declarations never seed a candidate, model writes are weak, and model
predicate reads bypass refinement. Independent dense-default random oracles challenge
this invariant, including disjoint states and model/table flags.

Candidate maps reference persistent support sets in the same arena. A declaration
or assignment is a typed proof atom, identified by complete kind/owner/detail fields;
delimited display text is not identity. An assignment adds its atom through a bounded
trie path rather than copying all prior proofs. State writes likewise share unchanged
branches. Simultaneous receivers evaluate against one immutable predecessor; ordinary
known writes retain strong kills, while model/table writes union with previous values.
ASCII upper, space-only trim, Unicode scalar fits, candidate refinement and all source
assumptions/uncertainties retain their published meaning.

Term/predicate evaluation uses iterative identity-memoized DAG traversal scoped to a
predecessor. Fixed exact transfer and state-join caches memoize complete keys, do not
retain roots and are cleared before collection. Before roots/retention tokens use
page columns; pending work uses the paged FIFO. Each state replacement releases only
its former slot token. Safepoints collect outside transient construction and occur
only after record growth exceeds twice the last live size (with a fixed initial
threshold), avoiding a full sweep on every assignment. No historical cache entry is
a GC root. The previous one-million-work semantic cutoff is removed: configured
resource exhaustion throws operational failure instead of returning PARTIAL facts.
`limited()` remains a compatibility accessor and is false after successful construction.

The existing resident API detaches all requested candidates once after solving and
closes its temporary arena/columns/worklist/store. The additive borrowed PageStore +
AnalysisResources constructor closes its own owners and leaves the shared store open.
The original constructor is explicitly resident and uses no implicit process-derived
memory quota; applications wanting managed capacity supply the ledger/store. Source
input handle indexes, typed proof/text dictionaries and detached List output remain
resident bridges, not a whole-route bounded-memory claim. Complete external input,
dictionaries and output cursors are still required by AS-W02/08/09.

Tests include full proof preservation across 1,024 assignments with bounded shared
record growth, shared-predecessor fanout with one transfer evaluation, separator-safe
proof identities, empty observation zero-page admission, controlled quota failure,
and exact resident/forced-spill equivalence under a 64 KiB managed working set. The
original source-state discovery family must also pass at its original heap envelope;
these tests alone do not qualify all dependencies paths or the corporate incident.


Source admission now traverses term/predicate DAG identities with iterative queues
and constructor-scoped memoization shared across all assignment/condition roots.
Reference, authority, CHOICE and duplicate checks remain separate obligations, with
the same rule messages; shared subtrees do not expand into their exponentially many
paths and deep unary input does not consume the Java call stack. These remain resident
source-model indexes; physical AIR/JSON validation is a separate open obligation.
Independent tests include 12,000-level terms/predicates, 30-level shared DAGs, hidden
invalid leaves and production candidate/branch evaluation.

Nominal source equality computes the same existential TRUE/FALSE image by indexing
classes modulo trailing U+0020 spaces. Space-padding a pair to the larger logical
scalar width is equal exactly when those classes match. TRUE requires an intersection;
FALSE is absent only when both candidate inventories contain the same single class.
Duplicate raw values/widths do not imply inequality. Tabs, leading spaces, non-space
whitespace and Unicode remain distinct. Open/empty operands preserve BOTH immediately.
This removes the candidate-left × candidate-right comparison and repeated fitted-text
materialization; each side is traversed once, with string processing proportional to
input payload plus indexed lookup cost. A counted collection falsifies the former
256 ×256 scan; independent scalar-padding/random Unicode oracles validate equivalence.
This is not a repair of the distinct unresolved scalar CONCAT join law.


## Shared source expression program and primitive truth summaries

Production source values carry a persistent normalized-class set and a raw-text
figurative truth mask alongside their complete candidate/support maps. These fields
are computed when candidates change, reused when proofs/flags change, joined by the
same persistent set primitives, and traced by arena roots. Equality checks canonical
class-root equality first and otherwise probes the smaller class set. Singleton
refinement therefore consults a shared index rather than rebuilding every unaffected
candidate inventory. Figurative equality consults the raw uniform-scalar image;
trimming equivalence classes cannot turn a non-uniform padded string into uniform text.
Physical sharing of equal primitive integer sets does not establish semantic identity
between proof/text namespaces; owning value fields and dictionaries bind interpretation.

`SourceExpressions` compiles the source term DAG once into primitive runtime rows and
argument vectors. ASCII upper and leading/trailing U+0020 trim commute, are idempotent,
and distribute over candidate/support/flag union. Their composition uses three bits,
so syntax identities specialize in at most eight transform contexts, not one per path.
CHOICE pushes these transforms into its children; canonical integer child vectors
share identical bodies and remove duplicate alternatives under proven idempotent union.
Literals/reads compare complete kind, transform mask and payload, never hash equality
or recursive AST record equality. This law is specific to the supported nominal unary
operations; it does not imply distributivity of scalar CONCAT or arbitrary domains.

Each predecessor evaluator uses a leased sparse primitive memo and iterative runtime
stack sized by touched program nodes, not the whole compiled program on every query.
A refinement evaluator borrows baseline transformed-read outputs only after exact
full input-value/root equality, including supports and flags. Changed leaves recompute;
unchanged alternatives remain shared even under unary-over-CHOICE expressions. Active
baseline memo state survives the complete refinement operation; it cannot be evicted
and rebuilt once per candidate. No memo roots remain after the operation's safepoint.
Program metadata currently uses explicitly resident syntax/String indexes under a
conservative capacity reservation. Paged input/program/dictionary migration remains
part of the global external-storage obligations.

A complete-refinement RED oracle (128 candidates) observed16,640/33,280 transformed
candidate visits after isolated equality optimization, proving the remaining product.
The shared program passes ordinary equality, unchanged transform and mixed-CHOICE
oracles with bounded class/transform visits and every original candidate/support.
Random Unicode composition/padding/figurative oracles and a three-producer merge
challenge the algebra independently; existing model/table/kill/filter tests and the
64 KiB forced-spill state transcript remain required.


### Input-owned source qualification and correlation inventory

`DependencyInput` now owns one immutable source certificate and one occurrence inventory.
`SourceQualifiedDependencyResult` prepares affected-control sets once per exact UnitEvidence
identity and constructs program/native-file facts in that pass. Factory admission no longer
interprets twice; public detached construction still compares every proposed occurrence against
a fresh interpretation. Native-file reads, result `partial()` and JSON delivery reuse immutable
facts. The certificate cannot be supplied to the input constructor. `DependencyResult` checks
publication identity when retaining a prepared certificate, including the new reuse overload.

Both former records are final immutable classes with the same public constructors, accessors,
value equality, component hash order and textual component presentation. Their computed fields
are not equality inputs and are never wire authority. Existing explicit JSON writers keep the
published shape. This changes Java record reflection metadata; no reflective product consumer
is part of this repository's contract. No public mutable index or global cache is introduced.

Correlation admission groups operation IDs by full typed source StatementId. Inventory building
visits those groups directly, deduplicates sites, preserves the established lexical operation
order and keeps uncorrelated executable occurrences. It removes occurrences × all-correlations
scans. Validation still checks duplicate/conflicting owners, exact labels, target kind/technology,
literals and source/AIR origin ancestry before preparation. Origin ancestry is prepared once
using the shared labeled graph closure described below; the retained input/certificate
indexes and detached outputs are resident bridges, not a claim of bounded whole-route memory.

Independent regressions cover all qualified-source fixture families, unknown-control native
candidates/remainders, repeated immutable reads, factory/public-constructor equality, one
certificate across execution, 512 correlated sites with reversed physical/link order, orphan
sites and foreign-certificate rejection. The old native implementation fails the reuse oracle
because each read constructs a new interpretation. These changes do not address scalar CONCAT,
contextual frame saturation or full paged AIR validation/transport.


### Shared ordinary flow and explicit Entry bindings

Core projection now builds each unit's ordinary body transitions once, together with one
entry-edge/normal-exit binding per active Entry. `CfgTransitionTable` is an immutable logical
List view owned by the exact Publication; only its package-private builder constructs tables.
Known return destinations bind to the selected Entry's normal exit. All other typed outcomes,
including equal-target true/false arms, retain their identities and order. Group prefix sums
support random access by binary search. The list-compatible polynomial hash factors common
body contributions rather than enumerating every contextual row. Equality with explicit
lists remains standard List equality; table-to-table equality compares immutable groups.

`CfgGraph` validates physical representative rows and every entry/normal-exit correlation.
Its explicit-list constructor still verifies every supplied transition. `IndexBuilder` admits
the complete logical cardinality but stores/links the physical rows. Specific entry heads and
shared body heads use distinct primitive keys. Context cursors combine them in established
order, bind return exits and activation identity, and never expose another unit/Entry's edges.
Explicit externally supplied dense graphs remain strictly admitted through the same path.
This is representation substitution, not skipped validation of dead AIR or a reachability claim.

`edgesIndexed` retains its logical coverage meaning; collection visit counters now report
actual physical indexing/link work. The independent arithmetic construction ledger follows
body rows + entry bindings. Retention auditing walks actual table fields rather than enumerating
virtual transient transitions, and an extra retained CFG transition still fails that audit.
The 256-sequence/128-entry witness changes retained/indexed rows from 32,896 to 384 while
preserving the full logical inventory. Dense/factored cursors, full List hash semantics, two
units, distinct roots, equal branch targets, malformed dense graphs and exact return exits
are directly compared. All cfg/analysis kernel tests pass at the focused checkpoint.

These resident arrays remove the Entry × ordinary-body storage/construction product. They do
not yet provide the paged ProgramStore, managed resource budget, factored open relations or
formal local-continuation summaries required to complete AS-W03/W04. Traversing/delivering the
full logical CFG still enumerates its requested contextual edges. Per-analysis Entry state
sharing and dependency output cursor integration remain later substitutions.

## Sparse formal ancestor conditions (AS-W04 checkpoint in progress)

Execution no longer calls `ActivationControl.possibleAncestors()` over all publication
frames and never builds a global absence conjunction. The root starts under TRUE;
its own guard tests are FALSE because its stack is empty. Child formulas retain
only encountered ancestor guards, with own-key tests TRUE. A matched return binds
the parent's active key TRUE; a root binding evaluates the all-false valuation by
following low branches, using the existing fixed computed table and creating no
Boolean nodes. Forward/backward feasibility and unique-boundary searches apply the
same closure. Caller subscriptions preserve matched return and unwind behavior.

The old global absence environments were sound pruning. Removing all pruning
passed the small oracles but regressed a public CardDemo input to480s. The correction
computes positive caller-key sets on reached structural shapes by iterative SCC
condensation and canonical persistent tries. Equal components share roots and
chain extensions share search paths. Only variables present in actual predicates
are cofactored; impossible guard moves become constant or disappear. No global
absence diagram or per-frame copied BitSet exists. Forward return delivery intersects
the structural conditional point before transfer; backward uses the same refined
shapes. The preparation-owned arena closes before domain evaluation. The independent sparse test
was RED:64 inaccessible keys retained2146nodes. Two terminals suffice after the
substitution. `possibleAncestors()` remains a diagnostic API; it is outside the
production execution route. This does not yet eliminate body×frame summaries or
CallerWitnesses whole-route residency, and managed BDD storage remains pending.

The Boolean operation follows exact restriction in
[Bryant1986, section4.4](https://www.cs.cmu.edu/~bryant/pubdir/ieeetc86.pdf).
The AIR-specific proof is by induction over matched caller bindings; universal
polynomial complexity is not claimed for arbitrary formulas or domains.

Sparse-condition checkpoint validation: complete CFG/analysis-kernel reactor passed
169 analysis-kernel tests, including the generated exact-stack oracle in both
directions and all infeasible-changing-input regressions. Repository FAST passed
with zero failures/errors/skips in145.234s before the support correction. All60
original dead-frame curves passed:6400dead keys about0.402s and32768dead frames with
32768body nodes about1.12s, both directions,4reachable points/3edges. The first
CardDemo check exposed the480s regression and is preserved. Corrected kernel passed
172methods;3public fixed input comparisons against729 then passed every semantic
field except the two execution-counter objects, with candidate1.168/1.769/1.605s.
The correction still requires FAST and committed curves. No global completion.

Corrected checkpoint gate: FAST845required methods, zero failures/errors/skips,
143.278s. The exact CI inventory now includes every Boolean condition law test,
the two sparse dead-frame tests and the two persistent caller-support tests.
Existing required methods and forbidden dependency rules remain intact.

### Shared requested-origin ancestry

`PersistentGraphClosure` generalizes the reached-caller support implementation. Iterative
SCC condensation propagates canonical persistent label sets along explicit typed edges.
Vertices may be unlabeled; equal sets retain one arena root. The graph arrays are borrowed
only during construction, while owned leases and pages are released at preparation close.

`OriginAncestryIndex` labels only origins requested by source correlations. Each origin and
its Derived inputs are indexed once; membership queries no longer repeat a graph walk for
every link. Full typed OriginIds distinguish publications. Missing inputs add no edge;
cycles are handled exactly by SCCs. Rule names and locations have no semantic role.
Duplicate/ownership/category/literal validation and uncorrelated occurrences remain intact.

The unchanged former BFS fails a counted regression with1,049,600 reads for1,024 links
sharing1,024 ancestors. The replacement passes that witness and independent BFS oracles
over50 cyclic graphs, missing inputs and foreign IDs. A512-site production admission fixture
adds a1,024-origin shared chain, reverses correlation order and rejects an unrelated origin.
The generalized control kernel passes all172 existing tests. This is a resident preparation
bridge: sparse label sharing removes repeated traversal but arbitrary distinct reachable
label relations may still grow. It does not claim complete managed-route memory or solve
formal active-frame summaries, scalar-domain laws or paged AIR admission/transport.

Repository FAST now selects848required methods including all three ancestry/admission
regressions and passes with zero skips in142.484s. Exact architecture inventories retain
their forbidden dependency checks. This gate qualifies this checkpoint, not AS-W00–W10.

### Structural Regional preparation

Regional values constructs `StoragePartition` directly from admitted `StatementEffects`.
It no longer constructs/discards `ReachingDefinitions` merely to read its partition.
The discarded constructor compiled every statement's definition plans, outcome maps,
control-open flags and Entry seeds; none of those roots was a Regional input. Partition
construction and Regional's own typed EntryFacts admission remain unchanged. Reaching
definitions still constructs the same structural partition for its own real analysis.
The source and compiled W3 boundary checks now reject a Regional dependency on the
discarded analysis. The new check was RED on the old preparation;63focused value methods,
including independent concrete/structural Regional oracles, pass after substitution.
Repository FAST848required methods and all boundaries pass in141.649s; the discarded
stage was preparation only, never a previously executed RD solve.

### Live summary versions (AS-W05 resident bridge)

Activation summaries are no longer rooted solely by having been indexed once.
`SummaryCollector` marks Entry roots, pending work and the current child/wait
relations at a safepoint after process/reconcile. Incoming/callers/waiters backlinks,
lookup indices and feasibility hints do not independently root a summary. Marking
is iterative, uses intrusive primitive tokens and a leased scratch queue, and
collects unrooted SCCs as well as isolated historical inputs. Retirement detaches
backlinks and cached indices; a future demand may evaluate the exact input again.
Backward return-call inventories are detached with their retired owner.

Published partitions belonging to live slots remain intact. This is reachability
collection, not a state-order substitution, widening or permission to discard an
old Scalar candidate/support. In particular it does not assume the unresolved
multi-read law. Pending work and waiter relations remain conservative strong roots;
this bridge does not yet prove a minimal live closure for arbitrary reactive graphs.
The Boolean structural pass keeps its inventory until model compilation.

Collection frequency follows work created since the preceding pass and the last
live slot/link count. Thus a growing live graph is not globally rescanned after
every new input. At convergence a final pass precedes result replay. Index filtering
uses cached fingerprints, checks exact live values and compacts capacity with a
staged reservation; quota rejection preserves the old lookup table. A failed mark
cannot authorize retirement, and every scratch lease closes on failure.

Tests cover rooted/unrooted SCCs against independent BFS, mark-token reuse, scratch
and staged-index rejection, old-input callers alongside version churn, nested shared
inputs/waiters, and collection during convergence against every IN/OUT state of an
independent explicit-stack oracle in both directions. The initial4096input lifetime
witness failed on the unchanged solver. The full184method kernel passed before the
additional convergence-oracle method; FAST now selects all eight added lifetime,
collector and index-filter methods. Large original inputs curves remain separate
qualification, not implied by these small tests.

The six new SolverMetrics fields record created/retired/final/peak versions,
collection passes and index probes. The existing22argument metrics constructor is
retained. The resident compatibility resource scope still uses an unlimited budget;
borrowed opaque domain objects and all solver graph metadata are not yet fully
paged/accounted. Managed end-to-end execution and formal continuation sharing remain
uncompleted AS-W04/W09 obligations.

Checkpoint gate: FAST864required methods and all source/compiled/transport boundaries
passed with zero failures/errors/skips in143.070s. This gate includes the additional
convergence oracle and preserves all prior required methods and forbidden edges.

### Caller support before guard saturation (AS-W04/W05 bridge)

A conservative typed caller graph is compiled before symbolic structural solving.
It includes actual invocation continuations and legal unwind/reset landing suffixes,
but creates no executable bypass. Body vertices and actual activation-key bindings
are separate. Shared SCC label sets prove only absence: a guard key outside the
body's caller support becomes FALSE before conjunction. Possible presence retains
its exact Boolean predicate. This avoids constructing impossible reentry predicates
through ordinary diamonds before the old post-saturation refinement can run.

Unconditional caller links also maintain a rooted proof forest. A TRUE feasibility
query can use that actual finite root path without searching every ancestor again.
Parallel links are counted. Removing a proof link detaches its subtree and regrows
it from rooted external predecessors; disconnected cycles cannot certify themselves.
Guarded paths remain individually tested, and TRUE never answers a nontrivial guard.
This follows the spanning-tree invariant in Swamy/Brayton/Singhal,
[Incremental Methods for FSM Traversal, §4.2](https://is.ifmo.ru/research/_incremental_methods_for_fsm_traversal.pdf).
Link changes and summary retirement repair certificates synchronously. Metadata is
still resident; this does not finish managed end-to-end execution or promise an
optimal bound for arbitrary repeated dynamic edge deletions.

The new callerPathEdgesRead counter includes shortest/exact caller searches and
proof-forest maintenance. peakBooleanNodes records peak interned conditions,
including transient construction. Older22/28/29argument metrics constructors stay
available. Independent dynamic BFS covers4000 graph mutations, parallel links and
unrooted cycles. Small nested/diamond cases compare every IN/OUT value against the
explicit-stack oracle in both directions. Unchanged geometric bounds cover32–512
nested frames and4–16 diamond levels. Full-kernel and original large-fixture/CLI
qualification are separate evidence; these regressions alone do not finish the
architecture plan.

Checkpoint gates:200 kernel methods pass; FAST881 required methods and all exact
source/compiled/transport boundaries pass with zero failures/errors/skips in217.415s.
The counted deep-prefix curve is32/64/128/256/512 forward proof-edge reads and
255/510/1022/2046/4094 backward, including forest maintenance. Large original
contextual fixtures and public CLI products are being measured separately.

### Exact-binding caller certificates (AS-W04/W05 bridge)

`CallerPathCertificates` supersedes the unconditional forest in solver execution.
Every guarded proof edge retains the actual invocation binding, pushed key and
predicate. A primary word and per-binding alternative words share primitive
persistent sets. An alternative certifies only its own parent word; keys from
unrelated alternatives are never unioned. Sparse key buckets nominate candidates
for a required-present key, and the entire query predicate is then tested on the
individual word. A missed hint retains the exact guarded caller search.

A second intrusive forest uses the SAME adjacency and every nonzero guard as an
unguarded scheduling superset. A raw-unreachable vertex has no caller word, so it
can answer FALSE without another ancestor search. A raw-reachable vertex cannot
answer a guarded query TRUE. Deleting a selected binding detaches the affected
subtree, then regrows from still-rooted external predecessors; cycles cannot root
themselves. Repair suppresses transient reach-change callbacks. Only final changes
notify watchers. A disconnected query watches its own region's version; a later
ancestor reconnection increments that version and schedules its subscribers even
when its direct incoming links remain unchanged. Root-connected guarded misses
retain their existing exact-search subscriptions.

Changing a nonzero predicate leaves raw topology intact. If the primary individual
word still satisfies its changed guard, descendants remain valid without repair.
An invalid primary proof repairs dependent words synchronously. Alternative binding
identity remains exact under parallel callers. Retirement detaches arcs, primitive
indices, root tokens and metadata leases. The backend is borrowed when supplied;
closing this owner leaves it usable. Old-plus-new growth is reserved before array
allocation. No resource interruption certifies a stable semantic result.

Soundness follows induction on the rooted individual-word forest, plus ordinary
root reachability over a superset for the negative decision. The unguarded repair
uses the same spanning-forest invariant cited above, rather than assuming its result
proves guarded reachability. There is no completeness claim for positive hints or
optimal bound for arbitrary dynamic deletions. BDD construction remains a separate
architectural debt; resident adjacency and detached result arrays remain bridges
until the managed program and observation route replaces them.

Focused tests include independently enumerated concrete words under add/remove/
guard-update operations, 4000 binding mutations against ordinary BFS, disconnected
cycles and reconnection callbacks, parallel actual keys, sparse deep key inventories,
a 512-descendant still-valid guard update, ten heap-quota interruptions and full
root/capacity cleanup. A one-page file backend forces eviction and preserves every
queried predicate and raw-support answer through repair and retirement. Small shared
recursive dispatch compares every IN/OUT state with the explicit-stack oracle;
geometric forward/backward sizes4–512 retain an unchanged64N total edge-read bound,
including certificate maintenance. These are structural regressions, not universal
linear-complexity or whole-CLI qualification claims.

SolverMetrics separates callerCertificateEdgesRead, callerValuationNodesVisited and
callerHintIndexProbes while callerPathEdgesRead continues to include all proof/search
edge reads. The published31argument constructor and older forms stay available.

Checkpoint validation: full kernel211 methods PASS with no failures/errors/skips.
FAST893 required methods and all exact architecture/transport boundaries PASS in
309.175s. Original frozen family curves and full public CLI comparison remain
separate pending checks; the global architecture work is still IN_PROGRESS.

### Literal predicate algebra and empty-root equations (AS-W04/W05, unqualified checkpoint)

Maximal AND/OR chains of signed literals use a canonical Patricia set over nonnegative
32-bit keys. The first literal's polarity normalizes every subtree; a root parity bit
complements all signs without copying payload. Branches preserve prefix separation,
size and positive count. Native union detects opposing literals, inclusion and signed
intersection skip identical subtrees, and pure absent-key restriction traverses only
the literal tree. Each insertion copies at most the key-width path. The representation
uses the six-column primitive canonical arena with reference columns2/4/5; page and
root-token ownership follows that arena's existing resident/file ports. Patricia prefix
separation follows the already cited Midtgaard treatment of canonical radix trees;
the signed normalization and Boolean fold rules are proved below, not imported as a
new complexity claim for arbitrary Boolean functions.

BooleanConditions normalizes every ordered decision that is itself a literal junction
into the same signed-set identity. Empty/singleton junctions use terminals/literal
nodes. Other ordered decisions keep Shannon semantics. NOT swaps AND/OR and flips
sign parity; native subset/intersection proves exact absorption, contradiction and
tautology before demanded Shannon cofactors unfold. Equivalent functions retain
canonical IDs under this maximal-junction normalization. Required-present hints on
AND and possible-present hints on either fold use sign counts/search; every nominated
caller word is still tested against the complete predicate. A hint miss preserves the
exact word decision. Indexed alternative words are tested before the primary word.

Soundness: OR of a signed set and AND of that same set are their literal folds.
Normalization factors only literal branches followed by the same fold and preserves
variable order. Duplicate opposite literals collapse to the fold's absorbing value.
For mixed folds, a shared signed literal makes AND imply OR; signed containment in a
complement proves contradiction/tautology. General decisions continue to use their
exact low/high cofactors. A Patricia subtree's parity flips every descendant sign,
and normalized first polarity makes its identity independent of insertion order.
No arbitrary-function linearity or bounded whole-CLI claim follows from these rules.

GuardedStates is the common pointwise state-function algebra in both solver directions.
Disjoint environments with equivalent states share one guard. A join class equal to
the incoming value is covered by the incoming guard, so its intermediate intersection
is unnecessary. Only a genuinely different joined value needs a split. Missing
environments remain uninitialized even when the supplied value is bottom. The covered
domain is cached and visited as a persistent condition root. This uses the declared
associative/commutative/idempotent upper-bound join and equivalence laws; it does not
assume distributive block/edge transfers.

Each pointwise composition opens an append-only condition allocation scope, keeps
its escaping state guards/domain, and retires unused new conditions. Earlier immutable
nodes cannot reference those append-only IDs. The dirty computed-table journal
invalidates retired operands/results before ID reuse. Group root tokens are released
on rollback, commit, collection and close. The literal backend is borrowed when
provided; closing the condition manager leaves it usable. Both solvers close their
condition managers after witnesses/path owners. Structural model construction transfers
ownership on success and closes it on failure. Legacy decision catalogs and operation
memo frontiers remain resident bridges; this is not complete managed-condition storage.

Forward ROOT discards the local word by delivering the original source value/edge
directly to the Entry's empty-root destination after exact guarded existence. POP and
positive-count UNWIND retain their matching rules. Backward ROOT reads the current
empty-root IN equation directly, interpreting its destination guards at the empty
word and preserving the source guard separately. Root-value changes schedule explicit
current readers. Readers detach/rebuild subscriptions on processing and retirement;
reverse links do not make dead summaries collection roots. Underflow reads its existing
invalid root destination, while valid unwind keeps exact ancestor arguments. Return
arguments remain caller-specific. Arbitrary state transfers keep their original edge
and Entry/node identity; no caller blocks are replayed or omitted by reset projection.

The independent literal-map, truth-table, cache/ID reuse, guarded-state table and
one-page spill/denial tests pass. Three real RED cases are preserved:128 accumulating
literal junctions16514 nodes;128 implication prefixes16514;128 initialized two-value
joins32644. Their original bounds now pass.8192 signed prefixes verify primitive
payload path sharing and rooted collection, rather than only compressed handle counts.
The full kernel executes227 tests:226 pass, with one newly added ROOT growth test
still RED at all12 backward combinations. Small reset IN/OUT, multiple Entry,
non-distributive edge-sensitive memory oracles pass. Forward reset sizes4..512 meet
the original16N bound; backward does not. This checkpoint is IN_PROGRESS/UNQUALIFIED;
no final semantic/performance qualification or completion is claimed.

### Exact paged condition catalog (AS-W01/W04/W09, unqualified checkpoint)

BooleanNodeStore replaces the full-cardinality resident decision catalog with paged
primitive rows and an exact complete-key index. Variable, low/high IDs, signed
literal root and fold kind determine identity; mark epoch, literal-retention token
and free link are metadata. A fixed leased primitive row cache never proves equality.
Group tokens retain the signed arena exactly once. Append-only scratch scopes cannot
reuse older holes; commit keeps escaping new roots and retires other new nodes, with
computed entries invalidated before ID reuse. All tracing frontiers and collection
marks use page storage. The externally supplied absent-binding memo in structural
model refinement remains a resident compatibility debt.

General AND/OR and unary operation memo tables/frontiers now spill through the same
backend; no per-diagram HashMap, ArrayDeque or node snapshot is used. Computed-table
and dirty-journal arrays remain fixed and are reserved before allocation. Quota or
storage failure aborts the owner even when a later request could return a terminal
shortcut. Supplied backends remain borrowed; owner close does not close them.

New independent constructor/growth denial and I/O interruption tests exposed unlinked
page leaks. PagedLongArray retains its provisional allocation. PagedLongIndex tracks
all owned pages in a separate exact paged ledger: teardown does not depend on the
validity of a split/merge interrupted halfway through its child updates. Ordinary
retirement removes ledger entries and releases empty ledger pages. The unchanged
index tests retain constant resident capacity under one-page file eviction and zero
final pages. Injected insertion/retirement interruptions also finish with zero pages.
A permanently failed backend or exhausted work during teardown still requires closing
the shared backend; temporary stores have no crash recovery contract.

The focused truth/canonical-ID/scratch/collection/domain/transport tests pass; the
full kernel runs234 methods,232 PASS and two growth methods FAIL with no errors or
skips. Required FAST likewise fails the unchanged ROOT16N backward bound and new
mixed64N relation bound (202 selected kernel methods, two failures). Exact W1/W2/W3/
W4/W5/W1D source and compiled boundaries pass. This checkpoint remains UNQUALIFIED.

The new mixed counterexample constructs OR_i(A_i AND B_i) and AND_i(A_i iff B_i)
with separated A/B variable order, independent of AIR/control scheduling. Complete
small truth/restriction oracles pass. Twelve pairs retain8191/8193 Boolean nodes even
after every unrooted row is collected. Each A assignment selects a distinct remaining
B residual; the ordered representation must encode those distinct residuals. This
proves that catalog paging alone cannot resolve the algorithmic debt. The production
engine must change its represented relations, while preserving exact functional
equality and convergence. Neither growth assertion nor peakBooleanNodes is weakened.

### Paged exact Boolean decision foundation (AS-W04/W09, not integrated)

PagedBooleanDecisions is an incremental clausal decision engine with watched
propagation and first-UIP resolution/backjumping, following the complete
[Eén/Sörensson SAT2003 paper](https://lara.epfl.ch/w/_media/projects/minisat-anextensiblesatsolver.pdf).
Variable ordering uses conflict recency solely to schedule search. It cannot change
the accepted assignments. Permanent clauses and temporary query assumptions are
distinct: assumption UNSAT cannot invalidate a satisfiable permanent formula.
Only consequences resolved from actual implication reasons are learned. Learned
units are installed at the permanent base after undoing temporary decisions.

Variables, heap positions, assignments, models, clauses, literals, intrusive watch
links, trail, levels and pending learned units occupy primitive paged tables. The
engine borrows its backend. Fixed two/three-literal overloads reuse staging buffers
for circuit clauses and queries. Quota/storage interruption aborts the owner rather
than returning a semantic Boolean result. Learned clauses remain until close; their
disk growth is quota-controlled, not yet subject to bounded optional retention.

Nine independent kernel tests cover incremental exhaustive CNF/models/assumptions,
dense nonunit formulas, actual learned conflicts that skip irrelevant decisions,
pigeonhole UNSAT, 4096 implication steps, quota denial and 24 insertion/query I/O
boundaries. Three adapter tests cover identical models through one-page eviction,
constant resident capacity at16/64/256 variables and five disk quotas. Owners close
with zero live pages and zero final heap/temporary/open-file reservations in these
tests. This is a foundation, not an adopted replacement of BooleanConditions.
Queries still assign/copy the whole admitted formula; a circuit adapter must limit
its formula to the exact queried fanin cones. SAT can require exponential search;
neither watched propagation nor paging proves a general linear-time solver.

### Shared circuit/query foundation (AS-W04/W09, not integrated)

PagedBooleanCircuit stores immutable complete primitive tuples for primary inputs
and shared AND gates with complemented edges. Raw handles express structural
identity; functional equality is a separate exact decision. Construction performs
constant/equal/complement/direct-absorption laws and never expands ordered truth
residuals. The six-field arena traces both unsigned child handles, retains distinct
root tokens, retires unrooted logic and never reuses collected handles.

Evaluation, simultaneous substitution and clause encoding use paged iterative
postorder memo/frontiers. Every SAT query admits only its exact fanin cone; equality
admits both cones and checks both differing output polarities. Each AND gate uses
three exact defining clauses. An acyclic gate definition has a unique output
extension for every assignment of its free primary inputs, so omitted unrelated
gate definitions cannot affect this query. This argument applies to circuit
definitions, not to arbitrary CNF slicing. The query's decision owner, memo and
frontier close before returning; learned query history is released.

Eight kernel component tests pass exhaustive independent expressions/valuations,
partial and complete substitutions, satisfiability, distributive/De Morgan equality,
distinct large primary keys, queried-cone counts, 4096-input nonrecursive operations,
root lifetimes, seven heap quotas and35 injected traversal/decision/collection I/O
boundaries. Both mixed relations retain at most8N circuit rows at8/32/128/512 pairs.
Two adapter tests pass through actual one-page eviction and preserve constant
resident capacity at16/64/256 inputs including the admitted decision formula.

This component is not a replacement BooleanConditions manager. It does not yet
provide functional class IDs, memoized cross-query decisions or solver convergence.
Repeated cone encoding remains a debt of the independent query bridge. The existing
integrated ROOT16N/mixed64N growth REDs remain required and unchanged. No complete
qualification, linear general SAT complexity or global managed pipeline is claimed.

### Congruent nomination and borrowed graph decisions (not a manager adoption)

The circuit foundation now stores28 fixed words of actual primary valuations per
immutable record, including all-zero/all-one assignments and sparse/dense complement
channels. AND combines child words; negation complements them. Independent tests
check every sample bit against direct evaluation, distributive equivalence and counted
linear construction work on4096 inputs. Equal samples never prove equivalence.
Channel densities only schedule comparisons; they do not narrow semantic behavior.

BooleanCircuitDecisions accepts a borrowed BooleanCircuitView of acyclic shared
logic. It normalizes exact aliases and indexes primary keys separately from node
handles, preserving correlation when several handles mean the same primary key.
Paged postorder admits only the queried cones, uses exact defining clauses and
releases each formula/model/reason/memo/primary index before returning. Invalid
cycles are operational CORRUPT failures and abort later terminal queries. The circuit
foundation now uses this shared decision implementation; no BooleanConditions
consumer is integrated. Three additional independent view methods and existing
one-page circuit tests pass. General SAT cost and repeated cross-query encoding
remain explicit debts; native exact-key dispatch is required before manager adoption.

### Native sampled sets and current-class nomination index (not adopted)

SignedLiteralSet optionally accepts a62-field arena: the original six structural
fields/reference columns retain their meaning;56 derived words store conjunctions
of each normalized literal polarity. Global literal sign inversion swaps the pair;
OR complements the all-false conjunction. Each copied Patricia branch combines
already stored child words, with no whole-prefix rescan. The increased staging
tuple is reserved before allocation. Six-field clients retain their schema and
behavior. Metadata is part of the same immutable arena and retires with its record.

BooleanFunctionIndex stores complete native aliases, derived sample vectors and
live mixed/native nomination lists in paged primitive columns and exact indices.
New native descriptors search only mixed nominees; complete native keys handle
pure prefixes directly. Every nomination match uses a supplied primitive exact
equivalence callback. Separate intrusive previous/next links remove a class in
constant list work, even when all samples collide and retirement starts at the tail.
Required payload/indices have fixed resident controls. Alias tokens are metadata
belonging to the enclosing literal arena: normal removal returns them for release;
the enclosing owner closes that arena after index teardown on failure.

Five index and three native-sample kernel methods pass independent collision/value,
complete-key, reuse, polarity/evaluation, copied-path work and retirement laws.
Six constructor/growth quotas and21 publication/binding/retirement I/O boundaries
abort and close with zero owned pages. Two index and one sampled-set adapter methods
pass one-page eviction, native/mixed transitions and arena collection;16/64/256
indexed classes retain constant resident capacity. These components are not yet
wired into BooleanConditions; global functional convergence and original integrated
growth failures remain unresolved. No global qualification is claimed.

Read interruption is an operational failure of the complete condition owner. Five
independent injected read paths (bit-set and persistent-map evaluation, empty-root
binding, required/potential primary hints) now abort subsequent terminal shortcuts.
Previously an interrupted internal store could leave the enclosing owner apparently
usable. The fixed computed buffers are also released by close, including Java
references held by the closed owner. Read-abort tests close through try-with-resources
and verify zero pages and resource reservations; this is not a physical heap-size
measurement. Literal/native-class components remain independent, with no manager
adoption or resolution of the two integrated growth REDs in this checkpoint.

### Functional shared conditions — integrated, qualification remains incomplete

BooleanConditions keeps signed function handles over shared binary AND/OR rows,
with native signed literal junctions. General Apply does not enumerate Shannon
residuals. An immutable borrowed view translates OR and native disjunctions to exact
AND/complement definitions only for the queried cone. Nomination uses actual stored
valuations; exact SAT miters establish every functional merge, including constants.
Independent DNF/CNF/complement constructions of all256 three-input functions have
one ID each. The previous manager failed this identity law (truth1, IDs9/262).
This identifies a canonicality defect, not a measured lost public dependency.

The existing catalog still owns IDs, scopes, marks and retirement. A class proved
equivalent to a native descriptor can replace its representative: remove the old
structural key before publishing the new one, preserve the function ID and marks,
and release obsolete child dependencies through collection. No new class references
enter an older representative. Native/support tokens are separately owned and
released exactly once. Cofactors use paged postorder memo and the fixed computed
table keyed by function/variable/binding; absent bindings retain caller-owned memo.
Empty-root evaluation uses an actual valuation bit, not simulation equivalence.

The63-field literal schema adds a canonical unsigned-key reference to the sampled
schema. Signed trees share their unsigned skeleton; one branch creates at most one
additional all-positive branch. Legacy6 and sampled62 schemas retain their laws.
Proper native junctions certify exact essential support; complement preserves it. AND/OR
of nonconstant functions on disjoint exact supports certifies the union. Opposing
full junctions on at least two keys describe all-equal/not-all-equal, with every key
essential. Other functions remain uncertified. Known support partitions skip only
provably different supports; unknown candidates compare against all nominated
known/unknown classes. Native/mixed group tags and support/unknown partitions share
three exact paged indices with four intrusive links per class. No all-prefix scan
is needed for certified disjoint prefixes; general equivalence remains SAT-hard.

Fault testing found a shared radix ownership defect: releasing a leaf before
unlinking its parent left a released generation reachable after an interrupted
write. Retirement now detaches/transfers the root before releasing and retains a
provisional page through interruption. A second defect used opaque page handles as
ownership-column addresses. Index ownership now uses local dense ordinals, packed
with the leaf flag in the existing header word, with opaque handles only as values.
A bijective renaming to widely scattered positive handles reproduces the old quota
failure and passes after the change, preserving96-byte minimum index pages. These
are internal temporary records; no AIR/CFG/dependencies wire format changes.

The original40KiB index and1MiB128-literal read-probe budgets pass. The read probe
now declares its current root during construction and releases unowned prefix
history; its cardinality, fault paths and quotas are unchanged. One-page indexed
capacity at16/64/256 remains constant. Shared retirement, binding, certification,
representative replacement and read interruptions close with zero owned pages.
ROOT16N backward remains required and failing. Eager collection was only a diagnostic,
terminated before completion, and is not adopted. Current root registration/history,
repeated query-cone encoding, resident absent-binding memo, admitted Scalar relation
and the rest of the managed pipeline remain debts; this is not global qualification.


The mixed manager also preserves optional positive caller-witness nomination.
An iterative paged traversal visits each `(condition ID, polarity)` at most once
per uncached query; a fixed owner-local memo includes the operation and full root.
Occurrence is only a hint: caller words are tested individually and failed hints
retain exact feasibility. Independent OR/complement cases reproduce the previous
missing-hint regression. Dispatcher timing passed two executions after correction;
the unchanged ROOT 16N envelope remains an integration gap. No qualification is
implied for AIR decoding, all domain joins, delivery or global memory bounds.


Circuit decisions can now share a required paged formula within an explicit
operation scope. Node encodings and entailed learned clauses persist across
queries within that scope; assumptions do not. Ending a scope releases the engine,
node memo, frontier and primary index before the manager reuses any row ID.
The manager uses this only during its append-only scratch lifetime; literal-arena
handles are never reused. Proved native replacement preserves Boolean meaning.
A prefix law isolates repeated cone encoding: fresh queries read1313280 definitions
at512 prefixes, whereas scoped encoding satisfies the unchanged32N read bound.
This bounds repeated definition traversal for that law, not arbitrary SAT search,
number of contextual configurations or the entire dependencies application.

### Explicit expression lifetimes (AS-W04/W05, integration in progress)

`PagedDagOwnership` manages the acyclic expression graph with strong root and
edge references, separate construction holds, and an iterative retirement queue.
A replaced binding acquires its new root before releasing its old one. Duplicate
edges count separately. A two-pass handoff supports graph IDs that are not in
numeric topological order. Root tokens are owner-bound and never reused; reused
node IDs receive a new generation. Construction journals, roots, reference metadata
and retirement queues use the borrowed page backend, including disk spill.
This algorithm does not collect program or equation cycles: those remain under
`SummaryCollector`. Its DAG precondition is guaranteed by expression construction,
not inferred from the fixtures. Work is proportional to created/retired edges and
root updates, with bounded radix addressing; it never rescans the graph history
for a binding update. Live function representation and SAT retain their separate
complexity limits.

The backward solver now registers every immutable model sharing a manager before
initial publication. Guarded value classes, incoming/child maps and caller arcs
own their condition roots. Previous child maps remain owned until reconciliation.
An external borrow across mutation requires an explicit root. Backward partition
reads and deliveries use distinct IN/OUT containers. Feasibility and deferred
query keys include condition generations; optional cache entries do not retain
expression history. The forward solver has not adopted this lifetime protocol.

Decision scopes remain append-only: retired IDs enter a paged queue and become
recyclable only after the scope's CNF engine closes. Computed caches validate operand
and function-result generations; variable keys and scalar hint results are distinct
roles. Proven replacement by a native leaf keeps the function ID/meaning and drops
its former child references. Quota/I/O interruption aborts the owner, including
terminal shortcuts; teardown closes ownership metadata before graph stores and
leaves a borrowed backend usable. Persistent corruption/interrupted release are
not qualified by the current synthetic fault tests.

Independent laws cover shared/duplicate edges, reverse-ID chains of 20,000 nodes,
root replacement, nested construction publication, generations, borrowed roots,
truth tables and guarded-state ownership. One-page file tests use a fixed 40,000
byte resident quota and 64/256/1024-node chains; declaration/replacement fault laws
inject 64 I/O failures. These qualify the new ownership primitive, not global heap
bounds: solver maps, models, semantic domains, decoding and delivery remain separate
pending work. Ownership alone still failed original ROOT16N. At an intermediate
1024-node peak, a temporary independent root trace reached 1018–1021 nonconstants;
current shared-function syntax is another remaining mechanism. The fixture and
its original envelope were not changed.

The actual manager additionally passes a separate 256,000-byte, one-page envelope
at16/64/256 independent versions with identical accounted resident peaks and at most
12 live condition nodes. A60,000-byte attempt fails while allocating fixed owner
controls, before input growth: it is retained as a quota-denial/cleanup law and its
raw initial failure is preserved. It is not a passing growth envelope. Initial
handoff/creation inject another64 I/O faults in the actual manager and verify that
terminal shortcuts abort and partial pages close. The original40,000-byte standalone
DAG envelope and originalROOT16N constraints remain unchanged.

A separate RED fault law empties the construction journal, then interrupts literal
collection during publication. The manager previously allowed terminal shortcuts
after that failure. Publication now shares the guarded suffix-publication path;
initial storage admission and permanent-root marking also poison the whole owner
on operational failure. This changes failure propagation, without changing Boolean
functions, public/wire contracts, sample channels or the original growth envelopes.


### Signed functional classes and native factor algebra (checkpoint in progress)

The public nonconstant condition handle is `2*(record-1)+phase`; terminals remain
0/1. Every stored representative is false at the all-absent assignment. This
orientation uses an actual valuation bit, never sample agreement as an equivalence
proof. Complement flips phase in O(1), sharing all required records, essential
support and generation with its positive representative. Native AND/OR junctions
also normalize kind and signed literals through De Morgan. SAT receives physical
record identities plus edge polarity; checkpoints and catalog offsets remain
physical. Handles have a checked operational address limit, not a semantic cap.

`PagedDagOwnership.Graph.canonical` is a pure, idempotent identity mapping. Signed
roots retain their actual value while counts and retirement use the shared record.
Duplicate edges in opposite phases each own one reference. Binding between phases
acquires before release. Weak caches validate shared generation; query scopes defer
recycling until their CNF closes. A native representative proved equivalent during
restriction preserves both rooted phases and the generation while dropping old
edges. The added law checks all truth assignments and both discard/commit paths.

Signed Patricia intersection/difference skip identical subtrees and use a frontier
bounded by key width. Native factorization uses exact distributivity without cube
expansion. Extracting a native child of a mixed junction requires certified,
disjoint essential support from the other child: this proves an independent
component. Residual construction bypasses recursive factoring. Unknown/overlapping
supports remain in the exact circuit. This does not promise minimal representation
or polynomial SAT for every Boolean function. The representation follows edge
inversion described in [ABC section3.3](https://people.eecs.berkeley.edu/~alanmi/publications/2010/cav10_abc.pdf)
and its [AIG implementation](https://raw.githubusercontent.com/berkeley-abc/abc/master/src/aig/aig.h),
without importing bounded cuts or heuristic semantic merges.

Current focused ROOT16N and factor laws pass with their original fixtures/envelopes.
A broad run with JFR passed302 kernel methods plus112 CFG methods. Earlier full
runs without profiling timed out or were interrupted in recursive dispatch; these
are preserved, unexplained evidence rather than relabeled PASS. Eight sequence
permutations at4/128 callers additionally preserve the independent small oracle
and64N certificate bound under a temporary diagnostic work quota, now removed.
Final FAST and public CLI parity remain pending. New shared representation does
not qualify all AS-W00–W10 work: forward ownership, correlated Scalar semantics,
managed admission/program/output and whole-pipeline resource bounds remain open.
### Quota-independent page teardown (AS-W01/W09, partial checkpoint)

Page ownership cannot require remaining analysis WORK to release its storage.
`PageStore.readForCleanup` and `releaseForCleanup` are explicit teardown operations;
they preserve address/generation validation and genuine backend failures, and cannot
admit new analysis allocations. Resident and file backends and all local decorators
implement this contract. Ordinary reads, writes, releases and reservations keep their
existing quotas. There is no thread-local bypass or reset of the analysis budget.

`AnalysisResources.cleanupWork` records teardown separately. Its observational
counter saturates with an explicit flag instead of interrupting cleanup. The paged
array traverses allocated ownership metadata with its existing fixed stack; the
index releases its separate ownership ledger. Borrowed stores remain usable after
successful cleanup. A permanently failed backend still requires closing its outer
owner; this protocol does not certify recovery from corrupt metadata.

The disposable file owner closes/deletes its arena without flushing payload that
would be discarded. It retains the disk charge if deletion fails. Cache payload
buffers are allocated and accounted once at construction and reused for I/O,
including eviction during teardown. Both actual activation solvers use language
resource scopes, preserving a primary domain failure and suppressing later cleanup
failures instead of replacing its phase/reason. Successful results cannot escape a
failed final close.

New laws exhaust WORK before releasing arrays/indexes, require zero live pages on
borrowed resident and one-page file backends, preserve ordinary WORK exhaustion,
and verify the exact primary exception and zero heap reservations for both solver
directions. These laws do not yet qualify every logical root/cursor lifetime,
cancellation/deadline behavior, permanent I/O failure, or zero-progress transfers.
Global runtime/CLI migration remains in progress.

### Independent circuit search dimensions (AS-W04, partial checkpoint)

Circuit encoding distinguishes original atoms from exact acyclic AND definitions.
The clausal engine's ordinary `newVariable` still creates an independent choice
for arbitrary CNF. Its `conjunction` operation creates a fresh derived output with
the full three-clause equivalence and references only existing inputs. Only original
atoms enter the branching heap; derived outputs keep propagation, implication
reasons, learning, assumptions and model values. The existing paged heap-position
column has a reserved negative marker for these outputs; no object/array per gate
or extra resident directory is introduced.

With every independent atom assigned, propagation of full acyclic definitions
determines all gate outputs. Model publication checks that every variable is
assigned. Thus removing auxiliary choices preserves all independent valuations;
it does not approximate clauses, drop dependencies, or publish partial models.
SAT may still require exponential work in necessary independent atoms.

The cost law counts the actual restored independent heap for chains of32/128/512
atoms, rather than counting only source graph nodes. A separate exhaustive four-atom
truth-mask oracle checks derived models, both signs, interleaved assumptions and
later arbitrary constraints. Existing generic CNF, circuit scope/equivalence,
ROOT16N, recursive dispatch, one-page spill and failure laws remain unchanged.
Recursive dispatch timing and schedule-sensitive certificate work still require
qualification; this representation change alone is not a global completion claim.

The exact compiled definition directory belongs to the clausal scope. Full signed
operand pairs are sorted and collision-checked, so aliases and commuted operands
reuse one derived variable and clause triple. Input keys occupy two additional
primitive columns in the existing variable store. The required open-addressed
directory occupies a disjoint high-address region of the existing paged heap store;
independent priority-heap addresses stay below Integer.MAX_VALUE. This adds no
cardinality-sized resident directory or per-definition object. Growth copies into
fresh paged addresses before publication, then clears old slots so radix pages
retire. Page admission includes the old-plus-new peak; failure aborts the owner.
Variable identity exhaustion bounds the table address arithmetic; the hash is only
an index, never an equality oracle. Encoded equal/complement handles answer equality
before SAT search. The unchanged40KB one-page law remains intact.

Condition canonicalization now has a focused [partial-assignment reduction design](air-scale-condition-reduction.md). Its local laws do not complete WORK-AIR-SCALE.

The [paged AIR identity-key bridge](air-scale-snapshot-identities.md) shares this
ledger/page runtime with official snapshot access. It qualifies exact bounded
index storage; complete paged Validator/streamed admission remains pending.
