# Text predicate filtering in values

The values analyses may send bottom along a published Branch transition only
when the requested Boolean outcome is absent from the abstract predicate value.
CFG construction and its edges remain unchanged. This is a transfer in the
existing forward fixpoint, not source reachability inferred by a consumer.

## Authority and assumptions

AIR `Branch`, Boolean operations, text equality, `FitText` and `SliceText` supply
the meaning (`IR_GUARANTEED`). Logical characters are not bytes; no encoding or
collating sequence is assumed. Unknown expressions admit both outcomes.
The usual sound abstraction rule applies: retain a superset of concrete states;
see [Cousot, Abstract Interpretation in a Nutshell, §§7–12](https://www.di.ens.fr/~cousot/AI/IntroAbsInt.html).

Scalar finite sets already retain an open remainder. Regional logical candidates
previously retained only positive support, so they cannot alone justify exclusion.
Add an independent closed-set flag for exact TEXT CellBinding objects. An exact
required whole-object assignment of a closed text expression can establish this
flag. Every normalized write touching that cell invalidates it unless its full
logical replacement is closed; MAY writes also require a closed prior value.
Join intersects closure flags and unions candidates. Entry PossibleLiterals and
missing values stay open. Physical projections and uncertain bindings stay open.
All scoped writes participate, including writes without an explicit operand.
Impacts use the storage index by StorageId, never a scan of objects per transfer.

## Algorithm and boundary

Evaluate EQ/NE over finite closed text sets and NOT/AND/OR over the two Boolean
possibilities. Fit and slice use Unicode scalar indices. Unsupported expressions,
partial text, open sets, extents outside the logical array representation, and
unproved operation preconditions yield both outcomes.
Equality uses set intersection; independent alternatives may overapproximate
correlations. No relational refinement of surviving values is claimed.
Demand preparation includes predicate reads and their copy sources.

The Boolean lattice is finite. Closure adds one bit per admitted cell and does
not add text values or paths to the underlying domain. Per predicate cost is its
expression size plus the text sets/characters inspected. Write invalidation costs
the prepared cell impacts; joins cost the existing maps plus closure intersection.
The existing finite-values domain and solver retain their termination boundary.

## Independent tests

Synthetic AIR checks both analyses against hand-enumerated outcomes: unequal
closed text excludes the true branch; equal text excludes false; unknown inputs,
MAY/scoped writes, partial entry alternatives and joins with an open predecessor
keep both; aliases share invalidation; copies and Unicode fit/slice preserve
meaning. Unknown collation combined with a proved false necessary condition is
false. Literal names and program identities do not participate in any rule.
