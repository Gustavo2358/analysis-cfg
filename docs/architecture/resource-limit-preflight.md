# Operational preflight failures after CP5

WORK-CFG-029 resolves CP6-BASELINE-COMPAT-001 against air-java
17029898fd0ee8fabcaaae89f7260148633d4b12. CP5 is APPROVED / MERGED; this is
POST_CP5_COMPATIBILITY_REMEDIATION, with no CP6 or baseline synchronization.

The real upstream ValidationContext marks operational work exhaustion with
RESOURCE_LIMIT, total diagnostic counts and traversalCompleted=false. ValidationResult
reports INCOMPLETE_VALIDATION unless an INVALID_IR diagnostic was already detected.
Diagnostic retention is independent from completion: hasIssues reads total counts,
not only retained messages. CfgPreflight returns that result unchanged.

CfgBuildCoordinator classifies in this explicit order:

| Condition | CFG outcome |
| --- | --- |
| INVALID_IR total count > 0 | INVALID_IR |
| RESOURCE_LIMIT total count > 0 | RESOURCE_LIMIT |
| VALIDATION_LIMIT total count > 0 | VALIDATION_LIMIT |
| upstream unsupported capability or downstream capability negotiation refusal | UNSUPPORTED_CAPABILITY |
| another incomplete traversal without the above typed issues | INCOMPLETE_VALIDATION |
| complete preflight but unsupported projection/policy | UNSUPPORTED_INPUT |
| completed preflight and supported projection | CFG_BUILT |

RESOURCE_LIMIT precedes the specific legacy limit when both coexist; invalidity
already detected remains INVALID_IR, matching ValidationResult.status precedence.
No CFG exists for any failure; only CFG_BUILT carries a graph. This also applies
when a public CfgBuildResult constructor is called directly.

VALIDATION_LIMIT is still emitted upstream for PRECONDITION_NOT_DISCHARGED and
ASSOCIATION_DOMAIN_BOUND. It means a specific validation obligation could not be
discharged by this validator slice. It is not the new operational resource category.
The real open storage association fixture protects this distinction. A package-only
post-preflight seam exercises generic incomplete and mixed/omitted diagnostics through
the same coordinator classification and product construction. The public BuildCfg
entry point always invokes the real AirValidator.

CORE-SIZE-001 remains absolute: **Program size/resource availability is never a
semantic admission criterion.** Sufficient resources can validate/build the same
Publication that previously exhausted an explicit operational budget. Recovery has
no static or cached failure state. Resources cannot imply INVALID_IR, unsupported
coverage, precision loss, sourceUnknownRemainder or effectiveUnknownRemainder.
Physical JVM Error propagates; no OOM is caught or converted to RESOURCE_LIMIT.

The W5 composition maps RESOURCE_LIMIT to PreparationException.EXTERNAL_RESOURCE_LIMIT
before AnalysisSession.open, AnalysisKey, solver, observations, consumers or result
capture. No ValueFact or PreparedAnalysisResult exists. The package-only options
overload tests the real builder with an opt-in budget. It adds no semantic key option.
INCOMPLETE_VALIDATION remains a distinct failure. Historical EXTERNAL_SIZE_CAP_DEBT
mapping for VALIDATION_LIMIT is preserved; this work does not redesign that category.

AirJsonException.RESOURCE_LIMIT maps to CLI exit 7 and stderr EXTERNAL_RESOURCE_LIMIT.
The configurable reader uses the real AirJson codec for byte/depth/validation budgets;
public CLI arguments/defaults stay unchanged. Failures stop before LocalResultWriter,
so stdout has no receipt, including FAILED, and no output is created. IMPLEMENTATION_LIMIT
retains its historical exit/category. Generic codec incomplete remains
UPSTREAM_VALIDATION_INCOMPLETE. The codec itself prioritizes RESOURCE_LIMIT over
already-detected invalidity, unlike ValidationResult.status; its typed Code is preserved.

No resource status enters prepared-analysis-result / analysis-dataflow-result 1.1.0
or delivery-receipt 1.0.0. These versions and successful result bytes remain unchanged.
The new default upstream budget uses Java representation ceilings; the former 16 MiB
ceiling is no longer a default. Explicit budget tests remain bounded and deterministic.
This does not qualify 117k programs, arbitrary-size files, physical heap, or the whole
pipeline: buffers, lower budgets and W3 performance/metrics debts remain separate.
