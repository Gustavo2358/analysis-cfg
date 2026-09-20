# POSITIVE_MEMORY_TOPOLOGY — W3 performance

Status: final legitimate-work, independence and control canaries green. All measurements use the accepted AIR semantics; no cap, widening, event collapsing, new solver representation or benchmark-specific behavior was added.

## Configuration

Java 21 (`21.0.12.1`), physical propagation only with `StorageAnalysisMode.EXPERIMENTAL_PHYSICAL` or CLI `--experimental-physical`. `W3LegitimateWorkTest` constructs published same-base views, explicit branch alternatives, CopyBytes, HavocMay and event histories using existing AIR fixtures. It measures preparation, solve and replay separately and asserts physical work really occurred. Raw JUnit output and E2E results are retained under `.positive-memory-topology/evidence/w3/`.

## Legitimate-work families

| Witness | Positive cause | Expected invariant |
|---|---|---|
| Same-base overlap | WHOLE, PREFIX and SUFFIX share one proved region; three writes overlap | final one candidate, two live producer supports |
| Real choice 2/4/8 | Explicit finite branch tree with a distinct producer on each leaf | candidate/support multiplicity 2/4/8 survives |
| Real control join | Two source branches each write both halves before reconvergence | two correlated whole values, four supports |
| Copy/correlation | Branch pairs on two regions followed by cross-base partial CopyBytes | only the two branch-correlated results survive |
| MAY effect | Explicit HavocMay over two known base objects | old candidate remains possible with true model remainder |
| Event history | Seven real writes on the same base, alternating the two halves | final candidate has the two last relevant supports |

The final pinned local run passed all eight witnesses. Counts are from `W3_WORK` in the retained raw log; durations are one local measurement in milliseconds, not a performance SLA. `groups/writes` are physical work applied, while targets/events are published work. Every witness has positive physical work.

| Witness | Targets/events | Candidates/supports | Groups/writes | Expanded labels | Relation union pairs | Max state alternatives | Max provenance rows | Prepare/solve/replay ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Same-base overlap | 3/3 | 1/2 | 6/6 | 14 | 0 | 2 | 0 | 37.11/30.53/26.47 |
| Real choice 2 | 2/2 | 2/2 | 2/2 | 8 | 1 | 4 | 0 | 0.89/2.60/2.44 |
| Real choice 4 | 4/4 | 4/4 | 4/4 | 16 | 3 | 8 | 0 | 1.04/2.84/2.75 |
| Real choice 8 | 8/8 | 8/8 | 8/8 | 32 | 7 | 16 | 0 | 1.05/6.04/3.61 |
| Control join | 4/4 | 2/4 | 4/4 | 12 | 1 | 4 | 0 | 0.94/1.57/1.32 |
| Copy correlation | 5/5 | 2/4 | 6/6 | 40 | 4 | 8 | 0 | 1.48/5.37/5.50 |
| Explicit MAY | 4/4 | 1/1 | 8/8 | 10 | 4 | 6 | 3 | 0.71/2.73/2.48 |
| Event history | 7/7 | 1/2 | 14/14 | 30 | 0 | 2 | 0 | 0.62/1.64/2.21 |

Real choice grows from 2 to 8 distinct candidates and supports with the corresponding real branch tree. The correlated CopyBytes witness retains exactly its two possible results. Explicit MAY retains the old candidate and the true remainder. The seven-event history retains the last two relevant producer supports. Raw metrics: `.positive-memory-topology/evidence/w3/legitimate-work-final.log` (local ignored evidence).

## Artificial work removed versus legitimate work observed

CP1–CP4 removed gap-only global effect/target/control compensation at its producer and lower callers. The W3 canaries show no added cross-base comparisons, materialized object pairs or compensating events in the independent cohort. This is a causal structural observation on the measured fixtures, not a general complexity claim. By contrast, the eight cases above intentionally publish overlap, alternatives, copying, MAY and repeated writes, and their physical groups, relation unions and support multiplicities remain. No performance shortcut was introduced to erase that work.

## W1 indep80/indep160 and control stress

The two W1 independence canaries ran once against the final pinned stack, with the frozen producer/consumer configuration. They are structural canaries, not timing SLAs:

| Case | Targets/events | Physical groups/writes | Base comparisons/object pairs | Expanded labels | Max state alternatives | Candidates | Physical consumer seconds |
|---|---:|---:|---:|---:|---:|---:|---:|
| indep80 | 161/161 | 161/161 | 0/0 | 333 | 160 | 1 | 1.016 |
| indep160 | 321/321 | 321/321 | 0/0 | 653 | 320 | 1 | 1.316 |

These counts match W2 structurally. Events equal the supported targets, with no compensating event growth; expanded labels grow from 333 to 653 rather than by a cross-base product. The default run in both cases had zero physical groups/writes. Raw data: `evidence/w3/canary-final/results.json`.

EVALUATE40 and W3 control20 were rerun on the final stack. They have 241/62 sequences and 80/20 branches respectively, with no Opaque, AllControl or AllMemory compensation and zero physical work in default mode. EVALUATE40 retains all 40 sites; W3 control20 retains both CALL candidates. Raw data: `evidence/w3/control-final2`.

The eight legitimate-work witnesses are deliberately bounded but exercise real overlap, alternatives, correlated copy, MAY and history. Real choice 2/4/8 increases relation union pairs 1/3/7, and state alternatives 4/8/16, along with the real candidate multiplicity. Copy correlation requires two bases and four union pairs. Explicit MAY has three provenance rows and a model remainder. These costs are causal to published AIR work; no duplicated cross-base work was found in this measured set. The lower local `qualification-local` performance/capacity suite passed (`PERFORMANCE_TEST_COUNT=39207`), and the integrated physical opt-in runs passed. Producer qualification reached only the separately proved preexisting `SOURCE_DEPENDENCY_OWNER_UNPROVED` blocker after its Maven tests passed. These local canaries do not establish production performance or operational physical readiness.
