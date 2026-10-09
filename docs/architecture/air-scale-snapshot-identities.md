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
