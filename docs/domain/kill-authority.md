# EP-W2 — positive overwrite authority

## Contract before implementation

Authority: AIR Assign, HavocMust, envelope mustOverwrite and completed Invoke
outcomes; memory/alias rules and entry.possibilities@2 in the pinned normative
specification. These are EXPLICIT_CONTRACT facts. A statement being unknown is
never itself a write proof. Candidate sets and their supports use the existing
domains and solver; this change centralizes update policy, not dataflow.

An overwrite permit requires an executed mandatory write, a complete known
destination, no unresolved destination remainder, and a direct source-applicable
target. A target introduced only because bases may alias cannot get a permit.
An unknown source with a proved MUST destination may kill: source uncertainty
and execution/destination certainty are separate dimensions.

RD uses a singleton destination permit. Regional Values represents correlated
stores: a closed finite Choice splits the state into its exact selected
destinations. Each alternative requires a selected-destination permit; the old
store can be excluded only if every possible selection is represented. Thus a
mandatory choice of prefix or suffix changes the whole image in every modeled
alternative, while either individual half may retain its previous contents.
This is positive per-alternative MUST evidence, not promotion of an alias MAY.
Open choices, MAY sets and uncompleted outcomes retain the previous store.

The scalar profile already admits only direct Cells with explicit separation.
Its strong updates additionally require a permit derived from the actual AIR
operation and exact Cell binding. Generic state APIs distinguish initialization,
widenUnknown, weakUpdate and strongOverwrite. Initialization occurs only at the
invocation boundary; observations and backedges cannot call it.

## Algorithm, limits and executable oracles

Check the finite destination set once per prepared write/transfer; known ranges
must have both bounds (a whole typed Cell is an exact logical location). Existing
transfer alternatives are retained or unioned unless a permit justifies replacing
the affected portion. No new solver, path enumeration or invented value is added.
Time is O(number of prepared targets) per policy check; state/output complexity
remains that of the existing finite set/image domains.

Independent oracles: generic unknown effects preserve a supported candidate;
MAY updates retain old support; unproved foreign-base aliases cannot kill or copy
unrelated literals; exact executed MUST removes old support; repeated observation
and a backedge cannot reseed VALUE; open storage preserves logical support.
Existing closed-Choice concrete image oracles guard against confusing MAY per
location with an obligatory selected write. Policy mutants must fail semantic
assertions, not compilation or test selection. Initial-state coexistence and
global failure containment remain W4 obligations.

Falsification: change only the proof axis (closed → open destination/outcome,
disjoint → unproved alias), leaving the candidate and syntax family unchanged.
If old evidence disappears without a permit, the architectural law is false.
