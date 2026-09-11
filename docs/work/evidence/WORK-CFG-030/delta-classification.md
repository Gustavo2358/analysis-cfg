# Frozen upstream delta classification

The initial candidate snapshot remains fixed. PR13 is the explicitly requested human remediation of the direct RESOURCE_LIMIT blocker; its merged tree equals reviewed head d083870b. Pure synchronization merges are added topologically to this snapshot. New unrelated remote commits are observations only.

| Repository | Old baseline → initial candidate | Ancestry / commits | Production, API, wire and contract | Risk / adaptation |
| --- | --- | --- | --- | --- |
| analysis-ir | 122ce54 → 51b4d9a | descendant; 3 commits | README and informative checkpoint-0b binding only. Normative specification, conformance and JSON binding bytes unchanged. | Low; no executable adaptation. |
| air-java | ce530a7 → 17029898 | descendant; 2 commits | Approved validator/codec capacity change across 15 production files; RESOURCE_LIMIT and total issue counts, uncapped defaults within Java representability. Public AIR model/fixture wire unchanged; additive diagnostics/APIs preserve existing call sites. | Original CFG diagnostic adaptation was required, stopped and separately resolved in human PR13. This sync changes no Java. |
| cobol-lower | 2329993 → fe7e6ef | descendant; 5 commits | Existing scalar DATA/MOVE/GOBACK lowering retained; 6 admission/transport files remove artificial input counts/bytes/visits caps, depth64 remains. CI/certification bootstrap corrected. No scalar translation/identity change. | Tests expected prior upstream capacity taxonomy/defaults; permitted harness updates only. Real canonical E2E bytes equal CP5. |
| proleap-poc | 8722945 → 8722945 | identical; 0 commits | W5 producer unchanged. | No frontend change. |

The lower's older frontend source lock2815 advances to the already exercised W5 producer8722945. SemanticProductJsonWriter, AIR-MOVE fixture and scalar-text-MOVE contract are byte-identical across that pin delta. NEXT SENTENCE upstream work does not change canonical CP4E.

Air-java PR8 repins only the normative baseline122→51 and docs/harness provenance. Its merge3bafe397 has the exact same Java21 model/codec JAR hashes as17029898. All local full/transport checks, four exact-head CI runs and both merged-main CI runs passed.

CP4E still means one Object, one Cell, literal PROGA Assign, Return and Publication PARTIAL. New candidate CP4E A/B, CP3 and generic overwrite SP/AIR/result hashes are identical to historical CP5, including producer/origin and remainders. No production semantic adaptation in the baseline branches.

W3-PERF-01 and W3-METRICS-01 remain STILL_OPEN. AIR default codec/validator capacity policy was resolved upstream for the tested route; explicit budgets and physical resource limits remain distinct. The lower10k probe produced112117041 AIR bytes, validated and round-tripped exactly. Lower transport memory amplification remains STILL_OPEN; no117k production qualification or SLA is claimed.

Lower pure baseline PR10 merged `18016f16b4f63149eb1bb4ca13db7e12593d8909` from final documentary head `36b0020cd26e14f42fa8334eb37f936dcd5986bd`. Both certified-parent and final closing-head exact CI passed; merge tree equals the final validated head. The final source lock now selects this merged main, with zero production Java/POM delta against frozen fe7e6ef.
