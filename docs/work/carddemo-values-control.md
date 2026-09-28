# CardDemo values and remaining entry/control semantics

- id: CARDDEMO-VALUES-CONTROL
- status: IN_PROGRESS
- scope: the four capability families authorized after W8; separate review PRs, no merge authorization.

## Checkpoints

1. **VC0 — discovery and independent oracles.** Reproduce each family through
   frontend → SP → lower → AIR → CFG → dependencies, under both unspecified
   logical storage and the explicit IBM physical profile. Distinguish missing
   value facts, admission, control and source qualification. Successful process
   exits are not semantic PASS.
2. **VC1 — MOVE receiver sequences.** Preserve receiving order, sending operand
   evaluation, alias-family coherence, fitting and unaffected data. Cover
   literals, figuratives and data sources; retain explicit limits for conversions
   and overlap whose result the language does not define.
3. **VC2 — indexed text.** Publish source-proved table selection and initial text
   slices with identities and uncertainty. Unknown index is a set of possible
   in-range elements plus an open remainder, never arbitrary neighboring data.
4. **VC3 — expressions and special registers.** Separate pure text transforms
   from runtime inputs and implicit runtime state. Known receiver effects must
   not poison unrelated data. Do not invent CURRENT-DATE values or treat a
   compiler register as a user storage declaration.
5. **VC4 — external control and entries.** Model lexical SQL WHENEVER directives,
   dynamic SQL completion/effects, CICS condition dispatch and alternate entries
   at their canonical owners. An alternate entry is not an edge from the primary
   entry. Preserve handler/caller context and unavailable external effects.
6. **VC5 — qualification and review.** All 73 CardDemo programs, PERFORM 39,
   Chaos 48, aliases 14, PERFORM adversaries 25, frontend fixture corpus and CICS
   qualification; changed-repository FAST and impact-selected local full gates.
   Investigate each lost candidate/support using positive kill/exclusion evidence.

Each implementation checkpoint records its contract, tests, exact pins and known
limits. Commit checkpoints progressively. No shared routine bodies (point 5),
ALTER, UI changes, fabricated CFG edges, candidate injection or weakened oracle.

## Rule and ownership

IBM Enterprise COBOL 6.4 [MOVE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-move-statement)
requires receiving order and evaluates sending subscripts/reference modification
and functions before the first receiving transfer. Receivers evaluate their own
selection immediately before their transfer. Overlap does not grant a defined
copy result. These are LANGUAGE_GUARANTEED premises, not corpus observations.

The frontend owns binding, source layout, expression classification and COBOL
control. SP transports typed canonical facts. Lowering composes existing generic
AIR operations where sufficient; CFG projects actual AIR terminators. Source
qualification may preserve possibilities but cannot manufacture executable flow.
Synthetic IBM declarations remain modelAssumed and never prove runtime values,
physical layout, branch exclusion or a strong kill.

## Validation design

Derive focal expected behavior from language rules before implementation. Assert
values, ordering, reachability and provenance rather than operation counts alone.
Challenge aliasing, unknown values, missing inputs, nesting and alternate routes.
Preserve raw RED outputs and use separate directories for GREEN runs. Reuse W8
baseline evidence only while its assumptions remain valid; mark reused evidence
explicitly. Whole-corpus counts are telemetry, not a replacement for the oracle.

Discovery records and raw products: workspace `.carddemo-values-control/evidence`.
VC0 starts from the merged W8 mains; the baseline manifest and runtime are frozen
in `.carddemo-values-control/baseline.json`.

## VC2/VC3 source expression rule

Source-qualified text facts will carry a closed expression tree rather than
COBOL function names interpreted downstream. The initial operators are ASCII
uppercase and SPACE trimming. The frontend selects these operators from typed
function syntax; unsupported functions remain UNKNOWN. The evaluator preserves
each input support and modelAssumed confidence and applies receiving fitting
after expression evaluation. Current date and RETURN-CODE stay runtime unknown.

IBM [TRIM](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=functions-trim)
and [case conversion](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=strings-converting-data-items-intrinsic-functions)
are the language authority. ASCII-only uppercase is an explicitly bounded
implementation domain; other characters retain an open remainder. This is
source conditional evidence, not permission to infer physical storage or prune
a branch based on synthetic declarations. Evaluation walks a finite expression
tree and transforms finite candidate sets; existing work limits remain explicit.

Adversaries cover nested transforms, all spaces, non-ASCII input, unknown input,
model assumptions, fitting after TRIM, provenance through reads, and forged
operators/arity. The pre-change TRIM and UPPER-CASE four-stage probes expose the
missing new candidate independently of the contract representation.

## VC3 checkpoint — bounded MOVE effects and source expressions

SP 2.58 adds MOVE_TARGETS: known data reads and receiving MAY effects, no MUST,
exposure proof or known runtime result. Missing receiving bindings retain ALL.
CURRENT-DATE and RETURN-CODE are runtime input/output, never fabricated names.
SP 2.59 adds NOMINAL_TEXT_SOURCE_V3 expression trees; qualified-source 1.3 carries
them unchanged. Dependencies remains 2.7 with the versioned nested certificate.
V1/V2 leaf wire shapes are unchanged. Typed ports, JSON adapters and independent
schema checks reject unknown operators, bad arity, foreign reads and downgrades.

TRIM has typed LEADING/TRAILING modifiers in the AST. Previously those valid
forms produced two parser errors and lost subsequent statements. New witnesses
require zero parser errors and preserve the following CALL. Uppercase is bounded
to ASCII; non-ASCII and unsupported functions retain uncertainty. Transforms run
before receiving fit and preserve transitive supports and modelAssumed. Source
conditional candidates do not grant executable AIR values or synthetic kills.

Focused frontend tests passed (14: six effects, five receiver sequences, three
expression tests). Four-stage probes recovered PROGB001 for TRIM, UPPER-CASE,
and nested transforms. TRIM TRAILING deliberately does not remove a leading
space; after fitting the witness does not gain PROGB001. Strict independent
qualified-source 1.3 checks passed all three direction/nesting products.

Validation records: vc3-direction-front-01.log, vc3-expression-probes-02,
vc3-direction-green. The first lower FAST exposed a future-version oracle using
2.58 as unsupported; it now tests known 2.58/2.59 and unsupported 2.60. CFG FAST
exposed the corresponding 1.3 future-version assumption (now 1.4) and a compiled
public API inventory delta. The refreshed inventory contains the intended Term
arguments API and no forbidden dependency. Rerun results are recorded separately.

One VC1 overlap adversary used the reserved word SAME and was invalid COBOL. It
now uses SRC-ALIAS and asserts valid grammar before publication; the corrected
overlap test passes. This fixes test evidence, not the production overlap rule.

The 73-program VC1 CardDemo replay passed all four stages with no candidate loss,
no support loss/change and no additions versus W8. This evidence predates VC3;
final campaign-wide qualification remains pending.

VC3 validation update: frontend FAST passed 662 tests, zero failures/skips
(vc3-front-fast-02.log). Its first run caught a real dynamic-slice invariant
regression; MOVE_TARGETS is now restricted to whole receiving references, keeping
the pre-existing conservative path for indexed/refmodified receivers. The JSON
writer now uses closed typed transport records, as required by the architecture
oracle. Existing expected values were preserved. Lower fast focal suites passed
(vc3-lower-focal-03.log), including new MOVE proof forgery and expression wire
round trips. CFG architecture FAST passed after refreshing the reviewed compiled
API inventory (vc3-expression-cfg-fast-03.log). Final full/corpus and pin gates
remain campaign obligations.

## Source table text — SP 2.60 / qualified source 1.4

NOMINAL_TEXT_SOURCE_V4 adds `tableFields`. Each field has a real storage node
identity and initializer values linked to real declaration origins. The frontend
uses typed DISPLAY character extents, fixed OCCURS bounds and proved REDEFINES
components. Numeric DISPLAY columns participate in offsets; their contents are
not program names. No physical memory capability is asserted.

This is an index-insensitive summary of each textual field and its overlapping
textual aliases. Valid constant indices and unknown indices admit the field's
possible occurrences, with an open remainder. An invalid constant index does not
publish a query. Every update to a summary is weak; conditions over the summary
cannot eliminate elements. Copying a table value preserves the predecessor
snapshot, initializer/assignment supports and index uncertainty. Model VALUEs
cannot initialize this analysis or grant a kill proof.

Only typed literal group writes are sliced in the producer. Unsupported partial
writes, ODO, non-DISPLAY geometry and unproved overlays remain unknown. A bounded
summary can retain obsolete names; it cannot certify an occurrence-specific kill.
No lower or CFG code reads declaration syntax, member names or statement text.

The wire uses a closed V4 variant; old nominal authorities reject table fields
and CHOICE terms. Source evidence 1.4 is required, preserving older envelopes and
requiring full declaration provenance. These facts do not change AIR or CFG.

IBM authorities: [OCCURS](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=entry-occurs-clause),
[subscripting](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=table-subscripting),
[REDEFINES](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=entry-redefines-clause).


VC2 checkpoint evidence: 73/73 CardDemo programs completed all four stages
(vc2-carddemo-02). Preservation audit: zero lost candidates, zero support
losses/changes, 42 new menu candidates across the five indexed occurrences.
The separate COSGN00C addition in COPAUS0C predates VC2 and is explained by its
existing literal MOVE at source line 669 becoming visible after bounded MOVE
effect admission; control topology, storage and nominal facts were unchanged.
The raw support traces this candidate to the existing AIR value producer.

Ten table probes passed exact candidate and strict source-wire oracles, including
before/after writes, dead writes, cross-root isolation, nested OCCURS, aliases and
the uninitialized occurrence. Every result retains valueRemainder. Seven focused
frontend tests include an alias sharing synthetic model geometry. Five real SPs
pass lower round trips, identity/version forgeries and byte-equal AIR with source
facts removed. Four new source evaluator adversaries pass; CFG architecture FAST
passed 650 tests. Frontend FAST passed 672 tests before the final model-propagation
adversary; focused tests were rerun afterward. Lower FAST focal suites pass.
Compiled API inventories were refreshed only for the typed table records/codec.
Final campaign qualification remains pending, including CICS/ENTRY changes.
