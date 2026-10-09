package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;

/** One construction lifetime. Temporary membership/role maps are released after ownership transfer. */
final class IndexBuilder {
    Map<CfgNodeId,LocalControlRules.Rule> localRules;
    final Object identity = new Object();
    final ProgramStore.Structural store;
    final IndexMetrics.Counter count = new IndexMetrics.Counter();
    final Map<UnitId, ProgramStore.UnitView> units = new HashMap<>();
    final Map<LabelId, ProgramStore.SequenceView> sequences = new HashMap<>();
    final Map<EntryId, Entries.Entry> entries = new HashMap<>();
    final Map<EntryId, Integer> entryOrdinals = new HashMap<>();
    final Map<CfgNodeId, ProgramIndex.Node> nodeIds = new HashMap<>();
    final Map<LabelId, ProgramIndex.Node> sequenceNodes = new HashMap<>();
    final Map<EntryId, ProgramIndex.Node> entryNodes = new HashMap<>();
    final Map<EntryId, ProgramIndex.Node> normalExits = new HashMap<>();
    final Map<OperationId, ProgramIndex.Node> haltExits = new HashMap<>();
    private record OutsideKey(OperationId operation,Control.InvocationAlternative outcome) { }
    private final Map<OutsideKey,ProgramIndex.Node> outcomeExits=new HashMap<>();
    private long expectedOutside;
    final Map<OperationId, ProgramIndex.Site> operations = new HashMap<>();
    final Map<Class<? extends Operation>, List<ProgramIndex.Site>> buckets = new HashMap<>();
    final ProgramIndex.Declarations objects = new ProgramIndex.Declarations();
    final Map<StorageId, Memory.Storage> storage = new HashMap<>();
    final Map<ObjectId, Memory.Cell> directCells = new HashMap<>();
    final Map<OperandId, Place> places = new HashMap<>();
    final Map<OperandId, ObjectId> objectReferences = new HashMap<>();
    final LongIntDirectory forwardHeads = new LongIntDirectory(), backwardHeads = new LongIntDirectory();
    ProgramIndex.Node[] nodes;
    boolean factored;
    final Map<UnitId,ProgramIndex.Node> returnRepresentatives=new HashMap<>();
    CfgTransition[] edges;
    int[] from, to, edgeEntry, forwardNext, backwardNext;
    private final CfgBuildResult result;
    final ProjectionPolicy policy;
    final Set<OperationId> unprovedPreconditions;
    private final Set<OperandId> operandIds = new HashSet<>();
    private long expectedEdges, expectedHalts, expectedActiveEntries;

    IndexBuilder(CfgBuildResult result, ProgramStore.Structural store, ProjectionPolicy policy) {
        this.result = result; this.store = store; this.policy = policy;
        unprovedPreconditions=result.preflight().unprovedOperationPreconditions().orElse(Set.of());
    }
    static final class Rejection extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final AnalysisSession.Status status;
        Rejection(AnalysisSession.Status status, String reason) { super(reason); this.status = status; }
    }
    static void valid(boolean condition, String reason) {
        if (!condition) throw new Rejection(AnalysisSession.Status.INVALID_INPUT, reason);
    }
    private static void supported(boolean condition, String reason) {
        if (!condition) throw new Rejection(AnalysisSession.Status.UNSUPPORTED, reason);
    }
    private static <K, V> void unique(Map<K, V> map, K key, V value, String reason) {
        valid(map.putIfAbsent(key, value) == null, reason);
    }

    ProgramIndex build() {
        valid(result.publicationId().equals(store.publicationId())
                && result.airVersion().equals(store.airVersion()), "result/program metadata mismatch");
        valid(result.options().projectionPolicy() == policy, "projection policy mismatch");
        valid(result.status() != CfgBuildResult.Status.INVALID_IR, "invalid AIR build");
        supported(result.status() == CfgBuildResult.Status.CFG_BUILT, "unsupported CFG build profile");
        CfgGraph graph = result.graph().orElseThrow();
        valid(graph.source().publicationId().equals(store.publicationId())
                && graph.source().airVersion().equals(store.airVersion()), "foreign CFG source");
        supported(store.airVersion().equals(SemanticVersion.AIR_2_0_0), "unsupported AIR version");
        var namePolicies = store.namePolicyExtensions();
        for (var capability : store.capabilities().required()) {
            count.visit("requiredCapabilities");
            supported(capability.equals(Capabilities.LOCAL_CONTROL) || capability.equals(Capabilities.LOCAL_REENTRY_GUARD) || capability.equals(Capabilities.LOCAL_RESUME_ROUTES) || capability.equals(Capabilities.LOCAL_BOUNDARY_ROUTES) || capability.equals(Capabilities.LOCAL_UNWIND_ALL) || capability.equals(Capabilities.RESOURCE_BINDINGS) || capability.equals(Capabilities.TARGET_POSSIBILITIES) || capability.equals(Capabilities.MEMORY_REGIONS) || capability.equals(Capabilities.IBM1047) || capability.equals(Capabilities.ENTRY_POSSIBILITIES_V2) || capability.equals(Capabilities.ENTRY_POSSIBILITIES) || namePolicies.contains(capability), "unsupported control capability");
        }
        supported(policy.acceptsInventory(store.coverage().inventory()), "unsupported publication inventory policy");
        declarations();
        payload();
        nodes(graph);
        edges(graph);
        localRules=graph.localRules();
        return new ProgramIndex(this);
    }

    private void declarations() {
        for (var unit : store.units()) {
            count.visit("units.declarations");
            valid(unit.id().publication().equals(store.publicationId()), "foreign Unit owner");
            unique(units, unit.id(), unit, "duplicate Unit");
            supported((unit.body() == Unit.BodyAvailability.AVAILABLE || policy == ProjectionPolicy.PARTIAL_ANALYSIS) && policy.acceptsInventory(unit.coverage().inventory()), "unsupported Unit body/inventory");
            var declarations=unit.objects();int objectOrdinal=0;
            for (Memory.ObjectDeclaration object : declarations) {
                count.visit("objects");
                valid(object.id().unit().equals(unit.id()), "foreign Object owner");
                valid(objects.append(object.id(),declarations,objectOrdinal++),"duplicate Object");
                count.objects = Math.incrementExact(count.objects);
            }
        }
        for (Memory.Storage item : store.storage()) {
            count.visit("storage");
            valid(item.header().id().publication().equals(store.publicationId()), "foreign Storage owner");
            if (item.header().owner().isPresent()) {
                count.reference("storage.owner");
                valid(units.containsKey(item.header().owner().orElseThrow()), "missing Storage Unit");
            }
            unique(storage, item.header().id(), item, "duplicate Storage");
            if (item instanceof Memory.Cell) count.locations = Math.incrementExact(count.locations);
        }
        var objectIdentities=objects.keySet().iterator();
        for (var unit : store.units()) {
            count.visit("units.references");
            if (unit.containingUnit().isPresent()) {
                count.reference("unit.containing");
                valid(units.containsKey(unit.containingUnit().orElseThrow()), "missing containing Unit");
            }
            for (ObjectId id : unit.visibleObjects()) {
                count.reference("unit.visibleObjects");
                valid(objects.containsKey(id), "missing visible Object");
            }
            for (Memory.ObjectDeclaration object : unit.objects()) {
                count.visit("objects.bindings");
                // Cold bodies reconstruct IDs. Borrow the established key only after
                // checking the complete identity; position alone never substitutes an ID.
                valid(objectIdentities.hasNext(),"changed Object inventory");
                var identity=objectIdentities.next();
                valid(identity.equals(object.id()),"changed indexed Object identity");
                if (object.storage() instanceof Memory.CellBinding binding) {
                    count.reference("bindings.cell");
                    Memory.Storage item = storage.get(binding.storage());
                    valid(item instanceof Memory.Cell, "CellBinding requires canonical Cell");
                    directCells.put(identity, (Memory.Cell) item);
                }
                // Other binding forms remain original AIR, without alias/effect interpretation.
            }
        }
        valid(!objectIdentities.hasNext(),"changed Object inventory");
    }

    private void payload() {
        for (var declared : store.units()) {
            var unit=units.get(declared.id());
            count.visit("units.payload");
            long arity = 0;
            for (var sequence : unit.sequences()) {
                count.visit("sequences");
                valid(sequence.label().unit().equals(unit.id()), "foreign Sequence owner");
                unique(sequences, sequence.label(), sequence, "duplicate Sequence label");
                Terminator term = sequence.terminator();
                supported(LocalControlRules.local(term) || term instanceof Operations.Jump || term instanceof Operations.Branch || term instanceof Operations.Return || term instanceof Operations.Halt
                    || term instanceof Operations.Invoke invoke && OpenControl.supportsInvoke(invoke)
                    || term instanceof Operations.Opaque opaque && OpenControl.supportsOpaque(opaque) || policy == ProjectionPolicy.PARTIAL_ANALYSIS, "unsupported control");
                int degree = term instanceof Operations.Branch ? 2 : term instanceof Operations.Invoke invoke ? (int) invoke.outcomes().known().stream().filter(Control.Normal.class::isInstance).count()
                    : term instanceof Operations.Opaque opaque ? (int) opaque.envelope().control().known().stream().filter(a->OpenControl.alternativeLabel(a)!=null || a instanceof Control.ReturnAlternative).map(a -> {
                        var l = OpenControl.alternativeLabel(a); return l == null ? a : l;
                    }).distinct().count() : term instanceof Operations.Return || term instanceof Operations.Jump || term instanceof Operations.Halt ? 1 : 0;
                var known=OpenControl.alternatives(term);
                degree+=Math.toIntExact(known.stream().map(OpenControl::exceptionLabel).filter(Objects::nonNull).distinct().count());
                long outside=known.stream().filter(OpenControl::outside).distinct().count();
                if(!LocalControlRules.local(term))degree+=Math.toIntExact(outside);expectedOutside=Math.addExact(expectedOutside,outside);
                arity = Math.addExact(arity, degree);
                if (term instanceof Operations.Halt) expectedHalts = Math.incrementExact(expectedHalts);
                int offset = 0;
                for (Instruction instruction : sequence.instructions()) {
                    operation(sequence, unit, offset, instruction);
                    offset = Math.incrementExact(offset);
                }
                operation(sequence, unit, offset, term);
            }
            for (Entries.Entry entry : unit.entries()) {
                count.visit("entries");
                valid(entry.id().unit().equals(unit.id()), "foreign Entry owner");
                unique(entries, entry.id(), entry, "duplicate Entry");
                entryOrdinals.put(entry.id(), entryOrdinals.size());
                if(entry.initialLabel().isPresent() || policy != ProjectionPolicy.PARTIAL_ANALYSIS) {
                expectedActiveEntries++;
                valid(entry.initialLabel().isPresent(), "Entry has no initial Sequence");
                count.reference("entry.initialLabel");
                valid(entry.initialLabel().orElseThrow().unit().equals(unit.id())
                        && sequences.containsKey(entry.initialLabel().orElseThrow()), "foreign/missing Entry initial Sequence");
                }
                for (Entries.InitialCondition condition : entry.state().conditions()) {
                    count.visit("entry.conditions");
                    operands(List.of(condition.place()), new EntryOwner(entry.id()));
                    if (condition.value() instanceof Entries.LiteralInitial initial)
                        operands(List.of(initial.value()), new EntryOwner(entry.id()));
                    if (condition.value() instanceof Entries.PossibleLiterals possible)
                        operands(new ArrayList<Operand>(possible.candidates()), new EntryOwner(entry.id()));
                }
            }
            // Expected cardinality plus unique valid roles proves completeness without constructing
            // expected edges, including on a severely truncated input. No Entries x Sequences pass.
            expectedEdges = Math.addExact(expectedEdges, Math.multiplyExact(unit.entries().stream().filter(e->e.initialLabel().isPresent()).count(), Math.incrementExact(arity)));
        }
    }

    private void operation(ProgramStore.SequenceView sequence, ProgramStore.UnitView owner, int offset, Operation operation) {
        count.visit("operations");
        valid(operation.header().id().unit().equals(owner.id()), "foreign Operation owner");
        var site = new ProgramIndex.Site(sequence, owner, offset);
        unique(operations, operation.header().id(), site, "duplicate Operation");
        buckets.computeIfAbsent(operation.getClass(), ignored -> new ArrayList<>()).add(site);
        count.operations = Math.incrementExact(count.operations);
        operands(Operands.roots(operation), new OperationOwner(operation.header().id()));
    }

    private void operands(List<Operand> roots, OperandOwner expectedOwner) {
        // AIR supplies non-semantic child traversal; no recursion or evaluation, no full model clone.
        ArrayDeque<Operand> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            Operand operand = pending.removeLast();
            count.visit("operands");
            valid(operand.header().id().owner().equals(expectedOwner), "foreign Operand owner");
            valid(operandIds.add(operand.header().id()), "duplicate Operand occurrence");
            if(operand instanceof Place place)places.put(place.header().id(),place);
            if (operand instanceof Places.ObjectPlace place) {
                ObjectId declaration = resolveObject(place.object());
                objectReferences.put(place.header().id(), declaration);
            }
            pending.addAll(Operands.children(operand));
        }
    }
    private ObjectId resolveObject(ObjectId id) {
        count.reference("operands.object");
        valid(objects.containsKey(id), "missing Object reference");
        return id;
    }

    private void nodes(CfgGraph graph) {
        nodes = new ProgramIndex.Node[graph.nodes().size()];
        int ordinal = 0;
        for (CfgNode source : graph.nodes()) {
            count.visit("cfg.nodes");
            valid(source.id().publicationId().equals(store.publicationId()), "foreign CFG node publication");
            UnitId owner = switch (source) {
                case CfgNode.SequenceNode n -> n.label().unit();
                case CfgNode.EntryNode n -> n.entry().unit();
                case CfgNode.NormalExit n -> n.unitId();
                case CfgNode.HaltExit n -> n.operation().unit();
                case CfgNode.OutcomeExit n -> n.operation().unit();
            };
            var unit = units.get(owner);
            valid(unit != null, "foreign CFG node Unit");
            var node = new ProgramIndex.Node(identity, ordinal, source, unit,
                    source instanceof CfgNode.SequenceNode sequence ? sequences.get(sequence.label()) : null);
            nodes[ordinal] = node;
            ordinal = Math.incrementExact(ordinal);
            unique(nodeIds, source.id(), node, "duplicate CFG node ID");
            switch (source) {
                case CfgNode.SequenceNode n -> {
                    var sequence=sequences.get(n.label());
                    valid(sequence != null && CfgControl.from(sequence.terminator()).equals(n.control())
                            && n.operations().equals(sequence.instructions().stream()
                                    .map(operation -> operation.header().id()).toList()),
                            "foreign/replaced Sequence source");
                    unique(sequenceNodes, n.label(), node, "duplicate SequenceNode");
                }
                case CfgNode.EntryNode n -> {
                    var entry=entries.get(n.entry());
                    valid(entry != null && entry.initialLabel().equals(n.initialLabel()), "foreign/replaced Entry source");
                    unique(entryNodes, n.entry(), node, "duplicate EntryNode");
                }
                case CfgNode.NormalExit n -> {
                    valid(entries.containsKey(n.entryId()) && n.entryId().unit().equals(n.unitId())
                            && n.publicationId().equals(store.publicationId()), "foreign NormalExit");
                    unique(normalExits, n.entryId(), node, "duplicate NormalExit");
                }
                case CfgNode.OutcomeExit n -> {
                    ProgramIndex.Site site=operations.get(n.operation());
                    valid(site!=null&&site.isTerminator(),"foreign/replaced outside outcome occurrence");
                    valid(OpenControl.outside(n.outcome())&&OpenControl.alternatives((Terminator)site.operation()).contains(n.outcome()),"unpublished outside outcome");
                    unique(outcomeExits,new OutsideKey(n.operation(),n.outcome()),node,"duplicate outside outcome");
                }
                case CfgNode.HaltExit n -> {
                    ProgramIndex.Site site = operations.get(n.operation());
                    valid(site != null && site.isTerminator() && site.operation() instanceof Operations.Halt halt
                            && halt.haltKind() == n.haltKind(), "foreign/replaced Halt occurrence");
                    unique(haltExits, n.operation(), node, "duplicate HaltExit");
                }
            }
            count.nodes = Math.incrementExact(count.nodes);
        }
        valid(sequenceNodes.size() == sequences.size(), "missing required SequenceNode");
        valid(entryNodes.size() == expectedActiveEntries, "missing required EntryNode");
        valid(normalExits.size() == expectedActiveEntries, "missing required NormalExit");
        valid(outcomeExits.size()==expectedOutside,"missing required outside outcome");
        valid(haltExits.size() == expectedHalts, "missing required HaltExit");
    }

    private void edges(CfgGraph graph) {
        factored=graph.transitions() instanceof CfgTransitionTable;
        var rows=factored?((CfgTransitionTable)graph.transitions()).stored():graph.transitions();
        if(factored) {
            var table=(CfgTransitionTable)graph.transitions();
            for(int group=0;group<table.groups();group++)returnRepresentatives.put(table.unit(group),nodeIds.get(table.normalExit(group,0)));
        }
        int length = rows.size();
        edges = new CfgTransition[length];
        from = new int[length]; to = new int[length]; edgeEntry = new int[length];
        forwardNext = new int[length]; backwardNext = new int[length];
        LongIntDirectory seenRoles = new LongIntDirectory();
        int ordinal = 0;
        for (CfgTransition edge : rows) {
            count.visit("cfg.transitions");
            ProgramIndex.Node source = nodeIds.get(edge.from()), target = nodeIds.get(edge.to());
            Entries.Entry activation = entries.get(edge.activationEntry());
            valid(source != null && target != null && activation != null, "foreign edge endpoint/Entry");
            valid(source.owner().id().equals(activation.id().unit()) && target.owner().id().equals(activation.id().unit()), "wrong edge activationEntry");
            ProgramIndex.Node expected = expectedTarget(source, activation, edge.kind(), target);
            valid(expected != null && expected == target, "wrong edge destination/kind/context");
            int context = entryOrdinals.get(activation.id());
            long key = LongIntDirectory.key(context, source.ordinal);
            int roles = seenRoles.get(key);
            if (roles < 0) roles = 0;
            int bit = 1 << edge.kind().ordinal();
            valid(edge.kind() == CfgTransition.Kind.OPAQUE_JUMP || edge.kind()==CfgTransition.Kind.EXCEPTION || edge.kind()==CfgTransition.Kind.CONTROL_EXIT || (roles & bit) == 0, "duplicate semantic contextual edge");
            seenRoles.put(key, roles | bit);
            edges[ordinal] = edge; from[ordinal] = source.ordinal; to[ordinal] = target.ordinal; edgeEntry[ordinal] = factored&&edge.kind()!=CfgTransition.Kind.ENTRY?-1:context;
            ordinal = Math.incrementExact(ordinal);
            count.edges = Math.incrementExact(count.edges);
        }
        if(factored)count.edges=graph.transitions().size();
        valid(count.edges == expectedEdges, "missing required contextual edge");
        // Linked edge columns preserve input order and require only nonempty (Entry, node) rows.
        for (int edge = length - 1; edge >= 0; edge--) {
            count.visit("adjacency.transitions");
            forwardNext[edge] = forwardHeads.put(LongIntDirectory.key(edgeEntry[edge], from[edge]), edge);
            backwardNext[edge] = backwardHeads.put(LongIntDirectory.key(edgeEntry[edge], to[edge]), edge);
        }
    }

    private ProgramIndex.Node expectedTarget(ProgramIndex.Node source, Entries.Entry activation, CfgTransition.Kind kind, ProgramIndex.Node target) {
        if (source.source() instanceof CfgNode.EntryNode entry) {
            return kind == CfgTransition.Kind.ENTRY && entry.entry().equals(activation.id())
                    ? sequenceNodes.get(activation.initialLabel().orElseThrow()) : null;
        }
        if (!(source.source() instanceof CfgNode.SequenceNode node)) return null;
        if(kind==CfgTransition.Kind.EXCEPTION&&target.source() instanceof CfgNode.SequenceNode destination)
            return CfgControl.alternatives(node.control()).stream().anyMatch(a->destination.label().equals(OpenControl.exceptionLabel(a)))?target:null;
        if(kind==CfgTransition.Kind.CONTROL_EXIT&&!LocalControlRules.local(node.control())&&target.source() instanceof CfgNode.OutcomeExit outside)
            return outside.operation().equals(node.control().operation())&&OpenControl.outside(outside.outcome())
                &&CfgControl.alternatives(node.control()).contains(outside.outcome())?target:null;
        return switch (node.control()) {
            case CfgControl.Opaque opaque -> kind == CfgTransition.Kind.OPAQUE_RETURN && opaque.alternatives().contains(Control.ReturnAlternative.INSTANCE)
                ? normalExits.get(activation.id()) : kind == CfgTransition.Kind.OPAQUE_JUMP && target.source() instanceof CfgNode.SequenceNode seq
                    && opaque.alternatives().stream().anyMatch(a -> seq.label().equals(OpenControl.alternativeLabel(a))) ? target : null;
            case CfgControl.Jump jump -> kind == CfgTransition.Kind.JUMP ? sequenceNodes.get(jump.destination()) : null;
            case CfgControl.Invoke invoke -> kind == CfgTransition.Kind.INVOKE_NORMAL && target.source() instanceof CfgNode.SequenceNode s
                    && invoke.alternatives().stream().anyMatch(o -> o instanceof Control.Normal n && n.label().equals(s.label())) ? target : null;
            case CfgControl.Branch branch -> switch (kind) {
                case BRANCH_TRUE -> sequenceNodes.get(branch.trueDestination());
                case BRANCH_FALSE -> sequenceNodes.get(branch.falseDestination());
                default -> null;
            };
            case CfgControl.Return ignored -> kind == CfgTransition.Kind.RETURN ? normalExits.get(activation.id()) : null;
            case CfgControl.Halt halt -> kind == CfgTransition.Kind.HALT ? haltExits.get(halt.operation()) : null;
            default -> throw new IllegalStateException("admitted unsupported terminator");
        };
    }
}
