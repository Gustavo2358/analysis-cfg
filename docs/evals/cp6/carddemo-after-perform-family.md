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


## Phase 3 implementation boundary discovered

The pinned AIR model already represents `known(int)`, but its JSON codec rejected
that type. The wave's `air-java` Draft PR #13 adds that existing generic transport
form; AIR and binding versions remain unchanged. PossibleValues also rejected
an entire publication if any direct cell was non-text. Its conservative auxiliary
integer admission will keep those cell values open and restrict candidate queries
to text objects. A single source-derived disjointness premise must still cover
all admitted cells, including auxiliary ones. Localized integer writes then leave
independent text candidates unchanged; all-memory effects still open text values.
This is a generic profile-admission extension, not numeric evaluation, lattice,
solver or CFG projection work. The existing per-cell effect transfer is reused.
Oracle: text PROGA survives a disjoint integer must-write, a numeric query is
unsupported, an all-memory may-write opens PROGA, and missing disjointness refuses.


## Phase 3A checkpoint — TIMES GREEN; THRU/UNTIL regression GREEN

Frontend `2a9c3ebf09257c213ed3b78b3f6c364bdc1c16ac`, lower
`b0d0cd03156f42623f8e6bb503c9a30e6b19b3e4`, auxiliary-cell admission
`d723dace8844311e26057e13de2f47a6602377e6`, AIR codec
`8be19ff385b42a5987a407acc464319d212081c3`. SP 2.4 preserves all older decoders.

THRU+UNTIL+TIMES 60/60 PASS; GO TO 23/23; EVALUATE 18/18; historical 38/38.
TIMES includes literal 1/5/1000000, identifier count, THRU, multiplicity 1/2/5/40,
partial counts/ranges, contradictory proofs, A/B bytes and physical permutations.
The three literal count fixtures each have 6 sequences, 8 operations and 2
Assigns: the initial text write and one body write. The identifier is read only
at activation entry; exhaustion decisions never reread it.
FAST frontend 132 tests PASS; lower PASS; CFG PASS; AIR PASS. AIR local
qualification also PASS: 179 model + 110 transport deterministic checks via Maven
clean verify. CFG projection production remains unchanged; the generic
PossibleValues admission boundary described above is the only dataflow change.
No lattice, solver, numeric value analysis or Reaching Definitions change.

Raw evidence: `times-focal-02`, `times-cumulative`, `goto-times`, `evaluate-times`,
`historical-times`, `times-static-size.json`. Failed discoveries remain preserved.
Final cumulative affected corpus qualification follows VARYING. No Full corpus
run has been performed. Continue immediately to single-variable VARYING.


## Phase 3B checkpoint — complete PERFORM family GREEN

Frontend `642ba4b3aeaffdfa0942f3fb462a4be17070d0d1`, lower
`e8cdc95491b7225c2c899e6f4dc229f64f26a8d7`, cumulative E2E
`519272ffaaefad1fc785fc3370554e156b28a437`; AIR codec unchanged at the Phase 3A pin.
SP 2.5 adds typed control-variable/FROM/BY operands and explicit VARYING levels.
The shared UNTIL decision models BEFORE and AFTER, with initialization before
entry and AFTER increments only on the repeat path. Integer values stay open;
initialization/update must-write only the proved control item. FROM reads,
implicit increment reads, subscript reads and provenance are retained.
Multi-level AFTER remains explicitly typed and conservative.

Cumulative E2E: 89/89 PASS (23 THRU, 19 UNTIL, 18 TIMES, 28 VARYING,
plus one mixed program composing all families and BASIC PERFORM).
Includes 1/2/5/40 callsites per family, A/B byte determinism at SP/AIR/CFG/dependency,
physical statement/JSON-field/AIR-sequence permutations, precise candidate oracles,
partial/adversarial control and source-derived candidate supports.
GO TO 23/23, EVALUATE 18/18, historical BASIC/MOVE/Multi-CALL/IF/partial/input 38/38.
Frontend FAST 139 tests PASS; lower FAST PASS; CFG FAST PASS; AIR FAST PASS.
All four Draft PRs have successful remote Fast CI runs at their productive heads.
Final qualification-local: frontend PASS (629 executed tests; one historical
future-condition opt-in test skipped), lower PASS, AIR PASS. Frozen historical
facts and bytes are unchanged; additive null integer metadata is checked explicitly.
An initial local CFG inventory check failed during concurrent shared dependency
builds; an independent capture matched the unchanged inventory, and a serialized
complete CFG FAST rerun passed. No inventory baseline was changed.

Raw evidence: `varying-cumulative`, `goto-final`, `evaluate-final`,
`historical-final`; runtime artifacts are immutable jars with SHA-256 hashes.
Final affected qualification and the single final Full corpus run follow this
checkpoint. No Full corpus run has yet been performed.
