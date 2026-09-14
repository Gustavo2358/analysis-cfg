# RF-W3: invocation target independence

CallDependencyPlan classifies the target expression independently of invocation
arguments/results. Literal candidates require no values run. Computed reads remain
queries at BEFORE Invoke; no result or callee effect is applied retroactively.

For publications with result destinations, the plan selects the existing Regional
Values provider (also supports Cell storage). Its canonical StatementEffects already
applies results as MUST unknown writes on normal return only. No solver, dataflow,
CFG projection or normative AIR contract changes were needed. The explicitly selected
legacy scalar effects profile retains its documented result/per-outcome limitations.

Ten literal/computed × no operand/reference argument/unknown expression argument/
exact result/unknown-binding result cases have permanent memory and JSON CLI oracles.
A normal-outcome query proves exact result kills the old value; unknown binding only
widens. With an open control remainder, a downstream label can join other paths that
have not normally returned. Such candidates must not be erased or declared constant.

AIR JSON now transports existing Argument and Place result forms using air-java
2b0c7c3 (binding 1.0.0/AIR 2.0.0 unchanged). Complex places unsupported by that codec
remain explicit limitations. No arguments/results are removed to serialize a publication.
The current SP CALL contract contains surface presence/count, not argument expressions;
lower retains that uncertainty in the signature. This wave tests materialized AIR facts
separately from source verticals and does not claim source argument projection.
