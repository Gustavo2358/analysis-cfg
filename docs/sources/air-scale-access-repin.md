# Typed AIR access checkpoint pin

WORK-AIR-SCALE is implementing the complete managed analysis architecture. The
current producer pin is `3bb2d55e7912ff34ee5185a2be51ab71a1a20b56`, on
[AIR PR 30](https://github.com/Gustavo2358/air-java/pull/30), pending human review.
No upstream merge or remote CI success is inferred. The lock and CI checkout use
the same immutable SHA; library coordinates, Java release and normative AIR pin
remain unchanged.

Compared with the formerly consumed `c2f80b59b7c38fa6efb9a20f0fa79644c697d8a4`,
all 51 existing production files have identical Git blobs. All three POMs are also
unchanged. Five model-owned types add complete typed access, explicit Publication
projection and incremental primitive storage construction. The existing model,
Validator and codec signatures/semantics are unchanged. The unmerged builder
prototype was corrected in place to require explicit write-port sealing.

Producer FAST passed 197 model and 143 transport checks plus module/boundary checks
in 25.008 seconds. The array-position mutation was rejected and the original bytes
restored. The freeze RED demonstrated that ownership transfer had not yet rejected
retained write-port use; the port is now sealed before transfer. Those are producer
checks, separate from the consumer's bridge/backend and full FAST results.

`PagedAirStorage` implements the official primitive storage port with four
`PagedLongArray` columns. Headers, references, collection trees, characters and
their directories all use the session's page store and ledger. Constructor and
appender control leases reserve capacity before allocation. Freeze rejects new
writes/claims while preserving reads. Close releases only the input-owned columns;
the runtime must close its shared store finally, including after operational failure.

The tests compare Unicode payload facts larger than the managed heap quota across
resident and forced-eviction file backends. They check model-derived owner/operation
facts, constructor faults and interrupted write cleanup. The test's Publication is
caller-owned and the copying helper is test-only: this does not prove bounded input
decoding. Resident page capacity stays funded until the resident store closes; the
file cache stays fixed while its disk payload exceeds the managed heap quota.

The managed CLI is not yet integrated. Complete official paged validation and JSON
binding, shared program/control, lawful domains and dependencies qualification remain
pending. Typed/frozen storage alone issues no AIR validity certificate.
