# Synthetic DFH structures and value confidence

- id: SYNTHETIC-DFH-STRUCTURE
- status: IN_PROGRESS
- scope: Complete documented DFHAID/DFHBMSCA structure and explicit model confidence across source values.

IBM CICS Primer demonstrates both 01 groups with 02 PIC X members. The TXSeries
DFHAID inventory adds DFHNULL. CICS TS BMS constants list 69 BMS data names;
DFHMDI and IBM APAR PH37181 document DFHERASE/DFHCURSR (88 under DFHBMFLG).
The structural analysis model covers the union of these documented names. It is
not claimed to reproduce every installed IBM version's physical member/order.
Ordinary fields have no VALUE; the two condition names carry documented sets.

SourceMap marks model input explicitly. AST retains that authority; source binding
and PIC/group structure survive, but model VALUE, physical/exact-cell and executable predicate
proofs are not admitted. SP 2.49 and NOMINAL_TEXT_SOURCE_V2 carry modelAssumed on
nominal symbols. Older source facts keep V1 byte shape. Lower transports this fact,
and validates missing/downgraded model markers. No AIR or CFG edge is invented.

A model-influenced write keeps predecessor candidates and unknown remainder.
Raw observed text and its documented-width interpretation remain possibilities;
model width cannot erase a source candidate. Model-influenced values cannot prune
a source branch. The marker propagates through copies and joins; model seeds are
ignored. Ordinary V1 writes/branches retain their existing behavior. Finite text
sets and the existing worklist budget guarantee termination; one marker is joined
per value state. Supports remain actual source assignments/provenance.

Oracles: complete independently listed names/PICs/group and 88 binding; DFHNULL;
qualified references; real member priority; no model initial VALUE facts; model
writes/unknown writes and transitive copies preserve candidates; both IF arms;
real proved kills still work; old wire shape and strict model-marker rejection.
Validate producer/transport/consumer tests and FAST, source→dependencies adversaries,
PERFORM/Chaos/aliases, and CardDemo dependency/support comparison. Existing unrelated
expected results remain unchanged. No ALTER, unrelated fixes or merge.

Sources:
- https://www.ibm.com/docs/SSJL4D_6.x/pdf/cics-primer.pdf
- https://www.ibm.com/docs/en/txseries/9.1.0?topic=constants-attention-identifier-list-dfhaid
- https://www.ibm.com/docs/en/cics-ts/6.x?topic=reference-bms-constants
- https://www.ibm.com/docs/en/cics-ts/6.x?topic=macros-dfhmdi
- https://www.ibm.com/support/pages/apar/PH37181

### Final discovery refinements

All 105 elementary PIC X fields must enter the nominal symbol inventory, including
fields followed by condition names. Level 88 declares a predicate, not subordinate
storage. A model's PIC remains a nominal assumption when the grammar's period token
includes whitespace beyond the COPY boundary; the PIC itself must have exact
provenance. This does not change whole-declaration provenance or admit physical proof.
A focal RED found 102/105 admitted fields; the corrected inventory is 105/105.

The original DFHPF3 CALL oracle required executable candidates/edges despite no
local-cell proof. Its result is retained as the historical executable probe. The
new product oracle independently requires dependencies.programs to contain PROGA001
and P, supported by the written assignment, with unknown remainder and no fabricated
executable edges. Three additional probes cover DFHSTRF, DFHOPAQ and DFHBMFLG.

## Qualification — 2026-09-27

Ready for Draft review; status remains IN_PROGRESS until a separately authorized merge.

| Check | Result |
| --- | --- |
| Documented elementary names and nominal shapes | 105/105, plus both groups and both 88 names |
| Structural pilot E2E | 14/14 |
| PERFORM | 39/39 |
| Chaos | 48/48 |
| Logical aliases | 14/14 |
| PERFORM adversaries | 25/25 |
| CardDemo real programs | 73/73 |
| Frontend FAST | 597 tests, zero failures/skips |
| Lower FAST | PASS, including contract/architecture/integration checks |
| CFG/dependencies FAST | 638 methods, zero failures/skips; architecture PASS |

CardDemo retained all candidate sets, reachability, prior executable supports and
original source operand provenance in 73/73 programs. 67 outputs were identical
under the provenance-aware comparison. Six program variants gained eight valid
MOVE supports: COACTVWC line 336, COCRDLIC lines 526/554 and COCRDSLC line 318,
plus their migrated .cl2 variants. Their original declaration supports remained;
no candidate or support was removed. Both COACTUPC variants matched the frozen
baseline, including seven FILE sites marked UNREACHABLE_IN_MODEL and the two
source-qualified program targets. That baseline has no FILE candidates.

The final V1 writer refinement only restores canonical key order. All 73 CardDemo
and 14 structural frontend products were reexecuted and were byte-identical to the
previous qualified structural products; downstream binaries were unchanged, so
that downstream evidence was reused explicitly. PERFORM, Chaos, aliases and PERFORM
adversaries were executed again with the final writer. PERFORM's 39 SP products
also match the previous pilot byte-for-byte. The initial writer ordering delta,
102/105 inventory RED, four confidence REDs and final outputs remain available.

The lower's future-version probes now use 2.50 because 2.49 is admitted; their
rejection assertions remain intact. The CFG API inventory change is confined to
modelAssumed accessors/constructors and the corresponding Java Stream/Predicate
references. No architecture restriction or dependency oracle was relaxed.

Full qualification and the older 310-fixture matrix were not rerun. The selected
FAST gates, source/transport adversaries and full CardDemo corpus cover this change.
Other DFH members, qualified/replaced missing COPY and authoritative physical/runtime
modeling remain outside this pilot. No ALTER or merge was performed.

Raw logs, immutable jars, hashes and products remain under `.synthetic-dfh/revision/`.
The frontend's `docs/work/synthetic-dfh-validation.json` records counts, binary hashes,
implementation SHAs, limitations and the exact scope of evidence reuse.

Chaos has two expected SP additions: WS-BOOL in case 28 and WS-STATE in case 42
now enter the nominal text inventory despite their level-88 children. Neither loses
a fact or changes its dependency oracle. The other 46 Chaos products, all 14 alias
products and all 25 PERFORM adversarial products match the previous pilot byte-for-byte.
