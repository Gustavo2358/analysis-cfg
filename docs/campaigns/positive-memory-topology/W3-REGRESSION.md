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
