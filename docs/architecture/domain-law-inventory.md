# Domain admission for the managed runtime

WORK-AIR-SCALE, AS-W00. This is an implementation inventory and proof obligation,
not a claim that every existing transfer already satisfies the replacement laws.
The generic `AnalysisDefinition` contract states assumptions; it does not prove
them for the built-in definitions. A managed domain registration must name its
actual order, stable equivalence, canonical key/codec, termination argument and
transfer class. No universal IFDS conversion or automatic input retirement.

| Domain | State/order and observations | Replacement condition |
| --- | --- | --- |
| Reachability | reached configurations with exact conditional stack language | monotone regular-language operations; negative guards/top-dependent returns require the contextual rule proof |
| RD | Entry-specific per-segment event sets, sparse default entry events, logical weak updates | static kill/gen is a fact-delta candidate; prove default/missing bindings, aliases, outcomes and support composition before admission |
| Scalar text | known candidates plus open remainder and producer supports, joined independently per Cell | literal/copy rules can use specialized deltas; multi-read expressions and branch feasibility require lawful relational equations; current expression transfer fails the published join-order witness below |
| Regional/physical values | factored image alternatives plus logical values/closure, same-prestate multi-source capture, aliases and MAY/MUST writes | retain relational kernels and read-before-write correlation; projection, overwrite, filters and support routing need independent laws; no distributivity assumption for the whole state |
| Source nominal values | candidate/proof sets, model/table assumptions, reached graph nodes, conditional branches and caller premises | demand closure is indexed Horn reachability; value propagation/proofs need persistent roots and exact versioned equations; caller premises are conjunctions, not independent paths |
| Generic SPI | arbitrary caller roots under the documented join/equivalence/transfer contract | managed hashing/spill requires an explicit lawful stable codec/key; finite height alone does not establish finitely many contextual inputs |

## Scalar join-order counterexample

The unchanged `TextExpressions.evaluate` selects one varying Cell per snapshot
and leaves other varying Cells unknown. Take two closed input stores, `a=(A,X)`
and `b=(B,Y)`, and assign `CONCAT(read x, read y)`. Direct evaluation yields `AX`
and `BY`; evaluation of the pointwise joined input yields an open remainder with
no complete candidates or producer supports. Under the actual candidate/support
join, `a <= join(a,b)`, while `f(a) <= f(join(a,b))` fails: joining the former
output into the latter changes it.

This is not a concretization unsoundness claim: the open remainder may cover those
concrete strings. It is a failure of monotonicity in the **published candidate and
support order** that governs dependency preservation and historical-version
retirement. Replacing the old output solely by the latest-input output would lose
explicit known dependencies. Joining historical outputs forever is not the new
architecture's solution either.

An independent two-store probe executed against the unchanged production transfer
reports `monotoneInPublishedJoinOrder=false`, two known pre-join outputs, zero known
post-join outputs, and three versus zero supports. The local evidence owner keeps
the source, command, source hash and raw output at
`artefatos-e2e/air-scale-implementation-20261006/probes/ScalarJoinLawProbe.java` and
`scalar-join-law-02.json` in the aggregation workspace. This observation is not a
repository test PASS or a qualification of the future transfer.

AS-W06 must supply lawful factorized expression semantics and preserve repeated
reads of one Cell as one selected input alternative. It must not enumerate a
whole Cartesian product just to store an intermediate, nor use unknown as a
replacement for already-established candidate/proof facts. New relational state
and monotonicity oracles must pass before AS-W05 can retire old bindings for this
domain. More precise valid candidates may change old serialization; no explicit
dependency or support may disappear. Termination also requires a proof for growth
through CONCAT loops, not an assumption that every expression is fitted to a
fixed destination width.

## Qualification still required

### Congruent lookup fingerprints

`AnalysisDefinition.stateFingerprint` is a solve-scoped immutable-root key law:
`equivalent(a,b)` implies equal fingerprints. The converse is deliberately false;
`StateIndex` checks equivalence on collisions and uses primitive open addressing.
The constant compatibility default preserves opaque user domains without assuming
their Java equals/hashCode. Expected constant-time lookup requires a registered
well-distributed key; adversarial collisions can still make lookup linear. A key
is not an order, codec, distributivity proof or permission to retire an old input.

Scalar keys include reachedness, bindings, candidates, openness and support ordinals.
AVL subtree fingerprints combine commutatively so equal maps built in different
orders have equal keys. Support/candidate hashes are cached; path copies update
the cached subtree contribution. RD keys include Entry, segment event sets and
logical sets. Regional keys include Entry, canonical relation nodes, logical values
and closed objects, matching the existing engine equivalence. Keys containing
canonical node identities are scoped to that engine and are never wire authority.

Backward continuation maps treat missing nodes as bottom. Each explicit pair adds
`mix(nodeHash ^ stateKey) - mix(nodeHash ^ bottomKey)`; an explicit bottom therefore
adds zero, map iteration order is irrelevant, and hash collisions still consult
the full existing signature equivalence. Depth and ordered ancestor positions are
included. Fingerprinting does not remove the current continuation-map copies.

The former linear lookup fails the4,096-input witness with16,777,216 comparisons.
Index tests cover aliases with different Java equality, forced hash collisions,
constant keys, capacity rejection and lease cleanup. Scalar map-shape/support laws,
SegmentMap shape/collision laws and repeated Regional solves within the same engine
pass.160generated indexed/colliding forward/backward solves match the independent
exact-stack oracle. The full177-method kernel passes. Root/history retirement and
lawful multi-read scalar transfers remain separate uncompleted obligations.

FAST856required methods and all compiled/source/transport boundaries pass in142.956s.
The first FAST selected all856methods successfully but rejected a stale W1D descriptor;
the refresh/recheck was sequential after preserving the earlier harness read/clean race.

The control relation's soundness/completeness proof, per-operation distributivity
classification, regional/source laws, canonical codec tests, live-binding
retirement and all new semantic counterexamples remain open. The page/index
foundation tests establish storage properties only. No domain is certified by
this inventory alone.
