# CICS control completion — qualification

The D1–D5 implementation is ready for review. Product work stays in isolated
`feat/cics-control-completion` worktrees, with separate checkpoint commits.
No merge is part of this campaign. Exact consumer pins are in sources.lock.json.

## Current qualification — historical failures repaired

The historical blockers below have been investigated and repaired. Two production
defects were found: retained DLI operand provenance was lost during framing/COPY
replacement, and a CALL target disappeared from executable AIR when topology
completion was unknown. The first is fixed in the frontend SourceMap/preprocessor;
the second reuses the lower's existing Invoke translator with an empty open local
frontier. A negative test forbids executing the CALL again at a post-call boundary.
No CFG solver, AIR contract, synthetic confidence or kill authority was changed.

| Current check | Result |
| --- | --- |
| Frontend full local | PASS; 1192 Java tests, zero failures/errors, one existing opt-in discovery test skipped; artifact and naming gates PASS |
| Frontend FAST | PASS; 618 Java tests, zero failures/errors/skips |
| Lower full local | PASS after CALL payload repair; final narrowing guard covered by FAST and the complete replay below |
| Lower final FAST | PASS, including computed/literal frontiers, no duplicate CALL and permutation adversaries |
| CFG FAST | PASS; version/predicate/coverage mutation tests also PASS |
| CFG full phases | All phases passed through resumption: architecture, semantic, performance, W5 integration, W2D, MOVE-data, PERFORM-basic, multi-CALL and partial-program |
| Partial-program E2E | 22/22; A/B products and sequence permutation checked for every fixture |
| Final W5 producer integration | PASS; CP4E twice, CP3 and generic overwrite, memory/file equivalence |
| Final corpus and suite replay | 560/560: frontend fixtures 331, CardDemo 73, PERFORM 39, Chaos 48, aliases 14, PERFORM adversaries 25, CICS adversaries 30 |

The CFG full command's original failed log is preserved. Successful architecture,
semantic, performance and W5 test phases were reused because CFG production code
did not change during this repair. Its previously blocked E2E stages were executed
with the final producer code, and W5 source integration was repeated. Partial cases
were run individually through the unchanged gate entrypoint to collect every
failure; all 22 now pass. This is a completed set of full phases, not a claim that
the original monolithic command exited zero.

Lower full passed before the final payload guard was narrowed to source occurrence
frontiers. Final FAST covers that guard and its RED/GREEN counterexample; the full
performance/architecture evidence is reused because no algorithm or boundary changed.
The final lowering replay covers every corpus input. Documentation/pin commits after
these runs do not change production code.

### Regressions and exact deltas

The frontend correction was run through all four stages for 331 fixtures and all
73 CardDemo programs. Only four CardDemo SPs changed, correcting DLI coordinates;
normalized control/data products and all prior dependency/support/provenance facts
were preserved. The other 69 CardDemo SPs and all 331 fixture SPs were byte-identical.
All 156 suite frontends were also replayed with identical SP bytes.

After the final lower correction, all 560 products were replayed again. Bundles,
AIR and qualified source are byte-identical in 559/560 cases. The only delta is
`partial-program/call-handlers.cbl`: one executable CALL site is restored with
PROGA, without a fabricated continuation. No previous candidate, support or written
provenance is lost. CFG/dependencies were rerun for this delta. For equal products,
downstream evidence is reused only after byte equality and consumer bytecode
identity checks. All 73 CardDemo products and all PERFORM/Chaos/alias/CICS suite
products remain identical at this final step. No unexplained regression remains.

### Gate corrections and authority

The obsolete SQLCA, missing-COPY, DLI-host and inline-PERFORM characterizations now
assert current typed facts, including negative cases. HTML artifact checks retain
incomplete coverage and model provenance. Naming checks allow only the exact
repository identity in documentary JSON; product/source naming restrictions remain.

The W2D version ceiling comes from the exact source lock, with independent feature
floors and malformed/future-version rejection. Typed text predicates, declaration
coverage identities, Assign plus completion Jump, retained copy supports and
context-specific PERFORM are checked directly. Literal truncation now requires its
exact raw value and overriding producer. This corrects an obsolete no-kill oracle
only where a complete typed write proves the kill. Source inventory beyond unknown
control is preserved without requiring an invented executable occurrence.

Raw local evidence is preserved in `.cics-completion/evidence/historical-repair/`:
`frontend-full-green.log`, `frontend-fast.log`, `lower-full-final.log`,
`lower-fast-release.log`, `cfg-full.log`, `cfg-fast-final.log`, `*-release.log`,
`partial-all/results.json`, `release-audit.json`, `product-comparison.json` and
`support-preservation.json`. Compiler-version mismatch and a concurrent rebuild
interruption have separate failed attempt logs; neither is reported as a semantic
failure or a passing gate. Review details are in
[historical repair](historical-qualification-repair.md).

## Initial D1–D5 qualification (historical, superseded above)

| Qualification | Result |
| --- | --- |
| CICS completion adversaries | 30/30, four production stages |
| PERFORM | 39/39 |
| Chaos | 48/48 |
| Logical aliases | 14/14 |
| PERFORM adversaries | 25/25 |
| CardDemo (all programs, including archive variants) | 73/73 |
| Frontend fixture matrix | 331/331; 310 unchanged historical inputs plus 21 additional fixtures |
| Frontend FAST | 614 tests, zero failures/errors/skips |
| Lower FAST | PASS after the final payload correction |
| CFG FAST | PASS with the new projection, wire and architecture inventories |
| CFG full local | Architecture, semantic, performance and W5 integration PASS; historical W2D version guard fails before its downstream stages |
| Lower full local (D5, before the final payload correction) | PASS: 205184 semantic checks; performance run 244391 checks, including 39207 performance checks |
| Frontend full local | 1189 tests: 6 failures, 1 error, 1 skip; all seven failing cases independently reproduced on baseline main |

The final lower correction is covered by final FAST, its RED/GREEN adversary and
complete consumer revalidation. It restores an existing typed payload translator;
performance evidence for the handler-state algorithm is reused from D5.

The frontend full failures are historical characterization expectations in
AstSemanticBoundaryCharacterizationTest, ExecDliProvenanceTest,
SemanticModelBaselineCharacterizationTest and SemanticProductStatementInventoryTest.
The isolated baseline run executes their 23 tests and reproduces the same six
failures and one error. They concern old SQLCA inventory/data hierarchy, DLI host
operands and legacy PERFORM statement inventory. They were not changed here.
Full qualification is therefore not globally green.

The CFG full run reaches W2D, whose script still requires the producer pin to be
SP2.38. Baseline main already pins SP2.49. Executing the main guard with its own
lock and the preserved historical SP2.47 fixture reproduces the identical failure.
The historical W2D/move/basic-PERFORM/multi-call/partial E2Es after that guard were
not completed by the full command. Current four-stage suites and the exhaustive
corpus runs above are separate evidence; they are not reported as a green full.
This unrelated historical guard was not relaxed. See w2d-baseline-guard/result.json.

## Corpus audit and the final correction

All 73 CardDemo programs preserve their program candidates. No previous candidate
or support was lost, including FILE supports and the written source provenance
behind each support. Conditional evidence and assumptions are preserved. Source
occurrences and operand provenance are identical; three programs only renumber
qualification-node references as their source topology changes.

39 dependency products are fully equivalent after identity normalization; 34 have
expected executable-site/context/reachability changes. Twelve have FILE-site
changes; six gain computed FILE candidates (three public programs and their
archive variants). No source dependency disappears.

The fixture matrix found one real intermediate regression: typed XCTL payload
preservation depended on occurrence deduplication, disabled for handler-state
specialization. The correction separates payload qualification from context
sharing. A new adversary first failed, then passed, requiring the original target
support while forbidding local continuation and unqualified handler dispatch.
The historical 310 inputs retain every previous candidate and support after it.

After this last production change, the lower consumer was reexecuted for all 331
fixtures, all 73 CardDemo programs and the 155 existing suite products. Bundles,
AIR and qualified source were byte-identical for 330/331, 73/73 and 155/155
respectively. Only the diagnosed XCTL fixture changed; CFG/dependencies were rerun
and its original CICS_LITERAL support was recovered. Downstream evidence was
reused only for identical inputs and unchanged consumer bytecode. A jar-entry
comparison proves TopologyProgramAssembler.class is the sole changed runtime entry.
The new 30th CICS adversary also traversed all four stages on the corrected runtime.

The independent evidence schema/oracle was stale even against the merged IBM
baseline. Its closed V1/V2 symbol shapes, modelAssumed and synthetic no-kill premise
now match the existing Java producer and the new MODEL_STORAGE input kind. Negative
checks still reject unknown authorities, lost confidence, malformed flags and
unjustified model premises. No corpus semantic expectation was weakened.

## COACTUPC

| Measure | IBM baseline | D1–D5 |
| --- | ---: | ---: |
| Nodes | 3048 | 5037 |
| Transitions | 3887 | 6293 |
| Weakly connected components | 5 | 1 |
| Reachable nodes | 966 | 5037 |
| Unreachable nodes | 2082 | 0 |
| Reachable executable FILE sites | 0/7 | 14/14 |
| Reachable executable program sites | 0/4 | 8/8 |

The new site counts include distinct handler-state contexts. The original source
occurrence inventory is preserved. COMEN01C and CSUTLDTC remain candidates;
ACCTDAT, CUSTDAT and CXACAIX gain executable FILE support. Graph connectivity is a
consequence of admitted memory/control proofs, not a numeric acceptance target.

## Limits retained

- Synthetic layout, initial bytes, disjointness, runtime values, branch pruning and
  kill proofs remain unavailable. Real independent records can regain local proof.
- RETURN supports the documented plain/TRANSID/COMMAREA/LENGTH/IMMEDIATE subset and
  response options. Other options retain explicit gaps.
- Executable exceptional selection uses the existing explicit ABEND and guarded
  XCTL PGMIDERR inventory. Other default-condition events, handler PROGRAM dispatch
  and restoration of an interrupted PERFORM stack remain unavailable.
- Unknown effects are conservative MAY effects. Unknown local handlers remain open
  frontiers. The source certificate remains independently available.
- Diverge remains outside the CFG known subset. CFG JSON v4 represents explicit
  exceptional edges and outside outcomes; old products keep v1/v2/v3.
- The UI/importer is unchanged; compatibility with SP2.50/CFG4 is separate work.
- The discovery's separate computed-FILE source fallback gap and ALTER are excluded.

Raw logs, immutable runtimes, baseline reproduction and all outputs remain in the
workspace `.cics-completion/evidence/`. Useful audits: preservation-audit.json,
fixture-preservation-reviewed.json, source-occurrence-audit.json,
coactupc-metrics.json, consumer-revalidation/, carddemo-consumer-revalidation/ and
suite-consumer-revalidation/. The initial failed attempts are retained separately.
