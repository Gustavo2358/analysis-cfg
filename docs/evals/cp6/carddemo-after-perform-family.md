# PERFORM family — cumulative evaluation in progress

Goal: THRU/THROUGH + UNTIL + TIMES + single-variable VARYING end-to-end.
Human review is reserved for the completed wave. No merge performed.
Baseline merges and the unchanged CardDemo snapshot are in
[pins](carddemo-perform-family-pins.json).

## Phase 1 checkpoint — THRU GREEN

Frontend `68ccdbff9bad0e4a43a82d43c904231d8ee32eb5`, lower
`5ce1705ae1906080aaa7537ad56f2a6f7e70c55b`, CFG production unchanged.
FAST: frontend 130 tests PASS; lower PASS; CFG PASS.
Focused THRU: 23 fixtures PASS, including multiplicity 1/2/5/40,
source/AIR permutations, byte determinism, adversarial ranges and composed bodies.
Cumulative E2E: GO TO 23/23, EVALUATE 18/18, historical BASIC PERFORM,
MOVE, Multi-CALL, IF and partial/input 38/38.

Affected qualification used 19 programs selected by typed THRU references in the
historical AST. Their 419 PERFORM occurrences include 371 with THRU/THROUGH;
365 now have typed range facts, all partial. Six combine later loop controls.
The remaining 54 PERFORM occurrences are outside this phase's profile.
Missing COPY/input, unsupported boundaries/control, incoming edges and unproved
primary isolation remain explicit. No closed range is claimed in this cohort.

Dependency: 19 → 19 programs; 27 → 27 analyzed CALL sites, all with known
candidates and PARTIAL_RESOLVED. All 27 candidate/classification/reachability/
remainder vectors are unchanged. Additions 0; removals 0; unexpected regressions 0.
Remainders: model value 0, source value 27, interpretation unknown 27,
effective unknown 27, open control 27. Result: NO_REAL_CALL_CANDIDATE_GAIN.

Raw runs are preserved locally under `.harness-results/perform-family/`:
`thru-provenance`, `goto-thru-final`, `evaluate-thru-final`,
`historical-thru-final`, `carddemo-thru`, `carddemo-thru-delta.json`.
The provenance fix was additionally replayed against all 19 corpus SPs:
`thru-lower-replay` proves byte-identical AIR, so its existing CFG/dependency
outputs remain applicable. Historical GO TO producer trees equal their actual
merge trees; CFG differs only in evaluation files, not production/build inputs.

## Phase 2 checkpoint — UNTIL GREEN; THRU regression GREEN

Frontend `a622c084707d8fd3750c75152bde6a008b221981`, lower
`36f5a7ccaa8747633e4f8aaceea12ec1bc88a117`, evaluation
`4d9f3bdff66a4363c6f558f27af427d08b06ea43`; CFG production unchanged.
SP 2.3 preserves 2.2 decoding. Default/explicit TEST BEFORE permits zero
iterations; TEST AFTER executes the body before its first decision. Predicates
retain typed reads and unknown truth. No pruning or iteration unrolling.

FAST frontend 131 tests PASS; lower PASS; CFG PASS. THRU+UNTIL 42/42;
GO TO 23/23; EVALUATE 18/18; historical regressions 38/38. UNTIL includes
BEFORE/AFTER candidate oracles, fixed point, THRU/BASIC composition,
multiplicity 1/2/5/40, partial/adversarial controls and byte/order determinism.
Final frontend replay reproduces identical SP for all 42 cases; final lower
class files equal the tested runtime. Raw evidence: `phase2-cumulative`,
`goto-phase2`, `evaluate-phase2`, `historical-phase2`, `until-frontend-replay.json`.

Affected qualification: 21 programs, 479 PERFORM occurrences, 373 typed partial
procedure facts; 371/371 THRU typed and 8/8 out-of-line UNTIL typed. Structured
activations remain 0 in this cohort; 106 occurrences remain outside the profile.
Dependency: 21 → 21 programs; 45 → 45 known-candidate CALL sites, all partial.
All 45 site vectors preserved; candidate changes/additions/removals 0;
unexpected regressions 0. Remainders: model 0, source/interpretation/effective/
open-control 45 each. NO_REAL_CALL_CANDIDATE_GAIN.

Did UNTIL change or regress a result already produced by THRU? **No.**
The separately compared 19-program THRU cohort preserves all 27 site vectors,
including candidates, classification, reachability and all five remainders.
Raw evidence: `carddemo-until`, `carddemo-until-delta.json`,
`carddemo-thru-after-until-delta.json`; the latter uses a declared subset view
of the original unchanged measurement records.

No Full corpus run has been performed in this wave. Continue to TIMES/VARYING.
