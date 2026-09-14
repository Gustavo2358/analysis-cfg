# CICS review remediation — F1–F3

This is a short remediation of the same four Draft PRs, without merge. It does
not restart W0–W4 or claim a new global qualification.

## Results and reproducible counterexamples

| Finding | Executed RED | Correction and observed GREEN |
| --- | --- | --- |
| F1 | Ordinary LINK at a paragraph boundary lost its known return. The real dependency query reported AFTER as UNREACHABLE_IN_MODEL with no candidates. | Separate paragraph-local and ordinary continuations in SP 2.14.0. Both `link-paragraph` and the copied-SP `link-unavailable` report AFTER reachable with candidate AFTER. |
| F1/PERFORM | The previous fixtures did not end the performed paragraph at CICS. | `perform-last-link` and `perform-last-xctl` check the activation resume, distinct ordinary successor, and actual downstream AFTER query. LINK has its success edge; XCTL has only the local error remainder. |
| F2 | The public decoder/lower accepted a NOHANDLE fact labeled DEFAULT_ENTRY_PREFIX. | Producer and lower reject contradictory typed options/gaps. A real XCTL/NOHANDLE SP first lowers successfully; changing only conditions is rejected by the public decoder/lower and CLI. The prior output remains byte-for-byte intact. |
| F3 | Disabled CICS still produced a gap-free PERFORM with one proved paragraph; lower rejected the contradictory opaque-body document. | Active and disabled LINK/XCTL tests exercise ExplorerMain composition. Disabled retains opaque statements, PERFORM_PARAGRAPH_BOUNDARY_NOT_PROVEN and an empty procedure proof; both disabled E2Es lower and query successfully. |

The [fixtures](../../scripts/project/cics_fixtures.py) are handwritten. The
[existing E2E runner](../../scripts/project/e2e_cics_program_control.py) checks
AIR/CFG/dependency JSON through the production CLIs and actual candidate queries.
Its LINK expectation no longer depends solely on the producer claiming KNOWN.
The missing-return case writes a separate mutated SP; the original producer
output is retained. No production input/output is rewritten to obtain a PASS.

An initial new oracle incorrectly expected OUTSIDE to be globally unreachable
after a performed LINK. The later opaque COBOL CALL has an AllControl remainder,
so that expectation was invalid. The corrected oracle checks the CICS activation's
specific success/error destinations and the downstream query. It retains the
open CALL remainder and makes no global exclusion claim. Initial logs are kept.

## Validation scope

- Frontend: 16 focal CICS/PERFORM/composition tests; FAST 246, all passed.
- Lower: FAST 2340 core checks plus its existing fixed adapter suites and 21
  harness tests, all passed. CICS includes six storage fixtures, the unavailable
  LINK fallback and the contradictory-XCTL/unchanged-output test. A subsequent
  test-only strengthening checks rejection under both SP 2.12 and 2.13; the
  CICS suite was repeated successfully, without rerunning unaffected gates.
- Integrated: 18/18 selected E2Es passed. They cover six review counterexamples
  and a short regression of CALL, LINK, XCTL, IF/PERFORM, BEFORE reads, overlaps
  and foreign effects. [Raw E2E output](cics-review-e2e.log) and
  [public lower-suite output](cics-review-lower.log) are included for review.
- CFG production Java is unchanged by this remediation. FAST passed with 437
  tests plus compiled architecture/harness/reader checks. Existing AIR model/JSON
  evidence and the immutable AIR pin are reused.

The integrated semantic producers are frontend
`be2b74bff2a37c3970a0e5f754a77ab430cd7cd8` and lower
`6837010f27ffee62fcef16ec7573da4a3a72d46d`. The final lower head adds test assertions
only; the final pins are in [sources.lock.json](../sources/sources.lock.json).
CFG semantic Java and AIR remain identical to the already reviewed heads
`4962eb677e37b7b53f0ac43007ab4b69093494f8` and
`0af506962ac719c3cff7603d81e51a66f875f704`, respectively.

## Commands

Use Java 21 and the product harnesses' prepared builds, with their pinned AIR
checkout and isolated Maven cache. After a clean FAST, restore the runtime
classpath files using the cached Maven dependency plugin (no new build):

```sh
# In frontend, then in lower (adding -pl adapters for lower):
mvn -o -B -ntp -Dmaven.repo.local="$M2" \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -Dmdep.outputFile=target/runtime-classpath.txt

# In analysis-cfg; WORK must be a fresh output directory:
python3 -B scripts/project/e2e_cics_program_control.py \
  --frontend "$FRONTEND" --lower "$LOWER" --m2 "$M2" --work "$WORK" \
  --cases link-paragraph link-unavailable \
  perform-last-link perform-last-xctl \
  perform-last-link-disabled perform-last-xctl-disabled \
  link-literal before-overlap after-overlap if mixed overlay \
  xctl-default xctl-nohandle xctl-resp xctl-unknown perform-link perform-xctl
```

## Support and limits

| Surface | Support |
| --- | --- |
| Literal PROGRAM | CICS identity/profile preserved; no automatic COBOL naming policy. |
| Variable PROGRAM | Existing storage/RD/possible values at BEFORE; physical eight-byte IBM1047 view required. |
| LINK | Ordinary cross-paragraph return, explicit PERFORM resume, conservative missing-return fallback. |
| XCTL | No successful local return; RESP/NOHANDLE local error routes retained. |
| Disabled | Syntax and opaque observations retained; no CICS paragraph-control contribution. |
| Signature/options | Parameters/results remain partial; COMMAREA/CHANNEL/RESP and unmodeled effects stay explicit and conservative. |

SP 2.14.0 deliberately supersedes Draft 2.13.0; old CICS documents are rejected,
not silently upgraded. AIR 2.0.0/JSON 1.0.0 and dependency JSON 1.1.0 are unchanged.
No plugin framework, new solver, general interprocedural model or other COBOL
family is introduced. Handler/runtime/catalog resolution remains conservative.
The historical naming failure in the unchanged frontend storage qualification
file remains documentary PARTIAL. The 73-source corpus, historical mutations and
full suites were not rerun for these localized corrections; their prior evidence
is neither relabeled as a new run nor used to conceal the review counterexamples.
