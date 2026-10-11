# Managed source storage boundary

AS-W07/09 IN_PROGRESS; source1.0--1.6 unchanged, no AIR/CFG model invented.

## Bounded hot metadata (2026-10-11; local qualification, global PARTIAL)

Three fixed private controls avoid re-reading stable primitive metadata. They
are not canonical identity tables, admission certificates or semantic cutoffs:

- Physical JSON storage retains 512 exact words, 256 each for NODES and packed
  UTF16 CHARACTERS. Full 64-bit indices and column tags identify entries. Setters
  persist successfully before updating a memo; FRAMES remains paged. Full UTF16
  name equality is unchanged. Control10240B replaces512B, a funded fixed9728B delta.
- SOURCE mapping retains the first512 frame words, with the original paged tail
  and complete address range. Control6656B replaces2048B, a fixed4608B delta;
  every resident get/set charges INDEX work. Pop clears only controls and the
  fields that the closed shape could write; other suffix cells remain zero.
- Immutable recipes retain64 full-row fingerprint keys and16 rows of32 selected
  full-word cells with validity masks. Control7168B replaces768B, a fixed6400B
  delta. Cold misses read the original paged recipes; every cell hit charges
  INDEX work. A matching fingerprint still compares tag and all30 fields. Fresh
  recipe addresses never repeat, so unwritten zero fields need no zero setter.

The additional fixed20736B is reserved before allocating these arrays, within
the original heap/quotas and4096x4096 shared physical page cache. Keys, values and
presence bits are exact; collisions and eviction cannot discard canonical rows.
Closed/failed owners reject memo access, and close detaches the arrays. Fixed
memo sizes do not bound program cardinality, nesting, candidates or semantics.

Three formal mechanism laws fail for their specific storage operations on the
previous productive runtime and pass on the qualified copies. The unchanged33
physical/SOURCE neighbors plus those three laws pass on actual productive target
classes,36 tests/zero skips. The ignored-copy original128000 CLI completed in
396.674s with the independent whole-product oracle;32000 retained the old output
bytes exactly. Actual productive128000 replay completed in419.063s with the whole
independent oracle. Productive8192 correlation and both818022612-byte SOURCE-file
CLIs also passed their complete oracles; these are finite capacity witnesses.

The first original complete FAST1367 failed the borrowed-owner oracle when a
qualified snapshot correctly expired its own pages during an injected read fault.
Raw failure reports and the preceding oracle are preserved. The corrected oracle
measures each independently constructed owner's actual pages, checks surviving
owners and keeps the primary failure and borrowed backend intact. A separate
five-owner page-handle law exposed a real missing abort on the second paged read
in SourceTargetPlan.requestsAnyQuery. That accessor now uses the existing abort
path; successful instructions and every other method remain bytecode-equivalent.
All22 neighboring ownership methods pass on actual classes with the five-owner law.

Five fresh productive CLI products pass their full oracles after that failure-only
guard; the prior128000/8192 large successes are explicitly reused through exact
successful-instruction and whole-other-runtime equivalence, not relabeled as new.
The fresh original FAST passes all1368 mandatory methods, zero failures/errors/
skips, in1423.102s. Exact audit compares1436 production classfiles across all nine
consumer modules and both AIR modules,568 Java sources and579 fixtures, with no
helper shadow. Producer a0aeb827ef53765815aa501c4687f197be77ac73 stays clean and
unchanged. Audit01's summary accidentally overwrote its method count with a CLI
count; that immutable record is retained and audit02 independently confirms1368.

The subsequent original eighteen SOURCE state/forward/reverse cases complete
with their unchanged independent whole-product oracles at consumer8fbe3dd. They
are finite isolated single observations, not three-repeat clock medians or a
general order/permutation proof. Global complexity, all-store funding,21-family
coverage and completion of AS-W00--AS-W10 remain pending.

## Native writer header and fixed root funding (2026-10-11; local qualification)

Native v3 site coverage reads the exact admitted Invoke scalar header through
SnapshotProgram.invokeCoverage(OperationId). The existing three-tape paged
operation catalogue stores the operation handle instead of only its ID handle;
identity views still read the complete typed ID and use the original canonical
full-key lookup. No fourth tape, resident coverage map, body projection or
cardinality cutoff is introduced. Missing/non-Invoke identities return empty;
all four admitted coverage statuses remain distinct. A failed read preserves
the primary exception, retires all three catalogue tapes and leaves borrowed
input/backend/program owners usable. Only the explicitly non-native Structural
compatibility route retains the historical resident coverage map.

A complete checked AIR witness with a65536-character contract shows the old
writer attempted a cold body read merely to emit header coverage. The guard
protects16384 packed words but fails on the first attempted read; it does not
measure16384 reads. The identical formal law is RED on the old writer and GREEN
on current productive classes. Colliding full identities, four statuses,
non-Invoke/missing IDs, expiry and primary read-fault ownership are covered.

Plain native Structural output now reserves65536+4096 RESIDENT bytes in ENCODE
before JsonOutput allocation: the existing fixed UTF8 buffer and root scratch.
Insufficient capacity emits no bytes, releases reservations and does not expire
borrowed input; output failure preserves its primary IOException and returns
the observed lease. Managed SOURCE output already owns one root reservation
and bypasses this overload, so it is not double-charged. Caller-managed resident
compatibility without an execution budget remains explicit. This fixed funding
does not qualify variable site/result maps, sorting or all-store coverage.

Five new laws plus ten unchanged neighbors pass on actual classes with zero
skips; eighteen original real CLI contracts also pass. Exact active inventory
review preserves all1368 old methods and adds five, requiring FAST1373. Seven
original compiled guards pass: only one additive scalar public method and the
existing Evidence/budget port references changed. Five fresh actual CLI products
pass their complete original oracles: SOURCE forward32000, manifest1/4, and
SOURCE-file16 through dependencies and pipeline. Each preserves the corresponding
previous whole-product bytes. Original FAST1373 passes in1439.137s with zero
failures/errors/skips. Exact post-FAST audit matches all1436 production classfiles
across nine consumer and two AIR modules,570 Java sources and579 fixtures to the
frozen runtime used by those products. The producer pin remains unchanged.
Prior large products remain historical, not fresh writer qualification or blanket
successful-bytecode equivalence. General native result/staging/sorting maps,
all-store coverage, architecture human review and global qualification remain
pending; this local wave does not complete AS-W00--AS-W10.

## General native CALL fact staging (local qualified; global PARTIAL)

PlanningExecution.executeStored explicitly accepts a typed FactStorage factory;
arbitrary Java facts have no inferred codec or automatic spill contract. The
ordinary execute API remains resident and detached. Successful stored consumer
views are immutable and expire with their execution. Commit does not read the
whole store. Consumer failure discards its transaction; a later failed epoch
does not close an earlier committed epoch. Cleanup preserves the original
callback/storage failure, including suppressed cleanup exceptions.

PagedDependencyFactStorage encodes all29 fields of DependencySiteFact, every
sealed AIR ID and both OperandOwner variants, full UTF16, duplicate list ordinals,
supports/premises, optional program points/outcomes and all completion/remainder
shapes. It is a closed disposable typed codec, not AIR parsing, a new wire or an
admission certificate. Two PagedLongArray tapes own payload and ordinal offsets;
no cardinality-sized resident payload/directory and no hash-only identity.
Fixed controls cost7168B plus the separately funded shared backend cache.
Sequential consumer ranges share those tapes, with256B funded per transaction
view before allocation. Closing a failed active tail clears its physical cells;
closing an earlier range cannot change a later range. No tapes-per-entry product.

Detached projection of one complete fact preflights1024+64B per encoded cell in
SCRATCH and releases that projection envelope on return; a caller retaining the
returned DTO is explicitly resident. The full-shape test's former100KB success
budget could not fund its175808B projection. Raw failure is preserved; a closed
fixture inventory of <=10000cells derives650000B for one projection plus fixed
controls, without changing production quotas or weakening full-pressure denial.
Five finite geometries preserve all fields with fixed resident store controls.
Work denial initially left owned pages until close: the identical law now passes
after open aborts immediately. Native program expiry, read faults and projection
denial retire owned pages while borrowed AIR/backend remain usable.

Actual dependencies/pipeline CLI preparation, plain AIR and admitted SOURCE,
uses the same general planner/providers with this CALL staging. It does not
switch to the limited direct-dependency engine.20 focused current laws/neighbors
and18 unchanged original CLI contracts pass. All1373 prior mandatory method names
are preserved and13 new methods are added. Seven architecture guards pass after
explicit registration of the two new kernel classes and exact mechanical refresh
of only the active W4/core inventories; outer and historical inventories stay
unchanged. Purity/denials, Java21, Maven and solver rules are not weakened.
Five fresh actual CLI products pass the original complete oracles and preserve
previous whole bytes: SOURCE32000 (70.029858s,22824055B), manifests1/4 and
SOURCEFILE16 through both dependencies and pipeline. Original production defaults
and watchdog480 are unchanged. These are single finite observations, not medians
or fresh qualification of the previous large SOURCE/manifest/SOURCEFILE dimensions.
Original FAST1386 passes in1470.273s with zero failures/errors/skips. Exact audit
matches all1441 production classfiles across nine consumer and two AIR modules,
575 Java sources,579 fixtures and15 runtime components to the frozen runtime
used by those products. Producer a0 remains clean/unchanged; its old FAST is
reused. Human architecture review and global qualification remain pending.
Final retained site/reachability/enrichment maps, detached site/edge result DTOs,
FILE staging, observation maps and writer sorting are still migration debt;
typed staging alone does not close the architectural resident-result RED or all-store.

SourceShape lists the41 existing record shapes and4 primitive/container shapes
from QualifiedSourceDependencies,NominalValueEvidence and NominalValues. Typed
storage is NOT a successful admission/certificate. All existing local constraints,
reference/ownership,version/authority,AND/OR grounding and native alternatives
must be checked before an admitted input can reach analysis/publication.

SourceSnapshot exposes owner-bound primitive fields, full UTF16 block reads,
explicit scoped String projection and ordered list ordinals. No resident model
or unbounded per-input Map/List directory is part of this port. Optional fields
retain physical absence: the closed binder and admission still enforce conditional
required fields (nominal authorities),version gates and defaults.

The construction uses complete typed recipes with a hash-first paged index;
collisions compare every field. Text equality is full UTF16; namespaces/typed
identities are structural,never concatenated names. Lists have sixteen-item leaves
and binary carry assembly; balanced internal nodes store count and element type.
Canonical payload sharing does not erase duplicates: every input ordinal remains
in its list. Element lookup descends counts,not replaying all previous siblings.
Leaf partition and carry/fold order are deterministic from list order and length.
No cardinality cutoff or depth-limited semantic approximation.

Variable payloads,index/directories and construction frames belong to existing
PageStore/PagedLongArray/PagedLongIndex ports. Fixed controls/probes and text
projection are funded before allocation. Builder transfer,owner expiry,doubleclose,
failure poisoning and borrowed-page-store cleanup require direct laws. Root access
is sealed before publication; no caller can manufacture an admission certificate.
The resident source codec remains an explicitly resident API during migration.

Producer a0aeb827ef53765815aa501c4687f197be77ac73 exposes official physical staging
with explicitUTF16_JSON profile. The new binder must map that access into the closed
typed source catalogue without retaining JsonNode/whole Pojo products. It must not
reuse AIR semantics for source fields or relax any AIR entrypoint default.
Source mapping and complete admission now have local implementations and direct
witnesses; production CLI consumers and cursor output are still pending. No
global memory/performance/AS-W09/AS-W10 completion claim from this foundation.

## Physical mapping implementation (local qualified foundation; global PARTIAL)

QualifiedSourceJson.decodeSnapshot is an additive C3 storage API, not a replacement
for decode or an admission API. SourceSnapshotJson uses the producer's official
UTF16_JSON physical profile, then postorder traversal with forty-word paged frames.
There is one fixed thirty-field recipe and one reusable 256-code-unit borrowed
CharSequence view. Text payloads are interned without constructing whole Strings;
integral tokens retain the existing signed-int range and reject fractions/exponents.
Exact field names, required physical presence, element types and duplicate ordinals
are preserved. Conditional nominal authority/version requirements and all model
admission laws are still pending; storage success is not a semantic PASS.

Input and PageStore are borrowed. Physical staging and construction frames close
before storage returns; failure, including teardown after transfer, closes the typed
snapshot too. Exceptions retain their primary cause: physical parser failures are
AirJsonException, closed mapping/type failures IllegalArgumentException, and a
borrowed input IOException is preserved by identity. Primitive handles are local
to an owner, not globally unique tokens; external consumers must retain that owner.

The list carry invariant is the occupied-level representation of a binary counter:
every complete leaf is inserted once; a carry combines an earlier left subtree
with a later right subtree. The final descending-level fold retains input order.
For L leaves the carry count is less than L; this bounds structural combines, NOT
index comparisons or physical I/O. Ordinal lookup follows stored subtree counts;
both leaf and branch recipes compare all thirty payload fields after fingerprints.
See [Cornell binary counter analysis](https://www.cs.cornell.edu/courses/cs3110/2011sp/Recitations/rec20.htm).
The explicit traversal stack replaces recursive call frames and completes children
before their parent, following [Cornell DFS/postorder](https://www.cs.cornell.edu/courses/cs2112/2014fa/lectures/lec_traversals/).
These are algorithm references, not a qualification of this implementation's I/O.

Observed focused builder law: all six geometries 1/4/16/64/256/8192 passed with
30592 resident control bytes; largest temporary peak 64802944 and bytes read
6793721824. No no-thrashing claim. The three initial mapping laws passed, including
2048 nested terms. Historical literal coverage first failed for five missing shapes;
the supplement subsequently passed:53 historical fixtures plus one independent
literal supplement cover all45 catalogue shapes.13 builder/mapping laws,34
dictionary/value neighbors and31 resident source contract/provider neighbors passed.
The hash-only recipe mutant fails on the independently found real31-word
fingerprint collision(last field39176/70428;hash-2008668112);production preserves
all30 fields and distinct canonical identities. This is not the older8-field
prototype collision,which is not evidence for this recipe.

The preserved818022612-byte input(SHA2562600f843f269fa52d449da92ada5f310ba906d128feca5c72d37a63a199848e7)
also passed the typed metadata storage witness in128m/original64MiB heap/16MiB
scratch/16GiB temporary/480s,using the existing default-budget4096x4096 page
cache policy. Every distinct16384-unit path,coords,metadata sharing and8192
statement/native-use/declaration-origin occurrences was checked. Managed heap
peak17493632B,temporary4426570016B,read7958948000B/write4697738176B.
Internal storage-ready168.773s/full selected metadata oracle173.671s;
wrapper JVM-only175.902s excludes hashing. Capacity witness only:background
focused checks may overlap,so these times are not isolated campaign curves.
Admission,analysis,certificate and CLI were NOT_RUN;the original CLI OOM staysRED.

Native common-owner expiry found two genuine REDs:retained live text after
SnapshotProgram.close and5 live construction pages before any subsequent query.
One fixed cleanup link is now registered on the builder's funded control lease,
then points to the snapshot on transfer. It is not re-registered on the same lease;
explicit builder close cannot close transferred storage. Explicit snapshot close
detaches the link and all text projections. Decode checks the final root after
staging teardown so owner/deadline expiry cannot return a dead storage result.
These changed lifetime bytes invalidated the previous freeze for exact current
qualification. The final expiry02 repeat subsequently passed with identical input,
quotas and measured pools:170.047s JVM-only,ready165.442s/selected metadata168.851s.
All pools released. Admission and CLI remain NOT_RUN.

Compiled boundary capture reviewed8 new core/3 adapter classes before the expiry
fix;only source-local scalar/paged ports point inward.No Maven DAG or existing
resident codec descriptor changed.SourceSnapshotJson is one explicitly named
outer source registration,not wildcard scope expansion.Historical W1D source
inventory is READ_ONLY;the first refresh attempt rejected its stale baseline
without changing it.Current Java delta is checked against GitHEAD49855e2b.
The lifetime fix received its exact private-class capture. Original consumer FAST02
passed817.192s/1220mandatory methods/zero skips and all original compiled guards.
FAST01 stopped before tests because workflow checkout still referenced0ab while
the source lock referenceda0;only the local workflow ref was aligned,then the
original command repeated. Neither run is remote CI or publication evidence.
Strict post-FAST audit checks complete production class paths,all current source
hashes and exact final capacity runtime bytes for both changed consumer modules.
Closed raw foundation:130files/5031702B,including failures,captures and producer
logs;final FAST XML and integrity manifests are archived separately. Binaries,
toolchains,caches and reusable builds remain ignored,not versioned.
Campaign-level admission/certificate/CLI and global qualification remain pending.

## Complete admission implementation (working delta; global PARTIAL)

SourceAdmissionSnapshot.admit is the only legitimate certificate factory. It
accepts only the private sealed SourceSnapshotBuilder storage implementation,
with the exact borrowed PageStore and AnalysisResources identities used in its
construction. A successful claim transfers storage on both success and semantic,
resource or operational rejection. Forged/mismatched ports and duplicate claims
cannot steal an existing owner. The constructor is private; no incomplete result
escapes a check or its teardown. This is SOURCE admission, not an AIR publication
correlation decision or evidence that downstream analysis/CLI has migrated.

SourceSnapshotConstraints walks reachable records/lists using paged work and
exact (canonical reference, nominal authority) visited keys. Every existing local
record law, full Java UTF16 code-point extent, conditional physical presence and
nominal term/predicate arity is checked without resident trees. Shared canonical
terms must be checked separately under V3 and V4 in either unit order. Empty
literal/seed/initializer payloads remain permitted; blank identities do not.

SourceSnapshotRelations indexes complete typed (unit, namespace, first, second)
keys in SourceAdmissionDirectory. Full canonical owner-local handles, not hash
or concatenated spellings, define equality. Payload ordinals and two paged state
words are excluded from key comparison. A dedicated row-zero lookup probe is
never retained. Directories and work/edge queues spill through existing ports;
there is no per-input resident Map/List/Pojo/JsonNode bridge or alternate AIR parser.
Checks cover inventory uniqueness, references/ownership, registration origins,
event/guard/selection/derivation correlations, all version gates, nominal
declarations/readers/writers/tables/branches/queries and complete native/occurrence
alternatives. Per-location node counts plus unique, existing, same-location
qualification references prove exact alternative-set coverage.

All proofs, including unused proofs, use a dependency-indegree ready worklist to
reject cycles. Qualification grounding uses one pending count per derivation:
source and distinct caller prerequisites are AND, incoming derivations are OR,
and a node is queued only on its first reach. Equal source/caller counts once;
grounded cycles are allowed. These finite logical traversals are O(V+E), in
addition to paged index comparison/ordinal lookup and physical I/O costs, not a
universal linear-runtime or no-thrashing claim. Algorithm references:
[Cornell graph traversal](https://www.cs.cornell.edu/courses/cs4120/2016sp/lectures/lec_traversals/)
and [Waterloo topological worklist](https://student.cs.uwaterloo.ca/~cs341/lectures/lec12.graphs3.dark.1up.pdf).

The existing funded storage cleanup link also expires the certificate. Explicit
certificate/storage close and native common-owner expiry clear it before another
query and retire all owned pages; the backend remains borrowed. Close detaches
storage before invoking its dependent, and the certificate holds no monitor while
calling storage close, preventing callback recursion/lock-order inversion.

Observed current focused evidence:26 new methods in four classes;53 independent
literal inputs accepted by both local and global checks;counterexamples cover
duplicate/foreign identities, unused proof cycles, ungrounded nodes, missing or
duplicate alternatives and AND-versus-OR bootstrapping. The frozen factory witness
passed53 originals and seven independent negatives in10.480s JVM-only. Owner tests
also cover private construction, forged ports, exact resource/backend identity,
duplicate claim, denial, primitive faults and live post-admission read failure.
Directory8192 passed with11904B fixed resident usage including its backend controls,
1659232B temporary and2528978752B reads;this is not an I/O complexity claim.

The original818022612B input and unchanged SHA256/quotas/cache/watchdog passed actual
factory admission and the complete selected metadata oracle:ready169.690s,
oracle173.148s,JVM-only174.342s,heapPeak17493632B,temp4426570016B,
read8533310144B/write4723565408B,all pools0. The16-row control passed0.414s.
Neither witness executes analysis or production CLI, and neither is an isolated
campaign curve. The original production CLI OOM remains RED until its actual
input/consumer/output route is migrated and independently requalified.

Current boundary capture adds five core classfiles (four authored types and the
compiler's enum-switch helper), changes only two existing private inward dependency
sets, and preserves all existing public descriptors, Maven DAG and outer W5
inventory. Historical W1D source evidence is READ_ONLY. Current inventory refresh
is limited to that exact reviewed delta;26 new methods are mandatory in FAST.
Family73/core47 neighbors passed without failures/errors/skips. The current
original FAST passed871.569s:1246 mandatory methods,zero failures/errors/skips,
all original compiled guards and contracts. Exact post-FAST audit records1408
complete project production class paths,2192 consumer files,651 producer tracked
files and15 runtime jars. Every production class in the frozen literal and capacity
runtime is byte-identical to the final FAST,including unchanged upstream modules;
all532 frozen Java sources and original fixtures are unchanged. Helpers shadow no
production class. Raw logs,commands,source hashes and478 final FAST/audit/XML files
are preserved in the campaign archive,without binaries or caches. This qualifies
complete SOURCE admission locally,not the still-resident production SOURCE
consumer/CLI/output route or the campaign-wide obligations. Earlier foundation
FAST remains historical and is not relabeled as this delta's FAST.

## Productive SOURCE route (2026-10-10; global PARTIAL)

The preceding sections describe their frozen foundation checkpoints. The current
production SOURCE routes in AnalysisDependencies and AnalysisPipeline now consume
typed storage/admission directly. NativeInput delays SOURCE until its exact checked
SnapshotProgram exists; SourceSnapshotInput checks execution-budget identity before
allocating, decodes and hashes one stream, closes physical staging, verifies the
AIR/document correlations, then transfers storage to the private admission factory.
An equal configuration is not an authentic owner. DECODE and VALIDATION retain their
actual diagnostic phase through the execution facade rather than becoming INDEX.

SourceCorrelationSnapshot indexes explicit links and origin ancestry. The prepared
SourceQualifiedSnapshot shares its control facts with the nominal planner, so
qualification is not solved again per consumer. SourceTargetPlan selects queries
using the existing TargetResolver law, including model refutation, executable
closure and weaker SOURCE evidence. SourceNominalDemand, SourceSnapshotExpressions,
SourceLiteralView and SourceNominalCandidates retain variable facts/text/proofs in
pages. Bounds, collisions and full ordered provenance remain semantic properties,
not fingerprints or truncated strings.

SourceProgramSnapshot assembles complete result records without reconstructing
QualifiedDependencyOccurrence or SOURCE DTO lists. It evaluates a requested unit
once, copies its necessary candidate/proof facts, then retires the unit owner.
Candidate identity/order follows the existing length-prefixed reference/raw key;
support identity includes kind, full IDs and original ordered premises. Existing
first-contribution duplicates remain legal; merging contributions performs exact
dedup. Authorities, interpretation/value remainder and reasons remain independent.
Unsupported name interpretation does not erase an independently known executable
candidate or fabricate an interpreted SOURCE name.

ManagedDependencyResult is a private-factory read-only dependent of that admission.
SourceSnapshotWriter streams programs, raw/reference text, conditional evidence,
native files, reasons and metrics through one funded root JsonOutput buffer and one
output meter. Per-row wire fields are fixed-size frames; variable ID ordering uses
paged indices. Verification after the last flush and after stream finalization
precedes atomic move. Operational failure cannot replace the prior destination or
turn quota exhaustion into a semantic COMPLETE/PARTIAL result. Borrowed owners and
PageStore are never closed by the writer.

The stable focused product family passed 43 tests and the launcher family passed
14, including full small resident-oracle comparisons, known plus unknown,
full-identity/support cases, fixed-control geometries, owner expiry, page faults,
source/AIR hashes and preservation of both destinations at validation failure.
The original 818022612-byte SOURCE / 8192-FILE input passed both real CLIs under
128 MiB heap, unchanged default quotas/cache and 480-second watchdog. Independent
oracles compared every certificate token, FILE row and original AIR semantic field.
The 1364173307-byte dependencies files are byte-identical between the two routes,
SHA256 `475154d5f5ed0f36674e1c40ad054a166e2c59f135d85ae8818e25c2273d7bb6`.
Observed times are 232.198s and 258.500s; the latter overlaps FAST and is not an
isolated performance curve. That fixture has no nominal queries, so it cannot
qualify nominal scale. Original OOM evidence remains unchanged in the local campaign.

Seven original compiled boundary guards passed after exact capability review;
1279 old required methods are unchanged and 77 new methods are registered. The
current repository FAST passed all 1356 required methods, zero failures/errors/
skips, in 1720.547s. The entire 1433-production-class runtime is byte-exact to
the frozen real-CLI runtime, including all nine consumer and both AIR modules;
561 Java sources and 579 fixtures are unchanged, with no helper shadow. Producer a0 is unchanged and its
previous FAST is reused, not rerun. Complete campaign qualification is still open:
original nominal dimensions/isolated curves, AIR-base SiteFacts/result maps and
streams, resident manifest/correlation lists, all-store funding/spill, global
families, E2E, legacy retirement and human review. A source-only managed vertical
does not qualify these remaining structures or imply a universal linear bound.

## Typed manifest and correlation boundary (2026-10-11; working delta)

The productive dependency-input route now stages its closed wire with the official
AirJson physical parser. DependencyManifestSnapshot retains correlation text and
structural paths in paged records instead of a JsonNode tree or resident list of
StatementCorrelation DTOs. Only explicitly funded file-path/digest projections
are materialized. The manifest and checked AIR read share one physical backend,
not two independently enlarged caches. Transfer and failure distinguish the owner
of staging from the owner of the borrowed backend.

StatementCorrelations is a read-only borrowed data port, not an admission
certificate. Its eleven UTF-16 text fields retain full source, operation, label
and origin identities; structural-path coordinates are exact primitives. Binding
requires the authentic checked program/execution owner. Program/input expiry
retires the data. SourceCorrelationSnapshot compares these fields in blocks and
uses a paged full-operation-identity index. Duplicate/conflicting links, invalid
owners, derived origins and literal/technology rules remain checked before READY;
temporary preparation indices and scratch retire before downstream access.

Focused manifest/input tests passed 12 methods, existing correlation regressions
passed 15, and PipelineCliTest passed 12. All 1356 old mandatory methods are exact;
eight new methods are registered, for 1364. Seven original compiled guards passed
after review of only the two changed active inventories; historical SOURCE and
other inventories, producer pin and Maven dependency direction are unchanged.
The original FAST passed all 1364 mandatory methods, zero failures/errors/skips,
in 1716.698s. Exact post-FAST audit compared all 1436 production classfiles from
all nine consumer and both AIR modules with frozen03 used by the real CLIs;
all match, with 564 Java sources, 579 fixtures, 15 runtime components and no
helper shadow. Producer a0 remains clean/unchanged; its prior FAST is reused.

Unchanged original legal manifests with 1/4/1024/4096/8192 correlations passed
complete typed AIR/SOURCE/correlation admission under original 128 MiB heap,
default quotas and 480-second watchdog. Every full operation identity and
membership was checked, with all root resource pools zero after close. The former
4096-row resident-tree HEAP/DECODE failure is preserved separately. Actual CLI
4096 completed in 167.235s and passed a complete product oracle, including every
unreachable synthetic site and the original reachable PROGA/PROGB candidates.
CLI 8192 failed downstream with OOM at the executable inventory's occurrence
projection after 331.961s; its destination sentinel remained intact. Complete
typed admission alone did not qualify the complete product at this dimension.
The correction visits one typed SiteView/Invoke at a time instead of
retaining a second complete occurrence list beside the planner's facts. Borrowed
selected index handles and existing entry/site order remain; result maps and
SiteFacts are still resident. Sixteen product and eighteen launcher regression
methods passed. The unchanged original 8192 manifest then completed through the
real CLI in 352.583s with the complete independent product oracle passing; output
31013296 bytes, SHA256
`af131f88ff8663ee39963c3d440c38dbc73114bbcdba4903e76b97a2f9d333c5`.
The 1/4-row control products remain byte-identical. Runtime and material-source
integrity passed. Both original SOURCE/FILE CLI replays completed with the full
product oracle and whole previous dependency/CFG byte equality: dependencies
258.564s, Pipeline 308.729s for the unchanged 818022612-byte SOURCE. FAST overlaps
these replays, so these are not isolated latency curves. Mandatory FAST and its
exact whole-runtime audit passed. No global/all-store claim follows.

The original SOURCE-forward 128000 input also timed out in an isolated replay,
480.169s with destination sentinel preserved. Concurrent FAST is therefore not
its sole explanation. A diagnostic JFR run at 32000 produced the byte-exact
product; paging/resource-progress samples identify investigation targets, not a
proven asymptotic cause. Reverse scale, AIR-base result maps, all-store funding,
global qualification and review remain open. The small legacy resident reader
still exists for explicit compatibility/oracle use, not this productive route.

The ignored read-only admission-directory probe prototype passed the small
mechanism law, five unmodified directory laws and original nominal32000 full
product/whole previous byte equality. It still hit watchdog480 at128000 with FAST
concurrent; the prior destination remained intact. It is not integrated and is
not a closed explanation/fix for the isolated nominal timeout. A same-frozen03
productive baseline versus single-class prototype isolated pair is running only
after FAST and exact runtime audit. Reverse, all-store, global and review remain
open; no new quota/cache/deadline or semantic cap was introduced.

## General managed CALL and scoped SOURCE support ownership (2026-10-11)

Both productive CLI routes now retain general CALL facts in a managed paged
owner, including when SOURCE is absent. SOURCE preparation borrows this owner;
header indices preserve full typed site/operation identity and last-fact-wins
ordering without retaining a second complete fact list. Candidate/support extent
directories permit funded leaf projections instead of decoding unrelated fact
bodies for each support. Closing a SOURCE plan retires its projections without
closing borrowed CALL storage. Operational failure and owner expiry remain errors,
not semantic remainder. Detached List/DTO access remains explicit compatibility.

The common execution root now receives OUTPUT charges from the dependency
resource view. The original mandatory FAST first failed at one publishing-success
test whose root allowed zero output: its requested 12081 bytes were correctly
denied. That failed run and reports are preserved. Only that success fixture now
provides explicit output capacity and asserts both counters equal actual bytes;
the four original methods and other zero-output negative/read-only fixtures remain.
Its 19-method neighboring family passed. Exact equality of all 1453 productive
classfiles proves reuse of the earlier 40-method family, 18 original CLI contracts,
seven compiled guards and five actual complete-product oracle runs; those were
not newly executed after the fixture correction. The second original FAST passed
all 1404 mandatory methods, zero failures/errors/skips, in 1516.515s. The audit
confirmed all nine consumer and both AIR runtime paths, 15 components, 584 Java
sources and 579 fixtures, with no helper shadow. Producer
`a0aeb827ef53765815aa501c4687f197be77ac73` remains unchanged.

This is not all-output or all-store qualification. Direct managed DependencyJson
stream ports still bypass the meter that the file adapter correctly owns; the
next focused laws must expose that bypass and prevent double charging when the
same codec is called through the file adapter. Individual support/premise/ID/fact
and program projections still materialize complete nested DTOs. FILE results,
observations, plain qualification, common WORK accounting, global families and
new gaps, original dimensions/isolated curves, integrated E2E, legacy retirement,
human review and externally authorized integration remain pending. The earlier
large products are historical, not fresh qualification of this general owner.

## Shared managed codec OUTPUT qualification (2026-10-11)

The direct managed general and SOURCE codecs now charge logical UTF8 bytes before
writing, through the same final package-private stream meter used by standalone
SOURCE and atomic file writers. Exact resource-owner identity reuses the meter;
a foreign wrapper is rejected rather than silently switching accounting owners.
Zstandard charges logical bytes, not compressed file size. Borrowed streams and
result owners remain open; primary IO failures and pre-write output denial are
preserved. The explicitly caller-managed direct compatibility DependencyResult
port is unchanged. No productive quota, semantic/wire admission or pin changed.

Four direct-codec bypass RED laws are preserved. Seven new laws and the 30-method
neighboring family, 18 original CLI contracts, seven compiled guards and five
actual complete-product CLI oracles passed freshly for this wave. All five products
are byte-exact to their previous products; the SOURCE32000 observation took
71.534475s and emitted 22824055 bytes. This is not a median or large-scale curve.
The earlier GZIP test setup failure is retained; the test was corrected to the
actual Zstandard transport without changing production for that setup error.

The original complete FAST passed all 1411 mandatory methods, including every
unchanged prior 1404 method, zero failures/errors/skips, in 1491.931s. Exact audit
confirmed all 1452 productive classfiles across nine consumer and both AIR paths,
586 Java sources, 579 fixtures and 15 runtime components, without helper shadow.
Producer `a0aeb827ef53765815aa501c4687f197be77ac73` remains clean and unchanged.
Only the explicit new adapter source and active outer compiled inventory were
registered; existing public owner APIs and architectural denials are unchanged.

This closes the managed direct OUTPUT bypass, not the campaign. A support leaf
still materializes all premises and complete IDs. The next five prospective laws
target constant funded support/identity cursors, full block identity and ordinal
preservation, primary read failure, pressure and native expiry. They have not yet
executed at this qualification point. Complete facts/programs/provider assembly,
FILE results, observations, plain qualification, common WORK accounting, all-store
and global families/new gaps, original dimensions/isolated curves, integrated E2E,
legacy retirement, human review and external integration remain pending. Earlier
large products remain historical, not fresh qualification of this OUTPUT wave.
