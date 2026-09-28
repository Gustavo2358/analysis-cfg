# FILE composite control — SP 2.51

- id: FILE-COMPOSITE-CONTROL
- status: DONE
- scope: published intra-statement FILE control; OPEN/CLOSE operands and SORT/MERGE phases without procedure callbacks.

Merged and qualified: [fechamento da integração](carddemo-control-integration.md).

## Rule and authority

IBM Enterprise COBOL 6.4 [OPEN](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-open-statement),
[Language Reference](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf) (CLOSE, OPEN, SORT, MERGE)
and [sort/merge process](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=files-sort-merge-process)
allow multiple file operands. Each operand participates before ordinary completion.
The frontend models OPEN/CLOSE as successive per-file operations in lexical order.
SORT/MERGE uses input, work, output phases; order/count inside aggregate phases remain unknown.
No exception continuation, USE callback or SORT procedure return is inferred.

## Contract and algorithm

`FRONTEND_CONTROL_TOPOLOGY_R2` adds `controlTopology.fileFlows` (empty in R1), required
when an SP2.51 composite is executable. Each flow owns one existing statement and
an entry FILE_POINT target. A point has an opaque id, kind USE or CHOICE, use ordinal
(-1 for CHOICE), targets and proofs. USE has one ordinary continuation; CHOICE has
explicit possible successors. FILE_POINT is a target within the same statement.
Points are not COBOL occurrences. Existing statement identity and per-use identity remain.

OPEN/CLOSE publish one USE point per operand and a chain to statement completion.
SORT/MERGE without callbacks publish an INPUT selection point, WORK use, OUTPUT
selection point. Participants return to their selection point, which can also exit
its phase; this overapproximates aggregate ordering/count without a Cartesian product.
Every FILE route remains authoritative: CONTINUE selects the use continuation,
HANDLER remains a handler region, USE remains UNKNOWN_LOCAL. Critical-exit metadata
still licenses an open remainder, never a closed success edge.

Lower binds points in the active PERFORM/CICS context and emits existing AIR jumps,
finite alternatives and FILE operations. It does not reconstruct flow from ordinals,
legacy continuations, file names, source positions or sortPlans. Point targets,
ownership, route coverage and use inventory are checked at wire and typed boundaries.
Old SP contracts retain their published interpretation; new fields require 2.51.
AIR and CFG contracts/solver do not change.

Graph construction and emission are linear in points, uses and routes; canonical
inventory sorting adds O(n log n). Reference closure
visits finite point/occurrence inventories; executable loops are allowed. Symbolic
region and proof cycles remain invalid. All new control cites source proof provenance.

## Independent oracles and qualification

RED: OPEN F/G -> CLOSE F/G -> GOBACK, three operands, reversed operands, split vs
combined statements, mixed modes and single-file control. Success must visit every
operand in order without a bypass. PERFORM callers must resume independently.
SORT/MERGE participants must be reachable in the appropriate phases; SD stays local,
repetition/order stays partial, callbacks remain unknown. FILE STATUS for later uses
must reach subsequent code: exact alphanumeric status has a proved overwrite;
non-admitted numeric status remains MAY and cannot kill prior candidates. Critical error retains
its open remainder. Corrupt/missing/duplicate/cross-owner points reject at both ports;
permuted inventories and legacy metadata cannot replace published authority.

Run producer/consumer focal suites and FAST; current-contract FILE E2E traverses AIR
and CFG and checks dependency candidates/supports. Replay all 73 CardDemo inputs,
PERFORM39, Chaos48, aliases14, PERFORM adversarial25 and existing CICS/fixture corpus:
shared topology and new wire invalidate their admission/composition evidence. Compare
candidates, supports, provenance and all unaffected products; investigate every delta.
No UI change, ALTER, callback implementation or merge in this campaign.

SP2.51 requires R2 and a nonempty flow inventory. USE ordinary targets agree with
their SUCCESS/0 outcome when that event is published; callbacks on success are outside these composite forms.
R1 serializations may omit fileFlows; an empty compatibility array grants no new authority.

Structural-only frontend ports can publish these control facts without an I/O event
model. Their required locality inventory explicitly records an unavailable physical
profile, with no memory facts, proofs or bindings. A missing continuation in an
unavailable event plan makes no completion claim; a supplied contradictory target
still rejects. Both unit and compilation serializers omit empty R1 fileFlows.
