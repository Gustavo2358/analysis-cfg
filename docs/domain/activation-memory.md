# Activation memory: support and ownership

Authority: AIR 2.0 control.local, reentry_guard, resume_routes and unwind_all at
`4f09e8b1b496bf8de2e0fb62532e7aa0b97b9c6e`. Local frames have matched continuations;
an ancestor key can be present only on a transitive caller path. A jump preserves
the stack, and an unwind/root transfer may change the active frame.

## Structural support before Boolean tabulation

Before constructing guard formulas, traverse typed control for each possible top
frame. At invokes, record the caller relation and conservatively visit every
declared continuation and guard failure. No return is asserted by this scheduling:
only a superset of possible callers is computed. At positive unwind, its destination
is considered under every possible remaining frame (including root); unwind-all
seeds the root destination. Boundary/resume follow the exact top-frame rules.
Transitive parents provide a superset of ancestor keys. A key outside that set is
necessarily absent; the current frame's key remains unconstrained because it is
shadowed locally. This restriction is applied before structural combinations.

Premises: IR_GUARANTEED for transfer rules; ARCHITECTURE_GUARANTEED for closed,
immutable ProgramIndex/ContextView. Unknown ordinary control keeps all transitions
already admitted by the context. No source spelling, ordering, stack-depth cap or
fixture size is consulted. The graph has finite frames and nodes; traversal and
caller closure terminate. Worst-case traversal is O(F*(V+E)); caller closure may
be quadratic in F and output support size. No universal polynomial BDD claim.

## BDD lifetime

BDD unique-table membership is not external ownership. Only conditions reachable
from solver/model roots are needed between processing steps. Collection runs at a
safe point after a complete step, with no operation-local scratch active. Surviving
IDs remain stable; dead slots may be reused only after computed entries have been
invalidated. A feasibility scratch scope appends nodes instead of reusing slots,
so rollback cannot invalidate an existing condition or return a scratch ID.

Roots include entry environments, incoming/child subscriptions, recursion guards,
exit partitions, input/output partitions, feasibility/deferred keys belonging to
the same manager and all persistent ActivationModel guards, environments and parent
links. Historical regions are conservatively retained in this first collection
law; dropping them requires an additional subscription/recomputation proof.

Collection trigger affects only optimization, never semantic admission. Small
executions avoid scanning roots; threshold scales with retained live nodes. Rooted
mark/sweep is O(slots + reachable edges + roots); space is O(slots + live nodes).
It does not bound a single operation's scratch or an intrinsically large live BDD.

The [CUDD manual](https://www.cs.rice.edu/~lm30/RSynth/CUDD/cudd/doc/node4.html)
distinguishes external references from unique/computed tables and explains cache
invalidation on collection. We use explicit root traversal instead of CUDD's
reference-counting implementation. [Bryant 1986](https://www.cs.cmu.edu/~bryant/pubdir/ieeetc86.pdf)
establishes ordered-BDD canonicalization and size/operation limits; variable order
or collection alone cannot guarantee bounded complexity for arbitrary formulas.

## Oracles and qualification

Independent truth tables test every valuation, including repeated collection,
slot reuse and computed-cache collisions. The existing explicit-stack activation
oracle compares control/value results for both directions, guard overlap, nested
returns and unwind. Scale families measure work/product growth; heap/RSS and
repeated timing comparisons supplement these counters. ACTAND/ACTOR production
CLI runs and the corpus compare candidates, supports and all remainders. No final
query optimization or one fixture-specific path substitutes for these laws.

## Condensed caller support

The finite caller relation is oriented parent to child. Iterative Kosaraju computes
its SCCs without Java recursion; all vertices in one component have the same
transitive ancestor components. Seed each component with the exact variables of
its member frames, then propagate bitsets once along the condensation DAG in
topological order. Root has no variable and does not become a caller vertex.
This computes exactly the previous transitive closure, not a new approximation.
Duplicate edges are idempotent; equal activation keys remain equal bits.
Including independently owned returned frame snapshots, time is
O(F+P+(C+Pc+F)*ceil(K/word)), where P caller edges, C components, Pc condensed
edges and K activation-variable ordinal span. Peak space is
O(F+P+(C+F)*ceil(K/word)). Condensation shares computation, then clones each
frame's mutable BitSet result so downstream mutation cannot change another frame.
Dense returned support can still require quadratic space. The preceding typed
control traversal is separate and can cost O(F*(V+E)); SCC does not make that
whole stage linear.
Finite DFS stacks and decreasing DAG indegrees establish termination.
Independent seeded random graphs use scalar Floyd reachability as the oracle;
cycles, disconnected vertices, duplicate edges and shared variables are covered.
Typed traversal and unwind support laws are unchanged.
