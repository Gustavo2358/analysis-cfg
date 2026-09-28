# CardDemo values/control — integration closeout

- id: CARDDEMO-VALUES-CONTROL
- status: DONE
- scope: post-W8 capabilities 1–4 and the two reviewed corrections.

## Integrated implementation

- [proleap-poc PR](https://github.com/Gustavo2358/proleap-poc/pull/75): `221c967ce22d7b78706efeb8b35aca1ca4efdad5`; reviewed head `73cca8e59045355c3b5e45ac147c29a8f9d865b9`.
- [cobol-lower PR](https://github.com/Gustavo2358/cobol-lower/pull/50): `b5182db28ec7f347dec6ab740848aab1b2a2b0a5`; reviewed head `9832d9c8781dd2f7ad3d3aee44261b6a23634539`.
- [analysis-cfg PR](https://github.com/Gustavo2358/analysis-cfg/pull/55): `bcc3471f259e019199deebfea138939aba3d1ab7`; reviewed head `d72a4ddeab6a00090bd8bc03505e6fa4f7f8f1d4`.

Commits are preserved with normal merges. Consumer locks pin actual merged
upstream revisions; no floating main is used as product authority. Documentation
closure is separate from semantic implementation.

## Qualified behavior and validation

- Ordered MOVE receivers and alias updates inside the admitted domain.
- Bounded table summaries, ASCII UPPER-CASE/TRIM and explicit register effects.
- Lexical SQL WHENEVER, bounded dynamic SQL, independent ENTRY roots and CICS dispatch.
- Restoration hypotheses require a causal path and respect proved later replacements.
- Level 88 does not allocate table storage or invalidate known geometry/initializers.

The latest qualification executed all 560 pipelines, including all 73 CardDemo
programs. PERFORM 39/39, Chaos 48/48 plus 28 rejected mutations, aliases 14/14 and
PERFORM adversaries 25/25 passed. All 18 new counterexamples/controls passed in
both logical and IBM physical profiles, with previous focal positives retained.
No candidates, supports or provenance were lost against the reviewed heads.
The cumulative W8 result remains 43 explained additions.

Required local FAST and exact-head CI passed in the three review repositories.
Integration changes only documentation and equivalent pins. Production, tests,
resources and contracts must remain identical to the qualified review trees;
the integration report records that comparison and the final main gates.
The 560-case corpus is reused on this explicit equivalence basis; it is not
reported as another execution. Full wrappers are not rerun for this closure.

Raw qualification and integration evidence remain in workspace
.carddemo-values-control/evidence. The local E2E repository records the final
merge SHAs, main gates and hashes in
`carddemo-values-control-integration-20260928/REPORT.md`.

## Remaining bounds and next campaign

PARTIAL and genuine remainders remain explicit. Same-family MOVE overlap,
unsupported functions/conversions, exact per-index table values, external runtime
SQL and exact CICS handler-stack execution remain outside the admitted domain.
Model-assumed declarations cannot prove runtime values, physical layout or kills.

Point 5 — sharing routine bodies while retaining caller/return context — belongs
to a new, separate PR/campaign. No implementation of that representation change
is part of this integration.
