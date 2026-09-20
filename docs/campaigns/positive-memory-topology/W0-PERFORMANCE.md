# W0 — current-behavior sensitivity and performance evidence

Campaign: POSITIVE_MEMORY_TOPOLOGY. **CURRENT_BEHAVIOR; no proposed semantic change implemented.**

## Reproducible scope

- Product baseline: CFG `98fa57c3db2edf9f70bb7a99bb667dbf36d28104`; AIR Java `646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa`; AIR spec `3fff18e2c16663a3f599207457caa1946d2e0945`.
- Temurin Java 21.0.12, fixed `-Xms256m -Xmx1g`. Existing `RegionalCostProbe`, `RegionalExplosionFixturesTest.fixture` and real `RegionalValuesAnalysis` with EXPERIMENTAL_PHYSICAL. Two warmups and three solves per process; metrics describe the last solve, not the sum of three.
- Synthetic N independent-intended 8-byte regions, one whole view each, P sequential literal writes to region 0, no source copies/loops/entry conditions. All production classes unchanged. Paired publication differs only by one valid DisjointStorage premise; the existing fixture test asserts that relationship.
- The separated control is a **sensitivity proxy**, not a new implementation and not a semantically equivalent AIR publication under today's rules. It removes the cross-base targets but adds premise metadata to direct targets. Full outputs between *open and separated* are intentionally different; do not compare them as byte-equivalent.
- Six original probe executions plus six neutral diagnostic repeats. Diagnostics copy only the existing test probe to an ignored directory, rename the main class and add two public-metric print statements. No production class overlay, filtering, mock solver, semantic switch or event structure change. For each configuration the original and diagnostic runs have byte-identical complete `facts.typed`, `targets.typed`, and `metrics.txt`. Thus instrumentation neutrality and repeat determinism are checked directly.
- Raw evidence: anchor `.harness-results/w0/`; local summary `.positive-memory-topology/evidence/measurement-summary.json`. Builds, caches and raw snapshots are deliberately not versioned. The stable source, commands, aggregate metrics and output hashes below are versioned.

## Preparation and solve

Pairs are current open → explicit-separation control. Events are measured with `Execution.preparationMetrics`, not inferred from elapsed time.

| Metric | 4 regions / 5 writes | 16 / 50 | 32 / 100 |
| --- | ---: | ---: | ---: |
| Prepared targets | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Prepared Events / physical plans | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Other-base comparisons | 15 → 15 | 750 → 750 | 3,100 → 3,100 |
| Correlation groups | 4 → 4 | 16 → 16 | 32 → 32 |
| Max bases/group | 1 → 1 | 1 → 1 | 1 → 1 |
| Premise memberships | 0 → 4 | 0 → 16 | 0 → 32 |
| Solve physical target applications | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Live encoded edges | 7 → 1 | 31 → 1 | 63 → 1 |
| Live expanded-equivalent edges | 19 → 1 | 766 → 1 | 3,132 → 1 |
| Live compact event rows | 15 → 0 | 750 → 0 | 3,100 → 0 |
| Historical interned compact event rows | 60 → 0 | 19,875 → 0 | 159,650 → 0 |
| Interned nodes | 39 → 6 | 1,566 → 51 | 6,332 → 101 |
| Interned encoded edges | 54 → 6 | 2,316 → 51 | 9,432 → 101 |
| Interned expanded-equivalent edges | 84 → 6 | 20,691 → 51 | 162,882 → 101 |
| Union requests/pairs | 15 → 0 | 750 → 0 | 3,100 → 0 |
| Projected selections | 20 → 5 | 800 → 50 | 3,200 → 100 |
| Concrete fallback calls | 5 → 5 | 50 → 50 | 100 → 100 |
| Expanded labels | 5 → 5 | 50 → 50 | 100 → 100 |

UNPROVEN-only target counts: **15→0, 750→0, 3,100→0**. Their event attribution is exact in this fixture: one physical prepared event per target; no other explicit writes, entry facts, logical plans or outcomes. These targets have only `UNPROVEN_BASE_SEPARATION`, MAY and sourceApplicable=false. Remaining direct events: 5, 50, 100. Direct target strength is unchanged.

Logical plans = 0; object pairs materialized = 0; partition segments = N. Separation does not reduce the preparation **base comparison** loop: it still visits other bases. Therefore the experiment measures downstream avoidance and does not measure the additional preparation gain of actually removing that loop under a new contract.

The compact cross-base unknown rows correspond to P producer-distinct unknown labels per other base: `(N−1)×P`. Unspecified entry content remains, excluded from writer rows. Current live encoded edges are `1+2(N−1)` for these nonempty producer sets; expanded equivalents `1+(N−1)(P+1)`. The separated state materializes one overwritten group; other groups still exist with implicit unknown entry content. No world-count or reachability reduction is claimed from the materialized-edge count.

`internedProvenanceRows` is the sum across interned labels/history, **not unique events or heap bytes**. For this fixture it matches `(N−1)×(P(P+1)/2+P)`: 60 / 19,875 / 159,650. This directly exposes retained prefix-set growth; it is not a claim that arbitrary programs follow the same formula.

## Observation/replay amplification

The diagnostic records cumulative metrics after the existing probe asks one BEFORE-Return storage observation per region, each in a separate batch. These numbers are **solve + replay + projection**, distinct from solve-only above. A production consumer with different batching/demand need not incur these exact counts.

| Metric after N separate observations | 4 / 5 | 16 / 50 | 32 / 100 |
| --- | ---: | ---: | ---: |
| Physical target applications | 100 → 25 | 13,600 → 850 | 105,600 → 3,300 |
| Content reads | 104 → 29 | 13,616 → 866 | 105,632 → 3,332 |
| Union requests/pairs | 75 → 0 | 12,750 → 0 | 102,300 → 0 |
| Projected selections | 119 → 29 | 14,366 → 866 | 108,732 → 3,332 |
| Concrete fallback calls | 25 → 25 | 850 → 850 | 3,300 → 3,300 |
| Expanded labels | 44 → 29 | 1,616 → 866 | 6,432 → 3,332 |

The post-observation 32/100 case makes 105,600→3,300 target applications and 108,732→3,332 projected selections. The fallback count remains 3,300; avoiding invented interference does not eliminate direct overwrite/observation work. These are structural counts, not timings or byte allocation measures.

## Remaining expensive controls and limits

The existing FAST also executed `RegionalCompositionTest`, `RegionalAlternativesTest`, `RegionalConcreteOracleTest`, `RegionalStructuralOracleTest` and `RegionalFallbackStressTest` as selected by the fixed profile. Partial-copy stress proves fallback use and complete replay/capture preservation. It has unproved fan-out too, so its totals are **not** a clean estimate of legitimate post-migration cost. No stress result is relabeled as a positive-topology execution.

Real branch joins and explicit AllMemory/MAY can still supply P legitimate events. Exact Events unions can still retain quadratic prefix history; multi-child union can still take a×b overlap comparisons; source selection and output can remain large. The measured separated witnesses have one live overwritten value and zero compact unknown rows, but **typical corpus event distributions are not measured**. Defer persistent Events until post-topology profiling demonstrates residual material cost.

Elapsed wall/thread-CPU observations are retained in raw logs, but there was concurrent local FAST/build activity and only small synthetic runs. No speedup percentage, memory/SLA, corporate gain or global complexity claim is made. No RSS/retained heap/JFR was collected in W0. Existing corporate closeout was non-convergent and lacked exact config/counters; its unresolved status is preserved.

## Commands

Run from the permanent campaign worktree. Prepare exact dependencies using the repository harness; set JAVA_HOME/PATH to Java 21. W0 bootstrapped an isolated local clone of AIR Java at the lock SHA and copied a reusable Maven cache, then ran the ordinary mandatory FAST (which validates source pin and compiles it). No snapshot dependency was substituted.

```sh
python3 -B scripts/harness/lean.py fast
python3 scripts/project/regional_cost_probe.py --regions 4 --producers 5 --warmups 2 --repeats 3 --output .harness-results/w0/n4p5-open
python3 scripts/project/regional_cost_probe.py --regions 4 --producers 5 --warmups 2 --repeats 3 --disjoint --output .harness-results/w0/n4p5-separated
python3 scripts/project/regional_cost_probe.py --regions 16 --producers 50 --warmups 2 --repeats 3 --output .harness-results/w0/n16p50-open
python3 scripts/project/regional_cost_probe.py --regions 16 --producers 50 --warmups 2 --repeats 3 --disjoint --output .harness-results/w0/n16p50-separated
python3 scripts/project/regional_cost_probe.py --regions 32 --producers 100 --warmups 2 --repeats 3 --output .harness-results/w0/n32p100-open
python3 scripts/project/regional_cost_probe.py --regions 32 --producers 100 --warmups 2 --repeats 3 --disjoint --output .harness-results/w0/n32p100-separated
```

For preparation and post-observation counters, reproduce the neutral test-main copy with this Python snippet (uses the same dependency classpath patterns as the existing runner). It fails on changed probe anchors and asserts neutrality of every repeated product:

```python
from pathlib import Path
import os, subprocess
root = Path.cwd()
out = root / '.harness-results/w0/diagnostic'
out.mkdir(parents=True, exist_ok=True)
source = root / 'analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalCostProbe.java'
text = source.read_text().replace('RegionalCostProbe', 'W0TopologyDiagnostic')
for old, new in [
    ('printCounters();', 'printCounters();\n            System.out.println("W0_PREPARATION "+new TreeMap<>(execution.preparationMetrics()));'),
    ('System.out.println("W3_TARGETS_SHA256 "+RegionalSemanticSnapshot.digest(targets));',
     'System.out.println("W3_TARGETS_SHA256 "+RegionalSemanticSnapshot.digest(targets));\n            System.out.println("W0_POST_OBSERVATION "+new TreeMap<>(execution.metrics()));')]:
    assert text.count(old) == 1
    text = text.replace(old, new)
probe = out / 'W0TopologyDiagnostic.java'
probe.write_text(text)
m2 = root / '.harness-results/build/m2'
patterns = ('io/github/gustavo2358/air-java/*/*.jar',
            'org/junit/jupiter/junit-jupiter-api/*/*.jar',
            'org/opentest4j/opentest4j/*/*.jar',
            'org/apiguardian/apiguardian-api/*/*.jar',
            'org/junit/platform/junit-platform-commons/*/*.jar')
cp = os.pathsep.join([str(root / module / 'target' / kind)
                     for module in ('analysis-values', 'analysis-kernel', 'cfg-kernel')
                     for kind in ('test-classes', 'classes')]
                    + [str(jar) for pattern in patterns for jar in m2.glob(pattern)])
subprocess.run(['javac', '--release', '21', '-cp', cp, '-d', str(out), str(probe)], check=True)
for n, p in ((4, 5), (16, 50), (32, 100)):
    for separated in (False, True):
        name = f'n{n}p{p}-' + ('separated' if separated else 'open')
        dest = out / name
        cmd = ['java', '-Xms256m', '-Xmx1g', '-cp', str(out) + os.pathsep + cp,
               'io.github.gustavo2358.analysis.values.W0TopologyDiagnostic',
               str(n), str(p), '2', '3', str(dest)]
        if separated:
            cmd.append('disjoint')
        result = subprocess.run(cmd, check=True, text=True, capture_output=True)
        (dest / 'run.log').write_text(result.stdout)
        for filename in ('facts.typed', 'targets.typed', 'metrics.txt'):
            assert (dest / filename).read_bytes() == (root / '.harness-results/w0' / name / filename).read_bytes()
```

## Stable output hashes

SHA-256 of actual typed snapshot file bytes; original and neutral diagnostic repeat match. These are different from a formatter's digest of an in-memory list if that formatter wraps the list differently.

| Case | facts.typed SHA-256 | targets.typed SHA-256 |
| --- | --- | --- |
| n4p5-open | `3bf6ed5144cc53452781cca4809aee08a6eea32727697a092159400c9c5fcbbe` | `85513f581d39e62f9dd3aead82afede6031d138c59f4f24c0fc15db8468ddab2` |
| n4p5-separated | `22d1216224174d58fb77291cf18e21ae45874c43032917c6bdc0153512072583` | `ded88aacc3a2d2559b761987f6cfe09301e50218fad1f63a6627cd14ac1417cd` |
| n16p50-open | `e9b3792a76ffbd96d302075402c341701adfb9cbcb26c634af88cf8ea0656302` | `f6a26180cba8e0090c652faa8e3619889a1e3bcdbd64775c0b5b262151c538b5` |
| n16p50-separated | `46362c3a17a37828d0049df03b9ddcd2a1cabb8f66069f46f994e7dea54c1efe` | `13c686f3519373249b72d19e8b84061b71779f5efed2628d30e4aab22bd64ac4` |
| n32p100-open | `9e8437da4f81de7deb3b717f2fe78fe0c5244943c09b7e18e594f44c491ca496` | `027c14c2ea4b40906741282c18ecc736a057078ec6b1b882f7d8434b5d613983` |
| n32p100-separated | `a8b83a27df02c7a540607931835839f3185e8e20c204eb5afd0c48c773f28c6d` | `8acf2cf46de578a5dee8cd7cb5fefe8219b521ac420fae72e6d9177d3af43d87` |

## Validation ledger

| Check | W0 status / scope |
| --- | --- |
| Local `lean.py fast` | **PASS CODE_CHANGE**, 96.773 s; 598 required unit/contract methods, zero skips; architecture/wire/harness boundaries passed |
| Six baseline sensitivity runs | **PASS**, real admitted AIR/CFG/session/solver, no semantic mutation |
| Six isolated diagnostic repeats | **PASS**, exact facts/targets/solve-metrics equality per case |
| 42 immutable source references | **PASS**, inspected file bytes equal linked Git blobs |
| Main report Q1–Q12 / T1–T10 | Discovery and oracle design, **not future semantics PASS** |
| Full source E2E / five-repo full / corporate run | **NOT RUN**; no product/pin change; synthetic causal boundary selected; limits explicit |
| Parent source/logical/file/dependency qualification | **REUSED context**, parent exact SHAs/CI, not W0 executions |

No baseline output was edited to obtain PASS. No thresholds, hidden caps, arbitrary precision loss or new solver were used. The exact permanent campaign PR/commit and remote checks are recorded in GitHub and the final handoff.
