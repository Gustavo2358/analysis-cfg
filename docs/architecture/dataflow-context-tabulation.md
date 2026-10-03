# Local activation tabulation

Status: implemented and locally qualified. Human review and merge pending.

## Problem and authority

AIR 05.7 supplies local invoke, completion ports, selected resumes, guarded
reentry and counted/full unwind. These are typed control rules, independent of
COBOL names. The old solver and dependency reachability enumerate the full pending
stack at each node. A small cyclic dispatcher produces millions of points before
any value transfer. Acyclic call diamonds can also multiply pending call strings.

Required invariants: matched returns; Entry isolation; exact guard decisions;
full/count unwind; separate value roots before non-distributive replay; no lost
candidate, evidence or provenance; no arbitrary depth, node or iteration cutoff.
A source PARTIAL or an open remainder must remain explicit.

## Candidate and preconditions

Tabulate an activation by its frame and abstract input value, retaining caller
subscriptions for return delivery. Equivalent inputs share computation; different
inputs remain separate. The existing SPI requires isolated immutable roots,
monotone transfers and lawful joins. Tabulation additionally depends on finitely
many reachable input values. The production domains audited below satisfy that condition. Transfers depend on the node and Entry, not an inspected concrete call
string; no production callback currently inspects a traversal stack.

An activation's output also depends on guards in its ancestors. Do not erase
these tests or key the cache by an enumerated set of ancestors. Represent guard
conditions as canonical Boolean decision diagrams. A child's condition is
substituted with the parent's active key, then intersected with its call condition.
Pointwise state joins retain distinctions between disjoint guard conditions.
Caller subscriptions preserve the concrete frame's return/selected routes. Escapes
carry their destination and remaining pop count back across activation boundaries.
An activation exit and full unwind return to the root activation.

The root has no active keys. A Boolean structural pass first builds frame shapes
and a conservative caller graph. A frame cannot have an ancestor outside the
transitive caller set. Variables for such ancestors are fixed false before
tabulating values. The current frame's
own key is always treated as active locally; its formal ancestor variable is
irrelevant and stays unconstrained. This prevents impossible
guard combinations from multiplying whole stores even in a straight-line routine.
The remaining guard variables stay symbolic; no reachable-set enumeration is used.

## Demand and feasibility

Before creating a new input-value summary, predicate reachability runs backwards
through current caller subscriptions. A cached summary can be reused immediately;
its incoming guard still participates in subsequent feasibility checks. Backward
continuation-vector construction also filters infeasible partial combinations
when several continuation targets would otherwise form a Cartesian product.

Within a region, a block or edge contribution that changes its input value is
propagated only into a feasible partition. Call inputs are checked before a new
summary is created, while existing summaries retain their call predicates. Identity propagation can proceed without that query: it does
not generate fresh values around an infeasible cycle. Transfers are pure callbacks;
a candidate value can be computed to test equivalence before its propagation is
accepted. Calls with no current witness are deferred, not permanently discarded.
New incoming subscriptions wake the affected slots. Negative query caches retain
the versions of all watched incoming relations; any growth invalidates the answer.
The 1,024-entry negative cache bounds memoization only: eviction recomputes work.
Positive witnesses survive added links and are invalidated by replacement/removal.

A preliminary breadth-first graph search tries one shortest caller path and checks
every guard along it using a concrete Boolean valuation. Failure of that witness
falls back to complete symbolic predicate reachability. The symbolic worklist
coalesces pending deltas by region rather than enqueuing each alternative path.
Neither search invents a success or bounds the number/depth of semantic contexts.

Read-only feasibility and boundary queries use a BDD allocation checkpoint.
Temporary nodes are discarded when the query returns; persistent condition IDs
remain stable. Computed-cache entries referencing discarded operands or results
are invalidated before IDs can be reused. Only Boolean answers and watched-region
versions escape the query, never scratch condition IDs. This avoids retaining the
union of every temporary proof formula for the lifetime of the analysis.

At publication, the same predicate reachability filters result roots.
It asks whether a given guard condition can reach the empty root, combining needs
by Boolean OR. It does not construct concrete call strings or enumerate subsets.
The same test filters unguarded recursion errors; feasible unguarded recursion
preserves the existing refusal.

Backward analysis tabulates by the vector of states at possible continuations:
normal/selected returns, root exits and the counted unwind destinations at each
relevant depth. Signature components are selected under the same guard condition,
so independent continuations are not freely mixed. Calls substitute the parent's
active key, and return-edge transfer uses the original program destination.

Arbitrary node boundaries retain the unique-context requirement. A predicate
search finds one witness stack and a second search excludes that word with a
small automaton. A second feasible word makes the boundary ambiguous. No full
stack inventory is constructed.

## Soundness and complexity obligations

A call subscription denotes all concrete caller stacks with the same frame,
input value and satisfied guard condition. Push substitutes one active key; a
matched pop delivers only to that subscription. Composition and pointwise joins
must preserve this relation. Counted unwind must cross exactly its number of
subscriptions; full unwind must reach the empty root. Guard conditions cannot be
collapsed to MAY/MUST booleans or unrestricted return edges.

The implementation should scale with tabulated input values, nodes, subscriptions
and decision-diagram size, rather than the number of call-string permutations.
This is not a universal polynomial bound: arbitrary finite abstract domains and
Boolean functions may themselves have exponentially large representations. Such
inherent cases must not be hidden behind a claim of universal bounded memory.

## Research and alternatives

[Padhye and Khedker (2013)](https://arxiv.org/pdf/1304.6274), section 2, explains
sharing analysis results by procedure/input value for monotone finite domains,
including non-distributive transfers. That paper does not supply the AIR guard or
unwind semantics; those are obligations of this extension.
[Bryant (1986)](https://www.cs.cmu.edu/~bryant/pubdir/ieeetc86.pdf) supplies reduced
ordered Boolean decision diagrams and their operation/representation tradeoffs.

Rejected: increasing heap; enumerating only lazily but retaining all stacks;
merging all callers; bounding stack depth; treating an unsupported domain as an
empty successful analysis; applying IFDS without a distributivity proof.

## Oracles and qualification

- Preserve an independent small explicit-stack interpreter as a test oracle.
- Compare IN/OUT and replay results for callers with distinct values, guards,
  selected resume, nested/empty/count/full unwind, forward/backward and cycles.
- Scale cyclic dispatchers and acyclic diamonds with a fixed small abstract domain.
- Exercise dependency reachability directly even when no value analysis is planned.
- Run production source→SP→AIR→CFG→dependencies for all 560 frozen corpus inputs.
  Compare candidates and their evidence/provenance by complete source identity,
  retaining open remainders and rejecting unexplained additions as well as losses.
- Run CFG FAST and full local qualification after stabilization.

## SPI and counters

The pre-release `AnalysisDefinition` contract explicitly requires stack-independent
transfers for local tabulation. Production callbacks read the node, Entry and state;
none inspects the traversal stack. `AnalysisPoint.concreteTraversal()` is empty for
summarized points. `traversal()` throws there, instead of presenting a fabricated
empty stack. Ordinary graphs retain their concrete handles and existing solver.
A user-defined transfer that inspects every call string cannot in general have its
arbitrary behavior preserved without performing that work.

Result `states` are separate tabulated roots, rather than one root per concrete
stack. Observation replay must remain per root before projection/join. Counters for
local control measure tabulated work and returned roots, not concrete stack counts.
The structural Boolean preparation is separate from domain transfer counters.
Ordinary graph counters and worklist scheduling are unchanged.

## Preventive audit

- `SolverTopology` and dependency `ReachabilityProvider` were the two production
  clients enumerating `ContextView.Point` stacks. Both public and internal solver
  entry points now route local control to tabulation. The ordinary topology
  constructor rejects accidental local-control use.
- Forward and backward execution, cyclic dispatchers and acyclic call diamonds
  are covered. Boolean diagram operations use explicit work stacks; the 20,000
  variable test protects against Java recursion overflow.
- Reaching definitions uses sets of static events/segments. Reachability has four
  bit-mask states. Scalar text values originate in finite literals and bounded
  FitText/SliceText expressions; a bare growing CONCAT is not admitted by
  `TextProfile.prepare`. Producer/support IDs are static. These production domains
  satisfy the finite-input condition.
- Regional values retains a symbolic relation over finite prepared segments,
  contents and static provenance. Its Cartesian `sourceSelections` loop was
  inspected: a prepared Assign or CopyBytes has one captured read; foreign and
  opaque writes have unknown sources; Entry batches contain typed literal seeds.
  Thus the current typed producers cannot supply several independent captured
  reads to that loop. No speculative semantic rewrite was applied there.
- Queries which must publish exponentially many distinct values/support
  combinations still have output-sensitive cost. This change removes enumeration
  of pending call histories; it does not assert a polynomial bound for all possible
  abstract domains, Boolean functions or dependency outputs.

## Regression discovered during qualification

The first universal-guard prototype passed the small explicit-stack oracle but
caused CardDemo value analysis to exceed five minutes. The caller-set restriction
above removed that infeasible work. A synthetic outer routine with ten sequential
value-producing guarded calls now tests the condition independently of CardDemo:
its 1,114 transfers in the failed prototype violate the regression bound. No
boundary, dependency output or expected semantic value was weakened to pass it.

Boolean apply/restrict use a fixed-memory computed table. The complete operand
IDs and operation are checked on every hit; collisions evict recomputable results.
This cache cannot drop a guard, truncate a domain or change a semantic result.
A two-slot table is tested against all 64 truth assignments of 300 generated
formulas, deliberately causing collisions. Keeping the current frame's redundant
ancestor variable unconstrained also avoids needless BDD work without weakening
its always-active local guard.

## Current subscriptions and result roots

A caller subscription denotes its current input or continuation vector. When that
input changes during the fixed-point iteration, its old subscription is removed;
the immutable old summary can remain cached for other callers. Final feasibility
therefore excludes intermediate input versions with no live path from the Entry.
This also preserves `in` at a single concrete activation in a loop, instead of
incorrectly reporting multiple contexts. The regression test is RED when old
subscriptions accumulate. The generated oracle compares exact sets of IN/OUT roots: 240 analyses in the
regular test and 2,400 in the expanded qualification. It does not discard subsumed
roots when comparing.

Additional synthetic regressions cover infeasible guarded calls with changing
inputs (40,977 transfers before demand filtering), and ordinary loops whose block
or edge transfer grows a value only in an infeasible guard environment (4,112
transfers before filtering). Each now requires fewer than 100 transfers while
matching the independent explicit-stack oracle. A 64-call dispatcher exposed
scratch BDD retention and redundant feasibility queries; qualification exercises
both directions with a 512 MiB heap. The missing-direction rejection is also
preserved at the solver entry point.


## Completed local qualification

- CFG FAST and full local qualification: PASS.
- 44 focused tests and 2,400 generated forward/backward analyses: PASS.
- 560 fresh source-to-dependencies executions: PASS. Every dependency field,
  candidate, support, provenance and remainder is unchanged, excluding only the
  two execution-counter objects. The 439 COMPLETE and 121 PARTIAL statuses remain.
- All 2,240 upstream product comparisons (SP, dependency bundle, AIR and CFG): equal.
- Twelve COBOL stress cases including typed ESCAPE and CICS effects, and six AIR
  scale cases through 64 calls in both directions: PASS with 512 MiB heaps.

The workspace evidence is in `artefatos-e2e/dataflow-context-fix-20261003/`.
`final-runtime-manifest.json` identifies the exact production source tree and JAR;
`comparison-qualified.json` records each source identity and semantic comparison.
The corporate program was unavailable. This is local qualification of the frozen
corpus and synthetic cases, not a claim that the corporate program was executed.

## Final feasibility queries after lower unification

The 100-destination CICS fixture finished lowering but exceeded 90 seconds during
`ActivationSolver.result`. Many final predicates asked whether an activation key
could be present in an ancestor. A shortest caller path often omitted that key,
so each observation repeated symbolic search over the same stabilized graph.
Backward analysis had the same final-query path.

`CallerWitnesses` records concrete active-key valuations reached from the Entry.
An edge contributes a valuation only after its full predicate is true; the parent
frame's key is then marked present. Each retained valuation adds a previously
unwitnessed positive or negative literal in that region. With V guard variables,
at most 2V+1 valuations survive per region. Every accepted valuation is a complete
witness; their bits are never combined into an invented path. Positive and negative
literal masks control cache admission only, and cannot answer a semantic query.
The cache is built once after subscriptions stabilize. Transfer-time queries keep
the existing invalidation and subscriptions.

A cache hit proves existence by evaluating the entire requested predicate on one
verified path. A miss continues through the shortest-path check and exact symbolic
search. When the BDD's forced prefix requires an ancestor key to be present,
the shortest-path search uses the product of the caller graph with a two-state
monitor (key not yet seen / key seen). It visits at most 2R states and 2E edges;
all edge predicates and the full query are still verified on the resulting path.
This avoids repeatedly choosing a short path which cannot satisfy the query.
No path through the product implies no feasible path, because that key is necessary.
Other predicates continue through the exact search. It never proves absence or drops a context. This is bounded memoization;
no threshold changes semantic results. Induction over accepted caller edges proves
that every cached valuation is reachable. The independent finite bit-mask oracle
checks this property on 80 cyclic graphs, including correlated keys. The full
solver oracle compares exact IN/OUT roots in both directions.

For R regions, E caller edges and V variables, cache construction processes at most
(2V+1)E edge candidates. BDD evaluation and bit-set operations are polynomial in V;
stored witnesses use O(R V²) bits. The exact fallback and abstract-domain fixed point
retain their intrinsic worst-case complexity. This is not a claim that arbitrary
Boolean predicates or user-supplied domains admit a universal polynomial bound.

Qualification for the unified lower is recorded in
[lower-unification.md](../engineering/lower-unification.md). Earlier qualification
above remains evidence for its original producer and solver pins.
