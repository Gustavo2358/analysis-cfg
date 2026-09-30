# STAGE5-PREEXISTING-FIXES

Status: IN_PROGRESS — implementation and qualification complete; Draft review,
no merge. User authorized both pre-existing fixes in the stage-5 campaign.

## Causes and model

1. **Fixed-format continuation**: the frontend accepted only an open literal or
   a split word. It rejected complete literals and complete word/literal operand
   boundaries before producing SP. Frontend #77 now preserves closed literals as
   separate tokens, retains split words/open literals, observes physical column
   72 for escaped quotes, and rejects split literal prefixes/delimiters. Physical
   records, LF/CRLF/CR and COPY provenance are retained. No grammar/SP change.
2. **Anonymous logical root**: valid `01 FILLER` has a Storage NodeId and no DataId.
   LogicalTextMove.initial/translate incorrectly required a nominal root. Lower
   #52 now allocates an internal abstract TEXT cell by unit and structural NodeId.
   It creates no COBOL name, DataLink or physical proof. Existing family equations
   initialize/update the root and project named views. Unknown filler retains a
   read of unknown storage; it never becomes invented spaces.

The analysis-cfg production code is unchanged by this addition to stage 5.
This PR supplies strict producer pins and the permanent four-stage oracle.

## Qualification against the qualified stage-5 baseline

All **560** programs ran through the four production CLIs again:

| Population | Cases |
| --- | ---: |
| CardDemo, all programs including extracted archive variants | 73 |
| PERFORM | 39 |
| Chaos | 48 |
| Logical aliases | 14 |
| PERFORM adversaries | 25 |
| Frontend/general fixtures | 331 |
| Previous final focal contracts | 29 |
| Frontier payload | 1 |

SP, AIR, CFG, dependencies and qualified source are byte-identical in **560/560**
cases (2,800 compared products). Program/file candidates, supports and provenance:
zero losses, zero additions. All existing expected files remain unchanged.
PERFORM **39/39**, Chaos **48/48** with 31 rejected oracle mutations, aliases
**14/14**, PERFORM adversaries **25/25**, independent CFG wire checks **560/560**.
CardDemo keeps 79,176 nodes, 84,504 ordinary transitions and 3,063 local rules.
No new reachability, control edge or kill proof was introduced.

### New adversaries

**25/25**, run twice with identical four-stage products: both quote styles,
LF/CRLF/CR, split word and word/literal continuation; named/anonymous roots;
VALUE initialization, proven overwrite, two independent roots, nested aliases,
unknown filler/sibling/group target, DATA sender, multiple receivers, IF branches,
PERFORM, logical text disabled and explicitly enabled physical propagation.
Physical endings are stored verbatim with a fixture-local `.gitattributes`.
Supports, site provenance, bound hashes and CFG wire contracts are checked.
Five oracle corruptions are rejected: dropped candidate/support, invented blank
initialization, closed unknown remainder and fabricated nominal FILLER.

All **11** original photo-discovery reproductions/controls now complete every
stage. The two CALL variants retain `PROGA001`. Both bugs were witnessed RED on
the original producer code; the lower also has nine permanent real-SP controls
in its fixed FAST logical-storage suite.

### Gates and evidence reuse

- Frontend FAST PASS (42.169 s); qualification-local PASS, including 1,264 Maven
  tests, zero failures/errors and one existing skip, full source-normalizer
  regression, COPY/provenance and naming checks.
- Lower fixed FAST PASS (585.001 s). The expanded focal suite was recompiled and
  rerun independently after its final test additions; it passes. Remote FAST also
  exercises those final tests.
- CFG fixed FAST PASS (234.320 s). No consumer production change in this fix.
- AIR unchanged; original stage-5 full/FAST evidence remains applicable.
- Lower/CFG full wrappers were not rerun for these fixes. Original stage-5 full
  qualification is historical evidence, not a new full-wrapper claim. Fresh
  validation covers the changed producer boundaries and the full 560 population.

The fresh committed-head runtime has all 3,741 class/resource entries identical
to the frozen implementation runtime used by the corpus. Later changes are tests,
documentation and pins only. Evidence, commands, hashes and exact executed SHAs:
workspace `.shared-routine-bodies/evidence/photo-fixes*/`; durable local report
`artefatos-e2e/shared-routine-bodies-20260930/photo-fixes/REPORT.md`.

Reproduce with:

```bash
python3 scripts/project/e2e_normalization_logical_roots.py \
  --runtime <frozen-runtime.json> --work <new-output-directory>
```

## Limits and delivery

Drafts: frontend #77, lower #52 and this #57; AIR #23 is unchanged. Current exact
pins are in sources.lock.json. No merge or schema/version bump. Existing PARTIAL,
unknown effects, modelAssumed and physical-memory limits remain intact. The
normalizer's existing policy for short records in open literals remains unchanged
(no implicit padding to column 72). This fix does not claim complete continuation
syntax or full physical-memory analysis. UI import of CFG v5 remains outside this
producer campaign, as documented in the original stage-5 work item.
