# CardDemo IBM catalogue consumer validation

- id: CARDDEMO-IBM-COPYBOOKS-CFG
- status: IN_PROGRESS
- scope: Pin expanded structural models; validate unchanged model confidence semantics across the full CardDemo corpus.

The producer adds all seven missing IBM members: six MQ and Db2 SQLCA, extending
the two CICS members already modeled. SP 2.49 / nominal V2 remains unchanged.
There is no CFG, values, dependency, codec or AIR production delta in this expansion.

All 73 CardDemo variants ran frontend → lower → CFG → dependencies with immutable
binaries. All candidate sets, executable supports, conditional candidate evidence
and assumptions, source operand and COPY/INCLUDE provenance were preserved.
COTRTLIC/COTRTUPC have expected uncertainty detail changes: opaque SQLCA becomes
unavailable model input; adjacent DCLTRTYP opacity gets its own source provenance.
No uncertainty was used as proof of kill. 24 missing includes now have models.

21/21 structural adversaries passed, including seven new MQ/SQLCA cases.
PERFORM 39, Chaos 48, aliases 14 and PERFORM adversaries 25 reran the frontend
with byte-identical SP/compilation products. Their downstream evidence is reused
with identical consumer binaries and hashes. Frontend FAST: 602 tests passed.
The complete CardDemo run is newly executed, not reused.

Evidence: frontend `docs/work/carddemo-ibm-validation.json` and local
`.synthetic-dfh/carddemo-expansion/`. Synthetic inputs remain PARTIAL. No physical
layout/initial-byte proof, invented CFG edges or merge.

## Expansion gate and pins

CFG/dependencies FAST PASS, including contract tests and compiled architecture boundaries.
Final producer `f8170f513eef29adfff5a94f418e1c6481ee2365`; lower `be09905ca5a8d01f26a7e57afa9bc2697fe2df98`.
The gate used frontend `503e11f6b33daa504e6338cf84a11984acab82cf` and lower `91d2cfeb9392ce7563f5f02da3b28bb6164ca2bb`.
Frontend changed only its runtime-label report; lower changed only docs/pins.
Git comparison proves all consumed production code unchanged. The 12 rebuilt
producer/consumer JARs have byte-identical production classes/model resources
to the frozen E2E runtime. Final docs/pin checks PASS; FAST evidence is reused
for these equivalent-content repins, with no repeated semantic run.

## Review follow-up — current authority and exact pins

Current frontend: `e2d825b1551dd7a730ae79c4a1b7141586c23fc9`; lower: `6860a052878ea7f1718490fbe48f884eb630cd77`.
The producer restricts synthetic SQLCA to EXEC SQL INCLUDE; real COPY SQLCA
continues normally and missing COPY SQLCA stays unresolved. No consumer code
changes. Lock descriptions now identify SP 2.49 / NOMINAL_TEXT_SOURCE_V2,
modelAssumed and Drafts #64/#39; stale PERFORM PR and SP 2.47 descriptions were
replaced. Historical authority pins are unchanged. Wave-specific evidence and
storage checkpoints are explicitly historical; upstream-state separates current
integration from the chronological record.

Review validation newly executed: frontend focal 28/28 and FAST 603/603;
lower FAST and CFG/dependencies FAST PASS, including contract/architecture checks
(CFG: 638 required methods, zero skips). All 73 CardDemo, 21 structural
adversaries, 39 PERFORM, 48 Chaos, 14 aliases and 25 PERFORM adversaries reran
the frontend: all 220 SP/compilation pairs are byte-identical. Downstream corpus
stages were not rerun; their evidence is reused with identical inputs and consumer
binaries, preserving candidates, supports and provenance. Twelve rebuilt JARs
have the same production classes/model resources as the immutable runtime.

The consumer FAST gates used frontend implementation
`3a4d9e9cbbce4d481eaf58db9e4ef8463e635914` and the prior lower HEAD
`be09905ca5a8d01f26a7e57afa9bc2697fe2df98` plus metadata edits. Final frontend and
lower commits differ only in docs/pins. Final docs and pin checks pass. Raw logs,
result hashes and compiled equivalence: `.synthetic-dfh/carddemo-review-fixes/`;
producer report: `docs/work/carddemo-ibm-review-validation.json`. Full qualification
and the historical 310 matrix were not executed. Draft #49 remains open; no merge.
