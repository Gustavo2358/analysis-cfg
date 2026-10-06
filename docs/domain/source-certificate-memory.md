# Certificate indexing and grounding

Qualification validates a finite AND/OR causal graph, not an enumeration of paths.
An immutable hash index can store only bucket heads and next ordinals into the
already-owned list. Hash collision resolution always compares full typed keys;
no identity is inferred from a hash. The map obeys ordinary get/contains/entry
semantics and rejects duplicate keys exactly as before. It costs O(N) primitive
slots with no per-entry object or cloned DTO. Expected lookup/build work is O(N);
buckets above eight keys use the same HashMap collision handling as the prior
implementation, retaining its tree-bin protection for comparable keys. No
validation lookup silently discards a collision. Identity renaming and deliberately colliding
keys are adversarial oracles.

Each derivation has at most one source and one caller premise. Grounding therefore
uses a byte count (0..2), primitive links and node ordinals. Multiple derivations
remain OR, distinct source/caller premises remain AND; a repeated same node is
one premise. Nodes are queued once, on first grounding. Ungrounded SCCs and
unknown references are still rejected; a rooted SCC is accepted. This is Kahn's
finite monotone certificate worklist, with O(N+D) scratch and O(N+D) work after
indexing. Observation-location indexes are built only for declared occurrences;
all nodes and derivations are still validated. A location with zero observed uses
has no validation query and needs no retained inverted set.

Independent tests cover rooted/unrooted cycles, AND requiring both premises,
duplicate identities, colliding strings, same-source/caller idempotence, and a
large legal certificate under a fixed small heap. Resource failure does not
publish any certificate. The source model and wire contract remain unchanged.

## Incremental certificate admission and output

The parser retains one inventory element tree at a time, accepts arbitrary legal
field order, and applies the same closed fields, duplicate/trailing rejection and
typed graph laws. Input/output ownership remains with the caller. A dependency
bundle hashes the very stream used for admission; no second-open race or full
source byte/tree copy is allowed. The output mapping visits each immutable record
once and retains no complete wire DTO graph. Logical contexts are unchanged.
Time is O(wire bytes plus the existing typed validation), live space is O(typed
facts + largest element + validation scratch). Frozen producer inventories and
all conditional evidence remain independent oracles. No new wire version.

## Lossless owned column inventories

A unit-local builder copies every logical tuple into primitive ordinal columns,
with dictionaries of exact equal immutable strings, supports and proof lists.
Node and derivation rows are correlated by their original ordinal, not by a
Cartesian product. All opaque IDs are retained verbatim; no prefix or source
name has semantic significance. Freezing severs all builder aliases and lookup
maps. The only immutable views exempt from List.copyOf are private final owner
classes created by this factory; arbitrary caller lists are still copied.

The expansion bijection is get(i) == the original record at i. Shared values use
full equality including context, support, source, caller, proof alternatives and
selection. No graph fact is removed; all closed-wire and grounding validation
runs on the same expansion. Decoder builds one row per admitted element, in any
field order, and retains no expanded history. Builder is one-shot. Overflow or
invalid input fails admission. get is O(1); build and dictionary admission are
expected O(N+D+tuple payload), owned storage O(unique values + column cells),
with an O(unique values) temporary dictionary. Validation remains O(N+D) work
and scratch. Unique facts remain intrinsically proportional to their size.

Independent tests compare arbitrary IDs, equal/different supports and caller
premises to manually authored records, reject ungrounded/foreign references as
before, mutate input lists after freeze, attempt view mutation and builder reuse,
and compare every legacy transport byte and result. Large profiles use identical
logical inventories and observations, not a smaller graph.
