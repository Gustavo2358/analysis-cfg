# DVI — Declarative Value Inference

DVI-W0–W3 selected qualification complete. D01–D17 PASS; D18 NO_REAL_GAIN with
measured blockers. Handoff is ready for human review subject to Fast CI on the
current Draft head. No merge or auto-merge. Work items remain IN_PROGRESS because
repository DONE requires merge. Product completeness remains PARTIAL.

Frontend Draft [#49](https://github.com/Gustavo2358/proleap-poc/pull/49),
lower Draft [#26](https://github.com/Gustavo2358/cobol-lower/pull/26),
CFG Draft [#35](https://github.com/Gustavo2358/analysis-cfg/pull/35).
**CFG production Java is unchanged**, as are AIR model/codec/normative contracts.
Existing Regional Values/RD and CALL/LINK/XCTL consumers use source-proved entry
literals. There is no synthetic MOVE, second solver, or CICS logic in the solver.

## Pins and contracts

| Input | Integrated CICS baseline | DVI downstream pin |
| --- | --- | --- |
| frontend | be74bcb3cfe46357e6a692a45b130c4e0a61624b | 58846a65a641c583387bc2dd4ae6c45306765611 |
| lower | 8b87818395a6e96978380c288ec1cbeb81bfa14a | fbbf86dfcb1af9e4c41fbca13ce19798e423152b |
| CFG | 63c66d6a5cc1a7a9d30bbfc70ce4f12426242cfd | this Draft PR; no production delta |
| AIR Java | eaf83c6233d347348a3927b5983de03cde62554a | unchanged |
| normative AIR | 31893d1f4d203d19a61a750e2c4220120d9dab84 | unchanged |

Baselines were verified as integrated main after CICS merges, not mixed approved
heads. Lower pins frontend and AIR; CFG pins both producers and AIR in
`docs/sources/sources.lock.json`. SP advances 2.14.0→**2.15.0**, storage 1.3.0→**1.4.0**;
AIR **2.0.0/JSON 1.0.0** and dependency JSON **1.1.0** are unchanged.

Global CLI mode stays UNKNOWN by default; the explicit IBM1047 fixed DISPLAY
profile is required. Required per-condition proof distinguishes NONE,
EXPLICIT_INITIAL, EXPLICIT_PRESERVED, PROGRAM_INITIAL and DECLARATIVE_INVARIANT.
Lower admits consistent proof/kind/mode/extent/locality and preserves proof/version
in a derived VALUE origin on existing LiteralInitial. No global AUTO contract.

## D01–D18 closure

Evidence: handwritten `dvi_fixtures.py`, `e2e_dvi.py`, frontend
DeclarativeValueInferenceTest, lower DeclarativeValueSuite and existing regional
entry/composition tests. `dvi/selected-summary.json` records the 25 actual outcomes.

| ID | Result | Direct witness / assertion |
| --- | --- | --- |
| D01 | PASS | explicit-initial: PROGA, EXPLICIT_INITIAL |
| D02 | PASS | direct-write: ordinary UNKNOWN has no VALUE candidate |
| D03 | PASS | program-initial: PROGA, PROGRAM_INITIAL, global UNKNOWN |
| D04 | PASS | invariant-call: PROGA, DECLARATIVE_INVARIANT, global UNKNOWN |
| D05 | PASS | disjoint-write: PROGA retained |
| D06 | PASS | direct-write: overlapping later lifetime write blocks invariant |
| D07 | PASS | group-write, alias-write, renames-write: no invariant |
| D08 | PASS | slice-disjoint, exact [8,9) vs target [0,8): PROGA |
| D09 | PASS | slice-overlap, [7,8): no invariant |
| D10 | PASS | slice-dynamic: unknown destination fails open |
| D11 | PASS | unknown-effect, escape; frontend disabled CICS/functions/unmodeled effects |
| D12 | PASS | data-move-call: PROGA via existing copy_bytes |
| D13 | PASS | data-move-link and data-move-xctl: PROGA, shared consumer/engine |
| D14 | PASS | runtime-branch and runtime-cics: PROGA plus modelValueRemainder=true |
| D15 | PASS | loop-kill: OTHERPGM, no PROGA; existing entry/backedge/partial-kill tests |
| D16 | PASS | renamed-format equivalent to slice-disjoint |
| D17 | PASS | incomplete-input plus frontend incomplete coverage: NONE, gaps, open target |
| D18 | NO_REAL_GAIN | Three unchanged CardDemo witnesses below; no false closure |

Additional adversarial pair: remove-one-blocker remains NONE; only
remove-all-blockers enables PROGA. Adding alias overlap or making exact ref-mod
dynamic removes the invariant. The final oracle also rejects a false model
closure in both runtime fixtures even when source remainder remains true.
Explicit PRESERVED remains PRESERVE without a declared-literal candidate.

## Actual real candidate delta

Unchanged CardDemo commit **59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e**. The real
runner verifies exact source hashes before invoking production CLIs.

| Site | Before candidates / open | After candidates / open | Delta |
| --- | --- | --- | --- |
| COACTUPC XCTL | {} / true | {} / true | NO_REAL_GAIN |
| COCRDSLC XCTL | {} / true | {} / true | NO_REAL_GAIN |
| COUSR01C XCTL | {} / true | {} / true | NO_REAL_GAIN |
| COACTUPC literal CALL | {CSUTLDTC} / true | {CSUTLDTC} / true | retained control |

For each real XCTL: modelValueRemainder=null (no modeled regional query),
sourceValueRemainder=true and interpretationUnknownRemainder=true, before and
after. For CSUTLDTC: model=false, source=true, interpretation=true, unchanged.
Null is unavailable, not a closed value set.

All three retain INPUT_MISSING for DFHAID/DFHBMSCA; all initial proofs are NONE.
The first two contain COMEN01C VALUE→MOVE and a runtime alternate. Their SET,
INITIALIZE, unmodeled CICS effects and COMMAREA exposure are additional independent
proof blockers observed in W0. COUSR01C is a literal-MOVE/runtime-group control.
Supplying missing copybooks alone is not evidence that all other blockers close.
No CardDemo source/copybook was modified and no gain is inferred from VALUE counts.

## Tests, reuse and limits

New executions: six W0 production CLI pipelines; W1 focused 47 frontend tests and
one successful stabilized FAST (255 JUnit tests plus policy); W2 lower DVI/entry/
CICS focused checks (including eight malformed-proof cases), lower FAST (2,340
core assertions, existing adapter families and policy), 21 selected E2Es; 13
existing RegionalInitialTest/RegionalCompositionTest checks; W3 13 frontend DVI
adversarial tests, 25 selected E2Es, and three real before/after pipelines.
Final test-only oracle strengthening was checked against all 25 preserved product
sets plus two deliberately false-closure mutations; it did not rerun producers.
Final remote FASTs are attached to the three Draft PRs.

Raw production command lines, stage exit codes, source hashes, immutable SP/AIR
inputs and products are preserved in the local artifacts DVI campaign. W2 ran
with in-flight lower changes; W3 producer heads are the exact pins above. W3
recorded CFG baseline HEAD before committing test/evidence scripts; its production
Java is byte-identical to this PR. Frontend f853bc3→58846a6 adds tests only; W1
production FAST evidence is reused and final remote FAST validates the final head.
Prepared AIR builds/classpaths and unaffected CICS evidence are reused explicitly.
Earlier RED, stale-version assertion and build/preflight failures are retained
and explained in the local handoff, not overwritten.

Not executed: full repository suites, 73-source corpus, historical mutation
suites, AIR FAST/full. The bounded source proof and coordinated SP reader have
focused admission/negative and vertical evidence; no generic solver production
change or unexplained regression justifies a broad shared-core/corpus gate.
The mandatory campaign lean gates take precedence over generic full guidance.

The lifetime slice requires supported fixed textual VALUE, exact codec/view,
independent local WS, complete canonical inventory and no overlapping write or
unproved effect/escape. Dynamic/unresolved writes, unsupported effects, nested
programs, signatures/declaratives, incomplete input and external exposure remain
conservative. CICS permits only the existing contribution's input-only
PROGRAM/NOHANDLE slice; other options block invariance. No whole-program purity
claim or real CardDemo uplift is made.

## Reproduce and review order

```sh
python3 -B scripts/project/e2e_dvi.py --frontend FRONTEND --lower LOWER --m2 M2 --work NEW_OUTPUT
python3 -B scripts/project/e2e_dvi_cohort.py --frontend FRONTEND --lower LOWER --m2 M2 --before W0_OUTPUT --work NEW_REAL_OUTPUT
```

Helpers reuse existing prepared runtime classpaths; no build per fixture. Oracles
inspect candidates, all three remainders, BEFORE point, supports, proof origin,
physical copy_bytes, no synthetic MOVE, and overwritten VALUE absence.
After human review: frontend → repin lower → lower → repin CFG → CFG.
Repin to actual merge commits; do not use floating main or omit changed contracts.
No AIR/analysis-ir PR or merge is required by DVI. Original checkouts are preserved.
