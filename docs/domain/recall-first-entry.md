# RF-W1 — invocation entry possibilities

Status IN_PROGRESS for campaign RF-W0 through RF-W4. Authority: pinned analysis-ir entry.possibilities@1; baseline main and exact RF consumer pins in sources.lock.json. No replacement dataflow or solver.

PossibleLiterals contributes enumerated literals by MAY boundary updates plus the existing unspecified entry alternative. The scalar provider joins supported literals with Candidates.UNKNOWN at its boundary. RD exposes ENTRY_POSSIBILITY alongside ENTRY_UNKNOWN; neither is an executable statement. Provenance includes the mandatory remainder reference. Strong literals retain their existing initialization behavior.

The CFG only recognizes the new capability as control-neutral; no topology, successor or entry semantics change. Traversal indexes all entry candidate operands. Conditions are installed on the invocation entry node, which has no backedge predecessors. They are never joined at use sites. Later ordinary transfer functions apply MAY preservation and exact MUST kill.

G1/G2: 50 tests passed across CfgPreflightTest, AdmissionTest, ReachingDefinitionsTest, PossibleEntryTest, RegionalInitialTest, RegionalTransferTest and ValuesTest. Cases include candidate + remainder, opaque no-write/MAY all, exact known/unknown MUST, branch, entry-vs-label backedge and agreement between scalar/regional providers. Existing strong entry behavior remains covered. Repository FAST passed (75.521 s). Its first run found the expected compiled-inventory drift: three references to the new AIR variant, reviewed and recorded without changing DAG rules. Selected SP→AIR→dependency vertical qualification follows at the campaign boundary; full/corpus is not the debugger.

`ENTRY_POSSIBILITY` is an additive definition kind in the existing regional result JSON; consumers must retain it as entry evidence, not invent an executable write. Existing result fields and strong-definition meanings are unchanged.
