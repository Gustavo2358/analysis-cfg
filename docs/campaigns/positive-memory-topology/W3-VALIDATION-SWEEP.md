# W3-R1 frozen AIR validation-only sweep

Status: **HOLD before merge**. This check does not change the approved AIR
validator fix. It tests whether the frozen W3-R1 publications used for prior
evidence remain valid under final AIR validator `980d4989a18f876996390cc61af41419a595eb7b`.

The frozen inputs are the 40 `air.json` files in
`.positive-memory-topology/evidence/w3-r1/logical-final/` and the eight in
`physical-final/`. Each file's SHA-256 matches its cohort `results.json`.
Only AIR JSON decode and `AirValidator.validate` ran. The baseline validator
was the frozen W3-R1 runtime at AIR `fa2487306366acfba8124fd6403f940f1b1a98aa`;
the final validator was the exact pinned AIR build. No producer, CFG, solver,
query, performance canary or source E2E ran, and no frozen artifact was edited.
The raw manifest, driver, old/new status outputs and machine-readable summary
are retained locally in ignored `evidence/w3-r1/validator-sweep-final/`.

| Cohort | Files | Same status and issue count | Changed |
|---|---:|---:|---:|
| Logical | 40 | 39 | 1 |
| Physical | 8 | 8 | 0 |

The changed logical case is `source-dependencies-w3--composition`, AIR SHA-256
`d62a73d0feca84e8bc6b4a9ed87e7021f55d550f200562446755ac232681af92`.
The baseline validator reported `STRUCTURALLY_VALID`. The final validator
reports `INVALID_IR` with five `I-13` issues. The AIR contains `FILE-RECORD`
with `UnknownBinding(ObjectsMemory(self))` and five executable
`Opaque.otherWrites = WithinMemory(ObjectsMemory(FILE-RECORD))` sites. The
published FILE READ plan has real `MAY_UNKNOWN` writes to that record, while
its old SP 2.31/storage 1.8 projection has no grounded Cell or Region for the
record. The new validator correctly rejects that executable ungrounded bound.

The 39 other logical and all eight physical publications retain their exact
prior validation status and issue count. Evidence for the rejected case cannot
be carried forward to the final validator. Keep W3-R1 closeout and merge on
HOLD until the producer/lower publishes a grounded supported location for this
effect, or the case's supported boundary is resolved and independently
requalified. Do not erase the true FILE effect, weaken `I-13`, broaden the
scope, or edit the frozen AIR to make this check pass.
