# EP-R2 — Order-Independent Entry & Factorized Recall

Scope: analysis-cfg, branch `fix/ep-r2-entry-factorized-recall`, persistent
[Draft PR #39](https://github.com/Gustavo2358/analysis-cfg/pull/39). No merge or
auto-merge. Human semantic/architecture review remains required.

## Result and authority

Entry admission uses original validated facts and their kind/resolution. AIR I-17
remains the strong-consistency authority. Projected cross-base MAY targets still
model possible effects, but cannot manufacture an entry contradiction.
ExternalUnknown widens the boundary and carries uncertainty/evidence; it is not
a fake strong assignment or a removed condition. PossibleLiterals is weak.

Regional Values uses a shared ordered decision DAG over existing partition
segments. Independent suffixes share structure; entry, weak writes, copies and
joins no longer enumerate complete Stores. Exact branch/copy correlations are
retained where represented. Only existing positive KillAuthority permits kill.
No second solver, caps, guessed separation, AIR change, query-time reseed or
consumer VALUE reinjection. The consumer and semantic JSON contracts did not change; domain telemetry gains explicit
structural and cumulative allocation counters.

The [W0 contract](ep-r2-w0-contract.md) freezes the LAB authority and initial REDs.
The [domain decision](../domain/factorized-regional-values.md) was committed before
the solver representation changed. Its assumptions, complexity and local-read
limits remain part of this handoff.

Base after actual fetch: `194fac2af6cfc54053318164275e682feac5c750`. EP #37 is merged
in that base. AIR pin `5b8a5c231958b62de34583d871cbb10b473d53e8` and analysis-ir pin
`6b8ce96f5b1020199e3fcceedf81d931a3b8d2ff` remain unchanged. The final selected E2E
run used clean commit `f8f0494e4ef0a1a8f8ef9e73f9ea103bcfe3dff2`, including the canonical-level fix. Later
changes only record the final evidence and handoff. [Execution metadata](ep-r2/after-execution.json)
contains the exact production hashes and all dependency pins.

## Before / after evidence

BEFORE is REUSED EP-LAB-01 evidence, with frozen hashes and original counters in
[baseline CSV](ep-r2/lab-baseline.csv) and [product baseline](ep-r2/lab-product-baseline.json).
The consumer production at the fetched base matches that baseline. We did not
repeat the entire LAB. Censored timeout values are lower bounds, not completed runs.

AFTER is NEW evidence: [full metrics](ep-r2/after-metrics.json),
[CSV](ep-r2/after-metrics.csv). Every selected successful product traversed real
AIR JSON decode, CFG, RD, RV, DependencyAnalysis and CLI dependencies.json, with
byte-identical memory/CLI JSON. F5 is intentionally rejected before solving.
All six F-order products have identical candidates, remainder and support lists.

| Case | BEFORE | AFTER retained structure | RV solve ms (observation) |
| --- | --- | --- | ---: |
| F1 open X/X | UNSUPPORTED, artificial overlap | ACCEPTED, unknown remainder | see CSV |
| six P/X/X permutations | order changes acceptance/recall | all ACCEPTED; PROG0000 + support + remainder | see CSV |
| H2 | PARTIAL, zero candidates | all four sites retain PROG0002 + place-2 support + remainder | 20.46 |
| G P=1 | 256 boundary Stores | 16 boundary edges; max state 100 edges | 48.78 |
| G P=2 | 6561 boundary Stores | 24 boundary edges; max state 136 edges | 59.18 |
| G P=4 | 390625 boundary Stores | 40 boundary edges; max state 208 edges | 76.36 |
| G P=7 | censored ≥675000, TIMEOUT | 64 boundary edges; max state 316 edges | 108.30 |
| I empty-n32-writes32 | ≥1048576 alternatives, TIMEOUT | max state 528 edges / 32 nodes | 427.75 |
| V2-E1 scaled diamonds | 128 boundary Stores | 15 boundary edges; max state 16 edges | 35.59 |
| V2-E3 scaled diamonds | censored ≥562500, TIMEOUT | 64 boundary edges; max state 316 edges / 64 nodes | 114.24 |

The G boundary curve is 8×(P+1) materialized edges, not (P+1)^8 complete worlds.
One-region independent slices and the 32-component × five-alternative algebra
oracle separately exercise segment factorization. I has zero explicit boundary
edges because unspecified content is implicit; it does not mean empty semantics.
Its empty known candidate set is honest (no literal source), with remainder open.

Edges, decision nodes and complete Stores are different units. `maxStateAlternatives`
counts unique reachable edges in one published/transfer state, including evidence.
`maxComponentCardinality` counts distinct contents of a component. Cumulative
interning is also measured: I retains 33,327 interned nodes / 356,244 edges across
all intermediate relations; G P=7 records 3,829 / 15,296, E3 3,816 / 15,252.
The arena remains alive for the execution. These totals must not be hidden by
reporting only a final root. None counts complete Cartesian worlds.

The CSV/JSON include separate preparation and RD/RV solve durations, targets,
groups/max bases, worklist pushes/max size, operations, joins, comparisons and
domain metrics. Existing K×N target expansion remains: I prepares 1024 targets.
Cold JVM wall times corroborate structure; they are not hardware-independent
thresholds. E1/E3 use the same 1005-operation CFG and differ in separation and visibility
as in the LAB; their complete fixture parameters are in EpR2Probe.

## Acceptance and attempts to falsify

| Obligation | Executed oracle / result |
| --- | --- |
| 1–2: P/X/X order and open X/X admission | EpR2EntryTest, all six product permutations and F1 pass |
| 3–4: H2 and P+X recall | PROG0002 / known possible retained with source support and honest remainder |
| 5: real strong contradiction | F5 and I-17 focused negative control remain INVALID_IR |
| 6–7: positive MUST; MAY/UNKNOWN cannot kill | MUST product leaves only NEWPROG1, closed; KillAuthorityTest, EpR2TransferTest pass |
| 8–10: G/I/E3 factorization | structural assertions, zero-initial I and all selected scale cases pass |
| 11: copies, BEFORE, support/provenance | RegionalComposition/Transfer/Fit/Provenance and EP-R1 logical-place tests pass unchanged |
| 12: loop fixpoint | branchAndLoopSupportsConvergeWithoutPathHistories and overlappingCopyLoopHasExactlyTheFiniteConcreteImages pass |
| 13–14: supported candidates survive to JSON | 17 selected verticals pass, including candidate-specific support and remainder checks |
| 15: final FAST | PASS: 493 Java tests, zero failures/errors/skips; Python and compiled boundaries pass |

W6 also covers X/X, P/X, P/P, Literal/X in both orders; same-place unknown versus
literal; strong overwrite between MAY writes; unknown alias without separation;
multiple slices in one Region; multiple bases; cross-base copy groups; unknown
writes without possibilities; branch join laws/predecessor permutations; normal
CALL result AFTER/OUTCOME versus BEFORE; NamedObject/ObjectPlace/Choice queries.
Existing exact concrete, metamorphic, provenance and EP-R1 laws were not weakened.

W6 found and fixed a local capture defect: Choice must capture one selected source,
not project every alternative's components together. The valid focused fixture
failed its structural bound against `3fc5ac1`, then passed after `ba9a290`.
The fixture explicitly remains PARTIAL_ANALYSIS/INCOMPLETE_VALIDATION because the
pinned AIR validator does not discharge that FitText Choice precondition. It still
preserves PROG0000/PROG0001 with support. This is an explicit validation limitation,
not evidence of disjointness or an invented successful complete validation.

## Policy mutants

[Eleven results](ep-r2/policy-mutants.json) are KILLED by semantic assertion
failures, with zero target errors and successful restored builds/tests. Each run
establishes baseline GREEN first, restores source in `finally`, then rebuilds and
tests unmutated source in `finally`. The mutations cover fake-strong unknown,
cross-MAY entry conflict, order-sensitive admission, P+X loss, MAY kill, weak old
alternative loss, join candidate loss, alias-as-disjoint, entry reseed, disabled
MUST kill and unshared Cartesian suffixes. Cartesian qualification uses small G,
not a timeout/OOM as a substitute for a semantic failure.

Earlier raw runs are `.harness-results/ep-r2/mutations-01` and `mutations-02`. The first
alias-as-disjoint attempt failed a parent test with an exception before executing
the selected oracle; it is NOT_QUALIFIED and is not counted as a kill. Its replay
uses an unaffected reactor sentinel and fails the intended semantic assertion.

## Validation ledger and limits

NEW: W0 five semantic REDs; W1 focused GREEN; domain-family 104 tests; W6 family
107 tests; affected-module final-family 114 passing values tests plus one CALL
fixture setup error, corrected and followed by the two passing transfer tests;
17 final product verticals; 11 qualified mutants. Final FAST includes the fixed
repository cohort and all 15 new methods, and supersedes the fixture setup error.
The final post-canonicalization mutant run is `mutations-final` (all 11 semantic
kills and restored rebuilds pass); the final 17-case run is `product-final-21` on Temurin 21.0.12+1.1.
Raw logs remain under `.harness-results/ep-r2/`. Setup/compile errors, watchdogs
and incomplete runs are never counted as semantic successes or killed mutants.

REUSED: selected pre-fix LAB measurements, unchanged upstream contracts and
production hashes, earlier focused tests where subsequent deltas did not invalidate
them. NOT RUN: real program, broad corpus, CardDemo E2E, CICS, FILE, full multi-repo
or complete historical campaigns. The fixed FAST includes its existing small
CardDemo baseline harness unit test; no CardDemo campaign is launched.

Genuinely correlated relations and a query/read requesting a composite value may
still have exponential output. Node interning retains intermediates until execution
ends; the observed I allocation is not a claim of optimal asymptotic work. The
generic solver and its failure contract are unchanged. Tested open-storage entry
cases no longer discard independently supported candidates; unsupported CFG/AIR
capabilities remain outside this qualification. No residual solver-incompleteness
blocker was found in this synthetic incident class. This does not qualify every AIR
program or establish performance of the confidential source.

The final gate exposed a wire determinism regression in cumulative interning counts:
StoragePartition ordinals follow input inventory, which changed the DAG variable
order. Candidate/support observations were equal, but the complete JSON differed.
The unchanged RegionalWireTest caught it. `f125c58` assigns canonical DAG levels
by base identity/range, preserving the shared partition and RD.
`canonical-levels-focused.log` passes the affected values, dataflow, dependencies
and adapter oracles, including full wire equality. Final measurements and mutants
were renewed after this delta; the failed FAST logs remain preserved.

The next FAST attempt passed all 493 Java tests and W1–W4 architecture, then
found a compiler-only W5 inventory difference (`java.lang.Record` in unchanged
launcher bytecode): local JDK 25 versus CI JDK 21. Recompilation with installed
Temurin 21.0.12+1.1 passed the original W5 inventory unchanged. W3 was captured
with that same compiler. Final selected E2Es and FAST use JDK 21; development
and the final mutant run used JDK 25 with release 21. Source semantics did not
change during compiler alignment. No failed gate is reported as PASS.

## Human review and confidential real case

Review EntryFacts/I-17 authority, the shared relation and capture semantics, the
unchanged strong/copy/loop oracles, and the structural/intermediate metrics in PR #39.
Keep the PR Draft until the requested human review flow decides otherwise; do not
merge or enable auto-merge as part of this campaign. The work registry stays
IN_PROGRESS because this repository only calls merged work DONE.

After human review of the synthetics, **perform a new manual run of the real
program**. Do not add or upload its source/AIR, names, literals, paths, logs or
dependency details. Record only nonsensitive aggregate conditions by kind,
regions/bases, targetsPrepared, admission status, site/candidate/remainder counts,
runtime and domain cardinality (including cumulative interning). Real-case status
remains AWAITING MANUAL RE-RUN regardless of synthetic PASS.

To reproduce after selecting JDK 21 on PATH/JAVA_HOME, run:

```sh
python3 -B scripts/harness/lean.py fast
python3 -B scripts/project/ep_r2.py \
  --output "$PWD/.harness-results/ep-r2-replay" \
  --maven-repo "$PWD/.harness-results/build/m2"
python3 -B scripts/project/ep_r2_policy_mutations.py \
  --evidence-dir "$PWD/.harness-results/ep-r2-mutations-replay" \
  --maven-repo "$PWD/.harness-results/build/m2"
```

Run these sequentially in a quiescent isolated worktree, using fresh output
folders. FAST prepares exact pinned dependencies and builds the modules. Mutation
execution temporarily edits tracked production and always restores/rebuilds it.

## Final disposition

**READY FOR HUMAN REVIEW**, still a Draft PR, with no merge/auto-merge.

| Area | Status |
| --- | --- |
| ENTRY SEMANTICS | QUALIFIED |
| FACTORIZED VALUES DOMAIN | QUALIFIED |
| SYNTHETIC INCIDENT CLASS | QUALIFIED |
| REAL CASE | AWAITING MANUAL RE-RUN |

Final repository FAST: PASS CODE_CHANGE on JDK 21, 493 Java tests with zero
failures/errors/skips, Python checks and all compiled architectural boundaries.
Raw log: `.harness-results/ep-r2/fast-final-04.log` (80.219 seconds, observation
only). No semantic code or gate changed after this PASS. The prior three attempts
remain recorded: missing source inventory, real wire permutation regression, then
compiler-only inventory drift. They are not counted as successful FAST runs.

Final scope audit matches `.harness-results/ep-r2/hygiene-before.json` exactly:
original analysis-cfg and proleap-poc/cobol-lower/air-java/analysis-ir checkouts remain
clean at their initial HEADs. FILE is still clean at
`f78683d4fab9a52dcdd315d269d7ff9c488dfcdd`. All seven LAB input hashes still match.
The final audit is `.harness-results/ep-r2/hygiene-final.json`. No real confidential
source or reproducible build/cache is versioned. Human review and the subsequent
manual real-case rerun are the remaining external steps, not completed campaign
claims.
