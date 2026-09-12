package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;

/**
 * Immutable structural inventory for one canonical AIR/CFG snapshot. No reachability, effects or values
 * are computed. Full-ID lookup is a boundary operation; contextual traversal uses opaque node handles.
 * Collection order is snapshot order (CFG order for nodes/edges, AIR instruction order for sites).
 */
public final class ProgramIndex {
    final Map<UnitId,List<Node>> unitNodes = new HashMap<>(), openSources = new HashMap<>();
    final Object identity;
    final Node[] nodes;
    final CfgTransition[] edges;
    final int[] from, to, entry, forwardNext, backwardNext;
    final LongIntDirectory forwardHeads, backwardHeads;
    final Map<EntryId, Integer> entryOrdinals;
    final Map<EntryId, Node> entryNodes, normalExits;
    private final Publication publication;
    private final Map<CfgNodeId, Node> nodeIds;
    private final Map<UnitId, Unit> units;
    private final Map<LabelId, Node> sequences;
    private final Map<OperationId, Site> operations;
    private final Map<Class<? extends Operation>, List<Site>> buckets;
    private final Map<ObjectId, Memory.ObjectDeclaration> objects;
    private final Map<StorageId, Memory.Storage> storage;
    private final Map<ObjectId, Memory.Cell> directCells;
    private final Map<OperandId, Memory.ObjectDeclaration> objectReferences;
    private final Map<EntryId, Entries.Entry> entries;
    private final IndexMetrics metrics;

    ProgramIndex(IndexBuilder b) {
        identity = b.identity;
        publication = b.snapshot;
        nodes = b.nodes;
        for (var node : nodes) {
            unitNodes.computeIfAbsent(node.owner().id(), ignored -> new ArrayList<>()).add(node);
            if (OpenControl.bound(node) instanceof Scopes.WithinControl)
                openSources.computeIfAbsent(node.owner().id(), ignored -> new ArrayList<>()).add(node);
        }
        unitNodes.replaceAll((u, list) -> List.copyOf(list)); openSources.replaceAll((u, list) -> List.copyOf(list));
        edges = b.edges;
        from = b.from; to = b.to; entry = b.edgeEntry;
        forwardNext = b.forwardNext; backwardNext = b.backwardNext;
        forwardHeads = b.forwardHeads; backwardHeads = b.backwardHeads;
        nodeIds = b.nodeIds; units = b.units; sequences = b.sequenceNodes;
        operations = b.operations; objects = b.objects; storage = b.storage;
        directCells = b.directCells; objectReferences = b.objectReferences;
        entries = b.entries; entryOrdinals = b.entryOrdinals;
        entryNodes = b.entryNodes; normalExits = b.normalExits;
        // Builder ownership is transferred. No builder or mutable collection escapes.
        b.buckets.replaceAll((kind, sites) -> {
            b.count.visit("buckets.freeze");
            return Collections.unmodifiableList(sites);
        });
        buckets = b.buckets;
        metrics = b.count.snapshot();
    }

    public Publication publication() { return publication; }
    public IndexMetrics metrics() { return metrics; }
    /** Null means ID absent from this snapshot; IDs always include their owners. */
    public Node node(CfgNodeId id) { return nodeIds.get(id); }
    public Node sequence(LabelId id) { return sequences.get(id); }
    public Unit unit(UnitId id) { return units.get(id); }
    public Entries.Entry entry(EntryId id) { return entries.get(id); }
    public Site site(OperationId id) { return operations.get(id); }
    public List<Site> sites(Class<? extends Operation> kind) { return buckets.getOrDefault(kind, List.of()); }
    public Memory.ObjectDeclaration object(ObjectId id) { return objects.get(id); }
    public Memory.Storage storage(StorageId id) { return storage.get(id); }
    /** Direct whole Cell association only; null does not assert absence of indirect storage. */
    public Memory.Cell directCell(ObjectId id) { return directCells.get(id); }
    /** Pre-resolved ObjectPlace occurrence, including nested operands and Entry initial conditions. */
    public Memory.ObjectDeclaration referencedObject(OperandId occurrence) { return objectReferences.get(occurrence); }

    /** Opaque index handle. Its private ordinal never becomes an AIR/CFG identity or public result. */
    public static final class Node {
        final Object identity;
        final int ordinal;
        private final CfgNode source;
        private final Unit owner;
        Node(Object identity, int ordinal, CfgNode source, Unit owner) {
            this.identity = identity; this.ordinal = ordinal; this.source = source; this.owner = owner;
        }
        public CfgNode source() { return source; }
        public Unit owner() { return owner; }
    }

    /** One retained site per operation, reused by ID lookup and kind buckets; no per-query wrappers. */
    public static final class Site {
        private final Sequence sequence;
        private final Unit owner;
        private final int offset;
        Site(Sequence sequence, Unit owner, int offset) {
            this.sequence = sequence; this.owner = owner; this.offset = offset;
        }
        public Sequence sequence() { return sequence; }
        public Unit owner() { return owner; }
        public int offset() { return offset; }
        public boolean isTerminator() { return offset == sequence.instructions().size(); }
        public Operation operation() { return isTerminator() ? sequence.terminator() : sequence.instructions().get(offset); }
    }
}
