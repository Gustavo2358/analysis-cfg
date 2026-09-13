package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;

/** One construction lifetime. Temporary membership/role maps are released after ownership transfer. */
final class IndexBuilder {
    final Object identity = new Object();
    final Publication snapshot;
    final IndexMetrics.Counter count = new IndexMetrics.Counter();
    final Map<UnitId, Unit> units = new HashMap<>();
    final Map<LabelId, Sequence> sequences = new HashMap<>();
    final Map<EntryId, Entries.Entry> entries = new HashMap<>();
    final Map<EntryId, Integer> entryOrdinals = new HashMap<>();
    final Map<CfgNodeId, ProgramIndex.Node> nodeIds = new HashMap<>();
    final Map<LabelId, ProgramIndex.Node> sequenceNodes = new HashMap<>();
    final Map<EntryId, ProgramIndex.Node> entryNodes = new HashMap<>();
    final Map<EntryId, ProgramIndex.Node> normalExits = new HashMap<>();
    final Map<OperationId, ProgramIndex.Node> haltExits = new HashMap<>();
    final Map<OperationId, ProgramIndex.Site> operations = new HashMap<>();
    final Map<Class<? extends Operation>, List<ProgramIndex.Site>> buckets = new HashMap<>();
    final Map<ObjectId, Memory.ObjectDeclaration> objects = new HashMap<>();
    final Map<StorageId, Memory.Storage> storage = new HashMap<>();
    final Map<ObjectId, Memory.Cell> directCells = new HashMap<>();
    final Map<OperandId, Memory.ObjectDeclaration> objectReferences = new HashMap<>();
    final LongIntDirectory forwardHeads = new LongIntDirectory(), backwardHeads = new LongIntDirectory();
    ProgramIndex.Node[] nodes;
    CfgTransition[] edges;
    int[] from, to, edgeEntry, forwardNext, backwardNext;
    private final CfgBuildResult result;
    private final ProjectionPolicy policy;
    private final Set<OperandId> operandIds = new HashSet<>();
    private long expectedEdges, expectedHalts;

    IndexBuilder(CfgBuildResult result, Publication snapshot, ProjectionPolicy policy) {
        this.result = result; this.snapshot = snapshot; this.policy = policy;
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
        valid(result.publicationId().equals(snapshot.id()) && result.airVersion().equals(snapshot.airVersion()), "result/snapshot metadata mismatch");
        valid(result.options().projectionPolicy() == policy, "projection policy mismatch");
        valid(result.status() != CfgBuildResult.Status.INVALID_IR, "invalid AIR build");
        supported(result.status() == CfgBuildResult.Status.CFG_BUILT, "unsupported CFG build profile");
        CfgGraph graph = result.graph().orElseThrow();
        valid(graph.publication() == snapshot, "foreign Publication instance");
        supported(snapshot.airVersion().equals(SemanticVersion.AIR_2_0_0), "unsupported AIR version");
        for (var capability : snapshot.capabilities().required()) {
            count.visit("requiredCapabilities");
            supported(capability.equals(Capabilities.MEMORY_REGIONS), "unsupported control capability");
        }
        supported(policy.acceptsInventory(snapshot.coverage().inventory()), "unsupported publication inventory policy");
        declarations();
        payload();
        nodes(graph);
        edges(graph);
        return new ProgramIndex(this);
    }

    private void declarations() {
        for (Unit unit : snapshot.units()) {
            count.visit("units.declarations");
            valid(unit.id().publication().equals(snapshot.id()), "foreign Unit owner");
            unique(units, unit.id(), unit, "duplicate Unit");
            supported(unit.body() == Unit.BodyAvailability.AVAILABLE && policy.acceptsInventory(unit.coverage().inventory()), "unsupported Unit body/inventory");
            for (Memory.ObjectDeclaration object : unit.objects()) {
                count.visit("objects");
                valid(object.id().unit().equals(unit.id()), "foreign Object owner");
                unique(objects, object.id(), object, "duplicate Object");
                count.objects = Math.incrementExact(count.objects);
            }
        }
        for (Memory.Storage item : snapshot.storage()) {
            count.visit("storage");
            valid(item.header().id().publication().equals(snapshot.id()), "foreign Storage owner");
            if (item.header().owner().isPresent()) {
                count.reference("storage.owner");
                valid(units.containsKey(item.header().owner().orElseThrow()), "missing Storage Unit");
            }
            unique(storage, item.header().id(), item, "duplicate Storage");
            if (item instanceof Memory.Cell) count.locations = Math.incrementExact(count.locations);
        }
        for (Unit unit : snapshot.units()) {
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
                if (object.storage() instanceof Memory.CellBinding binding) {
                    count.reference("bindings.cell");
                    Memory.Storage item = storage.get(binding.storage());
                    valid(item instanceof Memory.Cell, "CellBinding requires canonical Cell");
                    directCells.put(object.id(), (Memory.Cell) item);
                }
                // Other binding forms remain original AIR, without alias/effect interpretation.
            }
        }
    }

    private void payload() {
        for (Unit unit : snapshot.units()) {
            count.visit("units.payload");
            long arity = 0;
            for (Sequence sequence : unit.sequences()) {
                count.visit("sequences");
                valid(sequence.label().unit().equals(unit.id()), "foreign Sequence owner");
                unique(sequences, sequence.label(), sequence, "duplicate Sequence label");
                Terminator term = sequence.terminator();
                supported(term instanceof Operations.Jump || term instanceof Operations.Branch || term instanceof Operations.Return || term instanceof Operations.Halt
                    || term instanceof Operations.Invoke invoke && OpenControl.supportsInvoke(invoke)
                    || term instanceof Operations.Opaque opaque && OpenControl.supportsOpaque(opaque), "unsupported control");
                int degree = term instanceof Operations.Branch ? 2 : term instanceof Operations.Invoke invoke ? invoke.outcomes().known().size()
                    : term instanceof Operations.Opaque opaque ? (int) opaque.envelope().control().known().stream().map(a -> {
                        var l = OpenControl.alternativeLabel(a); return l == null ? a : l;
                    }).distinct().count() : 1;
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
                valid(entry.initialLabel().isPresent(), "Entry has no initial Sequence");
                count.reference("entry.initialLabel");
                valid(entry.initialLabel().orElseThrow().unit().equals(unit.id())
                        && sequences.containsKey(entry.initialLabel().orElseThrow()), "foreign/missing Entry initial Sequence");
                for (Entries.InitialCondition condition : entry.state().conditions()) {
                    count.visit("entry.conditions");
                    operands(List.of(condition.place()), new EntryOwner(entry.id()));
                    if (condition.value() instanceof Entries.LiteralInitial initial)
                        operands(List.of(initial.value()), new EntryOwner(entry.id()));
                }
            }
            // Expected cardinality plus unique valid roles proves completeness without constructing
            // expected edges, including on a severely truncated input. No Entries x Sequences pass.
            expectedEdges = Math.addExact(expectedEdges, Math.multiplyExact((long) unit.entries().size(), Math.incrementExact(arity)));
        }
    }

    private void operation(Sequence sequence, Unit owner, int offset, Operation operation) {
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
            if (operand instanceof Places.ObjectPlace place) {
                Memory.ObjectDeclaration declaration = resolveObject(place.object());
                objectReferences.put(place.header().id(), declaration);
            }
            pending.addAll(Operands.children(operand));
        }
    }
    private Memory.ObjectDeclaration resolveObject(ObjectId id) {
        count.reference("operands.object");
        Memory.ObjectDeclaration declaration = objects.get(id);
        valid(declaration != null, "missing Object reference");
        return declaration;
    }

    private void nodes(CfgGraph graph) {
        nodes = new ProgramIndex.Node[graph.nodes().size()];
        int ordinal = 0;
        for (CfgNode source : graph.nodes()) {
            count.visit("cfg.nodes");
            valid(source.id().publicationId().equals(snapshot.id()), "foreign CFG node publication");
            UnitId owner = switch (source) {
                case CfgNode.SequenceNode n -> n.source().label().unit();
                case CfgNode.EntryNode n -> n.source().id().unit();
                case CfgNode.NormalExit n -> n.unitId();
                case CfgNode.HaltExit n -> n.source().header().id().unit();
            };
            Unit unit = units.get(owner);
            valid(unit != null, "foreign CFG node Unit");
            var node = new ProgramIndex.Node(identity, ordinal, source, unit);
            nodes[ordinal] = node;
            ordinal = Math.incrementExact(ordinal);
            unique(nodeIds, source.id(), node, "duplicate CFG node ID");
            switch (source) {
                case CfgNode.SequenceNode n -> {
                    valid(sequences.get(n.source().label()) == n.source(), "foreign/replaced Sequence source");
                    unique(sequenceNodes, n.source().label(), node, "duplicate SequenceNode");
                }
                case CfgNode.EntryNode n -> {
                    valid(entries.get(n.source().id()) == n.source(), "foreign/replaced Entry source");
                    unique(entryNodes, n.source().id(), node, "duplicate EntryNode");
                }
                case CfgNode.NormalExit n -> {
                    valid(entries.containsKey(n.entryId()) && n.entryId().unit().equals(n.unitId())
                            && n.publicationId().equals(snapshot.id()), "foreign NormalExit");
                    unique(normalExits, n.entryId(), node, "duplicate NormalExit");
                }
                case CfgNode.HaltExit n -> {
                    ProgramIndex.Site site = operations.get(n.source().header().id());
                    valid(site != null && site.isTerminator() && site.operation() == n.source(), "foreign/replaced Halt occurrence");
                    unique(haltExits, n.source().header().id(), node, "duplicate HaltExit");
                }
            }
            count.nodes = Math.incrementExact(count.nodes);
        }
        valid(sequenceNodes.size() == sequences.size(), "missing required SequenceNode");
        valid(entryNodes.size() == entries.size(), "missing required EntryNode");
        valid(normalExits.size() == entries.size(), "missing required NormalExit");
        valid(haltExits.size() == expectedHalts, "missing required HaltExit");
    }

    private void edges(CfgGraph graph) {
        int length = graph.transitions().size();
        edges = new CfgTransition[length];
        from = new int[length]; to = new int[length]; edgeEntry = new int[length];
        forwardNext = new int[length]; backwardNext = new int[length];
        LongIntDirectory seenRoles = new LongIntDirectory();
        int ordinal = 0;
        for (CfgTransition edge : graph.transitions()) {
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
            valid(edge.kind() == CfgTransition.Kind.OPAQUE_JUMP || (roles & bit) == 0, "duplicate semantic contextual edge");
            seenRoles.put(key, roles | bit);
            edges[ordinal] = edge; from[ordinal] = source.ordinal; to[ordinal] = target.ordinal; edgeEntry[ordinal] = context;
            ordinal = Math.incrementExact(ordinal);
            count.edges = Math.incrementExact(count.edges);
        }
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
            return kind == CfgTransition.Kind.ENTRY && entry.source() == activation
                    ? sequenceNodes.get(activation.initialLabel().orElseThrow()) : null;
        }
        if (!(source.source() instanceof CfgNode.SequenceNode node)) return null;
        return switch (node.source().terminator()) {
            case Operations.Opaque opaque -> kind == CfgTransition.Kind.OPAQUE_RETURN && opaque.envelope().control().known().contains(Control.ReturnAlternative.INSTANCE)
                ? normalExits.get(activation.id()) : kind == CfgTransition.Kind.OPAQUE_JUMP && target.source() instanceof CfgNode.SequenceNode seq
                    && OpenControl.opaqueDestination(opaque, seq.source().label()) ? target : null;
            case Operations.Jump jump -> kind == CfgTransition.Kind.JUMP ? sequenceNodes.get(jump.destination()) : null;
            case Operations.Invoke invoke -> kind == CfgTransition.Kind.INVOKE_NORMAL
                    ? sequenceNodes.get(((Control.Normal) invoke.outcomes().known().getFirst()).label()) : null;
            case Operations.Branch branch -> switch (kind) {
                case BRANCH_TRUE -> sequenceNodes.get(branch.trueDestination());
                case BRANCH_FALSE -> sequenceNodes.get(branch.falseDestination());
                default -> null;
            };
            case Operations.Return ignored -> kind == CfgTransition.Kind.RETURN ? normalExits.get(activation.id()) : null;
            case Operations.Halt halt -> kind == CfgTransition.Kind.HALT ? haltExits.get(halt.header().id()) : null;
            default -> throw new IllegalStateException("admitted unsupported terminator");
        };
    }
}
