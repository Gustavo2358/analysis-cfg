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
