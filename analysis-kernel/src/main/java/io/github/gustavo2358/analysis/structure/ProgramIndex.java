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
    final Map<CfgNodeId,LocalControlRules.Rule> localRules;
    final Map<Node,List<Node>> labelTargets = new HashMap<>();
    final Object identity;
    final ProjectionPolicy policy;
    final Node[] nodes;
    final boolean factored;
    final Map<UnitId,Node> returnRepresentatives;
    final CfgTransition[] edges;
    final int[] from, to, entry, forwardNext, backwardNext;
    final LongIntDirectory forwardHeads, backwardHeads;
    final Map<EntryId, Integer> entryOrdinals;
    final Map<EntryId, Node> entryNodes, normalExits;
    private final ProgramStore.Structural store;
    private final Map<CfgNodeId, Node> nodeIds;
    private final Map<UnitId, ProgramStore.UnitView> units;
    private final Map<LabelId, Node> sequences;
    private final Map<OperationId, Site> operations;
    private final Map<Class<? extends Operation>, List<Site>> buckets;
    private final Map<Class<? extends Operation>,Map<UnitId,List<Site>>> unitBuckets=new HashMap<>();
    private final Map<ObjectId, Memory.ObjectDeclaration> objects;
    private final Map<StorageId, Memory.Storage> storage;
    private final Map<ObjectId, Memory.Cell> directCells;
    private final Map<OperandId, Memory.ObjectDeclaration> objectReferences;
    private final Map<OperandId, Place> places;
    private final Map<EntryId, Entries.Entry> entries;
    private final IndexMetrics metrics;
    private final Set<UnitId> partialControlUnits;
    private final Set<OperationId> unprovedPreconditions;
    private final Set<UnitId> unprovedPreconditionUnits;

    ProgramIndex(IndexBuilder b) {
        identity = b.identity;
        localRules = b.localRules;
        policy = b.policy;
        unprovedPreconditions=b.unprovedPreconditions;
        unprovedPreconditionUnits=unprovedPreconditions.stream().map(OperationId::unit).collect(java.util.stream.Collectors.toUnmodifiableSet());
        store = b.store;
        nodes = b.nodes;
        var partialUnits=new HashSet<UnitId>();
        for (var node : nodes) {
            if(OpenControl.partial(node,policy))partialUnits.add(node.owner().id());
            unitNodes.computeIfAbsent(node.owner().id(), ignored -> new ArrayList<>()).add(node);
            if (OpenControl.bound(node,policy) instanceof Scopes.WithinControl)
                openSources.computeIfAbsent(node.owner().id(), ignored -> new ArrayList<>()).add(node);
        }
        partialControlUnits=Set.copyOf(partialUnits);
        unitNodes.replaceAll((u, list) -> List.copyOf(list)); openSources.replaceAll((u, list) -> List.copyOf(list));
        factored=b.factored;returnRepresentatives=b.returnRepresentatives;
        edges = b.edges;
        from = b.from; to = b.to; entry = b.edgeEntry;
        forwardNext = b.forwardNext; backwardNext = b.backwardNext;
        forwardHeads = b.forwardHeads; backwardHeads = b.backwardHeads;
        nodeIds = b.nodeIds; units = b.units; sequences = b.sequenceNodes;
        // Resolve finite label bounds once. Preserve the original CFG node order and set semantics.
        for (var sources : openSources.values()) for (var source : sources) {
            if (OpenControl.bound(source,policy) instanceof Scopes.WithinControl within
                    && within.scope() instanceof Scopes.LabelsControl labels) {
                labelTargets.put(source, labels.labels().stream().map(sequences::get).filter(Objects::nonNull)
                        .filter(n -> n.owner().id().equals(source.owner().id())).distinct()
                        .sorted(Comparator.comparingInt(n -> n.ordinal)).toList());
            }
        }
        operations = b.operations; objects = b.objects; storage = b.storage;
        directCells = b.directCells; objectReferences = b.objectReferences;places=b.places;
        entries = b.entries; entryOrdinals = b.entryOrdinals;
        entryNodes = b.entryNodes; normalExits = b.normalExits;
        // Builder ownership is transferred. No builder or mutable collection escapes.
        b.buckets.replaceAll((kind, sites) -> {
            b.count.visit("buckets.freeze");
            return Collections.unmodifiableList(sites);
        });
        buckets = b.buckets;
        // Partition the canonical site handles once, without retaining another
        // operation payload. Entry-scoped admission must not reopen every Unit.
        for(var bucket:buckets.entrySet()) {
            var owners=new HashMap<UnitId,List<Site>>();
            for(var site:bucket.getValue())owners.computeIfAbsent(site.owner().id(),ignored->new ArrayList<>()).add(site);
            owners.replaceAll((unit,sites)->Collections.unmodifiableList(sites));
            unitBuckets.put(bucket.getKey(),owners);
        }
        metrics = b.count.snapshot();
    }

    public boolean unprovedPreconditions(OperationId operation) { return unprovedPreconditions.contains(operation); }
    public boolean unprovedPreconditions(UnitId unit) { return unprovedPreconditionUnits.contains(unit); }
    public boolean hasUnprovedPreconditions() { return !unprovedPreconditions.isEmpty(); }
    public boolean partialControl(UnitId unit) { return partialControlUnits.contains(unit); }
    public ProgramStore.Structural store() { return store; }
    public IndexMetrics metrics() { return metrics; }
    /** Null means ID absent from this snapshot; IDs always include their owners. */
    public Node node(CfgNodeId id) { return nodeIds.get(id); }
    public Node sequence(LabelId id) { return sequences.get(id); }
    public ProgramStore.UnitView unit(UnitId id) { return units.get(id); }
    public Entries.Entry entry(EntryId id) { return entries.get(id); }
    public Site site(OperationId id) { return operations.get(id); }
    /** Immutable source occurrence, not Java allocation identity; rejects changed same-ID payloads. */
    public void requireOperation(Operation operation) {
        Objects.requireNonNull(operation);
        var site=operations.get(operation.header().id());
        if(site==null||!site.operation().equals(operation))throw new IllegalArgumentException("operation outside indexed snapshot");
    }
    public List<Site> sites(Class<? extends Operation> kind) { return buckets.getOrDefault(kind, List.of()); }
    /** Same shared handles and AIR order, partitioned by the full nominal owner ID. */
    public List<Site> sites(Class<? extends Operation> kind,UnitId owner) {
        return unitBuckets.getOrDefault(kind,Map.of()).getOrDefault(owner,List.of());
    }
    public Memory.ObjectDeclaration object(ObjectId id) { return objects.get(id); }
    /** Already correlated declarations in AIR inventory order; no store access or payload copy. */
    public Collection<Memory.ObjectDeclaration> objects() { return Collections.unmodifiableCollection(objects.values()); }
    public Memory.Storage storage(StorageId id) { return storage.get(id); }
    /** Direct whole Cell association only; null does not assert absence of indirect storage. */
    public Memory.Cell directCell(ObjectId id) { return directCells.get(id); }
    public Place place(OperandId occurrence){return places.get(occurrence);}
    /** Pre-resolved ObjectPlace occurrence, including nested operands and Entry initial conditions. */
    public Memory.ObjectDeclaration referencedObject(OperandId occurrence) { return objectReferences.get(occurrence); }
    /** Owner-checked body address; no resident AIR Sequence is required. */
    public ProgramStore.SequenceView sequence(Node node) {
        if (node.identity != identity) throw new IllegalArgumentException("foreign node");
        return node.sequence;
    }

    /** Opaque index handle. Its private ordinal never becomes an AIR/CFG identity or public result. */
    public static final class Node {
        final Object identity;
        final int ordinal;
        private final CfgNode source;
        private final ProgramStore.UnitView owner;
        private final ProgramStore.SequenceView sequence;
        Node(Object identity, int ordinal, CfgNode source, ProgramStore.UnitView owner, ProgramStore.SequenceView sequence) {
            this.identity = identity; this.ordinal = ordinal; this.source = source; this.owner = owner;
            this.sequence = sequence;
        }
        public CfgNode source() { return source; }
        public ProgramStore.UnitView owner() { return owner; }
    }

    /** One retained site per operation, reused by ID lookup and kind buckets; no per-query wrappers. */
    public static final class Site {
        private final ProgramStore.SequenceView sequence;
        private final ProgramStore.UnitView owner;
        private final int offset;
        Site(ProgramStore.SequenceView sequence, ProgramStore.UnitView owner, int offset) {
            this.sequence = sequence; this.owner = owner; this.offset = offset;
        }
        public ProgramStore.SequenceView sequence() { return sequence; }
        public ProgramStore.UnitView owner() { return owner; }
        public int offset() { return offset; }
        public boolean isTerminator() { return offset == sequence.instructions().size(); }
        public Operation operation() { return isTerminator() ? sequence.terminator() : sequence.instructions().get(offset); }
    }
}
