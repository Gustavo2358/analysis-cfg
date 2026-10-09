# Incremental physical AIR input on the real application route

AS-W02 / IN_PROGRESS. DataflowAirReader reads JsonFiles.input incrementally through
the official checked InputStream codec. It counts/hashes decompressed bytes while
reading; no readAllBytes input array or resident physical token tape is required.
PagedJsonInputStorage uses three shared columns, an exact literal child-address
catalogue, sparse values and complete decoded-name ordering. UTF-16 is packed four
units per word. Read-only ordinal queries do not intern or retain query history.
The temporary PageStore and directory close on success/failure; cleanup exceptions
preserve the primary. Physical input quota failures remain operational failures,
never partial semantic success.

The new route is used by the ordinary reader/launcher, rather than an unused helper.
BindingReader and AirValidator still construct/check a resident Publication.
This bridge removes complete physical-input retention; it does not finish official
typed snapshot binding, complete snapshot admission or downstream managed ownership.
Conversion scratch is guarded; returned model memory is explicitly outside the
physical staging lease. Default staging limits and caller-supplied resources are
operational policy, not new AIR cardinality rules. No end-to-end bound is claimed.

Focused local overlay evidence: real reader and AnalysisDependencies.run pass for
the54849byte dynamic fixture. Publication, complete original Validator result and
decompressed SHA256 equal the preserved resident path. Staging peak58936B under
65536B, temporary1415936B. Four port laws pass:4096cold ordinal rows/8192reads use
49176B under65536B with631616B payload and zero query growth; exact Unicode names,
list-local duplicate membership, every primitive WORK interruption, denied
constructors and invalid/duplicate ordinals preserve0leases/pages after teardown.
The overlay uses explicit dirty source hashes. The immutable producer pin60f50d
resolves offline. Focused Maven admission/wire/launcher regressions pass in69.052s;
mandatory repository FAST passes1,071tests with zero failures/errors/skips in
525.078s (215reports), including compiled boundaries and wire contracts. Its
900s aggregate watchdog is separate from the480s per-analysis experiment deadline.
Complete dependency/support CLI comparisons qualify the clean committed delivery
before the next integration work. Global qualification remains pending.

[The producer staging contract and resident binding limit](https://github.com/Gustavo2358/air-java/pull/30)
remain coordinated with this draft. The integrated acceptance probe still fails
because no common run owns decode, complete snapshot admission, program, domains
and output. The admitted correlated-Scalar loss remains open. Global AS-W00-W10
qualification is pending.
