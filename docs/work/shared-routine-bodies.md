# SHARED-ROUTINE-BODIES — stage 5 consumer

- status: IN_PROGRESS
- scope: User authorized all remaining checkpoints through stage 5 completion on 2026-09-30, without intermediate approval stops. Dedicated worktree/PR; no merge.

## Rules and design before implementation

Authority: AIR 2.0.0 §05.7, O-56–O-60, pinned `2c7f31f19efbe3211a2aea5bbda90173a9666fe2`; transport `air-java#23` at `e0aef0e1928d88a74fe66b7a4d0af84556b84b19`. Prior stage-5 discovery is in that PR's `docs/work/shared-routine-bodies.md`.

Project one node per AIR body sequence, ordinary edges plus typed local-control rules. A rule retains the originating operation and exact CFG destinations/ports. A local invocation pushes a frame (operation, resume, ports); boundaries inspect only the top; explicit resume pops; unwind pops exactly its count or produces its specified invalid-control exception. Memory activation is shared. No ordinary invoke-to-resume edge and no unconditional union of potential return edges.

Traversal states are `(activation Entry, CFG node, local stack)`. Reachability and solver use the same contextual successor API. Values at distinct states are separate during propagation and instruction replay; observation may join results only after replay to the requested boundary. No fallback memory effects are applied when the local operation itself is interpreted; body operations provide its effects. Fallback obligations remain on AIR.

Initial execution domain: finite contexts without a repeated simultaneously active local invocation occurrence. Repetition is explicitly refused by contextual traversal; it is not cut off with a depth bound or treated as unreachable. The shared graph itself can represent recursive rules. Producer sharing will preserve the former explicit source-undefined/reentry frontiers and retain specialization when contextual equivalence cannot be established. Ordinary loops that pop before another invocation remain admitted at any number of iterations.

This is explicit finite-state exploration of the AIR transition relation, not IFDS or a recursion-summary algorithm. The old lowering already pays for many of these contexts by duplicating full operations. Here only context handles/state roots expand; the body and operations remain shared. For B body nodes, R rules and reachable context graph (V,E), storage is O(B+R+V+E) plus abstract states; traversal is output-sensitive. Persistent stacks share parents and avoid copying complete bodies. A push scans active frames for recursion, stack equality can inspect depth D, and boundary matching scans the current port set; worst-case traversal work includes these factors, rather than claiming constant-time stack operations. No claim of linear context count or improved solver iterations.

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

2026-09-30: semantic RED (`local-projection-red-03.log`) showed `UNSUPPORTED_CAPABILITY` for valid AIR. Projection, persistent local frames, contextual solver/reachability and per-context observation replay are implemented. All modules passed `mvn test` in `local-integration-compile-05.log`; final module tests and FAST subsequently passed. Focused tests cover matched calls, nested/top-only boundaries, empty/default/invalid returns, exact and huge unwind counts, Entry isolation, loops, recursion refusal, separated caller roots, and a non-distributive observation transfer that must not invent cross-caller pairs. Compiled architecture inventories were reviewed for the contextual API. Final corpus qualification and focal checks are recorded below; the final full wrapper passed (`cfg-qualification-03.log`).


## S4–S5 qualification

The producer is now pinned to lower PR #52 at
`85abf8e0e3f601e9c56b322bc1dcf8a055e7c75a`. It shares only typed, proved body
closures; CICS support and handler mode participate in the body key. A change to
entry support, handler registration, exceptional dispatch or a nonlocal escape
prevents sharing. Recursive source contexts retain the previous explicit frontier.

Fresh four-stage replay: **560/560** succeeded: CardDemo 73, PERFORM 39, Chaos 48,
aliases 14, PERFORM adversaries 25, frontend fixtures 331, previous focal contracts
29 and one frontier case. All 560 SPs are byte-identical to baseline. Comparison by
full source occurrence and FILE action preserves every previous program/file
candidate, support kind and written provenance leaf: **zero losses, zero
additions, zero support/provenance losses**. Multiplicity is aggregated across all
operations at an occurrence, never overwritten by the last operation. Representation
identity is intentionally different; dependency values are not.

Independent contracts passed: 78 PERFORM/alias oracles; Chaos 48/48 with 31
rejected negative mutations; CFG wire 560/560; all 3,600 local rules correlate to
full AIR operation/label/port identities. Caller-value focals check both the
qualified pipeline and the executable AIR consumer: first continuation A, second
B, shared body A/B. CICS focals retain distinct registrations and do not resurrect
the replaced handler. Three compiled mutations (skipping a body, matching an
outer frame, joining before instruction replay) fail the focused tests; restored
production passes. AIR transport has three additional rejected mutants.

### Representation results

| CardDemo measure | Baseline | Shared bodies |
| --- | ---: | ---: |
| Nodes | 110,570 | 79,176 |
| Ordinary transitions | 124,356 | 84,504 |
| Separate local-control rules | 0 | 3,063 |
| AIR bytes | 1,338,048,740 | 1,122,289,870 |
| CFG bytes | 80,302,820 | 57,186,388 |

Nodes decrease 28.4% overall. Sixty programs shrink, five are unchanged and eight
increase slightly: explicit invocation/resume/invalid-return nodes add overhead
when few body copies are saved. This is not a claim that every graph shrinks.
COACTUPC: 5,037 → 3,132; COTRTLIC: 5,656 → 2,943; COTRTUPC: 1,600 → 598.
COACCT01 and CODATE01 stay at 32,145 and 17,440 under the admitted closure proof.

An alternating baseline/candidate experiment used those five programs, two
repetitions, the same SPs and `-Xmx2g`: 60 lower/CFG/dependency processes. Summed
elapsed time was 242.57 s → 221.28 s; maximum process RSS was 2,562,472 →
2,556,092 KiB. These are local observations under concurrent gate load, not a
universal speedup or reduced solver-context complexity guarantee.

### Oracles and historical assumptions

The full gate exposed tests that equated a COBOL activation with a separate AIR
body. PERFORM, multi-CALL, partial and FILE composition oracles now follow local
frames and check exact resumes. They retain all prior values, kill expectations,
remainders, supports, A/B byte determinism and sequence-permutation invariance.
The Chaos 40 manifest is unchanged: its three source activation expectations are
checked by an independent scalar AIR interpreter, plus equality with the published
aggregate. Missing/swapped/invented activation values are rejected. No production
result or historical evidence was edited to satisfy an oracle.

### Boundaries and integration

- This is a representation campaign. No frontend or AIR normative-spec changes;
  no new kill proof, branch pruning, dependency names or forced reachability.
- Finite local contexts are supported. Recursive activation is explicitly refused;
  the producer does not opt recursive or nonlocal-escape closures into sharing.
  The solver still holds separate reachable context states; it has no recursion
  summary or universal context-count reduction.
- Existing PARTIAL, source uncertainty and modelAssumed constraints remain.
- CFG v5 is necessary for rules that cannot be represented as unconditional
  edges. The current `cobol-graph-explorer` importer accepts v1–4 and must gain v5
  rule-aware traversal before displaying these new graphs. UI migration is not
  included in these analyzer PRs; older graph formats retain their wire contract.
- Review order: AIR #23 → lower #52 → CFG #57. These remain Draft; merge and pins
  to actual merge SHAs require the later integration step.

Raw products, commands, mutations, source hashes and measurements are preserved
in workspace `.shared-routine-bodies/evidence/`; local integration report:
`artefatos-e2e/shared-routine-bodies-20260930/REPORT.md`.


Metadata audit: all 560 `sourceDependencies`, global analysis status/boundary and
publication inventory are identical. Qualified source evidence is identical after
removing only its recorded AIR byte hash. In 103 products the uncertainty-reference
set changes with representation multiplicity. The only removed uncertainty meanings
are 73 groups of `TOPOLOGY_OCCURRENCE_NOT_IN_ENTRY_PROJECTION` in 15 cases: all 263
associated source occurrences now have concrete operation outputs in published
coverage, verified against their source handle, mapped unit and complete AIR identity.
Other prior uncertainty meanings and written origins survive. Materialization does
not assert runtime reachability, and global completeness was not upgraded.


## Final gate result

`python3 -B scripts/harness/lean.py qualification-local` passed in its entirety
against the final AIR/lower pins: full Maven, every architecture boundary,
semantic/performance/integration W1–W5 and real-source W2D, MOVE, PERFORM,
multi-CALL, partial and FILE composition checks. The earlier incomplete runs and
their causes remain in local logs; only run 03 is the final full result. FAST also
passed; exact final commit CI is tracked on Draft #57.

[Compact evidence, all 73 CardDemo measurements and SHA-256 index](shared-routine-bodies-evidence.json).
Implementation and qualification are complete; repository lifecycle remains
IN_PROGRESS until review and a separately authorized merge.
