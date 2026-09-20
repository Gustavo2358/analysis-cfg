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
