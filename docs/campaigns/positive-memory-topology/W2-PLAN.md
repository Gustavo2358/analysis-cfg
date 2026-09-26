# POSITIVE_MEMORY_TOPOLOGY — W2 plan

Status: CP0 frozen; CP1–CP5 authorized in sequence. W0-R1/W1 policy and the
logical default are unchanged. This is one continuation on the five existing
draft campaign PRs, with no checkpoint branches or merge.

## Frozen authorities and baseline

| Repository | Base | Local and remote campaign HEAD at CP0 | PR |
|---|---|---|---|
| analysis-cfg | `98fa57c3db2edf9f70bb7a99bb667dbf36d28104` | `258076b9a990cf7ba96636efcc0365e06238b4bf` | #45 DRAFT |
| proleap-poc | `edb64520a6269be9fa6d71cd47e6974112fbfece` | `cfcf0abf06a3b6186e157957bb301077fcb6f0bc` | #58 DRAFT |
| cobol-lower | `f8e181f95929c650181c989318f8ba23d1e68a1a` | `6c0317ceb5e64c11f437e027a17dd55fba85df52` | #34 DRAFT |
| air-java | `646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa` | `26016f10460336f237a33b2ed126a6a1427f0207` | #21 DRAFT |
| analysis-ir | `3fff18e2c16663a3f599207457caa1946d2e0945` | `b26465964fe75f944f6324df63330d69f33d77cd` | #8 DRAFT |

All five worktrees were clean at CP0, local HEAD matched `gh pr view`
`headRefOid`, and each stated base is an ancestor. No later campaign commit
required reconciliation. CFG locks IR `b264659`, AIR `26016f1`, frontend
`cfcf0ab`, lower `6c0317c`; lower locks frontend, IR and AIR at those SHAs.
W1 final FAST and qualification results are frozen in W1-REPORT.md, including
the preexisting frontend `SOURCE_DEPENDENCY_OWNER_UNPROVED` qualification
failure proven by W1 A/B. No fresh CP0 gate is claimed by that record.

The raw workspace baseline is the preserved `.positive-memory-topology/evidence/w1/B-final2` logical cohort,
`.positive-memory-topology/evidence/w1/CD-final2` physical C/D cohort, and
`.positive-memory-topology/evidence/w1/runtime-final2.json` command/classpath authority. Baseline
fixtures are `.positive-memory-topology/evidence/w1/cohort/r1--*` and the existing versioned CP6,
FILE/CICS and positive-memory-topology fixtures. Do not regenerate a golden
from the new implementation.

## Minimal witnesses and oracle

| Property | Existing witness / addition only where absent | Baseline oracle |
|---|---|---|
| IF supported and unsupported predicate, ELSE/no ELSE | `r1--if-equality`, `r1--if-class`, CP6 IF fixtures | true/false/continuation edges; no compensating AllMemory/control |
| EVALUATE supported, unevaluated and no OTHER | `r1--evaluate-no-other`, `r1--evaluate-condition`, EvaluateIntegrationSuite | ordered first-match chain and continuation |
| PERFORM supported, UNTIL partial, TIMES/VARYING | `r1--perform-until`, `r1--perform-times`, PerformFamilyIntegrationSuite | body, resume, test mode, backedge and zero-pass where supported |
| GO TO, ALTER and DEPENDING | `r1--goto`, `r1--alter`, `cp6/goto/{g1,alter,depending}.cbl` | textual target, ordinal alternatives, default; ALTER coverage isolation |
| Opaque/effects/CALL | `r1--display`, `r1--compute`, `r1--call-before`, `cp6/partial-program/call-*.cbl` | operand inventory, real effects and normal return; no global compensation |
| FILE/CICS | existing FileMemoryEffectsSuite, CicsProgramControlSuite, CicsFileControlSuite; `file-dependencies/w8/{read,write,delete,length-open,computed-unknown}.cbl` and six CICS LINK/XCTL fixtures | genuine external Unknown/read/return survives; option gap adds no memory/control |
| Physical C/D | positive-memory-topology group copy and relevant W2 storage witness | same AIR: default physical work zero, explicit opt-in coherent |

Baseline products are compared by candidate/support/BEFORE/control/snapshot/
resource/supported-effect semantics. Diagnostic and origin-only deltas require
classification. 0/1/50 diagnostic mutations hold executable facts fixed.
Explicit Havoc, AllMemory, open control and true external input are B controls;
invalid IDs/types/ranges are D controls. Consumer stays language agnostic.

## Checkpoints and gates

- CP1 (M08/M09): preserve IF and EVALUATE structure with bool Unknown, known
  reads and coverage. RED/GREEN, focal producer/lower/CFG checks, FAST for
  changed repos.
- CP2 (M10–M12): preserve PERFORM activation and GO TO targets/dispatch under
  gaps. RED/GREEN and ALTER/diagnostic metamorphics.
- CP3 (M04/M05/M13, M18/M19 encountered): split Opaque summaries and CALL
  model facts from omissions. Audit all broad constructors; keep B controls.
- CP4 (M14/M15 and residual M13): separate real FILE/CICS effects from
  profile/option/materialization gaps. Require B/C pair per changed helper.
- CP5: selected W1 and W2 source E2E, logical regression, physical C/D,
  indep80/indep160 once, control-heavy case, property tests P1–P7, FAST in
  every changed repo and qualification-local where pertinent. Record precise
  remaining variants and known blocker without promoting PARTIAL to PASS.

Commit/push producer changes before consumer repins to exact SHAs. No IR/AIR
commit absent a real contract or implementation change. A genuinely new
architecture decision triggers the requested stop packet; local implementation
and ordinary RED failures do not.
