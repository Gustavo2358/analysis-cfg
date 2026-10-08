# Exact partial assignments before condition canonicalization

Status: implementation in progress, WORK-AIR-SCALE; focused integration (114 tests)
and repository FAST (1,021 methods, zero skips) pass. CLI/global qualification remains pending. This is a bounded change to the condition algebra, not a
claim that the complete large-AIR architecture or all Boolean families are solved.

## Captured mechanism

A diagnostic over frozen d0f7485 completed 46,002 condition queries before a
60-second individual-solve watchdog. The slowest completed query took 9 ms.
45,537 of 45,666 equivalence queries began with different unordered source pairs;
simple pair-result memoization therefore cannot remove that observed scan. Source
IDs may be recycled across scopes, so these are diagnostic counts, not an identity
contract. Instrumentation and concurrent host work invalidate benchmark claims.

A second observer captured an exact 43-node candidate pair with twelve primary
keys. An independent exhaustive table of all 4,096 assignments established the
forms `x0 OR AND(1,2,3,4,8,10,11,12,13,14,15)` and
`x0 OR AND(1,2,8,10,11,12,13,14,15)`. They are unequal. These different compact
functions accumulate in nomination buckets when nested differences obscure their
factorization. Samples nominate candidates only; they cannot certify equality.
The linked candidate scan still exists and remains a debt for other families.

Raw evidence, command/classpath manifests, rejected representations and negative
results are retained in the local E2E repository under
`air-scale-implementation-20261006/runs/decision-query-{capture,curves}-01`.
A 60-second diagnostic cutoff does not establish an eight-minute individual case.

## Rule and preconditions

For arbitrary Boolean functions, AND(a,b) may simplify b under a=true; OR(a,b)
may simplify b under a=false. A signed native conjunction under true fixes every
one of its literals; a signed native disjunction under false fixes their
complements. Primary literals are singleton native junctions. These premises are
ARCHITECTURE_GUARANTEED by the existing complete signed-native descriptors.
Unknown/mixed premises remain unknown except for exact full-handle equality or
complement. Essential-support metadata and samples do not establish assignments.

`knownUnder` proves constant children by exact signed-set intersection and subset
operations. An opposite mixed junction then returns the surviving child or its
absorbing constant. A native conjunction/clause pair removes exactly those
literals whose values are fixed by its opposite operand. All rewrites preserve
both polarities; constants, unknown overlap and resource interruption retain their
existing semantics. No frame, candidate, dependency or proof is truncated.

The logical restriction/cofactor concept is described in Bryant, *Graph-Based
Algorithms for Boolean Function Manipulation* (1986), sections 1.1 and 4.4:
[author paper](https://www.cs.cmu.edu/~wklieber/15817-f08/ieeetc86.pdf).
This implementation proves local rewrites over its existing circuit and signed
sets. The paper's BDD traversal complexity does not establish a complexity bound
for our representation or for general SAT.

## Termination, ownership and cost

A mixed reduction returns a strict child or a constant. Iteration reduces the
sum of operand DAG depths; a native residual deletes at least one signed literal.
There is no distributive expansion or assignment enumeration. Native Patricia
operations retain their existing paged representation and full equality checks.
The operation cache records the original operand pair and its generation; a
result must never be cached solely under the reduced pair. No new resident index,
global cache, allocation bypass or store lifecycle is introduced.

For the nested-difference family, the common atom and native residual remain
factored; published mixed nodes do not accumulate a prefix DAG. The cost oracle
requires at most 4N equivalence comparisons and eight retained Boolean records
at N=4,16,64,128, in both duals and with independent signed permutations. Signed
set traversal/storage work is accounted separately. This does not prove O(N)
for arbitrary conditions, remove the nomination scan universally, or bound SAT
for every compact Boolean graph.

## Verification and scope limits

The pre-change nested-difference law is RED (twelve retained records at N=4,
limit eight). Focused GREEN preserves exhaustive small assignments, rare large
assignments, per-key perturbations and signed permutations. An additional
independent four-key truth-table law exercises native premises in mixed signed
junctions. Existing factorization, collision, alias, ROOT16N, storage and failure
oracles remain unchanged and require integrated qualification.

Fresh/reused JVMs and lawful dispatch permutations are required before publication.
Insertion and reverse iteration are diagnostic variants, not production policy.
The plain ROBDD replacement was rejected after growth regressions and is archived.
The remaining AS-W00–W10 work and final CLI/dependency qualification remain open.

The active implementation also passes two fresh JVM replays of three epochs / 96
solves each with diagnostic insertion iteration. Counter runs at N=4,8,16,32,64,128
complete with 6,15,31,63,127,255 candidate comparisons. The identically observed
reference records 10,097 comparisons at N=16 and stops at the 60-second diagnostic
limit on N=32. These finite-family counters support the factorization argument;
elapsed times remain diagnostic. Gate commands and failures are preserved in the
local E2E record, including the first incomplete reactor selector attempt.
