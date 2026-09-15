# RF-W4 — physical target possibilities

A partially bound CALL can have multiple proven physical views and an open remainder.
Losing all views because binding is not unique loses source-supported dependency
possibilities. `target.possibilities@1` permits an open typed target domain; see the
pinned analysis-ir §14. It does not make an unknown type TEXT or a candidate constant.

The frontend publishes exact canonical alternatives, SP 2.18 transports them, and
lower emits a `Read(Place.Choice)` with those object views and an open memory bound.
This consumer resolves that existing AIR occurrence through `StorageSubject.PlaceOccurrence`.
The session index owns occurrences by publication/unit/operation IDs. Unknown or
foreign occurrences are rejected as unsupported subjects. No nominal-name resolution,
COBOL source access, synthetic object or second solver is involved.

The dependency plan queries the existing regional provider BEFORE Invoke. The
existing region resolver joins supported alternatives and the open bound. Existing
MAY transfer preserves possibilities and existing exact MUST overwrite removes the
prior bytes. Unknown target type keeps interpretation uncertainty and its typed
uncertainty reference. Signature, arguments, results and outcomes remain independent.

Index construction adds one map entry per Place occurrence (linear space/time in
operand count). Lookups are by canonical ID. Existing finite operand traversal and
regional partition/solver bounds apply; this change adds no iterative analysis.

`RegionalResultJson` uses version 1.1.0 when a query uses PLACE_OCCURRENCE. Existing
subjects retain 1.0.0 output. The independent reader rejects the new subject under
an old version. AIR readers without target.possibilities/Choice support must reject
explicitly. Dependency JSON retains its existing schema, supports and BEFORE point.

Permanent qualification:

- PhysicalChoiceTargetTest: two possible targets, both overwritten to one new target,
  and one proven text view alongside an unproved numeric layout; memory/JSON CLI
  equality includes supports, uncertainty references and query point.
- StorageQueryTest: missing/foreign occurrence rejection; historical allocation,
  visibility, codec and bounds negatives remain active.
- InvocationIndependenceTest: literal/computed targets with partial arguments/results.
- Independent regional wire reader: 1.1.0 shape and deliberate downgrade rejection.

Scope: no CFG topology or solver transfer algorithm change. Unknown aliases never
prove disjunction. Source input isolation belongs to frontend/lower; observed CALL
inventory is separate from reachable edges. Calculated bounds and AIR storage-binding
alternatives still have explicit codec limitations; the supported choice is an AIR
operand occurrence, not a fabricated exhaustive binding.
