# Analysis gaps campaign

## Dependency on PR #40

STACKED ON PR #40. Temporary parent/base: `discovery/regional-explosion-fixtures`.
Parent validated SHA / G0_BASELINE_SHA: `f0efa4a76984781e09c26b92d4f6ee9cb2591f8f`.
New branch: `discovery/analysis-gaps`; isolated worktree: `.analysis-gaps/analysis-cfg`.
Draft PR will target the parent, never main while #40 is open. No auto-merge.
After #40 merges: incorporate its final head, rebase/update from main and retarget.
If W4 adds only documentation/evidence, continue normally. If W4 adds production
changes, incorporate the FINAL #40 head before closing dependent implementation.
No synchronization is performed by G0/W1. Corporate E2E is not run.

## G0

FACT: local parent ref and remote-tracking ref both identify the exact requested
commit; isolated branch was created directly from it. Original checkout is clean
`main`, HEAD `194fac2af6cfc54053318164275e682feac5c750`, 47 behind its cached upstream.
All 12 pre-existing live worktrees were clean; none was reset/rebased/modified.
One stale `/tmp/w5-inventory-repro/fix` entry is prunable and was left untouched.
Only unpublished branch commit reported against cached remote refs was
`1e12b45` (unrelated frozen investigation); this is a local observation, not a
fresh remote audit. Detailed paths/heads/status: local `.harness-results/g0-hygiene.json`.
Initial GitHub read failed due sandbox network; publication will be retried.

Inherited focal baseline, Java 21.0.12, new clean tree at parent SHA:

```sh
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-values -am \
  -Dtest=RegionalExplosionFixturesTest,RegionalAlternativesTest,RegionalValuesTest,StorageIndexTest,CfgBuildCoordinatorTest test
```

PASS: 39 tests, zero failures/errors/skips. Raw log: `.harness-results/g0-final.log`.
Two earlier commands stopped on reactor modules with no selected test; neither
was a semantic failure or reported as PASS. The final selection includes the
required per-module smokes without weakening the harness.
Build dependencies were copied into the new ignored build directory; AIR source
is pinned to `135d91f4d643c80eeb5d7bff9081fae229e9e62c`; no sibling build was mutated.

G0 COMPLETE — STACKED CAMPAIGN FOUNDATION READY.

## W1 — VALUE/MOVE/storage → CALL

IN_PROGRESS. Trace evidence and characterize before deciding any production fix.
W2 NOT STARTED. W3 NOT STARTED.
