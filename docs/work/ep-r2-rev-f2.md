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

The W0 interpreter used `[A-Z_][A-Z0-9_@#$]{0,7}`. It trims only final
U+0020 for computed values. CallDependencyConsumer first retains supported raw
values, then adds interpreted candidates only when referenceName is non-null.
The empty interpreted list produces OPEN_TARGET.

The frozen desired-behavior tests were RED on the entry head:

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
W0 did not run FAST: that wave intentionally stopped before production changes.

## W0B — real hypothesis is not yet audited

Synthetic mechanism: CONFIRMED. The four-site real-case hypothesis is
**NOT YET CONFIRMED OR REJECTED**. Neither the real dependencies.json path nor
the matching AIR and expected nine-name set were supplied in the request.
A local search did not identify the confidential incident artifacts. This is an
artifact-location limitation, not evidence about the expected real-name set.

The product owner subsequently confirmed PRE-RELEASE status and authorized the
leading-dollar capability independently of this audit. F2-W0B now means a later
incident audit, not implementation permission. The existing real dependencies.json,
matching AIR and exact expected nine-name set are still needed for that audit.
Do not infer an upstream boundary or claim 9/9 without those artifacts. If a name
remains absent after the same-AIR rerun, record the first observed loss and stop
for review; do not invent another fix.

For each missing site, keep the detailed incident audit local and confidential.
Real identifiers must not be versioned. Git may retain only non-sensitive
aggregate observations, never real names or site/support identifier mappings. The rerun must reuse
identical AIR bytes, upstreams and parameters, with only analysis-cfg changed.
Compare the exact expected set, all five prior names, all four new names,
candidate-specific support/provenance, raw spelling and each remainder.

## W1B — explicit pre-release authority supersedes the earlier proposal

The initial W0 proposal of a new profile revision was superseded by the product
owner's explicit PRE-RELEASE VERSIONING POLICY. The product has never had a
production baseline or external consumer. Git main is development, not publication.
The [canonical policy](../architecture/extensibility.md#política-de-versionamento-pré-release)
requires correcting local semantic bugs in-place until the first actual bank
baseline; cross-repository wires may still identify incompatible snapshots.

Decision: retain **cobol-zos-dynamic-call-minimal@1** and change only the initial
character class from `[A-Z_]` to `[A-Z_$]`. No other character, length, padding,
case, UnknownName/ExtensionName, evidence or remainder semantics changes. The
[canonical name policy](../domain/cp6-call-name-policy.md), architectural authority
and AGENTS reference are committed with the regex and assertions freezing @1.

Current JSON has top-level interpretationProfile=per-site and per-site nameProfile.
Both the constant and emitted COBOL profile remain @1. No AIR/frontend/lower,
new wire version, compatibility shim or legacy mode is introduced.

## Remaining qualification

C2 local semantic rule plus its existing product boundary: desired REDs → minimal
in-place policy delta → focused interpreter/consumer/wire tests → H2, representative
F1/F-order and one existing computed regional case → one FAST after stabilization → same-AIR manual real rerun. The old/no-dollar JSON baseline must remain equivalent
byte-for-byte, with no profile revision. No automatic rerun of the
whole EP-R2, upstream, FILE, CardDemo or CICS campaigns.

EP-R2 ENTRY SEMANTICS: QUALIFIED (reused).
EP-R2 FACTORIZED VALUES DOMAIN: QUALIFIED (reused).
R2-REV-F1 DEPTH: QUALIFIED (reused).
R2-REV-F2 LEADING-DOLLAR NAME POLICY: qualification in progress; implementation authorized.
REAL CASE: NOT AUDITED; no 9/9 claim.
