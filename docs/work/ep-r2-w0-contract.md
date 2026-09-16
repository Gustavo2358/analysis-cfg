# EP-R2 W0 — frozen contract

Status: IN_PROGRESS. Scope: analysis-cfg only. No production changes in W0.

Base after successful remote fetch: `194fac2af6cfc54053318164275e682feac5c750`.
Original checkout clean; EP #37 merged (ancestor `533a31e`). FILE worktree
clean at `f78683d4fab9a52dcdd315d269d7ff9c488dfcdd`; remains frozen.
Isolated branch: `fix/ep-r2-entry-factorized-recall`.

## Authority and invariants

EP-LAB-01 is factual baseline, not a new discovery task. The seven required inputs
were read; [hashes and pins](ep-r2/authority.json) identify them. Synthetic
`EpR2Fixtures` extracts only the typed LabV2 builder. No real source is included.
AIR remains 2.0.0, entry.possibilities@2; no upstream changes or repins.

1. Equivalent simultaneous conditions are order independent in admission,
   candidates, remainder and supports.
2. ExternalUnknown opens content uncertainty; it is not a strong assignment.
3. PossibleLiterals contributes supported alternatives and remainder, without kill.
4. Operational cross-base MAY effects are not positive entry contradictions.
5. Real strong contradiction remains INVALID_IR/I-17 at validated AIR admission.
6. Only positive KillAuthority permits an executed strong overwrite.
7. Independent alternatives do not require complete Cartesian Store worlds.
8. Any lost correlation may overapproximate; supported candidates cannot disappear.
9. No caps, first-K, truncation, timeout-as-success or closed remainder shortcuts.
10. Candidate support, byte provenance, source gaps, captures and BEFORE survive.

Unknown storage stays unknown. Distinct IDs and PRIVATE are not separation proofs.
No query-time entry reseed. No second solver or normative AIR change.

## Executed REDs and strong controls

Command (Maven repository is a task-owned copy of cached dependencies; AIR compiled
from the exact pinned checkout using the existing upstream_fast wrapper):

```sh
mvn -o -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-values -am \
  -Dtest=CfgPreflightTest,KillAuthorityTest,EpR2EntryTest,EpR2FactorizationTest,RegionalInitialTest test
```

Five semantic failures, zero errors/skips in the changed values cohort:
F1-open, F-order (all six permutations required), H2, connected G entry factor,
and seven independent slices within one Region. Logs:
`.harness-results/ep-r2/w0-red-02.log`. The first command failed test selection in
the parent reactor; `w0-red.log` is setup evidence, not a semantic RED.

F5 real contradictory literals remains INVALID_IR/I-17. RegionalInitialTest's
five controls pass, including exact overwrite through a backedge and provenance
of consistent overlapping strong literals. KillAuthorityTest also passes.

## Reused pre-fix measurements

[Complete selected baseline](ep-r2/lab-baseline.csv) records preparation/solve
times, targets, groups, max bases, Stores, worklist, transfers, joins/comparisons.
[Product baseline](ep-r2/lab-product-baseline.json) records candidates, remainder
and support. These are LAB measurements on identical consumer production, not
new campaign executions. Timeouts retain censored counters; blank is not zero.

| Case | Admission | Structural baseline | Product |
| --- | --- | --- | --- |
| F1-open | UNSUPPORTED | projected MAY collision | PARTIAL, empty candidates |
| P/X/X versus X/X/P | ACCEPTED / UNSUPPORTED | same simultaneous facts | PROG0000 / empty |
| H2 open | UNSUPPORTED | no intervening writes | PROG0002 lost; remainder true |
| G open P=1/2/4/7 | ACCEPTED | entry 256 / 6561 / 390625 / censored ≥675000 | solve TIMEOUT |
| I empty n32 writes32 | ACCEPTED | ≥1048576 intermediate alternatives | TIMEOUT |
| E1 scaled diamonds | ACCEPTED | one 8-base group, 128 entry Stores | PROG0000 + remainder |
| E3 scaled diamonds | ACCEPTED | one 8-base group, censored ≥562500 | TIMEOUT |

I and scaled E/G will be rerun selectively after the domain changes. The new
oracle must measure materialized structure, not merely runtime.

## Gates and boundaries

Impact class C4: RD entry admission and RV shared semantic domain; CFG construction,
AIR contract/codec and producer semantics stay unchanged. Focused RED/GREEN,
affected module tests, selected AIR-to-dependencies JSON verticals, policy mutants,
then one final repository FAST after stabilization. No automatic CardDemo, CICS,
FILE, historical corpus or multi-repo full campaign. Existing strong/copy/loop
oracles cannot be weakened to hide regressions.

W2 must document the domain before modifying it. Stop for human review if the
design requires a second solver, AIR changes, unsupported candidates, weakened
MUST/contradiction, arbitrary caps or removed provenance. Final status is review
readiness, never merge; REAL CASE remains AWAITING MANUAL RE-RUN.
