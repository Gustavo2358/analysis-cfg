# STAGE5-PREEXISTING-FIXES

Status: IN_PROGRESS

Scope: ship two reproduced pre-existing producer failures in the current stage-5
campaign. No new CFG/dataflow production changes. The frontend preserves complete
literal/word operand boundaries across fixed-format continuation. The lower gives
anonymous logical GROUP roots a structural internal TEXT cell, with no nominal
COBOL symbol or physical proof.

22 new four-stage cases pass with the real frontend, dependency-input lower, CFG
and dependencies entrypoints. They cover both quotes, LF/CRLF/CR, split words,
complete identifier to literal, named/anonymous controls, two roots, nested alias,
unknown filler/sibling, initialization, proven overwrite, multiple receptors,
PERFORM, logical-disabled and explicitly enabled physical propagation.

Reproduce with `python3 scripts/project/e2e_normalization_logical_roots.py
--runtime <frozen-runtime.json> --work <new-output-directory>`.
Supports, site provenance, artifact hashes and CFG wire contract are checked.
Unknown fragments retain reads of the root; unknown targets retain remainder.

560-case replay versus the qualified stage-5 products and repository gates are
in progress. Existing expected files are unchanged. No merge.
