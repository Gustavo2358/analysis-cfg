# Compositionality and conservative partial lowering

The default pipeline consumes structurally usable programs. Each supported region
has precise AIR; each semantic gap has a conservative operation and local
uncertainty. A semantic gap does not remove its program or dependency sites.

## Permanent invariants

1. **No Artificial Cardinality.** Capability limits semantic forms, never a finite
   number of occurrences. New features must pass 1, 2, 5 and a larger N, plus
   mixtures. A unique selected candidate in a RESOLVED nominal reference is a
   legitimate identity invariant, not a program/profile cardinality gate.
2. **No Silent Elision.** Unknown statements retain identity, provenance and an
   explicit operation/gap. They cannot disappear or become Nop without a proof
   of absence of effects.
3. **Conservative Fallback.** Reuse Assign/Branch/Jump/Invoke/Return for exact facts,
   HavocMust for a proved mandatory write, HavocMay for a possible scoped write,
   and Opaque with declared envelopes for insufficient effects/control facts.
4. **Local Degradation.** Apply effects at their ProgramPoint. A later unknown
   memory effect does not retroactively change an earlier observation. Unknown
   control may revisit an earlier region only when its envelope permits that.
5. **Known Facts Survive Uncertainty.** A possible write retains surviving known
   candidates and opens the remainder; a mandatory overwrite kills the old value.
   Strong later assignments can restore a closed model value.
6. **Completeness != Usefulness.** COMPLETE, PARTIAL and unknown knowledge are
   independent of publication success. Coverage, uncertainties and per-site
   remainders remain separate; no dependency-result wire change is needed.
7. **Whole-program rejection is exceptional.** Reject contradictory/invalid
   structure, unusable frontend output, identity/validation failure or an explicit
   resource limit. An unsupported statement/surface alone is not such a failure.

## Completion contract for future constructions

A construction is complete only with multiplicity, mixed composition, unsupported
neighbors, localized uncertainty and deterministic identity/control tests. This
applies to future EVALUATE, GO TO, I/O, SQL, CICS and PERFORM variants. Test numbers
are examples, never productive limits. No N-sized program gate may implement a
semantic profile. Physical inventory order does not create control edges.

The next product activity after this wave is CARDDEMO BASELINE: run the corpus,
count unsupported constructs and affected programs, measure CALL-site impact,
rank blockers by expected coverage gain, and rerun CardDemo after each vertical.
No precise EVALUATE/GO TO/READ/WRITE, SQL/CICS, general PERFORM, RD, Def-Use or SSA
is implemented by this wave. Git/PR/tests/merge are the record; remote FAST only.

## Generic AIR consumption

ConservativeEffectTransfer applies the existing direct-cell abstraction and
unchanged Candidates lattice: exact mandatory write -> UNKNOWN; possible writes
-> old candidates plus open remainder. Opaque uses known writes, mustOverwrite and
otherWrites from its memory envelope. Proved disjoint storage remains required
for the existing precise multiple-cell profile. No source opcode/name is examined.

CFG retains Opaque's source terminator and projects its known alternatives.
ContextView enumerates destinations allowed by open AIR scopes lazily; it does
not materialize a dense graph. The existing solver and transfer lattice are
unchanged. Reachability propagates control uncertainty along affected paths.
Existing Invoke known-normal analysis keeps its prior known-graph semantics and
source remainder; an invocation without a known normal target uses its open bound.

CFG JSON 3.0.0 extends the closed token domain with OPAQUE, OPAQUE_JUMP and
OPAQUE_RETURN and exposes openControlRemainder on terminators. Version 1/2 wires
retain their old shapes/bytes. Full envelopes remain in the correlated AIR input;
CFG JSON is not a replacement AIR publication or a standalone effect model.
The dependency-result wire remains 1.0.0, including coverage and existing remainder
fields. A known program candidate is not a certification of runtime name policy.

ConservativeOperationBoundaryTest is the AIR-only product gate, including disjoint
memory, prior sites, mandatory/possible writes and bounded open control. These
focal cases run in FAST. The source E2E and broad qualifications stay local.

The real-source local `e2e_partial.py` checks P1–P5, a real READ, CALL surfaces,
unknown names, both BASIC activation cases, nested/unknown IF, mixed declarations,
unknown entry signature and the focal 20/10/5/36 stress. P4 uses a mandatory unknown
write to a separately proved independent root. Each source statement has an AIR
operation link; SP/AIR/CFG/dependency bytes are compared across A/B and sequence
permutation preserves sites and edges.
