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
