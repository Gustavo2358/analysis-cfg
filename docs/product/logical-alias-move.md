# Logical alias MOVE regression

A typed whole-item MOVE must preserve a published logical storage family. The
lower used its scalar FULL_IDENTITY/POSSIBLE_TEXT shortcuts before family update,
so a write to a projection could leave overlapping views stale. The correction
uses the existing root FitText/SliceText/Assign translation before those shortcuts.
Logical admission also discharges stale scalar-fitting capability diagnostics;
invalid SP facts still reject before admission. No CFG, dataflow, dependency or
AIR model change is needed.

The frontend and wire contracts are unchanged. The source rule and implementation
premises are recorded in cobol-lower/docs/domain/logical-text-w2.md. IBM Enterprise
COBOL 6.4 [REDEFINES](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf), printed
pp225–227, keeps both descriptions of shared storage in effect.

## Permanent source oracles

Fourteen fixtures in `analysis-adapters/src/test/resources/cp6/logical-alias-move`
check forward/reverse alias writes, overwrite killing the old name, nested
PERFORM and unrelated performed writes, copy capture, partial overlay writes,
branch correlation, dead overwrite, independent families, a REDEFINES chain,
literal fitting, RENAMES and DATA copy into an alias. Expected values are source
oracles; both candidates and materialized dependency edges are checked, with
source provenance on every emitted support. The frozen pre-fix runtime passes
1/14; the corrected runtime passes 14/14. The pre-fix overwrite and partial-overlay
cases also retain stale forbidden values, so this is a precision regression test
as well as recall.

Use the existing source-stage runner (Java 21 and a pinned dependency bundle runtime):

```sh
python3 scripts/project/e2e_perform_completion.py \
  --runtime /absolute/runtime.json --work /new/output \
  --fixtures analysis-adapters/src/test/resources/cp6/logical-alias-move
```

The lower FAST gate contains three real-producer SP regressions and validates AIR
and codec roundtrip. Existing malformed logical-coordinate cases remain unchanged.
The original PERFORM corpus remains 39/39. In realistic chaos, cases 30 and 39
now resolve PROGA001 with reachable CALLs; the unchanged oracle reports 47/48.
Case 44 is the already-reviewed requirement for a dead CALL to exist in executable
`sites`; its source inventory remains present and no dead target is emitted.
No fixture expectation is changed to hide that remaining oracle discrepancy.

## Limits

The existing logical-family admission rules continue to apply: published fixed
text coordinates, whole-item operands, a literal or DATA from another family.
No general overlapping DATA-copy semantics, numeric/physical layout, new codec,
ALTER, or source reparsing is introduced. Physical analysis remains disabled.

## Historical W2 checks

The 24 existing W2 sources were also executed. Their product assertions pass
23/24; known-open still expects PROGA after ACCEPT writes the CALL target and
returns no candidates in both the pre-fix and corrected runtime. Its expected
is retained. The legacy runner's additional all-storage-is-Cell assertion is
incompatible with today's published Region inventory: group has the exact same
storage before/after this fix and zero physical groups/writes. Raw results remain
separate from product-oracle evaluation; these are not reported as 24/24 green.

## Regression and gate results

Both repositories' required FAST gates pass with Java 21. The existing PERFORM
adversarial suite remains 25/25. Across the four synthetic runs, 416 emitted support
references resolve to written source provenance. Only chaos 30 and 39 add candidate
and edge targets; the other 46 candidate sets are unchanged.

The complete CardDemo corpus (73 programs, upstream
`59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`) completes with its existing PARTIAL
statuses. Compared with the prior PERFORM runtime: all 121 program, 271 file and
523 source relations, support objects and 20,548 referenced origin nodes are
unchanged. No known false positives recur. Sixteen products have explained
metadata changes: 36 added `logical-text@2/capture-fit-update-root` origins and
updated AIR evidence hashes; four programs also index 2 or 10 more operations
from the root/view update. No existing origin is removed or modified.

The frozen E2E binaries were executed before committing the fix. Their 486 lower
core and 707 adapter class entries are byte-identical to the final FAST rebuild.
Raw outputs, runtime hashes, baseline comparisons, the legacy W2 results and the
metadata audit remain in the local workspace under `.alias-move/`.
