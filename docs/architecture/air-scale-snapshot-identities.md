# Paged complete snapshot identity keys

AS-W02 / IN_PROGRESS. Producer pin: air-java
`5c41a64a9838bb934218f918fe7bd8d0f4325c7a`; normative AIR
`4f09e8b1b496bf8de2e0fb62532e7aa0b97b9c6e`, identity §2, I-01/I-02.

The official `SnapshotIdentityKeys` algorithm reads complete typed namespaces and
bounded text blocks. `PagedSnapshotIdentityStorage` supplies exact seven-column
canonical tuples and a paged source-handle memo, using the same PageStore and
AnalysisResources ledger as the snapshot bridge. Only fixed staging/control remains
resident. No Java model record or String key is retained by this adapter.

Tuple columns 1 and 2 are child references; the remaining columns are literal
words. Exact full-tuple comparison, rather than hashes, determines equality. This
catalogue is required index state and remains owned until the validation index
closes; no collection may expire canonical keys while the source memo retains them.
The one fixed staging array is reused for every tuple request.

Namespace canonicalization performs O(V+C) primitive requests for V distinct
visited source nodes and C character units. The existing exact paged ordered indexes
perform O(log U) lookup comparisons for U resident logical keys; this is not a claim
of total linear I/O or universal analysis complexity. Every payload/directory spills
and is quota-controlled; a store may fail operationally without approximating IDs.

Independent backend tests compare model identity equality, complete namespaces,
Unicode and key transcripts on memory/one-page stores. A long nonrepetitive identity
forces key payload above the managed heap quota. The tests explicitly borrow a
resident Publication, so their claim is bounded index storage, not managed input
admission. Constructor denials and exhausted WORK must abort the key owner, release
all index pages/leases and leave the borrowed snapshot available.

Complete paged Validator/checked admission, streaming AIR JSON and production CLI
integration remain pending. These keys neither declare references resolved nor
certify input validity. Current Publication-based validation remains unchanged.

Checkpoint validation: three new backend laws and neighboring resource/arena/AIR
bridge tests PASS. Mandatory FAST PASS: 1026 methods, zero skips, 314.618s,
with exact compiled W1–W5/W1D boundaries. The pressure fixture observed 24032
managed heap bytes, 1343232 temporary bytes and 39 memo lookups/756 work units.
One-page cache eviction is a pressure condition, not the production configuration
or a general throughput claim. The producer FAST passed before immutable repin.

The first consumer FAST stopped at missing adapter inventory registration; the
second had two exact CLI stderr failures from JAVA_TOOL_OPTIONS startup banners.
Both are preserved. Explicit tool/Maven/fork limits replaced the environment banner
without changing test assertions, deny rules, quotas or algorithm inputs.

The consumer now pins producer `94a3fb0bdfa36c2240891c996b8f2df1e0b67949`.
Identity storage is unchanged from the preceding checkpoint; the producer adds
[direct typed occurrence traversal](https://github.com/Gustavo2358/air-java/blob/94a3fb0bdfa36c2240891c996b8f2df1e0b67949/docs/architecture/paged-operand-traversal.md)
and exact frozen cursor cardinality. A traversal-only 64-root fixture runs through
both resident and one-page backends, preserving order and releasing scratch on a
callback failure. It does not establish complete structural admission.

## Fused atom summaries

The consumer pins producer `546e774dfb3d207f4b7403099967e5a3e723a9c3`.
The same managed canonical arena now exposes immutable tuple columns for exact
TEXT/INTEGER content summaries: character length, Unicode scalar count, canonical
integer sign and magnitude modulo eight. The producer fuses these facts with
identity interning in one bounded character scan; subsequent property requests
read cached primitive words. No proportional String/BigInteger is reconstructed.

Two additional adapter laws exercise memory/file parity, a pair split across
1024-character blocks, a 100003-character integer written in bounded blocks,
repeated cached queries, and an operational metadata-read failure. The scalar
fixture has a legal Publication root but unattached atom nodes; it qualifies
scalar/index storage, not full structural admission. Its file run observed a
37088-byte managed heap peak and 631552 temporary bytes under a 65536-byte
quota. Repeating 320 fact queries charged 19392 work units with no page growth.
The empty borrowed page store survives owner cleanup; all run leases return to
zero on final closure. One-page-cache pressure is not a throughput claim.

Focused adapter laws (18 methods) and selected reactor neighbors passed. The W5
compiled descriptor inventory adds only the owned storage's `word(long,int)`
method; boundary deny rules are unchanged. Complete local graph/field constraints,
full Validator, codec and managed CLI admission remain required.

Initial atom checkpoint FAST: 1030 methods, zero failures/errors/skips,307.611s,
including producer resolution, wire oracles and compiled boundaries. The two new
atom methods passed in the focused run and are now explicitly added to the fixed
FAST selection. Final expanded FAST passed1032 methods, zero failures/errors/skips,
312.514s, with the same semantic oracles and boundary deny rules.

The exact producer pin now advances to24dd192258ce7410661fae971380965478e03bb4.
Cached Java21 blankness extends TEXT facts; cached sign/length and first unequal
canonical subtree implement exact signed integer order. This does not reparse
Source characters or reconstruct BigInteger. No unbounded pair-result cache is
introduced; immutable canonical subtrees and the bounded page cache provide reuse.

The additional backend law compares32769-character signed integers with a shared
prefix and checks whitespace versus NBSP. Memory/one-page transcripts agree,128
repeated comparisons allocate no new pages, and file metrics are37088B managed
peak/423392B temporary/51840work. Focused22 adapter laws and reactor neighbors
passed27.322s. The new law is explicitly registered in FAST. These fixtures use
unreachable scalar nodes plus a legal Publication root and qualify scalar/index
storage, not complete constructor/reference/domain admission or a managed decoder.

Expanded FAST passed1036 methods with zero failures/errors/skips in314.013s,
including immutable producer resolution, wire oracles and compiled boundaries.

## Integrated general identity lookup (2026-10-09)

Producer8d9090fb498e876822be423471a8e972083b5192 adds key(Ids.Id) into this same
exact tuple catalogue. Closed namespaces/operand owners and every UTF-16 character
use the original canonical tree; no normalization, hash-only equality or retained
typed-ID/String query cache. Typed queries cost their own text length plus bounded
namespace work and managed index lookups; they do not claim source-memo O(1).
SnapshotDeclarations.fact(Id,Fact) uses these keys directly. Internal general
admission borrows its sealed rows/cardinalities instead of building resident
ID maps/sets. It reuses the mandatory primitive cycle pass only after successful
admission; every remaining general rule still executes. Official int collection
views and resident visibility/domain-proof caches remain explicit limitations.

## Native visibility and demand-driven proof subjects (6d85657)

The current pin6d85657230f4e5c0e4504bd17f9a93c13ee94610 shares the admitted
canonical visibility pairs with the general validator. Exact typed membership
uses the same complete identity keys; the owner remains open until those rules
finish. No second UnitId/ObjectId visibility set is retained in the native route.
Object/Cell/operand proof subjects enter through required relations, premises and
queries, while full reference/type scans and all binding/premise edges remain.
Scoped overlays preserve their existing global roots when a later known subject
joins its type component. Unknown type reasons never become domain keys.

Canonical text construction also reuses its last exact packed leaf in five fixed
primitive words within the existing4096B control lease. Every character, namespace,
Unicode/integer summary and final length is still checked; hashes never replace
tuple equality and no String/Id history is cached. The real407MB private-ObjectId
input finished with the public pipeline under128MiB heap and the original eight-
minute deadline. This is input-specific evidence, not complete managed residency:
signature indexes, connected proof/scoped caches and consumer identity maps remain
resident. Earlier benchmark/FAST paragraphs above describe their stated checkpoints.

## Physical declaration keys borrow the canonical inventory

Native declaration values reconstruct their complete typed ObjectId on every cold
read. StorageIndex now takes the physical alias-inventory key from the existing
immutable declaration entry, not from that reconstructed body. Every declaration
and nested binding is still scanned in original order; aliases, resolutions,
origins and full nominal equality are unchanged. This removes one retained copy
of all identity text, not the necessary physical dependency edges or resolutions.

The existing cold-declaration law first failed because equal physical keys were
different instances from the canonical inventory; it passes with shared keys.
Focused storage/effects/definition laws34, adapter/region laws19, values17 and
public CLI16 passed with the current producer6d85657. The unchanged407,297,668-byte
physical input previously exhausted128MiB heap in StorageIndex preparation;
its same-envelope public rerun completed in448.71s under the original eight-minute
deadline. The unchanged independent oracle preserves all21 semantic wire fields,
all83 origins and CFG, including PROGA/PROGB candidates, supports and remainders;
work metrics separately confirm2052 objects resolved and10 physical plans. Final
FAST passed1158 required methods with zero skips and the original compiled
boundaries in742.710s. The stable-build public repeat completed in448.77s with
byte-identical dependencies/CFG and the same independent semantic oracle PASS.
These two large runtime measurements used Java25.0.4, with Java21 bytecode;
they do not replace the campaign's final Java21 runtime qualification.
Canonical identity maps, physical dependency edges and resolutions remain resident:
this targeted duplication fix does not complete AS-W09 managed spill.

## Cell and scalar keys share the same complete declaration identities

IndexBuilder still scans every Unit reference and Object binding in its original
order. Its direct Cell associations borrow the first declaration scan's key only
after checking complete ObjectId equality; position alone never identifies an
Object. TextProfile likewise reads every cold declaration and preserves all
storage/type admission, precision, demand closure and effect checks, but its
subject locations and text eligibility use the immutable declaration entry's key.
No new identity map, retained body cache or public inspection API is introduced.

The existing cold-declaration law observed separate RED failures for direct Cells
and scalar subjects; test-only reflection checks exact canonical-key sharing while
retaining its cold reads, full namespace, immutable view and owner-lifetime checks.
Focused kernel38, values52, adapters12 and public CLI19 methods passed without
skips. The same406,942,781-byte Cell-bound computed-CALL input first exhausted
128MiB in IndexBuilder, then in TextProfile after only the Cell correction.
With both associations sharing keys, the public pipeline completed in268.34s on
Java21.0.12+1.1 under the unchanged eight-minute deadline and128MiB heap. The
unchanged independent oracle preserves21 wire fields, all46 original origins,
every other semantic section and CFG; work metrics are evaluated separately.
The stable-build repeat completed in282.31s on the same Java21 runtime and
envelope; its complete dependencies/CFG bytes equal the first green run.
The local FAST executed1158 required methods with zero skips, then failed because
W3's exact compiled inventory lacked TextProfile's new java.util.Map$Entry edge.
Only that edge was added; no public descriptor or deny rule changed. W3 and all
subsequent architecture boundaries and the eleven original post-checks then
passed against the unchanged executable sources. This is reused test evidence
plus newly executed repaired boundaries, not a new complete local FAST PASS.
The final commit's complete remote FAST remains required.
Canonical identity maps and the location/eligibility associations remain resident:
this removes duplicate identity payloads, not the AS-W09 external-index obligation.

## Native declaration directory and cold nominal associations

The native route now owns two paged ordinal tapes: original AIR declaration
order and complete canonical-key lookup order. Its temporary ordered index closes
after construction. No per-Object typed ID, address record or decoded body is
retained in the structural declaration directory. Typed lookup compares complete
canonical identities, not a hash, display name or ordinal; values remain cold.
The backend-neutral DeclarationInventory port keeps the resident compatibility
path explicit. Construction begins only after the original initial CFG/policy
checks, preserving rejection precedence.

Direct Cell associations and scalar subject/text membership are cold views of
that directory. Existing Cell/Location data remain shared, and all original
declaration, binding, precision, demand and effect checks still execute. Physical
root traversal reuses the required alias keys in AIR insertion order instead of
projecting another complete identity collection. The physical alias graph and
resolutions are still resident: this does not complete AS-W09.

The nominal cold-read law now checks actual stored references rather than requiring
instance identity between two lazy projections. Its audit distinguishes equal
but distinct IDs and does not enumerate cold custom views: structural/scalar roots
retain zero unused typed ObjectIds; physical roots retain one instance per alias.
Full bodies, identity equality, original order, immutable views and closed-owner
rejection remain asserted. An injected failure after a real lookup-tape append
preserves the primary error, leaves AIR open and releases partial pages/reservations
on program closure. No partial catalogue is published.

Focused kernel38, values52, dependencies18, adapters17 and CLI27 methods passed
with zero skips. The unchanged406,942,781-byte Cell case completed on Java21 with
128MiB heap and the original eight-minute deadline in314.40s. The independent
oracle preserves all21 fields,46 original origins and CFG; the entire output also
matches the previous qualified bytes, including metrics. This finite run does
not establish universal throughput or complete managed residency. These runtime
results used producer6d85657, not the subsequent producer650a546.

The first complete FAST executed1158 required methods without skips, then failed
at the exact W1D compiled inventory for the new native directory. The original
refresh helper repaired only inventory metadata; unchanged deny rules, W1D,
storage and the eleven original post-checks subsequently passed. This is not a
second complete FAST PASS. The same407,297,668-byte physical case on Java21
then reached the original eight-minute application TIME limit, exit7, wall487.25s
including cleanup; neither output was published. The DECODE resource label also
covers later cold reads and does not identify the initial decoder as the cause.

Separate sampling identified repeated immutable canonical TEXT_PAIR probes.
Producer650a546 reuses16 fixed slots only when both complete child keys match,
within its unchanged4096-byte control claim. It retains no String or typed-ID
history and skips no source characters or namespace checks. Its complete local
FAST and both remote checks passed; consumer backend/admission/regional23 methods
passed. A fresh complete consumer FAST passed1158 required methods with zero
skips, all compiled inventories and the eleven original post-checks in755.266s.
The unchanged407,297,668-byte physical public CLI case completed on Java21 in
214.09s; its independent oracle preserves21 fields,83 origins and CFG. The two
metric differences from the small seed reference are2052 objects resolved and
4104 binding visits, reflecting all2048 additional declarations. Entire outputs
match the previous qualified large physical product, including metrics.
The unchanged406,942,781-byte Cell public case completed in199.56s; its oracle
preserves21 fields,46 origins and CFG, and entire outputs match the earlier large
Cell product. Both use128MiB heap, the original eight-minute application deadline
and no profiling. Producer/consumer runtime classes and all14 changed inputs
were verified unchanged after execution. These are individual finite runs, not
three-repeat medians or universal throughput bounds. The earlier physical TIME
and inventory FAIL remain historical evidence; consumer remote FAST still needs
the final commit. Original quotas/oracles are unchanged; AS-W09/W10 remain PARTIAL.

## Native storage catalogue and remaining broad physical effects

The structural storage catalogue now borrows source addresses, canonical keys and
lookup ordinals from three paged tapes. Keys are read once for sorting; binary
lookup compares complete canonical identities before decoding one cold body.
Every original ownership, duplicate and CellBinding check still executes in AIR
order. Physical StorageIndex bases share this immutable catalogue instead of
retaining another decoded inventory. The resident route remains explicit.

The existing nominal law retains its128 Object/display cases and adds128 private
Cells with4096-character identities. It checks repeated cold reads, full namespace,
AIR order, immutable views, zero stored unused StorageIds and closed-owner rejection.
A real third-tape append failure preserves the primary error and releases all
partial pages/reservations without closing AIR. Focused kernel24, dependencies10,
backend13 and order/cleanup2 methods passed without skips.

A new135,205,355-byte input with2048 private Cell storages and65536-character
identities exhausted128MiB in IndexBuilder on the frozen90c0375/650a546 build.
The native catalogue completes that unchanged public Java21 CLI case in39.36s
under the original eight-minute deadline. The independent oracle preserves all21
semantic fields,46 origins and CFG; whole bytes equal the original seed product.
The experimental-physical option also completes in39.71s but selects Scalar, not
Region; it is not a physical Region qualification.

The corresponding135,230,514-byte Region input reaches later physical preparation,
then exhausts heap in StorageIndex.select/StatementEffects while expanding broad
AllMemory effects into full candidate headers. That47.57s OOM is still a delivery
blocker, with no product published; neither quotas nor effects were narrowed.
Alias/resolution, broad effects, partition/domain and other variable stores remain
resident. These finite Cell results do not close AS-W09/W10.

Remote90c0375 had W3 inventory drift and a separate cancellation, not PASS.
Isolated Java21/25 compilation with equal release/debug options identifies only
an AbstractMap StackMap edge from TextProfile's constructor ternary. An equivalent
if avoids the edge; both compiler dependency sets now match, and21 logical family
methods passed. No deny rule or compiler-normalization exemption was introduced.
The first coherent storage/compatibility FAST failed at the unchanged256-Unit
general planner fixture: redundant cold alias/storage reads exhausted the original
one-billion WORK quota. Its failure is preserved, not relabeled as qualification.
Scalar relation preparation now receives the canonical Cell cardinality already
computed by its owner; native alias lookup borrows the admitted Cell association
without decoding the same immutable base again. Every original declaration and
storage-domain check still runs. The existing sparse-demand law rejects catalogue
enumeration during this preparation (focused RED), then passes with the fix.
The21 logical family methods and three native integration/cleanup methods pass;
the unchanged256-Unit case preserves candidates/supports with727,420,314 WORK.

The stabilized Java21 public Cell pressure case completes in39.62s at128MiB,
with all21 semantic fields,46 origins and CFG independently preserved. Its entire
outputs match the frozen seed reference. Runtime classes and changed inputs remain
identical before/after that execution. The corrected complete FAST passes all1158
methods without failures/errors/skips, architectural boundaries and post-checks
in746.582s. The frozen13 inputs are unchanged after the gate. All producer/consumer
runtime class bytes also match the pre-CLI manifest after the clean rebuild, so
the finite public CLI qualification applies to the actual stabilized FAST build.
Remote CI for the new commit is still pending;90c0375 is not a remote PASS.
The broad Region OOM remains unresolved; this is not AS-W09/W10 completion.

## Broad storage effects and native finite partitions

The subsequent wave keeps admitted AllMemory selections and write targets cold.
Their complete AIR-order views reconstruct the original public records on access;
only the private admitted views bypass defensive copying. Direct targets exclude
proved-empty Regions, while environment remainder targets still include every
base. MAY strength, source applicability, reasons and distinct direct/remainder
occurrences are unchanged. Narrow and general scopes retain their existing route.

An owner-local target descriptor links the same immutable storage inventory to
its checked source position. It is not a published AIR ID or a substitute for full
nominal identity. A descriptor from another inventory is rejected even when both
programmes borrow the same AIR. Complete identity lookup remains available at
public boundaries; internal broad updates need not decode/reintern long IDs.

Finite partition cuts, source positions, segment membership and canonical ranks
use managed primitive columns on the existing page runtime. Arbitrary BigInteger
cuts retain exact order and values; empty Regions and unknown tails keep their
original semantics. Public Segment/Location records still provide complete
headers. Physical plans and events retain write+target ordinal references, not
another header per target. Canonical ordering compares complete local-ID text,
never canonical-key numbers. Private occurrence maps preserve first-seen Write
order without repeatedly hashing the complete cold target inventory.

The original partition RED retained288 cold StorageId objects. Focused GREEN
asserts zero retained unused identity copies, exact complete candidates/targets/
segments, owner expiry and no cold identity-text reads for address/ordinal
projection. Additional manual tests cover257-bit finite cuts, adjacency, empty
Regions, an unknown tail and cleanup after a real nonzero partition-column write.
Kernel26, dependencies10, Regional40 and native consumer5 methods passed.

The unchanged135,230,514-byte Region pressure input (2048 private Regions with
65536-character IDs) previously failed in AllMemory and then StoragePartition.
An intermediate cold-plan run exhausted the original eight-minute budget and
returned7 without publishing products; that failure is preserved. Owner-bound
target addresses subsequently completed the physical CLI in210.80s with one
diagnostic thread dump. The final segment-projection run used Java21/Xmx128m,
no sampling and unchanged application quotas:112.35s, CPU114.75+3.52s,
RSS259524KiB, exit0. The independent frozen-reference oracle preserves all21
semantic fields,83 origins and CFG; both products are byte-identical to the
preceding green run. All eight runtime class trees and nine changed source files
were hash-checked after the run. The first coherent FAST passed1158 selected
methods but failed at a stale W5 compiled inventory; that failure is preserved.
The original W5 helper refreshed and verified the inventory without changing
its deny rules. The two new manual methods now belong to the explicit FAST
selection. Final complete FAST passes1160 methods without failures/errors/skips,
all architecture boundaries and post-checks in754.887s. All15 frozen inputs
remain unchanged, and all eight production class trees still match the final
112.35s physical CLI manifest after the clean rebuild. Thus that qualification
applies to the actual stabilized FAST runtime, not an earlier source version.

The published610891fe checkpoint has both remote FAST checks successful. The
producer/workflow pin remains650a5466f497c27399b06e803e203b6da9a3cf13.
Group arrays, physical relations/states and other resident indexes still require
AS-W09 coverage. These finite physical results do not complete AS-W09/W10.
An additional unused UnknownBinding(AllMemory) object has a focused RED:16
private Regions with4096-character IDs retain16 complete cold StorageIds in
StatementEffects.openByBase. Admission succeeds; this is the next demonstrated
retention gap, not a reason to relabel the preceding finite pressure result.

## Native whole-memory aliases

An admitted open binding with no direct candidates and an AllMemory remainder
now borrows a single whole-catalogue relation. Its object source positions live
in a managed ordinal column; complete ObjectIds are decoded when a logical
target is actually emitted, without decoding display payloads. Interleaved
narrow aliases keep their original first-target/first-declaration order. Explicit
named destinations retain source applicability and their first occurrence;
empty Region ranges do not invent an overlap. Resident compatibility keeps its
original index. Narrow native indexes are not yet claimed as fully spilled.

Unknown named destinations with no direct candidates likewise borrow their
whole remainder targets, including empty environment targets. Complete public
records, MAY strength and reasons remain unchanged. A manual native test first
retained80 unused storage identities across three aliases and two destinations;
it now retains zero. Every Statement/Candidate/Target/Write and finite Segment
matches the resident reference, alongside independent ordered logical-target
expectations. A real nonzero column-write failure preserves its original
exception, releases partial pages/leases immediately and leaves input owners
usable. Kernel26, dependencies10, Regional40 and native consumer7 methods pass.

The fresh real-CLI fixture preserves the original executable seed and83 origins,
adding one unused open object/uncertainty and2048 private Regions with65536-char
IDs:135,233,275 bytes, SHA256
`d4a2c047ca35baa973c133c6074ba5069cd0f29c1243e134aa955a5c27950258`.
Java21/Xmx128m, unchanged application quotas and eight-minute deadline, no
sampling:109.25s, CPU111.77+3.64s, RSS300760KiB, exit0. The independent frozen
modified-seed oracle preserves21 semantic fields,83 complete origins and CFG.
Sources frozen before execution and1232 classfiles hashed during/after it stay
identical. The initial old-path manifest missed the one new WholeAliases class;
it is preserved separately, never described as a complete runtime freeze.

Open AlternativesBinding is not covered by these admitted witnesses: the JSON
profile refuses it, and complete snapshot admission reports
VALIDATION_LIMIT/ASSOCIATION_DOMAIN_BOUND. Neither boundary was weakened.
Complete FAST for this additional wave passes1162 required methods with zero
failures/errors/skips, architecture boundaries and post-checks in780.571s.
All frozen inputs and1232 production classfiles match after the clean rebuild;
the109.25s physical qualification applies to this stabilized runtime.
Both remote FAST jobs of published
99db6808 were canceled at the existing15-minute job limit; local1160 FAST
and the preceding physical qualification remain green, not remote success.
Group arrays, states and other cardinality indexes remain AS-W09/W10 PARTIAL.

## Native regional correlation group directories

Native physical preparation now uses the inventory's owned primitive columns
for union-find, group members, offsets, base-to-group lookup and group sizes.
Compressed roots are externally sorted with stable canonical-rank ties, preserving
the previous minimum-root/component order. Each nested row is an immutable,
owner-checked borrowed view. States borrow group sizes rather than copying them.
The resident compatibility path remains explicit and unchanged semantically.
Connectivity visits existing batches directly, without copying all plans again.

A new RED/GREEN test specifies transitive a/b/c connectivity, UTF-16 canonical
ordering, empty Regions, original AIR segment ordinals and expired owner access.
Another injects failure after a real positive group offset write and a cleanup
failure: partial directory columns close, the original exception remains primary,
the checked input stays usable, and all resource pools return to zero on teardown.
Regional family47 and final focused consumer4 pass. A514-base native countercase
now retains no group arrays; all three directories are BorrowedList. This is a
structural retention assertion, not a measurement of the complete JVM heap.

The unchanged135,233,275-byte physical AIR completes through the real public
Java21/Xmx128m CLI in115.81s, CPU118.38+3.72s, RSS298760KiB, exit0. The independent
frozen modified-seed oracle confirms21 semantic fields,83 full origins and CFG
parity. All source/input and1233-class runtime hashes were frozen before execution
and match afterwards. The first attempt failed while decoding with Disk quota
exceeded in/tmp; its exit3 and diagnostic remain preserved, never counted as PASS.
The successful attempt uses a fresh workspace-disk java.io.tmpdir only; original
application quotas and the eight-minute deadline are unchanged. Original-environment
FAST passes1164 required methods, architecture and all post-checks in733.661s,
with zero failures/errors/skips. All frozen inputs and1233 runtime classfiles match
after its clean rebuild. A preceding JAVA_TOOL_OPTIONS run failed two empty-stderr
CLI contracts because the JVM prints that variable's value; it remains a failed
environmental attempt, and the tests were not relaxed for the successful rerun.

This does not complete AS-W09/W10: plans, states, relations and narrow indexes
still require coverage. A subsequent executed16-Cell countercase additionally
retains80 cold identities in Regional traces despite zero during preparation;
that RED is preserved separately and is the next integrated blocker.

## Borrowed regional evidence locations

Private Regional traces now keep inventory-owned base ordinals and exact ranges
for observations, original producers and capture contributions. They reconstruct
complete headers only at the public evidence boundary. Owner identity is checked
in equality; hashing never hashes the inventory Map or reads all its cold rows.
The resident compatibility path still owns its explicit Location records.
StoragePartition.address exposes a header-free segment-to-base projection with
checked bounds and source lifetime, without inventing an owner for resident data.

The admitted real paged16-Cell countercase changes80 retained complete identities
after execution to zero. A separate minimal CALL/AllMemory test changes48 to zero
and compares complete public facts to a resident reference, independently fixing
the preserved PROGA candidate, open remainder and Invoke unknown-writer evidence.
Segment addresses also preserve every complete ID/source ordinal without cold
header reads. Kernel26, Regional47, dependencies10 and focused consumer3 pass.
Initial test scaffolding errors remain preserved: a resident snapshot legitimately
owned16 original IDs; the resident overload requires Entries, and Origin.unavailable
is outside the JSON profile. The final test uses actual paged input and a supported
synthetic Written origin. No validator, fixture dimension or assertion was weakened.

The fresh AIR preserves all original executables/origins, adding2048 private TEXT
Cells with65536-char IDs:135,250,994 bytes, SHA256
`a778779e5ec1cc22d3a3c1a7d64006122eae321ec9bb7a895dab977b486e8d87`.
The real Java21/Xmx128m physical CLI, unchanged quotas/eight-minute deadline and
workspace-disk temporary directory, completes in206.39s, CPU207.43+6.26s,
RSS281620KiB, exit0. The independent pre-fix frozen16-Cell product confirms all21
semantic fields,83 complete origins and CFG parity; dependencies59668 bytes.
Twelve inputs and1234 runtime classfiles were frozen before execution and match
afterwards. The generator's separate768MiB JVM creates the fixture only; it is not
the application run or a changed product budget. Original-environment FAST for
this delta PASS:1165 required methods,zero failures/errors/skips,architecture and
post-checks,741.779s. Seven frozen inputs and1234 runtime classfiles match after
the clean rebuild, linking the physical CLI result to the same executable content.
Plans, relation stores, states and other input-cardinality metadata still prevent
AS-W09/W10 completion. No global qualification or fully spilled engine is claimed.
