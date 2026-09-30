# SHARED-ROUTINE-BODIES — stage 5 consumer

- status: IN_PROGRESS
- scope: User authorized all remaining checkpoints through stage 5 completion on 2026-09-30, without intermediate approval stops. Dedicated worktree/PR; no merge.

## Rules and design before implementation

Authority: AIR 2.0.0 §05.7, O-56–O-60, pinned `2c7f31f19efbe3211a2aea5bbda90173a9666fe2`; transport `air-java#23` at `e0aef0e1928d88a74fe66b7a4d0af84556b84b19`. Prior stage-5 discovery is in that PR's `docs/work/shared-routine-bodies.md`.

Project one node per AIR body sequence, ordinary edges plus typed local-control rules. A rule retains the originating operation and exact CFG destinations/ports. A local invocation pushes a frame (operation, resume, ports); boundaries inspect only the top; explicit resume pops; unwind pops exactly its count or produces its specified invalid-control exception. Memory activation is shared. No ordinary invoke-to-resume edge and no unconditional union of potential return edges.

Traversal states are `(activation Entry, CFG node, local stack)`. Reachability and solver use the same contextual successor API. Values at distinct states are separate during propagation and instruction replay; observation may join results only after replay to the requested boundary. No fallback memory effects are applied when the local operation itself is interpreted; body operations provide its effects. Fallback obligations remain on AIR.

Initial execution domain: finite contexts without a repeated simultaneously active local invocation occurrence. Repetition is explicitly refused by contextual traversal; it is not cut off with a depth bound or treated as unreachable. The shared graph itself can represent recursive rules. Producer sharing will preserve the former explicit source-undefined/reentry frontiers and retain specialization when contextual equivalence cannot be established. Ordinary loops that pop before another invocation remain admitted at any number of iterations.

This is explicit finite-state exploration of the AIR transition relation, not IFDS or a recursion-summary algorithm. The old lowering already pays for many of these contexts by duplicating full operations. Here only context handles/state roots expand; the body and operations remain shared. For B body nodes, R rules and reachable context graph (V,E), storage is O(B+R+V+E) plus abstract states; traversal is output-sensitive. Persistent/interned stacks avoid copying complete bodies. No claim of linear context count or improved solver iterations.

## Independent acceptance cases fixed before code

- Two callers, one body, different resumes and incoming values; never cross-return or mix values at either caller's continuation.
- Nested calls; intermediate boundary matches an outer but not top frame and must follow default. Two boundaries may signal one port.
- Ordinary entry with empty stack follows boundary default. Empty resume and excessive unwind yield their explicit exceptional exits; jump preserves stack; zero/large unwind counts keep exact semantics.
- Return/halt discard local frames, independent Entries cannot exchange frames, exitless loops converge in the admitted finite domain. Recursive activation is explicitly unavailable, with no partial success claim.
- Instructions before observations replay separately per frame context; effects in a body reach its actual caller. Local fallback does not havoc memory a second time.
- Wire v5 records symbolic rules and identities; versions 1–4 and their existing products remain unchanged for graphs without local control.
- Invalid/cross-publication rule endpoints reject; permutation changes no semantic relations. Existing open-control and PARTIAL contracts remain intact.

Validation: focused RED/GREEN, adversarial matching/dataflow, FAST and relevant local qualification, then real producer/consumer four-stage regression across all 560 cases (including CardDemo73, PERFORM39, Chaos48, aliases14 and PERFORM adversarial25). Compare dependencies with provenance/supports and audit all deltas. The optimization must not manufacture candidate changes.

## S2–S3 progress

2026-09-30: semantic RED (`local-projection-red-03.log`) showed `UNSUPPORTED_CAPABILITY` for valid AIR. Projection, persistent local frames, contextual solver/reachability and per-context observation replay are implemented. All modules passed `mvn test` in `local-integration-compile-05.log`. Focused tests cover matched calls, nested/top-only boundaries, empty/default/invalid returns, exact and huge unwind counts, Entry isolation, loops, recursion refusal, separated caller roots, and a non-distributive observation transfer that must not invent cross-caller pairs. FAST's test stage passes; reviewed compiled inventories are being refreshed for the new public contextual API. Full final qualification is still pending.
