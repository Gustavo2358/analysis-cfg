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

Union uses explicit frames over node pairs, with memoized results and the
same identity/empty shortcuts. Update, projection and restriction use a
memoized post-order node walk with explicit iterators; restriction prunes excluded
edges before traversing them. Enumeration uses an explicit DFS cursor stack
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

## GREEN and handoff

R2-REV-F1 is remediated; READY FOR HUMAN RE-REVIEW in the same Draft PR.
RED commit: `9ada4e3`. Fixed production: `20caab18b3ac9882491da171aeb6350ed090c998`.
Only FactorizedAlternatives changed in production. The compiled W3 inventory
adds the two private frame classes and their standard-library dependencies; no
public API, Maven dependency or architecture direction changes.

[Machine-readable evidence](ep-r2/rev-f1-validation.json) records source hashes,
raw log hashes, per-process outcomes and current metrics. The selected product
run happened before the production commit with those exact source hashes; FAST
and the five deep CLI invocations used the clean fixed production commit.

- All five standalone operations pass at 1024, 2048, 8192 and 32768 levels:
  20 separate JVMs, -Xmx256m, default stack. Update/union/restriction keep the
  exact linear structure; projection returns its requested component and
  enumeration returns the one complete tuple. No thresholds become caps.
- The independent finite-relation oracle passes 1000 scenarios / 7000 checks,
  including empty relations/tuples, skipped levels, colliding labels and weak
  updates. Six depth JUnit methods also cover update/projection invoking union
  on long suffixes, with two correlated tuples.
- The AIR JSON → CFG → RD/RV → regional result vertical passes on 8192 segments.
  MUST seed: OLDPROG1 closed. After MAY and BEFORE overwrite: OLDPROG1 open.
  After MUST: only NEWPROG1 closed. Copied field: OLDPROG1 open, retaining seed
  support and source/destination intervals captured BEFORE copy, after the source
  has changed. Reversed query order produces byte-identical JSON.
- Its maximum state has 16384 edges / 16383 nodes, maximum component cardinality
  two; cumulative arena has 40963 nodes / 40966 edges. This is linear depth with
  few alternatives, not a claim of zero transient allocations.
- All five observations independently pass the real CLI with -Xmx256m and no
  -Xss override; their complete JSON observations equal the memory result.
  The independent Python wire validator passes both forms.
- 111 focused tests across 25 suites pass, including copy/FitText/provenance,
  composition, branch/loop convergence, strong/MAY controls and EP-R1 laws.
- Eight selected dependency product verticals pass: H2, MUST, F1, F-order-012,
  F-order-210, G-7, I and E3. G-7/E3 still have maximum 316 state edges; I, 528.
  JSON, candidate support, honest remainder and BEFORE oracles pass. All six
  order permutations also remain covered in the Java cohort.
- Exactly one local final FAST passes in JDK 21: 501 Java methods, zero failures,
  errors or skips; Python checks and all compiled architecture boundaries pass
  (`fast.log`, 81.499 seconds observed). Eight new methods extend the original
  493-method cohort. No production changes followed this gate.

Reproduce after the normal pinned dependency build, on JDK 21:

```sh
mvn -pl analysis-adapters -am -Dtest=CfgPreflightTest,StorageRangeTest,FactorizedDepthTest,FactorizedAlternativesTest,RegionalAnalysisTest,NameInterpreterTest,EpR2DepthVerticalTest test
java -Xmx256m -cp analysis-values/target/classes:analysis-values/target/test-classes io.github.gustavo2358.analysis.values.FactorizedDepthTest update 32768
# Substitute restrict, union, project or selections for update; each runs separately.
python3 -B scripts/project/regional_result_wire.py analysis-adapters/target/ep-r2-depth/result.json
python3 -B scripts/harness/lean.py fast
```

The default FAST includes the new regressions. This remediation does not claim a
rerun of the old eleven policy mutants or all seventeen product cases; their
original results remain explicitly historical. The new oracle directly checks
all changed operations against finite collecting semantics. No other-repository
gates, CardDemo/CICS/FILE campaigns, broad corpus or confidential source were run.
The original six checkouts, including frozen FILE, retain their SHAs and clean
status. Pins remain unchanged.

ENTRY SEMANTICS: QUALIFIED. FACTORIZED VALUES DOMAIN: QUALIFIED, including the
new linear-depth obligation. SYNTHETIC INCIDENT CLASS: QUALIFIED.
REAL CASE: AWAITING MANUAL RE-RUN. After human review of these synthetics, request
a new manual real-program run recording only the already agreed non-sensitive
aggregate metrics. No merge or auto-merge has been performed.
