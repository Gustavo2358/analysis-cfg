# W3-R1 final-stack SP→AIR regeneration sweep

Status: **PASS — evidence HOLD resolved for human review**. This is the
regeneration-only closure requested after the FILE record fix. It does not
rewrite the historical [validator-only discovery](W3-VALIDATION-SWEEP.md) or
claim that downstream E2E/solver runs were repeated.

## Authority and method

The immutable 40 logical and eight physical W3-R1 AIRs remain in local ignored
`evidence/w3-r1/{logical-final,physical-final}/`. The FILE composition case is
already replaced and independently qualified at AIR SHA-256
`d9a1cce6f9d1304168f2764636e55cdf8e2f23d10f6b31cd4ab0ad3848139319`.
This sweep regenerated the **other 39 logical and all eight physical** cases
from their original frozen source paths and recorded frontend CLI flags, using:

- producer #58 `93a42de83da823e69514cdefe2a9aa0ef0182ce2`;
- lower #34 `2beb5f8329885c7f5921d9a5d50f172901624b55`;
- AIR #21 `980d4989a18f876996390cc61af41419a595eb7b`.

The eight physical producer runs retained their explicit
`ibm-enterprise-6.4-fixed-display-1047@1` profile. The other 39 used the
original `UNSPECIFIED` profile. The final AIR JARs, current producer/lower
classes, exact commands, source hashes, SP, AIR, logs, validator results and
audit JSON are in separate ignored
`evidence/w3-r1/regeneration-final-stack/`. No frozen artifact was edited.
The runner and strict comparer are
[`w3_r1_regeneration_sweep.py`](../../../scripts/project/w3_r1_regeneration_sweep.py)
and [`w3_r1_regeneration_audit.py`](../../../scripts/project/w3_r1_regeneration_audit.py).
The compact [47-case manifest](W3-REGENERATION-SWEEP.tsv) records each source,
old AIR and new AIR hash, classification and final validator result.

The audit verifies the old AIR against its frozen `results.json` hash; compares
object inventory, Cell membership, storage attributes, resources, artifacts,
premises, full instructions, operands, expressions, control and effect scopes;
and requires the same operation IDs and operation origins. Object and StorageIds
are normalized by the distinct object names and positive Cell membership.
Diagnostics are checked separately: precision status and operation coverage
are fixed, previous written source anchors must survive, and new uncertainty
codes are restricted to the physical storage declaration gap.

## Result

| Classification | Logical | Physical | Meaning |
| --- | ---: | ---: | --- |
| Byte-identical AIR | 2 | 8 | Same exact publication hash. |
| Executable-equivalent after ID normalization | 36 | 0 | Same executable facts; IDs, object origins and coverage republished. |
| Expected complete-view relation | 1 | 0 | `logical--padding` gains positive group↔sole-child Cell identity. |
| Unexpected executable delta | 0 | 0 | None. |

The final validator accepted **47/47** regenerated AIRs as
`STRUCTURALLY_VALID`, with the same status and issue count as each old frozen
AIR. Operation IDs and origin references did not change. No object lost a
written source anchor. Across changed objects, 107 coverage statuses changed
`MODELED → ABSTRACTED` and exactly 107
`cobol-lower:STORAGE_DECLARATION_UNKNOWN` diagnostics were added for unproved
physical declaration. Object and operation precision statuses did not change.
These diagnostics do not introduce executable memory or control effects.

The sole executable topology delta is
`logical--padding`: old AIR
`9aef30053951f6eeef0f1a3cc0ba5d13d2b27c19dab818091521f24bdb109e78`,
final AIR
`9c0a1734afd04ba9c9b2b01e4e03465bb05443752dc25632eea3b48713628746`.
The source declares `SRC-REC` with sole `SRC-A PIC X(5)` and `DST-REC` with sole
`WS-PGM PIC X(8)`. The final SP publishes exact logical view pairs of lengths
5 and 8; each pair now shares one TEXT Cell, while independent `FLAG` retains
its own Cell. All other executable AIR facts compare equal after excluding
only the explicitly inspected storage bindings. This is the intended positive
identity rule, not a partial-overlap generalization. Its earlier downstream
result remains historical; no claim of byte-identical query output is made.

The final evidence composition is now:

1. 39 frozen logical cases with final-stack SP→AIR regeneration and comparison;
2. one regenerated, separately qualified FILE composition case;
3. eight frozen physical cases with final-stack SP→AIR regeneration and exact
   AIR hashes.

The old invalid FILE AIR
`d62a73d0feca84e8bc6b4a9ed87e7021f55d550f200562446755ac232681af92`
remains a negative witness with five I-13 issues. This sweep ran no CFG,
solver, dependency consumer, physical C/D, indep80/160 or performance canary.
`SOURCE_DEPENDENCY_OWNER_UNPROVED` remains `PREEXISTING_CONFIRMED`.

The regeneration audit passed, including a mutation check that changed an
instruction kind and was rejected. CFG local FAST passed with the campaign's
Temurin 21 runtime (`PASS CODE_CHANGE`). An initial invocation inherited the
host's JDK 25 and failed only the historical `jdeps` inventory comparison:
that JDK adds `java.lang.Record` edges for two unchanged values classes.
No source or inventory was modified to address this environment mismatch;
the same gate passed under JDK 21.

Local raw evidence hashes: `runtime.json`
`609553dcd1182b35f59e519b32fede75aeea4938a4cc2e6332af371a60d0f50a`,
`results.json`
`d05da358b8fd40c2fa027c5c499a01c157a731fec6fa9ac13cd1a95615a36b97`,
`audit.json`
`52995bcb0903d1394598299235916dd45b15b8f16e7ed2b8af985998fee12787`.

No production code or dependency pin changed. The campaign is technically
ready for human closeout review; all PRs remain Draft. No merge, W4 work,
default change or physical operational unbanning was performed.
