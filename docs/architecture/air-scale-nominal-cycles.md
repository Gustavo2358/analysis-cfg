# Managed exact nominal cycle relations

AS-W02 / IN_PROGRESS; producer2434139e9052213ddc8d128db11f6722dc1979f4,
AIR authority4f09e8b1b496bf8de2e0fb62532e7aa0b97b9c6e.
SnapshotNominalCycles computes exact positive-degree Kahn residuals for Origin
inputs, Unit containing relations and direct aliases. It reports cycle dependents
as well as cycle members; missing targets and alternative alias grounding are
separate validation obligations. No full checked admission result is produced.

PagedSnapshotCycleStorage borrows the shared PageStore and owns an exact ordered
complete-key -> dense-row index, two-word degree/head rows, two-word reverse
occurrences (child key, earlier occurrence), and a dense primitive FIFO. Complete
64-bit keys never become array addresses. Every repeated edge increments degree;
all definitions/links precede scheduling. Queue consumption zeroes cells and resets
empty addresses. Reverse occurrence consumption zeroes cells and detaches each
parent head; empty leaves/directories are reclaimed. Required identity state
persists until owner close; no history grows across empty queue epochs. Checked
long arithmetic/address checks fail operationally rather than wrapping or truncating.

Control is reserved256B; index and three primitive arrays reserve their fixed
scratch before use. All payload, directories, indices and cache pages share the
VALIDATION ledger. For N selected nodes/E actual occurrences/D declarations,
producer primitive work is O(D+N+E); ordered index comparisons/page I/O are separate.
A one-page file cache tests pressure/parity, not production throughput. Operational
failures poison the scratch owner; cleanup releases owned state without requiring
remaining analysis work or closing the borrowed store. No counts after interruption.

Four deliberately registered adapter laws pass with selected reactor neighbors.
Memory/file4096-node chains preserve8190 repeated occurrences; file heap peak15392B
and temporary1308992B under65536B, with64 empty queue epochs and identical answers.
After propagation, only the required identity index retains pages; queue, degrees,
heads and reverse payload are reclaimed. A complete typed input copied to the
builder matches manual four-Origin residuals across resident/paged input; combined
input/key/declaration/cycle owners peak68064B under131072B. The initial caller-owned
Publication/test copy is outside the managed decode claim; this fixture deliberately
has reference/cycle errors and is not declared valid AIR.

Constructor quota denial and every primitive work interruption in a measured
small define/link/queue/cursor/decrement sequence leave zero owned pages/leases.
Invalid zero keys, duplicate declaration and degree underflow are rejected. The
isolated compiled mutation replacing exact degree increment with1 loses duplicate
occurrences and fails the manual degree2 oracle; untouched original passes.
Protocol and overflow checks do not establish broad I/O-fault/deadline qualification.
W5 source/classfile/API inventories add this adapter; deny rules remain unchanged.
Full references/visibility/grounding/types/premises/capabilities/operations,
CheckedSnapshot, incremental codec, managed CLI and global AS-W00–W10 remain pending.

Expanded mandatory FAST passed1044 required methods with zero failures/errors/skips
in336.455s, including W1–W5/W1D boundaries and all selected wire/contract oracles.
