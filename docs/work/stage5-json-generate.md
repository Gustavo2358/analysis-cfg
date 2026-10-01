# STAGE5-JSON-GENERATE — checkpoint 4

Status: IN_PROGRESS

This consumer only repins the frontend/lower authorities; production and wire contracts
are unchanged. Frontend PR #77 admits the documented IBM JSON GENERATE grammar,
preserves operands and exception scopes, and publishes existing CONTROL_POSSIBILITY
facts for its source alternatives. Unknown runtime effects remain a boundary.
No serializer, executable successor or memory proof is fabricated downstream.

The exact previously unsupported sentinel now preserves BEFORE and AFTER in both
profiles. Thirteen additional cases run in two profiles: 26/26 pass through all
four production CLIs, including nested handlers and the negative where both
handlers return and AFTER stays excluded. Four oracle mutations are rejected.
Source-qualified candidates retain their qualifications, provenance and remainder.

Frontend FAST 725 and qualification-local 1296 pass (one preexisting optional corpus
skip); the full normalizer regression and naming checks pass. Fresh frontend
replay produces 560 identical SPs, including all 73 CardDemo, PERFORM 39, Chaos 48,
aliases 14 and PERFORM-adversarial 25. All 2,036 consumer/runtime classes outside
the frontend are byte-identical; 2,240 downstream products are hash-checked/reused,
not rerun. The new JSON inputs received fresh downstream executions.

Consumer FAST is run after the exact authority repin; the aggregate report records
its result and review SHAs. Full consumer wrappers are not repeated: code is
unchanged, old SPs are identical and new possibilities cross the real consumers.
Evidence: artefatos-e2e/shared-routine-bodies-20260930/json-generate/ in the aggregate
workspace. Existing checkpoints 1–3 and their historical evidence remain intact.

Runtime JSON serialization, generated values, complete operand typing and JSON
PARSE remain outside this fix. SP 2.62 and all consumer wire versions are unchanged.
Same stage-5 Draft PR; no merge.
