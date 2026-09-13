# Reproducing the full CardDemo baseline

Local/on-demand only. The [pins](carddemo-full-pins.json) freeze the source and
four-product snapshots. The runner never follows upstream HEAD, edits COBOL,
stubs copybooks or implements a missing capability. Use a new directory for each
run; report generation refuses to overwrite an existing historical result.

```sh
git clone --no-checkout https://github.com/aws-samples/aws-mainframe-modernization-carddemo.git /tmp/carddemo-upstream
git -C /tmp/carddemo-upstream checkout --detach 59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e
python3 -B scripts/project/carddemo_setup.py --work /tmp/carddemo-build
python3 -B scripts/project/carddemo_baseline.py \
  --upstream /tmp/carddemo-upstream --runtime /tmp/carddemo-build/runtime.json \
  --work /tmp/carddemo-measured --stage-timeout-seconds 120
python3 -B scripts/project/carddemo_report.py \
  --measurements /tmp/carddemo-measured/measurements.json \
  --output-prefix /tmp/carddemo-after-change
python3 -B scripts/project/test_carddemo_baseline.py
```

Requirements: Python 3.10+, Java/Javac 21+, Maven, Git and the existing Maven
dependencies. `carddemo_setup.py --maven-repo <cache>` optionally reuses downloaded
third-party dependencies. Every product is rebuilt at its exact pin in an isolated
checkout; runtime classpath prioritizes those builds. No product qualification is
implied by building. Full product suites are not part of this evaluation.

Each stage is a fresh process. Default `--jvm-arg=-Xmx2g` bounds the heap; repeat
`--jvm-arg` to explicitly configure a later experiment. The 120-second stage
timeout is an operational safeguard, not a productive limit or SLA. Timeout kills
the process group, records TIMEOUT and elapsed time, then continues to the next
source. Expected refusals are BLOCKED; unexpected process/contract failures are
FAILED. No usable upstream result makes downstream stages NOT_REACHED. Produced
results carrying gaps are PARTIAL, never silently upgraded to complete semantics.

Discovery includes every `.cbl`, `.cob` and `.cl2` path, case insensitive, both
directly in the clean checkout and inside its ZIPs. `.cl2` is concretely justified
by the UniKix standalone COBOL sources. Archive members are extracted byte-for-byte
outside the checkout with path-traversal checks. AppleDouble entries are excluded
only with a matching binary signature; exclusions are explicit. Equal source
bytes at different paths are all retained. Nested ZIPs/duplicate member paths
fail setup visibly instead of silently hiding a population.

Dependency discovery finds `.cpy`/`.dcl` parents and extensionless files inside
upstream archive `cpy`/`cpy-bms`/`copybook`/`copybooks`/`dcl` directories. All real
roots are available; longest shared path prefix then lexical order determines
root precedence. Colliding names and their content equality are recorded. The
product's existing COPY resolver consumes those roots. No source-specific override
or dependency substitution is introduced.

Per-program `measurement.json`, stdout/stderr and raw products stay in the local
run directory. Product SHA256s guard report generation. Normalization and
preprocessing have no separate CLI process, so only their combined frontend time
is claimed. CFG JSON generation and dependency analysis are separate processes;
dependency analysis consumes AIR and builds its own analysis context. Every time
is measured by the runner's monotonic clock, including failures. Canonical corpus
time covers one sequential loop; build/discovery/report time is separate.

The artifact validator in `carddemo_metrics.validate_baseline` checks schema
version, full enumeration, stage states/categories/timings, reference resolution,
exact aggregation and HUMAN/ADVISORY_ONLY policy. The FAST suite exercises these
contracts without upstream checkout, network, or a full corpus execution. It also
validates the committed historical result. Provenance and impact dictionaries
are lossless per-program references to reduce duplicate data.

Source/site identity is preserved in the result: upstream-relative path (including
archive member), source digest, SP statement and ProgramPoint, plus the complete
AIR OperationId/EntryId. CALL candidates retain raw values, supports, origins and
all remainder fields. Globally deduplicated known dependencies retain contributing
sites and source variants; they do not certify runtime linkage.

## Historical run note

The first filesystem-only run discovered 44 loose sources. A subsequent archive
audit found additional `.cbl` and `.cl2` sources. Its logs/results are retained in
`/tmp/carddemo-full-20260913/measured`; it is **preliminary and excluded** from the
published timing/coverage metrics. The canonical run is
`/tmp/carddemo-full-20260913/canonical`, with one measured attempt per member of
the expanded universe. This rerun corrected discovery completeness; it was not an
A/B determinism experiment. No measured run was optimized or source-adapted.

Future evaluations must use separate filenames and explicitly chosen pipeline
pins. Compare program/site coverage and timing deltas; neither performance deltas
nor corpus frequency is an automatic implementation gate. Engineering effort,
risk, strategy and preferences remain separate human judgments.

Cross-version CALL comparisons should use source digest, original span/include
chain and source path, retaining SP/OperationId identity within each run. A changed
frontend may change local statement IDs; ambiguous matches require explicit
reconciliation rather than an inferred improvement. Missing-dependency counts
are actual frontend COPY diagnostics. Opaque SQL include semantics and hidden
input needs in programs blocked before SP are not reconstructed by a new resolver.
