# Focal self-review

The registry fixture proves both persisted mismatches before validation and checks both consistent controls. The existing validator is unchanged.

The tool helper separates stdout and stderr at process capture. It retains stdout exactly, forwards successful stderr to diagnostics, and includes both streams and the exit code in failures. No warning-specific filters or expected inventory changes were introduced. A new real-process regression is part of fast/CI. A compiled inventory probe runs real javap/jdeps with arbitrary stderr and then a descriptor mutation; it verifies unchanged inventory hash and rejects changed product.

Production Java, tests, POMs, source authorities and approved inventories are outside this diff and are checked byte-exact. Historical STOPs remain investigation history, including the initial NOT REPRODUCED finding and later independent diagnostic-contamination RED. The cause of the oldest event remains unproven.

Main and the same frozen candidate are validated separately against their exact clean upstream builds. Final execution receipts and exact-head remote CI are required before merge. This is agent self-review under explicit user authorization, not a newly invented human approval.
