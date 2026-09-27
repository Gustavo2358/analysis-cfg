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

## Final gate and pins

CFG/dependencies FAST PASS, including contract tests and compiled architecture boundaries.
Final producer `f8170f513eef29adfff5a94f418e1c6481ee2365`; lower `be09905ca5a8d01f26a7e57afa9bc2697fe2df98`.
The gate used frontend `503e11f6b33daa504e6338cf84a11984acab82cf` and lower `91d2cfeb9392ce7563f5f02da3b28bb6164ca2bb`.
Frontend changed only its runtime-label report; lower changed only docs/pins.
Git comparison proves all consumed production code unchanged. The 12 rebuilt
producer/consumer JARs have byte-identical production classes/model resources
to the frozen E2E runtime. Final docs/pin checks PASS; FAST evidence is reused
for these equivalent-content repins, with no repeated semantic run.
