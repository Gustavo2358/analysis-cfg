# Logical-only — operational dependency discovery

id: logical-text-w1; status: IN_PROGRESS; scope: W1 logical text and explicit physical boundary. Historical W1 checkpoint, continued by [W2](logical-text-w2.md) in the same PR; no merge. By user decision on 2026-09-18, corporate execution is not a blocking W1 acceptance gate: synthetic source E2E proves the new property. The corporate ON/OFF observation is user-reported; no corporate rerun or nine-target measurement is claimed.

Dependency discovery defaults to `StorageAnalysisMode.LOGICAL_ONLY`. Logical/scalar source facts, existing CFG/reaching definitions and possible-values infrastructure supply supported candidates. Completeness is a separate claim. Physical/regional propagation is **EXPERIMENTAL / NOT PRODUCTION QUALIFIED**, default **OFF**, with automatic physical fallback **FORBIDDEN**. The limitation is operational scalability/convergence, not a finding that physical semantics are incorrect.

## Policy and use

`new DependencyAnalysis().prepare(publication)` selects logical-only. An explicit `new DependencyAnalysis(StorageAnalysisMode.EXPERIMENTAL_PHYSICAL)` selects the experimental policy. Provider keys include the policy through existing profile/precisionPolicy fields; caches cannot confuse modes. Both CALL and FILE receive the same policy. A scalar admission refusal may select the wider logical provider, never experimental propagation.

The `AnalysisDependencies` CLI accepts `<input.air.json> <output.dependencies.json>` for logical-only. Append `--experimental-physical` to opt in. The regional observation CLI has the same optional trailing flag. Programmatic RegionalAnalysis and RegionalValuesAnalysis also default to logical-only and accept an explicit mode. No environment switch or silent fallback exists.

Existing wire profile labels remain unchanged for reader compatibility; they describe the historical observation contract, not permission to execute physical propagation. The executed policy is explicit in metrics, which identify providers and expose `logicalOnlyMode`, `experimentalPhysicalMode`, `physicalGroupsApplied` and `physicalWritesApplied`, including FILE runs. The wire shape is unchanged.

## Boundary and meaning

RegionalValuesAnalysis retains its physical grouped loop, source-selection enumeration, projected alternatives, restrict/union and writes. Only explicit experimental mode prepares physical value plans/groups or executes that loop. Logical-only also skips physical reads during copy evaluation and observation; it propagates the existing logical map using the existing solver. Named exact writes, source literals, VALUE, Read/FitText and identity shared by the same CellBinding retain logical evidence. No byte offset alias expansion, byte images or encoding inference is added. Input structural/storage indexes and shared admission/partition validation remain; this is not whole-program slicing.

A logical-only wider-provider observation is open in the model with reason `PHYSICAL_PROPAGATION_DISABLED`. It may have supported candidates and no physical fragments. CALL publishes existing remainder flags and a PARTIAL analysis reason; FILE uses its existing model/source remainder reasons. Literal targets and independently proved scalar results are not gratuitously opened. Stable solver status means convergence, not complete physical modeling.

The missing guarantees are concrete: reconstruction of byte slices, aliases through overlapping views, byte reinterpretation/codecs, arbitrary physical write effects, and correlations/capture fragments of physical group copies. Physical-sensitive queries remain unknown/open. This policy does not recover REDEFINES, RENAMES or complete group-to-group MOVE; they belong to future logical extensions, not permission to reactivate physical propagation.

## W1 vertical and demand

The frontend's optional logical layout proof and lowering project parent literal writes into existing independent AIR leaf cells. `CallDependencyPlan` collects requested subjects before scalar admission; `TextProfile` closes demand over copy sources and prepares detailed scalar producers only for relevant cells. Proven-disjoint writes do not allocate detailed candidate state; uncertain effects/control remain. No new solver or storage engine exists.

10/100/1000 irrelevant-group source fixtures must retain one detailed scalar cell/write/producer and zero physical updates. All input/CFG traversal is still global. Wider logical-provider preparation still includes global nominal plans; the constant detailed-state measurement qualifies the admitted scalar route, not every possible program/provider. The synthetic 10,000-group experiment is **NOT_MEASURED_FOR_ANALYSIS** because AIR serialization exhausted upstream resources; it is not an analysis qualification failure and no serializer optimization is in scope.

Scalar copy is W1, not deferred: closed-control snapshot tests pass in both scalar and wider logical modes. The earlier source-level extra candidate also occurs in the baseline because lowered CALL control is open; it remains an explicit precision limitation, not evidence of a live alias. Group copies and overlays need separate future capture/correlation authority.

Tests distinguish default product candidates/open completeness from experimental physical laws. Existing physical-fragment/offset oracles explicitly opt in and retain their assertions. New boundary tests cover default zero physical work, explicit opt-in, unknown without fallback, candidate plus partial completeness, snapshot copies, same-cell identity, and CALL/CICS/FILE consumers. Evidence and classification are in `artefatos-e2e/logical-text-w1-20260918/pivot`.
