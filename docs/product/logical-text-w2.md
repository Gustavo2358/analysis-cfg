# Logical text W2

Status: IN_PROGRESS until human review/merge. W1+W2 local qualification complete; exact-head remote Fast checks are attached to the PRs. Continues frontend #55, lower #31, CFG #42; air-java #20 supplies existing expression transport. No merge. C0 is qualified. Corporate NOT REEXECUTED / NOT AN ACCEPTANCE GATE / NO NEW NINE-TARGET CLAIM.

## C1 decision before implementation

A shared Cell for identical ranges is correct but cannot express unequal ranges. Static boundary cells express partial content but the existing per-cell value join loses cross-cell correlation: concatenating {PROG, MODU} and {0001, 0002} invents combinations. Therefore boundary-only cells are not the selected mutable representation.

Choose composition by existing AIR expressions over one authoritative logical TEXT Cell per admitted source family. Each named view has a query projection Cell. A view write becomes a simultaneous expression over the old root: prefix slice + fitted source snapshot + suffix slice. Then named views are refreshed by slices of the new root, contiguously in the same source statement sequence. FILLER occupies root positions. Group copy reads the source sequence, fits the destination extent, and projects its new views; never child-to-child copying. Overlapping copies may be conservatively refused.

Existing AIR Read/Assign/FitText/SliceText/Binary(CONCAT), Cells, disjointness premises, CFG and PossibleValues solver are reused. No AIR schema change, physical Region, codec, MAY alias expansion or RegionalAlternatives work. Source layout/REDEFINES components and RENAMES endpoint proofs remain frontend-owned. Lower validates published proof rather than parsing COBOL.

The current scalar domain only interns complete strings. Extend that value carrier with finite partially known character sequences: unknown positions have no invented Unicode character and never materialize as dependency text. Projection of a wholly known interval can produce an ordinary TextValue even if other intervals remain unknown. Root alternatives remain whole sequences through joins, preserving within-family correlation. This is text expression evaluation in the existing scalar solver, not a new storage engine/lattice of physical regions.

Do not enumerate unrelated variable inputs into Cartesian products. Expression admission/evaluation must preserve same-input identity, and unsupported multi-input correlation remains open. All finite extents come from published source facts and explicit AIR fit/slice operands; no arbitrary budget or truncation of candidate sets.

Demand closure includes every read in admitted expressions, including old destination root and copy source. It prepares detailed values only for requested cells and transitive sources; global input/CFG scans remain. Unknown effects must reach root and projections or conservatively preserve open completeness; no unsupported write becomes a no-op.

Required challenges: W1 partial child, exact/unequal overlays both ways, RENAMES/FILLER/invalid endpoints, padding/truncation, snapshot and overlap refusal, branch correlation, unknown interference, demand scale. Physical tests retain explicit opt-in and their original oracles.

## Source CALL frontier and evidence

The source E2E RED for snapshot produced ABCD and ZZZD; the correlation RED produced all four combinations. The root expression tests kept snapshots/correlation, but legacy lowering mapped a no-handler CALL to AllControl, inventing backward jumps into arbitrary MOVE statements. For a CALL whose surface proves all handlers absent and publishes its normal successor, lower now bounds the unknown outcomes to nonlocal UnitControl (labels=false). Normal return keeps that successor; exceptional exit, halt, divergence and external control remain open, as do all foreign memory effects. No arbitrary AIR Opaque/control bounds are narrowed. Unknown/present handlers retain AllControl.

Authority: IBM Enterprise COBOL 6.4 [CALL](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-call-statement) and [Language Reference / EXIT PROGRAM](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf): normal return resumes following CALL; handler clauses govern local exceptional continuation. The source contract already publishes these clauses and normal continuation. This is a corrected lowering of those facts, not removal of unknown CFG edges by the values solver. Synthetic source fixtures are the acceptance authority; the original RED artifacts remain local.

## Final local qualification (2026-09-18)

24 synthetic source fixtures passed twice through normal frontend CLI -> SP -> lower -> AIR JSON -> CFG -> DependencyAnalysis -> dependencies.json. The final JSON bytes match exactly across both runs. Caller identity is matched to the AIR unit and source PROGRAM-ID; CALL origin lines, targets, raw padding, candidate evidence, remainder and absence of spurious edges are asserted. No `--logical-text enabled`, physical-profile selection or experimental-physical flag is passed. Supported fixtures publish logicalTextViews and only AIR Cells.

| Fixture | Expected targets | Actual targets | Effective remainder | Physical groups/writes |
|---|---|---|---|---|
| group | ABCD | ABCD | true | 0 / 0 |
| padding | PROGA | PROGA | true | 0 / 0 |
| truncation | PROGA | PROGA | true | 0 / 0 |
| snapshot | ABCD | ABCD | true | 0 / 0 |
| overlay-forward | PROGA | PROGA | true | 0 / 0 |
| overlay-reverse | PROGB | PROGB | true | 0 / 0 |
| overlay-split | PROGA | PROGA | true | 0 / 0 |
| overlay-partial | PROGA | PROGA | true | 0 / 0 |
| renames | PROGA001 | PROGA001 | true | 0 / 0 |
| renames-filler | PROGA001 | PROGA001 | true | 0 / 0 |
| correlation | MODU0002, PROG0001 | MODU0002, PROG0001 | true | 0 / 0 |
| value-child | PROGA001 | PROGA001 | true | 0 / 0 |
| composition | PROGA | PROGA | true | 0 / 0 |
| known-open | PROGA | PROGA | true | 0 / 0 |
| unsupported | UNKNOWN/PARTIAL | no invented target | true | 0 / 0 |
| invalid-renames | UNKNOWN/PARTIAL | no invented target | true | 0 / 0 |
| overlap-refused | UNKNOWN/PARTIAL | no invented target | true | 0 / 0 |
| w1-a | PROGA | PROGA | true | 0 / 0 |
| w1-b | PROGA | PROGA | true | 0 / 0 |
| w1-c | PROGB | PROGB | true | 0 / 0 |
| w1-d | PROGA, PROGB | PROGA, PROGB | true | 0 / 0 |
| overlay-branch | PROGA, PROGB | PROGA, PROGB | true | 0 / 0 |
| overlay-open-root | UNKNOWN/PARTIAL | no invented target | true | 0 / 0 |
| unequal-overlay-unknown | UNKNOWN/PARTIAL | no invented target | true | 0 / 0 |

The remainder is deliberately open. A fully known projected text does not claim general physical completeness. `known-open` retains PROGA after an unsupported input effect, with open value/completeness evidence. `overlay-open-root` preserves a known child but cannot publish its partially unknown whole root. The unequal-extent overlay challenge exposed and fixed invented padding of an unknown tail: the old runtime incorrectly returned EFGH, while the corrected root initialization retains unknown [8,12). Its RED and GREEN are preserved, and lower FAST includes the regression.

## Demand and scale

| Irrelevant families | Detailed cells | Writes | Producers | Physical plans | Physical groups | Physical writes |
|---:|---:|---:|---:|---:|---:|---:|
| 10 | 5 | 6 | 6 | 0 | 0 | 0 |
| 100 | 5 | 6 | 6 | 0 | 0 | 0 |
| 1000 | 5 | 6 | 6 | 0 | 0 | 0 |

Demand closure is 5 Cells for the single requested CALL, including transitive root/source reads. Each irrelevant family contains a group, a textual overlay, RENAMES and a MOVE. Detailed prepared state stays constant. Parsing/indexes/CFG still grow; this is not a claim of globally O(1) processing. `baseComparisons=0`, `targetsPrepared=0` for physical preparation on this scalar route.

| Families | Frontend s | Lower s | CFG s | Dependency CLI s | Prepare ms | Solve ms | Nodes popped | Heap MiB observation |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 1.417 | 1.324 | 0.516 | 0.665 | 2.662 | 9.297 | 17 | 22.8 |
| 100 | 2.024 | 2.320 | 1.170 | 1.468 | 6.404 | 10.968 | 107 | 97.1 |
| 1000 | 5.530 | 8.557 | 5.434 | 7.000 | 39.241 | 18.199 | 1007 | 1039.3 |

Prepare/solve are a separate observation-only run on the exact emitted AIR and the same demanded provider key. Heap is used JVM heap before forced GC, including parsing, full AIR and CFG; it is not retained logical-state memory. JVM processes use `-Xmx2g` and 120-second timeouts; timeout/resource failure is never reported as stable completion. Timings stay outside deterministic product JSON. The previous 10,000 case remains NOT_MEASURED_FOR_ANALYSIS (upstream AIR serialization exhaustion); it was not repeated.

Reproduce the source tests with `python3 scripts/project/e2e_logical_text.py --runtime runtime.json --work /tmp/logical-e2e`; use `--scale` for 10/100/1000. The runtime JSON records each CLI main/classpath and frontend checkout; actual invocations and stdout/stderr are preserved per fixture. `LogicalTextTimingProbe` is an optional observation-only adapter test main.

## Providers, contracts and regressions

The W2 fixtures use existing PossibleValues + Reachability on CFG, with demand closure through Read/FitText/SliceText/Concat. No second solver or global MAY-alias domain was introduced. A bounded partial-character carrier is interned in the existing Candidates/ValueUniverse; same-root alternatives remain whole. No Cartesian product between varying independent inputs is synthesized.

The broader RegionalValues provider remains selectable under LOGICAL_ONLY for its supported cases, but is not the provider used by these fixtures. Physical/regional propagation is EXPERIMENTAL / NOT PRODUCTION QUALIFIED, DEFAULT OFF, automatic fallback NONE. Its grouped loop and RegionalAlternatives were not changed. W1 opt-in boundary tests continue to prove that experimental physical work is accessible only explicitly.

Local gates: frontend FAST 366 tests; air-java FAST 187 model + 128 transport checks and 40 harness tests; lower FAST (including the unequal-overlay regression); CFG FAST including four new logical expression/carrier tests, wire/consumer/architecture checks; full CFG Maven reactor tests also passed. Required CALL/scalar VALUE/MOVE, IF/PERFORM/EVALUATE/GOTO, FILE and CICS families remain covered by the existing gates.

Physical branches of ControlFlowEvidenceTest and the byte-level FileIoOutcomeOracleTest now opt in explicitly and keep their original physical oracles. The scalar copy provider assertion accounts for the existing demanded key. WireAdversarialTest retains its unavailable-profile oracle using general TrimRight, because FitText is now supported. No production physical fallback was enabled to satisfy these tests. Architecture inventories were updated for the two logical-expression classes; inner module boundaries remain intact.

Producer pins are exact in sources.lock.json: frontend 84845762c58ba0f64199bf01db9afce4c97a939b, lower 557693a1184ddd2bd61886ceaa1176a78c0aa162, air-java 646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa. Lower pins that frontend and air-java. AIR remains 2.0 / binding 1.0, SP remains 2.29/storage 1.9. Old SP readers/contracts are retained. Remote Fast status must be read from the exact published heads, never inferred from local gates.

## Remaining limits

- Fixed textual ordinary local WORKING-STORAGE only. Numeric storage, COMP/COMP-3/BINARY, NATIONAL/DBCS, dynamic extents, OCCURS/ODO and general reference modification remain outside admission.
- Copies within one shared family are conservatively refused; no sequential overlapping-copy approximation.
- Whole demanded families are retained, not a formal minimal interval slice. Global input/index/CFG cost remains proportional to input.
- General cross-root correlation, arbitrary physical aliasing, codec conversions, GRBE byte semantics and comprehensive overlapping VALUE initialization are not qualified. Child VALUE in an ordinary group is covered.
- Unknown source/control/effects remain explicit. Candidate discovery does not certify a closed target set.

Corporate NOT REEXECUTED / NOT AN ACCEPTANCE GATE / NO NEW NINE-TARGET CLAIM. No automatic merge.
