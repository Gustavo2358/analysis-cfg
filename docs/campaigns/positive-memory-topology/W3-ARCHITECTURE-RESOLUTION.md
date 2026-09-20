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

CP0 RED captured above. CP1 AIR/IR contract reconciliation, CP2 lower Cell publication, CP3 consumer bounded resolution/target selection, CP4 executable oracles and CP5 W0–W3 regression will be recorded here and in the W3 report/regression/performance documents as they complete.
