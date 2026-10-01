# Shared bodies and AIR latency — DONE / MERGED

Approved on 2026-10-01. [CFG #57](https://github.com/Gustavo2358/analysis-cfg/pull/57)
merged as `3bf5d5aa544e58eefe0bdd0327a888317e0936c2`; performance
[CFG #58](https://github.com/Gustavo2358/analysis-cfg/pull/58) merged as
`4adcb4157139319bcf4678538a3d8ebf093dd428`. Normal merges preserve the qualified
commits. Producer authorities are fixed to actual merged revisions in
[sources.lock.json](../sources/sources.lock.json); the workflow uses the same AIR SHA.

## Current behavior

Shared CFG bodies retain typed local invocation, boundary, resume and unwind
rules. Traversal and abstract states retain caller frames; observations join only
after per-context instruction replay. This representation does not reduce the
semantic number of contexts or introduce arbitrary depth cutoffs.

The latency change adds three complementary optimizations:

1. Resolve finite open-control label bounds once in `ProgramIndex`. Preserve CFG
   encounter order, set semantics, activation ownership and edge-read counts.
   Broad and union bounds keep their previous traversal behavior.
2. Carry validator-owned `CheckedPublication` through file, dependency, dataflow
   and regional paths. Reuse the full result only for the same publication and
   validation options; changed budgets revalidate. Capability negotiation and
   consumer admission always remain. Invalid/incomplete results cannot become
   successful products through this optimization.
3. Emit dependency inventories while mapping records, with buffered UTF-8 output
   in 64 KiB blocks. Preserve escaping, key/array order, newline and atomic file
   publication. No persistent result cache or compression is introduced.

The companion AIR codec binds directly from segmented UTF-8 offsets and converts
large blocks in parallel. It checks the complete physical input before binding
and retains exact reference-parser diagnostics for malformed input.

## Measurements

Java 21, Ryzen 5 5600GT, G1, `-Xmx2g`, populated filesystem cache. Medians of three
fresh JVMs per variant/case, alternating order without concurrent benchmarking.
The baseline pair was AIR `2eec91d6ea607c01b42e7d69b2938e0131370be0` and
CFG `ac24f49460594a5460c164b6a38320330291596e`. The measured implementation was
AIR `1c917c0d274afe51a9cfed6dfa13f3aedd94e109` and
CFG `b93cbaab1855329ecc6860a34569a871d9b04a4c`.

| Case | AIR MB | Decode, seconds | Complete dependency bundle, seconds | Reduction |
| --- | ---: | ---: | ---: | ---: |
| COTRTUPC | 14.8 | 0.653 → 0.508 | 1.608 → 1.290 | 19.8% |
| COACTUPC | 53.2 | 1.358 → 0.953 | 3.690 → 2.756 | 25.3% |
| COTRTLIC | 38.6 | 1.120 → 0.830 | 3.213 → 2.374 | 26.1% |
| CODATE01 | 149.9 | 3.026 → 1.940 | 6.695 → 3.692 | 44.9% |
| COACCT01 | 264.2 | 4.733 → 2.867 | 16.559 → 6.553 | 60.4% |

Complete time includes input, analysis, serialization, hashing and output writes.
COACCT01 process wall time was 16.796 → 6.771 s. The 60.4% result combines producer
and consumer improvements; it is not solely the cost of parallel decoding and is
not a universal speedup guarantee. The reproducible runner is
[compare_air_codecs.py](../../scripts/benchmarks/compare_air_codecs.py).

## Qualification and evidence reuse

Final implementation review heads: AIR `13518a709908eb3824ccd83d5c52d26cf83c1aac`
and CFG `230e74ce4192556353d6832b234c4e0fb0478780`.
[CFG Fast](https://github.com/Gustavo2358/analysis-cfg/actions/runs/36875023407)
and [AIR Fast](https://github.com/Gustavo2358/air-java/actions/runs/36874215719)
passed. CFG local Fast requires 672 methods with zero skips plus architecture and
Python checks. It includes checked-validation, indexed-order and UTF-8 writer
regressions; gate counterexamples reject stale signatures and unchecked/unbounded
reading. AIR requires 189 model checks and 136 transport checks, including 18,410
physical-parser comparisons. Another 23,001 baseline/candidate cases preserve
canonical bytes or exact structured diagnostics.

The performance replay compares **560 cases and 2,240 complete products**, all
byte-identical: canonical AIR, validation, CFG and dependencies. Correctness used
`-Xmx3g`; timings used `-Xmx2g`. Previous sharing qualification separately checked
candidate/support/provenance preservation and reduced CardDemo nodes
110,570 → 79,176 (28.4%). These are distinct experiments and baselines.

Integration changes only documentation and producer pins. Product and Java tests
remain equal to the qualified heads. Corpus/full/benchmark results are reused on
that basis; producer and consumer Fast checks cover the final dependency
resolution. Raw evidence stays in workspace `.shared-routine-bodies/evidence`
and `.air-codec-latency-v2/evidence`; integration SHAs, new Fast logs and final main
CIs are recorded in `artefatos-e2e/analyzer-integration-20261001/REPORT.md`.

## Remaining limits

PARTIAL, source qualifications, model assumptions and true remainders remain.
Exact recursive-context summaries and universal context-count reduction are not
implemented. CFG v5 needs rule-aware viewer support; that UI migration is outside
these analyzer PRs. The decoder retains input, offset tables and AIR objects;
there is no unlimited-memory claim or serialized/wire format change.
