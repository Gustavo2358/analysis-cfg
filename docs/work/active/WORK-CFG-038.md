# CP6 — compositionality and conservative partial lowering

Status: **IN_PROGRESS — IMPLEMENTED / AWAITING HUMAN REVIEW**. Git/PR/tests/merge are the source of truth; no receipts,
certificates, evidence bundles or hash chains. Replaces the former EVALUATE next step.

## Verified merge baseline

All three PRs merged; fetch, clean main and equality with origin/main verified
on 2026-09-12 before creating feat/compositional-partial-lowering.

- proleap-poc: `ff3704911e53b3d8b337fc610cf84343cdc5fe90`
- cobol-lower: `797dcbd6f05f12661299339746c607b5b57ac1ba`
- analysis-cfg: `664a932af0421d8a604b77d8de5852e7ac8db955`

analysis-ir remains read-only. air-java was extended with explicit user authorization after the transport RED below.

## Discovery

The initial matrix was recorded before production edits; AIR-only probes identified the generic consumer and codec gaps. Final review also verified the retained unique PRIMARY role constraint.

NEXT: CARDDEMO BASELINE

## Discovery before production

Locations below are at the verified merge baselines. No size gate is removed
merely because it contains a singleton check.

| restriction | location | classification | action |
| --- | --- | --- | --- |
| one PERFORM per unit | proleap-poc PerformSemantics:58,68; CobolSemanticProduct:1013; lower SupportedProgramAdmission:70,103 | ARTIFICIAL_CARDINALITY | remove; specialize each proven BASIC activation |
| exactly two direct paragraphs; all primary statements supported | proleap-poc PerformSemantics:89–118 | ARTIFICIAL_WHOLE_PROGRAM_SHAPE | separate intrinsic body from activation continuation in SP1.8; retain isolation proof where needed |
| final body MOVE carries global resume | PerformSemantics.applyCompletions; CobolSemanticProduct.validateStructure | LEGITIMATE_SEMANTIC_GAP | SP1.8 intrinsic body end, callsite resume in PerformFact; retain SP1.6/1.7 decoder meaning |
| every statement modeled, root-only controls, closed acyclic GOBACK path | lower SupportedProgramAdmission:58–78,87–119 | ARTIFICIAL_WHOLE_PROGRAM_SHAPE | local exact handlers or conservative fallback; preserve complete observed inventory |
| only MOVE/IF/PERFORM/CALL/GOBACK, only runtime CALL gaps | lower SupportedProgramAdmission:67,77 | ARTIFICIAL_WHOLE_PROGRAM_SHAPE | remove global refusal, publish operation and local uncertainty for every region |
| one IF/CALL in legacy entrypoints | lower IfAdmission:36, CallAdmission:61 | ARTIFICIAL_CARDINALITY | default uses SupportedProgramLowerer already; prevent legacy restrictions governing new composition |
| complete observed inventory, missing IDs, duplicate identities, inconsistent containment | lower EntryGobackAdmission.validate, SP validateStructure | STRUCTURAL_INVALIDITY | retain contradictory-fact rejection; distinguish incomplete source from malformed structure |
| PRIMARY_ONLY entry inventory has one unique PRIMARY role | SP EntryRole/EntryInventoryScope; lower EntryGobackAdmission.validateEntries | STRUCTURAL_INVALIDITY | retain unique role/usable start; alternate-entry gap remains explicit, not a program construction-count gate |
| resolved reference has one selected candidate; one precise elementary receiving operand | SP DataBinding, ScalarMoveSemantics; lower CallAdmission:153,179 | LEGITIMATE_SEMANTIC_GAP | retain exact-access proof; unsupported multiplicity of operands uses fallback, not whole-program rejection |
| USING/RETURNING/handlers refused | projector:727,957; lower SupportedProgramAdmission.admitCall | LEGITIMATE_SEMANTIC_GAP | preserve available target with partial signature, effects/outcomes; no invented argument semantics |
| nested/unsupported IF predicate or arm | IfSemantics.arm; lower IfAdmission.admitPredicate/arm | LEGITIMATE_SEMANTIC_GAP | preserve known structure/boolean domain when proved, else opaque |
| ObservedStatement contains kind/shape/gap only | SP ObservedStatement; projector:590; lower OtherStatement | LEGITIMATE_SEMANTIC_GAP | generic explicit conservative boundary facts; no lower reparsing |
| HavocMust/HavocMay refused by values | analysis-cfg TextProfile.prepare | LEGITIMATE_SEMANTIC_GAP | generic strong unknown/weak unknown transfer using existing state/lattice |
| Opaque refused by CFG | CoreCfgProjection.unsupported | LEGITIMATE_SEMANTIC_GAP | generic AIR envelope consumption; no source opcode dispatch |
| operation/source uncertainty aggregated across unit | TextProfile constructor, ReachabilityProvider.prepare | LEGITIMATE_SEMANTIC_GAP | preserve point-local model precision; distinguish global coverage from reachable control interference |
| codec rejects havoc/opaque; scoped envelopes not transported | air-java BindingWriter:86,236; BindingReader:178,215 | LEGITIMATE_SEMANTIC_GAP | user explicitly extended air-java scope after factual RED; implement existing binding forms only |
| depth/document/diagnostic/identity resource limits | SpJsonDecoder.Limits, AdmitInput.Limits, AirJson.Limits, CanonicalRevision | RESOURCE_LIMIT | retain explicit operational failures; no program cardinality caps |

## AIR-only baseline probes and scope extension

`ConservativeOperationBoundaryTest` builds four AIR publications: mandatory write,
possible write, opaque with known continuation and opaque with open label scope.
All four validate STRUCTURALLY_VALID at air-java
`760593b923ca7311f699547c36349f54eb0dac42`. Both havoc forms build CFG but fail
PossibleValues scalar-text-effects@1 with UNSUPPORTED_EFFECT_PROFILE and dependency
preparation fails ANALYSIS_UNSUPPORTED. Both Opaque forms fail CFG with
UNSUPPORTED_INPUT and dependency preparation fails CFG_UNSUPPORTED.
The codec rejects all four with IMPLEMENTATION_LIMIT before output. A precise
control case transports and resolves two PROGA sites with closed model remainders.

Commands: `mvn -o -B -ntp -Dmaven.repo.local=/tmp/move-cfg-build/m2 -pl analysis-adapters -am '-Dtest=EvalCfg025Test,StructureTest,ValuesTest,CompositionTest,NameInterpreterTest,ConservativeOperationBoundaryTest' test`.
Baseline characterization PASS: `/tmp/partial-conservative-boundary-3.log`.
Adding `-Dcp6.requireConservativeTransport=true` reproduces the product transport
RED in `/tmp/partial-conservative-transport-red.log` (four assertion failures).
Earlier logs retain a test selector error and an unrelated untransportable fixture
origin; the final fixture uses a Written origin and the precise control passes.
These are characterizations, not evidence that the requested product already works.

The user explicitly authorized extending air-java after this finding. Scope:
**air-json reader/writer and tests/docs for existing AIR2.0/binding1.0 forms only**.
No normative AIR, model, validator, lattice or fundamental solver changes are planned.
This extension is required by the file pipeline; encoding an opaque source operation
as a fake Invoke/Nop or private JSON would violate the architecture.

## Implemented boundary and focused results

SP1.8 publishes intrinsic BASIC body ends plus activation resumes; the lower
specializes both distinct and repeated targets. Observed regions retain nominal
references, proven normal continuation, gaps and provenance. Opaque normal edges
retain abnormal-exit uncertainty; absent control facts do not fabricate fallthrough.
A proved mandatory receiver uses HavocMust. CALL surfaces/unknown names and IF
semantic gaps preserve sites. Qualified storage roots survive unrelated unsupported
declarations, with no disjointness inferred from identifiers.

Synthetic AIR probes now pass for HavocMust, HavocMay and Opaque: overwrite kills
old values; possible writes retain known candidates plus remainder; disjoint writes
preserve precision; bounded control reaches later sites and has matching forward
/backward cursors. There is no COBOL-specific solver rule. Generic production in
CFG/structure, effect transfer and reachability was necessary; solver and Candidates
lattice sources remain unchanged. CFG JSON v3 is required for the new closed-domain
Opaque tokens; dependency result wire remains v1.

Development integration of all new lower AIR fixtures confirms P1 PROGA closed in
model, P2 PROGA/PROGB closed, P3 and READ PROGA plus model remainder, P4 disjoint
PROGA closed, mandatory overwrite empty/open, both precise BASIC cases, nested IF
and 1/2/5/40 composition. This is focal validation; exact pinned real-source E2E and
final CFG qualification are recorded below when complete.

Qualified producers: frontend `15354e946904337a74980ca659119f2eb22df57b`
([Draft #40](https://github.com/Gustavo2358/proleap-poc/pull/40)); lower
`bb377189aeaffa4c81f0a0a4c3d65a763974d5a7`
([Draft #17](https://github.com/Gustavo2358/cobol-lower/pull/17)); shared codec
`b1c45b8f23452082c9f3969b78915976747e87c7`
([Draft #12](https://github.com/Gustavo2358/air-java/pull/12)). Each has local
qualification PASS. Frontend, lower and codec FAST remote PASS on these exact heads.

## Integrated boundary corrections

The first exact-pin E2E exposed duplicate inputs in the lower's derived unit
origin: it reused entryOrigin as entrySequenceOrigin. The lower now links the
actual initial sequence origin and tests this lineage independently. The dependency
wire and its strict canonical-reference oracle were preserved.

A source-boundary review also found that a syntactic PERFORM resume does not
exclude execution of an unqualified target body. Observed compound control now
publishes unavailable continuation; `control-body` preserves both AFTER and INNER
CALL sites through the conservative bound. DISPLAY handlers similarly prevent a
sole-normal-successor assertion. No precise general PERFORM/handler semantics were
added. Final source qualification passed again after these productive corrections:
frontend `/tmp/partial-front-full-5.log` (608 tests, zero failures/errors, one existing
skip, normalizer and naming); lower `/tmp/partial-lower-full-5.log` (semantic, capacity,
transport and architecture). Control-only fallback after a proved Assign/HavocMust
has empty memory effects, so lack of continuation does not reopen the written value.

The unchanged PERFORM provenance oracle caught another integrated regression:
the specialized body-return origin only pointed to the resume. The lower now
derives the return from callsite, resolved target reference, paragraph, body write
and activation resume. Interior body transfers use intrinsic continuation origins.
The original provenance assertion was retained; this is a productive correction,
not a relaxed test expectation.

## Focal multiplicity size check

The permanent mixed fixtures produce the following sizes on the final lower source:

| N | SP statements | AIR sequences | AIR operations |
| --- | --- | --- | --- |
| 1 | 9 | 9 | 13 |
| 2 | 16 | 17 | 25 |
| 5 | 37 | 41 | 61 |
| 40 | 282 | 321 | 481 |

Output size grows linearly for this composition, including activation specialization.
The separate real-source stress has 20 CALLs, 10 IFs, 5 BASIC PERFORMs and 36 MOVEs.
These are focal regression sizes, not productive caps or a benchmark campaign.

Final producer snapshot commits above include test/documentation/pin-only guards
after final productive Full. The sealed fact inventory requires each nonterminal
semantic family to appear N times in the 1/2/5/40 compositions; adding a future
family without generators now fails. Both focused guards PASS; no productive
source changed, so those commits did not repeat Full.

## Final qualification and handoff

`python3 -B scripts/harness/lean.py qualification-local` PASS on final productive
CFG source, `/tmp/partial-cfg-full-1.log`, Java 21. Architecture, all Maven tests,
W1–W5 semantic/performance/integration, historical scalar/GOBACK, real W1/W2D,
MOVE→MOVE, PERFORM BASIC and seven MULTI-CALL fixtures passed. The seven per-site
candidate/support/remainder oracles were retained. No fallback opens model
remainders in a precise supported composition.

The 22 real-source partial/compositional programs passed A/B on SP, AIR, CFG and
dependency bytes at the final clean producer pins. Physical sequence permutation
preserves sites/edges. P1 preserves PROGA; P2 restores precise PROGB after the
unknown region; P3/READ preserve PROGA plus model remainder; P4's independently
proved disjoint write preserves PROGA; mandatory overwrite gives unknown value;
P5 keeps earlier evidence and exposes later control uncertainty. Both BASIC
activation cases are precise. The 20/10/5/36 stress has 20 PROGA sites with closed
model remainders. Source/runtime interpretation remainders remain separate.

Final producer qualifications: frontend `/tmp/partial-front-full-5.log`, lower
`/tmp/partial-lower-full-5.log`, codec `/tmp/partial-air-full.log`, all PASS. Frontend
has one pre-existing skipped test; none was introduced by this wave. Final test-only
family guards also passed. Local CFG FAST `/tmp/partial-cfg-fast-10.log` passed;
remote CI remains FAST ONLY. Full is not repeated for this documentation closeout.
The new work item stays IN_PROGRESS until human review and merge. No merge or
force/auto-merge was performed. Git/PR/tests/merge remain the record.

NEXT: CARDDEMO BASELINE. Run the corpus, count unsupported constructs and affected
programs, measure CALL-site impact, rank blockers by expected coverage gain and
rerun CardDemo after every vertical. No predetermined EVALUATE/GO TO/READ order.
