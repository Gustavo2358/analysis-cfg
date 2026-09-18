# Analysis gaps campaign

## Dependency on PR #40

STACKED ON PR #40. Temporary parent/base: `discovery/regional-explosion-fixtures`.
Parent validated SHA / G0_BASELINE_SHA: `f0efa4a76984781e09c26b92d4f6ee9cb2591f8f`.
New branch: `discovery/analysis-gaps`; isolated worktree: `.analysis-gaps/analysis-cfg`.
Draft PR will target the parent, never main while #40 is open. No auto-merge.
After #40 merges: incorporate its final head, rebase/update from main and retarget.
If W4 adds only documentation/evidence, continue normally. If W4 adds production
changes, incorporate the FINAL #40 head before closing dependent implementation.
No synchronization is performed by G0/W1. Corporate E2E is not run.

## G0

FACT: local parent ref and remote-tracking ref both identify the exact requested
commit; isolated branch was created directly from it. Original checkout is clean
`main`, HEAD `194fac2af6cfc54053318164275e682feac5c750`, 47 behind its cached upstream.
All 12 pre-existing live worktrees were clean; none was reset/rebased/modified.
One stale `/tmp/w5-inventory-repro/fix` entry is prunable and was left untouched.
Only unpublished branch commit reported against cached remote refs was
`1e12b45` (unrelated frozen investigation); this is a local observation, not a
fresh remote audit. Detailed paths/heads/status: local `.harness-results/g0-hygiene.json`.
Initial GitHub read failed due sandbox network; publication will be retried.

Inherited focal baseline, Java 21.0.12, new clean tree at parent SHA:

```sh
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-values -am \
  -Dtest=RegionalExplosionFixturesTest,RegionalAlternativesTest,RegionalValuesTest,StorageIndexTest,CfgBuildCoordinatorTest test
```

PASS: 39 tests, zero failures/errors/skips. Raw log: `.harness-results/g0-final.log`.
Two earlier commands stopped on reactor modules with no selected test; neither
was a semantic failure or reported as PASS. The final selection includes the
required per-module smokes without weakening the harness.
Build dependencies were copied into the new ignored build directory; AIR source
is pinned to `135d91f4d643c80eeb5d7bff9081fae229e9e62c`; no sibling build was mutated.

G0 COMPLETE — STACKED CAMPAIGN FOUNDATION READY.

## W1 — VALUE/MOVE/storage → CALL

Result C: simple path already supported; first adjacent gap is inline target-expression query admission (G4).
W2 NOT STARTED. W3 NOT STARTED.

### Pipeline map (discovery before any production change)

W1 start SHA: `7739c94` (G0 documentation only; same production tree as parent).
Draft stacked PR: https://github.com/Gustavo2358/analysis-cfg/pull/41.
Fresh GitHub read confirmed #40 OPEN/Draft at the exact parent SHA.

Pinned producer code inspected read-only, using `git show`/`git grep` at the
locked commits (not the possibly older sibling working directories):

- proleap-poc `fe88cc16f664c26d27e8f975476fc2dfc3e6eff8`;
- cobol-lower `9e746df89027de4aa0ec452f4e9a733d57eda866`;
- air-java `135d91f4d643c80eeb5d7bff9081fae229e9e62c`;
- analysis-ir `3fff18e2c16663a3f599207457caa1946d2e0945`.

```text
COBOL literal / declaration VALUE / resolved data reference
  -> frontend StorageAccessSemantics.move / StorageInitialSemantics
     SP LITERAL_BYTES, FITTED_LITERAL_BYTES, COPY_BYTES, FIT_TEXT,
     entry literal/possible literal + lifecycle proof + source provenance
  -> lower RegionalDataTranslator / RegionalEntryTranslator / RegionalMoveHandler
     Memory.Region + Memory.ViewBinding, ObjectPlace / RegionSlice
     EntryState(LiteralInitial | PossibleLiterals), Assign(literal | FitText(Read)), CopyBytes
  -> AIR validator -> CfgBuildCoordinator -> AnalysisSession / ProgramIndex
  -> StorageIndex: ObjectId -> binding -> base(StorageId) + range + codec
  -> StatementEffects: ExpressionSource / CapturedBytes + MUST/MAY + targets
  -> RegionalValuesAnalysis: entry seed, transfer, captured bytes, CFG solver/replay
     [legacy Cell path: PossibleValuesAnalysis + TextProfile]
  -> project: ByteImage.read(range) + MemoryCodecs.decodeText
  -> StorageValueFact -> asObjectFact -> RegionalValueFact : TextValueFact
  -> RegionalValuesProvider | StorageValuesProvider | PossibleValuesProvider
  -> CallDependencyPlan: PointQuery BEFORE Invoke, ObjectId or StorageSubject
  -> CallDependencyConsumer: candidateSupports -> RawCandidate + Support
  -> CallNameInterpreter -> DependencySiteFact.Candidate(referenceName, rawValue, supports)
```

Relevant paths are relative to their respective repositories. Consumer classes
above live in `analysis-values`, `analysis-kernel/{storage,structure,rd,query}`
and `analysis-dependencies`; upstream translators in `lower/application`.

| Boundary | Input → output | Preserved | Not carried / conservative limit |
| --- | --- | --- | --- |
| frontend → SP | literal/declaration/reference → published byte effect and entry facts | bytes/text, symbol/storage relations, extent, provenance, lifecycle proof | unsupported transfer is MUST_UNKNOWN/UNAVAILABLE, never an arbitrary literal |
| SP → AIR | RegionalData/Entry/Move translators → regions/views, initial conditions, Assign/CopyBytes | IDs via explicit links, ranges/codecs, source origins, known/possible distinction | no AST reinterpretation; unproved source semantics stay open |
| AIR → CFG/session | validated operations/control → indexed sites/contexts | operation and object IDs, explicit successors/outcomes | raw nonliteral codec writes without extent proof stop at VALIDATION_LIMIT; no bypass |
| object → storage | ObjectId/Place → StorageIndex.Resolution | StorageId, entry context, range, codec, occurrence, separation premises | display names convey no identity; uncertain binding cannot fabricate an allocation |
| effects → regional solver | ExpressionSource/CapturedBytes → regional images and event trace | literals, pre-write capture, interval writes, unknowns and correlation | unsupported expression/codec retains remainder; no global string scan |
| regional state → provider | bytes/scalars → TextValueFact, StorageValueFact | decoded candidates, model/source uncertainty, producer support, origins, premises | object projection omits interval alternatives/capture structure, available in storage projection |
| provider → CALL query | before-operation ObjectId/StorageSubject → text fact | exact query identity, program point and candidate support | only readable target shapes are requested; general expressions have no query |
| CALL → final candidate | supported raw text → reference name | raw text, support associations, evidence, origins, premises, uncertainty | name policy may trim padding; full interval capture trace is not duplicated in dependency wire |

FACT (code): `CallDependencyPlan.select` uses regional values for regions,
unproved preconditions, entry possibilities v2, Invoke results, or scalar
semantic-admission refusal. A readable non-ObjectPlace target selects
`StorageValuesProvider` for its unit. Otherwise admitted Cell-only cases use
`PossibleValuesProvider` with EFFECTS_PROFILE. Literal-only calls demand no
value analysis. `DependencyAnalysis` registers all three providers.

FACT: regional-to-text conversion already exists in
`RegionalValuesAnalysis.project`; `Execution.observe` projects a named storage
fact using `StorageValueFact.asObjectFact`. This is not a second scalar solver.
`CallDependencyConsumer` accepts the shared `TextValueFact` interface directly.
The metric named `possibleValuesRuns` counts all three value provider kinds;
it does not prove the scalar provider was selected.

FACT: ReachingDefinitions is not a prerequisite/provider registered by the CALL
plan. Regional values share `StatementEffects`, `StorageIndex`, `DefinitionEvent`
and the CFG dataflow infrastructure; `RegionalAnalysis` can compose RD and values
for storage diagnostics, but CALL does not run a new RD analysis to find literals.

### Fixtures and evidence flow

New `analysis-adapters/.../ValueToCallEvidenceTest.java` uses synthetic in-memory
AIR, independently asserted values and support IDs; it does not claim a newly
executed COBOL frontend E2E. W1-D is the authorized AIR-equivalent vertical
through real CFG, solver, provider and dependency consumer. The upstream map is
STRONG EVIDENCE from pinned code; source-language execution is not substituted
by these model tests.

- W1-A: literal producer, physical range, codec and support before copy.
- W1-B: equal-width regional object copy using FitText(Read); later source write
  cannot change captured B. Separate Cell Assign(Read) exercises scalar provider.
- W1-C: explicit provider observation for B, exact selected provider key, then
  independent final dependency candidates/supports.
- W1-D: literal MOVE directly to B; valid VALUE initial condition to A, copy to B,
  CALL B. Additional CopyBytes vertical checks its capture event.
- Adversarial: unknown B cannot borrow unrelated A's PROGA; literal CALL needs
  no values; two physical Choice alternatives retain both specific supports.
- First adjacent expression fixture: `CALL FitText(Read(B), 4, ' ')` with known
  B=`PROGA   `. Provider knows B; plan refuses the expression. An unwrapping
  fallback would wrongly report PROGA instead of the expression's PROG.
- Separate admission fixture retains the discovered raw regional Assign(Read)
  refusal. Without extent proof this is not the AIR shape emitted for the
  supported regional MOVE, and is not evidence that MOVE transfer is absent.

### Gap classification / pre-fix decision

| Scenario | Producer knows? | Storage identity correct? | Provider knows? | CALL consumer knows? | Gap class |
| --- | --- | --- | --- | --- | --- |
| literal direct CALL | yes | n/a | not demanded | PROGA | none, already supported |
| literal Assign to regional B | yes, PROGA padded | yes, B = region [8,16) | RegionalValues: exact text | PROGA | none |
| VALUE initial A → fitted object copy B | yes, proven EntryState literal | yes, A [0,8), B [8,16) | RegionalValues: exact text | PROGA | none |
| literal A → CopyBytes B | yes | yes | StorageValues: text + capture event | PROGA | none |
| Cell A → Assign(Read) B | yes | yes, separate Cell bindings with premise | PossibleValues: exact text | PROGA | none |
| unknown B, unrelated literal A | no B value | yes | open empty candidate set | OPEN_TARGET, no borrowed candidate | no gap: conservative unknown |
| Choice(A,B) | yes, PROGA/PROGB | explicit occurrence resolves both views | StorageValues: both | both candidates | none |
| raw regional Assign(Read), no extent proof | source literal exists | yes | not run: AIR preflight refuses | not reached | G3: representation/precondition boundary, not a CALL bug |
| inline FitText(Read(B),4) target | B yes; target expression not queried | yes | standalone B query exact | UNSUPPORTED_TARGET_EXPRESSION | G4: query/consumer admission boundary |

| Gap | Existing capability | Missing connection | Candidate fix | Risk |
| --- | --- | --- | --- | --- |
| raw nonliteral regional Assign | CopyBytes/FitText transfers already exist | an admissible, extent-proved write representation | producer should emit its existing supported representation; retain preflight | unproved re-encoding cannot be assumed total; do not weaken validator |
| inline computed FitText target | B fact; FitText transfer in regional writes | expression-aware observation for CALL, not merely object evidence | future bounded expression-query design reusing existing semantics | blindly unwrapping changes target, query point/support must remain correct |
| scalar-only predicate admission (read-only lead) | regional storage + transfer | predicate admission currently calls scalar proof path | investigate separately in W2 | upstream predicate semantics/control obligations; not safe to bypass |

No production fix is justified for the W1 simple data-item scope: the hypothesized
regional-fact/scalar-CALL disconnect is refuted for tested paths. The adjacent G4
is an explicit target-expression capability boundary, not a missing registration
of the regional provider. Supporting general expressions would extend the query
contract/evaluation surface; it is not necessary to make VALUE/MOVE/data-item
CALL work. No new lattice, fallback, reaching definitions, wire/API or pin change.

The raw Assign discovery was initially an invalid test assumption, corrected by
using the producer's FitText form in positive tests and retaining an explicit
negative test. Expected CALL values/support assertions were not weakened.

### Before / after

No production change, so before and after are identical: simple paths resolve
`{PROGA}`, physical choices resolve `{PROGA, PROGB}`, unknown stays empty/open,
and inline FitText stays explicitly unsupported. This campaign adds direct
boundary evidence rather than changing behavior to manufacture a fix.

### Provenance

FACT: candidate supports retain the original literal assignment or initial-place
operand, with origin/premises; CALL adds its site/target origins and operation.
CopyBytes storage projection also publishes its COPY event and before-capture
point/ranges. Named-object facts keep aggregate evidence but omit the structured
interval alternatives. Dependency candidates expose value producers, not a full
sequence of every MOVE; no invented copy provenance and no wire expansion.
The tests assert original support survives a later source overwrite and compare
provider evidence/provenance/premises against the final CALL fact.

### Remaining gaps for W2

STRONG EVIDENCE, pinned lower code: `IfAdmission.admitPredicate` accepts
SCALAR_TEXT_EQUALITY and calls `CallAdmission.admitReference`, requiring
wholeItemAccess/scalar TEXT proof; `IfPredicate` also reads wholeItemAccess.
`PartialProgramAdmission` invokes this path. Regional MOVE/storage existence
alone does not discharge those predicate obligations. This is a narrow
control-flow admission surface upstream of CFG, not proof that CALL ignores
an already delivered regional fact.

HYPOTHESIS: this boundary can explain losses for regional-only declarations
under IF. W2 must trace an actual failing source/SP/AIR example and distinguish
predicate admission from solver joins before deciding a fix. No new IF/EVALUATE,
GO TO, PERFORM, loop or interprocedural implementation was attempted. Existing
join tests are regression coverage only. Inline expression support is a separate
future decision, not automatically part of W2. Corporate gaps remain unmeasured.

### W1 answers

| Question | Answer and evidence level |
| --- | --- |
| VALUE literal modeled? | FACT in AIR EntryState tests; STRONG EVIDENCE for source lifecycle translation at pinned producer code |
| MOVE literal modeled? | FACT, regional Assign + provider + CALL |
| MOVE object→object modeled? | FACT, scalar Assign(Read), regional FitText(Read) and CopyBytes |
| Storage identity sufficient? | FACT for these cases: IDs/bindings/ranges/codecs, independent of identical display names |
| Which provider owns evidence? | FACT: PossibleValues for scalar Cells, RegionalValues for regional objects, StorageValues for physical/choice queries |
| Which consumer fails to use it? | None for the simple path; inline-expression admission does not request object facts |
| Narrow-consumer disconnect? | Refuted for plain data-item CALL; G4 shape restriction proved for inline FitText |
| Gap class? | G4 adjacent CALL expression query; G3 raw unproved write representation; no simple-path gap |
| Existing infrastructure reused? | Tests exercise existing pipeline; no production fix or duplicate analysis |
| Literal→MOVE→CALL works? | FACT, PROGA |
| Unknown conservative? | FACT, empty/open; unrelated literal not borrowed |
| Multiple candidates preserved? | FACT, PROGA and PROGB with distinct producer supports; existing join regression also executed |
| Provenance maintained? | FACT, original producers/origins/premises and storage capture event; structured interval trace not part of dependency candidates |
| Regional regression? | Focal explosion/alternatives/fallback gates pass; final module and FAST results below |

### Performance and validation scope

No production file, public contract, AIR/wire or source pin changed. No increased
regional demand was introduced. The CopyBytes probe reports existing solver
metrics: 6 interned structural edges (`solve_internedAlternatives`), maximum 2
structural edges in a state (`solve_maxStateAlternatives`), 0 interned/max event
rows, 2 concrete fallbacks, 5 expanded labels. These counts include the normal
known-byte transfer fallback, not a new explosion or a zero-fallback claim.
Inherited structural factoring, anti-correlation and provenance tests remain
independent oracles. The complete new test class has 9 tests.

Validation change class: tests/documentation only. Required focal, complete
changed-module reactor and FAST are executed. No full qualification-local,
broad baseline regeneration or corporate program: no production/contract/kernel
semantics changed to invalidate that evidence. Source inspection is newly done;
previous corporate/producer execution is not relabeled as a fresh test.

### Executed gates

All commands select Temurin Java 21.0.12 via JAVA_HOME/PATH and the isolated
`.harness-results/build/m2` repository. No test baseline was regenerated.

| Gate | Command / local raw log | Result |
| --- | --- | --- |
| G0 inherited | focal command above; `g0-final.log` | 39 tests, 0 failures/errors/skips |
| W1 focal | `mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" -pl analysis-adapters -am '-Dtest=ValueToCallEvidenceTest,*DependencyTest,Regional*Test,StorageIndexTest,CfgBuildCoordinatorTest,NameInterpreterTest' test`; `w1-focal-verified.log` | 132 tests, 0 failures/errors/skips |
| Complete modules | `mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" -pl analysis-adapters -am test`; `w1-modules-final.log` | 564 tests, 0 failures/errors/skips; includes all 149 analysis-adapters tests |
| FAST | `python3 -B scripts/harness/lean.py fast`; `w1-fast.log` | PASS CODE_CHANGE; 571 Java tests, 0 failures/errors/skips; architecture and Python checks PASS (92.839 s) |

The full module reactor includes `RegionalExplosionFixturesTest`,
`RegionalAlternativesTest`, `RegionalFallbackStressTest`, structural, provenance,
anti-correlation and CALL join tests. No regression detected. Earlier failed
logs remain local: fixture compilation/API mistakes, duplicate operand ID,
raw unproved Assign characterization and a reactor selector missing the
`NameInterpreterTest` module. None is represented as a passing gate.

### Closeout

Commits: `7739c94` establishes G0; `f4b1c59` characterizes the nine W1 fixtures;
final documentation commit records these results. Productive files changed: none.
Only the new test and this report differ from the validated parent. The parent
worktree remains clean at `f0efa4a76984781e09c26b92d4f6ee9cb2591f8f`.
Draft review is pending; campaign wave completion is not repository work-item
DONE/merge. No merge or auto-merge is authorized/performed.

G0 COMPLETE — STACKED CAMPAIGN FOUNDATION READY.

W1 COMPLETE — SIMPLE PATH ALREADY SUPPORTED; FIRST GAP IS INLINE TARGET-EXPRESSION QUERY ADMISSION (G4).

W2 NOT STARTED. W3 NOT STARTED. Await human review.

# W2 — control-flow composition

## Hygiene and scope

W2_START_SHA: `8d008354433e39e5b98986a6341e29dc96eaa390`.
Same clean worktree `.analysis-gaps/analysis-cfg`, same branch
`discovery/analysis-gaps`, same Draft PR #41; base
`discovery/regional-explosion-fixtures`, auto-merge null. Fresh GitHub read at
start: #40 OPEN/Draft at `f0efa4a76984781e09c26b92d4f6ee9cb2591f8f`.
No reset/rebase, no new PR, no change to #40. W1's earlier NOT STARTED statements
above record the W1 checkpoint; this section records the newly authorized W2.
W1 cheap gate ran before W2 edits: 41 tests, zero failures/errors/skips
(`.harness-results/w2-baseline.log`). G0/W1 implementation tree preserved.
Inline computed CALL expressions, EVALUATE, PERFORM/GO TO implementations,
CICS/FILE/SQL and W3 are excluded. No production or source-pin changes.

## Hypotheses

1. A correct AIR diamond may already propagate/join all values. **FACT: confirmed**
   for A–E in both scalar and regional domains, plus one nested diamond.
2. IF over a regional-only declaration may fail scalar predicate admission.
   **FACT: confirmed for the selected synthetic source**, with the first
   difference already at frontend predicate proof, before lower/CFG.
3. A solver/RD bug may explain lost targets. **Refuted for these fixtures**; no
   RD run is demanded, and no candidate is lost between provider and CALL.

## Pipeline and admission map

```text
COBOL IF / canonical binding / storage view
  -> frontend IfSemantics.predicate (complete scalar inventory)
  -> CobolSemanticProductProjector (IF projection) / PredicateGuarantee
  -> lower PartialProgramAdmission -> IfAdmission.admitPredicate
       -> CallAdmission.admitReference (wholeItemAccess + scalar TEXT)
  -> IfPredicate.translateReads -> Unknown BOOL with explicit Read dependencies
  -> IfSequenceAssembler.branch -> Operations.Branch(trueLabel,falseLabel)
     OR PartialProgramAssembler.opaque -> open control/effects envelope
  -> CoreCfgProjection -> BRANCH_TRUE/FALSE + arm JUMPs
  -> AnalysisSession / ContextView -> DataflowSolver
  -> RegionalValuesAnalysis.Engine.joinInto -> RegionalAlternatives.union
     OR PossibleValuesAnalysis.joinInto -> PossibleValuesState.join
  -> before-Invoke provider query -> TextValueFact
  -> CallDependencyConsumer -> candidate-specific supports
```

All upstream paths were inspected at the same immutable pins recorded in W1,
then built in isolated `.harness-results/w2-producers` using the existing
`prepare_w2d_producers.py`. The original sibling checkouts were not modified.
SP contract is **2.28.0**, storage profile explicitly
`ibm-enterprise-6.4-fixed-display-1047@1`. Default unspecified storage is not
used to establish the final regional finding.

| Boundary | Input → output | Admission / preserved information | Refusal or fallback |
| --- | --- | --- | --- |
| source → predicate proof | bound text equality + declarations → IfSemantics.Predicate | exact modeled relation, one uniquely resolved value read, member of complete scalar map | regional view alone does not satisfy `scalars.containsKey(selected)`; PARTIAL predicate |
| frontend → SP | proof → PredicateGuarantee, references, arm/continuation facts | known scalar: BOOLEAN/PURE/TOTAL/COMPLETE, truth UNKNOWN; regionalAccess independently retained | PREDICATE_NOT_PROVEN; wholeItemAccess absent; IF_OUTSIDE_SIMPLE_PROFILE |
| SP → lower selection | IfFact → precise set | KNOWN predicate, explicit arm/continuation destinations, all reads map via wholeItemAccess | IfFact not marked precise; no fabricated proof |
| lower admission → Branch | admitted predicate → Unknown BOOL + read dependencies, true/false labels | `IfAdmission.admitPredicate` checks SCALAR_TEXT_EQUALITY; `CallAdmission.admitReference` requires unique scalar whole-item; `IfPredicate` dereferences this proof | `PartialProgramAssembler.opaque`: PRECISE_SEMANTICS_UNAVAILABLE, no known branch successors, WithinControl(UnitControl), open memory effects |
| AIR → CFG | Operations.Branch + Jumps → typed transitions | CoreCfgProjection preserves labels and activation Entry; no guessed fallthrough | Opaque remains open; CFG does not synthesize BRANCH_TRUE/FALSE |
| CFG → fixed point | contextual edges and predecessor publications → joined roots | DataflowSolver propagates every edge contribution, joins into destination anchor, republishes changed states until worklist empty | no new cap/widening/pruning; open-control scopes remain conservative |
| regional join | reached predecessor stores → union of relations | joinInto includes missing bindings' default unknown; RegionalAlternatives.union preserves disjunction/support labels | no strong update across unrelated predecessor stores |
| scalar join | reached sparse Cells → candidate/support unions | PossibleValuesState.join retains unknown when a binding is absent in one reached root | unreachable bottom is distinct from reached unknown |
| provider → CALL | BEFORE Invoke ObjectId query → candidates | provider chosen by existing plan; values, evidence, origins, premises preserved | no inline-expression query added |

## AIR control matrix

`ControlFlowEvidenceTest` contains six tests, each run for Cell/PossibleValues
and Region/RegionalValues (12 domain/scenario combinations). It reuses W1's
identities/ranges. Predicate is a typed equality reading uninitialized A; CALL
reads B directly. No source parser/lower is involved in this independent oracle.
Control A is W1's straight-line literal-to-B test, freshly rerun in both baseline
and focal gates. The optional nested fixture has an inner diamond on the outer
true arm and two joins, not a loop/stress test.

Notation: `value{producer}` denotes exact candidate-specific support. `?` is
reached/model-unknown, not unreachable/bottom. Tables describe BOTH domains.

| Fixture | Branch A before join | Branch B before join | Post-join provider | CALL |
| --- | --- | --- | --- | --- |
| straight | PROGA{move-literal} | n/a | PROGA | PROGA, closed |
| A, same value | PROGA{write-left} | PROGA{write-right} | one PROGA candidate, both producers | one PROGA, both supports, closed |
| B, different | PROGA{write-left} | PROGB{write-right} | PROGA + PROGB, separate supports | same two, closed |
| C, unknown arm | PROGA{write-left} | ? | PROGA + model remainder | RESOLVED_CANDIDATES, effectiveUnknownRemainder=true |
| D, seed OLD / one overwrite | NEW{write-left}, no OLD | OLD{seed} | NEW + OLD | same two, closed |
| E, seed OLD / both overwrite | PROGA{write-left}, no OLD | PROGB{write-right}, no OLD | PROGA + PROGB; no OLD or seed support | same two, closed |
| nested | inner PROGA{write-inner-a} | inner PROGB{write-inner-b} | PROGA{write-inner-a,write-right} + PROGB{write-inner-b} | same two, closed; killed inner OLD absent |

Same-value support union describes alternative producers of one value; it does
not claim that both assignments executed together. All closed manual fixtures
have model/source/effective remainder false. C has model/effective true and
source false. Every final CALL remains reachable; `RESOLVED_CANDIDATES` means
known candidates exist, not that unknown possibilities are absent.

## CFG topology

The A–E expected edge set is asserted independently, including edge kinds,
sequence labels, activation E, exact cardinality and BFS reachability of all
nodes (7 nodes, 7 edges). The join sequence's terminator is CALL; therefore
there is no invented extra edge from JOIN to a separate CALL node.

```text
ENTRY --ENTRY--> start
start --BRANCH_TRUE--> left --JUMP--> join[CALL B]
start --BRANCH_FALSE-> right --JUMP--> join[CALL B]
join --INVOKE_NORMAL--> end --RETURN--> EXIT
```

Nested adds a Branch on left; inner-a and inner-b JUMP to middle, middle JUMPs
to join, right JUMPs directly to join (10 nodes, 11 edges). Every node is
reachable. No predecessor is missing. Closed model topology is distinguished
from the open source model below.

## Provider observations and join/convergence audit

For each domain/scenario, one explicitly selected provider run observes before
`jump-left` / `jump-right` and before `invoke`; nested observes its inner arm
jumps and final Invoke. Query and batch status are VALUE/COMPLETE; execution
outcome is STABLE. Assertions inspect candidates, exact support associations,
model/source/effective remainder. Aggregate evidence/provenance/premises from
the post-join provider must be contained in the final dependency fact.

**FACT:** both predecessor facts are correct, union preserves both, unknown
survives a missing reached binding, and strong overwrite eliminates seed only
on its path. `RegionalValuesAnalysis.Engine.joinInto` unions both roots using
`RegionalAlternatives.union`; `equivalent` compares entry, bindings and logical
facts. Scalar join unions candidates and supports and distinguishes missing
binding from bottom. `DataflowSolver` calls joinInto for each edge contribution,
queues changed anchors and exposes a result only after an empty worklist.

`KillAuthority.selected/exhaustive` authorize only required, exact closed writes;
regional `write` performs local replacement before joins. Scalar strong updates
use `exactCell` permits. D/E prove KILL is path-local and the implementation is
not merely accumulating historical literals. Existing solver law/schedule and
regional factoring suites supplement these finite acyclic probes; no claim of
new arbitrary-loop qualification is made.

## CALL observations

The registered implementations are asserted to be exactly Reachability plus
PossibleValues or RegionalValues, depending on storage. `possibleValuesRuns=1`.
CALL receives the post-join values unchanged except existing name-policy padding
normalization. No G4 post-join consumer loss was observed. No changes to
`CallDependencyPlan.readable()` or the W1 inline FitText boundary.

## Lowering/admission investigation

Three new sources in `analysis-adapters/src/test/resources/analysis-gaps/w2`
were actually run through frontend → SP → lower → AIR → CFG → dependency CLI,
with the existing pinned producer build wrapper. The selected source probe
`scripts/project/probe_analysis_gaps_w2.py` asserts public products and preserves
all raw products/stdout/stderr locally. This IS selected synthetic CLI E2E;
it is not corporate/corpus qualification or newly run full producer suites.

| Source | Predicate SP | Regional storage evidence | AIR IF / CFG | Post-IF CALL |
| --- | --- | --- | --- | --- |
| scalar-control: standalone FLAG, standalone WS-PGM | KNOWN SCALAR_TEXT_EQUALITY, PURE/TOTAL/COMPLETE, wholeItemAccess present | profile also publishes physical views | Branch Unknown BOOL with Read dependency; true/false edges enter their own Assign and Jump to CALL | PROGA + PROGB with original per-arm supports, open remainders |
| regional-predicate: FLAG nested under FLAGS, standalone WS-PGM | PARTIAL / UNAVAILABLE / PREDICATE_NOT_PROVEN; wholeItemAccess null | resolved DATA; regionalAccess points to exact offset 0, extent 1, IBM1047 view; no storage gap | Opaque IF, control.known empty, WithinControl(UnitControl), broad MAY effects; no BRANCH_TRUE/FALSE in known CFG | PROGA + PROGB survive conservatively, open remainders |
| regional-target: standalone FLAG, WS-PGM nested under TARGET-AREA | KNOWN scalar predicate even though overall IF profile is OUTSIDE_SLICE | regional target and fitted byte MOVEs valid | Branch + both regional Assigns + JUMPs to CALL | PROGA + PROGB, original per-arm supports, open remainders |

The scalar control label refers to predicate declaration proof; with the
explicit storage profile even these source products may use Regions. Cell-only
provider selection is proved separately by the independent AIR matrix.

**First differing layer = frontend predicate-proof admission**, specifically
`IfSemantics.predicate`: the uniquely bound read must appear in the complete
scalar map (`scalars.containsKey(selected)`). The SP projector publishes
PREDICATE_NOT_PROVEN, UNKNOWN evaluation/completion/domain, PARTIAL read
completeness, and missing wholeItemAccess even while retaining regionalAccess.
This is not lost nominal identity or absent storage layout.

The lower faithfully enforces that narrower proof surface:
`PartialProgramAdmission` requires KNOWN plus mapped wholeItemAccess;
`IfAdmission.admitPredicate` requires BOOLEAN/PURE/TOTAL/COMPLETE and calls
`CallAdmission.admitReference`; that requires scalar whole-item TEXT proof.
`IfPredicate.translateReads` consumes exactly that proof. The regional case
therefore falls through to `PartialProgramAssembler.opaque`, with reason
`cobol-lower:PRECISE_SEMANTICS_UNAVAILABLE`; it does NOT return a fake Branch.

**FACT:** the W1 suspicion is confirmed for regional-only predicate reads,
but must be refined: the first refusal is already upstream of cobol-lower.
**FACT:** merely having a regional target/arm does not force IF fallback;
the regional-target control refutes that broader claim.

**No fabricated loss of candidate names:** all three source cases still expose
PROGA and PROGB. The regional IF loses typed two-way topology and effects
precision, not these particular candidates. `ContextView`/`OpenControl` expand
published conservative control scopes for analysis; arm reachability in that
model is not a reconstructed exact IF. All source cases have model, source,
interpretation and control remainder true because real external CALL also
publishes broad control/MAY-write/name-policy uncertainty. Hence source remainder
flags alone cannot measure the additional IF imprecision. The differential
oracle is predicate proof / Branch / typed edges, with candidate preservation
checked separately. Candidate support origins resolve to the corresponding
original COBOL MOVE lines, never the other arm.

An initial exploratory run omitted the storage profile and produced unrelated
storage gaps. It is preserved in `.harness-results/w2-source`, not used for the
regional conclusion. Final oracle results use the explicit profile in
`.harness-results/w2-source-verified/summary.json`; exploratory profiled products
are separately retained. No AIR/SP output was edited to obtain PASS.

## Reaching Definitions decision

1. No W2 fixture requires an RD run; the plan's exact implementation set is
   tested, and `DependencyAnalysis` registers values and reachability providers.
2. RegionalValues already propagates values, unknowns and provenance through its
   own fixed point. PossibleValues does the same for admitted scalar Cells.
3. `RegionalAnalysis` can compose RD and values in a separate diagnostic path;
   shared `StatementEffects`/`DefinitionEvent` types do not imply RD execution.
4. Adding RD here would duplicate information and cannot restore a missing
   source predicate proof/Branch. No new RD implementation was added.

**FACT: RD não é o mecanismo que compõe este caminho.**

## Gap classification — discovery before any production fix

| Layer | Straight | Same-value IF | Different-value IF | Unknown branch | Kill/join |
| --- | --- | --- | --- | --- | --- |
| Independent AIR → CFG | supported (W1 rerun) | exact | exact | exact | exact |
| Scalar/regional provider | exact | one value, both supports | both values/supports | known + model open | path-local kill, correct union |
| CALL consumer | matches provider | matches provider | matches provider | matches provider/remainder | matches provider |
| Real source admission | W1 evidence only | not source-executed in W2 | scalar proof → Branch; regional-only predicate → Opaque | not source-executed | not source-executed |

| Failure | First differing layer | Classification | Existing capability | Missing connection |
| --- | --- | --- | --- | --- |
| regional predicate not represented as Branch | frontend IfSemantics predicate proof; then lower precise selection | G3 representation/precondition gap upstream | canonical read binding + exact regional view + working generic CFG joins/values | regional predicate proof/admission shared with producer/lower; BOOLEAN/PURE/TOTAL/COMPLETE obligations must actually be established |
| values/join corruption | none in A–E/nested | no G5 found | generic dataflow and regional union | none |
| post-join CALL loses a value | none | no G4 found for plain CALL B | existing TextValueFact consumer | none |

## Fix, if any

**ANALYSIS ALREADY SUPPORTS THIS CONTROL FLOW.** No production fix is necessary
in analysis-cfg. Do not reinterpret Opaque as Branch or weaken upstream
preconditions. The next producer decision belongs to proleap-poc predicate
semantics/SP proof and cobol-lower IF admission/translation, with explicit
contract review. No new expression evaluator, lattice, heuristic, wire or pin.
Before/after production behavior is identical; this wave supplies test evidence
and a source-reproduced location of the first gap.

## Regional regression gate

Tests/docs/probe-only delta. Mandatory focal, complete changed module reactor,
Java 21 FAST and selected three-source probe are run. No production values/solver
change invalidates corporate W3.3 qualification. Explosion, alternatives,
fallback, anti-correlation, provenance and stack-safety tests remain mandatory
regression evidence, not a reason to rerun a corporate program unavailable here.
Results and commands are recorded at W2 closeout below.

## Remaining gaps for W3

- Decide whether/how to publish a regional text predicate proof and consume it
  upstream without inventing purity, totality or complete reads.
- Measure remaining precision/coverage at final consumers separately from the
  deliberately open real-CALL contract. No coverage percentages inferred here.
- W1 inline FitText CALL G4 remains separate and unimplemented.
- Generic solver joins operate on contextual CFG edges, not on a Branch-specific
  lattice. **STRONG EVIDENCE** that already admitted PERFORM/GO TO edges use the
  same join machinery; no new family coverage claim without dedicated fixtures,
  and no automatic implementation/qualification of those families.
- **HYPOTHESIS:** establishing regional predicate proofs could narrow some real
  control/effects uncertainty. No claim of corporate impact or candidate gain
  follows from these three synthetic sources.

W3 NOT STARTED. PR #41 remains Draft; no merge authorized.

## W2 closeout — validation and reproducibility

Test/probe commit: `65f5955` (`test: characterize value flow across cfg joins`).
Production files changed: **none**. Versioned additions are the independent
`ControlFlowEvidenceTest`, three synthetic COBOL fixtures and a local selected
source probe; this report is the only documentation change. No baseline rewrite.

All analyzer gates use Temurin **21.0.12** and the isolated Maven repository
`.harness-results/build/m2`. Every Java test count below has **zero failures,
errors and skips**:

| Gate | Result | Local raw log |
| --- | --- | --- |
| W1 baseline before edits | PASS, 41 tests | `.harness-results/w2-baseline.log` |
| W2 final focal incl. W1/CALL/CFG/solver/regional | PASS, 254 tests | `.harness-results/w2-focal.log` |
| Complete analysis-adapters reactor + dependencies | PASS, 570 tests | `.harness-results/w2-modules.log` |
| FAST | PASS CODE_CHANGE, 571 Java tests + architecture/Python checks | `.harness-results/w2-fast.log` |
| Pinned selected source CLI probe | PASS, 3 cases | `.harness-results/w2-source-verified.log` and `w2-source-verified/summary.json` |

The module reactor includes cfg-kernel, analysis-kernel, analysis-values,
analysis-dataflow, analysis-dependencies and analysis-adapters. Focal selection
includes RegionalExplosionFixturesTest, RegionalAlternativesTest and
RegionalFallbackStressTest as well as CFG and solver oracles. No regional
regression observed. FAST uses its existing fixed selection; new W2 tests are
covered by the focal and complete-module gates, without claiming they were
added to that selection. Fresh upstream wrapper compilation is build-only; it is
not represented as execution of upstream test suites.

Reproduction from this worktree (the `--work` directories must be fresh):

```sh
export JAVA_HOME=/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-adapters -am \
  '-Dtest=ControlFlowEvidenceTest,ValueToCallEvidenceTest,W1d*Test,Regional*Test,*Solver*Test,*Join*Test,*Cfg*Test,StorageIndexTest,NameInterpreterTest' test
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-adapters -am test
python3 -B scripts/harness/lean.py fast
W2D_SOURCE_ROOT=/home/gustavo/workspace/teste-e2e \
  W2D_MAVEN_REPO="$PWD/.harness-results/build/m2" \
  python3 -B scripts/project/prepare_w2d_producers.py \
  --work "$PWD/.harness-results/w2-producers"
# Ensure both CLI modules and their reactor dependencies are compiled.
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" compile
python3 -B scripts/project/probe_analysis_gaps_w2.py \
  --producers "$PWD/.harness-results/w2-producers/producers.json" \
  --work "$PWD/.harness-results/w2-source-verified"
```

No expanded RegionalValues usage was introduced. The final focal log also
retains raw run metrics for each AIR scenario. For scale context (not new
performance thresholds):

| Regional fixture | Contextual edges | Prepared events | Concrete fallbacks | Expanded labels |
| --- | --- | --- | --- | --- |
| A | 7 | 2 | 3 | 8 |
| B | 7 | 2 | 3 | 8 |
| C | 7 | 1 | 2 | 6 |
| D | 7 | 2 | 3 | 8 |
| E | 7 | 3 | 4 | 10 |
| nested | 11 | 4 | 8 | 29 |

These are small concrete fixtures; zero interned provenance rows here does not
claim absent evidence. Exact producer supports are independently asserted.
Structural factoring/stress properties remain covered by inherited tests.

Final parent check before publication: #40 still OPEN/Draft at the original
validated `f0efa4a76984781e09c26b92d4f6ee9cb2591f8f`; no synchronization needed.
Future parent-production synchronization rule in G0 still applies.

**RESULT B**

**W2 COMPLETE — ANALYSIS SUPPORTS CONTROL FLOW; FIRST GAP IS UPSTREAM IF
ADMISSION (G3).**

**W3 NOT STARTED.** Same PR #41, Draft, temporary parent base, no auto-merge,
no merge. Await human review. Per repository lifecycle, this is a validated
campaign wave, not a claim that the unmerged work item is DONE.

# W3 — final consumer coverage

## Hygiene and discovery before production changes

W3_START_SHA `992bf21d111b66ad5583cad0412ca859acd0243a`; same clean
`.analysis-gaps/analysis-cfg` worktree, branch and Draft PR #41. Parent #40
OPEN/Draft, unchanged at `f0efa4a76984781e09c26b92d4f6ee9cb2591f8f`.
No reset/rebase. Baseline W1/W2/regional passed before edits
(`.harness-results/w3-baseline-final.log`). Two initial test selections failed
because intermediate reactor modules had no matching test; the corrected
selection includes their smoke tests. No test-skipping flag was introduced.

Initial AIR characterization and independent public-wire oracle pass (34 cases).
Discovery before any production fix:

| Gap | Existing capability | Missing connection | Candidate fix | Risk | Scope |
| --- | --- | --- | --- | --- | --- |
| CICS PROGRAM closed Choice | StorageValues returns both values with exact supports; plan already selects that provider; each leaf is an 8-byte IBM1047 view | CicsNameInterpreter.area only accepts ObjectPlace/RegionSlice; consumer rejects the outer Choice before reading the prepared fact | prove every leaf of a closed, typed Choice with the existing leaf-area rule | admitting an unproved/unknown area; reject open/empty/unknown-type or invalid leaf choices | local plan admission, no evaluator or wire change |
| native computed FILE | standalone storage query can know the text | FileValueQuery deliberately selects only cics.file; native source ASSIGN DYNAMIC is outside N-LR | new native dialect/profile semantics plus source and query work | treating assignment-name as runtime filename/DSNAME | deferred; not a local forgotten query |
| inline CALL FitText | transfer supports fitting assigned values | no whole-expression query | extend query surface only with explicit semantic design | unwrapping would return the wrong target | W1 G4 deferred |
| regional IF | valid binding/layout | frontend scalar predicate proof | upstream predicate proof/admission | fabricated purity/totality | W2 G3 deferred |

The CICS Choice case is a **G4 local consumer admission gap** for a closed set
of individually proven areas. It is distinct from an unproved Cell/short area
(G3) and the excluded inline expression G4. A positive regression must fail
before the local admission fix; open/invalid alternatives must stay refused.

## Consumer architecture inventory

| Consumer | Site type | Literal path | Computed path | Provider/query | Output |
| --- | --- | --- | --- | --- | --- |
| COBOL CALL | Invoke, category program, namespace cobol.program | LiteralTarget → CallNameInterpreter, no values run | Read(ObjectPlace/Choice/exact RegionSlice); name policy applied after values | BEFORE ObjectId → PossibleValues or RegionalValues; any non-object target in unit routes through StorageValues/NamedObject, PlaceOccurrence or PhysicalRange; always Reachability | DependencySiteFact → sites/edges, technology COBOL |
| CICS LINK | Invoke action call, program/cics.program | CICS literal support + cics-ts.program@1 | same query routing, additional physical 8-byte IBM1047 proof | CallDependencyPlan/Consumer plus CicsNameInterpreter; no separate CICS solver | DependencySiteFact, command LINK |
| CICS XCTL | Invoke action execute, program/cics.program | same program name policy | same values route; outcomes do not invent a normal return | same plan/provider, BEFORE observation | DependencySiteFact, command XCTL |
| native FILE | file/cobol.external-file-name Invoke + resource declarations/uses | ExactName external assignment-name; resource ID binds uses to declarations | not admitted by FileValueQuery | Reachability only; static semantic identity, not a CALL value query | FileDependencyResult → fileDependencies declarations/sites/edges |
| CICS FILE/DATASET | file/cics.file Invoke with typed action | cics-ts.file@1, FILE_LITERAL support | Read of 8-byte IBM1047 object, slice or Choice | FileDependencyAnalysis → FileValueQuery → StorageValues BEFORE → FileDependencyConsumer/FileNamePolicy | fileDependencies, no invented native SELECT/FD |

Source boundaries: frontend publishes CICS_PROGRAM_CONTROL/CICS_FILE_CONTROL or
native fileInventory and operation/resource association. `CicsInvokeHandler`
checks the name area before generating Read; `CicsFileInvokeHandler.Context.name`
checks extent/codec before Read; neither guesses a target. `FileResourceLowering`
uses known assignment-name as LiteralTarget, otherwise UnknownResource plus an
unknown computed target. All consumers use AIR IDs, never display-name matching.

CICS FILE additionally projects default/explicit system selection through
`FileSystemContext`. Computed SYSID is a separate 4-byte IBM1047 storage query.
FILE and SYSID candidates are independent projections, not asserted correlated
pairs. Existing FileCicsContextOracleTest is rerun; no context contract change.

## CALL

W1/W2 controls are rerun rather than duplicated: ValueToCallEvidenceTest proves
literal/no values run, simple initialized/copied value, StorageValues Choice,
unknown and candidate supports. ControlFlowEvidenceTest proves post-join
PossibleValues/RegionalValues. The known inline FitText negative remains
UNSUPPORTED_TARGET_EXPRESSION. No change to `readable()` or COBOL interpretation.
Within the admitted contract, no provider-to-plain-CALL loss was observed.

## CICS

`ConsumerCoverageTest` separately executes LINK and XCTL literal and computed
object/slice/Choice for simple, multiple, unknown and partial values. The
provider facts are observed before Invoke, with exact candidate/producer
associations; final facts must preserve them. Object routes select RegionalValues;
slice/Choice routes select StorageValues. Literal routes request no values.
The independent Python wire oracle checks the serialized candidates, status,
namespace, command, support association and remainder.

For the closed Choice fix, the independent StorageValues query was already
`{PROGA{seed-a}, PROGB{seed-b}}` while final CICS was unsupported. The positive
regression then failed exactly as expected in `w3-choice-red.log`:
`RESOLVED_CANDIDATES` expected, `UNSUPPORTED_TARGET_EXPRESSION` actual.
After the fix it publishes both candidates with their own supports. No extra
provider is selected; the same values query was already planned before the fix.

A short leaf remains unsupported. A typed open Choice has a valid SameDomain
premise proving TEXT, but that does not prove the width/codec of the remainder;
it remains unsupported. This negative uses the public in-memory AIR API because
the pinned AIR JSON codec cannot encode SameDomain (see transport ledger below).
The final dependency JSON for it is still generated and independently checked.
Unknown-type computed targets do not establish the required TEXT contract; they
are not falsely treated as valid transported source fixtures.

XCTL's explicit external control remainder makes the manual publication's
source closure open even with a known name. Therefore `RESOLVED_CANDIDATES`
is compatible with source/effective unknown. For LINK's closed manual models,
only the unknown/partial value cases have a value remainder. We preserve the
observed contract rather than forcing all known names to mean closed-world.

## FILE

Native exact names reuse ResourceBindingOracle A2: declaration ResourceId,
operation binding/role, owner and external name are distinct from storage IDs
and display labels. The wire preserves CLIENTDD and the exact use association.
No storage analysis runs merely to publish a native literal file name.

CICS FILE tests use the existing FileComputedOracleTest abstractions and actual
StorageValues facts. Objects, physical slices and Choice all preserve simple,
multiple, partial and unknown results through the FILE consumer and JSON.
Multiple names retain separate seed-a/seed-b supports. Unknown produces no
invented file. Existing timing, alias, FILE/SYSID context and shared-run tests
are rerun. No claim of general alias/OCCURS qualification is added.

The artificial native computed AIR control has a provider fact PROGA but
FileValueQuery does not select it outside cics.file. This is a deliberate query
contract boundary, not sufficient evidence to implement dynamic ASSIGN semantics.
The real source witness locates the earlier refusal: ASSIGN DYNAMIC publishes
UNAVAILABLE, externalFileName=null, `ASSIGN_OUTSIDE_N_LR`. Its OPEN/READ/CLOSE sites
remain present and unknown, even though data item X has VALUE 'CLIENTDD'.
Do not conflate X, logical file F, external assignment-name, DD allocation or
physical dataset identity. JCL/DSNAME resolution is outside the product, not a
missing promised field or an unknown dependency to fabricate.

## AIR-level matrix

All rows refer to freshly run tests. `CCT` = ConsumerCoverageTest; `W1` =
ValueToCallEvidenceTest; `W2` = ControlFlowEvidenceTest. CCT's computed positives
compare producer supports in provider → consumer; the independent
check_analysis_gaps_w3_wire.py validates **44** final JSON documents.

| Fixture | Producer | Provider/query | Consumer | Final |
| --- | --- | --- | --- | --- |
| CALL literal/simple/multiple/unknown | W1 assignments/initial/copy; W2 branches | W1/W2 exact provider or none for literal | unchanged | PASS W1/W2 |
| LINK/XCTL literal (CCT) | LiteralTarget PROGA | no values requested | CICS_LITERAL, correct command | PROGA |
| LINK/XCTL simple (CCT) | seed-a | PROGA from NamedObject/PhysicalRange/PlaceOccurrence | same support/origin/premises | PROGA |
| LINK/XCTL multiple (CCT) | seed-a / seed-b on distinct paths | PROGA / PROGB, separate producers | same two | both, no cross-association |
| LINK/XCTL unknown/partial (CCT) | no write / one branch writes | empty+unknown / PROGA+unknown | same remainder | conservative |
| CICS closed Choice (CCT) | both branch values, proven leaf areas | already correct before fix | formerly unsupported; now both | RECONCILED G4 |
| CICS short/open area (CCT, CicsInvokeRouteTest) | value may exist | proof insufficient for whole area | unsupported | no candidate invented |
| native FILE exact (CCT/FileDependencyTest) | semantic resource identity | no value query | CLIENTDD, resource association | exact source-level file |
| native computed AIR (CCT) | seed-a PROGA | standalone query knows; plan makes no value request | FILE_VALUES_UNAVAILABLE | empty/open |
| CICS FILE literal (CCT) | literal PROGA | no values requested | FILE_LITERAL | PROGA |
| CICS FILE computed (CCT/FileComputedOracleTest) | seed-a / seed-b | StorageValues: simple/multiple/partial/unknown | exact supports and remainder | unchanged PASS |

## Source-level matrix

Fresh W3 source probe: **12 cases** with unchanged pinned producers and the final
consumer code. Full raw SP/AIR/CFG/dependencies and stdout/stderr are under
`.harness-results/w3-source-final`; the earlier pre-fix run is retained separately.
No intermediate product is edited. Builds/classpaths are reused from W2 after
checking clean immutable pins against sources.lock.json. This is selected
synthetic E2E, not corpus/corporate qualification or new upstream full suites.

| Source | Classification | Result / first boundary |
| --- | --- | --- |
| CALL W2 scalar/regional-target controls | SOURCE PROVEN, reused W2 evidence | both known candidates; no relevant source/query change; new FILE controls also exercise downstream CALL preservation |
| link-literal / link-computed | SOURCE PROVEN, new | PROGA, model closed, source/effective open |
| xctl-literal / xctl-computed | SOURCE PROVEN, new | PROGA; no invented normal success outcome; source/effective open |
| link-multiple | SOURCE PROVEN, new | PROGA + PROGB, supports point to their corresponding source MOVEs |
| link-unknown | SOURCE PROVEN, new | no candidate; model/source/effective open |
| native-static (existing fixture rerun) | SOURCE PROVEN, new | OPEN/READ/CLOSE → CLIENTDD, exact declaration/use IDs |
| native-dynamic | SOURCE UNSUPPORTED, refusal proven by new run | ASSIGN_OUTSIDE_N_LR upstream; all three sites retained unknown |
| read-dataset-literal (existing fixture rerun) | SOURCE PROVEN, new | READ → ACCOUNTS, literal closed; DATASET spelling retained, canonical FILE |
| read-dataset-computed / read-file-computed | SOURCE PROVEN, new | ACCOUNTS; model/source remainders remain open in these mixed CALL sources |
| computed-unknown (existing fixture rerun) | SOURCE PROVEN, new | no CICS FILE candidate; unknown explicit |
| source-generated closed CICS PROGRAM Choice | NOT TESTED | fix is AIR PROVEN; source probe uses uniquely bound objects |
| source XCTL multiple / CICS FILE multiple | NOT TESTED in W3 | AIR PROVEN; no relabeling of prior campaigns' source runs |

The scalar IF in link-multiple is admitted. No new probe depends on regional IF
predicate proof, so W2 G3 does not block this suite and remains untouched.

## Provenance/support matrix

| Consumer | Internal information | Contractually exposed information | Observation |
| --- | --- | --- | --- |
| CALL/CICS program | TextValueFact candidate supports, aggregate evidence/provenance/premises; regional capture/logical facts can exist below projection | per-candidate kind/producer/origin/premises plus site evidence/provenance/premises and raw names | exact equality/subset checks in W1/W2/CCT; source CICS supports map to source MOVE operations |
| CICS FILE | StorageValueFact candidates/supports and richer storage/capture facts | per-candidate kind/producer/origin/premises, valuePoint, uncertainty/reasons; no aggregate storage-fact/capture graph promised | CCT compares every producer record to the corresponding fact; independent wire validates associations |
| native FILE | declaration/use/resource identity and origins | declaration objects, use roles/bindings, literal support and origins | direct identity checks and source-span traces |

No candidate-support loss was found in the admitted paths. Empty premise lists
in synthetic fixtures are not described as a proof of nonempty-premise transport;
existing W1/regional provenance regressions remain the broader oracle. Capture
relations/logical-source graphs are richer internal analysis data: dependencies
JSON does not promise to serialize that entire graph. No wire expansion here.

## Known inline-expression G4

No other inspected consumer exposes a reusable arbitrary Expression query.
All use ObjectId or StorageSubject (NamedObject/PhysicalRange/PlaceOccurrence).
Regional transfer evaluates FitText while applying an assignment; it is not a
provider query for the value of an arbitrary target expression at a point.
This is a potential implementation building block, not an already complete
query abstraction. W1-G4 stays UNSUPPORTED_TARGET_EXPRESSION and deferred.

## Known upstream G3

Regional IF predicate proof remains the W2 upstream G3 with source-reproduced
PREDICATE_NOT_PROVEN. No producer pin, predicate proof, lower admission or CFG
solver was changed. No new RD run or implementation was added.

## Gap ledger

| ID | Consumer | Scenario | Result | First boundary | Gap class | Fix now? |
| --- | --- | --- | --- | --- | --- | --- |
| W1-G4 | CALL | inline FitText(Read(B),4) | unsupported, known B insufficient to evaluate expression | whole-expression query admission | G4 consumer/query extension | no, explicitly deferred |
| W2-G3 | all post-IF consumers | regional-only predicate | precise Branch absent | frontend predicate proof, then lower admission | G3 upstream | no |
| W3-CICS-CHOICE | LINK/XCTL | closed typed Choice, all areas proven | provider already correct; now consumer correct | CICS area proof collection | G4 local consumer | YES, narrow reconciliation |
| W3-CICS-AREA | LINK/XCTL | Cell-only, short area, open choice | conservative unsupported | required physical 8-byte IBM1047 proof | G3 precondition | no; valid refusal |
| W3-CICS-POLICY | CICS programs/files | wrong policy/version or invalid spelling | no arbitrary normalized target | name-policy profile | G3 profile/precondition | no; existing negative tests |
| W3-NATIVE-DYNAMIC | native FILE | ASSIGN DYNAMIC X | source target unavailable even with VALUE X | frontend N-LR profile: ASSIGN_OUTSIDE_N_LR | G6 unsupported profile semantics | no, optional separate campaign |
| W3-NATIVE-QUERY | native FILE | manually supplied computed Read target | storage knows; FILE plan does not query | FileValueQuery namespace/policy contract | G4 query extension, dependent on native semantics | no, not a dynamic-FILE shortcut |
| W3-OPEN-CHOICE-TRANSPORT | AIR transport / CICS negative control | typed open Choice requires SameDomain | in-memory structurally valid; pinned JSON writer rejects assertion | air-json BindingWriter.premise supports only DisjointStorage | G3 upstream representation/transport | no; not a consumer loss |

No new G1 missing value producer, G2 identity mismatch or G5 join corruption was
found in the selected fixtures. This is not a proof that those gap classes are
absent everywhere. SQL/DB2, copybook expansion changes, PERFORM/GO TO/EVALUATE,
interprocedural parameters, general aliases/OCCURS and JCL/DSNAME are unqualified
or excluded; no invented per-feature failure result is assigned without a probe.

## Fixes, if any

Only production change: `CallDependencyPlan.cicsArea`. It walks the canonical
place alternatives iteratively; every closed Choice must have known TEXT and
nonempty candidates, and every leaf must pass the existing CicsNameInterpreter
area check. No string heuristic, scalar fallback, new provider, solver, lattice,
name-policy change, pruning or budget. Complexity is linear in place occurrences;
iteration avoids recursive stack growth. `readable()` is unchanged.

RED precedes the fix; after-fix closed Choice resolves both names. Short/open
alternatives remain refused. Before/after source probes agree for the 12 tested
source shapes. FILE and COBOL CALL implementation paths are unchanged.

## Performance and regional regression

The formerly rejected CICS Choice already requested StorageValues before the
fix. The change consumes its prepared result, with `possibleValuesRuns=1` asserted.
For the two-path positive witness: contextual edges 7, concreteFallbacks 3,
expandedLabels 16, maxStateAlternatives 6, maxProvenanceRows 0. Raw metrics for
all observed Choice variants are retained in w3-focal-pass.log. Zero rows in
this small concrete witness does not mean no candidate evidence.
RegionalExplosionFixturesTest, RegionalAlternativesTest and
RegionalFallbackStressTest are rerun, along with regional provenance/factoring,
CICS/FILE/CALL and W1/W2 tests. No semantic values implementation changed.

## Remaining campaigns

- Regional predicate proof/admission: separate upstream design and qualification.
- Inline target-expression query: separate authorization; possible transfer-code
  reuse does not establish the query contract.
- Native dynamic FILE dialect/profile: optional extension, not core N+C debt.
- SameDomain AIR transport: separate upstream contract implementation decision.
- Open/unknown physical CICS areas remain unsupported until proof is available.

**FACT:** selected admitted paths preserve candidates, support and unknowns;
one local G4 was reconciled. **STRONG EVIDENCE:** remaining named failures above
are at explicit upstream/query/profile boundaries, not a generic value solver
failure. **HYPOTHESIS:** future upstream/query work could improve real-world
coverage; no percentage or corporate impact is inferred here.

## Final capability matrix

PASS means a named oracle above, not universal language coverage.

| Capability | CALL | CICS LINK | CICS XCTL | Native FILE | CICS FILE/DATASET |
| --- | --- | --- | --- | --- | --- |
| Literal/exact | PASS W1 | PASS CCT/source | PASS CCT/source | PASS resource/source | PASS CCT/source |
| Simple computed | PASS W1 | PASS CCT/source | PASS CCT/source | outside current profile/query | PASS CCT/source |
| Multiple values | PASS W1/W2 | PASS CCT/source | PASS CCT; source NOT TESTED | computed N/A | PASS CCT; source NOT TESTED in W3 |
| Unknown conservative | PASS W1/W2 | PASS CCT/source | PASS CCT | PASS unsupported-source witness | PASS CCT/source |
| Candidate supports | PASS W1/W2 | PASS CCT/wire/source | PASS CCT/wire/source | PASS literal/resource origin | PASS CCT/wire/source |
| Physical Choice | PASS W1 | PASS after local fix, AIR only | PASS after local fix, AIR only | N/A | PASS existing path |
| Selected source pipeline | W2 reused + mixed W3 controls | PASS new | PASS new | exact PASS; dynamic refused | selected READ FILE/DATASET PASS |

No additional wave is technically required to finish this discovery map.
Residual implementation decisions require explicit authorization; none starts
automatically. PR #41 remains Draft, stacked on #40, awaiting human review.

## Validation and closeout

Change frontier: one local consumer-plan admission helper; no values transfer,
solver, storage identity, provider, public API/wire, source producer or pin change.
The compiled inventory update is exactly nine additional dependency edges for
CallDependencyPlan (Scopes/NoMemory/MemoryBound, Types/Known/Builtin/Type/TypeRef,
ArrayDeque); no class/public-descriptor drift and no broad baseline regeneration.
The first FAST correctly rejected that unrecorded compiled delta; it is retained
in w3-fast.log and the corrected gate is w3-fast-final.log.

| Newly executed gate | Result | Raw evidence |
| --- | --- | --- |
| W1/W2 + inherited regional baseline | PASS 60 tests, zero failures/errors/skips | w3-baseline-final.log |
| Positive CICS Choice before production fix | expected RED, one status mismatch | w3-choice-red.log |
| Final focal CALL/CICS/FILE/W1/W2/regional | PASS 202 tests, zero failures/errors/skips | w3-focal-pass.log |
| Complete changed-module reactor + dependencies | PASS 577 tests, zero failures/errors/skips | w3-modules.log |
| Independent dependency wire | PASS 44 cases | w3-wire.log |
| FAST Java 21 | PASS CODE_CHANGE, 571 Java tests, zero failures/errors/skips; architecture/Python PASS | w3-fast-final.log |
| Final pinned source CLI probe | PASS 12 cases, including expected native refusal | w3-source-final.log and w3-source-final/summary.json |

Logs are relative to `.harness-results/`. FAST retains its fixed selection; the new W3 cases are covered by focal and
complete-module gates, not falsely counted as new FAST selections.
The new 7 JUnit tests execute many scenario combinations; do not add those combinations to the Maven test count.
The open Choice negative is in-memory AIR plus dependency JSON, explicitly not
AIR JSON round-trip. The other new AIR fixtures round-trip the pinned codec.
Initial fixture-authoring compile/validation failures remain in local logs:
no additional JSON parser dependency was added; a known open TEXT Choice needs
SameDomain, whose serialization is outside the pinned AIR codec. These are
separate from the one intentional production RED above.

Reproduction (Temurin Java 21.0.12; fresh source output directory):

```sh
export JAVA_HOME=/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-adapters -am \
  '-Dtest=ConsumerCoverageTest,ValueToCallEvidenceTest,ControlFlowEvidenceTest,Cics*Test,File*Test,W1d*Test,Regional*Test,StorageIndexTest,CfgBuildCoordinatorTest,NameInterpreterTest' test
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" \
  -pl analysis-adapters -am test
python3 -B scripts/project/check_analysis_gaps_w3_wire.py
python3 -B scripts/harness/lean.py fast
# Pinned producer build preparation is documented in W2 above; reuse only if pins/trees match.
mvn -B -ntp -Dmaven.repo.local="$PWD/.harness-results/build/m2" compile
python3 -B scripts/project/probe_analysis_gaps_w3.py \
  --producers "$PWD/.harness-results/w2-producers/producers.json" \
  --work "$PWD/.harness-results/w3-source-final"
```

Reused: immutable producer builds from W2; W2 source CALL controls explicitly
marked historical; earlier corporate W3.3 factoring qualification is not rerun
or relabeled. Newly run inherited regression oracles supplement that evidence.
Not run: full corporate/corpus qualification, full upstream suites, unrelated
PERFORM/GO TO/SQL/dynamic-FILE campaigns. The local change consumes a query
already planned; focal/full-module, public-wire, selected source and FAST gates
cover its invalidated evidence without requalifying unchanged shared solvers.

Implementation commit `a3edcd4`; source-probe commit `ecfe566`. Final report commit
and exact final HEAD are recorded in PR #41/Git. Parent #40 was rechecked before
final qualification and is still at the original validated SHA; no synchronization
is needed. If parent production later changes, incorporate its final head before
closing dependent implementation; after #40 merges update/rebase from main and
retarget #41 to main. No merge or auto-merge authorized.

**W3 COMPLETE — CONSUMER COVERAGE MAPPED; RESIDUAL GAPS CLASSIFIED.**

The discovery campaign is technically closed, subject to parent dependency /
retarget and final human review. This is not repository lifecycle DONE while
PR #41 remains unmerged. No upstream G3 campaign or inline G4 implementation
has been started. Await human review.
