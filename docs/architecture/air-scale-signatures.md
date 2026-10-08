# Managed shared signature position and membership facts

AS-W02 / IN_PROGRESS. Pinned air-java78b8bf2e4204ace106ceb6d484cd89cb33a7af8c
adds SnapshotSignatureIndex and integerEqualsNatural. PagedSnapshotSignatureStorage
implements its required storage port over the borrowed run PageStore and ledger
in VALIDATION. This frontier does not complete the paged Validator or managed CLI.

One exact literal-word catalogue holds LIST/kind keys and table/canonical-INTEGER
memberships. Sparse managed metadata is addressed by stable table handles; bad-row
payloads use dense five-word rows (source row, position, ordinal, two mode links).
Open and closed counts/head/tail are separate. An open retained prefix goes directly
to an unordered row without scanning closed-only errors. Bad rows are immutable
required templates until owner closure. Selected cursor state is fixed primitives;
reselection and misses create no query-history rows. A table becomes queryable only
after successful finish; unfinished reuse, bad append order, rows outside declared
length and writes after finish abort the port. Address/count arithmetic is checked.

All growing catalogue/metadata/payload/directory state is managed and spillable.
Fixed staging/control is claimed before allocation. Construction denial and every
measured primitive WORK interruption close owned pages/leases while keeping the
borrowed run store usable. Operational failures poison the port. Cleanup preserves
primary/suppressed exceptions; the run owner remains responsible for PageStore.

Five direct laws cover memory/file parity,4096cold bad rows, separate mode sequences,
8192repeated member/miss/prefix queries without live-page/temp/heap growth,1024distinct
lists with both row kinds and8192members, shared signatures across1024entries, every
primitive interruption, six constructor quotas and unfinished/frozen/ordering faults.
The relation-only file peak is21048B under65536B, with1684512B temporary payload.
The combined typed-input/key/signature path peaks65784B under131072B, retaining
2712352B temporary payload;256shared positions produce262144errors and retain2.

The integration fixture is intentionally position-invalid, and entry ExternalBinding
has independent admission obligations. The test-only resident Publication/shallow
copy memo are outside managed decoder claims. Its copy preserves contextual typed
collection sharing; the original non-memoized helper duplicated256positions per
entry and exhausted a32MB resident test ledger. That raw failure, a later empty-list
context mismatch, and the final corrected fixture remain evidence. No quota was
raised and no assertion removed. Source raw handles are64-bit, never AIR identity.

35adapter laws and selected reactor neighbors passed86.662s. Compiled always-present
membership and merged-mode-prefix mutations fail independent assertions, followed by
untouched GREEN. Producer mandatory FAST236model/143transport/43policy passed35.554s.
Consumer mandatory FAST1053methods/zero failures/errors/skips passed379.321s.
Selected clean CLI dependency/support parity is pending for the committed checkpoint. Complete reference/type/domain/capability checks, diagnostic
binding, CheckedSnapshot, incremental JSON, managed CLI and global qualification
remain pending; this partial fact inspector issues no admission certificate.
