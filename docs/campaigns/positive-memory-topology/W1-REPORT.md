# POSITIVE_MEMORY_TOPOLOGY — W1

Status: READY_FOR_REVIEW — W1 implementation and required local gates complete;
review lifecycle remains IN_PROGRESS until human review/merge. Exact-head remote
closure checks are linked in PR #45. W2/W3 not started.
[Plan](W1-PLAN.md); accepted historical [W0-R1](W0-DISCOVERY.md).

## Rule delivered and operational boundary

The executable supported projection determines memory, values and control. Missing
source features remain coverage, without local/global substitute effects. Source
coverage does not imply equivalence with full COBOL; coverage growth can increase
or decrease candidates. Known AIR effects, aliases, choices and input uncertainty
remain semantic. Invalid AIR remains invalid.

LOGICAL_ONLY remains the default in CLI/APIs. No logical→physical fallback was
added. `--experimental-physical` and `StorageAnalysisMode.EXPERIMENTAL_PHYSICAL`
are the existing explicit opt-in. Physical remains EXPERIMENTAL / NOT PRODUCTION
QUALIFIED; controlled synthetic tests are not operational authorization.

## Stack and contracts

| Repository | Base | W1 commit | Campaign PR |
|---|---|---|---|
| proleap-poc | edb64520a6269be9fa6d71cd47e6974112fbfece (#56) | cfcf0abf06a3b6186e157957bb301077fcb6f0bc | #58 DRAFT |
| cobol-lower | f8e181f95929c650181c989318f8ba23d1e68a1a (#32) | 6c0317ceb5e64c11f437e027a17dd55fba85df52 | #34 DRAFT |
| air-java | 646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa (#20) | 26016f10460336f237a33b2ed126a6a1427f0207 | #21 DRAFT |
| analysis-ir | 3fff18e2c16663a3f599207457caa1946d2e0945 (main/#7 merged) | b26465964fe75f944f6324df63330d69f33d77cd | #8 DRAFT |
| analysis-cfg | 98fa57c3db2edf9f70bb7a99bb667dbf36d28104 (#43); W0-R1 a2bd04b9d106c3abe0250edccc1aad7e9e386ca8 | implementation ae69dc9ab9b613aacbe8e6e1b6ffc4e49def7210 + scalar-output correction 7b0a25f30cc221c960e6de8f0ac0fedb0d9b18b4; closure is documentation only | #45 DRAFT |

Parent remote heads were unchanged at CP0 and reconfirmed on 2026-09-20 at CP4; isolated campaign worktrees were created
at these exact baselines. No reset/stash/force push or parent edits. Existing E2E
workspace was preserved. Dependency locks are updated only to immutable companion
commits; AIR pins the final IR. Final checks are recorded below, never inherited
from a previous SHA.

Normative changes: independent positive StorageId bases; redundant DisjointStorage;
producer causal A/B/C/D; omitted effects with coverage; coverage independent from
consumer calculation and known-slot integrity. Existing Nop gains JSON transport;
no new model variant/schema/version. AIR keeps I-58 malformed premise validation,
same-base initialization consistency, IDs/types/bounds/codecs; removes redundant
I-59 source separation obligation. Precision metadata cannot waive known signature
slot type validation. Existing codec goldens are preserved. Pre-release semantics
are migrated in place, without a legacy mode or client negotiation framework.

## Concrete partial representation

Versioned fixture `analysis-adapters/src/test/resources/positive-memory-topology/partial-group-copy/input.cbl`
contains SRC-GRP partitioned 3+5 (the second PIC X member also has an omitted
representation clause), DST-GRP partitioned 4+4 and PGM REDEFINES DST-GRP.
The producer uses supported character widths and concatenated group correspondence;
it does not pretend to implement the omitted clause's full physical behavior.

Default publication uses root Cells plus supported logical member slices. Physical
profile `ibm-enterprise-6.4-fixed-display-1047@1` publishes two eight-byte Regions,
member views and the destination overlay on the same base. MOVE to source, group
copy and later source overwrite become Assign→CopyBytes→Assign physically. The
computed CALL observes the destination BEFORE execution, after a prior external
CALL without a body contract. It returns PROGA001, not the later source OTHER999.
This is group correspondence independent of member names/partitions, not child copy.
No zero width, fabricated codec or numeric text conversion is introduced.

Generic preserved representation clauses no longer poison unit allocation/alias
facts. The known PIC/group projection and diagnostics coexist. Unrepresented
numeric/OCCURS/general binding cases remain a documented limit, not an invented
independent Cell scheme. Existing REDEFINES/RENAMES relationships and group snapshots
remain tested.

## Causal implementation map

| Mechanism | W1 status | Change or remaining scope |
|---|---|---|
| M01 partial declaration | PARTIALLY TREATED | known textual/group projection survives generic preserved clauses; unrelated wholly unrepresented types remain pending |
| M02 positive relations | TREATED in slice | groups/overlays/RENAMES retained, distinct IDs never substituted for known sharing |
| M03 missing separation proof | TREATED | no cross-base target scan or premise admission requirement, no generated disjoint matrix |
| M04 opaque missing summary/exposure | PENDING W2 | generic opaque summaries/helpers still need causal projection |
| M05 unimplemented transformation summary | PENDING W2 | general arithmetic/string producer envelopes unchanged |
| M06 unsupported MOVE conversion | TREATED in slice | omitted transform publishes coverage-bearing Nop, no HavocMust; multiple receivers retain distinct identities |
| M07 ACCEPT/input | PARTIALLY TREATED | legitimate B unknown controls preserved; general frontend read bounds pending |
| M08 IF | PENDING W2 | supported branch regression retained; unsupported evaluator fallback not implemented here |
| M09 EVALUATE | PENDING W2 | existing order/branches preserved, general predicate projection pending |
| M10 PERFORM | PENDING W2 | supported forms regression retained; no new loop abstraction |
| M11 ALTER/GO TO | PENDING W2 | no ALTER implementation or retarget reform in W1 |
| M12 GO TO DEPENDING | PENDING W2 | supported dispatch regression retained |
| M13 CALL/CICS | PARTIALLY TREATED | CALL body/signature absence no longer adds global reads/writes; known normal continuation closed; absent continuation and general CICS effects remain pending |
| M14 FILE read/return versus physical proof/profile | PENDING W2 | legitimate existing FILE/CICS effects preserved and regression tested |
| M15 CICS FILE option/length/materialization | PENDING W2 | generic option/length fallbacks remain; legitimate returned effects preserved |
| M16 Coverage/Precision | TREATED in slice | metadata no longer produces memory effects or control openness in regional preparation; known-slot validity independent of precision |
| M17 captured gaps | TREATED | no diagnostic sourceGap insertion into ByteImage, captures, Events or equivalence |
| M18 gating | PARTIALLY TREATED | scalar no longer demands negative disjoint proof; supported text preserved upstream; genuine unsupported operation/provider refusal remains |
| M19 remainders | TREATED in slice | source metadata excluded from effective value/name OR; omitted physical CICS area proof not required for supported text-name interpretation; true unknown policy/raw-name interpretation retained |
| M20 semantic unknown/integrity | PRESERVED | MUST kills, MAY retains alternatives; deliberate broad scopes/Choice/overlap and invalid AIR controls remain |
| M21 diagnostic-only channel | TREATED in slice | 0/1/50 object+operation diagnostics leave semantic facts/work unchanged, scalar and regional |

Global policy is decided; implementation is a vertical slice, not global completion.
Pending producer fallbacks above are not rebranded as legitimate semantics.

## Consumer transfer and presentation

StorageIndex uses identity plus same-base interval intersection. StatementEffects
prepares direct/scoped targets only. Strong-write authority still checks required
execution, exact destination, single selection and covered range. RD events retain
semantic uncertainty reasons from destinations and Havoc; operation-header coverage
is excluded from event identity. No diagnostic target enumeration remains in RD.

Regional prepare no longer turns object/operation metadata into targets, control
openness or source gaps. Apply/write/replacements, joins and observations use the
same engine; copies still capture prior state and retain correlated fragments.
No preventive Events rewrite was introduced. Observation separately reports source
coverage; it does not propagate a diagnostic through copied content. Scalar provider
keeps coverage presentation but no negative separation admission gate.

Values effective remainder now equals model remainder. CALL effective remainder adds
only genuine name interpretation remainder (unknown configured/runtime name policy,
uninterpretable raw name); sourceValueRemainder remains separately visible. CICS
supported text-name interpretation does not require a source physical area proof.
The four COBOL verticals publish PGMNAME/DYNAM/DLL=UNSPECIFIED in SP policy3.0.0.
Their remaining UnknownName denotes the open runtime naming parameter in the
published abstraction; it is not inferred from a representation diagnostic. Known
text values close while that naming-policy remainder remains independently visible.
This does not certify every historical producer UnknownName as legitimate; transport
of other configured source naming policies remains outside this slice/M13 audit.
Diagnostics remain in publication/output metadata. Operational/validation failures
are distinct from source coverage and never become a successful logical fallback.

## Oracles and RED/GREEN

| Test | New or existing | Direct protection |
|---|---|---|
| T1 | existing mode/cache tests + new metadata/default and physical-failure CLI tests | no opt-in, no physical work; failed opted-in read keeps destination unchanged |
| T2 | new frontend PositiveMemoryTopologyTest, lower PositiveTopologySuite, four versioned E2Es | declaration/group/siblings/transfers survive representation gap |
| T3 | migrated StorageIndex/StatementEffects + existing alias/range tests | distinct bases without premise; same-base overlap/adjacency retained |
| T4 | existing snapshot/correlation/partial overwrite + new differently partitioned group fixture | BEFORE captures, supported producers and no invented Cartesian alternatives |
| T5 | new PositiveMemoryTopologyTest 0/1/50 | full regional alternatives/captures/Events/supports and preparation/solve counts unchanged; scalar support/work unchanged |
| T6 | existing HavocMust/HavocMay/Choice/AllMemory tests + lower omitted MOVE tests | real B kills or weakens correctly; C emits no substitute write |
| T7 | call-before and partial-group-copy E2Es | second computed CALL remains productive after external CALL; D executes physical engine |
| T8 | AIR model/codec negatives, consumer malformed input, resource/control regressions | no integrity waiver; existing FILE/CICS/source resources preserved |

Observed REDs: 4 bases/5 writes produced 20 targets; regional diagnostics changed
copied fragments/Events; scalar metadata opened effective remainder; frontend
representation clauses erased supported views; lower CALL manufactured global reads;
AIR rejected independent initial bases and existing Nop codec, while open Precision
waived an invalid signature. These failures are recorded before the corresponding fixes.

Old assertions requiring cross-base compensation, copied source gaps or source OR
were migrated explicitly. Historical frozen output digests were replaced by independent
full typed expected records for the new positive model, not regenerated from the solver.
Standalone ByteImage/provenance hash and field-sensitivity controls remain. No golden
fixture or corpus output was edited to obtain PASS.

## A/B/C/D and evidence

A freezes 96 synthetic cases/338 source and auxiliary files, old classpath hashes and
raw outputs before edits. 95 publish; COPY cycle has the recorded entry failure.
Source family includes qualified logical group/overlay/RENAMES/snapshot/correlation,
COPYBOOK/DCLGEN/SQL_INCLUDE/DB2 and R1 control/CALL probes. No corporate qualification
claim is made. Final B reproduces 95 outputs and the same COPY-cycle failure. The
[per-case regression ledger](W1-REGRESSION.md) records 52 preserved outputs and 43
classified deltas: no candidate/support/timing loss or unexplained difference.
All 95 B outputs execute zero physical groups/writes. FILE semantic inventories and
COPYBOOK/DCLGEN/SQL_INCLUDE/DB2 source resources are preserved. Changes include 32
control remainders, 34 premise lists, five model remainders and four productive
admissions; exact per-field changes and producer mappings remain in the raw audit.

Manual review of the four republished producer bodies confirms more than origin
equality. Independent and scale10/50 cases replace the legacy padded nominal literal
with `fit8(fit8('OLDPGM'))` on the same PIC X(8) PGM; added ODD roots never enter its
MOVE operands. Family views are FAMILY[0,9), PGM[0,8), ODD[8,9), with FLAG independent.
The supported group write is `fit9(fit8('OLDPGM') ++ slice1(FAMILY,8))`, followed by
the supported PGM prefix and ODD suffix projections. Thus OLDPGM remains the prefix
and the sibling survives; the family is neither erased nor split into invented
independent storage. All four retain the original MOVE source support and BEFORE
CALL, while model remainder true → false and PARTIAL → COMPLETE reflect productive
admission. The frontend layout test and vertical result oracle independently
protect widths/ranges and transfers. Scale cases were separately inspected.

Final vertical B: all four default-source cases return the sustained computed
candidate with modelValueRemainder=false and zero physical work. C/D use byte-identical
physical AIR, not manually edited JSON. `positive_memory_e2e.py` checks the actual
wire `sourceValueRemainder`; absence fails instead of becoming false.

| Case | Producer profile | AIR SHA-256 | Requested/effective | Groups/writes | Computed candidates / model remainder | Status |
|---|---|---|---|---|---|---|
| B: call-before | UNSPECIFIED | `eed57f943cc2420374b5de8bf2bde3564fd5675ac2f2e10476a685c2ca8b0cfa` | LOGICAL_ONLY | 0/0 | OLDPGM / False | PASS |
| B: layout-independent | UNSPECIFIED | `459ac07398615226d38ec6de99c3c77c6ad7f4b7cd55d7eb773b4e058db6c342` | LOGICAL_ONLY | 0/0 | OLDPGM / False | PASS |
| B: layout-family | UNSPECIFIED | `cef454716357ff46d1e99214c92a5dd70798412c738902ba54accefc7b2868dd` | LOGICAL_ONLY | 0/0 | OLDPGM / False | PASS |
| B: partial-group-copy | UNSPECIFIED | `5252fb3618b8b8b3fadd382e8c1b5c368bb89223825aa2cf9f9bbcdb8c5c76a5` | LOGICAL_ONLY | 0/0 | PROGA001 / False | PASS |
| C: call-before | IBM fixed DISPLAY1047 | `95572b27c4c17665035ad4b32f63fe10fa65858d85f50ecbb1e73027d492345d` | LOGICAL_ONLY | 0/0 | OLDPGM / True | PASS |
| D: call-before | IBM fixed DISPLAY1047 | `95572b27c4c17665035ad4b32f63fe10fa65858d85f50ecbb1e73027d492345d` | EXPERIMENTAL_PHYSICAL | 1/1 | OLDPGM / False | PASS |
| C: layout-independent | IBM fixed DISPLAY1047 | `e45a3f508a5d6e8d853a1a7aa274cedee04fc9ffa60ab0d30ab34fe802138ef0` | LOGICAL_ONLY | 0/0 | OLDPGM / True | PASS |
| D: layout-independent | IBM fixed DISPLAY1047 | `e45a3f508a5d6e8d853a1a7aa274cedee04fc9ffa60ab0d30ab34fe802138ef0` | EXPERIMENTAL_PHYSICAL | 1/1 | OLDPGM / False | PASS |
| C: layout-family | IBM fixed DISPLAY1047 | `f4c34682621122d007af52b44e29b2feb61e65830175f514ddb89602a1f75b71` | LOGICAL_ONLY | 0/0 | none / True | PASS |
| D: layout-family | IBM fixed DISPLAY1047 | `f4c34682621122d007af52b44e29b2feb61e65830175f514ddb89602a1f75b71` | EXPERIMENTAL_PHYSICAL | 1/1 | OLDPGM / False | PASS |
| C: partial-group-copy | IBM fixed DISPLAY1047 | `8b3d398dc4192267e1b33a4a5e5befd9e67c0864bf38955dfa5edf02a774fb46` | LOGICAL_ONLY | 0/0 | none / True | PASS |
| D: partial-group-copy | IBM fixed DISPLAY1047 | `8b3d398dc4192267e1b33a4a5e5befd9e67c0864bf38955dfa5edf02a774fb46` | EXPERIMENTAL_PHYSICAL | 3/3 | PROGA001 / False | PASS |

In the group-copy witness C has no computed candidate; D's known result and actual
copy depend on regional work. B is a different logical AIR and remains productive.
The matrix records actual output, including C refusals/open values; requested and
effective modes agree. All productive vertical candidates carry BEFORE and producer
supports. Per-consumer pre/post hashes prove C/D did not modify their shared AIR.

Development attempts are retained honestly: initial runs lacked new AIR class dirs;
a concurrent lower rebuild interrupted one B case. Neither is final qualification.
Final execution uses 22 frozen classpath entries, individually hashed in runtime-final2.json.
AIR 00373→26016 changes harness/docs only; production source equivalence retains the
compiled runtime evidence. Lower 8adc→6c0317c is likewise a pin-only change. A later scalar output correction
replaces the negative all-object demand metric sentinel with the actual object count.
Only TextProfile.class changed; no transfer changed. Final frozen consumers were
re-executed over the unchanged W1 SP/AIR artifacts: 95 cohort + 4 logical vertical + 8 C/D + 6 CICS
outputs are byte-identical to their preceding run. The driver records reused producer
artifacts explicitly; the pre-replay runs remain intact.

Additional real source CICS regression uses the six existing analysis-gaps/w3
LINK/XCTL fixtures, executed through both baseline and final frozen runtimes. All
six preserve candidates, raw values, BEFORE timing and source supports. Four
interpretation true → false changes remain REVIEW in the generic classifier and are
explicitly accepted here under M19: the supported cics-ts.program@1 text profile
requires eight characters and valid spelling, not a physical IBM1047 area proof.
`link-unknown` still has no candidates and model/effective remainder=true. XCTL
still publishes no known normal return. Existing broad CICS foreign/control effects
remain unchanged and pending W2; these tests do not claim general CICS migration.
The other two cases need only the source/effective contract and redundant-premise
changes already described. Raw manual-review evidence: compare-cics-v1.json.

## Physical measurements

New neutral versioned PositiveMemoryCostProbe: Java 21.0.12.1, -Xmx2g, timeout 120s,
one warmup and three fresh samples, explicit experimental physical API, no premises.
Separate phases include index/prepare, solve and replay. Full typed facts are checked.

| Bases/writes | Targets/events | Prep / solve / replay median ms | Historical compact Events positions |
|---|---:|---:|---:|
| 4/5 | 5/5 | 4.323 / 1.973 / 3.950 | 0 |
| 16/50 | 50/50 | 7.494 / 7.012 / 10.570 | 0 |
| 32/100 | 100/100 | 7.815 / 9.954 / 15.297 | 0 |

Bases retained: 4/16/32. Physical groups/writes: 5/50/100. Unique producer IDs: 5/50/100;
unique compact Events objects: 0. Interner nodes after solve 6/51/101 and after replay
9/66/132 remain legitimate work. These counts are not memory bytes. Raw invocation,
phase samples, full metrics and typed facts are in ignored evidence/w1/metrics.
The W0 3200→100 targets and 159650→0 positions are historical disjoint-premise controls;
this new run removes the premise requirement itself. No corporate performance claim,
constant total-time claim or proof that legitimate combinations/copies become cheap.

## Gates, limitations and handoff

Frontend FAST: 389 tests, zero failures/skips; additional Maven: 983 tests, zero failures, one conditional skip. Its wider
qualification did not complete: a later corpus normalizer/ownership stage failed.
No validator was relaxed and no corpus source/output was modified. Further corpus
execution was stopped; this gate remains FAIL. The owner failure is
**proved pre-existing by a focal A/B witness** at frontend baseline `edb64520`
and W1 `cfcf0abf`: both fail at the same source fact, owner containment check,
and Semantic Product stage, with byte-identical preprocessing/AST output.
The [focal reconciliation](W1-SOURCE-DEPENDENCY-OWNER-RECONCILIATION.md)
records commands, environment, hashes and the unchanged harness. Frontend AGENTS.md explicitly makes full
local/on-demand, never a closure requirement; the user also excludes unauthorized
corporate execution. Required synthetic W1/FAST evidence is complete; broader corpus
qualification remains pending. Later normalizer checks were not reached in that run.
AIR final FAST: 188 model + 129 codec, 41 harness + 12 lean and architecture passed;
qualification-local Maven clean verify also passed. Lower final FAST passed in
201.661s, and qualification-local semantic/performance/architecture passed at 6c0317c.
Anchor full Maven reactor passed 673 tests, zero failures/errors/skips; independent
wire checks passed; architecture inventory changes are limited to two
removed diagnostic records and 67 removed dependency edges, with no new API/Maven
dependency. Final aggregate qualification-local passed, including semantic/performance/integration,
W5 source file/memory equivalence, W2D, MOVE, PERFORM, multi-CALL and partial-program
E2Es. These historical harness names do not authorize campaign W2/W3. Final local FAST passed 605 fixed test methods with zero failures/errors/skips,
architecture, wire and harness checks in 92.848s. CP4 below records remote evidence.

A stale AIR harness literal rejected the synchronized normative pin at 00373; 26016
replaces it with strict comparison to the active immutable lock, with a RED/GREEN
test that also rejects missing/moving pins. Both remote checks pass at 26016.
Local cache/DNS failures and superseded-oracle failures remain in the raw logs;
none is hidden as PASS. The scalar wire reader now enforces effective=model. Its historical snapshot stays
unchanged; the reader test explicitly constructs the new single-PROGA oracle by
changing only effective=false and effectiveOpenResults=0, and rejects the old coupling.
The new all-object counter assertion rejects the negative sentinel. W5 real-source
file/memory equivalence passed after both corrections.
Full E2E wrappers are reconciled to the active producer
pins and supported contracts, rather than building historical lower revisions
or requiring the removed disjoint premise/foreign compensation.

W2 is the focused producer control/opaque/helper projection; W3 is remaining scale,
coverage qualification and measured legitimate work, not a new solver. Neither started.
No merges, auto-merge, ready-for-review or physical default/operational changes.

A feature não modelada permanece na cobertura, não ganha efeitos de pior caso.
As partes modeladas continuam produtivas nos dois caminhos.
O lógico continua sendo o produto padrão; o físico é qualificado em laboratório
por opt-in, sem desinterdição automática.

## CP4 review handoff

Implementation commits are ae69dc9 and 7b0a25f; the final closure commit changes
only this report, plan/navigation and evidence indexing. All five campaign worktrees
were checked against their expected ancestors and published heads; no concurrent
commits were discarded. Parent PRs #56/#32/#20/#43 remain open at the baseline SHAs,
and analysis-ir main remains 3fff18e. All campaign PRs remain DRAFT.

| Repository | Final local evidence | Remote evidence at recorded code HEAD |
|---|---|---|
| proleap-poc cfcf0ab | FAST PASS; wider qualification FAIL as limited above | [Fast CI PASS](https://github.com/Gustavo2358/proleap-poc/actions/runs/35520926565) |
| cobol-lower 6c0317c | FAST and qualification-local PASS | [Fast CI PASS](https://github.com/Gustavo2358/cobol-lower/actions/runs/35522322213) |
| air-java 26016f1 | FAST and qualification-local PASS | [Fast CI PASS](https://github.com/Gustavo2358/air-java/actions/runs/35522064586) |
| analysis-ir b264659 | normative review and pinned AIR validation | no configured remote checks; not reported as an executed PASS |
| analysis-cfg 7b0a25f | FAST 605 methods / 92.848s and qualification-local PASS | [code Fast CI PASS](https://github.com/Gustavo2358/analysis-cfg/actions/runs/35523587244) |

Both push/PR checks succeeded at each code HEAD where configured. The closing
report commit has its exact-head check receipt in PR #45; confirm that receipt
when reviewing the final HEAD. A docs-only remote pass does not replace the code execution
above. Required gates are not replaced by the optional failed frontend corpus run.

Stop for human review after publication. W2/W3 require new authorization; do not
merge, mark ready, change operational mode or retry corporate qualification. Review
can begin with the partial-group-copy C/D witness, scalar/regional isolation tests,
M01–M21 scope table and per-case regression ledger. Raw logs and phase artifacts
remain in ignored evidence/w1; the ledger hashes them. No corpus data is versioned.

## Reproduction and evidence locations

From the anchor worktree, use Java21 and the exact locked companion builds. The
versioned driver accepts a JSON runtime with `commands` (frontend/lower/cfg/dependency
JVM argv, classpath and main class) and `frontendCheckout`. The ignored final runtime
also records each frozen classpath entry's original path, file count and tree hash.

```sh
python3 -B scripts/project/positive_memory_e2e.py --runtime ../evidence/w1/runtime-final2.json --cohort ../evidence/w1/cohort --out ../evidence/w1/B-final2
python3 -B scripts/project/positive_memory_e2e.py --runtime ../evidence/w1/runtime-final2.json --cohort analysis-adapters/src/test/resources/positive-memory-topology --out ../evidence/w1/B-vertical-final2
python3 -B scripts/project/positive_memory_e2e.py --runtime ../evidence/w1/runtime-final2.json --cohort analysis-adapters/src/test/resources/positive-memory-topology --out ../evidence/w1/CD-final2 --physical
```

The physical driver adds `--storage-profile ibm-enterprise-6.4-fixed-display-1047@1`
to ExplorerMain; it runs AnalysisDependencies first without flags, then on the same
file with `--experimental-physical`. These are different producer/consumer switches.
Call `verify_vertical(output_directory, physical)` in that versioned module to assert
mode, work, result, support and BEFORE. Outputs must be fresh directories; the driver
never overwrites evidence. Every phase has a 120s external timeout and JVM -Xmx2g.

Metrics use the versioned `PositiveMemoryCostProbe` test main with the compiled
analysis-values test/main, analysis-kernel, cfg-kernel and locked AIR classpath.
`metrics/invocation.json` records exact JVM/argv/heap/timeout/warmup/repetitions;
`measurements.json` records samples and `facts.typed` full independent typed facts.
No production class was replaced, and no hidden semantic flag was introduced.

For the final incremental consumer check, `--replay-from` reuses exact producer
artifacts, verifies their AIR hash and executes both C/D consumers where present:

```sh
python3 -B scripts/project/positive_memory_e2e.py --runtime ../evidence/w1/runtime-final2.json --replay-from ../evidence/w1/B-final --out ../evidence/w1/B-final2
python3 -B scripts/project/positive_memory_compare.py --before ../evidence/w1/A --after ../evidence/w1/B-final2 --out ../evidence/w1/compare-final3.json --self-check
```

The historical full pipeline captures remain indexed alongside the final replays.
