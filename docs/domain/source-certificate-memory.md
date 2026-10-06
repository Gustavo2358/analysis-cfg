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
