# Managed shared location-grounding equations

AS-W02 / IN_PROGRESS; producer806c201c1d4a0e77295e89e206b2ffdedaaa5ed0,
AIR authority4f09e8b1b496bf8de2e0fb62532e7aa0b97b9c6e.
SnapshotGrounding collects positive Boolean equations through the iterative grammar
visitor and resolves aliases through the exact first-declaration index. Intrinsic
seeds propagate OR through aliases, alternatives/remainders and scopes once. A cycle
with a grounded alternative becomes true; unseeded cycles stay false. True/false
facts after saturation are reusable without root-dependent recursive replay.
This fact index does not publish executable owner diagnostics or checked admission.

PagedSnapshotGroundingStorage maps immutable source nodes to dense rows with an
exact ordered index. Expanded/true/queued bits occupy one primitive column; reverse
heads occupy another, reverse occurrences are two words (dependent source,next
occurrence), and FIFO addresses are dense. Source handles never become array
addresses or nominal identity keys. Links may introduce not-yet-expanded rows;
start requires complete expansion and all seeds queued. Queueing a seed cannot
seal collection. A scalar pending-change counter prevents a proven-but-unqueued
change from reaching start/freeze. Each true change queues once. Duplicate reverse
occurrences are preserved and consumed once; no resident row objects/maps grow.

Input, identity/declaration indices and relation share the run ledger. Fixed control
is reserved256B; index/four primitive columns reserve their fixed state before use.
Operational failure aborts and cleanup releases transferred state without requiring
remaining analysis WORK or closing borrowed input/PageStore. freeze releases all
heads/reverse/FIFO columns, including unpropagated negative-cycle edges; only the
exact index and truth/expansion flags remain. Frozen lookups never insert equations.
An uncollected-node query rejects and cannot become a false fact or grow the index.
Primitive producer work is linear in distinct grammar/equation nodes/edges plus
queries; ordered lookup comparisons and page I/O have their own costs.

Four adapter laws plus selected reactor neighbors pass56.842s (34adapter methods).
Memory/file4096equations8192repeated edges agree for both unseeded and seeded cycles.
Seeds are deliberately queued before later edges are added. Each case answers32768
frozen queries without page/temp/heap growth. File peak18464B under65536B; frozen
temporary1308672B(unseeded)/1308832B(seeded), query work2786504. All temporary graph
columns are closed after saturation. One-page cache is pressure/parity evidence,
not a throughput recommendation. Caller-owned Publication/test copy is outside
managed decoder claims. Combined builder input/keys/declarations/grammar scratch/
grounding owners agree on11equations/11edges/sixgrounded rows; file peak102512B and
temporary229952B under131072B. Deliberate alias cycles/missing cell references are
separately invalid AIR, not certified by this grounding fact test.

Constructor quota denials, every primitive interruption in a measured small full
collect/propagate/freeze/query sequence, unexpanded linked rows, missing equations
and unqueued seeds/changes reject with zero owned pages/leases. The actual unqueued
seed regression was RED before the pending-publication guard; corrected focused
suite passes. Compiled always-true and retained-topology mutations respectively
invent a grounded negative and retain dead relation pages; originals pass.
W5 source/classfile/descriptors and four FAST methods are explicit; no deny rules
change. Full reference/visibility/type/domain/premise/capability/operation checks,
executable I-13 diagnostic ordering, CheckedSnapshot/codec/managed CLI and remaining
AS-W00–W10 goals are pending. Global I/O/deadline/cancellation qualification is pending.

Mandatory expanded FAST passed1048 required methods with zero failures/errors/skips
in340.746s, exact W1–W5/W1D inventories/deny rules and wire/contract boundaries included.
