# Storage W6–W8 consumer profile

W6 consumes existing AIR constant RegionSlice reads and FitText(Read) assignments. Under the explicit IBM1047 one-byte mapping, fitting captures the pre-write source image, truncates its right suffix or pads on the right with the encoded explicit scalar. Other expressions/codecs retain unknown. The existing fragment/capture/correlation domain is reused; later source writes cannot alter captured bytes. Cost follows touched fragments and known payload; no receiver or candidate cap.

CALL queries its constant slice before foreign effects using StorageSubject.PhysicalRange and the shared regional provider runtime. A unit with slice and whole-object CALL reads has one storage values run per selected entry. Object-only callers retain the established ObjectId provider API. A slice has no fabricated ObjectId: dependency wire subject is null, valuePoint and operation identity retain the exact AIR read site. Literal CALL remains independent of storage availability.

Authority, premises and oracles: docs/work/WORK-STORAGE-W6-W8.json. G1: RegionalFitTest (padding/truncation/equal extent, captured source, unknown tail, branch correlation), RegionalDependencyTest (slice and object shared run), prior transfer/composition/planning/dependency contracts. G2 W6/W7 and composed final G3 W8 passed; see docs/engineering/storage-w8-qualification.md.

W7.1 consumes existing simultaneous EntryState. AIR I-17 proves bounded literal
consistency; RD retains each initial definition per intersecting segment and
refuses unresolved overlapping forms. Values keeps one image with co-initial
provenance per span, unioned at equal overlaps. It does not enumerate alternative
orders of initialization. Writes kill all prior supports only on written bytes;
copies preserve each surviving contribution. Detached alternatives preserve nonoverlapping complete fragment covers; their
union retains every co-initial interval/provenance association.
Unprovided bytes remain open. Entry boundary seeds run once; local invocation
and initial-label backedges do not reseed.

Algorithm: existing partition/index plus interval intersection of initialized
spans, no per-byte layout materialization or all-object pairs. Work follows
touched spans and retained support associations. Oracles include partial equal
overlap/permutation, unknown gaps, overwrite/loop, copy and source contributions.

W7.2 retains the established StatementEffects semantics. MAY preserves the old
possibility; MUST kills only its exact outcome-specific range. A named union
uses physical alias intersections and positive independent StorageId bases.
AllMemory includes private allocations; PRIVATE alone is not separation from
external effects. A normal-outcome override replaces otherwise effects on that
outcome; absent normal outcomes never create a normal continuation. CALL target
reads are before effects. ExternalStorageScopeTest supplies six independent
oracles for union/alias, private/all, outcome MUST, disjoint base and no known normal outcome. Explicit Diverge retains UNSUPPORTED_INPUT (existing control boundary); an open LabelsControl outcome creates only its allowed unknown destinations, never an invented normal edge.

W7.3: o mesmo snapshot comporta Cell, Region conhecida, Region sem extensão e
codec desconhecido. O havoc no componente opaco propaga interferência possível
somente para relações de alias positivamente publicadas; bases distintas são independentes (W1 PMT).
O oracle AIR independente testa os dois casos, incluindo PRIVATE sem prova.
CALL literal preserva candidatos e dispensa a execução de values mesmo nesse
inventário misto. Declarações não suportadas continuam explícitas no produtor.

W8 demonstrated fix: `ByteImage.fit` previously allocated padding by extent and
failed at 2^100 bytes even for a four-byte tail observation. Internal payloads now
represent repeated padding as one span. Slice/copy/kill retain exact producer and
capture offsets; solver/replay tests observe the tail without extent-sized work.
Materializing an actual dense byte/text result remains proportional to the output
size and subject to JVM representation/memory limits; this is not a candidate cap.
W8 metamorphic tests compare candidates, remainder flags, byte contributions and
supports across display renaming/alias observations, shuffled inventories and
simultaneous initial conditions, and insertion of proved-disjoint overlays.
