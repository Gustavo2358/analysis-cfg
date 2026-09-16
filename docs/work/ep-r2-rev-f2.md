# R2-REV-F2 — Leading-Dollar COBOL Program Names

## Scope and hygiene

Existing Draft PR #39, branch `fix/ep-r2-entry-factorized-recall`.
Entry head: `3968e4847a1dfe0526be649576822751a09e774d`.
After successful fetch, origin/main remains
`194fac2af6cfc54053318164275e682feac5c750`; the worktree was clean.
The first sandboxed fetch failed on system SSH configuration permissions; the
normal authorized fetch outside that sandbox succeeded. No merge/auto-merge.

Only the CALL name interpretation policy is in scope. CFG, RD, Regional Values,
factorization, entry admission, KillAuthority, upstreams, pins, FILE and SYNC
remain unchanged. No confidential source or real AIR/product is versioned here.

## W0 — synthetic evidence before any production change

The unchanged interpreter uses `[A-Z_][A-Z0-9_@#$]{0,7}`. It trims only final
U+0020 for computed values. CallDependencyConsumer first retains supported raw
values, then adds interpreted candidates only when referenceName is non-null.
The empty interpreted list produces OPEN_TARGET.

New desired-behavior tests intentionally remain RED on the entry head:

- LeadingDollarNameTest: 7 tests, 5 semantic failures, zero errors/skips.
  `$PROGA   ` computed, `$PROGA` literal, `$ABC1`, `$TEST123   ` and `$ABCDEFG`
  are rejected. Existing positive forms and all negative controls pass.
- LeadingDollarProductTest: 5 tests, 3 semantic failures, zero errors/skips.
  `$PROGA` literal, `$PROGA   ` computed and the eight-character `$ABCDEFG`
  reach rawCandidates with nonempty support and provenance, but candidates and
  edges are empty and targetStatus is OPEN_TARGET. The computed BEFORE value
  and its producer/origin are verified before the failing candidate assertion.
- Four positive control outputs and seven negative outputs preserve their
  expected behavior. The independent dependency_wire reader accepts all 14
  synthetic JSON outputs, including the three failure witnesses.

[W0 evidence](ep-r2/rev-f2-w0.json) records the production hash, counts and
synthetic site facts. Raw logs/XML and preserved AIR/JSON baseline are under
`.harness-results/ep-r2/sanitization/`; synthetic-before must not be overwritten by
later GREEN output. Temurin 21.0.12+1.1 / release 21; existing pinned AIR build.
No FAST yet: this wave is deliberately before the authorized production fix.

## W0B — real hypothesis is not yet audited

Synthetic mechanism: CONFIRMED. The four-site real-case hypothesis is
**NOT YET CONFIRMED OR REJECTED**. Neither the real dependencies.json path nor
the matching AIR and expected nine-name set were supplied in the request.
A local search did not identify the confidential incident artifacts. This is an
artifact-location limitation, not evidence about the expected real-name set.

Required input was requested: local paths for the existing real dependencies.json,
its exact AIR and the expected nine-name set (or a local file containing it).
Do not change the regex until all four missing names have supported raw facts
and the loss is confirmed at name interpretation. If one is absent upstream,
stop and report the first observable missing boundary; do not infer a boundary
without the corresponding evidence.

For each missing site, keep the detailed incident audit local and confidential.
Real identifiers must not be versioned. Git may retain only non-sensitive
aggregate observations, never real names or site/support identifier mappings. The rerun must reuse
identical AIR bytes, upstreams and parameters, with only analysis-cfg changed.
Compare the exact expected set, all five prior names, all four new names,
candidate-specific support/provenance, raw spelling and each remainder.

## W1B — profile version decision before coding

`cobol-zos-dynamic-call-minimal@1` and its regex already exist in origin/main,
introduced by `1ab16bdeae8d8af23e723d0b239ba191a695764a`.
The original internal-contract statement in
[dependency-result contract](../architecture/analysis-dependency-result-v1.md)
and WORK-CFG-033 spec describes an explicit first version without speculative
compatibility machinery. It does **not** expressly authorize changing @1's
observable accepted language in-place.

Decision, conditional on W0B confirmation: introduce
`cobol-zos-dynamic-call-minimal@2` with exactly `[A-Z_$][A-Z0-9_@#$]{0,7}`.
No other new leading characters, normalization, truncation, callee lookup or
real-name exceptions. Preserve raw text and supports; retain the existing
computed/literal padding distinction and UnknownName/ExtensionName remainder.

Current JSON uses top-level interpretationProfile=per-site; COBOL sites receive
CallNameInterpreter.PROFILE as nameProfile. The mapper transports that string;
DependencySiteFact and dependency_wire accept an explicit nonempty profile.
No current test/validator freezes the old literal profile string. The older
1.0.0 section of the dependency-result document predates per-site 1.1.0; the
[current per-site contract](../domain/cics-program-control.md) and mapper govern.
The change is local to analysis-cfg; it needs no AIR/frontend/lower version or
new JSON field. Update the policy documentation and assert the @2 per-site
identity when implementing, while retaining @1 as the historical language.

## Remaining qualification

C2 local semantic rule plus its existing product boundary: desired REDs → minimal
policy/profile delta → focused interpreter/consumer/wire tests → H2, representative
F1/F-order and one existing computed regional case → same-AIR real rerun → one
FAST after stabilization. The old/no-dollar JSON baseline must remain equivalent
except for the intentional per-site profile revision. No automatic rerun of the
whole EP-R2, upstream, FILE, CardDemo or CICS campaigns.

EP-R2 ENTRY SEMANTICS: QUALIFIED (reused).
EP-R2 FACTORIZED VALUES DOMAIN: QUALIFIED (reused).
R2-REV-F1 DEPTH: QUALIFIED (reused).
R2-REV-F2 LEADING-DOLLAR NAME POLICY: NOT QUALIFIED; W0B input pending.
REAL CASE: NOT AUDITED; no 9/9 claim.
