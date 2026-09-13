# ENTRY + LOCALIZED INPUT COMPLETENESS

**READY FOR HUMAN REVIEW.** One Full CardDemo run completed after the classified
40-program qualification. All known facts remain partial.

Focused qualification: **40/40 known starts, 40 AIR/CFG/dependency results,
37/37 previously stranded CALLs analyzed**. All 37 have known candidates and
remain `PARTIAL_RESOLVED`; no new downstream blocker in the classified population.

| Discovery class | Programs admitted | CALLs newly analyzed | Known candidates | Partial | Open unresolved |
| --- | ---: | ---: | ---: | ---: | ---: |
| A — leading ENTRY | 3/3 | 9 | 9 | 9 | 0 |
| B — missing DFHAID/DFHBMSCA | 35/35 | 10 | 10 | 10 | 0 |
| C — missing MQ COPYs | 2/2 | 18 | 18 | 18 | 0 |

`DBUNLDGS.CBL`, `PAUDBLOD.CBL`, and `PAUDBUNL.CBL` all reach dependency.
The 37 DATA-input programs retain 82 explicit `UNRESOLVED_COPY` occurrences,
INPUT_MISSING inventories and incomplete signatures/storage. No source stubs,
copybook downloads, or CardDemo edits. Missing PROCEDURE COPY remains blocked.

The [historical baseline](carddemo-full-baseline.md) and its 28 MB JSON remain
unchanged. The [new immutable pins](carddemo-entry-localization-pins.json) retain
upstream `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e` and the original AIR/CFG builds.
Frontend production: `38b8d7d8cf5b363ec4bda0d2517450b6b1b6ffdd`.
Lower production: `e5fbe832b3f073964df67302a6923afd1a6fcd3f`; final
`24be819d96bd640f1c090f7c82dbb8c6e7ae31d3` changes only the future-version test.
The focused run used the production commit; Full uses the final test commit.

## Full CardDemo delta

[Small machine-readable comparison](carddemo-after-entry-localization.json):
157 KB, with all 40 changed programs, per-site candidates/remainders and timing.
No copy of the historical 28 MB artifact.

| Metric | Before | After |
| --- | ---: | ---: |
| Programs attempted | 73 | 73 |
| SP produced | 67 | 67 |
| AIR / CFG / dependency programs | 27 / 27 / 27 | 67 / 67 / 67 |
| ENTRY_START blockers | 40 | 0 |
| CALL sites observed in SP | 73 | 73 |
| CALL sites analyzed / reachable | 36 | 73 |
| Sites with known candidates | 36 | 73 |
| Closed resolved | 0 | 0 |
| Partial resolved | 36 | 73 |
| Open unresolved | 0 | 0 |

**37 of 37 previously stranded CALLs now reach dependency**: A=9, B=10, C=18.
All have known candidates and modelValueRemainder=false. All 73 analyzed sites
retain source, interpretation, effective and open-control remainders. Thus zero
OPEN_UNRESOLVED does not imply closed runtime dependencies. The 36 preexisting
sites preserve exact known candidate values, classifications, reachability and
all five remainder fields. No source CALL disappeared or was added.

**New blockers: NONE.** The six previous failures before SP remain: four
PREPROCESSING_FAILED (app/cbl/COTRTLIC.cbl, app/cbl/COTRTUPC.cbl and their ZIP
`.cl2` variants), FIXED_FORMAT_TAB (app/app-transaction-type-db2/cbl/COTRTLIC.cbl),
and NORMALIZATION_REJECTED (ZIP migrated_app/cbl/CBSTM03A.cbl). CALLs in those six
sources remain unknown because SP is unavailable. No follow-on capability was
implemented.

Observed corpus time: **192.221 s → 317.438 s** (+125.217 s). Mean program time
2.631 s → 4.346 s; median 2.466 s → 3.926 s; p95 5.353 s → 7.553 s.
These are sequential process measurements with the same source snapshot and
Java 21/-Xmx2g, including the additional downstream work for 40 programs.
This is a coverage delta, not an isolated performance benchmark or an SLA.
Raw Full outputs: `/tmp/entry-localization-20260913/full`; the original baseline
outputs remain at `/tmp/carddemo-full-20260913/canonical`.

## Implementation and soundness

The [frontend rule](https://github.com/Gustavo2358/proleap-poc/blob/38b8d7d8cf5b363ec4bda0d2517450b6b1b6ffdd/docs/architecture/entry-localized-input.md)
uses typed grammar relations for primary entry and diagnostic occurrence anchors
carried by the preprocessing source map. Only gaps qualified inside the selected
DATA region can preserve the independent start. Unlocated/other input gaps and
lexer/parser/preprocessor errors remain conservative. SP 1.9 explicitly permits
known start with incomplete inventory/signature. Alternate ENTRY interfaces and
runtime semantics remain unmodeled. No AIR, CFG, lattice, or global algorithm change.

The lower changes only version admission to the existing typed decoder.
Real focal pipelines reach dependency for leading ENTRY, missing DATA input and
unknown declarations. The leading-ENTRY fixture retains PROGA. Both missing-DATA
fixtures remain open with no candidates: existing storage/binding proofs do not
recover the independent WS-PGM value under incomplete data. Entry localization
makes no storage-independence claim. The missing-PROCEDURE fixture remains blocked
at explicit entry admission, including a gap after the first visible MOVE.

Permanent FAST tests cover one/multiple leading ENTRYs; MOVE/PERFORM/DISPLAY starts;
later ENTRY and empty body; one/multiple/nested DATA gaps; unknown declaration;
repeated include provenance across DATA/PROCEDURE; unlocated gaps and parser errors.
Existing declaratives policy remains covered.

## Validation

- Frontend FAST and qualification-local PASS: 615 tests, one preexisting skip;
  source-normalization regression and naming PASS.
- Lower FAST and qualification-local PASS, including W1 literal/dynamic/open,
  IF, MOVE data, PERFORM BASIC, MULTI-CALL and partial/compositional contracts.
  The first qualification attempt exposed an old test using SP 1.9 as an unknown
  future version. Its rejection oracle now uses 1.10; no production change was
  needed. The completed qualification includes semantic, capacity/performance
  and architecture checks.
- CFG FAST PASS; no CFG production change or CFG Full. The 25 regenerated
  frontend partial/compositional SP fixtures are identical to their stored SP 1.8
  counterparts except the contract version, including P1–P5, READ and 1/2/5/40
  composition. Historical 1.6/1.7 fixtures remain in the lower compatibility suites.
- Runner/comparison focal tests: 23 PASS. Remote CI is FAST only.

Raw focused runs and focal pipeline outputs:
`/tmp/entry-localization-20260913/{classified40,focals}`.
Qualification logs: `/tmp/entry-frontend-full.log`,
`/tmp/entry-lower-full-final.log`; original failed attempt remains
`/tmp/entry-lower-full.log`. No evidence bundle or copied baseline was created.

## Reproduction

Use the existing runner with explicit new pins and a fresh output directory:

```sh
python3 -B scripts/project/carddemo_setup.py --work /tmp/entry-build \
  --pins docs/evals/cp6/carddemo-entry-localization-pins.json
python3 -B scripts/project/carddemo_baseline.py --upstream /tmp/carddemo-upstream \
  --runtime /tmp/entry-build/runtime.json --work /tmp/entry-full \
  --pins docs/evals/cp6/carddemo-entry-localization-pins.json
python3 -B scripts/project/carddemo_entry_delta.py \
  --baseline docs/evals/cp6/carddemo-full-baseline.json \
  --measurements /tmp/entry-full/measurements.json --output /tmp/entry-delta.json
```

The comparison validates artifact digests, unchanged source bytes and missing-COPY
inventory, joins each dependency site to its typed SP CALL, matches sites across
versions by original span/include instance, and rejects changes to existing known
candidates/remainders. It writes only the small comparison, never the historical
JSON. Publication IDs remain local to each run.

Drafts: [frontend #41](https://github.com/Gustavo2358/proleap-poc/pull/41),
[lower #18](https://github.com/Gustavo2358/cobol-lower/pull/18).
Human review before merge; no next capability selected or implemented.
