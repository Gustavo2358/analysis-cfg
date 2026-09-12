# CP6 MOVE→MOVE — scalar value propagation

[Lean work item](WORK-CFG-035.yaml). Baseline `cad8b45cdcd2d380cb98ba9984f34859551430e0`.
Branch `feat/move-data-to-data`; implementation awaits human review in a Draft PR.

Authority: AIR 2.0.0 at `51b4d9a8ae0364232bd97103cd73a77e1a34996c`,
`especificacao/04-operacoes.md` §1: Assign stores the evaluation of its expression
in the prior state; Read observes its place. IR_GUARANTEED: value copy, not alias.
EXPLICIT_CONTRACT: existing direct scalar TEXT Cells and DisjointStorage admission.
ARCHITECTURE_GUARANTEED: immutable Candidates and existing forward fixed point.

TextProfile admits exactly Literal(TextValue) or Read(ObjectPlace) assignment.
Transfer: destination := state[source], a strong update preserving candidates,
open remainder and existing candidate supports. An absent source entry is the
existing Candidates({}, true), not an invented value. Later source overwrites
cannot change the copied immutable value. No solver/lattice/CFG/model/codec change.
No RD, def-use graph, causal chain, path enumeration or backward scan.

Finite preparation indexes each copy once; transfer uses normal state lookup and
assignment, with no depth limit. Existing lattice/fixed-point termination applies;
N copies add N ordinary operations. Domain remains the existing scalar text profile.

Oracles: real COBOL one-hop, multi-hop, overwrite and snapshot through SP → AIR →
CFG → PossibleValues → dependency result; raw `PROGA   ` and model remainder false.
AIR-only branch+copy closed {PROGA,PROGB} and open {PROGA}+remainder preserve supports.
Negative missing disjointness and non-direct/non-text sources remain refusals.
Source/interpretation/effective openness remains explicit for partial real sources.

The same PR synchronizes W2D #20 as APPROVED / MERGED / CLOSED, preserving its
qualified source HEAD and merged tree in historical navigation. No receipt or
certificate is required. Remote CI is FAST ONLY; final qualification is local.

Pins: frontend `2b72d0c7ad72cbc9379a7e364607de89d5d1097c` (SP 1.5.0),
lower `16275bad22b2e48a13c3b2b3cf52bb29747e2ec1`, AIR Java
`760593b923ca7311f699547c36349f54eb0dac42`; normative AIR pin above is unchanged.

Development RED: direct copies were rejected as UNSUPPORTED_EFFECT_PROFILE in
three MoveCopyTest oracles. GREEN adds only TextProfile admission/transfer.
The old wire refusal fixture now uses FitText(Read), which remains unsupported;
plain Read is explicitly positive. Missing DisjointStorage still refuses.
