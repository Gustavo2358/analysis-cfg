# Managed contextual local-label references

AS-W02 / IN_PROGRESS. air-java8fcfc37af28498d428db525215e922e845f3f0fa
provides SnapshotLocalLabels and the borrowed projected diagnostic SPI. This
frontier replaces per-context scans/error arrays for actual local-label reference
lists, including invalid lists. Full reference/visibility admission is still pending.

PagedSnapshotLabelStorage uses one canonical literal-key arena plus two shared
managed primitive columns for all lists and Unit buckets. Sparse metadata is keyed
by stable table/group handles. Append stores each ordered row as label source,
complete Unit key and missing-declaration bit. Group counts and a linked group
order occupy that same metadata column. finish assigns dense global missing/posting
spans, fills them in one linear row fold and checks every span/count before sealing.
There is no Java owner, array or directory per Unit. Only one unfinished list is
admitted at a time so its row payload stays contiguous; source fold/table creation
is sequential in the producer kernel.

Matching ordinals are sorted by construction. Foreign ordinal j is selected with
one binary search on m[i]-i, then j+upperBound; absent buckets select directly.
Missing ordinals have direct random access. Queries never insert canonical keys,
materialize foreign arrays or retain query history. Duplicate row occurrences are
preserved. All growing payload/dictionaries/directories share the borrowed run
PageStore and VALIDATION ledger. Control/staging/directory owners are funded before
allocation, addresses/counts are checked, operational failures poison the port,
and close releases transferred state while the run store remains caller-owned.

Five adapter laws cover exact ordered duplicate/missing/complement rows, 4096 Unit
buckets, late foreign selection, absent context misses, both input backends, actual
projected diagnostic kind/rule/count/order, repeated zero-retention queries, every
primitive WORK interruption, denied construction and empty ordinal failure. Focused
raw evidence is in the authorized AIR-scale artifacts. A 4096-bucket file fixture
peaks21048B under65536B with2716192B temporary payload. Its late-foreign query costs
1043 ledger work units, within the independent non-scan envelope. Combined paged
AIR+keys+declarations+labels+diagnostics peaks90096B under131072B with1143392B
payload; 512 labels count256 errors for the local Unit and512 for the foreign Unit.
The two rules remain independently ordered on the same missing foreign label.
These are managed ledger/fixture results, not JVM retained-heap or global bounds.

Compiled gap-boundary and omitted-context mutations fail the exact independent
relation; query interning fails the read-only miss contract. Original passes.
Mandatory consumer FAST1062tests/zero failures/errors/skips passed418.296s.
Clean compiled0e228b93e60eff0f0764f6fab7e4d9a673b4695d passed3public products/
6actual CLI executions with full dependency/support semantic parity versus the
preserved frozen729baseline, excluding only two historical work-counter maps. The unchanged legacy CLI still
uses resident admission: its parity cannot certify managed admission integration.
Complete type/domain/premise/capability/operation rules, CheckedSnapshot issuance,
incremental official JSON, root retirement and AS-W03-W10 remain required.
