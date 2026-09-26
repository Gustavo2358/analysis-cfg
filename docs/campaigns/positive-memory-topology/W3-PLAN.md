# POSITIVE_MEMORY_TOPOLOGY — W3 plan

Status: CP0 frozen; CP1–CP5 authorized in order on the existing draft PRs. W0/W1/W2 semantics and LOGICAL_ONLY default remain authoritative. This plan records the causal frontier, not a new source-language completeness promise.

## CP0 baseline

| Repo | Base | Local / remote HEAD at CP0 | PR |
|---|---|---|---|
| analysis-cfg | `98fa57c3db2edf9f70bb7a99bb667dbf36d28104` | `d2df8aa998ad5bf62179df4e71700de9fd94ee80` | #45 DRAFT |
| proleap-poc | `edb64520a6269be9fa6d71cd47e6974112fbfece` | `138d93c794ec3682bc9324021d9125ea5ea9d6a8` | #58 DRAFT |
| cobol-lower | `f8e181f95929c650181c989318f8ba23d1e68a1a` | `ba4f36c7b131f0db48c480187f6a9638e46f0477` | #34 DRAFT |
| air-java | `646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa` | `26016f10460336f237a33b2ed126a6a1427f0207` | #21 DRAFT |
| analysis-ir | `3fff18e2c16663a3f599207457caa1946d2e0945` | `b26465964fe75f944f6324df63330d69f33d77cd` | #8 DRAFT |

All five worktrees are clean; local HEAD, upstream and GitHub PR HEAD agree. Each base is an ancestor. Lower pins frontend `138d93c`, AIR `26016f1`, IR `b264659`; CFG pins lower `ba4f36c` and the same frontend/AIR/IR. SP is 2.33.0. No concurrent commit required reconciliation.

Frozen W1 evidence is under `.positive-memory-topology/evidence/w1/{B-final2,CD-final2,runtime-final2.json}`; W2 evidence is under `.positive-memory-topology/evidence/w2`. W1-REPORT/REGRESSION and W2-PLAN/REPORT/REGRESSION specify the logical cohorts, six physical C/D pairs, EVALUATE40 and indep80/160. `SOURCE_DEPENDENCY_OWNER_UNPROVED` is preexisting by W1 A/B, outside W3.

## Residual causal matrix before code

| Residual | Producer fact | Lower AIR / consumer consequence | Class | W3 action |
|---|---|---|---|---|
| `FileIoEffects.outsideProfile` | step strength and exact view may be proven independently, plus profile gap | blanket `MAY_UNKNOWN` weakens COPY/FIT/MUST | A/B strength + C profile | CP1 remove blanket downgrade; retain proof-specific MAY and coverage |
| FILE `MAY_UNKNOWN` | target/range/alias proof may genuinely be absent | bounded regional/base MAY, or precise effect | B only when required proof absent; C otherwise | CP1 compare exact and adverse controls by step |
| FILE unknownRead/unknownWrite bounds | present typed ioReads/writes plus diagnostic gaps | foreign or Opaque scope may expand if bound true | A reads/writes, B only for explicitly open scope, C gap | CP1 audit each boolean's caller and prevent C expansion |
| FILE assignment name unmaterialized | resource operation and memory plan survive; no typed external filename | old computed `Unknown(TEXT)` opens runtime resource target | C name materialization, A buffer/effects | CP4 retain operation/effects with bounded Opaque and no invented target |
| `CICS_NAME_AREA_UNAVAILABLE` | target absent or representation not materialized | computed `Unknown(TEXT)` may open runtime name | B if runtime name truly dynamic; C if only materialization | CP2 split witness by actual target/value fact |
| `CICS_PHYSICAL_NAME_AREA_UNPROVEN` | nominal/logical target present, physical eight-byte view absent | nominal `Read` exists; diagnostic precision open | A target + C physical proof | CP2 keep nominal target and isolate diagnostic |
| CICS target absent/unmaterialized | command/options exist, target may be missing | current fallback `Unknown(TEXT)` | B only for genuine runtime dynamic value; C for absent model | CP2 preserve supported control/options; avoid synthetic runtime value |
| CALL without materialized continuation | source target and CALL fact, continuation unavailable | old open control frontier | A target, B if supported outcomes open, C if positional fact omitted | CP3 compare source completion to SP and lower admission; retain only causal remainder |
| residual Opaque broad bounds | known references and effects may coexist with gaps | Opaque envelope can publish memory/control bound | A/B when explicit; C if missing summary | CP4 classify reachable callers and remove C contribution |
| residual PERFORM/control fallback | AST/SP may retain target/body/resume while detail is omitted | structured branch or old open control | A structure + C evaluator; B for real alternatives | CP3 preserve representable structure and bound residual scope |

## Oracles and gates

CP1: existing `FileIoEffectPlanTest`, `FileEffectsContractTest`, lower `FileMemoryEffectsSuite` and new step-specific B/C witnesses; compare outcome, destination, strength, COPY/FIT and bounds. CP2: existing CICS program/file controls and nominal/physical/dynamic target witnesses. CP3: CALL continuation cases plus supported IF/EVALUATE/PERFORM/GO TO. CP4: audit actual constructors and 0/1/50 metadata isolation, explicit AIR uncertainty and malformed IR controls. CP5: selected W1/W2 logical and physical cohorts, EVALUATE40, indep80/160 once, and six measured legitimate-work families. Run FAST in each changed repo and local qualification only when the changed frontier requires it. Pins move in producer → lower → CFG order to immutable commits. No AIR/IR commit without a real change. No merge or operational physical release.
