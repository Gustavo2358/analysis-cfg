# POSITIVE_MEMORY_TOPOLOGY — W2

Status: CP0–CP5 implementation complete; final immutable pins, local gates and
remote draft PR closure are recorded below. [Plan](W2-PLAN.md);
[regression](W2-REGRESSION.md). W0-R1 and W1 architectural decisions remain
the authority. No W3 work or merge occurred.

## Baseline and final pins

The CP0 HEADs, bases, ancestry, clean worktrees and W1 evidence are frozen in
W2-PLAN. No later remote commit required reconciliation. Final continuation:

| Repo | Base | W2 final HEAD | PR |
|---|---|---|---|
| proleap-poc | `edb64520a6269be9fa6d71cd47e6974112fbfece` | `138d93c794ec3682bc9324021d9125ea5ea9d6a8` | #58 DRAFT |
| cobol-lower | `f8e181f95929c650181c989318f8ba23d1e68a1a` | `ba4f36c7b131f0db48c480187f6a9638e46f0477` | #34 DRAFT |
| analysis-cfg | `98fa57c3db2edf9f70bb7a99bb667dbf36d28104` | `this report commit (PR #45 HEAD)` | #45 DRAFT |
| air-java | `646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa` | `26016f10460336f237a33b2ed126a6a1427f0207` unchanged | #21 DRAFT |
| analysis-ir | `3fff18e2c16663a3f599207457caa1946d2e0945` | `b26465964fe75f944f6324df63330d69f33d77cd` unchanged | #8 DRAFT |

The lower lock pins frontend `138d93c794ec3682bc9324021d9125ea5ea9d6a8`, AIR `26016f1` and IR `b264659`.
The CFG lock pins lower `ba4f36c7b131f0db48c480187f6a9638e46f0477`, frontend `138d93c794ec3682bc9324021d9125ea5ea9d6a8` and the same AIR/IR
commits. No floating branch, arbitrary jar or new AIR/IR commit is used.

## CP0–CP5

| CP | Result | Oracle and scope |
|---|---|---|
| CP0 | DONE | Reconciled five draft PRs, ancestry, locks and worktrees; selected W1/source fixtures in W2-PLAN. |
| CP1 | DONE | IF true/false/continuation and EVALUATE ordered first-match chain survive unevaluated predicates. SP 2.33.0 adds only the optional arm condition fields; existing AIR Branch/Unknown BOOL suffices. |
| CP2 | DONE | PERFORM target/range/resume/backedge and supported TIMES/VARYING remain; UNTIL predicate gap chooses structurally. GO TO textual target survives ALTER coverage; DEPENDING retains ordinal choices and continuation. |
| CP3 | DONE in touched paths | Opaque retains known operands and source links; missing summary/transformation/unknown CALL body no longer supplies broad memory or foreign effects. Genuine input and explicit typed AIR uncertainty survive. |
| CP4 | DONE in touched paths | FILE/CICS READ/returned/status input and WRITE buffer reads remain; unimplemented option, profile and materialization add coverage without global compensation. LINK normal return and XCTL no-return are preserved. |
| CP5 | DONE | W1/W2 source regression, C/D same-AIR physical contrast, 0/1/50 metadata isolation, control-heavy case, indep80/160 and local gates are detailed below. |

CP1/CP2 RED cases showed the old full-control fallback; GREEN cases require
known arms, joins, targets and operands with no compensating AllMemory/AllControl.
CP3/CP4 regressions caught an Opaque frontier that dropped published operands,
FILE intrinsic/ordinary continuation conflation and source reference-role loss
when executable writes were removed. These were fixed before final qualification;
no test golden or evidence file was edited to manufacture a PASS.

## Mechanism disposition

| Mechanism | W2 status | Bounded result or remaining limit |
|---|---|---|
| M04 missing summary/materialization | PARTIALLY TREATED | Touched Opaque/CALL/FILE/CICS paths no longer expand C to AllMemory; unrelated unrepresented declaration forms retain diagnostic coverage and some explicit positive identities. |
| M05 unimplemented transformations | TREATED in touched paths | INITIALIZE, arithmetic/SET/STRING/UNSTRING/INSPECT examples no longer publish substitute writes; known references and normal control remain. |
| M08 IF | TREATED | bool Unknown keeps true and false edges, including no-ELSE continuation; known reads survive. |
| M09 EVALUATE | TREATED in published arm shape | ordered WHEN, OTHER/no-OTHER and joins survive unmodeled condition; other COBOL condition syntax remains coverage. |
| M10 PERFORM | PARTIALLY TREATED | supported BASIC/TIMES/VARYING and structural UNTIL stay; unrecognized family variants remain coverage. |
| M11 GO TO + ALTER | TREATED in chosen path | textual target retained, ALTER remains unsupported coverage without synthetic destinations. |
| M12 GO TO DEPENDING | TREATED | existing finite dispatch preserves ordinal alternatives and default under an unevaluated selector. |
| M13 CALL/CICS control | PARTIALLY TREATED | known normal continuation, target and LINK/XCTL outcomes retained without foreign compensation; CALL lacking any materialized continuation remains an older open frontier outside the selected witness. |
| M14 FILE | PARTIALLY TREATED | real input/read/return effects retained; profile/layout gaps no longer create global bounds in touched helpers; unqualified variants remain coverage. |
| M15 CICS FILE | PARTIALLY TREATED | option/LENGTH gaps do not erase known returned/status/target effects; only existing command/host roles are modeled. |
| M18 gap/precision gates | TREATED in touched paths | admission uses published structure/effects, not diagnostic count; invalid executable contracts still fail. |
| M19 C-only remainders | TREATED in touched paths | predicate/transform/ALTER/option C no longer opens unit control/value; real B and LOGICAL_ONLY physical limitation remain. |

## Integrated regression and properties

The 31-case logical source cohort and six same-AIR physical pairs are specified in
W2-REGRESSION. W1 candidates and support kinds are preserved except ACCEPT:
true external input to a known target removes its old MUST candidate. IF class,
EVALUATE condition and PERFORM UNTIL retain two alternatives while their C-only
open remainder closes. GO TO+ALTER, COMPUTE and DISPLAY preserve their known
target. Group/overlay/snapshot, CALL, FILE, CICS and selected source-dependency
observations are classified there. The control-heavy EVALUATE 40 case yields
40 reachable sites and 120 edges without global control or physical work.

P1 diagnostic isolation: lower 0/1/50 selector diagnostics leave finite targets,
known reads and reference work fixed; W1 CFG 0/1/50 scalar/regional tests keep
Events, captures, supports and solver work. P2 structure is checked by producer,
lower and source CFG fixtures. P3 audited Opaque, AllMemory, VisibleMemory,
Havoc and AllControl constructors: C branches in scope are closed; real B controls
remain. P4 true external input, explicit AIR Havoc/AllMemory and published open
control keep their semantic effects. P5 no COBOL feature-code switch entered CFG.
P6 AIR ID/type/range/domain validation remains enabled. P7 CLI default stayed
LOGICAL_ONLY throughout.

Same-AIR C is LOGICAL_ONLY with physical groups/writes 0 in all six pairs.
Explicit D uses `--experimental-physical`, applies 1/1 to 6/6 groups/writes and
retains coherent candidates; the producer's IBM1047 storage profile is separate.
Physical indep80/indep160 completed once with 161/321 groups, writes and targets,
zero base comparisons/object pairs/relation unions, and 1.016/1.316-second
physical dependency phases. No >600-second baseline was rerun.

## Gates, deltas and limits

Frontend FAST `PASS: 390 tests, 0 failures, 25.233 s`; full Maven `984` tests, zero failures/errors and one
skip. Frontend qualification-local reaches the independently A/B-proven
preexisting `SOURCE_DEPENDENCY_OWNER_UNPROVED` in the source-normalizer corpus;
`SourceDependencySemantics` is unchanged, and W1 A/B established this same
failure before W2. Lower FAST
`PASS with final SP pin: all fixed suites, architecture and 21 harness tests; 163.190 s`; lower qualification-local `PASS: semantic 244391 assertions and performance 39207 checks plus architecture`. CFG anchor gate
`PASS: immutable-pin/consumer FAST, 92.927 s; qualification-local not repeated because CFG production is unchanged and exact W2 source/physical E2E ran`. Exact logs are under `/tmp/w2-*` and the raw workspace evidence
directory; only reproducible contract/report files are versioned.

Exact-head remote checks: frontend #58 reports two `harness` successes at
`138d93c`; lower #34 reports two `checkpoint` successes at `ba4f36c`. The
first lower attempt received Maven Central HTTP 403 for
`maven-install-plugin:3.1.4` before compiling AIR; its re-run passed without a
source or pin change. The CFG check for this report commit is recorded on PR
#45 after publication, avoiding a self-referential HEAD assertion here.

Intended deltas: C-only broad writes/reads/control/aliases disappear; previously
omitted known branch/body/operand/target structure survives; ACCEPT remains real
semantic unknown. There is no unexplained candidate/support/BEFORE/snapshot or
resource loss in the selected cohort. Outside this wave, absent CALL completion,
unmodeled COBOL condition/loop variants, and unqualified FILE/CICS command forms
remain precise coverage work for a future authorized wave. W2 did not start W3.

LOGICAL_ONLY REMAINS DEFAULT. NO AUTOMATIC PHYSICAL FALLBACK. PHYSICAL REMAINS
EXPERIMENTAL / EXPLICIT OPT-IN. NO OPERATIONAL UNBANNING. NO MERGE PERFORMED.
