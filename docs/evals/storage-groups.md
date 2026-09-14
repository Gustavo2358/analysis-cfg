# Group storage: independent file qualification

The local ST-W3 runner is `scripts/project/e2e_storage_groups.py`. It requires a
clean frozen SP 2.7 runtime with the explicit IBM Enterprise 6.4 DISPLAY/IBM1047
profile. `storage_group_fixtures.py` defines source recipes and manual physical
expectations without importing frontend, lower or analysis implementations.

The 17 cases cover group-to-child, nesting, FILLER, variable renaming, repeated
names with qualifiers, 1/2/5/40 children and roots, repeated calls, capture,
copybook provenance, scalar INT mixture and unknown prefix. Byte goldens use an
independent fixture alphabet. The numeric scalar has no claimed byte extent:
its explicit DATA relation and scalar coverage identify its sole logical Cell.

Every positive follows SP physical node/base/view facts into AIR coverage IDs,
then checks exact write intervals, explicit codec, allocation premises, computed
CALL identity, BEFORE values, literal supports and reaching-definition ranges.
Copy capture has distinct COPY definition and original literal support. A second
CALL keeps the prior candidate with model remainder due to possible foreign
writes. Source remainder is checked independently and remains open.

`StorageE2eProbe` is a test-only adapter around the existing RD/value engines and
batch/replay APIs. Its diagnostic JSON is not a public result wire. It verifies
canonical AIR encode/decode stability and records physical ranges and metrics.
The product query/wire extension remains ST-W5 work.

The default run repeats all five boundaries byte-for-byte in separate JVMs and
permutes nonsemantic SP/AIR inventories. Permuted AIR must preserve CFG,
dependency and the RD/value diagnostics. Unknown offsets stay unknown and yield
an explicit unsupported/open CALL, never zero or an empty closed value set.

Run locally with `--work <new-directory> --runtime <frozen-runtime.json>`.
Development narrowing uses `--cases`, `--attempts 1`, `--no-permutations`; a
qualified wave uses all cases with defaults. Heavy file qualification supplements
the critical FAST classes ByteImageTest, RegionalValuesTest,
RegionalDependencyTest and the producer/reader contract suites. Historical E2E
oracles also accept the additive SP 2.7 contract, retaining their semantic checks.
