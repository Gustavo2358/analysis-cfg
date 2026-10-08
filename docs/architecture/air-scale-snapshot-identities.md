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

Final atom checkpoint FAST: 1032 methods, zero failures/errors/skips,307.611s,
including producer resolution, wire oracles and compiled boundaries.
