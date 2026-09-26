# R9 — qualified source dependency evidence

Status: IN_PROGRESS. Scope: lower → dependency source contract only.

The authorized contract is `qualified-source-dependencies` 1.0.0. It transports
validated source occurrence identities, literal value authority and the existing
R7 finite source/state graph, separately from executable AIR. The dependency
result uses 2.6.0 with `sourceQualifiedDependencies` when this input is supplied;
the legacy path remains 2.5.0. SP and AIR contracts do not change.

Qualification is a graph formula: incoming derivations are alternatives (OR);
source and caller-premise references of one derivation are conjunctive (AND).
An exceptional derivation references exactly one R7 selection, whose event owns
its guards/proofs. No union of guards across alternatives is permitted. Roots
represent ordinary supported authority. Nodes/contexts are source identities,
never executable operation/entry/label IDs. Cycles denote the finite least fixed
point already established by R7, not enumerated execution histories.

The producer projects validated SpInput and HandlerStateAnalysis; it does not
rescan source or solve values. Literal logical values are preserved with operand
provenance. Computed or unavailable targets have no new value candidates and an
open value remainder. Consumer admission validates identity, graph references,
selection/ACTIVE-registration/DEACTIVATED-entry correlation and bounded
remainders; it does not run a handler semantics engine. Candidate interpretation
uses the existing nominal policies and retains every qualification reference.

Projection is linear in the transported inventories, excluding canonical sorting
and existing R7 analysis. Reference admission uses indexed graphs; activation
checks additionally inspect the registrations of the selected target. Proof graph and derivation graph validations
terminate over finite inventories. No path enumeration. Wire documents carry
SP contract version/SHA-256 and correlated AIR publication identity/SHA-256.
Version/unknown fields, dangling refs, invalid identities and mismatches fail
before publication. Missing topology is explicit unavailability, never absence.

Oracles: unchanged AIR bytes/CFG edges, previously lost HANDPGM/CONDPGM, exact
R7 selections/supports, independent decoder mutations, separate alternatives,
computed-value emptiness, deterministic output, and corpus provenance audit.
T1–T12 and both repository FAST gates remain mandatory.

AIR correlation is optional (zero or one) for standalone source evidence. Combining
source evidence with an AIR dependency result requires exactly one matching
publication/digest; absence never permits an unverified pairing.

## Contract and use

```sh
cobol-lower input.sp.json output.air.json --source-evidence output.source.json
analysis-dependencies output.air.json output.dependencies.json --source-evidence output.source.json
```

`QualifiedSourceProjection.project(SpInput, Admission)` is the transport-independent
producer. `SourceQualifiedDependencyResult.admit(evidence)` is standalone source
admission; `DependencyResult.withSourceEvidence(evidence)` additionally requires
publication correlation. The file adapter compares the digest of the exact AIR
bytes decoded by `DataflowAirReader`, before replacing a dependency output.

The [closed JSON schema](../contracts/qualified-source-dependencies-1.0.0.schema.json)
is the structural wire definition; constructors also enforce referential and
correlation invariants. All record fields are required; optional correlations use
zero/one arrays. Unknown fields/versions fail. A 2.5 result rejects the new section;
2.6 requires it. Legacy executable sites, edges, statuses and metrics retain their
2.5 meaning. The new section declares `NON_EXECUTABLE_SOURCE`; its statuses are
`QUALIFIED_POSSIBLE`, `NOT_QUALIFIED_IN_SOURCE_MODEL`, or `CONTROL_UNAVAILABLE`.
None is AIR reachability or an assertion that a command executed at runtime.

| Contract component | Authority preserved |
| --- | --- |
| `source`, `air` | Consumed SP schema/version/hash and optional AIR container/hash |
| `units.statements`, `occurrences` | Typed unit/statement/operand identities; original/expanded/include provenance |
| `values`, `valueRemainder` | Published logical literal value, or an explicit open value remainder |
| `qualifications`, `nodes`, `derivations` | Occurrence alternatives and the finite R7 certificate |
| `events`, `guards`, `selections` | Event origin; runtime conditions; selected registration; entry state; local/outer remainder |
| `targets`, `proofs`, `frontiers` | Registration provenance, source proof references, localized gaps |

An occurrence's `qualifications` are OR alternatives. For a node, incoming
derivations are OR alternatives. Within a derivation, `source` and `callerPremise`
are AND premises. A `selection` reference qualifies only that derivation, through
its event and guard references. A node identifier is local to a source unit's
certificate and has no AIR interpretation. `context`, `location`, and `authority`
are opaque R7 descriptors; the consumer never parses their spellings for control.
Event and proof references are checked, including ACTIVE activation-to-target and
DEACTIVATED entry correlation. Closed proof cycles and ungrounded node certificates
are rejected. Cyclic, grounded R7 derivations are retained without path expansion.

Targets are the admitted CALL, CICS PROGRAM and CICS FILE occurrences. Existing
nominal interpreters produce candidate reference names; they do not solve computed
values. Program-handler registration descriptors remain evidence and do not become
new invocations. A syntactically present occurrence without a qualified node is
retained, with no candidate. Scope uncertainty stays on the R7 selection/frontier;
there is no global handler enumeration or unknown-control fallback.

AIR assembly, HandlerStateAnalyzer, SP, AIR types, CFG projection and value solvers
are unchanged. R8 is NOT_STARTED; ALTER is NOT_IMPLEMENTED; executable handler
dispatch is NOT_READY. The source contract is a separate optional CLI output, so
failure writing it can leave the already-written valid AIR output; consumers
require digest matching and never accept a stale companion as current evidence.

## Validation and continuity

R9 entry: lower `0fa8e6942c7233adb5fa30cf8e19eabdc3fb9f17` (Draft PR34),
consumer `bae087c89a3a851d91a70a053a37fb3bb25af097` (Draft PR45).
The qualified semantic authority is R7 lower at that SHA paired with SP producer
`fbbf61d1840eb92dca804c53d8e9b6e60538318a`; AIR/IR pins remain unchanged.
No new branch/PR and no merge. Final SHA and exact-head CI are recorded on the
existing PRs and in the integration workspace evidence package.

Durable checks cover literal/explicit/conditional witnesses; ordinary and
conditional alternatives; CANCEL/replacement/deactivation/RESET; registration
without dispatch; computed values; ALTER and isolated modeling gaps; multi-unit
composition; codec roundtrip; invalid versions, identities, proofs, guards and
candidate references; file correlation and rejection without destination mutation.
R7 state suites and both repository FAST gates remain required. The real corpus
is an external source of evidence, never a fixture-specific product rule.
