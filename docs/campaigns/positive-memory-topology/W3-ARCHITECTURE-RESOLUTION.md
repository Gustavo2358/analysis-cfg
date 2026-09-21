# W3-R1 architecture resolution and composition evidence

The first W3 closeout remains historical. Human review found a lower/consumer composition defect: a supported logical TEXT value without materialized bytes was published as `UnknownBinding(ObjectsMemory(self))`. A consumer retry of that ungrounded scope produced `UNRESOLVED_SCOPE` and MAY targets on every unrelated base. A separate consumer loop created a `LogicalTarget` for every open object on every write. W3-R1 implements the approved Cell and bounded-scope architecture without a new AIR kind or operational mode.

## W3-R1 RED characterization (before production changes)

`scripts/project/w3_r1_composition.py` uses the existing `dependency-preservation/partial.json` SP fixture. It runs lower, retains its AIR, adds 32 unrelated test-only Region bases, then runs AIR-validated CFG and logical/physical dependency queries. The added Regions have no relation to the SP logical target; changing their count must not change that target's effects. The baseline W3 result is:

| Baseline witness | Binding for `TARGET-PGM` | Unrelated Regions | Prepared targets, default / opt-in | Opt-in Events | Opt-in physical groups | Logical candidate |
|---|---|---:|---:|---:|---:|---|
| SP fixture alone | self-scoped UnknownBinding | 0 | 0 / 0 | 1 | 0 | none |
| Same SP plus independent Regions | self-scoped UnknownBinding | 32 | 32 / 32 | 33 | 32 | none |

The script deliberately exits RED on the positive Cell, target and candidate properties. Raw lower/AIR/CFG/query artifacts are retained in local ignored `evidence/w3-r1/red-script-32`; the separate unaugmented run is `red-partial`. The existing lower suite also proves AIR JSON encode/decode equality for this SP fixture. `StatementEffectsTest.boundedOpenObjectDoesNotReceiveUnrelatedLogicalWrite` is a second RED witness: `U` is bounded to R1, `Q` is an exact view in independent R2, and `Write(Q)` incorrectly includes a logical target for `U` on W3. It fails on the causal target assertion after AIR validation succeeds.

## Approved contract

`Memory.Cell` holds a supported logical value without claiming a byte layout. A missing physical representation remains coverage; it does not create a semantic location choice. `UnknownBinding` represents real location uncertainty inside its published scope. Scope resolution may discover only locations inside that scope, including when a bound is revisited. Executable object cycles without independent Cell, Region or explicit broad grounding are invalid, never global or silently effectless. Explicit AllMemory/VisibleMemory retain their defined breadth. Logical writes target only objects selected by an explicit destination, positive alias or intersecting scope relation.

## Checkpoints

CP0 RED captured above. CP1 AIR/IR contract reconciliation, CP2 lower Cell publication, CP3 consumer bounded resolution/target selection, CP4 executable oracles and CP5 W0–W3 regression are complete. The W3 report/regression/performance addenda retain their final evidence.

## Human review of CP2 and resolved architecture

The first CP2 attempt exposed a second ambiguity in the historical REDEFINES
fixture: A and B shared a physical source component, but the SP did not publish
their complete logical-view identity. Human review chose a local producer proof,
not two disconnected Cells, a symbolic Region, or loss of the supported MOVEs.
The old SP snapshots remain under the lower's
`docs/campaigns/positive-memory-topology/evidence/` directory.

The frontend now emits SP 2.34/storage 1.10 `logicalExactViews` for equivalent
complete TEXT views. Each record carries `node`, `representative` and logical
character `length`. Recognition requires one positive component, proved overlay
relations, equal locally modeled elementary TEXT extents, no unrepresented third
view, and ordinary local WORKING-STORAGE context. Missing COPY remains coverage.
The lower validates this fact and binds A/B to one persistent private TEXT Cell
owned by the unit. It does not parse COBOL pictures or derive this identity from
MOVE literals. Independent same-size bases and partial overlap do not get the
shared Cell. Parsed MOVE continuations are now retained under missing COPY;
the historical fixture's unavailable edges were regenerated from the frontend.

On the integrated REDEFINES witness, `MOVE PROGA→B; MOVE PROGB→A; XCTL B`
produces one Cell, two ObjectIds, two Assigns with explicit jumps and only
`PROGB` as target. Default and physical opt-in agree. With 32/100/160 unrelated
Regions, both modes prepare exactly two targets and the physical run prepares
two Events and two applied groups; those counts are independent of unrelated
bases. The original partial witness moves from 32 false targets and 33 Events
to one target and one Event at 32 unrelated bases. These are structural
measurements on the recorded fixtures, not a general complexity claim.

## W3-R1 implementation closeout

The producer's `logicalExactViews` proof is local to a supported WORKING-STORAGE
component: equal complete elementary TEXT extents, same positive overlay chain,
same owner/context and no omitted partial third view. Its `length` is logical
characters, not physical bytes. The lower verifies this typed SP fact without
parsing a picture and shares one persistent private Cell for A/B under missing
COPY. With complete physical proof it retains two views of the same Region.
The missing COPY changes coverage; it does not change the local identity proof.
MOVE continuations come from the parsed control structure, never array order.

`StorageIndex.select` traverses bounds with visiting/resolved states and no
all-base retry. AIR validation rejects executable ungrounded self and two-object
cycles; repeated resolved union members and independently grounded bounds remain
valid. `StatementEffects` indexes open objects by candidate storage base once,
then selects only intersecting objects for each write. Direct, bounded and
explicitly broad target work have distinct preparation counters. Explicit
AllMemory/VisibleMemory remain effective. The consumer has no COBOL gap-code
branch. Raw RED evidence above and final GREEN evidence are retained under local
`evidence/w3-r1/`.

## Final human review: executable scope roots

Review of the first W3-R1 closeout found that the AIR validator checked
grounding for executable `ObjectPlace` operands but missed executable memory
scopes without a place. The minimal AIR had
`X -> UnknownBinding(ObjectsMemory(X))` and
`HavocMay(ObjectsMemory(X))`. Before the fix, validation returned
`STRUCTURALLY_VALID`; the consumer then raised `UngroundedBound` when selecting
the scope. This was a validator implementation gap under the already approved
contract, with no new AIR kind or semantic decision.

The same grounding traversal now checks executable scope roots in `HavocMay`,
opaque memory envelopes, foreign effects, `Unknown.remainingReads` and
`Choice.remainder`. Nominal cycles without executable use remain valid.
Grounded object bounds, explicit AllMemory/VisibleMemory and repeated resolved
union members remain valid. The AIR contract suite records RED for the
scope-only self-cycle before the fix and GREEN `INVALID_IR/I-13` afterward;
the CFG test verifies `INVALID_IR` at preflight, before graph construction.
