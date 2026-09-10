# Production result wire — 1.1.0

W5 implements analysis-dataflow-result and prepared-analysis-result 1.1.0; the
external analysis-delivery-receipt is 1.0.0. The [machine contract](../evals/cp5/result-contract.json)
and [nominal reader](../../scripts/project/result_wire.py) define closed shapes.
The [1.0 contract](../evals/cp5/history/result-contract.json),
[result witness](../evals/cp5/history/result-review.json) and
[phase witness](../evals/cp5/history/phase-review.json) remain historical NOT_EXECUTED
design examples. The [current snapshot](../evals/cp5/result-review.json) is from a
fresh CP4E production pipeline.

## Version decision

Draft 1.0 could not losslessly transport W3 candidate-specific supports, W4 analysis-only
outcomes, generic consumer facts and every current point form. Version 1.1 explicitly
adds candidateSupports, analysisReason, prepared analyses, planningEpoch and statistics,
plus generic observation references. It includes Entry/Outcome points, full operand
owners and source inventory independently from per-observation W3 remainders. Reflection,
Java class names, dense ordinals and enum toString do not define the wire.

## Prepared results and identity

The envelope contains schema/version, caller-supplied nonblank stable resultId, full
publicationId, planningEpoch, analyses, batches, consumerPlan, consumers,
preparationStatus, partialPolicy and statistics. partialPolicy is
EXPLICIT_PARTIAL_BY_DEPENDENCY. Each batch has a stable ID, ValueFact@1 projection and
analysis result. A generic fact has kind ObservedValueFact, full sequenceId,
observationBatchId and query (point/objectId). Its value is a resolvable observation
reference, with no interpretation as a program/file/database dependency.

Each run/batch has a complete AnalysisKey (implementation/version, profile, direction,
precision policy, semantic options and Entry), Publication/Unit/Entry identities,
executionStatus, analysisReason, modelScope=KNOWN_GRAPH_ENTRY, sourceScope, observations,
statistics and completion. Source scope carries Publication/Unit inventory and Entry
uncertainties, with remainderPolicy=PER_OBSERVATION_W3. The provider's per-subject
source remainder remains authoritative; PARTIAL cannot become exact from a singleton.

IDs contain domain/localId and Publication owner, plus Unit where required. Operand
IDs carry a complete OperationId or EntryId owner. Storage, Origin, Premise and
Uncertainty IDs are Publication-owned. Equal local IDs with distinct owners stay distinct.

## Observations and phases

VALUE retains reachability, all candidates, modelValueRemainder, sourceUnknownRemainder
and their OR as effectiveUnknownRemainder. Each candidate has producers, each with
evidence (Assign OperationId or initial-condition OperandId), OriginId and PremiseIds.
Aggregate evidence/premises/provenance remain available. pathWitness=NOT_PROVIDED:
producer evidence is abstract support, not a concrete path claim.

UNSUPPORTED_POINT has a canonical nonnull queryReason and null value, reachability,
remainders and precision. It stays in a COMPLETE batch without making the run
UNSUPPORTED. UNREACHABLE_IN_MODEL uses value=null, never empty closed Candidates.
A reachable value cannot be empty and closed.

Admission is COMPLETE/REJECTED, analysis STABLE/NOT_STARTED, observations and consumers
COMPLETE/FAILED/NOT_STARTED. A failed batch is empty and atomic; only dependent
consumers are blocked. Independent successes survive. Failed consumers publish no
partial facts. A batch NOT_STARTED preserves DEPENDENCY_UNAVAILABLE; the analysis-only
view has no batch reason. Delivery success cannot promote an INCOMPLETE prepared result.

## Encoding and delivery

UTF-8 preserves whitespace and Unicode normalization form. Unpaired surrogates fail
encoding. Object fields sort by Java String order; arrays use explicit full-ID,
AnalysisKey, point, candidate and producer ordering. Generic facts sort by Sequence,
batch, point and subject. Counters use integral decimal values; unavailable fields
are explicit null. Encoding ends in exactly one LF. No clock, UUID, process identity
or duration enters the deterministic payload.

Encoding streams to a temporary file in the destination directory while hashing exact
accepted bytes. COMPLETE requires successful encoding, close and atomic replacement.
There is no non-atomic fallback, retry engine, fsync guarantee or output/query/fact/
consumer/candidate ceiling. Cleanup failure is counted and cannot certify delivery.

The external receipt contains schema/version, resultId, resultSha256, absolute normalized
destination, status and reason. COMPLETE carries the exact final SHA-256 and null
reason. FAILED uses ENCODING_FAILED, WRITE_FAILED or FINALIZATION_FAILED. Hash is null
until complete encoding and close; finalization failure can retain that complete hash.
The reader verifies ID, hash and destination correlation. Receipt never enters payload.

## CLI

Run io.github.gustavo2358.analysis.launcher.AnalysisDataflow with:

```text
<input.air.json> <output.result.json> --result-id <stable-id>
```

Use the six production modules plus pinned air-java/air-json dependencies on the
classpath. Stdout is one external receipt when delivery is attempted; expected
diagnostics use stderr without stacktraces. Exit codes are 0 complete; 2 usage;
3 input transport/version/I/O; 4 invalid AIR/unsupported profile; 5 execution or
preparation failure; 6 output/delivery; 7 pinned upstream size/validation debt.
Resource exhaustion is never semantic UNSUPPORTED or source-open coverage.

The independent reader is invoked with:

```text
python3 -B scripts/project/result_wire.py result.json --receipt receipt.json
```

Legacy CFG arguments, exits, writer/reader behavior and Java bytes remain unchanged.
The new reader has no local pre-read size check, but pinned AIR codec/validator bounds
remain **EXTERNAL SIZE-CAP DEBT**. This is not proof of an unbounded file route.
See the [ledger](../engineering/cp5-w5-composition-ledger.md) and
[W5 evidence](../work/evidence/WORK-CFG-028/wave-5/validation.md).
