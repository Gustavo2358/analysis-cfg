# POSITIVE_MEMORY_TOPOLOGY — W2 regression

The frozen W1 workspace oracles are `.positive-memory-topology/evidence/w1/B-final2`
(logical), `.positive-memory-topology/evidence/w1/CD-final2` (physical C/D) and
`runtime-final2.json` in the same evidence directory.
W2 uses the same CFG consumer binary and its existing LOGICAL_ONLY default.
Raw W2 source runs and commands are under `.positive-memory-topology/evidence/w2`;
the final producer
and lower compilation is identified in [W2-REPORT](W2-REPORT.md). A changed
diagnostic, origin or republished ID alone is not a semantic failure.

## Logical source comparison

The 31-case `e2e-integration-closure` cohort covers W1 group, overlay, padding,
snapshot and unsupported witnesses; IF, EVALUATE, PERFORM, GO TO/ALTER, CALL,
ACCEPT, COMPUTE and DISPLAY; five CICS FILE sources; and selected DB2_TABLE,
COPYBOOK, DCLGEN and SQL_INCLUDE dependency sources. Each stage completed and
the dependency consumer reported `logicalOnlyMode=1` and
`experimentalPhysicalMode=0`.

| Witness | W1 → W2 semantic delta |
|---|---|
| IF class; EVALUATE condition; PERFORM UNTIL | Both known candidates survive; model/control remainder caused only by predicate interpretation closes. Supported IF equality, EVALUATE without OTHER and PERFORM TIMES keep candidates. |
| GO TO with ALTER | Textual `OLDPGM` remains; ALTER coverage no longer opens control or value. Basic GO TO is unchanged. |
| COMPUTE; DISPLAY | Prior `OLDPGM` remains with its producer support; unimplemented transform/output detail no longer supplies global remainder. |
| ACCEPT | Real external input to the known receiver kills prior MUST candidate `OLDPGM`; the value remains unknown in the model. This is the intended B control. |
| CALL before computed CALL | `EXTERNAL` and `OLDPGM` remain on their respective sites, with normal continuation and no foreign memory compensation. |
| Group, overlay, padding, snapshot | Candidates and supports match W1. No physical work runs in default mode. |
| CICS FILE LENGTH gap | Known `OLDNAME`/`KEEPNAME` candidates remain in default mode with legitimate model remainder for the physical value not propagated there; returned FILE/status effects are retained. |
| Source dependency cohort | Existing dependency sites and source claims are preserved; no new W2 source dependency capability is claimed. |

The candidate/support-kind comparison across common W1/W2 cases differs only
for ACCEPT, where the B input correctly removes `OLDPGM`. `logical--unsupported`
loses a C-only open-control remainder without acquiring a candidate. The
source dependency composition witness retains its preexisting open frontier;
it is not relabeled as W2 success.

## Same-AIR physical contrast

`e2e-physical-closure` ran six source cases. In every pair the AIR SHA-256 is identical.
Default C has `logicalOnlyMode=1`, `physicalGroupsApplied=0` and
`physicalWritesApplied=0`. Explicit D uses `--experimental-physical` and
`experimentalPhysicalMode=1`:

| Case | D groups / writes | D observation |
|---|---:|---|
| call-before | 1 / 1 | prior supported target retained |
| cics-length-open | 6 / 6 | `KEEPNAME` closes on known length; true external READ remains unknown |
| if-class | 2 / 2 | both branch candidates retained |
| layout-family; layout-independent | 1 / 1 each | known storage candidates retained |
| partial-group-copy | 3 / 3 | `PROGA001` snapshot candidate retained |

## Capacity and isolation

The physical indep80 and indep160 canaries each completed once with the frozen
configuration and same-AIR C/D pairs. They prepared 161/321 targets and applied
161/321 physical groups and writes, respectively; dependency physical phases
took 1.016/1.316 seconds. Candidate observations are `PROGA`/`PROGC`.
`prepare_baseComparisons=0`, `prepare_objectPairsMaterialized=0` and
`solve_relationUnionPairs=0` in both. This is a structural canary, not an SLA;
the old >600-second baseline was not rerun. The `e2e-control-heavy-closure`
EVALUATE 40 case
has 40 reachable sites, each with the three supported alternatives, 120 edges,
no open-control site and no physical work in default mode.

The lower's 0/1/50 selector-diagnostic metamorphic holds ordinal targets,
bounded reads and reference work fixed. The retained W1 CFG 0/1/50 scalar and
regional metamorphics cover Events, captures, supports and semantic work.
Explicit AIR Havoc/AllMemory/open-control and real external input remain B
controls; structural validator and codec suites remain D controls. No
feature-name switch was added to the CFG consumer and its operational mode was
not changed.
