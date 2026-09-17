# Regional explosion campaign

Current checkpoint: W1/W2 COMPLETE; W3 INCOMPLETE (structural fix deferred); W4 PENDING. PR #40 remains Draft;
merge only after W4 and final human review. The W1 sections below are historical
reproduction evidence; production optimizations are documented under W2.

# W1 — Regional explosion synthetic fixtures

W1: DISCOVERY / REPRODUCTION — NO PRODUCTION FIX.

## Checkpoint 0 — hygiene and initial hypotheses

The original `analysis-cfg` checkout was clean on `main`, SHA
`194fac2af6cfc54053318164275e682feac5c750`, with no untracked files or local
changes. That checkout predates `FactorizedAlternatives`. W1 uses a dedicated
worktree `.regional-explosion/analysis-cfg`, branch
`discovery/regional-explosion-fixtures`, starting at the available `origin/main`
SHA `2f304ad3f247181967eb62714ee9447f0a15456c`. Its initial status was clean.
The factorized implementation is identical to the EP-R2 worktree for
`RegionalValuesAnalysis` and `FactorizedAlternatives`; no existing worktree was
switched or reset. No corporate program or identifiers were read or copied.

| Hypothesis | Component | Intended witness |
| --- | --- | --- |
| No disjointness causes cross-base MAY | StatementEffects / StorageIndex | 4 regions, one write, paired premise control; 2/8 scaling |
| Producer changes label identity | ByteImage / FactorizedAlternatives | 10 unknown images, only producer changes |
| The mechanisms compose | RegionalValuesAnalysis | 4 regions, 1/5 producers, disjoint/no-disjoint |

Existing partial coverage: `RegionalValuesTest` checks scalar/region separation
and same bytes with different supports; `RegionalCompositionTest` checks
support-sensitive joins; `FactorizedAlternativesTest` checks weak choices and
finite relation algebra; `EpR2FactorizationTest` checks factorized entry growth;
`RegionalInitialTest` checks overlapping initial facts. None is the W1 paired
fan-out/provenance matrix. W1 reuses `ValuesFixtures` and the regional helpers.

Pins remain unchanged: AIR Java `135d91f4d643c80eeb5d7bff9081fae229e9e62c`,
AIR spec `3fff18e2c16663a3f599207457caa1946d2e0945` (AIR 2.0.0), frontend
`fe88cc16f664c26d27e8f975476fc2dfc3e6eff8`, lower
`9e746df89027de4aa0ec452f4e9a733d57eda866`.
Only tests, their FAST inventory and documentation evidence change; no producer, wire, solver or domain
implementation changes. Gates: focused witnesses, whole affected module with
parent dependencies, and mandatory repository FAST. Cross-repo full/corpus
qualification is not needed for this test-only delta and is not claimed.

## 1. Executive summary

**YES: A, B and C reproduce the causal mechanisms with synthetic AIR and ten or
fewer producers.** Cross-base MAY effects occur with singleton correlation
groups; changing only the writer identity prevents label canonicalization; the
two mechanisms compose into additional retained edges. No time or memory
threshold is used. No production code or instrumentation changed.

No original incident hypothesis is refuted. A large correlation group and a
pathological solver loop are **not necessary** for these witnesses. This does
not establish their absence in every input. This linear fixture does not
reproduce the incident's scale, CPU percentages, elapsed time or RSS.

These are passing characterization tests that expose the undesirable growth:
“RED” means a reliable pre-fix witness, not a deliberately failing Maven suite.

## 2. Fixture A — regional fan-out

`fanOutOccursAcrossSingletonGroupsAndOnlyDisjointnessRemovesIt` builds one
8-byte IBM1047 whole view per `Memory.Region`, one literal assignment to region
0, one sequence and Return. There are no copies, aliases, entry facts or loops.
The paired publication differs **only** by one valid `Proofs.DisjointStorage`
premise, which lists all bases. Publication equality after removing that
premise is asserted. Normal AIR/CFG/session admission is exercised.

```text
                         no disjointness
write region 0 ── MUST ──> region 0 (literal is applicable)
               ├─ MAY ──> region 1 (unknown destination)
               ├─ MAY ──> region 2 (unknown destination)
               └─ MAY ──> region 3 (unknown destination)
                         explicit disjointness: only the MUST target remains
```

| Regions N | Targets, no proof | UNPROVEN_BASE_SEPARATION | Targets, proof | UNPROVEN, proof |
| --- | --- | --- | --- | --- |
| 2 | 2 | 1 | 1 | 0 |
| 4 | 4 | 3 | 1 | 0 |
| 8 | 8 | 7 | 1 | 0 |

In both controls, `basesIndexed = correlationGroups = N`, `maxGroupBases = 1`.
`premiseMemberships = 0` without proof and `N` with proof. Target strength and
source applicability are checked individually. These are **prepared target
counts**, not counts accumulated across solver/replay invocations.

Observed and explained by the implementation: one exact destination visits each
other base, emitting a MAY target unless separation is proved. Hence `N - 1`
cross-base targets per write; W writes yield `W × (N - 1)` in this fixture.
Two regions and one write are the smallest tested witness. Four regions suffice;
32 are unnecessary. Fan-out, singleton groups, and disjointness causality PASS.

## 3. Fixture B — producer-sensitive identity

`producerAlonePreventsInterningAndUnionCanonicalization` directly uses
`ByteImage.unknown(extent=8, reason=UNPROVEN_WRITE_DESTINATION, writer=0..9)`
as labels in the production `FactorizedAlternatives<ByteImage>`. No CFG is
needed. Every property except `Part.producer` remains identical.

| Property | Observed |
| --- | --- |
| Equality of different-producer images | false |
| Distinct hashes observed (diagnostic only) | 10 |
| Same image reconstructed with the same producer | same interned node |
| Different producer | different interned node |
| Union cardinality after each producer | 1, 2, ..., 10 |
| Final labels / shapes | 10 / 1 |
| Live decision nodes / edges | 1 / 10 |
| Cumulative interned nodes / edges | 19 / 64 |

`ByteImage.equals/hashCode` includes `parts`; record equality of `Part` includes
`producer`. `RegionalValuesAnalysis.Bytes` wraps the image and preserves this
identity. The factorized interning key uses a map keyed by labels; union keeps
both distinct keys. Reconstructing an equal label and unioning it again are
idempotent controls. The ten distinct hashes observed in this execution are
diagnostic only, not a semantic requirement or a PASS/FAIL assertion. Hash
collisions between unequal images are legal. Causal reproduction depends on
equality, label identity, interning and union, not hash uniqueness.

The test-only `ImageShape` preserves extent and every `Part` field, replacing
only `producer` by a sentinel. Payload (including repeated flag), payload offset,
range, producer offset, captures, reasons, source gaps, co-initial contributors
and logical supports remain. `ByteImage` has no codec field; no codec is erased.
Negative controls distinguish reason, extent, byte payload, copy captures,
source gaps and logical support. The shape is an investigative projection,
**not** a proposal for semantic equality.

## 4. Fixture C — composition

`fanOutAndProvenanceComposeThroughRealWeakTransfersAndSolver` uses the same four
regions and one/five sequential assignments of identical text to region 0.
Different operations create different prepared producer events. Each run uses
the real `RegionalValuesAnalysis.execute()`/`DataflowSolver` and checks stable
completion. Direct engine transfer gives the same edge count as the solver's
`maxStateAlternatives`. The final own-base value retains only the last producer.

| Control | Disjoint | Producers | Targets | UNPROVEN | State edges | Decision nodes | Cross labels / shapes | Interned nodes / edges |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A | yes | 1 | 1 | 0 | 1 | 1 | 0 / 0 | 2 / 2 |
| B | no | 1 | 4 | 3 | 7 | 4 | 3 / 3 | 11 / 14 |
| C | yes | 5 | 5 | 0 | 1 | 1 | 0 / 0 | 6 / 6 |
| D | no | 5 | 20 | 15 | 19 | 4 | 15 / 3 | 39 / 84 |

Metrics have distinct scopes:

- **State edges** are materialized alternatives, not enumerated complete worlds.
  Untouched default groups are implicit, so the disjoint state materializes only
  region 0; this does not mean the other regions are unreachable or absent.
- **Cross labels** are detached `StorageValueFact.Alternative` values carrying
  `UNPROVEN_WRITE_DESTINATION`, queried at Return for regions 1–3. Each has one
  UNKNOWN_BYTES fragment with an `unknownWriter` and no payload. Entry unknowns
  are excluded from this count, but checked separately: every affected region
  retains its unspecified-entry alternative plus one alternative per producer.
- **Cross shapes** preserve the interpretation/codec, candidate, fragment
  location, kind, bytes, captures, gaps, reasons and all definition metadata.
  Only the writer OperationId and its destination OperandId owner are normalized;
  operand local ID, entry, slot, outcome, target storage, kind, unknown flag,
  origin, premises, uncertainties, reasons and logical object remain unchanged.
  Target location is preserved, so five labels per affected base collapse to one
  shape, giving three shapes rather than one across three regions.
- Interning metrics are cumulative **solve-only snapshots**, taken before query
  replay; they include historical intermediate nodes/edges. They are reported,
  not hardcoded as exact regression thresholds.

The causal chain is observed through production effects, transfer and projection:

```text
write -> 3 cross-base MAY targets -> weak union keeps old content
      -> unknown bytes with per-target event -> distinct labels -> larger state
```

For this fixture, `edges(no proof, P) = 1 + 3 × (P + 1)` for P=1 and P=5;
`edges(proof, P) = 1`. The measured interaction term is
`(19 - 7) - (1 - 1) = 12 = 3 × 4`: additional producers multiply the three
cross-base effects. This is a bilinear retained-edge witness, **not evidence of
exponential materialized growth**. Factorization keeps four live decision nodes.

`repeatingOneEventDoesNotMimicFiveDistinctProducers` applies the very same
prepared operation five times. The resulting state is equivalent to one
application and still has seven edges. Thus extra transfer visits alone do not
explain the five-producer result. No loop or timeout is needed.

## 5. Entry facts

**NOT REPRODUCED IN W1 — D SKIPPED.** The mandatory fixtures have empty entry
conditions, isolating the requested A/B/C causes. Existing
`RegionalInitialTest` covers overlapping simultaneous strong initial literals;
`EpR2FactorizationTest` covers factorized possible-entry states. Neither by itself
isolates the incident's reported initialization-node increment. Overlapping
strong literals can combine support without widening candidates; conflating
that with open/admitted entry conditions would overstate the evidence. A
separate entry-facts causal matrix is deferred, not required for this gate.

## 6. Correspondence with the incident

Incident aggregates below are user-reported context, not independently rerun.

| Incident signature | Fixture | Result |
| --- | --- | --- |
| Regions > 0 | A/C | 2/4/8 physical regions admitted |
| No DisjointStorage | A/C | zero premise memberships in the open control |
| UNPROVEN_BASE_SEPARATION | A/C | 3 per write with 4 regions; 15 for five writes; zero with proof |
| maxGroupBases = 1 | A/C | asserted with one group per base |
| labels >> shapes | B/C | 10/1 isolated; 15/3 cross-base projection |
| Composed growth | C | state edges 7 -> 19; disjoint control 1 -> 1 |
| Same producer does not create path histories | C control | five reapplications remain at 7 edges |
| Initialization increment | D | NOT REPRODUCED IN W1 |
| CPU/RSS/13-minute behavior | none | not benchmarked or claimed |

## 7. Hypotheses for W2/W3 — not implemented

**FACT:** producer identity participates in byte-image equality, label hashing,
interning and union. MAY updates retain producer-distinct unknown contents.
Disjointness removes cross-base targets in these inputs. No production fix,
metric hook, reflection, budget, pruning, widening or solver limit was introduced.

**STRONG EVIDENCE:** the incident's fan-out and label/shape signatures have a
small compositional explanation even with singleton groups. The growing gap
between live edges and cumulative interned edges identifies representation work
worth profiling, without attributing its cost from cardinality alone.

**HYPOTHESIS:** separating content identity from provenance could reduce repeated
hashing/intermediate labels; cached structural sizes or interning changes might
reduce incidental work. No CPU-cost claim is established by these fixtures.
Erasing producer identity naively risks lost dependency supports, killed/retained
definition mistakes, and broken correlations. A future design must preserve
those semantics. Deriving separation requires valid upstream allocation proof;
it must not be invented to make an analysis fast. Disjointness alone does not
canonicalize the ten labels in B and need not solve every incident cost.

## 8. Validation and final gate

Executed on the pinned AIR dependency compiled by the existing `lean_project.prepare`
wrapper (Java `--release 21`; focused/module runtime Temurin 25.0.4, final FAST
Temurin 21.0.12):

```sh
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-values -am \
  -Dtest=RegionalExplosionFixturesTest,StorageIndexTest,CfgBuildCoordinatorTest test
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-values -am test
JAVA_HOME=/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem \
  PATH=/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem/bin:$PATH \
  python3 -B scripts/harness/lean.py fast
```

Focused final run: 5 new tests + 6 StorageIndex + 12 CfgBuildCoordinator, all PASS.
Whole affected module and dependencies: 188 analysis-values + 79 analysis-kernel
+ 108 cfg-kernel = **375 tests, zero failures/errors/skips**. The eight fixture
metric rows were byte-identical between the final focused, module and Java 21
FAST executions. Final FAST: **PASS CODE_CHANGE**, 549 required unit/contract
methods, zero skips, all architecture/policy/Python checks passed (84.801 s
observed, not an assertion).
No timing limit or randomized oracle was introduced. Existing suites cover
ByteImage, factorized relations, regional transfers/initial facts and solver laws.

The five new methods are registered in `docs/evals/cp6/fast-test-inventory.json`
so the fixed FAST selector executes them in subsequent CI runs. Full/corpus and
cross-repo E2E were NOT RUN: no production semantics, pins or contracts changed.
No historical full qualification is relabeled as W1 execution.

Early setup failures are preserved, not counted as semantic RED: a reactor-wide
single-class selector matched no upstream tests; the existing shared AIR cache
was on another pin and was replaced with an isolated exact-pin build; the first
A test used `bases` instead of the actual `basesIndexed` metric. These were
corrected without changing production or weakening an assertion. An initial
FAST invocation with the ambient Java 25 passed its selected tests but failed
`W3 compiled inventory drift`. Re-running with Java 21, the CI toolchain, passed
the unchanged compiled inventory and the complete FAST gate. The failed log is
preserved; no architecture baseline was regenerated.

Raw logs remain local in `.harness-results/w1/`; source pins above are unchanged. The reproduction gate is
separate from repository lifecycle DONE, which still requires review and merge.

Post-review hash assertion adjustment (base `dfa4893ec44ecc73257d042b94b72c29af684b87`):
removed only the hash-cardinality regression assertion; all semantic assertions,
shape helpers and negative controls are unchanged. Newly executed with Java 21:
focused fixture plus parent smoke **23 PASS**; full analysis-values and parent
modules **375 PASS**; repository FAST **PASS CODE_CHANGE**, 549 mandatory methods,
zero skips (85.199 s observed). The same commands above were used with Java 21
for all three runs. All eight structural A/B/C rows match the pre-adjustment W1
run, excluding diagnostic `distinctHashes` from the comparison. A/B/C remain
PASS and Entry remains SKIPPED. No production code changed. Raw logs and the
comparison are preserved locally in `.harness-results/w1-review/`.

**Temos RED suficiente para iniciar a campanha de correção? SIM.** A and B
isolate both causes; C demonstrates their positive interaction, with controls
for disjointness and repeated event identity. Tests are deterministic structural
characterizations and contain no timing assertions. Entry initialization and
large-case performance remain explicit limitations.

**W1 COMPLETE — SYNTHETIC REPRODUCTION ACHIEVED.** This completes only the W1
checkpoint, not the campaign or merge readiness. W1/W2/W3/W4 stay on branch
`discovery/regional-explosion-fixtures` and PR #40, which remains Draft during
the campaign. Merge may occur only after W4 and final human review. Stop for
human review; this adjustment does not start W2. No merge or auto-merge is
authorized.

# W2 — accidental-cost reduction

## Hygiene and measurement baseline

W2 starts at `c23a44691004d244a2632c8f779ca7031b49a99a` on the same branch and
Draft PR #40, with a clean working tree. W1 commits `107bbf4`, `dfa4893` and
`c23a446` are preserved. W2 is IN PROGRESS; W3/W4 are pending. No merge.

Before any optimization, `RegionalCostProbe` and
`scripts/project/regional_cost_probe.py` were added as a manual diagnostic.
They reuse W1's synthetic AIR, real preparation, `RegionalValuesAnalysis`,
`FactorizedAlternatives` and the real solver. Preparation is outside the measured
solve loop; complete typed observations are emitted afterwards to `facts.txt`.
The observation file retains candidates, supports, producer definitions, ranges,
codecs, gaps, premises and reasons, enabling exact before/after reconciliation.
No benchmark duration is a CI assertion. No confidential input is used.

Java 21, fixed 256 MiB initial / 1 GiB maximum heap, ten warmups and 100 measured
solves are used for the selected JFR run. Exploratory cases 4 regions/10 writes,
8/25 and 16/50 took 36/77/171 ms for three solves respectively (different warmup
counts; selection evidence only). The 8/25 JFR probe yielded only 47 regional
samples; 16/50 yielded 500 and is selected for comparison. Recording uses 2 ms
execution sampling and 256-frame stacks; `jfr print` must also set
`--stack-depth 256` (its default truncates to five frames).

Baseline structural metrics for 16 regions/50 producers, one solve:

| Metric | Baseline |
| --- | --- |
| Targets / unproven targets | 800 / 750 |
| Maximum materialized alternatives / decision nodes | 766 / 16 |
| Maximum component cardinality | 51 |
| Interned nodes / edges | 1,566 / 20,691 |
| Relation union pairs / projected alternatives | 750 / 800 |

JFR baseline (`baseline-profile/solve.jfr`): 500 regional execution samples.
Exclusive classification prioritizes size/track, then hashing, then interning,
then union, other representation, other transfer, solver. It estimates sampled
CPU distribution, not exact CPU accounting: hashing 239 (47.8%), interning
without a hashing frame 168 (33.6%), size/track 10 (2.0%), union 18 (3.6%), other
representation 51 (10.2%), other transfer 13 (2.6%), solver 1 (0.2%). Inclusive
stacks overlap: node 320, hashing 240, union 174. Thus hashing/interning is
reproduced as dominant; the incident's much larger track share is NOT reproduced.
Allocation sampling shows object arrays, KeyValueHolder, HashMap nodes/tables,
iterators and streams; sampled weights are estimates, not allocation counts.

A separate diagnostic overlay compiles instrumented **copies** of three sources
into an ignored output directory, placed first on the probe classpath. It never
changes production sources or API and must not be used for timing comparisons.
One measured solve: 2,366 node calls / Keys; 40,750 union calls, 750 nontrivial
calls and 750 unique ordered identity pairs; 51 track calls; 52 size traversals,
800 visited DAG nodes / 19,925 visited edges; 255,792 ByteImage hash calls.
Instrumented track/size durations are retained in raw logs, not used as speedup
claims. This proves repeated hashing and full-state metric work; a persistent
union cache has zero cross-call nontrivial pair reuse in this baseline.

All baseline records were obtained before production changes. Raw recordings,
logs, instrumented copies and observations stay in `.harness-results/w2/`.

## W2.1 — exact per-component size summaries

**FACT:** `track` only updates `maxStateAlternatives`, `maxDecisionNodes` and
`maxComponentCardinality`. These counters are exported as diagnostics; no
transfer, join, equivalence, worklist decision or precision rule reads them.
Previously each call gathered all state roots and traversed their DAG, hashing
labels into per-level sets. The state accessors still provide the original full
traversal as a reference.

The implementation caches exact size summaries per queried immutable group root,
inside the existing analysis-local interner. Singleton-level groups use edge
cardinality directly; multi-level roots retain the original shared-suffix-aware
traversal once per root. `track` sums nodes/edges and takes maximum label width
across correlation groups: this is valid because groups partition segment
levels. It does not sum overlapping DAG subtrees, retain state histories, or
approximate the maxima. The cache lives no longer than the interner that already
retains these roots, at the cost of an extra entry/Size per queried root.
Worst-case new multi-level roots still require traversal; no universal O(1)
claim is made. Singleton leaf detection inspects child references once per root,
but does not hash labels or build visited/label sets.

Selected-scenario before/after (one measured solve, temporary counters):

| Work | Baseline | W2.1 |
| --- | --- | --- |
| track calls | 51 | 51 |
| full DAG size traversals | 52 | 1 (empty entry boundary) |
| DAG node / edge visits in size | 800 / 19,925 | 0 / 0 |
| ByteImage hash calls | 255,792 | 235,867 |
| node calls / Keys | 2,366 / 2,366 | 2,366 / 2,366 |
| nontrivial union calls / distinct pairs | 750 / 750 | 750 / 750 |

**STRONG EVIDENCE:** JFR's track/size stack samples go from 10/500 to 0/507;
total profiled solve wall for 100 repeats changes 2.405 s -> 2.182 s, thread CPU
2.352 s -> 2.135 s. Timing is diagnostic, not a guarantee. Hashing/interning
remains dominant (235 hashing samples, 186 further interning samples).

**FACT:** `facts.txt` and the complete solve-metrics map are byte-identical to
baseline. The facts SHA-256 is
`ddcee1f4599b5e8cfe24c41f1dbbd08752ad50ebe79ae24def0a629d6d03ed5f`.
W1 A/B/C rows match, excluding the diagnostic hash-count field. New tests compare
cached summaries with the original traversal over 1,000 finite relations,
shared-suffix diamonds and retained old roots, and compare tracked maxima with
full-state traversal for singleton and copy-connected groups.

W2.1 validation (Java 21): focused W1/factorized plus parent smoke PASS; whole
analysis-values and parents PASS (190 + 79 + 108 tests); full FAST PASS with
551 required methods, zero skips, including wire/consumer and architectural
checks. No compiled architecture inventory was changed or regenerated.

## W2.2 — cache the unchanged immutable ByteImage hash

**FACT:** `ByteImage` is immutable: extent is Optional/BigInteger, normalized
parts are immutable records in an immutable list, Part collections are copied,
and `Values.BytesValue` defensively copies its octets. Every transformation
constructs a new image or returns an equal existing image. The constructor now
computes the **same** `Objects.hash(extent, parts)` once into a final int;
`hashCode()` returns that int. `equals` is unchanged. There is no sentinel hash,
lazy race, global cache or substitution of equality by hashes.

Tests preserve the pre-W2 structural hash across image transformations and
attempted input mutation. A deliberately colliding pair (reasons `Aa` and `BB`)
proves unequal images still produce distinct interned labels/nodes, union keeps
both and equal reconstructed labels remain canonical. W1 still has ten
producer-distinct labels and one shape; no uniqueness assertion was reintroduced.

| Work per selected solve | Baseline | W2.1 | W2.2 |
| --- | --- | --- | --- |
| ByteImage hashCode calls | 255,792 | 235,867 | 235,867 |
| Structural ByteImage hash computations | 255,792 | 235,867 | 1,616 |
| Full size traversal edge visits | 19,925 | 0 | 0 |
| Interned nodes / edges | 1,566 / 20,691 | same | same |
| Targets / unproven | 800 / 750 | same | same |
| State alternatives / nodes | 766 / 16 | same | same |

The cache removes over 99% of structural hash computations in this scenario.
It adds one int per ByteImage and computes hashes eagerly, including images
that might never be hashed; this is a measured tradeoff, not a universal gain
claim. JFR for 100 solves: wall 2.405 -> 2.182 -> 1.458 s; thread CPU
2.352 -> 2.135 -> 1.422 s (baseline -> W2.1 -> W2.2). These are individual
diagnostic recordings, not CI thresholds. Exclusive hashing samples drop
239/500 -> 235/507 -> 37/238; size/track samples 10 -> 0 -> 1. The final
profile is dominated by interning/map construction and equality checks
(135/238 further interning samples), not by recomputing ByteImage structure.
Complete `facts.txt` and solve metrics remain byte-identical to baseline.

## Union / interning audit and rejected changes

**FACT:** before and after both changes the selected solve makes 40,750 union
calls, of which 750 are nontrivial; all 750 ordered identity pairs are distinct.
Trivial null/same-node cases already return before memoization. A persistent
union cache is **not implemented**: measured cross-call nontrivial reuse is zero,
so it would add a lookup and pair retention without a demonstrated hit.
An unordered pair could exploit commutativity in another workload; it is not
justified by this one. Node identity is canonical only within one interner,
so any such cache would have to remain analysis-local. Loop workloads might
behave differently; no corporate conclusion follows from this probe.

**FACT:** `Key(level, edges)` still hashes its map during lookup/insertion.
`node()` still creates a live HashMap and immutable key map before knowing if a
node is already interned (2,366 calls/Keys in the selected solve). The Node then
receives an already-immutable map; on this JDK its `Map.copyOf` need not allocate
another map. Nodes remain immutable and identity-canonical within each interner.
A cached Key hash or alternate interning representation remains a possible
accidental-cost improvement, but was **not implemented** in this wave: after
two isolated gains, the remaining profile is mostly map construction, probes
and equality over retained edges. This evidence does not prove all remaining
accidental work has been eliminated. No speculative third cache is added.

Deferred metrics retaining every historical state were rejected: exact maxima
must include transient states and retaining them would create extra memory
pressure. Summing arbitrary DAG child sizes was rejected because shared suffixes
would be double-counted. Provenance factoring, disjointness inference, precision
changes and limits are outside W2 and were not attempted.

## Repeated runtime comparison and reproducibility

**STRONG EVIDENCE:** three alternating, uninstrumented fresh-JVM trials,
16 regions/50 writes, ten warmups/100 solves, no concurrent builds:

| Trial | Baseline wall (s) | Final wall (s) |
| --- | --- | --- |
| 1 | 2.266 | 1.352 |
| 2 | 2.353 | 1.441 |
| 3 | 2.424 | 1.366 |
| Median | 2.353 | 1.366 |

Median wall decreased **42.0%** (~1.72× throughput for this workload). Median
thread CPU decreased 2.322 -> 1.341 s. This is a diagnostic microbenchmark on
this host, not a CI clock contract or corporate-speedup prediction.
A separate 32-region/100-write check (three warmups/five solves) changed
1.029 -> 0.668 s; all 3,200 targets, 3,100 unproven targets, 3,132 live edges,
6,332 interned nodes and 162,882 interned edges remained identical. Both scales
produce byte-identical complete facts and solve metrics before/after.
Peak RSS was not measured; JFR sampled allocation weights must not be interpreted
as RSS or exact allocated bytes. Per-image cached ints and per-root summaries
are added memory costs; a retained-heap analysis of large workloads remains open.

Reproduce from the campaign checkout with Java 21 and its unchanged pinned AIR:

```sh
export JAVA_HOME=/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem
export PATH="$JAVA_HOME/bin:$PATH"
# Existing pin/bootstrap wrapper; installs into the ignored local build cache.
python3 -B -c 'import sys; from pathlib import Path; sys.path.insert(0,"scripts/harness"); from lean_project import prepare; prepare(Path.cwd())'
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-values -am \
  -Dtest=RegionalExplosionFixturesTest,ByteImageTest,FactorizedAlternativesTest,RegionalCostMetricsTest,StorageIndexTest,CfgBuildCoordinatorTest test
python3 -B scripts/project/regional_cost_probe.py --regions 16 --producers 50 \
  --warmups 10 --repeats 100 --jfr --source-ref c23a446 \
  --output .harness-results/w2/reproduce-before
python3 -B scripts/project/regional_cost_probe.py --regions 16 --producers 50 \
  --warmups 10 --repeats 100 --jfr --output .harness-results/w2/reproduce-after
cmp .harness-results/w2/reproduce-before/facts.txt .harness-results/w2/reproduce-after/facts.txt
cmp .harness-results/w2/reproduce-before/metrics.txt .harness-results/w2/reproduce-after/metrics.txt
```

For W2.1 alone use `--source-ref 8212584`; for deterministic work counters use
`--instrument --warmups 2 --repeats 1` in a separate output directory. For plain
runtime trials omit `--jfr` and `--instrument`. `--source-ref` compiles only the
three changed production classes from Git into a temporary classpath overlay;
it does not switch/reset the branch or change source files. All other production
classes and pins are unchanged between these revisions. Overlay source copies
are retained alongside output. The script fails if an instrumentation anchor is
missing or ambiguous; it never silently omits a requested counter.

## Semantic reconciliation and validation

**FACT — newly executed**, Java 21 after each production change:

| Check | W2.1 | W2.2 |
| --- | --- | --- |
| Focal W1 and directly affected tests + parent smoke | PASS | 38 PASS |
| Entire analysis-values + analysis-kernel + cfg-kernel | 377 PASS | 379 PASS |
| Complete repository FAST | 551 methods, PASS | 553 methods, PASS |
| Architecture inventories | unchanged, PASS | unchanged, PASS |
| Full typed observations / solve metrics vs baseline | identical | identical |

FAST includes the existing regional wire/consumer regression checks; no wire
format, contract, pin or serializer changed. Baseline tests ran before W2.1;
W1's eight structural diagnostic rows match all six post-change focal/module/FAST
runs. Hash cardinality remains diagnostic and was excluded from this comparison.
Full producer/corpus/E2E campaign qualification is NOT RUN in W2: the changes
are immutable-representation/observability optimizations with bounded regression
coverage; W4 retains responsibility for E2E and real-case validation. Historical
qualification is not relabeled as newly executed.

| W1 invariant | Before W2 | After W2 |
| --- | --- | --- |
| A, 4 bases, no proof: targets / unproven | 4 / 3 | 4 / 3 |
| A with proof: targets / unproven | 1 / 0 | 1 / 0 |
| A maximum group size | 1 | 1 |
| B labels / shapes | 10 / 1 | 10 / 1 |
| Same producer re-interning / union reinsertion | idempotent | idempotent |
| C no-disjoint, one producer | 7 | 7 |
| C no-disjoint, five producers | 19 | 19 |
| C disjoint, one/five producers | 1 / 1 | 1 / 1 |
| C repeat same event five times | 7 | 7 |
| C cross-base labels / shapes, five producers | 15 / 3 | 15 / 3 |
| Relevant producers, supports and observation results | retained | exact match |
| Entry reproduction | SKIPPED | SKIPPED |

## W2 gate and remaining uncertainty

1. **FACT — YES:** relevant incidental work was removed: repeated structural
   hashing and complete metric traversals, with measured workload/profile gains.
2. **FACT — YES:** W1 A/B/C and both larger-scale observation snapshots remain
   semantically identical; equality/targets/strength/precision rules are unchanged.
3. **FACT — NO:** no provenance was removed.
4. **FACT — NO:** no target was removed.
5. **FACT — NO:** no precision was reduced and no artificial limit was introduced.
6. **STRONG EVIDENCE / HYPOTHESIS:** the next dominant sampled work is interning
   maps and comparing retained labels. Those costs grow with the real alternative
   inventory, but these experiments **do not prove the remainder is exclusively
   structural**. Key hashing/map construction may contain further incidental
   overhead. Provenance-distinct labels and cross-base fan-out remain the W3
   structural questions. W2 stops after two measured, semantics-neutral changes;
   it does not claim the corporate incident is solved or all optimization is done.

**W2 COMPLETE — INCIDENTAL COST REDUCED.** Ready for human review of W2 and the
next-wave plan, not for merge. W1/W2 remain in PR #40, Draft, with no auto-merge.
W3/W4 are pending and are not started by this gate. No merge authorized.

# W3 — structural explosion reduction

**W3 INCOMPLETE — STRUCTURAL FIX DEFERRED.** Discovery/oracle checkpoint only;
no structural reduction is claimed. Selected outcome **N**, permitted by the
campaign's design/stop gate. W1/W2 remain complete; W4 remains pending. This is
not evidence that exact factoring is impossible, nor authorization to start W4.

## Hygiene and scope

**FACT:** initial SHA `80104d65c79381dd58085ebacc2ec43b5b579aec`, clean worktree
`.regional-explosion/analysis-cfg`, branch `discovery/regional-explosion-fixtures`.
PR #40 was OPEN, Draft, with no auto-merge. Its description was updated to W3
IN PROGRESS. No prior commit was rewritten. This checkpoint changes only tests,
the manual probe, its required-test inventory and this report. No production,
public API/wire, dependency pin or architecture inventory changes.

## Residual baseline after W2

**FACT:** the real W1 publication builder, `RegionalValuesAnalysis`,
`FactorizedAlternatives` and solver were reused. The probe compiled the three
measured production classes from the exact W2 SHA into an isolated classpath
overlay; no checkout/reset was used. Java 21, `-Xms256m -Xmx1g`, ten warmups and
100 measured solves per fork, 2 ms JFR execution samples. Observation/snapshot
encoding occurs after the solve timer and after JFR stops.

| Metric (one solve) | 16 regions / 50 writes | 32 regions / 100 writes |
| --- | ---: | ---: |
| Prepared targets | 800 | 3,200 |
| UNPROVEN_BASE_SEPARATION targets | 750 | 3,100 |
| Live edges (not complete worlds) | 766 | 3,132 |
| Decision nodes | 16 | 32 |
| Interned nodes | 1,566 | 6,332 |
| Cumulative interned edges | 20,691 | 162,882 |
| Max component cardinality | 51 | 101 |
| Union pairs | 750 | 3,100 |
| Projected alternatives | 800 | 3,200 |
| Alternative visits / content reads / updates | 800 each | 3,200 each |

**FACT:** residual JFR baseline has 241 regional execution samples at 16/50:
129 interning, 46 hashing, 37 other representation, 13 union, 2 metrics,
11 transfer, 3 solver/other. At 32/100 it has 2,416: 1,403 interning,
384 hashing, 455 other representation, 114 union, 12 metrics, 46 transfer,
2 solver/other. These are exclusive stack categories from the W2 classifier,
not exact CPU attribution; inclusive categories overlap. Interning is about
54%/58% and hashing 19%/16% of regional samples respectively.

**STRONG EVIDENCE:** map construction/comparison remains significant as scale
increases. The 32/100 leaf samples include `MapN.probe` (315), `HashMap.putVal`
(257), `Objects.equals` (239) and `HashMap.resize` (175). This does not prove
that all remaining cost is unavoidable semantic structure: copying cumulative
maps also contributes. The target list itself is not shown to dominate CPU.

## Semantic oracle frozen before a fix

`RegionalSemanticSnapshot` walks typed record components recursively, including
component names and types. It has explicit encodings for null, Optional, lists,
sets, maps and scalar leaves; tokens are length-prefixed. It never uses record
`toString()` or domain `hashCode()`. Unsupported types fail closed. Only Set/Map
iteration order is normalized; lists keep their existing order. Thus comparisons
are stricter than unordered semantic equivalence and cannot silently erase range,
alternative, capture or producer associations. `ByteImage` is expanded to extent
and every complete Part. SHA-256 below identifies the complete encoded content,
not a Java hash-uniqueness requirement.

The new mandatory `RegionalStructuralOracleTest` freezes B's ten complete images
and C's four complete observed region facts for each disjoint/producer control.
It also compares independent executions using typed `List<StorageValueFact>`
equality. The field-retention negative test individually changes all eleven Part
fields, checks collection-order policy and rejects unsupported classes.
The existing W1 shape negative controls remain unchanged.

| Frozen small oracle | SHA-256 |
| --- | --- |
| B, all 10 producers | `e98852005f7f4d49d1246c3d1c1ae860acab3b001d17c197067342a42132577e` |
| C, 1 producer, no disjointness | `083a6aa0ff2826958698b32b42ffb6fafacad171b989d339f2e1d408c471e206` |
| C, 5 producers, no disjointness | `3bf6ed5144cc53452781cca4809aee08a6eea32727697a092159400c9c5fcbbe` |
| C, 1 producer, disjoint | `59eae5b3d2f41a5180c411c47ddc689ab4457517c361b79577d0f3e5ff8ea6e1` |
| C, 5 producers, disjoint | `22d1216224174d58fb77291cf18e21ae45874c43032917c6bdc0153512072583` |

The large probe additionally emits `facts.typed` and `targets.typed`;
`--compare BASELINE_DIRECTORY` compares the **entire files**, not just their
hashes. The old `facts.txt` remains a W2 diagnostic. Observation snapshots include
all StorageValueFact fields: candidates, alternatives, unknownWriter, candidate
supports, fragments/ranges/bytes, reasons, all remainder flags, captures, gaps,
logical captures/alternatives, premises, evidence, origins and query identity.
Target snapshots include location, strength, sourceApplicable, premises and
reasons. These files are local diagnostic artifacts, reproducible from Git.

| Frozen large oracle | Full typed facts SHA-256 | Full targets SHA-256 |
| --- | --- | --- |
| 16/50 | `e9b3792a76ffbd96d302075402c341701adfb9cbcb26c634af88cf8ea0656302` | `f6a26180cba8e0090c652faa8e3619889a1e3bcdbd64775c0b5b262151c538b5` |
| 32/100 | `9e8437da4f81de7deb3b717f2fe78fe0c5244943c09b7e18e594f44c491ca496` | `027c14c2ea4b40906741282c18ecc736a057078ec6b1b882f7d8434b5d613983` |

**Limit:** these straight-line scenarios do not exercise every nonempty capture,
logical support or joined multi-Part combination. Field completeness is not a
proof of transfer correctness on those inputs. Existing ByteImage,
StorageValueObservation, RegionalInitial, RegionalProvenance and regional copy/
composition tests cover those families; a future production design must add its
own differential cases. Do not regenerate these snapshots to accept missing
information. If internal representation changes, expand it to the same observable
records; B's internal-image adapter may need explicit replacement, not weakening.

## Provenance discovery

**FACT:** `ByteImage.Part.producer` is an analysis-local event ordinal, not merely
a globally interchangeable operation name. `RegionalValuesAnalysis.compile`
creates a `PreparedEvent` for each write/target (and logical target). Its public
`DefinitionEvent` also carries entry, destination/slot, outcome, storage, kind,
origin, premises, uncertainties, reasons and optional logical object.

`Bytes` embeds the entire image in a Content label. Image equality and cached
hash include every Part field. DAG interning therefore distinguishes these
labels. `restrict` compares full labels in the BEFORE relation; `selections`
preserves their correlation with child relations. Read/copy transfer first
projects source levels, restricts the original relation to that selection, then
updates destinations. Removing identity without modifying those operations is
not a transparent representation change.

**FACT:** producer identity does not grant kill authority. `KillAuthority`
checks execution, occurrence/selection, destination completeness and positive
source-applicability/exhaustiveness. However, producer identity affects support
construction and observation grouping. `project` collects contributors and
logical supports; `trace` uses the event's destination plus producerOffset to
reconstruct the original contributed range. Copy capture offsets contain the
source-candidate alternative index, not just an offset. `fragment` reconstructs
unknownWriter, copy DefinitionEvents, before points, source/destination ranges
and logical capture support. `RegionalResultJson` emits these detached fields.
RD independently consumes the same concrete targets; it does not consume
ByteImage, but target aggregation would affect its plan construction.

| Information | Can factor? | Required granularity | Risk |
| --- | --- | --- | --- |
| Extent, payload, reasons | Identical immutable values may share storage | Exact image/Part shape | Equating unknown reasons, repeated payload or extent changes meaning |
| Producer + producerOffset | Only with exact association retained | Event × contributed Part/range × alternative | A global producer set loses which writer supplied which bytes |
| Multiple Parts | Not as independent sets in general | Joint row/decision relation across ranges | Inventing combinations absent from the source relation |
| capturedOffsets | Share only equal mappings or correlated rows | Copy event × source alternative × offset × range | Mixing captured source choices or before-store alternatives |
| coInitial | Existing special factorization only | Simultaneous contributors to the same initial image/range | Treating disjunctive writers as simultaneous evidence |
| sourceGaps | Keep affected range and alternative association | Gap origin/uncertainty × affected range | Moving a gap contaminates previously known bytes |
| logicalSupports | Preserve object/event and capture context | Logical object × support event × alternative | Assigning support to a different candidate or capture |
| DAG continuation | Equal shape is insufficient | Exact child relation or explicit support-to-child relation | Cartesian-product precision loss at a later read/copy |
| DefinitionEvent/wire | Expand exact records at observation | Full event identity, ranges and correlations | Fewer detached alternatives can be an observable contract change |

**FACT (executable counterexamples):** two 8-byte images containing identical
bytes and the same producer set `{1,2}` differ when producers swap their 4-byte
ranges. A two-level relation with only `(A,B)` and `(B,A)` has two selections;
independent `{A,B}` sets admit four, adding `(A,A)` and `(B,B)`. The new tests
check both using real ByteImage writes and FactorizedAlternatives operations.
They refute *uncorrelated sets*, not all possible exact factoring designs.

## Fan-out discovery

**FACT:** `StatementEffects.targets` enumerates every other base and consults
`StorageIndex.disjoint`. Unproved separation produces a whole-base MAY target
with `sourceApplicable=false`; direct target proof premises and strength are
kept separately. Scopes/remainders already describe unknown coverage, but there
is no prepared Target variant for “all except this destination and positively
separated bases”. `AllMemory` alone would lose this exclusion/proof distinction.

Values compilation immediately creates per-target events/plans; RD compilation
immediately intersects each target with StoragePartition segments. Regional
transfer groups plans by correlation group. A lazy collection expanded at these
same points would defer allocation, then produce the same state and event count.
It would not solve label multiplication. It is unnecessary to materialize every
base at the *IR scope* level, but the current prepared/domain APIs require it.

**HYPOTHESIS:** an analysis-local default effect plus per-base exceptions could
represent fan-out symbolically until a query/update intersects a base. This needs
exact handling of partial disjoint proofs, selected destinations, unequal extents,
MAY_SET versus SINGLE_DESTINATION, per-outcome evidence and source gaps. Reads
would need lookup/intersection plus event reconstruction; connected read/copy
groups still need joint correlation. Neither cheaper total projection cost nor
smaller retained state has been demonstrated.

A shared Target redesign affects StatementEffects, StoragePartition consumers,
RD and Values. StorageIndex can remain the proof authority but needs a query path
usable by the symbolic consumer. This does **not inherently require an AIR/wire
change** if all exact records are expanded at the boundary. Publishing an aggregate
in place of current concrete records *would* cross that boundary and must stop
for compatibility review. No such change was made.

## Candidate designs

| Candidate | Expected structural effect | Contracts and risks | Effort / required tests | Decision |
| --- | --- | --- | --- | --- |
| P: shape + flat producer set | B may appear 10→1 | Loses range and joint-alternative associations; precision risk even with same bytes | Small implementation, but counterexamples fail | Rejected |
| P: exact relational provenance, grouped only with compatible continuation | May share shape once while retaining 10 support rows; benefit for repeated unknown shapes | Internal label algebra changes; must keep source selections and support-to-child relation. Public wire could stay unchanged by expansion | Substantial: union/update/project/restrict laws, multi-Part join/copy/capture/initial/logical/gap differential oracles, scale/memory | Deferred, not disproved |
| P: only single-Part unknown leaf labels | Could compress the W1 hot case | Smaller initial domain, but copies/partial overwrites can turn these into multi-Part/connected values; needs exact expansion and stable canonical joins at those transitions | Prototype plus transition laws and retained-memory comparison required; no benefit measured yet | Deferred; plausible follow-up, not declared unsafe |
| F: lazy Target enumeration | Fewer early Target objects at most | Existing consumers immediately expand; no demonstrated domain reduction | Moderate, shared-consumer regression required | Not selected as W3 structural fix |
| F: symbolic default effect with exceptions | Potentially reduces base × write state | Changes prepared/domain interfaces; must preserve all exclusions, proofs, evidence and group correlations | Large, RD/Values differential tests and API review; wire can stay concrete | Deferred |
| H: provenance + symbolic fan-out | Potentially largest | Combines both unproved transfer changes, widest diagnosis surface | Largest; all P/F gates | Rejected for this checkpoint |
| N: freeze oracles and defer production | None | No new semantic or public contract risk | Discovery + regression validation | Selected |

## Selected design / gate decision

**SELECTED DESIGN:** N — `STRUCTURAL FIX DEFERRED`. The small flat-set designs
have concrete counterexamples. Exact relational P remains promising but has not
been established as a small, correct change across transfer and observation.
The smaller lazy F candidate does not remove the internal multiplication, and
symbolic F broadens the shared domain interfaces. This is a bounded discovery
result, not a claim that the campaign can never implement either design.

**REJECTED ALTERNATIVES:** uncorrelated image/range producer sets, reusing
coInitial to mean disjunction, shape-only equality, lazy targets presented as a
domain reduction, simultaneous P+F implementation. No pruning, limits, new
DisjointStorage proof, precision loss or scalar fallback was considered acceptable.

**SEMANTIC INVARIANTS:** exact targets and strength; complete producer/range/
capture/gap/logical-support associations; candidate supports; unchanged detached
DefinitionEvents, reasons, premises and observations; exact relational choices.

**EXPECTED STRUCTURAL EFFECT:** zero in this checkpoint. No production fix or
structural RED was written because no implementable design passed this gate.
The new negative tests protect against the rejected transformations; they are
not mislabeled as a successful structural RED/GREEN. Historical W1 internal
counts are retained, but they are not requirements for a future accepted W3 fix.

## Implementation and structural before/after

Only a complete typed snapshot encoder, frozen/adversarial tests, manual probe
comparison/RSS options and the explicit FAST test inventory were added. No new
instrumentation is installed in production; no global cache or semantic budget.
The W2 cached int and component-size cache remain untouched.

**FACT:** initial-W2 overlay and final checkpoint produce identical complete
`facts.typed`, `targets.typed`, legacy `facts.txt` and all solve metrics at both
scales. Every metric in the baseline table is unchanged; structural reduction
is **zero**. This equivalence validates the oracle/checkpoint, not a fix.

## Semantic reconciliation

| W1/W2 property | Before W3 | Checkpoint after discovery |
| --- | --- | --- |
| A no-proof targets, 2/4/8 bases | 2/4/8; unproven 1/3/7 | Identical |
| A with DisjointStorage | 1 target; 0 unproven | Identical |
| B labels / shape / recovered producers | 10 / 1 / 10 | Identical |
| C no-proof, 1/5 producers | 7 / 19 live edges | Identical |
| C with proof, 1/5 producers | 1 / 1 live edge | Identical |
| Repeat same event five times | 7 live edges | Identical |
| C complete observed facts, all four controls | Frozen typed field snapshots | Identical |
| 16/50 facts and targets | Complete typed snapshots above | Byte-for-byte equal |
| 32/100 facts and targets | Complete typed snapshots above | Byte-for-byte equal |
| Optional Entry incident reproduction | SKIPPED | SKIPPED |

## Memory evidence

`--rss` uses GNU time on the Java child process only (source-overlay compilation
excluded). It includes JVM, warmups, solve, JFR and final observation/snapshot
encoding. It is **peak RSS, not retained heap**, and uses a 256 MiB initial heap.
Do not infer corporate memory needs or the size of the W2 caches from this number.
JFR allocation sample weights measure allocation traffic, not retained objects.
The 32/100 baseline's largest regional sample weights are KeyValueHolder about
3.20 GB, HashMap.Node 2.17 GB and Object[] 1.54 GB across 100 solves. These can
exceed peak RSS because objects are reclaimed between/during solves.

Runtime/RSS results and final validation are recorded below. No memory-reduction
claim is made, since the production implementation is identical.

## Remaining risks and next decision

**STRONG EVIDENCE:** repeated maps and label comparisons are still expensive;
independent producer sets are an invalid replacement for the current relation.
**HYPOTHESIS:** grouping equal-shape labels only under an exactly shared child,
with complete correlated support rows and lossless transfer expansion, can reduce
retained representation. Neither its gain nor correctness is certified here.
A follow-up W3 design should prototype that bounded P candidate and challenge
partial overwrites, copies into connected groups, joins/loops, co-initial values,
logical capture and range-specific gaps before considering production acceptance.

There is no expected improvement to the corporate incident from this checkpoint.
W4 must eventually validate real E2E termination, memory, candidates/evidence,
CALL/files/tables and all external outputs under the unchanged pinned pipeline.
Synthetic timings alone cannot authorize that conclusion. W4 was not started.

Final gate: structure reduced **NO**; provenance lost **NO**; targets lost **NO**;
precision reduced **NO**; observable facts equivalent **YES for the frozen
scenarios**; residual cost acceptable for corporate E2E **NOT ESTABLISHED**.

W3 INCOMPLETE — STRUCTURAL FIX DEFERRED.
W1/W2 remain in PR #40. W3 requires a reviewed follow-up design; W4 pending.
No merge authorized.

## Runtime evidence and reproduction

Diagnostic only, 100 solves per row; both columns run the **same production
implementation**. Different timing/sample/RSS values are run variation, not a W3
gain. Selected comparison runs were executed without a concurrent test suite.
Earlier exploratory `checkpoint-*` runs overlapped module validation and are not
used for timing conclusions.

| Measurement | W2 SHA overlay baseline | W3 discovery checkpoint |
| --- | ---: | ---: |
| 16/50 solve wall | 1.417 s | 1.461 s |
| 16/50 thread CPU | 1.384 s | 1.424 s |
| 16/50 peak RSS | 314,140 KiB | 311,432 KiB |
| 16/50 regional JFR samples | 241 | 254 |
| 16/50 interning / hashing samples | 129 / 46 | 144 / 35 |
| 32/100 solve wall | 9.219 s | 8.922 s |
| 32/100 thread CPU | 8.990 s | 8.689 s |
| 32/100 peak RSS | 391,140 KiB | 398,644 KiB |
| 32/100 regional JFR samples | 2,416 | 2,350 |
| 32/100 interning / hashing samples | 1,403 / 384 | 1,331 / 353 |

Reproduce after compiling tests with Java 21 and the existing pinned Maven cache:

```sh
# Run from the campaign worktree; JAVA_HOME/PATH must select Java 21.
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-values -am test-compile
python3 scripts/project/regional_cost_probe.py \
  --regions 16 --producers 50 --warmups 10 --repeats 100 --jfr --rss \
  --source-ref 80104d65c79381dd58085ebacc2ec43b5b579aec \
  --output .harness-results/w3/baseline-16-50
python3 scripts/project/regional_cost_probe.py \
  --regions 16 --producers 50 --warmups 10 --repeats 100 --jfr --rss \
  --compare .harness-results/w3/baseline-16-50 \
  --output .harness-results/w3/verified-16-50
# Repeat with --regions 32 --producers 100 and separate 32-100 output directories.
```

Keep the frozen baseline files when experimenting with a future design. The
source-overlay approach assumes the probe remains binary-compatible with the
three baseline classes; revise the test driver explicitly if that ceases to hold.
No assertion uses elapsed time, memory consumption or Java hash uniqueness.

## Validation at the W3 discovery checkpoint

**FACT:** all validation used Java 21.0.12.1:

- Focal `RegionalStructuralOracleTest`, `RegionalExplosionFixturesTest`,
  `StorageIndexTest`, `CfgBuildCoordinatorTest`: PASS.
- Complete `analysis-values -am test`: PASS; cfg-kernel 108, analysis-kernel 79,
  analysis-values 195, total **382**, zero failures/errors/skips.
- `python3 -B scripts/harness/lean.py fast`: **PASS CODE_CHANGE**, 85.221 s;
  **556 required unit/contract methods, zero skips**, including the three new
  methods explicitly registered in fast-test-inventory.json. Existing compiled
  architecture, consumer and wire checks pass; no baseline regeneration.
- Full typed file comparisons at 16/50 and 32/100: PASS. All solve metrics and
  legacy observation snapshots match as well.
- Comparison negative control: intentionally comparing 4/1 against the captured
  4/5 baseline exits nonzero with `W3 semantic snapshot mismatch: facts.typed`.
- No production change, so no per-optimization performance claim or new
  structural acceptance result. Full corporate/E2E qualification not run (W4).

Raw evidence: `.harness-results/w3/{baseline,verified}-{16-50,32-100}/`,
`focal.log`, `modules.log`, `fast.log`, and `reject-mismatch.log`. The mandatory
FAST check is new evidence; W1/W2's earlier historical results remain historical.
