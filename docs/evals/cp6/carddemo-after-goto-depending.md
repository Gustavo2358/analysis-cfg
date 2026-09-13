# CardDemo after GO TO DEPENDING ON

**GO TO DEPENDING ON — READY FOR HUMAN REVIEW.** No merge performed.
decisionPolicy = HUMAN; rankingAuthority = ADVISORY_ONLY.

The preserved post-PERFORM SP/AST has **zero conditional GO TO occurrences**.
No COBOL source regex or invented example selected the cohort. Target-count
min/median/p95/max are **not applicable**; destination occurrences, typed,
precise/structured, partial, unsupported, affected programs and cohort CALLs
are all **0**. This says nothing about sources blocked before SP production.
The empty affected qualification passed before the Full.

Exactly **one final local Full** attempted all 73 historical sources, including
ZIP members, at upstream `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`.
Runtime snapshots were clean before and after execution; compiled product JARs
were frozen and hashed. Baseline merge identities and source/build equivalence
to the preserved pre-merge execution are explicit in [the pins](carddemo-goto-depending-pins.json).

| Full measure | Before | After |
| --- | ---: | ---: |
| Sources attempted | 73 | 73 |
| Programs reaching dependency | 67 | 67 |
| CALL sites observed/analyzed/reachable | 73 | 73 |
| Sites with known candidates | 73 | 73 |
| CLOSED / PARTIAL / OPEN sites | 0 / 73 / 0 | 0 / 73 / 0 |
| modelValueRemainder | 0 | 0 |
| sourceValueRemainder | 73 | 73 |
| interpretationUnknownRemainder | 73 | 73 |
| effectiveUnknownRemainder | 73 | 73 |
| openControlRemainder | 73 | 73 |

All **73 site vectors**, including all **73 outside the conditional-control
frontier**, retain candidate values, classification, reachability and all five
remainders. Candidate changes: **0 sites, 0 additions, 0 removals**.
Unexpected regressions: **NONE**. The same six pre-SP input blockers remain;
all 67 produced programs remain PARTIAL. Nothing was cleared merely because
conditional transfers can now carry known alternatives.

**Modelar GO TO DEPENDING ON alterou algum resultado real de dependência? Não.**
This historical CardDemo has no observed occurrence. **Control fidelity gain**
is proved by the focals: D1 reaches exactly `{FALLPGM, PROGA, PROGB, PROGC}`,
D2 excludes the unrelated textual path, and order/duplicate/partial/cyclic
and PERFORM interactions are independently verified. **CALL candidate gain**
in this real corpus is zero; no new feature is selected from this result.

Implementation: SP **2.6.0** retains the ordered ordinal mapping, one integer
selector reference, per-destination identities/entries/provenance/local gaps,
and explicit fallthrough. Unknown peers do not erase known destinations.
Lower emits one existing generic AIR finite control envelope, with a single
selector read, no writes, known alternatives and independent open remainder.
Resolution and lowering scale O(N). AIR/CFG production: **NONE**.
Numeric selector evaluation/pruning, PossibleValues lattice changes and RD:
**NOT IMPLEMENTED**.

Validation: **33/33** final focals/adversarials; cardinalities
**1, 2, 5, 40, 100, 200, 255 PASS**. The parser accepts 256 and preserves all
occurrences as typed partial with a dialect/profile gap. No target cap, tail
truncation, order sorting or deduplication occurs in SP. Indexed work assertions
and a 255-target timeout guard passed. SP/AIR/CFG/dependency A/B bytes and
physical inventory/field/sequence permutations passed for all 33 focals.
Malformed wire/in-memory ordinals, entries, type claims and provenance are
rejected as INVALID_INPUT. Candidate supports remain real value producers.

PERFORM interaction: BASIC and range incoming control/isolation, internal closed
transfer, escape detection and unresolved-target closure all PASS.
Regression: **GO TO 23/23; EVALUATE 18/18; PERFORM FAMILY 89/89;
historical 38/38**, plus ENTRY/localized input in lower suites. FAST local and
remote PASS for all three changed repositories; remote **FAST ONLY**.
Final frontend and lower qualification-local PASS. No redundant AIR/CFG
qualification was run for unchanged production.

Earlier development runs hit the `/tmp` disk quota; failed logs are retained.
Final evidence uses workspace disk, unchanged test sizes and resource policies.
The single Full took approximately **316 seconds**. Raw outputs (about 578 MB)
remain outside Git in `goto-depending/evidence/full-final`; hashes and compact
results are in [the JSON delta](carddemo-after-goto-depending.json) and
[qualification record](goto-depending-qualification.json).

Draft PRs: [frontend #45](https://github.com/Gustavo2358/proleap-poc/pull/45),
[lower #22](https://github.com/Gustavo2358/cobol-lower/pull/22),
[CFG/evaluation #31](https://github.com/Gustavo2358/analysis-cfg/pull/31).
Stop for human review. ALTER, numeric refinement and the next corpus gap remain
outside this wave.
