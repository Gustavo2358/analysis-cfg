# Explicit dependency input 1.0.0

Status: IN_PROGRESS; approved scope, Draft review required.

The normal dependency entrypoint consumes an explicit manifest. It binds the existing
AIR bytes, qualified-source-dependencies 1.0.0 bytes and typed StatementLinks from the
same lower execution. It changes no AIR, control, qualification or R9 semantics.

## File composition

1. Run `io.github.gustavo2358.lower.adapters.cli.CobolDependencyInput` with the
   Semantic Product path and destination manifest path.
2. Run `io.github.gustavo2358.analysis.launcher.AnalysisDependencies` with that
   manifest path and destination dependencies path. No evidence flag is required.

Both classes accept their two paths as positional arguments on the ordinary Java
runtime classpath. The historical CobolLower AIR command and explicit
`--source-evidence` input remain compatibility entrypoints.

The manifest schema is [dependency-input-1.0.0.schema.json](../contracts/dependency-input-1.0.0.schema.json).
`air` and `qualifiedSource` each contain an explicit path and SHA-256. Relative paths
are relative to the manifest directory; absolute paths are also explicit. No adjacent
file discovery occurs. `sourceSha256` must equal the R9 certificate's Semantic Product
digest. Its AIR publication and digest must match the selected AIR snapshot.

Each correlation binds a full source StatementId (compilation unit, structural path,
canonical program and statement handle) to a full AIR OperationId, LabelId and OriginId.
The producer emits only links already present in LoweringResult. The consumer rejects
unknown source occurrences, missing operations, wrong labels, wrong publications,
conflicting ownership, duplicate links, incompatible targets and unrelated origins.
Derived operation origins may refer transitively to the claimed statement origin.
A link to an existing Opaque operation remains non-executable evidence; it cannot
create an Invoke, label, edge, program point or reachable value.

Missing links leave source-only computed targets open. A source qualification does
not make an unreachable AIR site reachable. The same Entry/BEFORE/subject query and
its existing result are reused when both authorities identify an executable site.

The producer installs content-addressed AIR and certificate snapshots before atomically
publishing the manifest. A failed run does not replace an existing manifest. Orphan
snapshots may remain after failure; they do not constitute a published input. The
consumer verifies all evidence before analysis and replaces its destination atomically
only after success. Unknown fields, duplicate JSON keys, trailing values, wrong versions,
modified snapshots and invalid correlations are rejected.

The memory port is DependencyInput(Publication, optional certificate, typed correlations).
Digest checks belong to file adapters; memory construction checks the same identity,
ownership and origin relationships. No core module imports file or JSON APIs.

Producer: cobol-lower `8b646b2fae4da10937d4feeb8cb8db13dab984bc` ([Draft #35](https://github.com/Gustavo2358/cobol-lower/pull/35)).
