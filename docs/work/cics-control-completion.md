# CICS control completion — D1–D5

Status: IN_PROGRESS. User-authorized implementation following COACTUPC discovery (2026-09-27). One review PR per repository; progressive commits; no merge.

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
