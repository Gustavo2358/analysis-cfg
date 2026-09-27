# analysis-cfg-json 4.0.0 — explicit exceptional outcomes

The generic AIR alternatives Exceptional, AnyException and HaltAlternative are
projected for Invoke and Opaque. No source-language inference is involved.

| Extension | Meaning |
| --- | --- |
| EXCEPTION transition | An explicitly published local Handler label |
| CONTROL_EXIT transition | Propagation outside the local unit, or alternative halt |
| OUTCOME_EXIT node | Original operation identity plus HALT, EXCEPTION (with tag), or ANY_EXCEPTION outcome |

An OUTCOME_EXIT belongs to one terminator occurrence and one distinct outside
outcome. It is not NORMAL_EXIT and has no invented successor. Edges retain the
activation Entry. The full AIR retains the exception tags and all proof origins;
multiple local exception tags to the same handler share one edge in that role.
The independent structural index checks the original terminator identity, outcome
membership, endpoint roles and complete cardinality. Missing outcomes are rejected.

The writer selects v4 only when these nodes or edge roles occur; old v1/v2/v3
products retain their contracts. v4 includes the v3 openControlRemainder field.
The independent Python oracle rejects v4 roles under older versions and rejects
unnecessary upgrades. External importers must add v4 support before importing
these products. Application UI compatibility is outside this campaign.

Diverge remains outside the known subset. Existing partial-analysis treatment of
unsupported forms is unchanged. Exceptional transfer applies conservative memory
effects; no synthetic MUST overwrite or assumption that an error occurred is added.

See [campaign and qualification](../work/cics-control-completion.md).
