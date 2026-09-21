# POSITIVE_MEMORY_TOPOLOGY — W3 regression

Status: CP5 integrated run complete. Inputs and raw outputs are retained locally under `.positive-memory-topology/evidence/w3/`; this document records observed oracles and deltas.

## Executable frontiers

- `FileIoEffectPlanTest`: SUCCESS `READ ... INTO` with KEPT LOCK retains `MUST_UNKNOWN` on INTO and FILE STATUS. Buffer remains MAY; END has no INTO. `WRITE FROM` with DYNAMIC ASSIGN retains `COPY_BYTES`. Both plans keep closed unknown-read/write bounds.
- `FileMemoryEffectsSuite`: a typed profile gap with intact owner/view/alias facts still lowers the proved strong FILE steps; contradictory owner or missing address proofs remain adverse controls.
- `CicsProgramControlSuite`: nominal target `Read` survives missing physical 8-byte area; no input target emits no computed Unknown name; LINK normal and XCTL nonreturn remain.
- `CicsFileControlSuite`: READ receiver, WRITE buffer, LENGTH open case and RESP/RESP2 returned writes remain. A missing host target retains SYSID read and normal continuation; missing typed target for a literal is invalid input. Adding 1 or 50 orthogonal option diagnostics preserves the executable target/effect/control shape.
- `CallCheckpointW1ATest`: CALL at end of paragraph has a known ordinary successor in the next paragraph. At end of unit it stays unavailable; lower retains a bounded remainder, not global control.
- `DependencyPreservationSuite`: unmaterialized nominal storage retains identity and no global alias. AIR roundtrip and invalid wire controls remain.

## Cohorts and equivalence rule

The W3 logical cohort contains the W2 integration cohort (31 cases covering CALL, FILE, CICS FILE, DB2_TABLE, COPYBOOK, DCLGEN, SQL_INCLUDE, IF/EVALUATE/PERFORM/GO TO, logical snapshots/overlays), six W1 CICS PROGRAM controls and three W3 witnesses (cross-paragraph CALL, KEPT LOCK READ and DYNAMIC ASSIGN WRITE). W2 output `.positive-memory-topology/evidence/w2/e2e-integration-final` is the comparison baseline for shared cases. Semantic comparison preserves candidates, support kind, producers, BEFORE, control, snapshots, resources and source dependencies. Republished IDs, origins and coverage are interpreted separately. New cases use source and lower contract oracles.

An adversarial source with undeclared `FILE(MISSING-FN)` was run separately and retained as raw evidence under `evidence/w3/logical-final2`. The producer rejected it at the existing Semantic Product invariant `incomplete binding must retain a nominal binding gap`; it produced no AIR or consumer result. The accepted W3 CICS target-gap oracle uses a typed in-memory SP mutation of a real host-target fixture, preserving the published COMMAREA/SYSID effects and normal control. No successful pipeline result is inferred from the invalid source.

The W3 physical cohort reuses the six W2 C/D witnesses and adds the two W3 FILE witnesses. Each C and D pair consumes the same `air.json` path and hash; C is the CLI default and D explicitly passes `--experimental-physical`. The IBM producer storage profile remains separate from the consumer flag.

## Results

The final logical cohort has **40/40 OBSERVED**. Every default consumer run reports `logicalOnlyMode=1`, `physicalGroupsApplied=0` and `physicalWritesApplied=0`. The W2 comparator reports 30 `PRESERVED`, one `CLASSIFIED_DELTA` and nine `PIPELINE_DELTA`; the latter nine are the six W1 CICS PROGRAM cases and three W3 cases absent from the W2 comparison baseline, all independently `OBSERVED` in W3. The comparator exits 1 for those one-sided inputs, not for a new pipeline failure.

The single shared delta is `r1--compute`: regenerated publication IDs, diagnostic/origin references and an edge serialization carrying those IDs. Its candidate values, timing and source support witnesses are preserved; `/edges` is identical after removing only nested `publication` fields. The comparator has no unknown path. Across the other 30 shared cases, candidates, supports, BEFORE, CFG, resources, snapshots and source dependencies are preserved without classification. Raw comparison: `evidence/w3/compare-w2-w3-final.json`.

The cross-paragraph CALL witness publishes `EXTERNAL` and `OLDPGM` at its two sites with no model remainder. The W3 KEPT LOCK READ and DYNAMIC ASSIGN WRITE witnesses preserve the known `TARGET` candidate. Their logical result retains a real open model remainder from the unproved address in the nonphysical profile; neither introduces global compensation.

The eight physical pairs are **8/8 OBSERVED**. C has `logicalOnlyMode=1` and zero physical groups/writes in every case. D has `experimentalPhysicalMode=1`, with positive physical groups/writes in all eight cases (1/1, 6/6, 2/2, 1/1, 1/1, 3/3, 8/8 and 5/5 respectively). The commands in each pair point to the same immutable AIR file; only D contains `--experimental-physical`. D preserves or refines the supported candidate set, including `TARGET` in both W3 FILE witnesses. Full metrics and AIR hashes are in `evidence/w3/physical-final/results.json`.

Control stress was rerun on the final stack: EVALUATE40 has 241 sequences and 80 branches; W3 control20 has 62 sequences and 20 branches. Both are `OBSERVED`, have zero Opaque/AllControl/AllMemory compensation and zero physical groups/writes by default. W3 control20 retains both CALL candidates. Raw outputs: `evidence/w3/control-final2`.

Producer FAST passed with 391 tests. Producer `qualification-local` passed its Maven 986 tests (one skipped) and reached the known `SOURCE_DEPENDENCY_OWNER_UNPROVED` source-normalizer blocker, proved preexisting in W1 A/B. Lower FAST and `qualification-local` passed with Java 21; the qualification included semantic, capacity and architecture suites. Analysis-cfg FAST and `W3LegitimateWorkTest` passed. No AIR/IR source change or W3 gate is required there. No unexplained semantic delta remains.

## W3-R1 final regression (after human architectural resolution)

The preceding section is the historical W3 result. W3-R1 runs from a frozen
runtime in local `evidence/w3-r1/runtime-final/` with the final producer,
lower, AIR and CFG classes. The earlier four `PIPELINE_FAILURE` physical cases
were caused by concurrent Maven clean of a live classpath; the raw failed run
is retained. On the frozen runtime all 40 logical and eight physical cases
are `OBSERVED`. DB2_TABLE, COPYBOOK, DCLGEN, SQL_INCLUDE, FILE, CICS, CALL and
altered control remain in the 40-case cohort.

| Oracle | Final observation |
|---|---|
| Local REDEFINES with/without missing COPY | SP publishes the same two complete logical TEXT views; physical proof may differ |
| Distinct names, shared storage | Missing-COPY A/B have distinct ObjectIds and one TEXT Cell; complete physical A/B share one Region |
| Strong overwrite | MOVE B=PROGA then A=PROGB reaches XCTL(B) with PROGB only |
| Literal mutation | Frontend test changes/removes/reorders MOVEs without changing `logicalExactViews` |
| Independent equal size / partial overlap | Frontend and lower do not create false full-Cell alias |
| Real MUST / MAY / snapshot | `W3R1CellSemanticsTest` preserves strong kill, weak alternative and copied old value |
| Bounded / broad scopes | `StatementEffectsTest` excludes R2 from R1 bound, includes intersection, preserves AllMemory32 and VisibleMemory |
| Circular / revisit | AIR validation rejects executable ungrounded one/two-object cycles; grounded and repeated union cases pass |
| Diagnostic isolation | 0/1/50 extra diagnostics yield identical logical and physical sites and metrics |
| Physical mode | C has zero applied groups/writes in all eight pairs; D has positive work in all eight; Cell-only unaugmented witness needs zero physical work in either mode |

The W3-to-W3-R1 classifier reports 37 `PRESERVED`, two `CLASSIFIED_DELTA`
cost/diagnostic cases and one `REQUIRES_REVIEW` by its generic policy. The last
case is manually classified `INTENTIONAL_POSITIVE_LOGICAL_CELL` with raw old/new
site fields and invariant assertions in local
`evidence/w3-r1/logical-unsupported-delta-classification.json`: CALL remains
without a candidate or supported write; a known Cell subject at BEFORE now
allows the query to report OPEN_TARGET and real model value remainder. The
source and interpretation remainders remain true. All eight physical site
projections (candidate names/support multiplicity, timing and remainders) are
identical to W3. `evidence/w3-r1/compare-final.json` retains the unmodified
classifier result; `compare-physical-final.json` retains the cost-only deltas.

## Final validator review regression

The first W3-R1 closeout checked executable cycles through `ObjectPlace` but
missed scopes used directly by `HavocMay`. The AIR contract suite now asserts
`INVALID_IR/I-13` for scope-only self and two-object cycles, plus cycles in
opaque reads/writes, foreign writes, `Unknown.remainingReads` and
`Choice.remainder`. It keeps a nominal unused cycle valid and accepts grounded
object scopes, explicit AllMemory/VisibleMemory and repeated resolved union
members. `StorageIndexTest.executableScopeOnlyCycleFailsAtAirPreflight`
checks the composed CFG boundary: no graph is built and the validator reports
I-13 before `StorageIndex` can select the invalid scope.

This AIR validator change alters rejection of invalid publications only. The
previous 40-case logical, eight same-AIR C/D, scale, and physical canary
evidence remains evidence from the prior code head; it has not been relabeled
as a new run. The new AIR pin is exercised by the affected FAST gates.

The later [validation-only sweep](W3-VALIDATION-SWEEP.md) found one exception
to evidence reuse: `source-dependencies-w3--composition` is invalid under the
final validator because five executable FILE write scopes reach only a
self-scoped UnknownBinding. Its prior query result is retained as historical
evidence, not accepted as a final-validator regression PASS. The other 47
frozen AIRs kept their prior validation status and issue count.

## FILE record grounding replacement

The invalid frozen AIR above remains a negative witness with five I-13 issues.
The final producer/lower regenerated only `source-dependencies-w3--composition`
from its original source, yielding AIR SHA-256
`d9a1cce6f9d1304168f2764636e55cdf8e2f23d10f6b31cd4ab0ad3848139319`.
The final validator accepts it; CFG builds; the existing source-dependency
oracle confirms COPYBOOK/DCLGEN supports, F/DD001 and `SUBA`. The separate
`file-dependencies/w2/composition.cbl` R/K witness is AIR SHA-256
`a19ea77c0c04caff339591f4ac8f0277482e60faeb467a92abe6f4dd0cc45b36`:
R/K have distinct ObjectIds, one TEXT Cell, and retain F/CLIENTDD,
READ/WRITE and `EOF`/`BAD`/`OK`. Split children, partial REDEFINES,
independent equal-sized roots, forged length and absent new-contract proof
remain negative controls. FILE MAY writes were not promoted.

The validation-only audit checks SHA and status for all 48 entries: 39 frozen
logical and eight frozen physical entries are byte-identical with identical
validator results; the one replacement is structurally valid with zero I-13.
No other E2E, physical C/D, indep80/160, solver or performance result was
relabeled as a new run. Default CLI metrics remain LOGICAL_ONLY with no
physical groups or writes.
