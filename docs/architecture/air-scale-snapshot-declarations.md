# Official primitive AIR declaration bridge

AS-W02 / IN_PROGRESS. Producer: air-java
`2c69580a7165203446fefb94602738e6d55ad771`, normative AIR
`4f09e8b1b496bf8de2e0fb62532e7aa0b97b9c6e`. This bridge is a shared-runtime
index foundation, not complete admission or managed CLI input.

PagedSnapshotDeclarationsStorage implements official SnapshotDeclarations.Storage.
A numeric canonical-key index points into dense primitive five-column declaration
rows: source node, complete identity handle, containing Unit, Sequence and owner.
All index/directory/row payload uses existing exact page stores. The FIFO stores
node/owner/depth triples without source-handle deduplication. Each consumed row is
zeroed/reclaimed; empty queue addresses reset immediately, including interleaved
producer/consumer walks. Historical visits do not remain roots or growing prefixes.

Fixed control state reserves256 resident bytes before child owners; each column/index
accounts its own fixed buffers/teardown. No Java row objects, arrays per declaration,
model IDs or maps are retained. The caller still owns PageStore/AnalysisResources.
Exact lookup currently needs ordered O(log D) comparisons; this is not a universal
linear I/O claim. Tiny one-page tests intentionally stress eviction, not throughput.

Storage freeze rejects definition/FIFO writes and new claims, retaining exact reads.
Operational writes/reads abort the port; failed official build/lookup closes its
owner and cannot return a partial index. Partial construction releases already-created
columns/indexes. Final store closure remains responsible for unreclaimable pages after
permanent backend failure. Borrowed input, identity catalogue and run backend have
separate lifetimes and stay available after a declaration owner closes.

Qualification laws cover4,096 declarations (raw row payload163,840 bytes) and8,192
FIFO occurrences with handles above int range, both resident and one128-byte-page
file backend under65,536 managed heap bytes. Duplicate definitions keep exact first
rows; repeated occurrences are preserved. Independent expected fields, dense ordinal
reads, FIFO order and64 drained/refilled epochs check that live pages/disk capacity do
not grow with history. A64-operand typed input copied to official paged AIR builds
70 expected declarations and correct operation ownership in both backends; caller-owned
Publication/test copy lies outside the managed input claim. Denied construction and
spent-WORK laws check phases, abort and clean accounting. Complete source decoding,
model-local/reference/type/domain/capability validation and CheckedSnapshot remain
pending, as do the global AS-W03–W10 objectives.
