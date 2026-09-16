# EP-R1 — Logical evidence under equivalent query representations

Status: READY FOR HUMAN REVIEW. Remediation in Draft PR #37; no merge authorized.

## Review and baseline

F1 was reported by human code inspection, not as an executed RED. Local reproduction
at `3469a41418f4f84e8b615cbc25ae9c7aaad7a5b5` confirms that normal AIR validation
accepts the fixture, but a canonical ObjectPlace occurrence loses the logical entry
candidate returned by NamedObject, BEFORE the first operation. The first failing
boundary is state → query projection. Baseline tree was clean; fetch confirmed the
same remote branch head and main `c4be7246de617b6d283aac19b60239fae88f6eb8`.

## Decision and authority

Project explicit object identities in shared storage query preparation: NamedObject,
ObjectPlace, and the alternatives of Choice. Read only the current RD/value state.
Do not traverse binding aliases, Choice remainder, RegionSlice address expressions,
or physical ranges to invent an object identity. Physical interpretation and logical
support are additive; neither is a permission gate for the other. Apply the same
projection in reads/copies, retaining the selected logical identity in captured support.

Regional result transport must explicitly describe occurrence object references in a
new version; older closed shapes keep their meaning. This is a CFG result contract
change, not an AIR normative or capability change. F2 corrects the nested normative
pin to the snapshot declared by the already-pinned air-java commit.

## Qualification plan

Required evidence = representation/copy regression + applicable recall/kill laws +
mixed physical/logical and open-alternative contrasts. Run focused RD/values/Choice,
writer and independent reader (including invalid-wire contrasts), selected strict AIR
memory/CLI E2Es, then one CFG FAST on stabilized content. Reuse other products' gates;
do not rerun all 49 source verticals, historical campaigns, or broad corpora.

Counterexperiment: add a physically represented alternative and an open alternative
to the logical Choice; then copy and overwrite the destination. This can disprove a
fix limited to query presentation or a union that reseeds historical declarations.
Negative identity cases must prevent support from leaking through aliases/addresses.

REAL CASE = NOT AVAILABLE. Closure of F1 does not establish complete COBOL semantics.

## Executed evidence and counterproofs

- Minimal RED, reviewed head: four semantic assertion failures, not compiler or
  admission failures. Named vs ObjectPlace occurrence and Named vs singleton Choice
  fail independently in RD and values. Strict AIR admission completed normally.
- After query-only repair, copying singleton Choice into a Cell still lost the
  candidate. That independent RED forced the read/copy repair as well.
- Seven representation tests cover current candidates, supports, source/model
  remainder, logical RD definitions, nested explicit identities, alias/address/range
  negatives, and copies into logical, Cell, and regional destinations. Eight copy
  combinations cross Cell/bytes × singleton/mixed × extra unknown alternative.
  Mixed copies include two physical alternatives with distinct source intervals.
  Exact subsequent overwrite kills copied values; an unproved logical destination
  only weakly adds its new value. No query/backedge reseed was added.
- Four strict AIR E2Es: direct, singleton Choice, logical + physical, and extra unknown
  alternative. AIR model/codec roundtrip, RD BEFORE, Regional Values BEFORE,
  DependencySite, and actual dependencies CLI agree. PROGA and its support remain;
  mixed cases also publish PROGB. Memory/CLI JSON is byte-identical.
- Choice logical→bytes transport matches direct-copy observations before/after a
  later source change and after a destination MUST. LogicalCapture retains the
  selected object and BEFORE instant without inventing a source range.
- Independent regional reader: 11 tests pass, including 35 new occurrence identity,
  support/remainder/version mutations; previous 10 capture, 8 entry, and 27 physical
  wire mutations also die. Versions 1.0–1.3 retain their existing representations.
- Four selected policy mutations die after passing unmutated oracle baselines:
  occurrence-hides-logical, choice-hides-logical, query-reseed, unknown-storage-empty.
  Sources restored, followed by unmutated rebuild and tests. The harness now requires
  a passing baseline before mutating an oracle. No complete historical mutation run.
- Focused family: 73 tests/16 suites pass; the additional logical-destination focus
  passes 17 tests/3 suites. Counts overlap and are not summed as unique tests.
- Compiled architecture inventory changes are limited to explicit capture identity /
  physical alternative position and detached Observation.explicitObjects. No new
  solver, consumer VALUE union, dependency schema, AIR capability, or AIR pin.

An exploratory multi-physical fixture initially omitted the positive separation
contract required by its simultaneous strong entry facts. Its admission failed;
that run and a premature exploratory mutation run are retained but excluded from
qualification. The corrected fixture states the separation premise explicitly and
passes unmutated before the qualified mutations. No production admission check was
relaxed. Likewise, fixture compilation/role mistakes are setup failures, not REDs.

## Gates and scope of claims

- Qualified production/pin snapshot: `3024fb493fd2b073a3acbc684bc80460e12c50c6`.
- CFG FAST: PASS once on the qualified snapshot; 478 tests / 83 suites, zero
  failure/error/skip, readers and architecture pass; elapsed 76.988 seconds.
- REUSED: unchanged KillAuthority, weak-update and entry-coexistence obligations;
  prior campaign source/IR/AIR/lower qualification at their recorded heads.
- NOT RERUN: the 49 source verticals, other repos' FASTs, CardDemo73, all-repo full,
  Storage/CICS/DVI25, or complete historical mutations. This delta changes CFG
  projection/capture/result transport; F2 is metadata only.
- LOCAL FIX: executable RED→GREEN at the first loss boundary and at copy transfer.
- ARCHITECTURAL LAW: equivalent explicit identities preserve current logical support
  through NamedObject/ObjectPlace/Choice, including physical/logical combinations.
- INCIDENT CLASS: the query-representation crossing is now exercised in product
  output; old W5 keyword/generic evidence is reused, not presented as rerun here.
- REAL CASE: NOT AVAILABLE. No claim of full language/layout/codec support or 4/4.

What this proves: representation changes do not hide the current supported values
in the covered canonical forms. What it does not prove: every expression, codec,
or future Place extension is interpreted. Independent causes remain: absent source
support, unsupported expression/repertoire, real MUST overwrite, invalid publication,
or resource failure. The explicit-object projection returns no identity for physical
ranges/RegionSlice addresses and never infers aliases or disjointness.

F2: nested normative commit is `6b8ce96f5b1020199e3fcceedf81d931a3b8d2ff`, read from
`air-java@5b8a5c231958b62de34583d871cbb10b473d53e8:docs/sources.lock.json`. The consumed
AIR commit and Maven artifact remain unchanged. No semantic requalification is
attributed to that metadata correction.
