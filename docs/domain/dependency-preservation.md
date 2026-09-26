# Dependency preservation under incomplete physical evidence

Status: implemented for review; stacked dependency-preservation campaign, no merge.
The [harness principle](../engineering/lean-harness.md#dependency-preservation-principle)
is normative. Evidence hierarchy: exact physical/dataflow proof; semantic nominal/value
support; source-supported possibilities; no usable evidence. Lower levels keep supported
candidates with remainder. They cannot invent storage, aliases, control edges, or values.

The change reuses AIR 2.0.0 ObjectPlace/Read, logical Assign, UnknownBinding and
`target.possibilities@1`; no AIR schema or solver change. Possibilities are attached to
specific definitions and queried BEFORE the dependency site through existing CFG/RD
and values services. A failed physical name-area check opens interpretation remainder.
Runtime paths and missing source keep their existing uncertainty.

## Contract and limits

SP 2.32.0 adds `POSSIBLE_TEXT` for a single literal MOVE to a uniquely resolved whole
elementary DISPLAY PIC X receiver. Exact local syntax establishes receiver text length
and right padding, independently of allocation proof. It excludes truncation, tables,
subscripts, ref-mod, groups, unsupported clauses and representation conversions.
It publishes `logicalWholeItem` and `textAdjustment`, never a fabricated scalar or
physical view. Typed CICS PROGRAM/FILE hosts may carry canonical whole-item evidence
although flattened EXEC provenance is inexact. Physical provenance remains inexact.
Older SP versions cannot silently acquire the new copy semantics.

Strong regional transfers retain precedence. The lower validates the local contract,
uses the existing nominal object and UnknownBinding where storage is unproved, and
preserves value definitions. No independent Cell/disjointness premise is invented.
CICS PROGRAM and FILE consumers query readable nominal targets even without physical
8-byte IBM1047 proof. Name spelling/length validation still applies; raw value evidence
and interpretation remainder remain independent. SYSID keeps its separate context policy.

Nonliteral copies across unproved representations, generalized missing-source control,
interprocedural propagation and SQL/IMS typed calls remain outside this bounded change.
No claim of globally complete dependencies is made.

## Semantic authority and algorithm

[IBM elementary MOVE](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=items-assigning-values-elementary-data-move)
provides left alignment/right space padding for the admitted alphanumeric case.
[IBM CICS XCTL](https://www.ibm.com/docs/en/cics-ts/5.5.0?topic=summary-xctl)
requires the computed program data area to be 8 bytes; lack of that physical proof
therefore prevents closure, but does not delete supported logical name possibilities.
AIR pinned specification 14 governs target possibilities and unknown domains.

Producer declaration and resolution indexes are built once; local transfer recognition
is linear in AST/symbols/bindings plus emitted text. Lower type evidence is indexed in
one pass over statements and entry conditions. Existing solver termination/complexity
is unchanged; there is no new dataflow, path enumeration, or global MOVE collection.

## Regression

`DependencyPreservationTest` is listed in the FAST inventory: adding runtime uncertainty
preserves A; weakening physical metadata preserves A with remainder; strong physical
proof permits closure; a must-overwrite removes BAD; an unreachable definition cannot
contribute; truly unknown stays empty/open; FILE known/runtime remains enumerable.
`CicsInvokeRouteTest` and `TargetTimingTest` preserve the BEFORE-site query and supports.
No shared CFG, RD, storage lattice or solver implementation changed.

## Unknown layout and REDEFINES

For B REDEFINES A, MOVE PROGA to B followed by MOVE PROGB to A does not give the
nominal fallback physical alias/overwrite authority. With unavailable layout, B's
PROGA remains a possibility with remainder; PROGB is not copied to B merely because
of source names or a REDEFINES clause. UnknownBinding retains the unenumerated
alias effect. Strong physical proof may refine this result, but is not invented.
The frontend and lower-produced AIR consumer fixture freeze this boundary.
