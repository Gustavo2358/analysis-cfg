# PERFORM control completion

Implementation and qualification complete. Integration is recorded in
[PR #47](https://github.com/Gustavo2358/analysis-cfg/pull/47); merge authorized by the user.
Scope: integrate SP 2.48 and its lower producer; add source regression oracles.
CFG construction and dependency analysis use their existing AIR contracts.

## Findings and semantics

The source topology previously lost four independent facts: paragraph completion
at EXIT PARAGRAPH, lexical inline exit at EXIT PERFORM, resolved SECTION endpoints,
and the binding for VARYING with AFTER levels. The first two left the continuation
CALL unreachable; the latter two stopped the executable closure before that CALL.
The producer publishes these facts; lower translates them into the existing AIR
jumps, branches and effects. No dependency target or reachability is injected here.

The lower preserves an out-of-line PERFORM activation across an explicit GO TO
outside its range. EXIT PARAGRAPH only unwinds intervening inline frames. Return
still occurs when execution reaches the published endpoint. Nested EXIT PERFORM
selects its own inline continuation; CYCLE selects iteration completion.

SECTION entry includes any preamble, and completion is bounded by that section.
VARYING levels have independent predicates, updates and current-FROM resets, in
BEFORE/AFTER order. Numeric values remain open. Recursion and unsupported operands
retain explicit limitations; existing legacy profile gaps remain historical facts.

## Reproduction

`analysis-adapters/src/test/resources/cp6/perform-completion/expected.json` contains
25 reviewed synthetic source oracles with SHA-256 hashes. The oracle compares
candidate sets AND materialized edges at each CALL, requires source-backed supports,
and rejects reachable dead calls or forbidden targets. It never rewrites expected.

```sh
python3 scripts/project/e2e_perform_completion.py \
  --runtime /absolute/path/runtime.json --work /absolute/path/new-result-directory
```

The runtime uses the `carddemo_setup.py` stage/main/classpath/checkouts/sources
format, the locked frontend/lower commits and clean checkouts. The lower stage must
use `CobolDependencyInput` to publish the AIR/source bundle. Jar hashes are checked
before and after; raw SP, AIR, CFG, dependencies and logs are retained per fixture.
`--fixtures` and `--manifest` also allow running the unchanged original 39-case
PERFORM suite without copying or changing its expected results.

Cases cover paragraph dead tails and ordinary completion, IF/EVALUATE, nested inline
exits, CYCLE, ignored EXIT PERFORM outside inline, repeated callers, external GO TO,
empty SECTION/paragraph, preambles, section bounds, mixed THRU endpoints (including a start paragraph inside its ending section), ordinary
section transitions, two/three VARYING levels, nested PERFORM, body and continuation.
The lower's FAST suite separately checks actual AIR phase edges, memory reads and
writes, codec roundtrip, inventory permutations and malformed contract rejection.

## Review evidence

The integrated campaign first recovered the original suite from 35/39 to 39/39.
One intermediate regression in inline VARYING exposed missing numeric declaration
facts and duplicate fallback identities; both were corrected and added to tests.
An additional GO TO/EXIT PARAGRAPH adversary exposed premature loss of an active
procedure endpoint; its source oracle and lower FAST regression guard that return.

The 73-program real corpus retains PARTIAL status in all four stages. Its first
comparison preserves all 121 program, 271 file and 523 source relations, with no
new relation or loss and none of the five known false-positive regressions. All
candidate supports retain their producer, kind and written provenance. Namespace
IDs and additional source evidence change in 38 products because inline repetition
now publishes typed facts; normalized dependency semantics and support paths agree.
Exact qualification pins, gates and rerun results are recorded in PR #47 and the alias follow-up #48.

No ALTER implementation or unrelated correction is included. No AIR model or
normative IR change is required. Numeric evaluation and existing unrelated corpus
gaps are not claimed as complete.

## Qualification closeout

The original PERFORM suite passes 39/39 and the source adversaries pass 25/25.
The later alias MOVE correction retains both results and adds 14/14 alias
adversaries. Required local and remote FAST gates passed. The 73-program CardDemo
comparison retains 121 program, 271 file and 523 source relations and their
supports; existing PARTIAL states and numeric/recursion limits remain explicit.
The final documentation changes no production, contract, fixture or build input.
