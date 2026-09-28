# Source possibility contract witnesses

Synthetic COBOL inputs; SP 2.52 / qualified source 1.1 / dependency 2.7.
Produced with the W1 development runtime, before W2 command support. This snapshot
intentionally retains opaque SQL and NEXT SENTENCE as executable boundaries.
Expected candidates are authored independently in tests, never copied from output.

SQL then CALL preserves AFTERIO only as a source possibility. A proven GOBACK
and an uncalled paragraph exclude DEAD. PERFORM resumes after a possible source
completion. A precise overwrite replaces FIRST in PGM while SNAP keeps FIRST.
OPEN/CLOSE use distinct FILE control points and preserve declared external names;
placing GOBACK before them excludes all four. NEXT SENTENCE remains unavailable
here and cannot be reinterpreted as lexical fallthrough to SKIPPED.

The original source provenance in the snapshots is retained for port correlation.
Regenerate by invoking the real frontend, CobolDependencyInput and
AnalysisDependencies entrypoints on the matching .cbl source, then review the
semantic oracle before changing any snapshot.
