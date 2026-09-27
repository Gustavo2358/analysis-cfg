# CICS control completion — qualification

The D1–D5 implementation is ready for review. Product work stays in isolated
`feat/cics-control-completion` worktrees, with separate checkpoint commits.
No merge is part of this campaign. Exact consumer pins are in sources.lock.json.

## Results

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
