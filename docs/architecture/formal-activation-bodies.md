# Formal activation bodies and concrete push symbols

WORK-AIR-SCALE / AS-W04–05, resident bridge. This document describes the replacement,
its proof boundary and remaining work; it does not certify the whole scale plan.

The previous Region(frame,input) duplicated ordinary body equations and structural
shapes for every invocation. In the128caller/128node witness it retained16,515
analysis points. The replacement separates a canonical body interface from actual
invocation symbols. A concrete symbol retains its operation, activation key, ports,
default and route destinations. Body identity includes its explicit entry and only
observed top-port membership/route availability; destinations belong to the binding.
There is no source-procedure or label-name inference. Different entries and truly
observable top interfaces remain distinct specializations.

Interface discovery follows ordinary successors and explicit parent continuations
of nested calls, including guard rejection. Resume ends that walk; boundary records
its port/route and also explores its default. Zero unwind preserves the frame;
positive unwind/reset terminate that walk. A positive unwind can land outside the
ancestor's entry walk: the union of legal landing suffixes is discovered once per
control owner and contributes ports/routes to every interface. This conservative
projection was required by a real prototype counterexample that missed a reachable
boundary. It does not infer procedure ownership of arbitrary labels. Root Entry
keeps the separate empty-stack interface.

## Preconditions and relation

- IR_GUARANTEED: admitted typed destinations, declared ports/routes, explicit guard
  keys, count/reset semantics and Unit ownership from the locked AIR/local rules.
- ARCHITECTURE_GUARANTEED: body operations retain the same ProgramIndex node/context;
  callbacks receive those identities, not a representative invocation's source.
- EXPLICIT_CONTRACT: equivalent immutable states are observationally interchangeable
  under deterministic domain callbacks; fingerprints remain congruent. Convergence
  remains the domain contract. Distributivity is not assumed.
- OBSERVED_IN_FIXTURES_ONLY: the measured sharing factor, elapsed time and finite
  generated corpus. None is a production capability or semantic quantity cutoff.

A concrete configuration maps to a body class, a word of **actual invocation
symbols**, its input/equation state and a Boolean predicate over the complete current
active-key valuation. Unlike the old ancestor-only convention, this valuation
includes the current top. Push links store their actual symbol separately from the
parent/child body representatives. This separation is required even when keys,
entries or return targets are equal.

For a successful push of symbol a with key k, the child valuation is the caller
valuation with k present. Therefore the preimage of child predicate P is exactly
`P[k := TRUE]`, intersected with the caller predicate. Root closure evaluates the
empty valuation. Guards test their stated key; no representative own-key constant
is substituted. The same relation drives exact feasibility, verified positive paths,
structural supports and boundary searches. Positive supports use unlabeled body
vertices and labeled binding vertices in the shared SCC closure. They are a pruning
superset, never a feasibility witness.

## Transition argument

Ordinary edges retain their original source/destination and effect. Invoke first
filters its positive/negative guard, then adds the actual push symbol. Resume exports
a formal pop event identified by its source and default/route role. The matched
caller resolves the destination from its actual invocation, cofactors that pushed
key and applies the original edge once at the resolved destination. Boundary uses
its body interface's top-port decision; nonmatching default preserves the frame,
matching pop binds the actual default/route, and unavailable routes retain the
explicit invalid exit. Counted unwind propagates the exact remaining count through
matched caller bindings; reset/Unit exits close at the Entry root. BigInteger
underflow decisions and recursive-activation refusal are preserved.

Induction over a concrete step gives each corresponding symbolic rule. Conversely,
a feasible symbolic rule has a witness word of actual symbols and its predicate
holds on that word's valuation, so it produces the same typed concrete step. Body
sharing joins only equivalent argument evaluations; unequal arguments remain exact
separate index entries. Disjoint predicates continue to preserve state correlation.
These arguments require all landing observations to be included in the interface.

A boundary's uniqueness is over actual symbol words, not body paths. The exclusion
automaton compares actual pushed symbols. Distinct invocations sharing a class can
still give two contexts; a dead invocation gives none. This preserves both rejection
of ambiguous boundaries and admission of a unique internal boundary.

Backward continuation arguments are normalized by formal exit source. Before
sharing, each actual binding applies the domain edge transfer at its actual
source/destination to the selected continuation value. This is necessary because
a generic domain can distinguish destinations and be nondistributive. Applying the
representative edge after sharing equal raw values would be unsound. Choice predicates,
root values and ordered unwind ancestor values remain part of the exact signature.

## Validation and limits

The independent explicit-stack oracles cover guards, ports, routes, unwind/reset,
reentry and every IN/OUT state in both directions. New tests additionally cover the
physical body bound, unused top ports/routes, distinct return targets, edge-sensitive
backward inputs with a nondistributive block, exact boundary ambiguity/dead symbols,
and an unwind landing outside the normal body walk. The initial body bound was RED;
the incomplete unwind interface also had a separate semantic RED. No expected output
was regenerated from the new builder.

The algorithm terminates when the existing finite-context/domain convergence
contract holds; no arbitrary frame/candidate/depth cap was added. Conditional stack
predicates and domains can intrinsically grow. Cost includes distinct observable
interfaces/arguments, Boolean relation size and requested output, rather than claiming
O(input) universally. Different-entry overlaps, per-interface ordinary prefixes,
whole-route witness BitSets, backward ancestor-map copies and pre-refinement transient
conditions remain separate debts. This bridge is resident; full paged ProgramStore,
managed control/equation dictionaries and end-to-end spill remain unimplemented.

The primary foundation is [Carayol and Hague2014, sections2–3](https://arxiv.org/pdf/1405.5593):
P-automata distinguish control states from stack symbols and represent reachable
configurations regularly. This AIR-specific conditional/value construction requires
the argument and oracles above; their polynomial bounds for ordinary PDS are not
claimed for all our guards or opaque domains. Fixed independent fixtures establish
operational evidence, not a proof for an unavailable corporate input.

Checkpoint gate: FAST869required methods and every existing source/compiled/transport
boundary pass with zero failures/errors/skips in142.175s. The W2 checker initially
rejected a new solver dependency on CfgNode; invocation lookup was moved into the
existing structural control port, preserving the forbidden edges. Large original
shared-body families and public CLI comparisons are still separate obligations.

## Backward equation read dependencies

Backward calls read their bound formal-pop continuations and declared unwind
landings. These read edges are compiled into each region's predecessor relation;
a continuation change enqueues its readers rather than every call in the body.
A child entry change still notifies its matched callers. Global root signature
changes retain their existing conservative notification until signature projection
is separately justified. Formal-pop source lists are compiled once per Shape,
so binding does not rescan the complete ordinary body for every caller.

An impossible symbolic CALL can have no reached child Shape. Its empty read
interface is skipped, without admitting the rejected call or changing guard
semantics. This case was found by two independent generated stack oracles and
has an additional explicit guarded-call regression.

The scheduling argument is dependency completeness: an equation is initially
evaluated, every mutable input it reads has a notification edge, and changes
reschedule those readers. Ordinary predecessors, formal-pop continuations,
unwind destinations, child entries and global root signatures remain covered.
No domain distributivity, convergence cap or caller-order heuristic is added.
The geometric serial-call test was RED at32 callers with1,107 transfers; its
unchanged bound requires at most16*(callers+body nodes) transfers and formal
return-source reads. The complete192-method kernel now passes, including every
IN/OUT comparison with independently interpreted concrete stacks.
