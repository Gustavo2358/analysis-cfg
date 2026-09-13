# GO TO FIRST SLICE

**READY FOR HUMAN REVIEW.** Coordinated Drafts:
[frontend #43](https://github.com/Gustavo2358/proleap-poc/pull/43) and
[lower #20](https://github.com/Gustavo2358/cobol-lower/pull/20); this CFG review
contains only E2E fixtures, evaluation scripts, the small delta and pins.
No merge or auto-merge. **decisionPolicy = HUMAN; rankingAuthority = ADVISORY_ONLY.**

WORK-CFG-041. Supported: local, unconditional, uniquely resolved paragraph GO TO
in the same program unit, with a proved executable entry and exact provenance.
SP **2.1.0** adds `GoToFact`; the lower retains historical decoders and maps the
new typed fact to existing AIR `Jump`. AIR, CFG, PossibleValues, dependency
analysis, lattice and solver production changes: **NONE**.

The existing grammar reference resolves through procedure symbols to paragraph
identity and its direct grammatical executable entry. The Jump preserves the
GO TO occurrence, reference, paragraph and entry origins. It has exactly one
normal successor: that explicit entry. A known paragraph without a proved entry
retains partial identity and open Opaque control; it never acquires the next
textual statement as a destination.

The transfer rule was checked against [IBM Enterprise COBOL 6.4, unconditional
GO TO](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-unconditional-go).
No section transfer, DEPENDING ON, computed/dynamic transfer or ALTER resolution
was added. Ambiguous/unresolved/outside-unit targets, empty paragraphs, incomplete
procedure input, inadequate provenance and unsupported containment stay
conservative. A unit containing ALTER cannot publish a precise transfer in this
slice. Qualified references to a unique local paragraph are supported.

## Semantic qualification

All **23 focused E2Es passed**, with identical SP/AIR/CFG/dependency bytes in A/B
executions using the same source pins. Statement inventory and JSON field order
were reversed for G1, backward, IF jump, EVALUATE jump and multiplicity 40; AIR
sequence/field permutations also retained identical CFG/dependency bytes.
Every precise GO TO has one CFG JUMP edge to its published entry; every partial
GO TO has Opaque control with no known continuation. Candidate supports are
checked against the actual literal MOVE producer and original source provenance.

| Oracle | Result |
| --- | --- |
| G1: eliminate false fallthrough | PASS: `{PROGA}`; `PROGB` absent |
| G2: IF join before GO TO | PASS: `{PROGA, PROGB}`; `BADPROG` absent |
| G3: multiple CALL sites | PASS: three distinct sites, `{PROGA}` / `{PROGB}` / `{PROGC}` |
| G4: EVALUATE join before GO TO | PASS: `{PROGA, PROGB}`; `BADPROG` absent |
| G5: target strong update | PASS: `{NEWPROG}`; `OLDPROG` and `BADPROG` absent |
| GO TO inside IF / EVALUATE | PASS: jumped arm bypasses the enclosing continuation |
| Backward target / cycle | PASS: explicit backward edge; existing fixed point converges |
| Multiplicity 1 / 2 / 5 / 40 | PASS: all transfers and distinct CALL sites retained |
| BASIC PERFORM | PASS: adjacent/disjoint composition; overlap and cycle cannot prove isolated returning primary |
| Unsupported / partial variants | PASS: no arbitrary Jump or invented fallthrough |
| EVALUATE regression | All 18 existing fixtures PASS A/B |

In [G1](../../../analysis-adapters/src/test/resources/cp6/goto/g1.cbl), the published
relation is `statement:3` (GO TO, line 11) → target paragraph identity →
`statement:2` (CALL, line 14). The support of `PROGA` is the MOVE on line 10,
`statement:0`; the MOVE of `PROGB` on line 12 is absent from that path. Statement
IDs are deliberately not in textual order. These results prove the semantic
gain on dynamic CALL fixtures, without changing PossibleValues.

Known candidate sets remain partial runtime dependencies: the focal results
retain source-value, interpretation, effective and open-control remainders.
No remainder was cleared merely because a Jump became precise.

## Baseline and exact pins

The work started from the integrated EVALUATE mains. Confirmed merge commits:

| Repository / review | Merge SHA |
| --- | --- |
| proleap-poc #42 | `04a74a00c3692e05c5ffde2b492205ccdc04d262` |
| cobol-lower #19 | `4a72bb27d62326f26236586095eb8b2122635fea` |
| analysis-cfg #28 | `b40c24279783a873f73b465b9c75e1ee2245d11b` |

[Exact execution pins](carddemo-goto-pins.json) identify frontend
`3842239551153e98590744ceb8b1e730885b1bbd`, lower
`2dd97f4fb07eadb2a5cb76d94f94f587aa59a175`, AIR
`96cd5e545723c6fd76d1520f431ebbc196af84f6` and CFG
`f7c12eaac5d9b6b08c51e923491bce517f3f692b`. The normative AIR source is
`51b4d9a8ae0364232bd97103cd73a77e1a34996c`. The later CFG report/path-resolution
commit changes no product source. Runtime jars/classes were built in isolated
clean checkouts at these exact SHAs; SNAPSHOT coordinates are not compatibility
evidence.

The preserved post-EVALUATE baseline is
`/tmp/evaluate-finding-20260913/full/measurements-final.json`. Its producer source
and build descriptors were verified byte-identical to the integrated merges;
no old draft was replayed or cherry-picked. Historical Full, ENTRY and EVALUATE
outputs remain unchanged. The same historical CardDemo upstream is
`59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`.

## Affected population

Selection came exclusively from GO_TO occurrences in preserved SP: **306
occurrences in 16 programs**, containing **20 CALL sites**. Every selected
program reached SP, AIR, CFG and dependency before starting Full. All 20 CALLs
have literal targets; their candidates, classification, reachability and five
remainders were preserved. No addition or removal required causal justification.

The slice publishes **306 typed facts: 227 precise Jump and 79 partial**.
Remaining observed/unsupported GO TO: **0**. This zero describes the observed
variant count in this population; it does not mean the 79 partial facts became
precise or that the whole GO TO family is supported. Partial gap counts overlap:
62 `GO_TO_PROVENANCE_INCOMPLETE`, 51 `CONTAINMENT_NOT_PROJECTED`,
14 `GO_TO_ALTER_IN_UNIT`. CBSTM03A retains all 14 transfers partial because the
unit contains ALTER. CBSTM03B has 12 precise transfers and one partial;
COACTUPC has 62 precise and four partial in the checkout source.

The first affected invocation failed before SP because relative copybook paths
were interpreted against the per-program working directory. The evaluator now
resolves CLI paths before dispatch. The failed invocation and corrected affected
run have separate raw directories; neither counts as a Full run.

## Full comparison and product result

Exactly **one local Full** attempted all 73 physical sources, including archived
`.cl2` sources, on the same upstream. **67 programs reached dependency**; all
program stage states/reasons match the integrated baseline. The six pre-SP
blockers remain four EXEC preprocessing policy failures, one fixed-format tab
and one normalization rejection. Their GO TO/CALL inventories are unknown.
The occurrence counts below describe the 67 available SP products.

The [JSON delta](carddemo-after-goto.json) records the 16 affected programs, before
and after totals, site comparison and exact snapshots. All 16 affected programs
produced byte-identical SP/AIR/CFG/dependency artifacts in affected and Full runs.

| Metric | Post-EVALUATE baseline | After GO TO |
| --- | ---: | ---: |
| Programs reaching dependency | 67 | 67 |
| CALLs observed / analyzed / reachable | 73 / 73 / 73 | 73 / 73 / 73 |
| Known candidate sites | 73 | 73 |
| Closed / partial / open unresolved sites | 0 / 73 / 0 | 0 / 73 / 0 |
| GO TO occurrences | 306 | 306 |
| Typed GO TO | 0 | 306 |
| Precise GO TO Jump | 0 | 227 |
| Partial typed GO TO | 0 | 79 |
| Observed/unsupported GO TO | 306 | 0 |
| modelValueRemainder | 0 | 0 |
| sourceValueRemainder | 73 | 73 |
| interpretationUnknownRemainder | 73 | 73 |
| effectiveUnknownRemainder | 73 | 73 |
| openControlRemainder | 73 | 73 |

**Candidate changes: 0 sites; additions: 0; removals: 0. Unexpected regressions:
NONE.** Every one of the 73 sites preserves its candidate values, classification,
reachability and five remainders. This includes **53 sites outside the prior
GO TO Opaque control frontier**. Sites are correlated by original source
provenance, not publication IDs or source position heuristics. Since no site
changed candidates, there is no added/removed candidate to justify and
`changedSites` is empty. The comparator retains before/after supports and explicit
transfer evidence whenever a site changes; it does not classify a candidate
change as an automatic regression.

**Product answer: no improvement in the candidate set of a real CardDemo CALL
was observed in this run.** The 227 explicit transfers improve the control model,
and G1/G2/G4/G5 prove elimination of false value paths on dynamic CALL fixtures.
All 20 CALLs in the real affected population use literal targets; none changed
its candidates. The remaining partial GO TO bounds still potentially include
those 20 sites. Scope membership is not causal attribution, and precise Jump
edges do not close runtime dependency remainders. No gain is forced or inferred
from the number of newly typed facts.

Human review is the stopping point. No next feature, GO TO DEPENDING ON, PERFORM
extension, RD or solver work is selected by this result.

## Validation and reproduction

Frontend final FAST and qualification-local passed: 623 tests, one preexisting
skip, normalization and naming. An initial qualification found a missing
structural-gap category under unsupported containment; a regression was added
and the corrected final production source passed qualification-local once.
Lower FAST and one qualification-local on final production source passed,
including semantic, performance, architecture, partial lowering, ENTRY/localized
input and multi-CALL regressions. Its later commit only updates the frontend pin.
CFG FAST passed after the evaluator path fix; no CFG production change requires
qualification-local. Remote CI is FAST only.

Local outputs are under `analysis-cfg/.harness-results/goto/`:
`pinned-build/runtime.json`, `e2e-final/`, `evaluate-regression/`,
`affected-final/`, `affected-comparison.json`, `full/`, their logs, and
`fast-post-pathfix.log`. Failed attempts remain separate. Frontend qualification
is `proleap-poc/.harness-results/goto/qualification-final2.log`; lower qualification
is `cobol-lower/.harness-results/goto/qualification-final.log`.

The commands use existing project runners, Java 21, the pinned runtime and new
output directories. The actual sequence was focal A/B → affected comparison →
**one local Full** → final comparison. No Full ran remotely or before the affected
run passed. Representative commands, from `analysis-cfg/` (replace output paths
with fresh directories when reproducing):

```sh
export JAVA_HOME=/home/gustavo/workspace/teste-e2e/.w5-recovery/toolchains/jdk-21.0.12.1+1
export PATH="$JAVA_HOME/bin:$PATH"
python3 -B scripts/project/e2e_goto.py --runtime .harness-results/goto/pinned-build/runtime.json --work .harness-results/goto/e2e-final
python3 -B scripts/project/e2e_evaluate.py --runtime .harness-results/goto/pinned-build/runtime.json --work .harness-results/goto/evaluate-regression
python3 -B scripts/project/carddemo_goto.py affected --before /tmp/evaluate-finding-20260913/full/measurements-final.json --upstream /tmp/carddemo-full-20260913/upstream --runtime .harness-results/goto/pinned-build/runtime.json --pins docs/evals/cp6/carddemo-goto-pins.json --work .harness-results/goto/affected-final
python3 -B scripts/project/carddemo_goto.py compare --before /tmp/evaluate-finding-20260913/full/measurements-final.json --after .harness-results/goto/affected-final/measurements.json --output .harness-results/goto/affected-comparison.json
python3 -B scripts/project/carddemo_baseline.py --upstream /tmp/carddemo-full-20260913/upstream --runtime .harness-results/goto/pinned-build/runtime.json --pins docs/evals/cp6/carddemo-goto-pins.json --work .harness-results/goto/full
python3 -B scripts/project/carddemo_goto.py compare --before /tmp/evaluate-finding-20260913/full/measurements-final.json --after .harness-results/goto/full/measurements.json --focal .harness-results/goto/affected-final/measurements.json --output docs/evals/cp6/carddemo-after-goto.json
```
