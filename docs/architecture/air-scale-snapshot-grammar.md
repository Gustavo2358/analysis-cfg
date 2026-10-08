# Managed ordered typed graph grammar

AS-W02 / IN_PROGRESS. Producer ff49c9a3d444b8d7292c8cb3cc30dfc5781d0537
adds the official SnapshotGraphWalk. It borrows the immutable input and transfers
one PagedSnapshotGraphStorage owner. This vertical validates typed storage grammar
and drives later local rules; it does not produce full structural admission.

The backend owns a four-word task column (source node, element context, depth,
exit marker), an active source ancestry index, a canonical two-word source/context
arena and a completed-context index. Source addresses are literal words, not child
references into that arena. Exact context equality never relies on hashes. All
frontier/memo/ancestry payload and directories spill through the shared PageStore;
only funded fixed controls/staging remain resident. Run ownership of PageStore is
retained. All accesses after operational failure/closure reject.

The stack pops clear all four words and reclaim empty pages. Addresses follow
current stack size, not the count of historical tasks. Reversing a newly appended
segment preserves declared preorder without retaining an ancestor cursor or a list
of Java rows. Ancestry markers are removed at exit; completed context keys remain
required memo state until this snapshot walk ends. B-tree/canonical lookup costs
remain O(log keys); linear primitive graph requests do not imply linear I/O.

Independent memory/file tests preserve4096 sparse64-bit task rows, row fields,
ordered segment reversal, distinct element contexts and64 drained/refilled epochs
with no live-page or temporary-capacity growth. A builder-written complete typed
Publication grammar includes512 nested intersections, shared leaf/subject nodes
and explicit proof payload. Both backends preserve traversal counts and the
manual512-intersection oracle under65536 managed heap bytes; no scratch cursor is
live at visitor invocation. The fixture qualifies grammar only: it does not
establish references, domains or premise validity. The small original Publication
and test-only copy adapter are caller-owned; no managed corporate decoder claim.

Constructor denials and spent WORK abort without Counts or semantic success;
owned tables/leases close and borrowed run stores remain available. Initial RED
was absent adapter/compile only. Focused21 adapter methods plus reactor neighbors
passed; complete FAST/CLI qualification is recorded at the final checkpoint.
Full constructor-local/reference/type/domain/capability/operation checks,
CheckedSnapshot, incremental codec and managed CLI admission remain pending.

The isolated frontier run measured28080B managed heap peak and376032B temporary
storage for4096 rows. This is accounted storage, not a JVM retained-heap census.

Final FAST passed1035 required methods, zero failures/errors/skips,312.714s.
The full builder/grammar fixture observed684 node contexts/1199 edges,512
intersections,49328B managed heap peak and482272B temporary storage remaining
while the borrowed input was still open. All pages/accounting close at run teardown.
