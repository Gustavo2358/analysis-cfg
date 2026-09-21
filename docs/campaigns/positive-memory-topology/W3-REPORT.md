# POSITIVE_MEMORY_TOPOLOGY — W3 report

Status: **POSITIVE_MEMORY_TOPOLOGY_W3_READY_FOR_REVIEW**. CP0–CP5 complete on the existing draft PRs. The W0/W1/W2 contract remains authoritative. No new AIR variant, wire shape, storage mode or solver rule was introduced.

## Baseline and pins

CP0 heads and bases are frozen in [W3-PLAN.md](W3-PLAN.md). The final producer SHA is `da84bfc3eca555d7d74b0a85b73819a75d1f48c7` (#58, draft); lower SHA is `9ed53b6af6590101aeb9f9d27912718f0383c444` (#34, draft), pinned to that producer. Analysis-cfg #45 pins both immutable SHAs. AIR #21 remains `26016f10460336f237a33b2ed126a6a1427f0207`; IR #8 remains `b26465964fe75f944f6324df63330d69f33d77cd`. They need no W3 commit. The base SHAs for all five PRs remain those in W3-PLAN.

## Checkpoints

| Checkpoint | Change and oracle | Status |
|---|---|---|
| CP0 | Five heads/base ancestry, PR draft state, pins, worktrees and W1/W2 cohorts frozen; causal matrix in W3-PLAN | DONE |
| CP1 FILE | Profile/option gap no longer downgrades independently proved `MUST_UNKNOWN` or `COPY_BYTES`. Lower accepts strong steps when owner, exact receiver and alias proof remain, irrespective of unrelated syntax/assignment profile. Real unproved address/alias still produces MAY. RED/GREEN producer `FileIoEffectPlanTest`, lower `FileMemoryEffectsSuite` | DONE |
| CP2 CICS | Nominal PROGRAM target survives absent physical name area; absent target uses bounded Opaque without invented runtime name or foreign memory. CICS FILE missing input name uses the same bounded structural form and retains typed option effects; literal without typed target remains invalid input. LINK return and XCTL no return controls retained. CICS FILE RESP/RESP2 returned effects and LENGTH bounds remain. `CicsProgramControlSuite`, `CicsFileControlSuite` including 1/50 diagnostic mutations | DONE |
| CP3 CALL/control | Frontend uses ordinary CALL successor across paragraph boundary when materialized; lower no longer maps an unavailable continuation to global AllControl. End-of-unit completion remains bounded unknown. IF/EVALUATE/PERFORM/GO TO supported structure unchanged, checked by FAST. Frontend `CallCheckpointW1ATest`, lower `PartialIntegrationSuite` RED/GREEN | DONE in represented variants |
| CP4 residual audit | Unmaterialized regional nominal binding is scoped to its own positive object rather than AllMemory. Missing FILE assignment name no longer becomes runtime `Unknown(TEXT)`; published buffer read/FROM copy remains. Broad explicit bounds and true external unknowns retained. `DependencyPreservationSuite` and `FileMemoryEffectsSuite` validate AIR closure | DONE in touched callers |
| CP5 | 40 logical cases, eight same-AIR physical pairs, EVALUATE40/control20, indep80/160 once, eight legitimate-work measurements and final gates | DONE |

## Residual constructor audit

| Constructor / caller | Cause | Decision | Witness / rationale |
|---|---|---|---|
| `FileIoEffects` profile step downgrade; `FileEffectAdmission` | C orthogonal to a proved step | REMOVE | KEPT LOCK and DYNAMIC ASSIGN retain SUCCESS status/INTO MUST and FROM copy; alias and address B remain MAY |
| `FileMemoryLowering` HavocMust, MAY Opaque, conditional outcomes | A+B | KEEP | External READ target has real unknown contents; bounded MAY has an unproved destination or alias; outcomes are modeled choices |
| `FileMemoryLowering.bound` VisibleMemory | B only when explicit typed unknown bound | KEEP | Current producer emits false for C-only gaps; no diagnostic implies a visible scope |
| `OpaqueOperands.memory` AllMemory | B only on explicit SP unknown bounds | KEEP | Current producer does not derive these bounds from coverage; explicit broad control remains possible |
| `RegionalDataTranslator` UnknownBinding AllMemory without base | C materialization | REMOVE | Unknown nominal object retains own identity and diagnostic; no global alias from missing view |
| `FileResourceLowering` computed Unknown(FILE name) without a typed assignment name | C materialization | REMOVE | Bounded Opaque retains the known resource operation, buffer read and FROM transfer; no fabricated runtime target |
| `CicsInvokeHandler` / `CicsFileInvokeHandler` Unknown(TEXT) on missing input target | C absent materialization | REMOVE | Opaque retains positive option reads/writes and control; nominal/physical target Read remains when available |
| CICS FILE output-name Unknown, true host results and option effects | B | KEEP | INQUIRE output name is supplied externally; RESP/RESP2 normal outcome overwrites remain |
| `InvokeHandler` UnknownRemainder / target Unknown | B when signature/value is genuinely unknown | KEEP | CALL target and normal successor retained; no foreign memory compensation |
| `InvokeHandler` AllControl on unavailable continuation | C/B conflation | REMOVE | Existing bounded local remainder retains genuinely unmaterialized completion |
| IF/EVALUATE/PERFORM condition Unknown(bool) | B structural choice, C evaluator coverage | KEEP | Known arms/body/joins retained; no AllMemory/AllControl |
| FILE SORT/USE LocalControl Opaque | B supported local return context or C unavailable variant | DEFER | W2 bounds already local; unrepresented variants have no new W3 positive facts |
| `UnknownType`, `Precision.OPEN`, source gaps, captured gaps | diagnostics and bounded typed uncertainty | KEEP | No downstream gate executes a gap code; malformed AIR remains rejected by validator |

No COBOL feature-name branch was added to analysis-cfg. The only consumer W3 source addition is a test of legitimate physical work.

## Mechanisms and remaining scope

M04 summary/materialization, M13 CALL/CICS and M14 FILE are **PARTIALLY TREATED** in the new causal witnesses; M10 unrepresented PERFORM family variants and M15 CICS FILE unmodeled option families remain **PENDING outside the represented abstraction**. Existing supported IF/EVALUATE/PERFORM/GO TO behavior is a regression gate, not a W3 redesign. A FILE assignment without a proved resource name, source features without a target/value fact, and external handler families retain explicit coverage. These are not promoted to broad executable effects.

## Gates and integration

Producer FAST: PASS, 391 tests. Producer `qualification-local`: Maven 986 tests passed, one skipped; source normalizer then met the proved preexisting W1 A/B blocker `SOURCE_DEPENDENCY_OWNER_UNPROVED`. W3 did not modify that owner logic. Lower FAST and `qualification-local`: PASS with Java 21, pinned AIR, semantic, capacity and architecture suites. Focal W3 CICS/FILE/CALL/dependency suites: PASS. Analysis-cfg FAST and `W3LegitimateWorkTest`: PASS. AIR/IR unchanged and their W1/W2 qualification evidence remains valid. Raw gate logs are retained locally under `evidence/w3` and `/tmp/w3-*.log` for this run.

Logical integration: 40/40 OBSERVED. Of the 31 W2 shared cases, 30 are exact preservation and `r1--compute` differs only by republished identity/diagnostic serialization; candidates, source support witnesses, timing and normalized edges are preserved. Nine W1/W3 additions are OBSERVED and have no W2 baseline. No unexplained semantic delta. Default remained `LOGICAL_ONLY` with zero physical groups/writes in all 40 cases. Eight physical pairs used the same AIR per C/D case; only explicit `--experimental-physical` executed physical work, positive in all eight. Control stress had no AllControl/AllMemory compensation. indep80/160 preserved W2 structural counts with zero cross-base comparisons/object pairs. The eight legitimate-work witnesses all performed real physical work with required candidates/supports and no detected artificial duplication. Details and raw paths: [W3-REGRESSION.md](W3-REGRESSION.md), [W3-PERFORMANCE.md](W3-PERFORMANCE.md).

## Diagnostic isolation, limits and next scope

The CICS FILE 1/50 diagnostic mutations and FILE profile gap controls preserve target/effect/control and MUST strength for orthogonal C. The logical cohort, control stress and constructor audit show no gap-only global compensation. Typed invalid CICS literal target and the separate undeclared-source negative case remain rejected. A true unknown external READ, RESP/RESP2 result, explicit MAY/Havoc and physical unproved address still retain semantic uncertainty B. The consumer has no new COBOL feature-name branches. No default or physical policy changed.

M04, M10, M13, M14 and M15 remain **PARTIALLY TREATED** where other unrepresented language-family variants lack positive facts; their exact W3 represented paths are closed as above. Further PERFORM variants, CICS FILE option families, FILE assignment/resource names without typed targets and other source coverage belong to a future separately authorized wave. They are not W3 regressions. W4 was not started, no PR was merged, and all campaign PRs remain draft.

Recommendation: review the five permanent draft PRs as the W0–W3 campaign result, with W3 changes confined to #58, #34 and #45. The supported abstraction has no remaining known C-only global compensation in the W3 treated paths. Human merge and any operational policy change remain separate decisions; physical analysis stays experimental and explicit opt-in.

## W3-R1 addendum — human review resolved and implemented

The READY status above records the first W3 closeout. Human review then found the
composition bug documented in [W3-ARCHITECTURE-RESOLUTION.md](W3-ARCHITECTURE-RESOLUTION.md):
a physical materialization gap became self-scoped UnknownBinding, the consumer
widened its cycle to all bases, and every write was broadcast to open objects.
The approved resolution is now implemented in the same draft PRs. The final
upstream pins are IR #8 `b628e4c1a62de61a157cac85ae71ba4cd111052e`, AIR
#21 `fa2487306366acfba8124fd6403f940f1b1a98aa`, producer #58
`1e7af863823a693fe9d36f6b6551e8c586170f50`, and lower #34
`27d1cf6dbd4450dc157e40424fc91ac63fe35be7`. The CFG source lock and CI
checkout use these immutable pins. The anchor's final HEAD is reported by Git
and PR #45 rather than self-referencing this file.

CP0 preserved the RED 32-base witness and bounded-open-object counterexample.
CP1 clarified Cell/UnknownBinding scope and AIR validation without a new AIR
kind. CP2 introduced SP 2.34/storage 1.10 `logicalExactViews`, regenerated the
real partial/REDEFINES fixtures, and lowered the partial A/B pair to distinct
ObjectIds sharing one unit-owned persistent private TEXT Cell. A complete
physical version keeps two views of one Region. Captured logical targets use
their declaring unit's Cell and a child AliasBinding. CP3 removed all-base
cycle retry and write × openObjects broadcast, using bounded DFS and a
base-to-open-object relation index. CP4 added exact/bounded/broad controls,
shared-Cell strong/MUST/MAY/snapshot tests, and diagnostic isolation. CP5
repeated the frozen cohorts and canaries recorded below.

The two mandatory MOVEs through B then A leave only `PROGB   ` at XCTL. A true
MUST Unknown kills an older literal; MAY retains it with a remainder. Copying
to another Cell is a snapshot. Missing COPY, absent physical layout and 0/1/50
orthogonal diagnostics do not alter the executable topology. A/B identity is
derived from declarations and a positive overlay relation, not the MOVE
literals. Partial overlap and independent equal-sized bases do not get a shared
Cell. Explicit AllMemory and VisibleMemory still expand according to their
published scopes. Executable ungrounded cycles are invalid; grounded object
bounds and repeated resolved members are valid.

The final 40-case logical cohort is 40/40 observed; eight same-AIR C/D pairs
are 8/8 observed, with zero physical work in C and positive physical work in D.
W3-to-W3-R1 logical comparison has 37 preserved cases, two classified
cost/diagnostic deltas and one manually classified intentional Cell delta:
`logical--unsupported` moves from `UNSUPPORTED_TARGET_EXPRESSION` to a
known Cell subject at BEFORE with `OPEN_TARGET`, still zero candidates and the
same source/interpretation remainder. Its raw old/new fields and invariant
checks are in `evidence/w3-r1/logical-unsupported-delta-classification.json`.
All eight physical query site projections retain command, candidate/support
multiplicity, BEFORE, and remainders. No unexplained semantic delta remains.

The 32/100/160-base REDEFINES witness always prepares two targets and two
Events for two writes and yields only PROGB. `nominal-cell-160` has 320 writes,
320 targets and 320 Events, rather than a 320 × 160 product. The 160 bounded
open objects test produces 160 LogicalTargets for 160 localized writes; the
explicit AllMemory control still expands 32 bases. indep80/160 preserve their
161/321 targets and Events, zero base comparisons and zero materialized object
pairs. EVALUATE40 retains 40 sites and control20 retains both CALL candidates.
The hardened eight-case legitimate-work test asserts support multiplicity,
MAY remainder, correlated copy candidates/supports and last relevant supports.

Producer FAST passed (392 tests); producer qualification passed its Maven
semantic portion, then met only the A/B-proved preexisting
`SOURCE_DEPENDENCY_OWNER_UNPROVED` blocker. Lower FAST and qualification-local
passed (`SEMANTIC_TEST_COUNT=244391`, `PERFORMANCE_TEST_COUNT=39207`). AIR FAST,
CFG FAST, focal Cell/StorageIndex/StatementEffects/legitimate-work suites and
integrated E2E passed. The IR clarification is documentation and contract
wording; no Analysis IR 2.1 or wire kind was added. Remote checks and final
PR draft/head verification are recorded in the final handoff.

Residual source-language variants and physical operational policy remain the
original W3 future scope. No W4 work started. Logical-only remains default;
physical remains explicit opt-in and operationally interdicted. No merge was
performed.

## W3-R1 gate handoff — READY_FOR_REVIEW

The W3-R1 code head `5c53b3fe0f50533047c22698303d5019129511a1`
passed local CFG FAST (`PASS CODE_CHANGE`, 92.325 s), focal
`StatementEffectsTest`/`StorageIndexTest`, `W3R1CellSemanticsTest`, hardened
`W3LegitimateWorkTest`, and the frozen integrated E2E described above. The
later documentation closeout does not change executable code. Producer FAST
passed 392 tests; its qualification-local stopped only at the previously
confirmed `SOURCE_DEPENDENCY_OWNER_UNPROVED` after Maven semantic success.
Lower FAST and qualification-local passed. AIR FAST passed; IR #8 is a normative
documentation clarification with no CI checks configured on its branch.

Exact-SHA remote checks completed successfully:

| Repository | Validated SHA | Remote result |
|---|---|---|
| air-java #21 | `fa2487306366acfba8124fd6403f940f1b1a98aa` | [contracts PASS](https://github.com/Gustavo2358/air-java/actions/runs/35544266225) |
| proleap-poc #58 | `1e7af863823a693fe9d36f6b6551e8c586170f50` | [harness PASS](https://github.com/Gustavo2358/proleap-poc/actions/runs/35544532817) |
| cobol-lower #34 | `27d1cf6dbd4450dc157e40424fc91ac63fe35be7` | [checkpoint PASS](https://github.com/Gustavo2358/cobol-lower/actions/runs/35545370177) |
| analysis-cfg #45 code head | `5c53b3fe0f50533047c22698303d5019129511a1` | [fast PASS](https://github.com/Gustavo2358/analysis-cfg/actions/runs/35546346033) |

All five worktrees and remote campaign branches matched at closeout; all five
PRs remained open and DRAFT. PR #45 has the W0–W3 campaign title. This is
`POSITIVE_MEMORY_TOPOLOGY_W3_R1_READY_FOR_REVIEW`, subject to human review and
merge only. W4 and operational physical unbanning were not started.
