# CP6-BASELINE-HARNESS-001 — HARNESS FIX COMPLETE / NEW BLOCKER FOUND

**STOP FOR HUMAN REVIEW.** WORK-CFG-031 is IMPLEMENTED_HARNESS_FIX / BLOCKED_BY_NEW_FINDING / AWAITING_HUMAN_REVIEW. HARNESS-REGISTRY-DRIFT-001 is RESOLVED. The exact frozen candidate passed focal/fast/docs, then failed **global architecture** at the W5 compiled inventory oracle. No subsequent candidate gate or remediation was attempted.

Repo: analysis-cfg. Branch: `fix/w5-inventory-reproducibility`. HEAD/base: `15bd3afe1affdcb5ec49956960f884bde8c89498`. The fix and evidence remain local/uncommitted. Commit, push, PR and remote CI were not executed because the explicit architecture-failure STOP (request items 23/48) occurred first. No merge or auto-merge.

## Historical hypothesis and new exact RED

The [previous STOP](previous-stop.md) correctly concluded NOT REPRODUCED at that time. The human follow-up accepted it and authorized only the registry harness correction. That history has not been rewritten.

The W5 inventory mismatch is now **REPRODUCED as an oracle failure by new independent evidence**. The cause of the older occurrence is still **NOT ESTABLISHED**; this new observation does not prove historical causal identity. No causal production/inventory correction was applied.

Provisional finding: **W5-JAVAP-DIAGNOSTIC-CONTAMINATION-001**, OBSERVED / NOT RESOLVED.

| Property | Factual result |
| --- | --- |
| First failing gate | architecture |
| Exact command | `bash scripts/harness/check-architecture.sh` |
| Working directory | `/home/gustavo/workspace/teste-e2e/.w5-recovery/remediation/candidate` |
| Exit | 1 |
| First failing oracle | `scripts/project/check_w5.py:68`, exact expected inventory equality |
| Expected | canonical inventory SHA-256 `f7f268d52d267cd6a1a6052dbd4326588a9f87c58178a4074c498d0f6c969cee` |
| Actual | canonical inventory SHA-256 `994e46f0f0161b386e586812ce8f40703187661df9b3fec03861a1f63b09344d` |
| Exact location | analysis-adapters → javap_descriptors → LocalResultWriter$Attempt.class |
| Difference | one JVM warning prefix; the remaining descriptor text equals expected |
| Main also fails | NO: fresh main architecture and separate full PASS |
| Candidate W5 isolated | NOT RUN after STOP; previous PASS is historical only |
| Candidate full | NOT RUN after STOP |
| Deterministic reproduction | NOT ESTABLISHED: one captured occurrence; no repeated probe after STOP |

The extra line is:

```text
[0.001s][warning][perf,memops] Cannot use file /tmp/hsperfdata_gustavo/4248 because it is locked by another process (errno = 11)
```

[Exact focused diff](HARNESS-REGISTRY-DRIFT-001/validation/candidate/architecture/focal-diff.patch), [full expected/actual comparison](HARNESS-REGISTRY-DRIFT-001/validation/candidate/architecture/inventory-diff.json), [first blocker receipt](HARNESS-REGISTRY-DRIFT-001/first-blocker.json), and [raw architecture log](HARNESS-REGISTRY-DRIFT-001/validation/candidate/architecture/run.log.gz). The uncompressed log SHA-256 is `d312efecd2a53c2c7c2c62e3fbb539b33600252cccc4bc7234e9589733d38d93`. The receipt includes exact tool paths/versions, environment, changed descriptor strings and retained build-state archive path/hash. Generated build products remain outside the deliverable in persistent workspace storage.

Relevant unchanged authorities are `scripts/project/check_w5.py`, `docs/evals/resource-limit-w5-inventory.json` and the compiled LocalResultWriter$Attempt class. A read-only external trace captured the original gate's actual value; it did not replace the calculation or its verdict. No warning filtering, environment workaround, inventory regeneration or code correction was attempted. **NO REMEDIATION ATTEMPTED.**

## Registry fix and independent oracles

The old test assigned `registry.status = blocked` without consulting work-item status. [Controlled old reproduction](HARNESS-REGISTRY-DRIFT-001/old-blocked/receipt.json): setup succeeded, exit 1, work and registry remained blocked, mismatch=false and validator diagnostics empty. This is the required RED, not a setup failure or validator defect.

The new test controls both initial fixture states, reads the corresponding work item, and changes only registry during each adversarial mutation. It chooses a different valid active/blocked status, proves the work-item bytes stayed identical, and asserts **persisted inequality before calling validate on the adversarial**. Detection checks the exact item-specific status diagnostic, independently of any other fixture diagnostic.

| Fixture | Registry mutation | Construction assertion | Status mismatch diagnostic |
| --- | --- | --- | --- |
| active / active | blocked | PASS: active != blocked | present |
| blocked / blocked | active | PASS: blocked != active | present |
| active / active control | none | consistent | absent |
| blocked / blocked control | none | consistent | absent |

[Active receipt](HARNESS-REGISTRY-DRIFT-001/green-active/receipt.json), [blocked receipt](HARNESS-REGISTRY-DRIFT-001/green-blocked/receipt.json), [candidate receipt](HARNESS-REGISTRY-DRIFT-001/validation/candidate/focal-observation/receipt.json). Both cases are exercised within test_07; the real first registry item's status does not determine the result. Real item states were not changed to make this test pass. Only this work item's lifecycle is now blocked because of the actual new finding.

[Ritual receipt](HARNESS-REGISTRY-DRIFT-001/ritual.json): GREEN → constant-blocked mutant imports successfully → RED on the independent construction assertion → restore byte-exact → second GREEN. One valid mutant, one killed. Source before/after, commands, logs and hashes are preserved. `validate_docs.py` is unchanged.

## Main baseline validation

Two controlled copies of base `15bd3afe1affdcb5ec49956960f884bde8c89498` received exactly the focal harness patch. Full used a separate initial target/build state. Exact pinned upstream sources were rebuilt with JDK 21 into isolated Maven repositories; the third-party seed excluded all first-party coordinates before installation.

| Check | Result |
| --- | --- |
| `mvn -B -ntp clean verify` | PASS: 270 tests; 0 failures/errors/skips |
| fast / docs | PASS / PASS |
| architecture / semantic | PASS / PASS |
| integration / performance | PASS / PASS |
| full | PASS |
| scope / diff-check | PASS / PASS |

[Main results](HARNESS-REGISTRY-DRIFT-001/validation/main/results.json), [full results](HARNESS-REGISTRY-DRIFT-001/validation/main-full/results.json), [Maven count](HARNESS-REGISTRY-DRIFT-001/maven-summary.json). Every result has the exact command, exit, duration and original log hash. Raw logs are compressed without altering their bytes. The canonical scope check's older RESOURCE_LIMIT/repin wording describes the approved base, not a new repin by this task.

## Frozen candidate and preservation

The candidate is the same uncommitted overlay on base 15bd3afe, from the original blocked `chore/cp6-baseline-sync` tree. Its [frozen snapshot](HARNESS-REGISTRY-DRIFT-001/frozen-sync-snapshot.tar.gz) preserves the exact tracked/staged patches, all untracked bytes and context. Manifest SHA-256: `c88ac70d20be2eb1707d95e3a4535d9b999f7663b7a743e9d16bf0f05ed9cb4d`, identical to the previous frozen candidate. No latest-sibling refresh occurred.

| Authority | Frozen SHA |
| --- | --- |
| analysis-ir | 51b4d9a8ae0364232bd97103cd73a77e1a34996c |
| air-java | 3bafe3978f0f392e842038ad5628e85dfd91d00d |
| cobol-lower | 18016f16b4f63149eb1bb4ca13db7e12593d8909 |
| proleap-poc | 8722945cc4cd2052c6091533f6ee6989278aa2f8 |

The same focal patch was applied to main and candidate; focal method SHA-256 is `2131970fdb4200ff948904981beb8191952608678f8e8e4547e64ccafd028679`. [Source receipt](HARNESS-REGISTRY-DRIFT-001/candidate-source.json) and [post-probe preservation](HARNESS-REGISTRY-DRIFT-001/preservation.json) prove production bytes before=after, candidate tracked diff unchanged after the harness patch, frozen untracked files unchanged and original blocked working tree unchanged.

[Candidate sequence](HARNESS-REGISTRY-DRIFT-001/validation/candidate/results.json): focal PASS → fast PASS → docs PASS → architecture FAIL → STOP. No W5 isolated/full run followed this failure.

## Evidence limits and lifecycle

All expected inventories remain unchanged. Fresh main architecture/full actual inventories matched. The prior **11 complete inventories MATCH**, JDK 21/25, ordering, corrupted classpath→regeneration and corrupted TGF→regeneration remain historical reported evidence. Their raw temporary files were lost in the environment interruption and were not recovered or fabricated; the [explicit recovery gap](previous-stop.md) remains. Surviving historical failure log/receipt and exact frozen snapshot are preserved separately.

Fresh focal checks in [JDK 21 and 25 environments](HARNESS-REGISTRY-DRIFT-001/jdk-focal/receipt.json) passed. Full main ran on JDK 21; the extensive old corruption/order campaigns were not repeated. The new candidate RED prevents any claim of all inventories matching in this task.

Functional delta: only scripts/harness/test_harness.py::test_07_registry_drift. Production Java=0; POM=0; pins=0; expected inventory=0; validator=0; architecture/classpath/TGF algorithms=0. Remaining changes are work documentation, evidence and manifest. The [hash index](HARNESS-REGISTRY-DRIFT-001/sha256.json) authenticates focal evidence; receipts also hash decompressed logs.

CP5 = APPROVED / MERGED. WORK-CFG-031 = IMPLEMENTED_HARNESS_FIX / BLOCKED_BY_NEW_FINDING / AWAITING_HUMAN_REVIEW. CP6_BASELINE_SYNC remains BLOCKED pending new review; CP6 = NOT_STARTED / NOT_AUTHORIZED. No production behavior change, inventory correction, repin, baseline sync continuation/freeze, CP6, merge or auto-merge. The next action is human review of this exact RED; no additional correction is authorized.
