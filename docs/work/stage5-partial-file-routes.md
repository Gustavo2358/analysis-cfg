# STAGE5-PARTIAL-FILE-ROUTES

[Current integration and qualification](air-codec-latency.md). The checkpoint scope below is historical.

Status: DONE / MERGED

Frontend PR #77 fixes a publication abort after partial parsing. Every retained
FILE destination has an authoritative UNKNOWN_LOCAL outcome when statement
control is unproved, including handlers without admitted regions. Grammar,
SP schema, lower, AIR, CFG and dependency algorithms remain unchanged.

This addition to Draft #57 records producer pins and integrated qualification.
The baseline is the previously qualified stage-5 runtime including normalization
and anonymous logical-root fixes. New partial inputs must retain diagnostics,
source-native FILE candidates and provenance with open remainders; no ordinary
continuation or handler reachability can be inferred from recovered metadata.

## Qualification

- 24 actual four-stage witnesses PASS. The previous frozen runtime aborts in
  18; all six already successful controls preserve the five products byte for
  byte. Seven frontend focal methods cover nine native operations, multi-file
  OPEN/CLOSE, SORT/MERGE, callbacks, handlers, damage before/after and missing
  data COPY that must not erase independently proved control.
- Independent oracle preserves FILE role coverage, partial proofs and provenance,
  source-native inventory and qualified INDD candidates, plus open AIR control.
  Six corruptions (missing role, invented normal/handler, closed remainder,
  removed candidate or provenance) are rejected.
- Existing FILE composite oracle: 22/22 complete pipelines PASS, including
  operand order, matched PERFORM returns, FILE STATUS aliases and error routes.
- New frontend execution over all 560 sources: SP bytes identical in 560/560.
  Populations: CardDemo 73, PERFORM 39, Chaos 48, aliases 14, PERFORM adversaries
  25, frontend/general fixtures 331, prior focal 29, frontier payload 1.
  No unexpected delta; all 73 CardDemo programs included.
- Frozen runtime jars differ only in `ControlTopologySemantics.class`; 3,787
  other entries are identical. The 2,240 AIR/CFG/dependency/qualified-source
  products are explicitly reused for those unchanged SPs. This is not a fresh
  560-case four-stage replay. Candidates/supports/provenance cannot change under
  this byte-equivalent consumer input and implementation.
- FAST: frontend PASS (36.814 s; final seven focal methods rerun separately),
  lower PASS (407.577 s), CFG PASS (252.044 s). Final pin/document checks cover
  later documentation-only commits. Full wrappers and AIR qualification were
  not rerun: no AIR/consumer algorithm or wire contract changed.

## Limits

This fixes recovery publication, not arbitrary syntax or executable reachability
through damaged input. Nineteen malformed-input SPs retain INPUT_MISSING; the
18 that contain FILE uses also retain PARTIAL dependencies and open native FILE
remainders. A malformed input with no FILE use keeps the pre-existing downstream
status, byte-identical; downstream status alone does not certify source validity.
Source occurrences lacking a control qualification still do not gain candidates.
No new normal continuation, handler edge or whole-program completeness is claimed.

Raw logs, per-case source/SP hashes and compressed complete frontend products
are under `.shared-routine-bodies/evidence/file-partial-*`. The local E2E report,
inputs, oracle and runner snapshots are in
`artefatos-e2e/shared-routine-bodies-20260930/partial-file-routes/REPORT.md`.
Drafts #77 / #52 / #57 stay open. No merge or stage-5 scope expansion.
