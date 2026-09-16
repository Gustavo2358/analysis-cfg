# R2-REV-F2 — Leading-Dollar COBOL Program Names

## Scope and authority

Same Draft [PR #39](https://github.com/Gustavo2358/analysis-cfg/pull/39), branch
`fix/ep-r2-entry-factorized-recall`. No merge or auto-merge.
F2 entry/base: `3968e4847a1dfe0526be649576822751a09e774d`.
Main: `194fac2af6cfc54053318164275e682feac5c750`, untouched.

The requirement is generic: a COBOL program name may start with `$`; the character
is part of the name. Only `CallNameInterpreter` changes in Java production.
CFG, RD, Regional Values, storage, KillAuthority, upstreams, pins and frozen FILE
remain unchanged. The independent Python reader accepts the same language.

The explicit [pre-release policy](../architecture/extensibility.md#política-de-versionamento-pré-release)
requires correcting unpublished local semantic bugs in place. The profile stays
**cobol-zos-dynamic-call-minimal@1**. No new profile, legacy mode or wire revision.

## Sanitized history

The F2 portion was rebuilt as three commits using synthetic data, including the
RED commit itself. The base and all earlier EP-R2/F1 commits are preserved.
The exhaustive search covers all 16 PR commit trees and the current 2,876
tracked files, including recursive gzip/xz/tar/zip contents, filenames and commit
metadata: zero occurrences of the locally supplied prohibited identifiers. The
three rebuilt F2 commits are individually clean. The PR body has zero matches
and the PR has no comments. The local denylist is not versioned.
No real-to-synthetic mapping is retained. Production is byte-identical to the
previously qualified F2 implementation; sanitation changes fixtures, method names,
inventory, documentation and evidence, not analysis behavior.

The [canonical harness rule](../engineering/lean-harness.md#confidential-incident-data)
prohibits promoting real/corporate incident identifiers into fixtures, tests,
documentation, evidence, regression data, commit history or PR discussion.
**Real identifiers must not be versioned.** Detailed real-case evidence stays
local and confidential. Git may contain only non-sensitive aggregate observations.
AGENTS.md links directly to this authority.

## Rebuilt RED and unchanged correction

Synthetic RED commit: `249206dfa21e2cd1fd57b8de60aaa77f9248706c`.
Production/policy/test commit: `058312e71d06cd9df5218ee4e6ba1d48aef83b92`.
The fix retains the regex `[A-Z_$][A-Z0-9_@#$]{0,7}` in @1, replacing only the
initial class of `[A-Z_][A-Z0-9_@#$]{0,7}`. `$PROGA` stays `$PROGA` in
referenceName and in the edge. Computed `$PROGA   ` drops only trailing U+0020
from referenceName; rawValue remains integral. Literal padding is still rejected.

The [W0 evidence](ep-r2/rev-f2-w0.json) was regenerated against unchanged pre-fix
production, using synthetic examples only:

- Seven interpreter tests: five semantic failures, zero errors/skips.
- Five product tests: three semantic failures, zero errors/skips.
- Fourteen baseline JSON products pass the independent reader. Their content
  equals the previous synthetic baseline. The three leading-dollar witnesses
  have supported raw values, empty candidates/edges and OPEN_TARGET.

Examples include `$PROGA`, `$ABC1`, `$TEST123` and `$ABCDEFG`. They are synthetic
representatives of the policy, not substitutions documented against real names.
Existing positives, negative forms, eight-character limit, raw values,
UnknownName remainder, support and provenance remain covered.

## Qualification after rebuilding

[Validation ledger](ep-r2/rev-f2-validation.json), JDK 21:

- NEW: 28 focused Java tests across seven suites pass, including eight interpreter
  and six product methods. Constant and emitted COBOL profiles are frozen at @1.
- NEW: nine independent dependency JSON reader tests pass.
- NEW: 15 AIR → production CLI → dependencies.json outputs are byte-identical
  to in-memory output and pass the reader. The Regional Values case checks BEFORE,
  supported raw value, provenance, candidate and edge.
- NEW: all 14 reconstructed W0 AIR inputs remain byte-identical. Three corrected
  products differ only in candidates/status/edges; all raw/support/provenance/
  premises/remainder fields remain identical. Eleven controls are byte-identical
  as entire products, including the unchanged profile.
- NEW: one FAST after rebuilding passes 515 Java methods with zero failures,
  errors or skips, plus Python and compiled architecture checks. Observed duration:
  79.164 seconds; no production changes after the gate.

REUSED: 69 neighboring Java regressions and H2/F1/two F-order product verticals
from the preceding qualification, plus EP-R2 entry/domain/F1 evidence. Their
production and pins are unchanged. The ledger labels these as reused, not rerun.
NOT RERUN: historical campaigns, CardDemo, full CICS, FILE, other repositories or
broad corpus. This sanitation does not invalidate their production boundaries.

Raw logs, XML and reconstructed baseline stay in the ignored local directory
`.harness-results/ep-r2/sanitization/`. The obsolete incident-bearing evidence
is not promoted into the reconstructed history. No failed attempt is relabeled PASS.

Reproduce after the pinned build, on JDK 21:

```sh
mvn -pl analysis-adapters -am -Dtest=CfgPreflightTest,StorageRangeTest,FactorizedAlternativesTest,RegionalAnalysisTest,NameInterpreterTest,LeadingDollarNameTest,LeadingDollarProductTest test
python3 -B -m unittest discover -s scripts/project -p test_dependency_wire.py
python3 -B scripts/project/ep_r2_names.py --maven-repo .harness-results/build/m2 --output .harness-results/f2-replay
python3 -B scripts/harness/lean.py fast
```

For the historical comparison, supply the locally preserved synthetic baseline
with `--before .harness-results/ep-r2/sanitization/synthetic-before`. The CLI oracle
does not fabricate a baseline when absent.

## Real-case handoff remains local and pending

The four previously absent real names and the expected real-name set are not
versioned. The exact prior AIR/result, parameters and expected set remain
unavailable here. No X/9, 9/9 or first-loss claim is made.

Feature work and incident reruns are paused for sanitation. A subsequent authorized
manual rerun must use the same AIR/upstreams/parameters and compare the expected
real-name set locally, preserving the five previously present names and checking
supported raw/candidate/edge facts for the four previously absent real names.
Keep names, site identifiers and detailed comparisons confidential. Publish only
non-sensitive aggregate counts/status. If a name still disappears, record the
first-loss boundary locally and stop for review without inventing a new fix.

PRE-RELEASE POLICY: DOCUMENTED.
LEADING-$ CAPABILITY: QUALIFIED.
PROFILE: cobol-zos-dynamic-call-minimal@1.
SYNTHETIC PRODUCT: QUALIFIED.
REAL CASE: AWAITING MANUAL SAME-AIR RE-RUN; not executed by sanitation.
FAST: PASS.
PR: Draft, no merge, no auto-merge.

EP-R2 ENTRY SEMANTICS, FACTORIZED VALUES DOMAIN and R2-REV-F1 DEPTH remain
QUALIFIED on their recorded evidence.
