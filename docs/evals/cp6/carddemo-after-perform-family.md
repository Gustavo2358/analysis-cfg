# PERFORM FAMILY LONG-RUNNING — READY FOR HUMAN REVIEW

THRU/THROUGH, UNTIL, TIMES and single-variable VARYING are implemented end-to-end
and cumulatively qualified. No merge performed. Roadmap authority: **HUMAN**.
The wave uses one `feat/perform-family` branch and one Draft PR per affected repo.

The control gain is demonstrated by the supported focal profiles: typed ordered
ranges, activation-specific resumes, BEFORE/AFTER decisions, one-time TIMES
count reads and localized VARYING initialization/update writes. **CardDemo has
no real CALL candidate gain:** 373 occurrences now carry typed procedure facts,
but all remain partial; no structured activation is claimed in this corpus.

## Immutable snapshots

[Exact pins and baseline equivalence](carddemo-perform-family-pins.json).
Historical GO TO measurements use pre-merge producer SHAs with identical
production/build inputs to the actual baseline merges; the five CFG differences
are evaluation/work metadata and are listed in the pins.

| Repository | Baseline main merge | Final execution snapshot |
| --- | --- | --- |
| proleap-poc #43 | `f62e4cf2792d518de5f4f9c753d0d86e92d3f755` | `642ba4b3aeaffdfa0942f3fb462a4be17070d0d1` |
| cobol-lower #20 | `715f196369d1e82cfd7e4affb31ee34dc73c0e2d` | `e8cdc95491b7225c2c899e6f4dc229f64f26a8d7` |
| analysis-cfg #29 | `51beeb1859689dc83e16aca9cc8a1fc9fc7e0bc7` | `982179c812ff526e9e6095ea3feaffa5e42c7985` |
| air-java | `96cd5e545723c6fd76d1520f431ebbc196af84f6` | `8be19ff385b42a5987a407acc464319d212081c3` |

CardDemo remains `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`; sources and missing
COPY/dependency inputs were unchanged. `analysis-ir` remains
`51b4d9a8ae0364232bd97103cd73a77e1a34996c`.
Later evaluation-only commits do not replace these execution pins.

## Cumulative qualification

| Family | Focal/adversarial E2E | Result |
| --- | ---: | --- |
| THRU / THROUGH | 23 | PASS: typed range, composed bodies and activation-specific resumes |
| UNTIL | 19 | PASS: default/explicit BEFORE, AFTER, fixed point and THRU composition |
| TIMES | 18 | PASS: positive literals, conservative identifier, THRU and no unrolling |
| VARYING | 28 | PASS: single variable, FROM/BY effects, condition reads, TEST modes and THRU |
| Mixed families + BASIC | 1 | PASS |

**89/89 PERFORM E2E PASS.** All applicable families pass 1/2/5/40 callsites,
SP/AIR/CFG/dependency A/B bytes, statement-inventory and JSON-field permutations,
and AIR-sequence permutations. Candidates retain source-derived MOVE/literal-CALL
supports. UNTIL/VARYING BEFORE preserves OLDPROG+NEWPROG; AFTER preserves NEWPROG
alone after the body strong update. Literal 1/5/1000000 TIMES each has one static
body: 6 sequences, 8 operations and 2 text assignments in its focal publication.

Adversarial tests cover dangling identities, wrong endpoints/resume, overlap,
ordinary incoming/escaping GO TO, cycles, recursive activation, open continuation,
contradictory proofs, unresolved/unsupported operands and conditions. Contradictions
are INVALID_INPUT or conservative fallback; no fabricated finite return is admitted.

GO TO **23/23**, EVALUATE **18/18**, historical BASIC PERFORM, MOVE, IF,
Multi-CALL and ENTRY/partial/compositional lowering **38/38 PASS**.
Frontend FAST **139 tests PASS**; lower, CFG and AIR FAST **PASS**.
Remote CI is **FAST ONLY**, with successful runs for all productive heads.
Final qualification-local: frontend **PASS** (629 executed tests; one existing
future-condition opt-in test skipped), lower **PASS**, AIR **PASS** (179 model +
110 transport checks). No inventory or historical baseline was changed to get PASS.
A local CFG inventory check failed during concurrent shared dependency builds;
an independent capture matched the unchanged baseline and a serialized full FAST
rerun passed. Raw failed and successful runs remain preserved.

SP evolves through 2.2 range, 2.3 loop, 2.4 count/integer declaration and 2.5 VARYING
wire shapes. Historical SP 1.x, 2.0 and 2.1 decoders, plus intermediate wave
versions, remain available. Semantic authority and boundaries are documented in
[frontend PERFORM semantics](https://github.com/Gustavo2358/proleap-poc/blob/642ba4b3aeaffdfa0942f3fb462a4be17070d0d1/docs/domain/perform-family.md)
and [lowering rules](https://github.com/Gustavo2358/cobol-lower/blob/e8cdc95491b7225c2c899e6f4dc229f64f26a8d7/docs/domain/perform-family.md),
using the official IBM Enterprise COBOL 6.4 Language Reference.

## Affected corpus and one final Full

Phase 1 qualified 19 THRU programs and preserved 27 CALL vectors. Phase 2 qualified
21 cumulative programs and preserved 45 vectors; its separate THRU comparison
preserved all 27. The final affected run again qualified those 21 programs and
preserved all 45 vectors against the baseline and UNTIL, and all 27 against THRU.
**TIMES/VARYING did not regress any earlier THRU or UNTIL result.**

After all cumulative gates and affected comparisons passed, **exactly one final
Full** attempted all 73 historical sources. 67 reached SP → AIR → CFG → dependency;
the six historical frontend blockers retained identical stage states and reason
codes. Full qualification is PASS with those unchanged input boundaries, not a
claim of complete semantics for every source. No stubs, new COPY dependencies or
source edits were used.

There are **1,829 observed PERFORM occurrences in the 67 available SP publications**:

| Family | Occurrences | Out of line | Typed | Structured/precise | Typed partial | Unsupported |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| THRU | 371 | 371 | 371 | 0 | 371 | 0 |
| UNTIL | 45 | 8 | 8 | 0 | 8 | 37 |
| TIMES | 0 | 0 | 0 | 0 | 0 | 0 |
| VARYING | 24 | 0 | 0 | 0 | 0 | 24 |

THRU+UNTIL contributes to both family rows: 373 distinct typed partial facts.
The remaining **1,456 unsupported** occurrences are 1,395 unproved procedure/ONCE,
37 inline UNTIL and 24 inline VARYING. There are no out-of-line TIMES or VARYING
occurrences in this snapshot. Missing input, unproved primary isolation, paragraph
boundaries, incoming control, range control and overlap remain explicit gaps.

Dependency programs **67 → 67**; analyzed CALL sites **73 → 73**; known-candidate
sites **73 → 73**. All 73 candidate/classification/reachability/remainder vectors
are preserved, including 28 sites outside the affected program cohort.
Changed candidate sites **0**, additions **0**, removals **0**; unexpected regressions
**NONE**. Closed **0**, partial **73**, open **0**. Result: **NO_REAL_CALL_CANDIDATE_GAIN**.

| Remainder | Before | After |
| --- | ---: | ---: |
| modelValueRemainder | 0 | 0 |
| sourceValueRemainder | 73 | 73 |
| interpretationUnknownRemainder | 73 | 73 |
| effectiveUnknownRemainder | 73 | 73 |
| openControlRemainder | 73 | 73 |

## Production boundary and handoff

CFG projection production change: **NONE**. Two generic supporting changes were
necessary: AIR JSON transport of the existing `known(int)` type, and PossibleValues
admission of disjoint auxiliary integer cells while queries remain TEXT-only
(`d723dace8844311e26057e13de2f47a6602377e6`). Existing effect transfer and fixed point
are reused. No new AIR operation, numeric evaluation, solver, lattice or RD.

VARYING initialization and increments must-write only the known control item with
open numeric values. TEST AFTER is supported; multi-level **AFTER is explicitly
typed/conservative outside the slice**. Unknown nonzero BY proof and unsupported
numeric/condition/range profiles remain partial. Inline PERFORM remains outside
this wave. UNTIL reuses the existing scalar TEXT equality predicate profile;
VARYING also admits simple integer relations. Other conditions remain partial.

[Small machine-readable delta](carddemo-after-perform-family.json) includes the
historical blockers and raw measurement hashes. Immutable raw SP/AIR/CFG/dependency,
logs, A/B runs and comparisons remain under `.harness-results/perform-family/`,
including `varying-cumulative`, `carddemo-family-affected`, `carddemo-family-full`,
`carddemo-until-after-family-delta.json` and `carddemo-thru-after-family-delta.json`.
The prior green checkpoints remain auditable in the branch history.

Draft PRs: [frontend #44](https://github.com/Gustavo2358/proleap-poc/pull/44),
[lower #21](https://github.com/Gustavo2358/cobol-lower/pull/21),
[evaluation/values #30](https://github.com/Gustavo2358/analysis-cfg/pull/30),
[existing int transport #13](https://github.com/Gustavo2358/air-java/pull/13).

**No merge performed. Roadmap authority HUMAN.**
