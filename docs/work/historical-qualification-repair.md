# Historical qualification failures — repair

Status: IN_PROGRESS before merge; DONE once [PR #50](https://github.com/Gustavo2358/analysis-cfg/pull/50) is MERGED with required checks passing. Repairs and review are complete; integration is authorized.

Classify each existing failure against current product contracts before changing production or expectations. Initial evidence: six failures and one error in four frontend characterization classes; W2D hardcodes SP2.38 while the producer lock is SP2.50. Baseline reproductions are preserved.

Scope: repair application defects if proved; otherwise replace obsolete expectations with explicit current capability and negative assertions. Retain independent inventory, provenance, no synthetic kill, exact source pins and feature version floors. Do not regenerate oracles from outputs or accept arbitrary versions. Integration follows the approved frontend → lower → CFG order.

Validation: targeted RED/GREEN; complete frontend and CFG qualification to exercise stages previously blocked; FAST in changed repositories. Reuse corpus evidence only if production inputs/code remain identical. If any production change is needed, rerun the affected corpus and investigate deltas.

Checkpoint H1: obsolete characterization expectations replaced with explicit current SQLCA, copybook, DLI and PERFORM assertions; missing SQL INCLUDE stays opaque. W2D reads its version ceiling from the exact producer lock, keeps feature floors, and has five positive/negative guard tests. DLI retained provenance is corrected through framing and replacements; whole-command provenance and confidence remain unchanged.

## Current integration oracles

- W2D's Boolean placeholder predates typed EQUAL_TEXT. The oracle now checks the
  exact read subject, literal, fitting extent/padding, operator and predicate role.
- Storage coverage uses the source identity within a profile namespace; the old
  `/data/<id>` path no longer exists. The gate requires exactly one declaration
  owner/object output and rejects missing, duplicate and mismatched identities.
- A source MOVE owns an Assign and its explicit completion Jump. Multi-CALL now
  accepts exactly that pair in the same sequence; arbitrary extra operations or
  activations still fail. Every continuation is checked against the SP.
- Logical copy supports contain the literal and retained copy contributions. The
  gates require the exact independently specified producer set and exact written
  provenance for each contribution, including exclusion of overwritten values.
- PERFORM return origins follow the current topology proof DAG: callsite,
  paragraph frontier and body completion. The destination is checked separately
  against the published resume. The old oracle required a legacy resume-source
  span that is not a premise in this topology contract.
- `body-gap` and `must-write` use the now-supported literal truncation profile.
  Their exact result is LONG-PRO, with the overriding producer; OLD/PROGA are
  killed by a proved complete write. The program-name interpretation remains
  open and publishes no invented valid program name. The oracle asserts raw
  values, supports, openness and activation separation explicitly.
- An unprojected source occurrence is represented by ABSTRACTED inventory
  coverage with uncertainty, not necessarily an executable operation. Unknown
  control still cannot license a handler or following statement.

Eight focal test methods exercise the pin, predicate and coverage rules, including
negative mutations. Existing positive candidate sets in W2D, MOVE-data,
PERFORM-basic and multi-CALL are unchanged. The truncating overwrite expectation
is corrected only because the current typed write proves the kill.

The partial-program gate uncovered a **real lower defect**: CALL with only
UNKNOWN_LOCAL outcomes lost its target when replaced by Opaque. The lower fix
preserves Invoke target/signature and leaves an empty open local frontier.
The original `call-handlers` candidate expectation remains mandatory.

The current topology projects demanded execution contexts only. `control-body`
therefore requires exactly the body CALL and its resumed CALL, with the correct
normal outcome; it no longer requires an unused lexical shadow. `display-handler`
requires the following CALL to remain inventory-only beyond the unknown DISPLAY
completion. Its previously expected empty unreachable site was a representation
artifact; the BEFORE candidate and the prohibition on AFTER execution are unchanged.

## Closure

Implementation and historical gate repair are complete and approved. Git/PR merge
and required checks establish DONE. Final results, exact reuse boundaries, the
remaining opt-in skip and corpus deltas are in
[campaign qualification](cics-control-qualification.md). Current consumer locks
identify the actual merged upstream revisions during integration.
