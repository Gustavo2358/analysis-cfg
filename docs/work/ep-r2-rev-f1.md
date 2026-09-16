# R2-REV-F1 — stack-independent DAG traversal

Review base: PR #39, `49472df2478ad38d1ff375766fddd714df66c7fa`.
The production FactorizedAlternatives blob is exactly the reviewed
`5bfc457706d6de1ae1b4163f679148dc7c56b5a4`. The review message includes the minimal
reproducer but no downloadable attachment URL; local evidence below reproduces
that witness directly, without claiming to have executed the external package.

Status at start: REQUEST CHANGES; domain depth blocks review readiness. Entry
admission and the width results of the original campaign are not reopened.
Scope: FactorizedAlternatives traversal, tests and current architecture inventory;
no producer, FILE, pins, AIR semantics, KillAuthority or second solver changes.

## Contract and design

For D segments and few paths, Java call-stack depth must not grow with D.
No increased -Xss, segment/candidate caps, error-to-empty conversion or dropped
evidence. Shared suffixes, canonical nodes, empty relation versus empty tuple,
strong/weak updates and exact relational algebra retain their existing meanings.

Union will use explicit frames over node pairs, with memoized results and the
same identity/empty shortcuts. Update, projection and restriction will use a
memoized post-order node walk with explicit iterators; restriction prunes excluded
edges before traversing them. Enumeration will use an explicit DFS cursor stack
and one mutable path, copying only the tuples the query actually returns.
Singleton construction and structural size already use iteration. A rebuild may
invoke iterative union; it must not re-enter the node traversal recursively.

Auxiliary heap is proportional to visited nodes/pairs plus frontier depth; the
call stack is independent of DAG depth. Enumeration still pays for its actual
output. This does not change the documented worst case of genuinely correlated
relations or large composite results. Linear depth and combinatorial width are
distinct dimensions; the earlier exponential-cost caveat does not excuse F1.

## Frozen RED

Temurin 21.0.12+1.1, release 21; standalone probes use -Xmx256m and default stack.
Each operation runs in a separate JVM. Depths 1024/2048/8192 are observations,
not production limits. Logs: `.harness-results/ep-r2/rev-f1/red-processes/`.

| Depth | update | restrict | union | project one component | selections |
| ---: | --- | --- | --- | --- | --- |
| 1024 | PASS | PASS | PASS | PASS | PASS |
| 2048 | StackOverflowError | StackOverflowError | StackOverflowError | PASS | StackOverflowError |
| 8192 | StackOverflowError | StackOverflowError | StackOverflowError | StackOverflowError | StackOverflowError |

Five JUnit regressions at 8192 also fail on the reviewed production, specifically
from StackOverflowError (`depth-red.log`, `depth-red.xml`). They require successful
exact results, not merely catching the failure. The algebra oracle passes 1000
independent small scenarios / 7000 comparisons on that same production.

The AIR vertical uses one Region with 8192 disjoint eight-byte field views, no
aliases/loops/initial conditions, four instructions and few alternatives. It
checks exact MUST writes, MAY preservation/remainder, BEFORE points, a copy that
survives a later source overwrite, source intervals/support/provenance, and JSON
query-order equality. Its first fixture attempt used Origin.unavailable, unsupported
by the codec; `vertical-red.log` is a setup failure, not the claimed depth RED.

## Validation frontier

C4, bounded to the existing relation engine. New evidence: depth RED/GREEN in
separate bounded-heap JVMs, finite relation algebra/canonicality, the deep AIR
vertical, affected values/copy/provenance/loop families, selected current product
cases and one repository FAST after stabilization. The reviewed baseline and
unchanged admission/pins are reused as historical evidence. No other repository
gate, broad corpus, real source, SYNC, FILE or complete LAB rerun is warranted.

The corrected IBM1047/FitText fixture passes AIR validation with no issues and
fails in the original DAG restrict traversal with StackOverflowError
(`vertical-red-04.log`, `vertical-red.xml`). Earlier ASCII-copy attempts
(`vertical-red-02/03.log`) had undischarged codec preconditions and are setup
failures, not depth evidence.

Final results and current handoff will be recorded after the fix and qualification.
