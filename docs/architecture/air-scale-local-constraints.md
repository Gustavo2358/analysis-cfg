# Managed constructor-local AIR predicates

AS-W02 / IN_PROGRESS; immutable producer7e66aebd2a4c9bcd21d1dd3c0c75c1ef51a929a0,
normative AIR4f09e8b1b496bf8de2e0fb62532e7aa0b97b9c6e. The official
SnapshotLocalConstraints visitor composes with the ordered whole-graph walk,
borrows exact atom/identity keys and input, and transfers local scratch. It checks
constructor predicates, not complete Validator/reference/type/capability validity.

PagedSnapshotLocalStorage uses one managed canonical arena of three literal words:
(tag,source-list-handle,fact-kind or complete borrowed LabelId key). Fact kinds0/1/2
are labels/decimal aggregate/octet validity. Tags distinguish facts and memberships;
source addresses and keys from another owner are not child references in this arena.
One fixed staging array is reserved256B before allocation. All tuple payload and
ordered unique index pages share AnalysisResources/VALIDATION and the borrowed
PageStore. Required facts/memberships persist until close; no collection is allowed
while the local inspector retains them. Keys' owner must outlive the local inspector.

CanonicalTupleArena.find copies a supplied fixed tuple into existing staging and
queries the exact ordered unique index without allocating a row/root/key. Absent
queries retain no historical payload; intern still publishes only required rows.
This permits memo/membership lookups without growing state by query count. Exact
full-tuple comparison determines equality, with O(log U) ordered lookup comparisons
for U required rows. It is not an expected-hash or universal linear-I/O claim.

Focused26 adapter laws plus selected reactor neighbors passed49.439s. A4096-member
index and32768 distinct membership/fact misses preserve live pages, temporary
capacity and managed heap; memory/file answers agree. File peak14776B/temp1216032B
under65536B proves local index residency, not complete input admission. Lookup tests
also cover caller-buffer reuse, exact reference/retirement rules, root collection,
arity errors, miss-history bounds and operational WORK exhaustion. Constructor
quota denials and spent-WORK abort release owned indices, keep borrowed input/run
available, and close all ledger leases. Existing canonical DAG/retirement laws remain.

A builder-written typed graph with256 labels and1024 shared parameter occurrences
matches memory/file673contexts/1954edges. Input pages, atom index, graph scratch and
local membership share the ledger; file peak86792B/temp1122912B under131072B.
This combined success budget funds four owners; the smaller65536B is the isolated
local-index pressure budget, not a silently relaxed combined envelope. One-page
cache is an I/O pressure configuration, not a production throughput claim.
Repeated128 LabelValue checks allocate no pages. The graph intentionally qualifies
local grammar only: duplicate parameter positions/undeclared domain labels would
be checked by later reference/type/operation passes. It is not complete valid AIR.
Caller-owned initial Publication/test-only copy stays outside the managed decoder
claim; all paged owners themselves share the ledger and return pages/leases on close.

The adapter is explicitly registered in W5 source/classfile/descriptor inventories;
no deny rule changes. FAST deliberately adds its three laws and one generic arena
lookup law. Complete paged reference/visibility/cycle/type/domain/capability
admission, CheckedSnapshot, incremental official codec and production CLI remain
pending, as do other global AS-W00–W10 goals.

The first expanded FAST passed1040 methods with zero failures/errors/skips then
stopped at W2 compiled descriptor drift: the new kernel find API also requires
its exact W2 inventory entry. Only that descriptor is updated; no source set/deny
rule changes. This raw277.491s failure is preserved and the whole gate is rerun.
A separately compiled mutation returning correct miss answers while interning
misses retained1025 rows and failed the independent no-history oracle; unchanged
production lookup passed the same1024-miss law with zero leaks.

Final expanded FAST passed1040 required methods with zero failures/errors/skips
in337.230s, including exact W1–W5/W1D boundaries and all wire/contract oracles.
