# EVALUATE FIRST SLICE

**READY FOR HUMAN REVIEW.** WORK-CFG-040; roadmap authority **HUMAN**.
Drafts: [frontend #42](https://github.com/Gustavo2358/proleap-poc/pull/42),
[lower #19](https://github.com/Gustavo2358/cobol-lower/pull/19),
[CFG evaluation #28](https://github.com/Gustavo2358/analysis-cfg/pull/28).
No merge or auto-merge.

Closed-primary remediation: lower `b61204e6959c6454b4bc4961acf265d99536a237`
keeps the iterative IF/EVALUATE traversal, but requires every frontier to close
through GOBACK. Open continuations/arms and cycles cannot qualify an isolated
BASIC body; completed shared joins remain valid. Target disjointness is preserved.
The no-GOBACK regression failed against the previous lower and now passes, along
with open IF/EVALUATE arms, cycle rejection and valid BASIC cases. Lower FAST and
one qualification-local passed on this final source; all 18 E2Es were rerun A/B.
Frontend and CFG production are unchanged by the remediation.

SP **2.0.0** publishes a simple DATA reference, ordered textual WHEN literals,
explicit arm identity/membership/entry, OTHER presence and normal continuation.
The lower retains decoders 1.1–1.9 and adds a distinct 2.0 decoder. Each WHEN
becomes an existing AIR Branch; each arm completes at its published continuation.
No OTHER retains the no-match edge. Unknown subjects preserve all possible arms.
AIR and analysis-cfg production changes: **NONE**.

18 focused fixtures passed A/B through SP, AIR, CFG and dependency, including
byte determinism, physical-order permutations, explicit branch/completion edges,
nonempty candidate supports and original COBOL provenance.

| Fixture | Known candidates / property |
| --- | --- |
| E1 | PROGA, PROGB, PROGC |
| E2 — no OTHER | OLDPROG, PROGA |
| E3 — literal CALLs | Three distinct sites: PROGA / PROGB / PROGC |
| E4 — nested IF | PROGA, PROGB, PROGC |
| E5 — sequential EVALUATEs | Two sites, each PROGA, PROGB, PROGC |
| Strong updates | NEW2, OTHER; NEW1 killed |
| Known subject | PROGA, PROGB plus model remainder; no path pruning |
| Unknown subject / partial body | PROGA, PROGB, PROGC |
| Three literal arms | PROGA, PROGB, PROGC, PROGD |
| BASIC PERFORM / GOBACK | PROGA, PROGC / PROGB, PROGC |
| Multiplicity | 1, 2, 5, 40 EVALUATEs, all CALL sites retained |
| ALSO / empty arm | Conservative Opaque; no invented entry or silent elision |

The known-subject fixture intentionally retains the no-match path under the
existing Unknown BOOL predicate abstraction. Since its incoming target is
uninitialized, modelValueRemainder also remains true. No new solver or lattice.
Source, interpretation and open-control remainders remain explicit; these known
candidate sets are not claims of closed runtime dependencies.

FAST passed in all three repositories, locally and remotely. Final production
qualification-local passed in frontend (619 tests, one preexisting skip, plus
normalization/naming) and lower (semantic, performance and architecture checks).
The first frontend qualification exposed a canonical-projection/cardinality issue;
it was fixed before the final passing qualification. The initial partial-body
E2E expectation was corrected: MOVE after DISPLAY strongly overwrites its unknown
data effect. Raw failed and successful attempts remain separate.

Supported selection literals are basic text literals compatible with the existing
scalar-text model. TRUE, ALSO, ANY, THRU/ranges, NOT, numeric/complex selections,
complex subjects and general nested EVALUATE remain conservative/unsupported.
An undelimited or empty arm remains partial; known inner facts are still lowered.
No GO TO or unrelated capability was implemented.

## CardDemo comparison

The comparison uses the preserved [ENTRY baseline](carddemo-after-entry-localization.md),
with 73 CALLs already reaching dependency. The earlier full baseline is unchanged.
[Pins](carddemo-evaluate-pins.json) retain upstream
`59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`, AIR and CFG, and identify the exact
qualified frontend/lower commits. The [JSON comparison](carddemo-after-evaluate.json)
contains every affected program and site, candidate supports and all five remainders.

The initial slice's affected run completed **46/46 programs and 47/47 CALLs**
before its Full run. That comparison remains preserved in
[commit 7686a21](https://github.com/Gustavo2358/analysis-cfg/blob/7686a21534585949037dc5290ba107d8beba840f/docs/evals/cp6/carddemo-after-evaluate.json)
and its raw outputs. The remediation uses one new final Full with the corrected
lower, without repeating the affected run. The table reports this final source
against the unchanged ENTRY baseline.

A `/tmp` quota interruption required resuming that same Full: 25 completed
program records were reused and 48 interrupted/uncompleted programs resumed.
Original failure logs and outputs were not overwritten. Resumed outputs live on
the workspace disk through `full/resume`; `full/measurements-final.json` is the
completed aggregate. No successful program was rerun for this recovery.

Compared with the previous EVALUATE lower, all 73 CALL sites preserve candidate
values, classification, reachability and all five remainders. The 67 dependency
products are byte-identical. The JSON's `remediationValidation` records this
comparison and the quota recovery separately from the ENTRY-to-EVALUATE delta.

| Metric | Before (ENTRY baseline) | After EVALUATE |
| --- | ---: | ---: |
| Programs reaching dependency | 67 | 67 |
| CALLs observed / analyzed | 73 / 73 | 73 / 73 |
| Closed resolved | 0 | 0 |
| Partial resolved | 73 | 73 |
| Open unresolved | 0 | 0 |
| Known candidate occurrences | 73 | 73 |
| Programs containing EVALUATE | 46 | 46 |
| EVALUATE occurrences | 264 | 264 |
| Observed unsupported EVALUATE | 264 | 254 |
| Typed EVALUATE (all PARTIAL) | 0 | 10 |
| EVALUATE with modeled control | 0 | 8 |
| EVALUATE-derived AIR Branch / Opaque | 0 / 264 | 16 / 256 |
| CALL sites in EVALUATE Opaque control bounds | 47 | 45 |
| Affected CALL openControlRemainder=true | 47 | 47 |
| Full CALL openControlRemainder=true | 73 | 73 |

Eight typed constructs now have explicit generic control. Two typed constructs,
in CBIMPORT.cbl and CBSTM03A.CBL, lack a proved continuation and retain Opaque.
The other 254 occurrences remain observed/unsupported. This preserves control
structure where proved without claiming general CardDemo EVALUATE support.

Potential CALL impact means membership in an EVALUATE-derived Opaque control
bound; it is not individual causal attribution. The two sites are in CBTRN03C
(checkout and archived variant). All 47 affected sites retain the same known
candidate values and each of the five remainders. Removing those bounds
does not close them: all 73 sites retain source, interpretation, effective
and open-control remainders, while modelValueRemainder remains false. Zero
OPEN_UNRESOLVED does not mean closed dependencies.

The six prior pre-SP blockers remain: four EXEC preprocessing policy failures,
one fixed-format tab and one normalization rejection. Their CALL inventories
remain unknown. Missing DATA COPY stays partial; missing PROCEDURE COPY stays
conservative. No next blocker was implemented.

Final outputs and logs are under `/tmp/evaluate-finding-20260913/{e2e,full}`;
configuration is `/tmp/evaluate-finding-20260913/runtime.json`, and the 18 E2E
results are in `e2e/results.json`. The previous outputs remain untouched under
`/tmp/evaluate-carddemo/{affected,full}`. The evaluator scripts are
`scripts/project/e2e_evaluate.py` and `scripts/project/carddemo_evaluate.py`.
Remote runs are FAST only; CardDemo is local/on-demand.
