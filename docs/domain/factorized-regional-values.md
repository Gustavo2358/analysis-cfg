# Factorized Regional Values — EP-R2 W2 decision

Status: design recorded before implementation. Scope: existing RegionalValuesAnalysis
on the existing DataflowSolver/BatchReplayer. AIR, KillAuthority and wire contracts
are unchanged. Review occurs on the campaign Draft PR before merge.

## Information and obligations

The current `SegmentMap<Set<Store>>` stores complete joint byte/scalar images for
each copy-connected group. It preserves branch pairings, exclusive destinations,
byte producer coordinates, captured BEFORE source ranges, uncertainty and logical
support. Weak updates enumerate unchanged/changed worlds for each target. Entry
P independent contributions over N bases therefore stores (P+1)^N images; even
independent slices in one base multiply. Unknown write provenance also distinguishes
worlds without any known candidate (LAB I).

Queries need the alternatives of their selected physical range and candidate-specific
supports. Copies need the source at BEFORE, and local relationships with destination
bytes that survive. Existing branch-pair and overlapping-copy oracles require those
relationships. Merely replacing every base with an independent set would lose
these useful correlations; replacing every base with complete images still multiplies
independent slices. Neither is sufficient for this campaign.

## Decision: shared decision DAG over partition segments

Represent a group's relation as an ordered, multi-valued decision DAG. A level is
one existing StoragePartition segment (or scalar cell); an edge is its immutable
content with complete evidence. Equal suffix relations share nodes. A terminal
means the empty suffix, and the empty relation is separate. Nodes are interned
within an execution, not globally. The fixed level order derives from prepared
storage identity/ranges, never from program values or source spellings.

For independent A={a1,a2}, B={b1,b2}, C={c1,c2}, three nonterminal nodes and six
edges represent all eight combinations. No complete Store is allocated. Different
predecessor relationships may retain different suffix nodes. The partition is by
finite program boundaries, never by every octet.

This follows the shared ordered graph, memoized apply and restriction approach in
[Bryant, Graph-Based Algorithms for Boolean Function Manipulation (1986), §§2,4.3–4.4](https://www.cs.cmu.edu/~emc/15414-s14/lecture/ieeetc86.pdf).
Our extension uses finite content-labelled edges, retains every level, and performs
relational union and image updates. It is not a Boolean solver. Graph ordering can
affect size; genuinely relational inputs can still need exponential space.

## Transfer and projection

* Entry: validated simultaneous strong refinements followed by possible/open
  widening; adding a local alternative changes an edge, not every complete path.
* Weak update: union old relation with the image under the selected update.
  MAY_SET impacts compose locally, sharing unchanged suffixes. Unknown edges carry
  their event/reason; ExternalUnknown is not eliminated.
* Strong update: use the existing positive KillAuthority permit; transform only
  destination segments. Closed exclusive destinations union their individually
  selected images, preserving the established selection semantics.
* Join: memoized union of DAGs. No predecessor Cartesian multiplication. Equality
  uses canonical nodes; support-only changes remain observable.
* Copies/FitText: project only the prepared source slices needed by that operation;
  restrict the BEFORE relation to each supported local capture, then update the
  destination in that restricted relation. All sources of one operation are captured
  before any destination is changed. Unrelated components remain symbolic. Preserve
  byte producer offsets, captures, logical supports and source gaps using ByteImage.
* Query: project just the requested range, compose its fragments and decode with
  the existing codec rules. Local output combinations are necessary when the query
  itself requests a composite value. No entry reseed, global world reconstruction,
  fabricated name, range or support.

Initially retain exact representable correlations. If an operation needs a sound
loss of correlation, record that policy and its open remainder explicitly before
adding it; supported edges may only grow. No cardinality-based precision switch.

## Soundness, finiteness and cost

Concretization is the union of labelled paths in each group's DAG. Interning only
shares equal suffix relations. Union, restriction and local image are the same set
operations as on complete Stores; ByteImage transfer and evidence semantics are
retained. Separate groups retain the previous product interpretation. This is a
representation change, not a change to MUST or AIR validity.

Assumptions: validated AIR/positive kill are EXPLICIT_CONTRACT; finite partition and
static event/provenance identities are ARCHITECTURE_GUARANTEED. Synthetic G/I shapes
are OBSERVED_IN_FIXTURES_ONLY, not a universal complexity promise. The existing
finite image/evidence domain and monotone union give fixpoint termination; loops
must be exercised because an incorrect canonicalization could obscure changes.

For independent factors, retained edges grow as the sum of local alternatives.
Memoized union costs graph-pair work, bounded by the product of graph sizes times
edge handling, not the number of complete paths. Local updates visit graph nodes;
read projection costs the projected output size plus DAG traversal. A query/copy
requiring genuinely correlated composite alternatives can have exponential output;
there is no arbitrary cap or success on timeout. DAG interning/caches and intermediate
nodes must be measured as well as final roots, to avoid hiding materialization.

Telemetry: retained decision nodes/edges, max edge cardinality per component,
total interned nodes/edges, projected capture alternatives, boundaries and retained
state maxima; existing preparation/worklist/transfer/join/comparison counters stay.
No semantic decision may read these counters.

## Qualification and rejected shortcuts

G 1/2/4/7 and independent slices prove additive structural growth; I with no seeds
proves transfer factorization. E1/E3 scaled diamonds exercise joins and copies.
Existing independent concrete oracles protect branch correlations, overlapping copy,
producer coordinates and loops. H2 and full JSON products protect recall. Policy
mutants include Cartesian reintroduction and support loss.

Rejected: entry-only optimization (I still fails); per-base complete images (slices
still multiply); unqualified nonrelational replacement (existing copy correlation
contracts regress); caps/truncation; dropping provenance; second solver; frontend
SYNC changes or invented separation. None is authorized by this decision.

## First implementation checkpoint

W3 and W4 share the same State/Engine representation and are implemented together;
there is no temporary second domain/solver. Entry-only structural REDs pass, as do
the I weak-write regression, G/E scaled copies/joins and the unchanged independent
concrete branch/copy/loop oracles. `w3-focused.log`, `w4-scale.log`,
`domain-family.log` record this checkpoint (104 values-family tests, zero failures).
The 17 selected product verticals in `product-01` pass real codec/CLI byte parity.
Further W6 falsification and policy mutations remain pending; no final FAST yet.

W6 found a local projection hazard: a Choice source must project one selected
range, not the simultaneous product of every candidate's range. A focused oracle
first needed explicit PARTIAL_ANALYSIS because AIR cannot discharge FitText over
the Choice; that setup error is not a semantic RED. With the honest partial
policy, `w6-choice-red-valid.log` fails the projected-alternative bound against
3fc5ac1; `w6-choice-green-valid.log` passes after candidate-local capture.
Source identity and the original candidate index still govern copy provenance.
