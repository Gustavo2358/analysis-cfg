# Successful publication and failed-run destruction

Status: qualified checkpoint in WORK-AIR-SCALE; global runtime/CLI resource
coverage remains incomplete. Actual mutation/publication failures must preserve
the original operational exception and return no analysis result.

The original actual-solver test exhausts the run budget through a domain callback.
That does not necessarily abort its Boolean manager. New tests perform a real
condition operation on the same exhausted manager, in block transfer and nested
edge contribution. Before this change, backward finally blocks invoke semantic
publication after manager abort and replace the CONTROL Exhausted instance with
IllegalStateException. Forward/backward closure must preserve the exact primary
instance and release all resident accounting.

Backward slot processing, final projection and contribution journals now publish
only after their computation succeeds. Successful early continues still publish
their journal before moving on: empty conditions and absent child regions do not
retain temporary construction roots until the enclosing iteration ends. Scratch
feasibility in both directions restores its temporary semantic scope only after
a successful query. A failed query/run is destroyed by the existing execution
owner; it exposes no result, reopens no manager and resets no exhausted budget.
A restoration which itself fails after a successful computation becomes the
operational failure of that computation.

No normal transfer, join, guard, caller subscription, contextual path or result
projection is omitted. Existing try-with-resources run ownership and the separate
cleanup accounting perform physical destruction; semantic publication is not a
resource destructor and must not be treated as one.

Validation: actual block and edge failure laws RED before production edits, then
GREEN in both directions; the unchanged domain-failure law remains GREEN. A
neighboring 88-test gate covers contextual/oracle, guard, caller, root, signature,
non-distributive memory and Boolean ownership behavior. Mandatory FAST passes
1,023 unit/contract methods, zero skips, and compiled architecture checks. These
laws do not qualify every permanent I/O fault, deadline/cancellation mechanism or
managed CLI admission/delivery path in AS-W01–W10.
