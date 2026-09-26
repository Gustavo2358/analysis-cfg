# W1 source dependency owner: focal A/B reconciliation

Status: **PREEXISTING_CONFIRMED**. This document concerns only the
`SOURCE_DEPENDENCY_OWNER_UNPROVED` witness from the frontend W1 local
qualification. It does not turn that qualification into PASS or address other
corpus cases.

## Witness and failure site

The W1 qualification log (`evidence/w1/gates/pmt-frontend-qualification.log`,
SHA-256 `d1ac565ba1d8bf0dbe36838fcda568de12f929d5d6b5e55b2e850ec25639abb8`)
records `python3 -B scripts/harness/lean.py qualification-local` invoking
`bash scripts/source-normalizer-regression.sh full`. The first normalizer case
uses the existing frontend `corpus/cbl/COACTUPC.cbl` fixture and `corpus/cpy`.
Its Maven `compile exec:java` command reaches `SEMANTIC_PRODUCT`, then
`SourceDependencySemantics.associate` throws at line 26, called by
`CobolSemanticProductProjector.FrontendProducts.withSourceDependencies` and
`ExplorerMain.analyze`. The original output directory was
`/tmp/cobol-source-normalizer-full.6x1OiE/coactupc`; preprocessing, AST,
symbol and observed-dependency artifacts exist, but no Semantic Product was
published. No later normalizer case ran in that qualification.

The first source dependency fact is a resolved COPY at original source line
166:11–26, with exact original provenance and a root site in the same source.
The expected owner is the sole program unit, `COACTUPC`. The unit's AST
original provenance spans only line 21:7–31 and has `exact=false` in both
revisions. The checker's source-file/start-position lookup finds the unit, but
its end-position containment check rejects line 166. The local read-only
fact probe found 56/56 facts outside that published original unit span in
both revisions. The exception reports the first; it does not identify the
occurrence by name.

## Controlled reproduction

Two new, clean detached frontend worktrees were created at exactly:

| Side | Commit | Result |
|---|---|---|
| A, PR #56 baseline | `edb64520a6269be9fa6d71cd47e6974112fbfece` | exit 1, same owner exception |
| B, PR #58 W1 | `cfcf0abf06a3b6186e157957bb301077fcb6f0bc` | exit 1, same owner exception |

From each worktree root, the same focal command was executed twice; the second
run was captured in local ignored evidence:

```sh
JAVA_HOME=/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem PATH=/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem/bin:$PATH mvn -o -q compile exec:java '-Dexec.args=--source corpus/cbl/COACTUPC.cbl --copybooks corpus/cpy --output target/w1-owner-witness'
```

The `-o` switch uses the same existing local Maven cache for both runs; it
does not change the fixture or CLI settings. Both fresh worktrees had no
pre-existing `target/`. Environment: Temurin JDK 21.0.12.1+1, Maven 3.9.16,
Linux amd64, `LANG=en_US.UTF-8`, `LC_ALL=C.UTF-8`, `LC_CTYPE=C.UTF-8`;
`MAVEN_BIN`, `MAVEN_OPTS`,
`FRONTEND_MAVEN_REPO`, and CI variables were unset. The same Maven user
repository was used. The normalizer shell script and its `full_local` call
site are unchanged A→B. W1 only added one class to the FAST list in
`lean_project.py`; it did not add this witness to the full gate.

The frontend input blob is `d66944150c9ad91fe0459ef304cc536b651b9a42`
(SHA-256 `b5bb7d6ccad022e0fc91b4dd1e971f49d184adf89b56abdce14eccff35b39396`)
on both sides; the `corpus/cpy` tree is
`c5d07dff7499ae4c0d63c58af48f6153ab000c68` on both sides. The checker,
preprocessor, AST builder, source normalizer, copybook library, CLI, and
normalizer script have identical Git blobs A→B. There is no setup or input
delta in this comparison.

## Output equality and classification

| Artifact in `target/w1-owner-witness/` | A SHA-256 | B SHA-256 |
|---|---|---|
| `preprocessed.cbl` | `d88cc149766b254ee3c244e8304f1c9c798f55551f19246134b28338ae2d2a2a` | same |
| `ast-data.js` | `4af5e8cfd6124d8e67d11a85561584a1bed58394e5b5a7e7a14a1436ec66d35c` | same |
| `observed-dependencies.json` | `114ef272acfef38e4fb93eb9443727d047c9237b10b260db48e27cfb811fecae` | same |
| `tree-data.js` | `1c32627ffddb59cdb893cc57d502dfb19c2124fa1a16958c7b72a19834baa384` | same |
| `symbol-data.js` | `51d35a03618a6560cf787b006e2e68e303779456887ee952fdae9295e64d3e32` | same |

The recorded A and B stderr/stdout logs have SHA-256
`418d5dc78281616685d916c46535d4199554a35e3e05e72f5b5ad1b0f6774094`
and `3f65ea8eeddc9f1abd3bb758d7411aefdff49761e8094b4edae1b2fea59958a1`
respectively; timestamps, run IDs and output paths differ. Removing only the
timestamped warning line from the read-only fact-probe logs yields identical
SHA-256 `d1acb47c5ce85c4333367acf184fad4ab0266f5aa9c0d1e1346bb26c032b9959`.
The local logs and generated artifacts are preserved under ignored
`.positive-memory-topology/evidence/w1/owner-reconciliation/` and the two
detached worktrees; no raw corpus output is versioned here.

**PREEXISTING_CONFIRMED:** A and B fail at the same source fact, owner lookup,
containment rule, exception site and stage. There is no first A/B causal
divergence. This is a pre-existing product and gate failure, not a failure
newly exposed by the W1 harness. No fix, validator change, fixture relaxation,
contract change or repin was made. The frontend `qualification-local` result
remains **FAIL**; the earlier FAST and synthetic W1 gates remain as reported.
Only this owner uncertainty is reconciled.
