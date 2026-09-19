# W3 — COPYBOOK and DCLGEN source dependencies

Status: DB2 implementation present; current integrated qualification status is recorded in the existing stacked PR. STACKED ON W2 #42, base
`337196db14157037f91c15aae1f1baa96e6cbf1f`. Do not merge before W2.

## Architecture and discovery

COPY's name, resolution and original span already existed in the frontend
preprocessor, but successful/empty COPY directives vanished on replacement.
SourceMap carried expanded content provenance, not an inventory of directives.
SQL regions were opaque: no prior DCLGEN authority or nominal INCLUDE contract.
The W3 producer captures syntax before expansion and uses explicit configured
artifact inventory for positive DCLGEN classification. SQLCA, SQLDA and generic
includes are never promoted based on INCLUDE syntax.

Minimal path: source fact -> SP 2.31 -> existing AIR nominal resources ->
SourceDependencyAnalysis -> dependencies.json. Frontend/lower/analysis-cfg change.
AIR Java remains W2 `646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa`; no AIR-JAVA W3 PR.
AIR ResourceDescription, ResourceDeclaration and original provenance suffice;
ResourceBinding executable uses and DependencyEnvelope are unnecessary. Existing
Artifacts.Relation can represent an artifact edge but does not itself carry all
program association/resolution/authority fields. No new operation/schema/solver.

## Product contract

`analysis-dependency-result` version **2.5.0** extends required `sourceDependencies`:

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
The strict Python reader accepts 2.5.0 and 2.4.0 and validates closed shapes, enums,
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
are not inspected for implicit tables. Qualified COPY names are retained, but flat
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
DB2 TABLE continuation is implemented in the same W3 source contract, described below.

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

## DB2 TABLE continuation

The previous COPYBOOK/DCLGEN/SQL_INCLUDE qualification remains a completed checkpoint.
DB2 TABLE qualification is now required before the campaign returns to READY_FOR_REVIEW.
Same branch and PR; no new administrative W4, no AIR/runtime/physical engine changes.

## DB2 TABLE source dependencies

Static SQL is extracted before embedded-language framing, from the normalized original EXEC SQL region and its existing SourceMap span. A lightweight tokenizer neutralizes SQL strings, host variables, `--` and `/* */` comments; paired parentheses are indexed once. A deterministic structural scanner recognizes table positions and scoped CTEs without constructing SQL grammar/AST/IR or evaluating expressions. Each region has independent state. Unsupported/malformed supported structure rejects all tentative table facts for that region and opens a gap.

Supported: SELECT FROM/JOIN (including multiple and comma joins), schema qualification, INSERT target and INSERT SELECT, UPDATE/DELETE targets and nested SELECT, MERGE target and nominal/derived USING, CTE definitions, derived SELECT, UNION/EXCEPT/INTERSECT branches, and simple DECLARE name CURSOR FOR SELECT. Aliases are consumed at relation boundaries. Local CTE names are indexed before traversing definitions; recursive/forward CTE references conservatively open DB2_RECURSIVE_CTE_UNSUPPORTED. SQL expression validity, column binding and catalog object kinds are not certified: DB2_TABLE denotes a syntactic relation reference, which a catalog could resolve to a table/view/alias. No catalog resolution is attempted.

Identity: uppercase ordinary identifier name plus explicit qualification; CLIENTE and DBPROD.CLIENTE remain distinct. Delimited identifiers are tokenized but conservatively rejected with DB2_DELIMITED_IDENTIFIER_UNSUPPORTED because the current source identity contract folds case. Table functions, VALUES-derived relations, DDL, stored procedures, unfamiliar relation constructs, and unsupported cursor options open explicit gaps. Nesting beyond 128 levels opens a gap. No inference from DCLGEN or INCLUDE names/content.

SP 2.31.0 adds DB2_TABLE and typed operation/access per occurrence. Non-DB2 occurrences use NONE/NONE. SELECT uses READ; INSERT/UPDATE/DELETE use WRITE; MERGE target uses MERGE/READ_WRITE and nominal USING uses MERGE/READ (derived SELECT uses SELECT/READ). Authority STATIC_SQL_TABLE_POSITION is required. Resolution NOT_APPLICABLE is exclusive to DB2_TABLE: catalog lookup is outside this product and no physical artifact identity is fabricated. This nominal completeness is separate from source artifact resolution.

PREPARE and EXECUTE, including EXECUTE IMMEDIATE literals, emit DYNAMIC_SQL_NOT_ANALYZED with remainder=true and no invented table. PossibleValues is never invoked. Other unsupported SQL shapes remain open. Existing source gap transport is conservative at compilation scope; no statement-level SQL gap provenance type is introduced in this continuation.

Each support retains original EXEC SQL span, program association, sourceOwner and include chain. SQL in A.cpy is TRANSITIVE to the program and points into A.cpy. Repeated SELECT/UPDATE of the same qualified table aggregate into one dependency with separate usage-bearing supports. Source aggregation uses maps and canonical sorting, no CFG/reachability/RD/values/physical inputs. Runtime SQL stays opaque in its existing path.

AIR stays unchanged at 646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa. Existing LiteralTarget category source-db2_table and ResourceDeclaration classification source.NOT_APPLICABLE carry nominal references; nameSource source.STATIC_SQL_<operation>_<access>@1 carries a closed usage profile. No runtime uses, objects or operations are added. The dependency wire is 2.5.0, with operation/access on every source support; 2.4.0 readers reject it. The new reader retains explicit support for older wires; lower upgrades legacy SP2.30 NONE usage only after rejecting DB2/new fields in that old envelope.

Scope remains source-only, physical default OFF with NO AUTOMATIC FALLBACK. Existing cyclic COPY primary-entry admission limitation remains unchanged. Corporate NOT EXECUTED / NOT AN ACCEPTANCE GATE / NO CORPORATE SOURCE USED.

Primary language references: [IBM CTE](https://www.ibm.com/docs/en/db2-for-zos/12.0.0?topic=statement-common-table-expression), [identifiers](https://www.ibm.com/docs/en/db2/12.1.x?topic=elements-identifiers), [tokens/comments](https://www.ibm.com/docs/en/db2-as-a-service?topic=elements-tokens). Scope is deliberately smaller than the SQL language.

### DB2 synthetic scale

| SQL statements | Table occurrences | Unique | Frontend ms | Extraction ms | Lower ms | Aggregation ms | AIR bytes | JSON bytes |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 10 | 10 | 865.4 | 0.206 | 665.1 | 0.292 | 123727 | 58560 |
| 100 | 100 | 100 | 915.5 | 0.624 | 865.5 | 0.640 | 1108189 | 517514 |
| 1000 | 1000 | 1000 | 1266.1 | 2.493 | 1767.0 | 4.708 | 10965001 | 5117428 |
| 1000 | 1000 | 100 | 1266.0 | 2.299 | 1767.1 | 4.459 | 10965001 | 4954527 |

Extraction and source aggregation are medians of 11 samples after 5 warmups; no runtime consumers/decoding included in those two timings. Frontend and lower include JVM startup, parsing and serialization. Same frozen runtime, no concurrent task builds.
Counts exactly match all four oracles. Linear payload growth; map/index lookups and canonical O(N log N) sorting, no table pairwise loop. Small sizes are dominated by fixed/JIT cost. The repeated case retains 1000 supports with 100 unique identities.
Memory bounded at -Xmx1g; peak/RSS not measured. Combined output includes existing opaque runtime SQL operations, explaining its size relative to empty-member COPY scale.

DB2 final producer pins: frontend `49ce9a7e727ad0c3301cc1fdbb6da9828439b9ce`; lower `3a2c9751c6b15239a5c63812038c77bf472e9b37`. Earlier qualification and pins above remain historical checkpoint evidence.

### DB2 acceptance — real source pipeline

| Fixture | Expected tables | Actual tables | Operations/access | Source owner | Remainder |
| --- | --- | --- | --- | --- | --- |
| db2-alias-not-table | CLIENTE, CONTA | CLIENTE, CONTA | SELECT/READ | program.cbl | false |
| db2-case | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-comment-negative | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-composition | DBPROD.CLIENTE, DBPROD.CONTA | DBPROD.CLIENTE, DBPROD.CONTA | SELECT/READ | program.cbl | false |
| db2-cte | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-cte-name-not-table | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-cursor | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-delete | CLIENTE, HISTORICO | CLIENTE, HISTORICO | DELETE/WRITE, SELECT/READ | program.cbl | false |
| db2-delimited-name | none | none | — | — | true |
| db2-derived-table | CLIENTE, CONTA | CLIENTE, CONTA | SELECT/READ | program.cbl | false |
| db2-dynamic-execute-immediate | none | none | — | — | true |
| db2-dynamic-prepare | none | none | — | — | true |
| db2-host-variable-negative | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-insert | CLIENTE | CLIENTE | INSERT/WRITE | program.cbl | false |
| db2-insert-select | DESTINO, ORIGEM | DESTINO, ORIGEM | INSERT/WRITE, SELECT/READ | program.cbl | false |
| db2-inside-copybook | CLIENTE | CLIENTE | SELECT/READ | A.cpy | false |
| db2-malformed-static-sql | none | none | — | — | true |
| db2-merge | AJUSTE, CONTA | AJUSTE, CONTA | MERGE/READ, MERGE/READ_WRITE | program.cbl | false |
| db2-merge-derived | AJUSTE, CONTA | AJUSTE, CONTA | MERGE/READ_WRITE, SELECT/READ | program.cbl | false |
| db2-multiple-ctes | CLIENTE, CONTA | CLIENTE, CONTA | SELECT/READ | program.cbl | false |
| db2-multiple-statements-negative | none | none | — | — | true |
| db2-nested-comment-negative | none | none | — | — | true |
| db2-recursive-cte | none | none | — | — | true |
| db2-repeated-mixed-access | CLIENTE | CLIENTE | SELECT/READ, UPDATE/WRITE | program.cbl | false |
| db2-schema-qualified | CLIENTE, DBPROD.CLIENTE | CLIENTE, DBPROD.CLIENTE | SELECT/READ | program.cbl | false |
| db2-select-join | CLIENTE, CONTA, MOVIMENTO, SALDO | CLIENTE, CONTA, MOVIMENTO, SALDO | SELECT/READ | program.cbl | false |
| db2-select-one | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-statement-scope | A, CLIENTE | A, CLIENTE | SELECT/READ | program.cbl | false |
| db2-string-from-negative | CLIENTE | CLIENTE | SELECT/READ | program.cbl | false |
| db2-union | CLIENTE, CONTA | CLIENTE, CONTA | SELECT/READ | program.cbl | false |
| db2-unsupported-sql-shape | none | none | — | — | true |
| db2-update | CONTA | CONTA | UPDATE/WRITE | program.cbl | false |
| db2-update-subquery | CLIENTE, CONTA | CLIENTE, CONTA | SELECT/READ, UPDATE/WRITE | program.cbl | false |

33/33 PASS in two independent runs; final dependencies.json byte-identical. Expectations are authored separately from extractor output.
Original EXEC SQL span and column 7 checked for every support. Nested table owner is A.cpy:1, TRANSITIVE; the main program association is retained. Repeated mixed-access CLIENTE is one identity with 3 supports at distinct original statements (SELECT/UPDATE/SELECT).
Dynamic PREPARE/EXECUTE IMMEDIATE produce no tables and DYNAMIC_SQL_NOT_ANALYZED. Delimited names, recursive CTEs and unsupported relation shapes intentionally pass as explicit partial negatives.
Composition proves COPYBOOK CPY001, DCLGEN DCLCLI, generic SQL_INCLUDE GENERIC, DB2_TABLE DBPROD.CLIENTE/DBPROD.CONTA, CALL SUBA and FILE DD001. Physical metrics are 1/0/0/0 (logical/experimental/groups/writes).

Nested bracketed comments and multi-statement regions are additional explicit partial negatives, with zero invented tables.

The prior 21 W3 source cases also PASS in two executions on the final DB2 runtime, with byte-identical 2.5.0 products and unchanged nominal behavior. The extra cyclic COPY case remains explicitly BLOCKED at existing lower primary-entry admission.
