# CP6 MULTI-CALL PROGRAM COVERAGE

Status: **DONE / MERGED**. All three PRs merged on 2026-09-12. Discovery preceded production. No receipts/certificates.

## Post-PERFORM baseline

All three PRs were already merged before this task started. GitHub reports
lower #15 at 21:36:12Z, CFG #22 at 21:36:15Z, frontend #38 at 21:36:17Z
on 2026-09-12: historical order differs from the requested frontend → lower → CFG.
No merge was repeated or history rewritten. All source-head FAST checks passed.
Fetch completed; main was checked out, fast-forwarded to origin/main and clean
in every repository before creating feat/multi-call-program branches.

| Repository | Actual merge baseline |
| --- | --- |
| proleap-poc #38 | `a14147106f329cce2f0006aa94194b47db96f367` |
| cobol-lower #15 | `546af4067bc42540bb73e88684e18e3082ddfb94` |
| analysis-cfg #22 | `e200b101455fa7addf59413ce5e6548c03d9116f` |

Immutable unchanged pins: analysis-ir `51b4d9a8ae0364232bd97103cd73a77e1a34996c`;
air-java `760593b923ca7311f699547c36349f54eb0dac42`.

## Discovery

Discovery executed at the exact merge pins before any production edit. Raw products
and stage logs: `/tmp/multicall-discovery/fixture-{1..7}/`; build log
`/tmp/multicall-discovery-build.log`; raw matrix `/tmp/multicall-discovery/matrix.json`.

| Fixture | Frontend/SP | lower | AIR | CFG | dependency | First boundary |
| --- | --- | --- | --- | --- | --- | --- |
| 1 linear | PASS: 2 MOVE, 3 CALL, GOBACK, all continuations | UNSUPPORTED_SLICE, exactly one CALL | not produced | not run | not run | lower |
| 2 diamond | PASS: IF and all arms/continuations | UNSUPPORTED_SLICE, one IF/CALL/GOBACK | not produced | not run | not run | lower |
| 3 two diamonds | PASS: 2 IF, 3 CALL, complete individual facts | same cardinality refusal | not produced | not run | not run | lower |
| 4 CALL/PERFORM/CALL | emits SP; PERFORM OBSERVED/UNSUPPORTED, body exit unavailable | UNSUPPORTED_SLICE | not produced | not run | not run | SP PERFORM profile |
| 5 cumulative | emits SP; PERFORM OBSERVED/UNSUPPORTED, body exit unavailable | UNSUPPORTED_SLICE | not produced | not run | not run | SP PERFORM profile |
| 6 point-sensitive | emits SP; PERFORM OBSERVED/UNSUPPORTED, body exit unavailable | UNSUPPORTED_SLICE | not produced | not run | not run | SP PERFORM profile |
| 7 snapshot | PASS: 3 MOVE, 3 CALL, GOBACK, all continuations | UNSUPPORTED_SLICE, exactly one CALL | not produced | not run | not run | lower |

`MultiCallModelTest`: 3/3 PASS before lower edits; 22 other reactor tests PASS (25 total)
(`/tmp/multicall-model-3.log`). Direct three-Invoke model, IF join then overwrite of
same object, orphan literal BADPROG, 2 dynamic + 3 literal sites, distinct supports,
exact per-site identity/offset/subject/valuePoint, global deduplication, reversal,
PossibleValues=1 and Reachability=1 with exactly N reach + D value queries.
Two earlier invocations failed because the strict reactor requires a selected test
in each module; neither was a semantic RED. Production analysis-cfg change = NONE.

## Composition rule before implementation

SP1.7 keeps typed MOVE/IF/PERFORM/CALL/GOBACK facts and relaxes the SP1.6 PERFORM
primary-flow invariant explicitly. PrimaryStatements remains ordered direct root
membership; IF direct arms complete that inventory. Unique non-reentrant PERFORM,
two distinct direct paragraphs, nonempty MOVE-only target, primary final GOBACK,
complete inventory, exact identities/provenance and closed explicit continuations
remain mandatory. No second PERFORM context or ordinary target entry is admitted.

SUPPORTED_CP6_PROGRAM admission follows the explicit entry and normal continuations,
indexes IF arm membership and the isolated target, and verifies every observed
statement is consumed exactly once. Unknown/extra statements and cycles refuse.
Root MOVEs remain instructions until a terminator; CALL emits Invoke to its published
continuation; IF emits the existing Branch/linear arms/Jump; PERFORM emits the existing
Jump/body/unique-resume Jump. Generic block boundaries are entry, every explicit
terminator continuation, IF arm entries and PERFORM target. No fixed number of CALLs,
no syntax inspection, no positional successor inference. Typed relation traversal is
finite O(statements + references), plus existing canonical identity ordering; storage,
predicate, MOVE, CALL and GOBACK semantics reuse current translators and premises.

No new AIR/result wire, solver, lattice, RD, EVALUATE, GO TO or multi-PERFORM.
Program names are derived as distinct reachable DependencyResult.edges candidates;
site remainders remain independent and no global closed-world claim follows.

## Implemented composition and qualification

Frontend productive source `01ee3492c04736850f1f092bea2027b0d0d63366`, Draft
[proleap-poc #39](https://github.com/Gustavo2358/proleap-poc/pull/39), publishes SP1.7
with the existing typed facts. Its final qualification-local PASS: 602 tests,
1 existing skip, normalizer/naming/architecture checks. Raw log:
`/tmp/multicall-frontend-full.log`. Remote FAST at this SHA is SUCCESS.

Lower productive source `1fe4a56d6cb7e81ff6fbd249808f65a72195322b`, Draft
[cobol-lower #16](https://github.com/Gustavo2358/cobol-lower/pull/16), provides
SupportedProgramAdmission/Lowerer/Assembler. IF, PERFORM and primitive handlers
share qualified semantics; canonical hashing visits data/MOVEs once. All seven
SP snapshots, explicit relation/closure refusals, unknown statements, SP1.6 legacy
shape, and 64/128 literal CALL composition pass. Final qualification-local PASS:
244225 semantic and 39215 performance checks; `/tmp/multicall-lower-final-full.log`.
Later lower commit only records this result; downstream intentionally pins the
qualified productive SHA. An initial Full found diagnostic precedence regression
for unsupported CALL handlers with missing continuation; it was corrected and the
complete Maven verify plus final qualification passed. Original failed log retained
at `/tmp/multicall-lower-full.log`.

Analysis-cfg production change: **NONE**. Local FAST PASS (97.250 seconds).
The seven real programs plus W1 literal/dynamic singleton and open-no-MOVE
regressions PASS using isolated clean producers at the exact lock pins:
`/tmp/multicall-pinned/producers/producers.json`; log `/tmp/multicall-pinned-e2e.log`.
Every case compares SP/AIR/CFG/dependency A/B bytes; fixtures 3,5,6 also reverse
physical AIR sequences. The oracle correlates explicit source flow to operation
coverage/IDs, then checks each site's sequence, offset, subject, valuePoint,
candidates, supports, edges and independent remainders. Literal targets have no
value query; metrics show one PossibleValues preparation/run and one Reachability
run per Entry, with N reachability plus D computed-target queries.

| Fixture | CALL sites in explicit control order | Distinct reachable program names |
| --- | --- | --- |
| 1 | PROGA; PROGB; PROGC | PROGA, PROGB, PROGC |
| 2 | PROGA/PROGB; PROGC | PROGA, PROGB, PROGC |
| 3 | PROGA/PROGB; PROGC/PROGD; PROGE | PROGA, PROGB, PROGC, PROGD, PROGE |
| 4 | PROGA; PROGB; PROGC | PROGA, PROGB, PROGC |
| 5 | PROGA; PROGB/PROGC; PROGD | PROGA, PROGB, PROGC, PROGD |
| 6 | PROGA/PROGB; PROGC | PROGA, PROGB, PROGC |
| 7 | PROGA; PROGB; PROGA | PROGA, PROGB |

All ten focal local challenges KILLED (`/tmp/multicall-challenges.log`): second CALL
omission, all candidates at all sites, final state at every point, lost literal,
duplicate global names, unreachable BADPROG inclusion, values run per site,
cross-site supports, skipped PERFORM body (actual AIR mutation reanalyzed), and
first diamond reused at the second site. Original outputs remain unchanged.

Coverage stays PARTIAL with source/interpretation/effective remainders retained at
each real site. In fixture7, the final snapshot candidate is correctly PROGA, but
its modelValueRemainder is true because the existing external CALL may-write effects
open the model after the copy; earlier sites' model remainders remain false. This
wave preserves that semantics. The union is exact for reachable **known** candidates
and does not assert a closed runtime target set. The AIR-only isolated models have
closed value remainders and exclude an actual orphan BADPROG Invoke.

Final analysis-cfg **qualification-local PASS** on
`3c16a35eaa1997acd83d0cb91c70026273080cd8`; exit 0, raw log
`/tmp/multicall-cfg-full.log`. Real final products and per-process outputs remain at
`/tmp/move-cfg-build/w2d-ddavvnzb/`: W2D closed/open, four MOVE-copy cases, three
PERFORM cases, W1 literal/dynamic/open and all seven multi-CALL programs. No
production/test/fixture/harness changes follow this qualification, only documentation.
No remote cross-repo E2Es; FAST contains the focused multi-site, same-object, IF,
batching and orphan tests. All ten focal challenges passed before the final Full;
no additional broad mutation campaign was run.

Draft reviews: [frontend #39](https://github.com/Gustavo2358/proleap-poc/pull/39),
[lower #16](https://github.com/Gustavo2358/cobol-lower/pull/16),
[CFG #23](https://github.com/Gustavo2358/analysis-cfg/pull/23). Remote FAST is SUCCESS
at frontend `01ee3492c04736850f1f092bea2027b0d0d63366`, lower documentary HEAD
`3d38514cbd48422e30155c75eaa61ce8cd654d8f`, and CFG qualified source above.
GitHub checks on the final Draft head remain the remote authority.

CP6 MULTI-CALL PROGRAM COVERAGE: **IMPLEMENTED / AWAITING HUMAN REVIEW**.
Input: supported COBOL program. Output: all possible known subprograms from every
reachable CALL site, with per-site detail and the exact program-wide union.
No merge/auto-merge. WORK-CFG-036 is DONE / MERGED. NEXT: **COMPOSITIONALITY + PARTIAL CONSERVATIVE LOWERING**.


## Merge closeout

GitHub confirmed #39/#16/#23 MERGED; fetch, main fast-forward, clean working trees
and equality with origin/main verified before this wave. Actual merge commits:

- proleap-poc: `ff3704911e53b3d8b337fc610cf84343cdc5fe90`
- cobol-lower: `797dcbd6f05f12661299339746c607b5b57ac1ba`
- analysis-cfg: `664a932af0421d8a604b77d8de5852e7ac8db955`
