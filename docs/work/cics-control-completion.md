# CICS control completion — D1–D5

Status: IN_PROGRESS before merge; DONE once [PR #50](https://github.com/Gustavo2358/analysis-cfg/pull/50) is MERGED with required checks passing. Implementation and qualification are complete; human review approved integration on 2026-09-27.

## Checkpoints

- C0: freeze scope, baseline pins, language rules and validation.
- D1: distinguish structural model uncertainty from unknown COPY text. Restore only independently proved real declaration context/allocation; preserve synthetic uncertainty and alias bounds.
- D2: qualify existing RECEIVE/SEND host commands against those proofs; keep control, host footprint and runtime value confidence separate. Published topology and matched PERFORM contexts remain authoritative.
- D3: support SYNCPOINT ROLLBACK with ordinary completion and explicit exceptional uncertainty.
- D4: model CICS RETURN and project explicit exit/ABEND semantics without textual fallthrough.
- D5: project positively qualified handler selections using existing handler-state analysis; preserve guards, cancellation/reset/deactivation, outer-level remainder and interruption context.
- Final: adversarial and corpus qualification, documentation, exact consumer repins, review PRs.

## Rules and authorities

IBM CICS TS command references: [RECEIVE MAP](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-receive-map), [SEND MAP](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-send-map), [SYNCPOINT ROLLBACK](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-syncpoint-rollback), [RETURN](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-return), [ABEND](https://www.ibm.com/docs/en/cics-ts/6.x?topic=summary-abend), [abend exits](https://www.ibm.com/docs/en/cics-ts/6.x?topic=processing-how-it-works-abend-exit-code). Rules were inspected in the discovery; recheck additional conditions as implementation requires.

Successful input/output and syncpoint commands have possible ordinary completion. RETURN exits the CICS logical level; XCTL successful transfer has no local return. Registration is not dispatch. A selected abend exit is deactivated before entry; CANCEL/RESET and outer levels must retain their distinct meanings. Unknown conditions never manufacture an active local handler.

Existing contracts: frontend fact-dependency locality and ControlTopology; lower CICS host-effects and exceptional-handler contracts; AIR independent precision dimensions, explicit control envelopes and MAY memory effects. Source evidence and executable control stay distinct. No downstream parsing of source/display names. No program/member/line special cases. No ALTER, application UI changes or unrelated FILE fallback expansion.

## D1 algorithm and oracle (before code)

Retain a typed structural-model qualifier with each owned input region. Publish MODEL_STORAGE inputs, distinct from MISSING_COPY. Such inputs can revoke memory proof for modeled components, aliases sharing them, and still-open enclosing records, but do not act as unknown section/header text for later complete real records. Actual missing/unlocated input preserves the prefix rule. All model-dependent exact-cell, physical-layout and kill proofs stay unavailable. A binding to real group storage may use an existing nominal region without claiming physical extent.

Evaluation remains a finite proof DAG; input classification O(inputs × declarations/regions), existing canonical proof evaluation O(proofs + dependencies). Oracles cover real data before/after model, genuine missing COPY, model operands, overlapping aliases, open headers, visibility and section scope. Existing unrelated negative oracles stay intact.

## Validation

Impact classes C3/C4: producer/consumer contract plus shared storage/control. Each checkpoint runs focal RED/GREEN, family and boundary tests; stable repo FAST and final broad qualification. Preserve baseline outputs and support provenance. Run all 73 CardDemo programs and PERFORM39, Chaos48, aliases14, PERFORM adversarial25, frontend fixture matrix310 and relevant local qualification. Compare dependencies/supports and classify every loss; graph size alone is not an oracle. New tests must reject false continuations, cross-caller returns, canceled handler dispatch, synthetic kill and lost MAY writes.

Historical evidence is read-only; final tests are reported separately from reused baseline/runtime artifacts. The discovery's five-component count is descriptive, not a target assertion.

## D1 checkpoint

Implemented typed MODEL_STORAGE scope and SP 2.50 transport. Real records following
a known structural model recover independent local allocation; model storage and
aliases remain unavailable. The source uncertainty channel retains MODEL_STORAGE.
No control edges or runtime synthetic values were introduced.

Focal producer tests passed (ModelInputLocalityTest, FactDependencyLocalityTest,
NominalCopybookTest); the initial red run reproduced the lost real-record proof.
Frontend FAST: 603 tests, zero failures. Consumer wire suite: 61 checks, including
old-version and available-model rejection. Seven new locality tests cover ordering,
real missing text, LINKAGE, model aliases and open record boundaries. The existing
SQLCA negative oracle still forbids an exact cell; only its input kind changed.

Raw evidence: workspace .cics-completion/evidence/d1-*. This checkpoint has not
run the final whole-corpus qualification.

## D3 algorithm and oracle (before implementation)

IBM SYNCPOINT ROLLBACK backs out recoverable resources and can complete normally.
Publish SYNCPOINT_ROLLBACK as a distinct command kind with a required operand-free
ROLLBACK option. Its application host footprint contains only explicit RESP/RESP2
writes; external transactional state stays open. No host-memory rollback or kill
is inferred. Ordinary source completion uses the existing contextual topology;
unsuppressed INVREQ retains exceptional uncertainty. RESP/NOHANDLE permits local
error completion, RESP2 alone does not suppress default processing. Closed option
validation rejects duplicate/value-bearing ROLLBACK and unsupported options.
This is a finite command classification and existing lowering, linear in options.
SP 2.50 is required for the distinct kind, including unavailable syntax facts.

## D2 checkpoint

Seven new four-stage adversaries pass with D1. They exercise RECEIVE/SEND MAP and
SEND FROM/LENGTH OF in nested PERFORM, two callers, real aliases, actual missing
COPY and model storage operands. Independent AIR assertions check the expected
read/write operands, bounded scopes and absence of mustOverwrite. RECEIVE retains
the prior candidate after MAY writes. No lower admission guard was removed: D1
restores the positive allocation proof required by the existing translator.

The first temporary runs exposed invalid fixed-format probe wrapping (column 72
and splitting a hyphenated token). Corrected sources preserve the semantic oracle;
raw failed runs remain separate. Valid run: d2-focal-valid, 7/7, all 28 stages zero.

## D3 checkpoint

SYNCPOINT_ROLLBACK is a separate SP2.50 command. Closed syntax, source continuation
and bounded MAY response writes are implemented. Producer red test reproduced the
unsupported command; green CicsCompletionTest/CicsCommandContractTest/CicsHostEffectsTest
passed. Four producer wire variants passed the new consumer suite. Four four-stage
PERFORM/rollback cases passed with prior value and supports preserved. Historical
unsupported-ROLLBACK tests now use ROLLBACK MYSTERY; the new positive/invalid-option
tests explicitly cover the capability that changed. Final corpus is pending.

## D4 algorithm and oracle (before implementation)

RETURN success exits the current CICS program level, including inside PERFORM; it
does not resume a local activation. Qualify the plain, TRANSID, COMMAREA/LENGTH and
IMMEDIATE subset plus response options. Host roles are READ for passed data and
WRITE for response areas. A locally handled error has its own source continuation
only when a condition-bearing option is present. Unhandled conditions stay open;
no value predicate proves success. Unsupported options remain a frontier.

ABEND CANCEL bypasses exits and has a task-halt outcome. Other explicit ABEND
events are completed with the context-sensitive dispatch in D5. A halt/return
never acquires a textual successor. Existing AIR return/halt alternatives suffice.
Oracles: RETURN within nested/range PERFORM, independent successful exit and local
error route, invalid options, RESP2 alone, LENGTH OF and passed-memory roles, and
ABEND CANCEL with an active handler. Linear option classification, finite topology.

## D4 RETURN checkpoint

RETURN has an explicit PROGRAM_RETURN success outcome; supported locally handled
errors use a separate condition-return outcome. Typed host operands and LENGTH
expressions feed the existing bounded memory translator. Four four-stage probes
pass, including nested/range PERFORM with no false resume and a positive local
error route. AIR host read/write and no-kill assertions pass. Producer completion,
command, host-effect and terminal-send tests pass (25 tests).

A preliminary probe used the reserved word AREA as a data-name and degraded parsing;
corrected WS-AREA fixtures preserve the oracle. Final probes have zero parser errors,
and the permanent runner now rejects parser degradation even when a dead-site oracle
happens to pass. Explicit ABEND exit and dispatch are integrated together in D5.

## D5 algorithm and oracle (before implementation)

Executable specialization adds one existing HandlerStateAnalysis.Support to each
matched PERFORM activation. It reuses source handler transfer/selection rules and
validated exceptional event descriptors. Support is a finite reaching definition,
not a trace or a runtime CICS level. Phase, body, escape and resume labels carry the
current support; a completed body returns its outgoing state only to its own caller.
Native CALL retains the existing unknown-handler-effect transfer.

Project a selected local LABEL as a possible exceptional destination, with event
proofs, activation provenance and runtime premises retained. The handler starts
deactivated in a separate ingress context. It cannot restore the interrupted
PERFORM stack; unproved handler paragraph completion remains a frontier. Nested
PERFORM invoked by the handler has its own matched continuations. CANCEL, RESET,
replacement and bypass use the same source transfer rules. ABEND CANCEL halts the
task. Inactive/unknown local state retains propagation/open local uncertainty;
unknown state does not license an arbitrary catalogued label. XCTL keeps its
external successful transfer and never gains a normal local return.

The admitted event inventory is explicit ABEND and guarded XCTL PGMIDERR, as already
published by the frontend. Other command-condition frontiers are preserved. No
source graph derivation is copied into AIR as an unconditional edge: in particular,
source summary callerPremise relations cannot join executable return contexts.

Work is finite over nonrecursive activation paths × source locations/phases ×
finite supports plus finite handler ingresses. Recursion retains the existing
bounded frontier. Each specialized label is emitted once; complexity is linear
in the resulting product graph. Oracles cover activation/cancellation/reset,
replacement, two callers with distinct values, transfer out of interrupted PERFORM,
handler completion bounds, nested handler PERFORM and conditional XCTL dispatch.

### D5 AIR consumer boundary discovered by the adversaries

AIR already defines Exceptional/AnyException destinations and HaltAlternative.
The CFG known subset and the independent analysis index accepted only ordinary
invocation outcomes. PARTIAL_ANALYSIS therefore widened these otherwise explicit
outcomes to unit-wide unknown control. This caused both export refusal and the
observed two-caller contamination. Extend these consumers by AIR type only.

A local exception has an EXCEPTION edge to its explicit Handler label. Propagation
and alternative halt have an occurrence-owned OutcomeExit and CONTROL_EXIT edge;
normal exit stays distinct. Deduplicate equal local destinations within each role,
retain every distinct outside outcome and the original AIR as authority. The index
independently validates endpoints, outcome membership, source identity and complete
cardinality. No synthetic entry backedge or general local-label expansion follows
from a supported exceptional alternative. Known-subset JSON with these roles uses
4.0.0; existing products keep their previous version. O(nodes + explicit outcomes).

## D5 checkpoint

Qualified ABEND/guarded XCTL events now carry correlated handler state through
matched PERFORM activations. Generic AIR exceptions/alternative halt are projected
by CFG and independently validated by the analysis index. Source selection proofs,
activation origins and runtime premises survive; no handler-state name heuristic
or textual fallthrough was added. Unqualified ABEND still has a NOT_READY frontier.

29 four-stage adversaries pass, including two callers, CANCEL/RESET/replacement,
handler deactivation, finite RESET reentry, nested PERFORM and interrupted-stack
isolation. Lower FAST passed. The CFG generic model test covers Invoke/Opaque ×
local/propagated typed/any exceptions and halt, plus missing/forged graph outcomes.
Final broad qualification is in progress.

The independent qualified-source schema was stale even against the merged IBM
baseline (COACTUPC V2 fails its old V1 constant). It now mirrors the existing
producer's closed V1/V2 shapes and D1 MODEL_STORAGE, with negative checks for
missing or malformed modelAssumed. No corpus expectation was relaxed.

RESET cycle oracle clarification: the initial graph-count expectation omitted the
transition from the first handler ingress to the recurring ingress. Running the
unchanged pre-D5 source analyzer independently proves all three selections (ROOT
to initial ingress, initial to recurring, recurring to itself). The expected
count is three; dead calls, destination identity and bounded-node checks remain.
Raw independent evidence: reset-cycle-before-d5/handler_reset_cycle/source.json.

The compiled architecture inventory adds only OutcomeExit, IndexBuilder.OutsideKey
and their explicit AIR exception types/standard collection dependencies. Existing
public structural descriptors, source inventory, Maven DAG and forbidden-package
checks are unchanged. The Class reference in the writer is the static
OutcomeExit.class::isInstance predicate, without textual discovery or loading.

## Review handoff

Implementation checkpoints D1–D5 are complete. Status stays IN_PROGRESS until
review/merge; no merge is authorized by this work item. See the
[qualification report](cics-control-qualification.md) for corpus results, the
regression found and corrected, reused evidence and remaining limits.

## Approved integration

The user approved D1–D5 and the historical gate repairs. Integration order is
frontend #65 → lower #40 → CFG #50. Each consumer pins the actual upstream merge
commit before its own merge. Git and these PRs record completion; qualification
and limits are in [the qualification report](cics-control-qualification.md).

Integration changes only documentation and equivalent-content pins. Required FAST
runs on each integrated main, with merge-tree and source-pin checks. Existing full,
560-input replay and support-preservation evidence can be reused only after
production, tests, contracts and build inputs are proved unchanged. No full/corpus
rerun is claimed for this documentary integration.
