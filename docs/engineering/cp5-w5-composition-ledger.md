# CP5 W5 — composition and delivery ledger

W5 is authorized by the [human request](../work/evidence/WORK-CFG-028/wave-5/authorization.json).
W1–W4 Java sources and tests remain byte-exact; the parent POM only adds three modules.
`analysis-dataflow` receives AIR objects and composes the existing builder, session,
explicit registry, provider and planner. `analysis-adapters` owns AIR reading,
explicit result encoding, receipt and filesystem. `analysis-launcher` owns CLI
arguments, diagnostics and exits. No domain resolver or CP6 API is present.

## Selection and work

`DefaultValuePlan` visits the indexed Assign bucket once. Each ObjectPlace destination
is associated with its owning Sequence and actual terminator. The selected triple is
(EntryId, LabelId, ObjectId); repeated writes deduplicate. Queries are BEFORE(terminator)
for Return, Jump, Branch and Halt. All represented Entries are selected explicitly.
Orphan Sequences remain unreachable in the admitted graph. Source/display names are
never consulted. Zero writes produces COMPLETE with no analysis, observation or consumer;
no STABLE run or zero-cost execution is invented.

For A Assign sites, E Entries and Q distinct Entry/Sequence/destination triples,
selection is O(A + E + Q), excluding query sorting and W4 indexed site planning.
There is no Objects × Sequences scan. Detached capture visits declarations once and
retains Cell IDs only for requested subjects. W4 coalesces keys/queries; W2/W3 own
solve/replay. The in-memory analyze API returns the unchanged W4 PreparedAnalysisResult.
The prepare API additionally captures detached source inventory and counters.

## Delivery and retention

Encoding walks observations/supports incrementally with ordered reference lists,
small maps and an encoder buffer. No whole-output byte array is required. Input
bytes are materialized because the pinned AIR codec accepts byte arrays; the new
reader adds no local admission threshold. The Python parser materializes the JSON
tree and retains no input stream or original byte buffer.

The writer creates a same-directory temporary file, hashes successful UTF-8 writes,
closes the stream and requests ATOMIC_MOVE with replacement. COMPLETE follows successful
finalization. There is no non-atomic fallback. Unsupported atomic movement yields
FINALIZATION_FAILED. The guarantee is publication atomicity on supporting filesystems,
not fsync or power-loss durability. Controlled failure preserves an existing destination;
cleanup is best effort and cannot promote failure to success.

Neither prepared result nor receipt retains AIR, CFG, session, index, solver result,
planner, consumer callbacks, value universe or writer streams. External JVM diagnostics
compare AIR, prepared, delivered and released phases after full GC. Histograms measure
shallow class bytes; JFR samples allocations, not retained size or RSS. Cold processes
have zero warmup; elapsed time is an observation with no pass/fail threshold.

## Counters and quality

| Counters | Meaning |
| --- | --- |
| compositionRuns, cfgBuilds | Actual successful composition and BuildCfg invocation |
| airReads, airBytesObserved | Actual read and observed bytes, reported by reader/E2E |
| defaultPlanAssignVisits, defaultPlanDestinations, defaultPlanQueries | Indexed Assign visits, destination writes, distinct context queries |
| analysisRuns, queryRequests, uniqueQueries, factsEmitted | Existing runtime work and committed generic facts |
| resultBytesWritten | Bytes accepted by successful stream writes; throwing partial writes may leave uncounted temporary bytes |
| resultSha256Computed | Complete encoding/close followed by digest finalization |
| deliveryAttempts, deliveryComplete, deliveryFailures | External publication attempts/outcomes |
| encodingFailures, writeFailures, finalizationFailures, cleanupFailures | Actual classified boundary failures |

Unavailable phase metrics remain null. Wall-clock time stays outside the deterministic
payload. N/2N/4N records queries, facts, cardinality, open/closed outcomes and refusals
alongside elapsed time, logical retained objects and bytes. The shared-Cell case does
not claim N independent live Cell bindings; W3 S3 covers that dimension. One 36 MiB
literal plus support yields >64 MiB output without a W5 capacity ceiling.

Global performance composes W1–W4 S1–S9 and W5 S10/S14/S15/S16. The canonical S10
is not broad corpus qualification. The unchanged pinned AIR codec's 16 MiB ceiling
and validator limits remain **EXTERNAL SIZE-CAP DEBT**. A valid document plus padding
beyond that boundary exits 7 without a prepared result. W3-PERF-01 (growing candidate/
support unions may be quadratic) and W3-METRICS-01 (typed refusal) remain nonblocking.


## Post-CP5 RESOURCE_LIMIT compatibility

CP5 W1–W5 are APPROVED / MERGED at 4229ec1cfd9c1d9f9e851f3cabe6993b4d4ed9b8.
WORK-CFG-029 is a separate POST_CP5_COMPATIBILITY_REMEDIATION. Current air-java pin
17029898fd0ee8fabcaaae89f7260148633d4b12 separates operational RESOURCE_LIMIT from
specific VALIDATION_LIMIT and generic INCOMPLETE_VALIDATION. Historical CP5 evidence
above retains its original pins/defaults. The new default codec no longer has a
16 MiB ceiling; explicit operational budgets still fail without any semantic result.
See [the current preflight contract](../architecture/resource-limit-preflight.md).
