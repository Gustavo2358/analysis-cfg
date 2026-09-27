# Historical qualification failures — repair

Status: IN_PROGRESS. Authorized after the D1–D5 review handoff.

Classify each existing failure against current product contracts before changing production or expectations. Initial evidence: six failures and one error in four frontend characterization classes; W2D hardcodes SP2.38 while the producer lock is SP2.50. Baseline reproductions are preserved.

Scope: repair application defects if proved; otherwise replace obsolete expectations with explicit current capability and negative assertions. Retain independent inventory, provenance, no synthetic kill, exact source pins and feature version floors. Do not regenerate oracles from outputs or accept arbitrary versions. No merge.

Validation: targeted RED/GREEN; complete frontend and CFG qualification to exercise stages previously blocked; FAST in changed repositories. Reuse corpus evidence only if production inputs/code remain identical. If any production change is needed, rerun the affected corpus and investigate deltas.

Checkpoint H1: obsolete characterization expectations replaced with explicit current SQLCA, copybook, DLI and PERFORM assertions; missing SQL INCLUDE stays opaque. W2D reads its version ceiling from the exact producer lock, keeps feature floors, and has five positive/negative guard tests. DLI retained provenance is corrected through framing and replacements; whole-command provenance and confidence remain unchanged.
