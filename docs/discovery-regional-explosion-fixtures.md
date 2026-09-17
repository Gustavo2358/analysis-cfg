# W1 — Regional explosion synthetic fixtures

DISCOVERY / REPRODUCTION — NO PRODUCTION FIX.

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
