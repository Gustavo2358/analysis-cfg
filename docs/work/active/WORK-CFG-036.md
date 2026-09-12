# CP6 PERFORM BASIC

Profile: `SIMPLE_SINGLE_CALLSITE_PROCEDURE_PERFORM`. This is not implementation
of `control.local@1`. [Lean item](WORK-CFG-036.yaml); merge baselines are recorded
in [lifecycle](../cp6-lifecycle.json). All three fetched main trees equal their
qualified MOVE→MOVE trees, at distinct actual merge commits.

## Rule and proof obligation

Primary source read on 2026-09-12: IBM Enterprise COBOL for z/OS 6.4 Language
Reference, Basic PERFORM, printed pages 413–414 (PDF pages 440–441),
[official manual](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf).
The paragraph form executes its body once, then resumes immediately after its
PERFORM. Ordinary entry into that same paragraph does not take this implicit
return. This excludes unconditional paragraph-end-to-resume rewriting in general.

LANGUAGE_GUARANTEED: this basic paragraph execution/return rule and GOBACK ending
the current program invocation. ARCHITECTURE_GUARANTEED: canonical primary entry,
paragraph/sentence membership, direct statement lists and exact provenance;
the resolver's PERFORM_FROM occurrence binds to the paragraph symbol identity.
EXPLICIT PROFILE: one callsite, one target, one resume; no other modeled entry.

The conservative candidate shape is two direct nondeclarative paragraphs. The
primary paragraph is identified through ProcedureEntry.startStatementId and
membership, never its spelling. Its entire direct body is an optional linear
MOVE prefix, one PERFORM, one CALL and a final GOBACK. The other paragraph has a
nonempty linear admitted MOVE body. Complete statement and reference inventories
exclude GO TO, ENTRY, hidden control, alternate activation and extra PERFORMs.
The final GOBACK prevents ordinary primary-flow entry into the target. Target
resolution must be local, unique, canonical and of PARAGRAPH kind.

With these conditions, after PERFORM every target-body state has the same unique
pending resume. Erasing that constant continuation maps source execution to
Jump(target), the unchanged MOVE transfers, and Jump(resume). Every execution
step in the body and every storage value is preserved. The only external program
dependency site is the CALL at resume. This argument needs no frame stack, RD,
contextual solver, path enumeration or new AIR operation.

Source closure must be established upstream; the lower may use only published
typed identities, ordered body membership and explicit continuation/exit facts.
Canonical sentence relations supply semantic ordering; sorting IDs, global source
positions or JSON/sequence arrays cannot supply control. The current AST's
normalContinuations map intentionally covers MOVE/IF/CALL but omits PERFORM.
Discovery must establish whether the existing structural facts can support its
new canonical completion fact without changing AST or grammar.

## Counterexamples outside the profile

| Counterexample | Failed premise / consequence |
| --- | --- |
| Two PERFORMs of one paragraph | Return depends on callsite; two return edges add spurious executions. |
| Primary flow reaches target by fallthrough | Ordinary activation must continue beyond paragraph, not return to resume. |
| GO TO target or another ENTRY | Adds an activation without the fixed PERFORM return context. |
| PERFORM A THRU B | Exit belongs to a range, not this paragraph's last statement. |
| Nested PERFORM | Requires another continuation and violates linear MOVE body. |
| TIMES/UNTIL/VARYING/WITH TEST | Body can repeat or be skipped; single traversal is not equivalent. |
| Inline, empty, IF/CALL/EXIT/embedded target body | Different body/control semantics; outside this profile. |
| Unresolved or ambiguous reference | No unique canonical paragraph entry can be selected. |

Finite indexed passes over nodes, references and direct members suffice; O(N+R)
time and space, with no path enumeration. Completeness is deliberately limited
to this shape. Unknown or incomplete evidence must refuse, not infer a successor.

Initial discovery run: `mvn -B -ntp -Dtest=PerformDiscoveryTest test`, PASS,
2 tests, log `/tmp/perform-discovery.log`. It verifies canonical target/entry/body
authorities on the real fixture and typed control presence for grammar loop forms.
At that discovery checkpoint, no PERFORM production change had been made. The
AIR-only model probe was a separate gate before the production implementation.

Discovery verdict: PASS for the restricted shape above; no counterexample inside
that profile was found. Existing typed AST containment provides the source relation
needed to publish PERFORM completion in a new post-binding fact; grammar/AST changes
are unnecessary. This is not a physical-order heuristic in lower/CFG.

AIR model gate: PerformBasicModelTest PASS (5 tests), plus 25 focal reactor regression
tests, log `/tmp/perform-model-reactor.log`. Literal, copy composition, overwrite,
all 24 sequence permutations and skipped-body/wrong-return challenges passed.
PRODUCTION CHANGE IN analysis-cfg = NONE; solver/lattice/Jump semantics unchanged.

## Implementation and real-source probe

SP 1.6.0 publishes canonical typed procedure identity, target entry/ordered body/end,
unique normal continuation and the complete disjoint primary statement inventory.
The lower validates those relations and emits primary Jump → MOVE body → Jump
resume → Invoke → Return. Entry remains explicit with primary serialized last;
no LocalInvoke or other control.local operation is used. No grammar/AST change.

Final producer source pins and coordinated Draft PRs:

- proleap-poc `a2e9645a2d0fa5e86befdc6f60c6bf2b5cf6f84b`, [PR #38](https://github.com/Gustavo2358/proleap-poc/pull/38).
- cobol-lower `bcd9980bed1255910f71b58f056dbf9857510a26`, [PR #15](https://github.com/Gustavo2358/cobol-lower/pull/15).

Both producer final sources passed local FAST and one final qualification-local.
Raw logs: `/tmp/perform-frontend-fast.log`, `/tmp/perform-frontend-full.log`,
`/tmp/perform-lower-fast.log`, `/tmp/perform-lower-full.log`. Lower's final Full
also covers the exact final upstream pin and SP decoder future-version guard.
Frontend remote FAST passed [run 34718498847](https://github.com/Gustavo2358/proleap-poc/actions/runs/34718498847).

Real isolated producer-build/E2E probe PASS, outputs at
`/tmp/perform-basic-probe/e2e/`, logs `/tmp/perform-producers.log` and
`/tmp/perform-e2e-probe.log`. Literal paragraph, MOVE→MOVE composition and overwrite
all produce only CALLER → PROGA, raw `"PROGA   "`, modelValueRemainder=false.
Candidate support reaches the original literal MOVE; overwrite kills OLDPROG.
No PERFORM dependency site/edge exists. All three fixtures pass A/B byte comparison
for SP, AIR, CFG and dependency JSON, and AIR sequence reversal. Source and
interpretation remainder flags remain explicit; closed model values do not claim
complete runtime program-resolution knowledge.

Local CFG FAST PASS, `/tmp/perform-cfg-fast.log`. The final qualification-local
includes this real E2E after W1/W2D/MOVE-copy regressions; it is never remote.
The source-only negatives cover THRU, TIMES/UNTIL/VARYING/WITH TEST, inline,
unresolved/ambiguous target, two callsites, ordinary fallthrough, GO TO, IF,
nested PERFORM, CALL, empty and other non-MOVE bodies. EXIT PARAGRAPH is already
rejected by the existing parser; this wave does not alter the grammar.

Focal challenges: model oracles reject bypassed body and wrong return; lower rejects
wrong target entry/end/resume, altered membership, invalid profile and second
callsite; source tests reject THRU and multiple callsites. Reversed source inventory,
24 model sequence permutations and real AIR reversal detect order-as-control.
These are focal tests, not a broad mutation campaign.

SUPPORTED: single-callsite procedure paragraph; linear admitted MOVE body;
unique static resume in the closed two-paragraph shape above.
NOT SUPPORTED: THRU, loops, inline, multiple callsites, nested control or other
ordinary target entries. Solver/lattice/strong update/CFG Jump/AIR unchanged;
RD and generic PERFORM stack not implemented. NEXT: EVALUATE (no work authorized
by this navigation entry). Review/merge remains human-only.

## Final review state

**IMPLEMENTED / AWAITING HUMAN REVIEW**. [CFG Draft PR #22](https://github.com/Gustavo2358/analysis-cfg/pull/22).
WORK-CFG-036 remains IN_PROGRESS under Lean until merge. No PR was merged.

Final local `python3 -B scripts/harness/lean.py qualification-local`: PASS once in
all three repositories. CFG tested source `656b2cb7568ac253af84248e3f99106a9eb4caaa`,
with the exact final producer pins above; raw log `/tmp/perform-cfg-full.log`.
Its final real E2E outputs are at `/tmp/move-cfg-build/w2d-tdqgmvia/perform-basic/`, including SP/AIR/CFG/dependency
A/B and permuted AIR/results. The final follow-up changes only this review record,
navigation and the remote test selector: the skipped-body/wrong-return falsification
is excluded from FAST and retained in the full local suite (all 5 model tests PASS).
No production or Full test/fixture changed; Full was not repeated for this follow-up.

Remote FAST results on implementation sources:

- frontend: [34718498847](https://github.com/Gustavo2358/proleap-poc/actions/runs/34718498847), PASS;
- lower: [34718604740](https://github.com/Gustavo2358/cobol-lower/actions/runs/34718604740), PASS;
- CFG: [34718660286](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34718660286), PASS.

The first CFG run included the cheap in-memory falsification method; the final
selector removes it to keep that challenge local. No production mutation campaign
or cross-repo E2E ran remotely. GitHub checks on the final Draft head are the
remote authority; no additional receipt/certificate is created.

Final local FAST selector check: PASS, `/tmp/perform-cfg-final-fast.log` (64.092s).
