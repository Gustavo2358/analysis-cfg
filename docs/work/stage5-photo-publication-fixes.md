# STAGE5-PHOTO-PUBLICATION-FIXES

Status: IN_PROGRESS

Draft #57 authority repins only; CFG, solver, dependency consumers and wire contracts unchanged.
Frontend #77 now preserves SUPPRESS outside COPY, distinct WHEN groups under
partial EVALUATE and canonical CICS nominal-binding gaps. No relaxed admission,
synthetic successor or memory/target proof is introduced downstream.

Frontend FAST716 and qualification-local pass. Twelve original photo/control
executions and 22 additional adversaries complete the actual SP → AIR → CFG →
dependencies path with semantic assertions. Two extra JSON sentinels remain an
explicit unsupported-parser limitation; full JSON support is not claimed.

Fresh 560-source replay: 542 identical SPs, 18 CardDemo SPs with only explained
structural/gap changes. The 18 changed SPs were lowered and consumed again with
zero candidate/support/provenance loss or additions; graph/executable semantics
are unchanged. For the other 542, unchanged consumer bytecode and identical SPs
justify reuse of 2,168 downstream products. No full downstream replay is claimed.

The three frontend implementation checkpoints are a83853c, ca2cc2d and b087a93;
the authority pin also includes their documentation-only qualification commit.
Consumer FAST is executed on this pin; final results and exact review SHAs are
recorded in the aggregate publication-fixes report. No merge.

Historical checkpoints 1–3: the JSON parser limitation recorded above is now
addressed by [checkpoint 4](stage5-json-generate.md). These older runs and their
observed results are preserved; new qualification is recorded separately.
