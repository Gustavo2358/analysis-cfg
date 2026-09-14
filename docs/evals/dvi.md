# DVI — Declarative Value Inference

Status: W2 complete; W3 final qualification in progress. No merge or auto-merge.

This PR changes selected E2E fixtures/oracles, evidence and strict upstream pins.
**CFG production Java is unchanged**, as are AIR model/codec/normative contracts.
Entry literals proved by the frontend use the existing Regional Values/RD engine
and existing CALL/LINK/XCTL dependency consumers. Runtime possibilities remain open.

Frontend Draft [#49](https://github.com/Gustavo2358/proleap-poc/pull/49),
lower Draft [#26](https://github.com/Gustavo2358/cobol-lower/pull/26).
Current SP 2.15.0/storage 1.4.0; AIR 2.0.0/JSON 1.0.0; dependency JSON 1.1.0.
The exact producer and AIR pins are in `docs/sources/sources.lock.json`.

Newly executed: 21 W2 and 25 W3 source→SP→AIR→CFG/dependency E2Es;
13 existing RegionalInitialTest/RegionalCompositionTest checks. Frontend focused
and FAST, lower source contract/entry/CICS tests and FAST passed in their products.
The W3 frontend adds 13 focused tests with multi-blocker, coverage and disabled
extension challenges. Final remote checks and three-source real comparison are
being recorded; no global/full/73-source gate is claimed.

Run the existing prepared-build production CLI composition:

```sh
python3 -B scripts/project/e2e_dvi.py --frontend FRONTEND --lower LOWER --m2 M2 --work NEW_OUTPUT
python3 -B scripts/project/e2e_dvi_cohort.py --frontend FRONTEND --lower LOWER --m2 M2 --before W0_OUTPUT --work NEW_REAL_OUTPUT
```

The helpers reuse existing runtime classpaths; they never build per fixture or
introduce a second value engine. Fixtures contain handwritten names/proofs;
oracles inspect candidates, three remainders, BEFORE query, supports, source proof
origin, no synthetic MOVE and overwritten VALUE absence. Exact input hashes and
stage commands/logs are retained. `docs/evals/dvi/selected-summary.json` records
the 25 final outcomes. Unknown input and foreign effects deliberately remain open.

W0 selected real sources: COACTUPC, COCRDSLC and COUSR01C, unchanged pinned
CardDemo bytes. COACTUPC/COCRDSLC contain VALUE COMEN01C→MOVE→XCTL with a runtime
alternative. All have missing DFHAID/DFHBMSCA input and unmodeled effects. W3 must
report the measured candidate delta, including NO_REAL_GAIN if those blockers
remain. Fixing missing system copybooks or modeling other effects is outside DVI.

Merge/repin after human review: frontend → repin lower → lower → repin CFG → CFG.
The existing source-proof limits are documented in the frontend/lower DVI notes.
