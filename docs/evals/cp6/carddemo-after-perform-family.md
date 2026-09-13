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

No Full corpus run has been performed in this wave. Continue to UNTIL.
