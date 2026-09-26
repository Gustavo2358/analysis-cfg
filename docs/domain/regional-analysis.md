# Regional storage and reaching definitions @1

ST-W2 implementation contract, extending [storage semantics](storage-semantics.md).
`analysis-kernel/storage` owns physical resolution and statement effects over AIR.
`analysis-kernel/rd` owns a finite forward analysis using the existing solver SPI.
Neither package belongs to CFG construction or knows source language syntax.
Values will consume the same prepared storage/effects context in W3/W5.

An immutable preparation belongs to one AnalysisSession. Bases retain StorageHeader
(owner, lifetime, visibility, origin); activation identities are qualified by Entry,
while persistent/external identities are not activation-local. This distinction
does not assert communication between independent analysis runs. Object aliases
share location; view codecs remain interpretation facts. Alternatives and unknown
bindings preserve candidates plus safe remainder scopes. Unknown extent uses an
unbounded abstract tail, never a zero extent or an asserted concrete allocation.

Ranges are half-open BigInteger intervals. Their finite partition uses observed
constant boundaries and an optional unknown tail, never one state per byte.
Whole Cells have their own non-byte footprint. Distinct StorageIds are independent;
DisjointStorage is redundant. Preparation never needs a matrix of object pairs.

Effects record ordered reads before writes, operand occurrences, operation/outcome,
MUST/MAY strength and evidence. Exact destinations permit strong update on their
own covered interval. No effect is added in other bases for missing source proof.
Explicitly modeled scoped effects and ambiguous destinations never
strong-update all alternatives. CopyBytes reads capture the source before updates;
fallback is used only when its precise ranges cannot be interpreted. Invoke target
reads precede foreign effects; per-outcome bounds replace the default for that outcome.

RD definitions are finite events (Entry/initial condition or operation/destination/
outcome), with a distinct unknown flag. At a reached point, absent facts denote an
entry unknown definition, not bottom. Unreachable point is separate. GEN/KILL
replaces contributors only on exact MUST segments; MAY joins its event with old
contributors. Join is set union per segment. Events do not contain path histories.
Finite program boundaries and events imply finite height and monotone transfers.
There is no candidate/work/iteration cap. Operational exhaustion emits no partial
semantic success.

Stable batched queries identify snapshot/session, profile, Entry and before/after/
outcome. Each contributing event includes its surviving interval, source origin,
premises and uncertainty; query remainder distinguishes physical resolution and
unknown definitions. Replay shares the stable solver run and ordered prefix.
Unsupported points remain explicit. This typed in-memory API needs no new wire
version in W2; W5 will add a separately versioned RD wire product if delivery needs it.

`ReachingDefinitions.prepare(effects)` reports admission, and `execute()` reuses the
generic `AnalysisDefinition`/`DataflowSolver`. Its Execution owns the exact session,
partition, proofs and finite event pool. AnalysisKey includes implementation/version,
storage/effects profiles, direction, precision and Entry; it is meaningful only
within that session lifetime. Repeated `observe` calls reuse STABLE. No global cache
uses publication ID or AnalysisKey alone. Ordinal event handles keep composite IDs
out of hot set unions. Sparse state uses persistent AVL updates rather than copying
the whole storage map per instruction; fixedpoint roots contain no path histories.

An event identifies the Entry, operation/initial condition, destination occurrence,
slot, outcome and affected storage base. Its unknown flag and reasons distinguish
unknown effects, possible cross-base alias impact and entry content. Literal entry,
preserve, external, parameter and uninitialized origins remain separate. Normal
Invoke effects run on the normal edge; target is observed before that transfer.
Open control conservatively weakens the possible outcome effects. The query API
materializes an explicit OUTCOME only when the CFG supports it. Generic batch replay
computes its subject-independent transfer once for that point/outcome; all legacy
profiles retain their existing refusal via the default optional hook.

Coverage diagnostics stay outside targets, definitions, ByteImage content, captures
and state equality. They can annotate the queried subject in presentation, but do
not travel with copied fragments. Semantic unknown writes and entry contents still
contribute typed definitions. Origins and premises of actual effects are preserved.

AIR currently refuses literal regional initializers with VALIDATION_LIMIT, including
a singleton, because byte/codec initializer consistency is outside its validator
slice. W2 preserves that guard; existing Cell literals and explicit admitted unknown
entry conditions have events. Unsupported overlapping conditions are classified
before the solve. This is not general VALUE/layout initialization support (W7).

Required independent witnesses include D1[0,8), D2[0,4) yielding D2[0,4) and
D1[4,8); unknown partial MUST/MAY, aliases, positive same-base overlap, alternatives,
branch without write, loops, separate Entries, unknown tail, zero length, before/
after and Invoke target-before-effects. Concrete per-octet test interpretation is
independent of interval transfer/join and the solver, with explicit finite fixtures.
