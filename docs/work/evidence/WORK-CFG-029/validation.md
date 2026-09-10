# CP6-BASELINE-COMPAT-001 — local validation

POST_CP5_COMPATIBILITY_REMEDIATION in WORK-CFG-029. CP5 remains APPROVED / MERGED;
CP6 and baseline synchronization are not authorized. Stop at the new focal PR for review.

Base analysis-cfg 4229ec1cfd9c1d9f9e851f3cabe6993b4d4ed9b8, tree
3bc8b948de250c9eb4a756513294fb14a3ccc6db. Remote main rechecked unchanged.
Exact upstream air-java 17029898fd0ee8fabcaaae89f7260148633d4b12, tree
6f863c3321b6cbf82b021d85b47cab02748eed78, built from git archive into
/tmp/cfg-resource-limit/air-java and freshly installed into /tmp/cfg-resource-limit/m2.
[Dependency receipt](air-dependency-receipt.json) compares every source file with Git
and installed JARs with build outputs. Local runtime Temurin 25.0.4, release 21 bytecode;
CI uses Temurin 21. No pre-existing air-java/air-json SNAPSHOT was trusted.

[Baseline RED](baseline-red-network.log.gz): exactly the two reported tests fail with
VALIDATION_LIMIT expected, INCOMPLETE_VALIDATION observed; 2 failures, zero errors/skips,
exit 1. [Independent new oracle RED](oracle-red.log.gz) first proves actual upstream
RESOURCE_LIMIT, incomplete status and traversalCompleted=false, then fails because
CfgBuildResult loses the category. This predates production changes.

[Current contract](../../../architecture/resource-limit-preflight.md): total typed
counts govern INVALID_IR > RESOURCE_LIMIT > VALIDATION_LIMIT > UNSUPPORTED_CAPABILITY
> generic INCOMPLETE_VALIDATION > unsupported projection > CFG_BUILT. Every failure
has no graph. The real ASSOCIATION_DOMAIN_BOUND fixture preserves VALIDATION_LIMIT.
Generic incomplete uses only the package post-preflight seam, with the same production
classification and result construction. Same-Publication sufficient/restricted/recovery
and graph invariants are nominal tests. A resource composition failure precedes session,
key, solver, queries, consumers, ValueFact and prepared result. No artificial remainder.
The configurable reader runs real AirJson; byte/validator exhaustion exits 7 with
EXTERNAL_RESOURCE_LIMIT, no output and no receipt or delivery attempt. Generic incomplete
and IMPLEMENTATION_LIMIT keep separate historical diagnostics. No wire version changes.

[Full run](full-first.log.gz) passed fast, architecture, semantic, performance and
integration, including all W1–W5 runtime hooks. [Summary](local-validation.json).
Fast: 47 legacy harness + 101 CP5 harness + 5 focal guard tests + 14 result-reader tests.
Architecture: 108 kernel tests, exact sources/classfiles/DAG and Java 21 bytecode.
Source preservation guards compare focal changes with both historical and current hashes;
all earlier source inventories and wave evidence remain intact.

[Fresh E2E receipt](e2e-receipt.json), [raw outputs](e2e-raw.tar.gz),
[raw file hashes](e2e-raw-sha256.json): CP4E A/B each executes COBOL → SP → AIR →
BuildCfg → W1–W5. Candidate PROGA, real Assign producer and original OriginId;
modelValueRemainder=false, sourceUnknownRemainder=true, effectiveUnknownRemainder=true.
Both fresh SP/AIR/results deterministic. Result SHA-256 aacb6e6cedbd7d1dbbd44e7a476b398df3eeff51f1a63b90257d4dc412705dec
also exactly equals the approved CP5 snapshot. CP3 COMPLETE with no analyses/queries/facts.
Generic OLDER → NEWER returns NEWER only, supported by the last Assign. Every file result
is semantically and byte-wise identical to the independent in-memory route.

[Mutation campaign](mutations/receipt.json): 9 valid compiled mutants, 9 nominal REDs,
9 byte-exact restorations and 9 individual second GREENs. Includes generic fallthrough,
unsupported, invalid, legacy collapse, CFG product, generic-as-resource, early session,
artificial source-open result and CLI unsupported classification. The early-session
mutant is killed by the nominal architectural pre-analysis ordering guard after compile;
other mutations are killed by Java runtime oracles. No compilation failure is counted.
[Aborted setup](aborted-mutation-setup/receipt.json) omitted the adapter nominal selector;
it stopped at baseline before mutation. Intermediate failures remain separately preserved.

[Production preservation](source-preservation.json): exactly CfgBuildCoordinator,
CfgBuildResult, AnalysisDataflow composition/launcher and DataflowAirReader changed;
74 production files preserved. CfgPreflight/BuildOptions, W1–W4, W5 default plan,
wire/writer/receipt and all POMs are unchanged. Only air-java source authority was repinned.
The scalar fixture provenance still points at its original ce530a7 publication; its exact
bytes are independently checked against new 17029898. Other source-lock fields and CP5
producer SHAs remain identical. [Sibling state after](sibling-after.json) is unchanged;
the old chore/cp6-baseline-sync branch still points to the CP5 merge.

No W3-PERF-01/W3-METRICS-01 remediation, 117k qualification, broad baseline sync, CP6,
Invoke, CallResolver, ProgramDependency, lowering/solver or sibling changes. Physical
resources remain finite. Exact final HEAD/push/PR checkout receipts are attached to the
focal PR after the final commit; they are not inferred from run metadata or embedded in
a self-referential evidence commit. No merge or auto-merge authorized.
