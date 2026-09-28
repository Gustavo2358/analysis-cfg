# FILE composite control — qualification

> Registro histórico de qualificação, preservado. O trabalho deste repositório está DONE / MERGED; consulte o [fechamento da integração](carddemo-control-integration.md). Os estados de Draft/parada/sem merge abaixo descrevem o checkpoint original.


Status: implemented and qualified locally; awaiting review; not merged.
Scope and contract: [FILE composite control](file-composite-control.md).

## Result and cause

SP2.51 publishes intra-statement FILE points. OPEN/CLOSE success visits every
operand in source order before the following statement. SORT/MERGE without
procedure callbacks preserve input/work/output phases and unknown order/count
inside aggregate phases. Lower binds these facts in the active context; AIR and
CFG retain their existing contracts. analysis-cfg production code is unchanged.

The prior frontend gave every use the completion of its whole statement. Lower
emitted multiple operations and consumed that completion for each one, bypassing
later operands. CFG correctly transported the AIR edges; its dependencies then
omitted the unreachable later sites. Changing CFG reachability or candidates would
have hidden the producer/consumer contract defect.

## Independent adversaries

- Source order: two and three files, reversed order, mixed/same OPEN modes, split
  statements, mixed split/combined OPEN and CLOSE, single-file control.
- Context: two PERFORM calls, nested PERFORM, IF alternatives, ordinary continuation.
- Effects: status on later operands; MUST overwrite eliminates a stale target,
  MAY write retains it; critical I/O exit stays open; USE callback stays unknown.
- State: CICS handler registration survives composite FILE control.
- Aggregates: SORT/MERGE participants, local SD, phase boundaries, open repetition,
  and unsupported procedure callbacks without a fabricated return.
- Authority: permuted statement/use/point inventories preserve AIR; changing legacy
  phase order does not replace topology. Missing/duplicate/foreign/orphan points,
  ordinal and success-target contradictions reject through wire and typed ports.
- Structural-only ports keep control without fabricated memory facts. An absent
  unavailable event continuation is accepted; a contradictory supplied one rejects.

The 22 source witnesses run the four production CLIs. Their oracle joins complete
identities, independently traverses CFG, checks all ordinary traces and compares
every explicit AIR destination to CFG. It also checks declaration bindings,
candidates and source supports. Frozen SP inputs keep the consumer FAST independent
of a runtime frontend. The original witness was RED against the prior producers.

## Validation

| Check | Result |
| --- | --- |
| Frontend FAST | PASS, 623 tests, zero skipped |
| Frontend qualification-local | PASS, 1,197 Maven tests, zero failures/errors, one preexisting conditional skip; source normalizer full and naming passed |
| Lower focused FILE suite | PASS, 195 assertions, including qualified-source traversal and AIR codec |
| Lower FAST | PASS, including 195 composite FILE assertions |
| Lower semantic + performance | PASS; existing markers 244,391 semantic checks and 39,207 performance checks; 10,000-MOVE deterministic capacity probe |
| analysis-cfg FAST | PASS |
| Real source FILE E2E | PASS, 22/22 |
| Final production replay | PASS, 560/560 four-stage executions; zero unexplained deltas or preservation losses |

The Maven skip is `SemanticConditionContextDiscoveryTest`, gated by the existing
`semantic.condition.required` system property. It is not a skipped FILE regression.
The lower `qualification-local` wrapper is not repeated: its semantic/performance
profile and the architecture check from FAST cover the same stable production tree.
Full analysis-cfg qualification was not run: its production code is unchanged;
FAST, the new real-producer E2E and the complete regression replay exercise the
changed boundary. No UI tests or bundle regeneration were needed for this producer fix.

## Regressions and evidence

All 560 sources completed frontend -> AIR/qualified source -> CFG -> dependencies
again on the stabilized runtime. The existing suite oracles were executed again:
PERFORM 39/39, Chaos 48/48, aliases 14/14 and PERFORM adversarial 25/25. The other
inputs are all 73 CardDemo programs, 331 existing frontend fixtures and 30 CICS
regressions. Existing fixture/input gaps remain partial; exit zero is not a claim
of complete source analysis.

556 cases have byte-identical SP, AIR, CFG and dependencies relative to the baseline.
Every prior candidate/support/written provenance survives. The four deltas are:

| Source | Explained delta |
| --- | --- |
| `cobol/resolution/file-binding.cbl` | Later OPEN becomes reachable; SELECTDD candidate and support restored |
| `cobol/semantic/statements.cbl` | SP publishes previously unrepresented internal FILE control; AIR is equal after replacing only the publication namespace; no dependency candidate change |
| `app/cbl/CBSTM03A.CBL` | Later OPEN HTML-FILE becomes reachable; HTMLFILE candidate/support restored |
| Unikix `CBSTM03A.cbl` | Same restored OPEN HTMLFILE |

No unexplained regression, candidate loss or support/provenance loss remains. The
other 71 CardDemo programs are byte-identical in all four products. In the original
multi-open witness all four sites are reachable with CLIENTDD/OTHERDD and the
ordinary trace OPEN F -> OPEN G -> CLOSE F -> CLOSE G -> GOBACK. Its 29 nodes and
32 transitions are observations, not the test oracle.

New evidence: 560 four-stage runs and refreshed suite oracles, 22 source adversaries,
producer/consumer FAST, frontend full and lower semantic/performance. Reused evidence:
the immutable baseline products used only as a before/after reference; the lower
FAST architecture check is reused for the same unchanged code in semantic/performance.
Historical products were never edited. A source-tree hash proves the frozen runtime
matches the final production sources; later commits change documentation/pins only.
Raw runs, commands, hashes and the before/after dependency audit are preserved under
workspace `.file-composite-flow/evidence/`. The consumer repository versions the
compact summary as `docs/work/file-composite-evidence.json`. Baseline
mains: frontend `313236603815cd8c4d7299ae366310a4292bc5ee`, lower
`da3325caa059a4beb7bd01f1fee262f9844725f2`, analysis-cfg
`50453b80f2cdac3239b7b77944c3546cdb8fc621`.
AIR runtime `59df1f7d6f3523b21b172a3ea4b5a0dc95128faa` has identical production
sources to the existing consumer pin `d760b07b0fac42a106d09342ee9d5b8445ccf630`;
AIR semantics remain `2c7f31f19efbe3211a2aea5bbda90173a9666fe2`.

Development checks caught and corrected an overly strict SUCCESS-event check for
structural-only publications, the second topology traversal in qualified-source
projection, and empty new inventories leaking into the compilation serializer.
Future-version rejection probes now use 2.52 because 2.51 is admitted; they retain
the unsupported-version requirement. Existing expected results were not weakened.

## Limits and review

PARTIAL coverage, unavailable storage and open critical-error remainders remain.
SORT/MERGE aggregate order/count is an overapproximation; USE and SORT procedure
callbacks remain unsupported. Synthetic IBM models gain no physical or kill proof.
Historical SPs keep their published interpretation. Reanalyze source with the new
frontend and lower to obtain the corrected flow; old bundles are not retroactively
repaired. No ALTER, UI changes, solver changes or merge.

Review/integration order: frontend -> lower (repin merged frontend) -> analysis-cfg
(repin both actual merge SHAs), followed by the final integration gate.

## Reproduce the new source E2E

In analysis-cfg, use the existing `prepare_w2d_producers.py --work <new-dir>`
with the current lock, generate `analysis-launcher/target/runtime-classpath.txt`
using the existing Maven dependency goal, then run:

```sh
python3 scripts/project/e2e_file_composite.py \
  --producers <new-dir>/producers.json --work <new-output-dir>
```

The runtime form `--runtime <frozen-runtime.json>` supports archived local builds;
it checks hashes before and after execution. Outputs are never overwritten.
