# EP-R1 — Logical evidence under equivalent query representations

Status: IN_PROGRESS. Remediation in Draft PR #37; no merge authorized.

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
