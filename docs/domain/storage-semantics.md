# Storage Semantics campaign — ST-W0..ST-W5

W0 freezes the implementation contract; it does not claim regional analysis is
already implemented. WORK-STORAGE-CFG-001 promotes the storage/RD/value residuals
of BACKLOG-CFG-020 and frontend handoffs BACKLOG-DF-001/004/003. CP5/CP6 delivered
solver, queries, scalar values and control remain the baseline, not work to repeat.

Authority: pinned AIR 2.0.0, memory §§1–6/8–10 and consumers §§3–5/6.1/8,
analysis-ir `51b4d9a8ae0364232bd97103cd73a77e1a34996c`. New runtime codec
`text.ebcdic.ibm1047@1` will have a normative extension contract in W1.
Generic AIR forms and JSON binding 1.0.0 DRAFT otherwise suffice.

## Canonical products

Storage resolution is prepared once per immutable AnalysisSession. A location
contains a declared base, its duration/owner/context and either a whole cell or
a half-open BigInteger octet interval. Resolve ObjectPlace, View, exact Alias
and constant RegionSlice; alternatives retain every candidate and an independent
remainder. Unknown binding/extent has an explicit reason and safe scope.

Equality of physical location is distinct from equality of interpretation.
Different StorageIds identify independent state bases in the supported abstraction.
Within one base, interval intersection determines sharing; explicit aliases and choices
retain their modeled relations. DisjointStorage is redundant, not required. Cyclic aliases fail
validation; unresolved/open alternatives cannot produce simultaneous strong
updates. Activation storage is context-qualified; persistent/external storage
is not reseeded at a local return or loop backedge.

Canonical statement effects identify operation, operand occurrence, program
point/outcome, reads of value/address, writes MUST/MAY, location resolution and
the proof supporting update strength. Assign/CopyBytes evaluate source before
destination writes. HavocMust creates an unknown definition over exactly its
proven footprint. HavocMay preserves prior possibilities. Opaque and Invoke
consume declared semantic envelopes. An omitted source effect may be absent from
the executable projection with coverage; a published unknown effect is still executed. Invoke target is
observed BEFORE interaction effects; outcome writes belong to that outcome.
RD and values share these effects; dependency consumers do not reinterpret them.

RD uses AnalysisDefinition/DataflowSolver, not a separate solver. Definition
identity is a finite entry/operation/destination/outcome event. Partition only
constant interval boundaries required by accesses; do not materialize storage
per byte. A state records contributing events by segment. Exact MUST replaces
only covered segments; MAY/ambiguous writes retain surviving contributors.
Entry unknown is a definition/remainder, distinct from unreachable bottom.
Definition output has a typed total order, including premises, uncertainties and
all other event metadata. Direct and environment-remainder events may share
operation/slot/storage while retaining distinct proofs; neither is discarded.

The immutable query result includes definitions, contributedRanges,
unknownRemainder, reachability, point (before/after/outcome), entry/context,
premise IDs and source origins. Batch/replay uses existing session/AnalysisKey
binding. Session snapshot plus profile/version/options prevents reuse with
different layout, CFG or proofs. Empty batches and unsupported points remain
explicit. No analysis runs per variable or per candidate.

Values store known/unknown byte fragments and finite producer supports. Literal
producer, copy event and captured read are different evidence. W3 initially
projects a fully covered view; W5 composes compatible fragments and preserves
untouched bytes under partial writes. It never queries a copy source at a later
use of the destination. Unknown segments invalidate incompatible whole values.
MAY writes retain compatible old candidates plus remainder.

Join unites candidates and remainders and preserves supports. Correlation must
be either preserved or explicitly classified as abstract overapproximation;
cartesian fragments cannot be called concrete execution witnesses. W5 must
document/test finitude and monotonicity for fragments, supports and copies,
not just the old universe of whole literals. No arbitrary candidate cap and no
path-history tree. Operational exhaustion is an explicit failure, never a
truncated semantic success.

## Versioning and architecture

Storage/RD are new products, versioned `regional-storage@1` and
`regional-reaching-definitions@1`. Existing Cell result fields keep their
meaning; a region result is not serialized as a Cell. A result-wire extension,
if required for these products, receives its own explicit version and closure
tests. Existing CALL queries receive a compatible projection with raw text,
producer supports and independent model/source/interpretation/control remainders.

Storage and effects belong in cohesive packages over AIR/session, outside the
generic solver. RD and values are separate analysis definitions. New packages
must be admitted by the architecture DAG with inward dependencies. CFG building
does not evaluate storage or values. No COBOL names, source, PICTURE, parser
contexts or observedKind drive memory semantics.

AIR byte values, bounds, codec profiles and manifest must survive file transport.
ASCII and IBM1047 are explicit distinct codecs. Unknown/uninterpreted codecs
preserve bytes while refusing exact text. Missing manifest, dangling IDs,
contradictory bounds and VALIDATION_LIMIT cannot become valid to unblock a caller.

## Milestones and acceptance

| Wave | Required evidence |
| --- | --- |
| W0 | exact merged baseline/pins, four FASTs, scalar/control E2E, independent byte oracle and seven falsifying mutants |
| W1 | manual AIR→model/validator→JSON→CFG, regional capability, codecs, bounds/BigInteger/unknown extent negatives |
| W2/M0 | D1 R[0,8), D2 R[0,4): query returns D2[0,4) and D1[4,8); MUST/MAY, alias, remainder, replay/context |
| W3/M1 | actual COBOL group MOVE→child→CALL, physical evidence and scalar regression |
| W4/M2 | actual REDEFINES bidirectional sharing; unrelated proven storage stays precise |
| W5/M3 | WXYZEFGH composition, copy capture, unknown partial writes, joins/loops/PERFORM and supports; broad local qualification |

BACKLOG-CFG-013/014/015 concern existing local-control representation/matching;
their delivered behavior is reused, not reopened as a new interprocedural project.
W6–W8 features remain outside this campaign. Work-item DONE still requires merge
under the lean harness; wave receipts may say qualified/ready while work items
remain IN_PROGRESS awaiting the final human review. No merge is authorized.
