# EP-W0 — Evidence-Preserving Partial Analysis

Status: **GO for EP-W1**; production **NOT STARTED**. The user resumed the campaign
after the hygiene stop and authorized pulling main and creating working branches.
Five `pull --ff-only origin main` operations passed. No open PR was returned by the
fresh GitHub query. The original product checkouts are clean and updated; all
campaign edits are confined to new worktrees. Existing evidence files remain intact.

## Decision and falsification experiment

Source-supported possibilities are independently owned facts. A precision proof
can strengthen them; missing layout, lifetime, effect or alias proof cannot delete
them. The precision order must preserve support: `(candidates, remainder)` retains
the enumerated source-supported alternatives when remainder opens. Candidate absence
is justified by absent/invalid source evidence or a positively proved executed kill,
never by failure to establish an unrelated proof.

**What would show that this architecture only fixes known witnesses?** Replace a
typed, unrelated declaration clause at the AST boundary with a preserved arbitrary
clause named JOHNDOE, with no write or MUST proof. Independently remove the target's
physical offset proof and its containing region's extent proof. If PROGA disappears
in any transformation, the architecture is still gated by a known mechanism. Vary
the opaque name, ownership, statement/effect family, arguments/results and input gap;
combine with a branch and with a real exact MUST overwrite. The last contrast must
remove the old current value, while the unknown/MAY variants must preserve it.

The new AST-boundary test already produces a semantic RED for JOHNDOE without adding
syntax to the grammar. Two additional REDs show the missing-offset and missing-base-
extent gates. These are independent obligations, not alternate spellings of SYNC.

## Boundary and authority map

Paths below are relative to the named worktree at the recorded baseline.

| Boundary | Knowledge entering | Current possible loss/globalization | Authority to remove | Required EP rule |
| --- | --- | --- | --- | --- |
| source → AST | valid VALUE syntax, literal, owner, origin; independent partial constructs | `AstBuilder.mapDataClause` preserves an unknown clause; coverage marks it uninterpreted | source semantic recognizer may reject an invalid literal; partial coverage alone has no kill authority | preserve recognized source facts and explicit gaps |
| AST → storage components | declaration identity, parent, clauses, relation witnesses | `StorageComponents.analyze` uses one allocation boolean for visibility and any preserved clause across the unit | only typed evidence can prove scope or separation; names, IDs and order cannot | represent uncertainty with owner/scope/dimensions/provenance; broad unknown may remain broad |
| components → layout | root/component identity, local shape and relation proof | `StorageLayoutSemantics` propagates allocation to all bases; environment and parent extent/offset gates can erase precise views | physical proof owns bounds/allocation only | unknown layout is not absence of logical VALUE evidence |
| layout + VALUE → initial evidence | valid literal, known source type/representation, layout and lifecycle proof | `StorageInitialSemantics` requires exact view and base bounds, then `reasons.isEmpty()` before encoding; nonlocal allocation blocks even possible literals | source evidence extraction owns validity/support; DVI owns precision upgrades only | split extraction from invariant proof; no EntryState.literal assertion for persistent arbitrary activation |
| semantic facts → SP | source VALUE and support, entry proof, uncertainty | `CobolSemanticProduct` consistency currently requires bounded physical view for every non-UNKNOWN condition; projection cannot invent missing analysis | projector only transports canonical facts | publish source evidence, precision and remainder independently; version contract explicitly |
| SP → lower admission | typed source conditions and physical facts | `RegionalStorageAdmission` requires exact bounded view and independent allocation even for DECLARATIVE_POSSIBILITY | structural contract checks may reject malformed or contradictory facts | separate possibility admission from strong physical initial-state admission |
| lower → AIR | supported candidates, source identity, origin and uncertain layout | `RegionalEntryTranslator` emits literal possibilities only with a bounded Place; `RegionalDataTranslator` drops unrepresentable bases and retains unknown objects without type proof | translator owns representation, never source reanalysis or speculative allocation | transport a supported logical candidate under unknown binding without fake Cell/disjoint proof |
| AIR → model/codec | entry possibilities and explicit remainder; object/storage distinction | normative §13 and `OperationChecks.possibleEntrySeparation` require every condition to be exactly located and disjoint | analysis-ir defines legality; air-java implements it | normative revision before downstream changes; maintain missing-reference/type/codec/strong-contradiction rejections |
| AIR/effects → RD | entry events; MAY/MUST writes and target resolutions | `ReachingDefinitions` rejects mixed/uncertain overlap; sparse entry unknown is separate from bottom | only a positively proved write on the modeled transition may kill | possibilities join at invocation boundary; semantic uncertainty does not globally reject; backedges never seed |
| RD/storage → Regional Values | supported definitions, bytes/scalar alternatives, remainder | `RegionalValuesAnalysis.prepare` inherits RD global refusal; `write` has separate replace/weak decisions | centralized write authority from canonical effects, exact destination, full coverage and required outcome | share authority with RD; extend existing domain for logical evidence if physical location cannot be proved, using the existing solver and replayer |
| values → DependencyAnalysis | BEFORE observations, literal targets, source support and reachability | `PlanningExecution` stages a whole consumer and skips it when any prerequisite fails; `DependencyAnalysis` rejects any non-STABLE analysis before publishing sites | structural invalidity can globally reject; a semantic limitation has only local scope | isolate consumers/results by site and prerequisite; publish PARTIAL/UNKNOWN with reason for affected sites; preserve DIRECT |
| DependencySite → JSON | supported raw and interpreted candidates, remainder and origins | CLI maps analysis refusal to exit 5 before file write; current schema lacks an explicit failed-analysis site state | serializer owns wire validity only | use primary dependencies.json to represent partiality; keep atomic rejection for truly invalid publication/output |

### Code anchors

- frontend: `StorageInitialSemantics`, `StorageMutationInventory`, `StorageComponents`,
  `StorageLayoutSemantics`, `semanticproduct/CobolSemanticProduct`,
  `semanticproduct/projection/CobolSemanticProductProjector`.
- lower: `RegionalStorageAdmission`, `RegionalDataTranslator`, `RegionalEntryTranslator`.
- AIR: normative `especificacao/03-memoria-e-aliases.md` and
  `13-possibilidades-de-entrada.md`; model `OperationChecks.possibleEntrySeparation`.
- CFG: `StatementEffects.targets`, `ReachingDefinitions` initialization and `Engine.apply`,
  `RegionalValuesAnalysis.prepare/write`, `PlanningExecution.execute`,
  `CallDependencyConsumer.lookup`, `DependencyAnalysis.prepare`, `AnalysisDependencies`.

## Authoritative kill rules

1. `widenUnknown` retains every supported alternative and opens appropriate remainder.
2. `weakUpdate` unions old and new supported alternatives; MAY never becomes a kill.
3. `strongOverwrite` requires canonical positive evidence for an exact destination,
   execution/outcome on the modeled path, complete coverage of the affected region,
   and sufficient alias/storage proof. Missing any obligation selects weak/widen.
4. Partial writes can kill only proved covered fragments; branch joins preserve
   alternatives from paths that do not execute the overwrite.
5. Distinct IDs, source order and names prove neither separation nor total overwrite.
6. DVI may establish invariant/fresh/program-initial precision. Its failure is not
   permission to hide a recognized source candidate.
7. Entry possibilities are boundary inputs, not executable writes. Queries and
   backedges cannot resurrect an overwritten declaration value.
8. Positive invalidity (dangling reference, impossible required type/codec, conflicting
   strong simultaneous facts) remains grounds for structural rejection. Unknown proof
   is not positive invalidity.

## Required contract changes and repository decision

All five product repositories need isolated worktrees:

| Repo | Required responsibility |
| --- | --- |
| proleap-poc | extract declarative possibility independently of DVI/layout closure; publish source support; scope proof uncertainty without declaring preserved clauses harmless |
| analysis-ir | evolve possible-entry contract to distinguish supported alternatives from exact simultaneous conditions and permit evidence independent of physical layout; state kill and local-incompleteness rules explicitly |
| air-java | model/validator/codec support for the normative revision; preserve structural rejection and backward handling explicitly |
| cobol-lower | admit and transport source evidence under open allocation/layout without strengthening it; immutable producer/model pins and SP version updates |
| analysis-cfg | shared kill authority, possible entry coexistence, logical evidence in the existing solver when physical mapping is unavailable, site-local preparation failure and honest JSON |

The initial normative choice is a separately negotiated revision of entry possibilities
(`entry.possibilities@2`) rather than silently weakening @1's exact/disjoint requirement.
Representation design must keep ObjectId distinct from StorageId. Source logical evidence
must not manufacture a physical region, scalar Cell or alias proof. The existing dataflow
domain can be extended; a second solver or query-time declaration union is prohibited.
Strong @1 literal and legacy exact behavior remain supported. Wire/model/API changes and
SP storage version must be explicit, with bilateral tests and immutable pins.

Scoped uncertainty must retain its real upper bound. An arbitrary preserved clause may
still require unit-wide allocation remainder if scope is unproved. This is acceptable:
the unit-wide remainder may degrade precision but cannot remove independent source evidence.

## Frozen oracles and results

- **NEW frontend:** `EvidencePreservationTest`: 7 tests, 4 semantic failures, 0 errors,
  0 skipped. REDs: preserved declaration, arbitrary JOHNDOE, unknown base extent,
  unknown offset. Three controls pass: statement/operand unknown preserves evidence,
  absent/invalid source literal fabricates nothing, future MUST is not lifetime invariant.
- **NEW model:** `EvidencePreservingEntryChecks`: four RED cases (two orderings each
  for overlapping possibilities and unproved inter-base separation); strong conflicting
  literal control continues to reject I-17. No separation premise is fabricated.
- **REUSED historical:** `historical-red-oracles.json` freezes 13 selected raw discovery
  products with hashes: USING, DISPLAY, SYNC/SYNCHRONIZED, JUSTIFIED, BLANK WHEN ZERO,
  SIGN, branch, two VALUEs with unknown separation, and literal DIRECT plus computed site.
  Ten REDs and three GREEN controls. No discovery rerun and no claim that current fixes exist.
- Initial test construction mistakes (one Java enum name; reserved COBOL AREA identifier;
  Python import path) remain in earlier logs. They are setup failures, not semantic REDs.
  The qualified runs are `frontend-red-03.log` and `air-model-red-02.log`.

Further wave oracles must inspect source facts, SP candidate/support/remainder, AIR entry
and target, RD BEFORE, Regional Values BEFORE, DependencySite and primary JSON. Record
the first losing boundary, including a refusal before later boundaries exist.

## Lean gates and qualification plan

W0 is C1/C0: focused oracle execution plus documentation checks; no production FAST.
W1/W4 are C3; W2/W3 touch C4. For each production wave: changed regression + applicable
laws + incident contrasts, focused family tests while editing, one FAST per changed
production repository at wave closure. Contract changes add bilateral model/codec/
admission and selected E2E. No automatic full, CardDemo73, Storage/CICS/DVI or historical
mutation campaign. Reuse only evidence with unchanged/proven-equivalent material inputs.

W2 policy mutants: UNKNOWN→clear, MAY→kill, alias without proof→kill,
query/backedge declaration reseed, unknown storage→empty. Each must be killed by a law.
W5: M1–M5 across statement/data/layout/argument/result/effect/input-gap families;
pairwise main factors and the four required high-risk triples from the campaign request.

## Hypothesis-resistant W0 qualification

**Proved:** current source, admission and preparation boundaries contain independent
recall gates; generic JOHNDOE and loss of physical proof falsify current preservation;
normative change is required for possible-entry coexistence.

**Not proved:** any production correction, solver law, end-to-end recovery, failure
containment, arbitrary COBOL correctness or real confidential-source improvement.

**Counterproof executed:** no VALUE does not create a candidate; invalid literal fitting
does not create bytes; strong simultaneous contradictory literals remain invalid;
known exact MUST historical output is PROGB rather than reseeded PROGA.

**Independent causes still possible:** open target binding/type admission, incomplete
consumer prerequisites, initialization order, codec policy, symbolic layout loss,
control bypass and serialization. Passing the source mechanism cannot close the incident.

LOCAL FIX = NOT IMPLEMENTED. ARCHITECTURAL LAW = NOT QUALIFIED.
INCIDENT CLASS = RED. REAL CASE = NOT AVAILABLE — confidential source. No 4/4 claim.

**GO:** implement EP-W1 starting with normative justification and source/precision
separation. W0 authorizes no keyword whitelist or new COBOL capability. Final human
review occurs only after EP-W5, with one persistent Draft PR per repo and no merge.
