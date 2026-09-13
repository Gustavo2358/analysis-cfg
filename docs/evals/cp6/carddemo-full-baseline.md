# Full CardDemo baseline

**IMPLEMENTED / AWAITING HUMAN REVIEW.** Historical, observational evaluation; no product capability or performance change.

## Corpus

Universe: [`aws-samples/aws-mainframe-modernization-carddemo`](https://github.com/aws-samples/aws-mainframe-modernization-carddemo/tree/59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e) at `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`.
**73 discovered; 73 attempted; every source path represented exactly once.** Recursive `.cbl`/`.cob`/`.cl2`, case insensitive, including ZIP members; no whitelist, source edits, dependency stubs or excluded failures.

Discovery includes **44 checkout files and 29 archived source variants**. `.cl2` is included because the UniKix `migrated_app/cbl` members contain COBOL IDENTIFICATION DIVISION/PROGRAM-ID alongside `.cbl` programs. Archive paths use `archive.zip!/member`. AppleDouble resource-fork metadata is excluded only with its binary magic verified; every exclusion is recorded. `.cpy`, `.dcl` and extensionless library files are dependencies; BMS, assembler, JCL, runtime binaries, compiler listings, data and marker files are not standalone COBOL programs. No source duplicate was excluded.

All dependency roots in the checkout and ZIP are available, including DCLGENs and extensionless archived copybooks. Root order prefers the longest shared directory prefix, then lexical order: native programs prefer native roots; archive variants prefer their own roots. **46 dependency-name collisions** are recorded with ordered paths and equal/different-content flags. Sources are never rewritten to select a dependency.

The selected ten programs in `proleap-poc/corpus/carddemo` remain the **small pinned regression/sentinel corpus**. This full temporary checkout is the **exploratory product baseline**; it is not vendored.

Fetch completed; clean `main == origin/main` and the real merge parents were verified before the evaluation branch. Isolated builds used these exact commits throughout:

| Product | Commit |
| --- | --- |
| air-java | `96cd5e545723c6fd76d1520f431ebbc196af84f6` |
| proleap-poc | `59ac43bc3ab4bd186092a13732381512cbdaddba` |
| cobol-lower | `7da4980067c2860b3827ecc3eea641a6afa2a526` |
| analysis-cfg | `18a78a6599bfad2d983a4a32d7515d379a9bac60` |

## Pipeline coverage

| Stage/output | Programs | Previous stage | Corpus |
| --- | ---: | ---: | ---: |
| discovered | 73 | 73/73 (100.0%) | 73/73 (100.0%) |
| normalizationPreprocessingUsable | 67 | 67/73 (91.8%) | 67/73 (91.8%) |
| spProduced | 67 | 67/67 (100.0%) | 67/73 (91.8%) |
| airProduced | 27 | 27/67 (40.3%) | 27/73 (37.0%) |
| cfgProduced | 27 | 27/27 (100.0%) | 27/73 (37.0%) |
| dependencyProduced | 27 | 27/27 (100.0%) | 27/73 (37.0%) |

`normalizationPreprocessingUsable` means a normalized/preprocessed artifact was delivered to the frontend, with input gaps retained. It does not assert that all COPYs resolved. Frontend timing combines normalization, preprocessing, parsing, SP and the current CLI exports.

All produced SP/AIR/CFG/dependency stages in this run are **PARTIAL**, not complete semantic coverage. Failed/blocked processes retain their measured time; subsequent stages are NOT_REACHED. No timeout or resource-limit event occurred.

| Actual stopping condition | Programs | Observed SP CALLs behind it |
| --- | ---: | ---: |
| ENTRY_START_UNAVAILABLE | 40 | 37 |
| FIXED_FORMAT_TAB | 1 | unknown: no SP |
| NORMALIZATION_REJECTED | 1 | unknown: no SP |
| PREPROCESSOR_EXEC_POLICY_MISSING | 4 | unknown: no SP |

The 40 ENTRY_START refusals split into **37 INPUT_MISSING entries / 28 observed CALLs**, and **3 unavailable starts without INPUT_MISSING / 9 observed CALLs**. Sources without INPUT_MISSING: app/app-authorization-ims-db2-mq/cbl/DBUNLDGS.CBL, app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL, app/app-authorization-ims-db2-mq/cbl/PAUDBUNL.CBL. SP publishes `EXECUTABLE_START_NOT_AVAILABLE`; the lower requires an explicit start. No entry is invented.

Missing dependencies, as reported by preprocessing: `CMQGMOV` (2 occurrences), `CMQMDV` (2 occurrences), `CMQODV` (2 occurrences), `CMQPMOV` (2 occurrences), `CMQTML` (2 occurrences), `CMQV` (2 occurrences), `DFHAID` (35 occurrences), `DFHBMSCA` (35 occurrences). These gaps occur in 37 programs. Explicit incomplete-entry input is counted separately above. These are input/dependency gaps, not statement capability gaps.

## CALL coverage

**73 typed CALLs among 67 SP-producing programs; 36 dependency sites among 27 completed pipelines.** The remaining observed CALLs have no dependency result and are not classified as unresolved sites. CALLs in the 6 no-SP programs are unknown.

| Category | Analyzed dependency sites |
| --- | ---: |
| CLOSED_RESOLVED | 0/36 (0.0%) |
| PARTIAL_RESOLVED | 36/36 (100.0%) |
| OPEN_UNRESOLVED | 0/36 (0.0%) |
| UNREACHABLE_IN_MODEL | 0/36 (0.0%) |
| OTHER | 0/36 (0.0%) |

Closed resolution among analyzed reachable sites: **0/36 (0.0%)**. Known candidates with closed model-value reasoning: **36/36 (100.0%)**. Analyzed target kinds: {'LITERAL': 36}. Remainders remain independent; an open/unknown site is never promoted to CLOSED.

Known dependency projection: **15 distinct caller-name → reference-name pairs**, with site associations and supports retained; **5 distinct reference names**: `CBSTM03B`, `CEE3ABD`, `CEEDAYS`, `COBDATFT`, `MVSWAIT`. These are supported known name candidates, not certified runtime linkage or a complete call graph.

| Caller | Known reference names |
| --- | --- |
| CBACT01C | CEE3ABD, COBDATFT |
| CBACT02C | CEE3ABD |
| CBACT03C | CEE3ABD |
| CBACT04C | CEE3ABD |
| CBCUS01C | CEE3ABD |
| CBEXPORT | CEE3ABD |
| CBIMPORT | CEE3ABD |
| CBSTM03A | CBSTM03B, CEE3ABD |
| CBTRN01C | CEE3ABD |
| CBTRN02C | CEE3ABD |
| CBTRN03C | CEE3ABD |
| COBSWAIT | MVSWAIT |
| CSUTLDTC | CEEDAYS |

## Unsupported/gap distribution

Authority: SP typed/observed facts in **67 programs**. Statements: **14915 total; 30 modeled; 8803 partial; 6082 unsupported; 0 input-missing**. Zero input-missing statements does not mean zero input gaps: the entry inventory and preprocessing separately expose missing dependencies.

| Observed family | Statements | Modeled | Partial | Unsupported |
| --- | ---: | ---: | ---: | ---: |
| CALL | 73 | 0 | 73 | 0 |
| EMBEDDED_LANGUAGE | 336 | 0 | 336 | 0 |
| EVALUATE | 264 | 0 | 0 | 264 |
| GOBACK | 30 | 30 | 0 | 0 |
| GO_TO | 306 | 0 | 0 | 306 |
| IF | 1811 | 0 | 1811 | 0 |
| MODELED_STATEMENT | 3450 | 0 | 0 | 3450 |
| MOVE | 6261 | 0 | 6028 | 233 |
| NEXT_SENTENCE | 2 | 0 | 2 | 0 |
| PERFORM | 1829 | 0 | 0 | 1829 |
| PRESERVED_STATEMENT | 553 | 0 | 553 | 0 |

The generic MODELED_STATEMENT/PRESERVED_STATEMENT buckets do not identify READ/WRITE/SEARCH/DISPLAY and other subfamilies. No source regex expands those buckets. Embedded-language shapes: 327 CICS, 9 SQL; these counts do not cover programs blocked before SP.

Most frequent SP gap-code/family combinations (multiple gaps may describe one statement):

| Gap code | Family | Occurrences | Programs |
| --- | --- | ---: | ---: |
| MOVE_IDENTITY_NOT_PROVEN | MOVE | 6028 | 66 |
| SCALAR_WHOLE_ITEM_NOT_PROVEN | MOVE | 6027 | 66 |
| NORMAL_CONTINUATION_NOT_AVAILABLE | MOVE | 4875 | 53 |
| OBSERVED_STATEMENT_UNSUPPORTED | MODELED_STATEMENT | 3450 | 66 |
| CONTAINMENT_NOT_PROJECTED | MOVE | 1918 | 47 |
| OBSERVED_STATEMENT_UNSUPPORTED | PERFORM | 1829 | 65 |
| CONDITION_SEMANTICS_NOT_AVAILABLE | IF | 1811 | 63 |
| IF_OUTSIDE_SIMPLE_PROFILE | IF | 1811 | 63 |
| LITERAL_KIND_NOT_PUBLISHED | MOVE | 1504 | 56 |
| CONDITION_REFERENCE_KIND_NOT_PROJECTED | IF | 1407 | 59 |

## CALL-site impact

Frequency is not causality. Potential impact below means an AIR unit-label control envelope contains an analyzed site with open-control remainder. It is a conservative **region bound**, without a claim that this particular gap caused the remainder or that its implementation will close the site. Bounds overlap; columns must not be summed across rows. Unknown attribution stays explicit in JSON.

| Capability/gap dimension | Occurrences | Programs | Direct CALLs | Potential CALLs | Partial | Open | Pipeline blockers |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ENTRY | 123 | 46 | 0 | 0 | 0 | 0 | 40 |
| LOWERING_GAP | 40 | 40 | 0 | 0 | 0 | 0 | 40 |
| COPYBOOK_MISSING | 82 | 37 | 0 | 0 | 0 | 0 | 37 |
| PREPROCESSING_GAP | 4 | 4 | 0 | 0 | 0 | 0 | 4 |
| NORMALIZATION_GAP | 2 | 2 | 0 | 0 | 0 | 0 | 2 |
| MODELED_STATEMENT | 3450 | 66 | 0 | 36 | 36 | 0 | 0 |
| PERFORM | 1829 | 65 | 0 | 35 | 35 | 0 | 0 |
| IF | 1811 | 63 | 0 | 33 | 33 | 0 | 0 |
| PRESERVED_STATEMENT | 553 | 43 | 0 | 33 | 33 | 0 | 0 |
| MOVE | 6261 | 66 | 0 | 24 | 24 | 0 | 0 |
| EVALUATE | 264 | 46 | 0 | 19 | 19 | 0 | 0 |
| GO_TO | 306 | 16 | 0 | 14 | 14 | 0 | 0 |
| NEXT_SENTENCE | 2 | 2 | 0 | 2 | 2 | 0 | 0 |
| CICS | 327 | 38 | 0 | 0 | 0 | 0 | 0 |
| ENTRY_INVENTORY | 104 | 67 | 0 | 0 | 0 | 0 | 0 |
| CALL | 73 | 33 | 0 | 0 | 0 | 0 | 0 |
| SQL | 9 | 3 | 0 | 0 | 0 | 0 | 0 |

Occurrences above deduplicate gap codes on the same source statement. Pipeline blockers require a process refusal or an explicit SP entry/input gap joined to that refusal. Mere presence in a blocked program is recorded separately as `presentInBlockedPrograms`. LOW means a site stays closed; MEDIUM means known candidates survive with partiality; HIGH means open/unresolved; PIPELINE_BLOCKER means the pipeline stops. UNKNOWN is retained when there is no attributed site.

Direct site-local AIR evidence is separate from COBOL-family frequency:

| AIR uncertainty | Occurrences | Programs | Direct sites |
| --- | ---: | ---: | ---: |
| CONTRACT_UNKNOWN | 36 | 22 | 36 |
| CONTROL_UNKNOWN | 36 | 22 | 36 |
| RESOURCE_TARGET_UNKNOWN | 36 | 22 | 36 |
| cobol-lower:RUNTIME_NAME_POLICY_UNKNOWN | 36 | 22 | 36 |

## Observed timing

**OBSERVED BASELINE TIMING** — one sequential measured run per source, `time.monotonic_ns()`. Timings include normal process startup/JVM behavior. A new JVM is used for each stage; no warmup, reuse or JIT correction. This is not a scientific benchmark, SLA or production throughput claim.

Corpus wall time: **192.221s**. Setup/build: 34.270s; discovery: 0.136s, both outside corpus time. Measured stages total 186.873s; observed runner overhead 5.348s. JSON parsing/validation, filesystem and per-program exports explain the expected difference. Clone/download time is outside these timers.

| Population | n | Total program time | Mean | Median | Min | Max | p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| allAttempted | 73 | 192.098s | 2.631s | 2.466s | 0.286s | 5.619s | 5.353s |
| completedDependencyPipeline | 27 | 80.263s | 2.973s | 2.582s | 1.632s | 4.151s | 4.106s |

allAttempted: fastest **checkout: COTRTLIC.cbl — 0.286s**; slowest **ZIP: CORPT00C.cl2 — 5.619s**.

completedDependencyPipeline: fastest **ZIP: SDSF.cbl — 1.632s**; slowest **checkout: CBSTM03A.CBL — 4.151s**.

The fastest all-attempted program fails normalization. The slowest all-attempted program is blocked in lower. Neither is excluded. p95 uses nearest rank and is omitted for n < 20.

| Stage | Executions | Total | Mean | Median | Min | Max | p95 | Slowest program |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| frontend | 73 | 112.761s | 1.545s | 1.316s | 0.264s | 4.922s | 4.071s | ZIP: CORPT00C.cl2 |
| lower | 67 | 51.022s | 0.762s | 0.715s | 0.465s | 1.216s | 1.116s | checkout: COACTUPC.cbl |
| cfg | 27 | 10.093s | 0.374s | 0.314s | 0.214s | 0.515s | 0.515s | checkout: CBTRN03C.cbl |
| dependency | 27 | 12.998s | 0.481s | 0.415s | 0.214s | 0.715s | 0.665s | checkout: CBSTM03A.CBL |

Frontend has the largest aggregate measured time. The dependency CLI reads AIR and constructs its own analysis context/CFG; the separate CFG stage publishes the CFG artifact. These are real process boundaries, not isolated internal algorithm costs.

| Slowest programs | Total | Frontend | Lower | CFG | Dependency | Final stage/status |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| ZIP: CORPT00C.cl2 | 5.619s | 4.922s | 0.665s | — | — | lower/BLOCKED |
| checkout: COACTUPC.cbl | 5.597s | 4.321s | 1.216s | — | — | lower/BLOCKED |
| checkout: CORPT00C.cbl | 5.418s | 4.672s | 0.715s | — | — | lower/BLOCKED |
| ZIP: COACTUPC.cl2 | 5.353s | 4.071s | 1.216s | — | — | lower/BLOCKED |
| checkout: CBSTM03A.CBL | 4.151s | 1.416s | 1.116s | 0.515s | 0.715s | dependency/PARTIAL |
| checkout: CBTRN03C.cbl | 4.106s | 1.516s | 1.116s | 0.515s | 0.665s | dependency/PARTIAL |
| ZIP: CBTRN03C.cbl | 4.050s | 1.567s | 1.016s | 0.515s | 0.665s | dependency/PARTIAL |
| checkout: CBTRN02C.cbl | 3.964s | 1.366s | 1.066s | 0.515s | 0.665s | dependency/PARTIAL |
| ZIP: CBTRN02C.cbl | 3.960s | 1.366s | 1.066s | 0.515s | 0.665s | dependency/PARTIAL |
| ZIP: CBACT04C.cbl | 3.893s | 1.366s | 1.116s | 0.515s | 0.615s | dependency/PARTIAL |

Environment: `Linux-7.0.0-31-generic-x86_64-with-glibc2.43`; Python `3.14.4`; `openjdk version "25.0.4" 2026-07-21 LTS; OpenJDK Runtime Environment Temurin-25.0.4+7 (build 25.0.4+7-LTS); OpenJDK 64-Bit Server VM Temurin-25.0.4+7 (build 25.0.4+7-LTS, mixed mode, sharing)`; 12 available processors; CPU `AMD Ryzen 5 5600GT with Radeon Graphics`; memory `15194040 kB`; JVM args `-Xmx2g`. Source bytes/lines, SP statement/CALL counts and per-program time are paired in `aggregate.exploratorySizeTiming`; these observations do not establish a hotspot or a causal size/performance relationship.

## Advisory capability suggestions

**NON-NORMATIVE — decisionPolicy: HUMAN; rankingAuthority: ADVISORY_ONLY.**

CardDemo evidence provides advisory implementation suggestions. It does not select or mandate the next capability. Human engineering/product judgment remains authoritative.

- **Input and executable-entry completeness**: Investigate the explicit SP entry/input gaps behind the lower ENTRY_START refusal. No missing copybook stubs. Supplying real dependencies or extending a supported entry surface requires separate human scope; gain is not guaranteed.
- **Frontend preprocessing and normalization acceptance**: These are distinct input/frontend acceptance blockers, not evidence that a COBOL statement capability should be implemented. CALLs in programs without SP were never counted.
- **CALL boundary and runtime interpretation**: Known targets can remain model-closed while source, interpretation and control remainders stay open. Site-local uncertainty proves partiality, not that implementing one language feature will close it.
- **Semantic-family candidates for human comparison**: Compare observed MOVE, IF, PERFORM, EVALUATE and GO_TO dimensions with generic-kind inventory limits. Potential bounds overlap; no reliable per-capability causal CALL-impact ranking or automatic next implementation.

The strongest measured opportunity to investigate is input/entry completeness: 40 programs and 37 already-observed CALLs stop there. This is a ceiling on currently hidden analysis, not a forecast of recovered closed sites. The corpus does **not** establish a reliable causal ranking of GO TO versus EVALUATE versus READ or other semantic families. No capability is selected.

Engineering considerations: effort, architectural risk, deadlines, demo goals, strategy and human preference were not measured and are not included in a score. A human may choose any next activity independently of frequency.

## Corpus limitations

One upstream commit and one environment. The 6 no-SP programs hide an unknown statement/CALL population. Embedded CICS/SQL operations are not equated with typed COBOL CALLs. Missing DFH/MQ resources stay absent; existing DCLGENs are available, while availability alone does not implement SQL preprocessing. The CLI publishes one primary program unit per source, so this evaluates every source file, not a certified count of all possible nested compilation units. Native and archived migrated variants are distinct sources; these are not unique PROGRAM-ID counts.

## Interpretation limits

SP counts, entry availability, lowering success, model closure and runtime dependency closure are different facts. Known literal targets do not close open runtime-name/contract policies. Potential impact measures envelope membership, not textual proximity, proven temporal reachability from the region or counterfactual causal gain. Most gap-to-site attribution is UNKNOWN; conservative bounds are deliberately broad.

This JSON/Markdown pair is the historical baseline at the recorded source/pipeline snapshots. Do not silently overwrite it after a capability change. Produce a separately named comparison using the stable source/site keys and delta fields; timing deltas are not automatic gates.

## Reproduction and validation

Full corpus is **LOCAL / ON-DEMAND**. Remote CI remains **FAST ONLY** and tests runner/aggregation/schema logic with synthetic processes/data; it does not download or run CardDemo. See [runner instructions](carddemo-full-runner.md).

Raw logs and SP/AIR/CFG/dependency files: `/tmp/carddemo-full-20260913/canonical`. Output hashes and commands are retained in JSON. The report can be regenerated from these measurements without rerunning a JVM. Only result artifacts and pins are versioned.

## Per-program stage matrix

Times in seconds; F=frontend (normalization/preprocessing included), L=lower, C=CFG, D=dependency. Every source path appears once. Full statements/gaps, source locations, CALL operations, candidate supports and remainders are in [the machine-readable baseline](carddemo-full-baseline.json).

| Upstream source path | F | L | C | D | Total | SP statements / CALLs / analyzed CALLs |
| --- | --- | --- | --- | --- | ---: | --- |
| app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl | BLOCKED 0.364s | NOT_REACHED — | NOT_REACHED — | NOT_REACHED — | 0.386s | not observed |
| app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl | BLOCKED 0.415s | NOT_REACHED — | NOT_REACHED — | NOT_REACHED — | 0.436s | not observed |
| app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl | BLOCKED 0.465s | NOT_REACHED — | NOT_REACHED — | NOT_REACHED — | 0.486s | not observed |
| app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl | BLOCKED 0.415s | NOT_REACHED — | NOT_REACHED — | NOT_REACHED — | 0.438s | not observed |
| app/app-authorization-ims-db2-mq/cbl/COPAUS2C.cbl | PARTIAL 1.066s | PARTIAL 0.715s | PARTIAL 0.314s | PARTIAL 0.415s | 2.582s | 55 / 0 / 0 |
| app/app-authorization-ims-db2-mq/cbl/DBUNLDGS.CBL | PARTIAL 1.116s | BLOCKED 0.565s | NOT_REACHED — | NOT_REACHED — | 1.707s | 67 / 4 / 0 |
| app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL | PARTIAL 1.166s | BLOCKED 0.616s | NOT_REACHED — | NOT_REACHED — | 1.805s | 88 / 3 / 0 |
| app/app-authorization-ims-db2-mq/cbl/PAUDBUNL.CBL | PARTIAL 1.117s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 1.755s | 73 / 2 / 0 |
| app/app-transaction-type-db2/cbl/COBTUPDT.cbl | PARTIAL 1.016s | PARTIAL 0.665s | PARTIAL 0.314s | PARTIAL 0.365s | 2.408s | 58 / 0 / 0 |
| app/app-transaction-type-db2/cbl/COTRTLIC.cbl | BLOCKED 0.264s | NOT_REACHED — | NOT_REACHED — | NOT_REACHED — | 0.286s | not observed |
| app/app-transaction-type-db2/cbl/COTRTUPC.cbl | PARTIAL 2.118s | BLOCKED 0.765s | NOT_REACHED — | NOT_REACHED — | 2.916s | 433 / 0 / 0 |
| app/app-vsam-mq/cbl/COACCT01.cbl | PARTIAL 1.466s | BLOCKED 0.665s | NOT_REACHED — | NOT_REACHED — | 2.162s | 205 / 9 / 0 |
| app/app-vsam-mq/cbl/CODATE01.cbl | PARTIAL 1.266s | BLOCKED 0.665s | NOT_REACHED — | NOT_REACHED — | 1.957s | 181 / 9 / 0 |
| app/cbl/CBACT01C.cbl | PARTIAL 1.218s | PARTIAL 0.916s | PARTIAL 0.415s | PARTIAL 0.515s | 3.219s | 190 / 2 / 2 |
| app/cbl/CBACT02C.cbl | PARTIAL 1.016s | PARTIAL 0.715s | PARTIAL 0.314s | PARTIAL 0.415s | 2.515s | 63 / 1 / 1 |
| app/cbl/CBACT03C.cbl | PARTIAL 0.965s | PARTIAL 0.715s | PARTIAL 0.314s | PARTIAL 0.415s | 2.466s | 64 / 1 / 1 |
| app/cbl/CBACT04C.cbl | PARTIAL 1.366s | PARTIAL 1.066s | PARTIAL 0.465s | PARTIAL 0.615s | 3.793s | 294 / 1 / 1 |
| app/cbl/CBCUS01C.cbl | PARTIAL 1.016s | PARTIAL 0.715s | PARTIAL 0.314s | PARTIAL 0.415s | 2.516s | 64 / 1 / 1 |
| app/cbl/CBEXPORT.cbl | PARTIAL 1.216s | PARTIAL 1.066s | PARTIAL 0.415s | PARTIAL 0.565s | 3.442s | 224 / 1 / 1 |
| app/cbl/CBIMPORT.cbl | PARTIAL 1.166s | PARTIAL 0.865s | PARTIAL 0.415s | PARTIAL 0.515s | 3.095s | 175 / 1 / 1 |
| app/cbl/CBSTM03A.CBL | PARTIAL 1.416s | PARTIAL 1.116s | PARTIAL 0.515s | PARTIAL 0.715s | 4.151s | 424 / 14 / 14 |
| app/cbl/CBSTM03B.CBL | PARTIAL 0.965s | PARTIAL 0.665s | PARTIAL 0.314s | PARTIAL 0.365s | 2.356s | 53 / 0 / 0 |
| app/cbl/CBTRN01C.cbl | PARTIAL 1.266s | PARTIAL 0.966s | PARTIAL 0.415s | PARTIAL 0.565s | 3.385s | 216 / 1 / 1 |
| app/cbl/CBTRN02C.cbl | PARTIAL 1.366s | PARTIAL 1.066s | PARTIAL 0.515s | PARTIAL 0.665s | 3.964s | 339 / 1 / 1 |
| app/cbl/CBTRN03C.cbl | PARTIAL 1.516s | PARTIAL 1.116s | PARTIAL 0.515s | PARTIAL 0.665s | 4.106s | 315 / 1 / 1 |
| app/cbl/COACTUPC.cbl | PARTIAL 4.321s | BLOCKED 1.216s | NOT_REACHED — | NOT_REACHED — | 5.597s | 1412 / 1 / 0 |
| app/cbl/COACTVWC.cbl | PARTIAL 2.368s | BLOCKED 0.765s | NOT_REACHED — | NOT_REACHED — | 3.162s | 271 / 0 / 0 |
| app/cbl/COADM01C.cbl | PARTIAL 1.316s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 1.958s | 80 / 0 / 0 |
| app/cbl/COBIL00C.cbl | PARTIAL 1.667s | BLOCKED 0.665s | NOT_REACHED — | NOT_REACHED — | 2.361s | 189 / 0 / 0 |
| app/cbl/COBSWAIT.cbl | PARTIAL 0.815s | PARTIAL 0.515s | PARTIAL 0.214s | PARTIAL 0.264s | 1.837s | 4 / 1 / 1 |
| app/cbl/COCRDLIC.cbl | PARTIAL 2.518s | BLOCKED 0.866s | NOT_REACHED — | NOT_REACHED — | 3.421s | 498 / 0 / 0 |
| app/cbl/COCRDSLC.cbl | PARTIAL 1.667s | BLOCKED 0.715s | NOT_REACHED — | NOT_REACHED — | 2.412s | 259 / 0 / 0 |
| app/cbl/COCRDUPC.cbl | PARTIAL 2.268s | BLOCKED 0.815s | NOT_REACHED — | NOT_REACHED — | 3.120s | 485 / 0 / 0 |
| app/cbl/COMEN01C.cbl | PARTIAL 1.416s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 2.057s | 91 / 0 / 0 |
| app/cbl/CORPT00C.cbl | PARTIAL 4.672s | BLOCKED 0.715s | NOT_REACHED — | NOT_REACHED — | 5.418s | 220 / 2 / 0 |
| app/cbl/COSGN00C.cbl | PARTIAL 1.218s | BLOCKED 0.565s | NOT_REACHED — | NOT_REACHED — | 1.808s | 70 / 0 / 0 |
| app/cbl/COTRN00C.cbl | PARTIAL 2.168s | BLOCKED 0.816s | NOT_REACHED — | NOT_REACHED — | 3.017s | 294 / 0 / 0 |
| app/cbl/COTRN01C.cbl | PARTIAL 1.466s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 2.110s | 95 / 0 / 0 |
| app/cbl/COTRN02C.cbl | PARTIAL 1.917s | BLOCKED 0.715s | NOT_REACHED — | NOT_REACHED — | 2.666s | 300 / 2 / 0 |
| app/cbl/COUSR00C.cbl | PARTIAL 2.118s | BLOCKED 0.765s | NOT_REACHED — | NOT_REACHED — | 2.918s | 288 / 0 / 0 |
| app/cbl/COUSR01C.cbl | PARTIAL 1.316s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 1.957s | 94 / 0 / 0 |
| app/cbl/COUSR02C.cbl | PARTIAL 1.567s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 2.211s | 148 / 0 / 0 |
| app/cbl/COUSR03C.cbl | PARTIAL 1.416s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 2.058s | 115 / 0 / 0 |
| app/cbl/CSUTLDTC.cbl | PARTIAL 1.016s | PARTIAL 0.615s | PARTIAL 0.264s | PARTIAL 0.364s | 2.308s | 27 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBACT01C.cbl | PARTIAL 1.066s | PARTIAL 0.765s | PARTIAL 0.365s | PARTIAL 0.465s | 2.730s | 77 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBACT02C.cbl | PARTIAL 1.066s | PARTIAL 0.715s | PARTIAL 0.314s | PARTIAL 0.415s | 2.564s | 63 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBACT03C.cbl | PARTIAL 1.016s | PARTIAL 0.765s | PARTIAL 0.314s | PARTIAL 0.415s | 2.568s | 64 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBACT04C.cbl | PARTIAL 1.366s | PARTIAL 1.116s | PARTIAL 0.515s | PARTIAL 0.615s | 3.893s | 294 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBCUS01C.cbl | PARTIAL 1.016s | PARTIAL 0.765s | PARTIAL 0.314s | PARTIAL 0.415s | 2.567s | 64 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBSTM03A.cbl | BLOCKED 0.465s | NOT_REACHED — | NOT_REACHED — | NOT_REACHED — | 0.486s | not observed |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBSTM03B.cbl | PARTIAL 0.965s | PARTIAL 0.715s | PARTIAL 0.314s | PARTIAL 0.365s | 2.410s | 53 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBTRN01C.cbl | PARTIAL 1.266s | PARTIAL 0.966s | PARTIAL 0.415s | PARTIAL 0.565s | 3.392s | 216 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBTRN02C.cbl | PARTIAL 1.366s | PARTIAL 1.066s | PARTIAL 0.515s | PARTIAL 0.665s | 3.960s | 339 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CBTRN03C.cbl | PARTIAL 1.567s | PARTIAL 1.016s | PARTIAL 0.515s | PARTIAL 0.665s | 4.050s | 315 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COACTUPC.cl2 | PARTIAL 4.071s | BLOCKED 1.216s | NOT_REACHED — | NOT_REACHED — | 5.353s | 1397 / 1 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COACTVWC.cl2 | PARTIAL 2.418s | BLOCKED 0.715s | NOT_REACHED — | NOT_REACHED — | 3.165s | 271 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COADM01C.cl2 | PARTIAL 1.316s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 1.958s | 74 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COBIL00C.cl2 | PARTIAL 1.667s | BLOCKED 0.665s | NOT_REACHED — | NOT_REACHED — | 2.361s | 189 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COCRDLIC.cl2 | PARTIAL 2.518s | BLOCKED 0.815s | NOT_REACHED — | NOT_REACHED — | 3.373s | 498 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COCRDSLC.cl2 | PARTIAL 1.667s | BLOCKED 0.715s | NOT_REACHED — | NOT_REACHED — | 2.413s | 259 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COCRDUPC.cl2 | PARTIAL 2.368s | BLOCKED 0.815s | NOT_REACHED — | NOT_REACHED — | 3.221s | 485 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COMEN01C.cl2 | PARTIAL 1.316s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 1.959s | 81 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CORPT00C.cl2 | PARTIAL 4.922s | BLOCKED 0.665s | NOT_REACHED — | NOT_REACHED — | 5.619s | 220 / 2 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COSGN00C.cl2 | PARTIAL 1.216s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 1.856s | 70 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COTRN00C.cl2 | PARTIAL 2.218s | BLOCKED 0.765s | NOT_REACHED — | NOT_REACHED — | 3.017s | 294 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COTRN01C.cl2 | PARTIAL 1.717s | BLOCKED 0.716s | NOT_REACHED — | NOT_REACHED — | 2.459s | 95 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COTRN02C.cl2 | PARTIAL 2.018s | BLOCKED 0.765s | NOT_REACHED — | NOT_REACHED — | 2.817s | 300 / 2 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COUSR00C.cl2 | PARTIAL 2.168s | BLOCKED 0.765s | NOT_REACHED — | NOT_REACHED — | 2.970s | 288 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COUSR01C.cl2 | PARTIAL 1.316s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 1.960s | 94 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COUSR02C.cl2 | PARTIAL 1.617s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 2.260s | 148 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/COUSR03C.cl2 | PARTIAL 1.366s | BLOCKED 0.615s | NOT_REACHED — | NOT_REACHED — | 2.007s | 115 / 0 / 0 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/CSUTLDTC.cbl | PARTIAL 1.016s | PARTIAL 0.665s | PARTIAL 0.264s | PARTIAL 0.364s | 2.354s | 29 / 1 / 1 |
| samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/SDSF.cbl | PARTIAL 0.715s | PARTIAL 0.465s | PARTIAL 0.214s | PARTIAL 0.214s | 1.632s | 2 / 0 / 0 |
