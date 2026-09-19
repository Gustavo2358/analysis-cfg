# W3 — COPYBOOK and DCLGEN source dependencies

Status: IN_PROGRESS. STACKED ON W2 #42, base
`337196db14157037f91c15aae1f1baa96e6cbf1f`. Do not merge before W2.

## Architecture and discovery

COPY's name, resolution and original span already existed in the frontend
preprocessor, but successful/empty COPY directives vanished on replacement.
SourceMap carried expanded content provenance, not an inventory of directives.
SQL regions were opaque: no prior DCLGEN authority or nominal INCLUDE contract.
The W3 producer captures syntax before expansion and uses explicit configured
artifact inventory for positive DCLGEN classification. SQLCA, SQLDA and generic
includes are never promoted based on INCLUDE syntax.

Minimal path: source fact -> SP 2.30 -> existing AIR nominal resources ->
SourceDependencyAnalysis -> dependencies.json. Frontend/lower/analysis-cfg change.
AIR Java remains W2 `646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa`; no AIR-JAVA W3 PR.
AIR ResourceDescription, ResourceDeclaration and original provenance suffice;
ResourceBinding executable uses and DependencyEnvelope are unnecessary. Existing
Artifacts.Relation can represent an artifact edge but does not itself carry all
program association/resolution/authority fields. No new operation/schema/solver.

## Product contract

`analysis-dependency-result` version **2.4.0** adds required `sourceDependencies`:

* profile `source-dependencies@1`, available, remainder, gapCodes, occurrences;
* dependencies keyed by program UnitId + kind + canonical name + qualification;
* kinds COPYBOOK, DCLGEN, SQL_INCLUDE;
* nonempty supports, each with occurrence ResourceId, origin OriginId, sourceOwner
  ArtifactId, DIRECT/TRANSITIVE relationship, resolution, resolvedArtifact and
  classificationAuthority;
* per-dependency remainder preserves unresolved artifact or unknown classification.

The source inventory is independent of CALL/FILE analysis. Old AIR publications
without this profile yield explicitly unavailable source inventory, not an empty
complete set. Program identifiers remain AIR identities as in the existing product.
Artifacts and origins in the parent document allow navigation to original file,
line/span and include chain. Resolved artifact identity is a logical input path;
it does not mean that downstream opened or verified that path.

The transport profile is defined in the pinned lower's
`docs/product/source-dependencies-w3.md`. Unknown source kind/resolution/authority,
missing declaration/evidence, executable uses or invalid DCLGEN classification
reject. No inference from names, paths, expanded synthetic lines or runtime values.

Nested model is program-centric with original owners: PROGRAM -> A is DIRECT;
A -> B has sourceOwner A, original COPY B span and TRANSITIVE relation to PROGRAM.
Repetition deduplicates the nominal key while retaining all distinct resource
occurrences. Both resolved and unresolved supports may coexist; one resolved
occurrence never erases another occurrence's remainder.

`DependencyJson` orders dependency keys and supports canonically. Time measurements
are outside the product; equal publication bytes produce byte-identical JSON.
The strict Python reader accepts 2.4.0 and validates closed shapes, enums,
references, original ownership, support cardinality and remainder consistency.
The W2 reader rejects 2.4.0 explicitly. Old supported versions retain their rules.

## Complexity and operational policy

SourceDependencyAnalysis accepts Publication directly. It indexes origins once,
then resources once, groups in maps and sorts final keys/supports. Complexity is
O(A + O + R + N log N), plus provenance payload. No pairwise artifact comparisons.
Source aggregation neither accepts nor invokes CFG/RD/values/physical analysis.
The combined dependency product still runs its existing runtime consumers for
CALL/FILE/CICS, independently. Source facts also survive the product's partial
runtime-inventory branch.

LOGICAL_ONLY remains default. Physical is EXPERIMENTAL, NOT PRODUCTION QUALIFIED,
DEFAULT OFF and NO AUTOMATIC FALLBACK. W3 does not change the physical engine.

## Limits and qualification

SQL INCLUDE supports a syntactically proved single unquoted member; other
INCLUDE-shaped payloads open a gap without fabricated candidate. DCLGEN contents
and DB2 tables are out of scope. Qualified COPY names are retained, but flat
library resolution cannot prove qualified artifact identity. Cyclic COPY facts
are retained in SP; when preprocessing invalidates the primary entry, existing
lower admission blocks the entire runtime publication. This cycle probe remains
an explicit blocked E2E case. Unproved program/source ownership rejects rather
than assigning a dependency to the wrong program.

SourceDependencyTest provides independent in-memory AIR oracles, original-owner
checks, dedup/supports, invalid authority/profile rejection, FILE composition,
determinism, codec roundtrip and a thousand-occurrence no-solver check.
The source E2E fixture set lives in the pinned frontend. Local scale probes report
frontend/lower/aggregation timing and AIR/JSON sizes, with no runtime timing fields.

Corporate: NOT EXECUTED; NOT AN ACCEPTANCE GATE; NO CORPORATE SOURCE USED.
W4 recommendation: define a separate positively proved DB2 table reference contract
before parsing SQL statements. W3 does not implement table extraction.

## Synthetic qualification

Frontend pin `1d20897965db55fca39a6a654c0386229f1e5232`; lower pin
`c55c9ca13afa46e053c53491c2af3f1cc3ed1423`; AIR Java remains the W2 pin above.

### Acceptance

| Fixture | Kind/name actual (= expected) | Resolved | Supports | Remainder |
| --- | --- | --- | --- | --- |
| composition | COPYBOOK:CPY001, DCLGEN:DCLCLI | true, true | 1, 1 | false |
| copy-case | COPYBOOK:CPY001 | true | 2 | false |
| copy-comment-negative | none | n/a | 0 | false |
| copy-direct | COPYBOOK:CPY001 | true | 1 | false |
| copy-missing | COPYBOOK:CPY404 | false | 1 | true |
| copy-nested | COPYBOOK:A, COPYBOOK:B | true, true | 1, 1 | false |
| copy-qualified | COPYBOOK:X | false | 2 | true |
| copy-repeated | COPYBOOK:CPY001 | true | 2 | false |
| copy-replacing | COPYBOOK:CPY001 | true | 1 | false |
| copy-two | COPYBOOK:CPY001, COPYBOOK:CPY002 | true, true | 1, 1 | false |
| dclgen-direct | DCLGEN:DCLCLI | true | 1 | false |
| dclgen-inside-copybook | COPYBOOK:A, DCLGEN:DCLCLI | true, true | 1, 1 | false |
| dclgen-missing | DCLGEN:DCL404 | false | 1 | true |
| dclgen-repeated | DCLGEN:DCLCLI | true | 2 | false |
| dclgen-two | DCLGEN:DCLCLI, DCLGEN:DCLCTA | true, true | 1, 1 | false |
| generic-include-not-dclgen | SQL_INCLUDE:GENERIC | true | 1 | false |
| malformed-sql-include | none | n/a | 0 | true |
| sql-comment-negative | none | n/a | 0 | false |
| sqlca-not-dclgen | SQL_INCLUDE:SQLCA | false | 1 | true |
| sqlda-not-dclgen | SQL_INCLUDE:SQLDA | false | 1 | true |
| unresolved-include | SQL_INCLUDE:SOMETHING | false | 1 | true |

All 21 products match the independent fixture oracle and original owner/line/column checks.
Both final runs are byte-identical. COPY B and nested DCLGEN retain A.cpy:1:7; A points to program.cbl:5:7.
Composition also proves CALL SUBA and FILE DD001.
Extra copy-cycle: SP retains 3 occurrences including CYCLIC; existing lower primary-entry admission blocks runtime AIR (exit 4). No E2E PASS claimed.

### Scale

| Kind | Occurrences | Unique | Frontend ms | Lower ms | Aggregation ms (median) | AIR bytes | JSON bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| COPYBOOK | 10 | 10 | 815.3 | 615.1 | 0.252 | 35663 | 28217 |
| COPYBOOK | 100 | 100 | 865.3 | 665.1 | 0.954 | 226761 | 213468 |
| COPYBOOK | 1000 | 1000 | 1015.5 | 1015.8 | 3.664 | 2141089 | 2069299 |
| DCLGEN | 10 | 10 | 865.3 | 615.0 | 0.264 | 39834 | 31224 |
| DCLGEN | 100 | 100 | 915.4 | 665.1 | 0.746 | 232196 | 217739 |
| DCLGEN | 1000 | 1000 | 1065.6 | 1015.7 | 4.336 | 2159128 | 2086174 |

Single program, unique synthetic nominal members, real source CLIs; final scale ran without concurrent builds or other campaign workloads started by this task.
Aggregation: 5 warmups and median of 11 measurements inside the same JVM, excluding AIR decode and runtime consumers. Frontend/lower timings include JVM startup.
Payload growth is approximately linear; aggregation includes canonical sorting O(N log N), with no pairwise artifact comparisons. Fixed startup/JIT costs dominate small cases.
Memory was bounded by -Xmx1g, but resident/peak memory was not measured. All cases retained exact counts and zero physical groups/writes.

W1 A–D and 17 source E2E W2 regressions PASS on the same frozen runtime.
Frontend FAST376 and lower FAST PASS. CFG local/remote exact-head gate results
are recorded in the stacked PR closeout; no pending gate is represented as PASS here.

Reproduce using `scripts/project/source_dependencies_w3.py matrix|scale --runtime runtime.json --output new-directory`.
The runtime file supplies exact checkout and classpath/main entries; it is local execution configuration, not product data.
