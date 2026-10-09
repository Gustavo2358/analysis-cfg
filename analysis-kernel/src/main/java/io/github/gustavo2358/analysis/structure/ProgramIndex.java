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
    private final Map<OperandId, ObjectId> objectReferences;
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
        operations = b.operations; b.objects.freeze();objects = b.objects; b.storage.freeze();storage = b.storage;
        if(b.directCells instanceof CellAssociations cells)cells.freeze();
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
    /** Canonical AIR inventory order. Native access borrows one declaration from its owner;
     * resident callers retain their original immutable instances. No owning body catalog. */
    public Collection<Memory.ObjectDeclaration> objects() { return Collections.unmodifiableCollection(objects.values()); }
    /** Same immutable declaration addresses, not another catalog of decoded AIR bodies. */
    public Map<ObjectId,Memory.ObjectDeclaration> objectDeclarations(){return Collections.unmodifiableMap(objects);}
    public Memory.Storage storage(StorageId id) { return storage.get(id); }
    /** Same complete immutable catalogue used during structural admission. */
    public Map<StorageId,Memory.Storage> storageDeclarations(){return Collections.unmodifiableMap(storage);}
    /** Direct whole Cell association only; null does not assert absence of indirect storage. */
    public Memory.Cell directCell(ObjectId id) { return directCells.get(id); }
    public Place place(OperandId occurrence){return places.get(occurrence);}
    /** Pre-resolved ObjectPlace occurrence, including nested operands and Entry initial conditions. */
    public Memory.ObjectDeclaration referencedObject(OperandId occurrence) { return objects.get(objectReferences.get(occurrence)); }
    /** Owner-checked body address; no resident AIR Sequence is required. */
    public ProgramStore.SequenceView sequence(Node node) {
        if (node.identity != identity) throw new IllegalArgumentException("foreign node");
        return node.sequence;
    }

    /** Native storage borrows the admitted directory; resident callers keep their original bodies. */
    static final class Storages extends AbstractMap<StorageId,Memory.Storage> {
        private final Map<StorageId,Memory.Storage> resident=new HashMap<>();
        private ProgramStore.StorageInventory inventory;
        private int registered;private boolean frozen;
        void connect(Optional<ProgramStore.StorageInventory> inventory){
            if(frozen||registered!=0||!resident.isEmpty())throw new IllegalStateException("storage inventory already started");
            this.inventory=Objects.requireNonNull(inventory).orElse(null);
        }
        boolean append(StorageId id,Memory.Storage value){
            if(frozen)throw new IllegalStateException("storage inventory is frozen");
            if(inventory!=null){
                if(!inventory.identityAt(registered,Objects.requireNonNull(id)))return false;
                registered=Math.incrementExact(registered);return true;
            }
            return resident.putIfAbsent(Objects.requireNonNull(id),Objects.requireNonNull(value))==null;
        }
        void freeze(){if(inventory!=null&&registered!=inventory.size())throw new IllegalStateException("changed Storage inventory");frozen=true;}
        @Override public int size(){return inventory==null?resident.size():inventory.size();}
        @Override public Memory.Storage get(Object key){return inventory==null?resident.get(key):inventory.get(key);}
        @Override public boolean containsKey(Object key){return inventory==null?resident.containsKey(key):inventory.containsKey(key);}
        @Override public Set<Entry<StorageId,Memory.Storage>> entrySet(){return Collections.unmodifiableSet(inventory==null?resident.entrySet():inventory.entrySet());}
    }

    /** Identity inventory owns only immutable list addresses. Native payloads remain cold;
     * explicit resident callers keep their original declaration instances and AIR order. */
    static final class Declarations extends AbstractMap<ObjectId,Memory.ObjectDeclaration> {
        private record Address(List<Memory.ObjectDeclaration> values,int ordinal) { }
        private final Map<ObjectId,Address> addresses=new LinkedHashMap<>();
        private ProgramStore.DeclarationInventory inventory;
        private int registered;
        private boolean frozen;
        Declarations(){this(Optional.empty());}
        Declarations(Optional<ProgramStore.DeclarationInventory> inventory){this.inventory=inventory.orElse(null);}
        void connect(Optional<ProgramStore.DeclarationInventory> inventory){
            if(frozen||registered!=0||!addresses.isEmpty())throw new IllegalStateException("declaration inventory already started");
            this.inventory=Objects.requireNonNull(inventory).orElse(null);
        }
        boolean nativeBacked(){return inventory!=null;}
        boolean append(ObjectId id,List<Memory.ObjectDeclaration> values,int ordinal) {
            if(frozen)throw new IllegalStateException("declaration inventory is frozen");
            if(inventory!=null){
                if(!inventory.identityAt(registered,Objects.requireNonNull(id)))return false;
                registered=Math.incrementExact(registered);return true;
            }
            return addresses.putIfAbsent(Objects.requireNonNull(id),new Address(values,ordinal))==null;
        }
        void freeze(){if(inventory!=null&&registered!=inventory.size())throw new IllegalStateException("changed Object inventory");frozen=true;}
        @Override public int size(){return inventory==null?addresses.size():inventory.size();}
        @Override public boolean containsKey(Object key){return inventory==null?addresses.containsKey(key):inventory.containsKey(key);}
        @Override public Set<ObjectId> keySet(){return Collections.unmodifiableSet(inventory==null?addresses.keySet():inventory.keySet());}
        @Override public Memory.ObjectDeclaration get(Object key) {
            if(inventory!=null)return inventory.get(key);
            var address=addresses.get(key);if(address==null)return null;
            var value=address.values().get(address.ordinal());
            if(!value.id().equals(key))throw new IllegalStateException("changed indexed Object identity");
            return value;
        }
        @Override public Set<Entry<ObjectId,Memory.ObjectDeclaration>> entrySet() {
            if(inventory!=null)return Collections.unmodifiableSet(inventory.entrySet());
            return Collections.unmodifiableSet(new AbstractSet<>() {
                @Override public int size(){return addresses.size();}
                @Override public Iterator<Entry<ObjectId,Memory.ObjectDeclaration>> iterator() {
                    var keys=addresses.keySet().iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext(){return keys.hasNext();}
                        @Override public Entry<ObjectId,Memory.ObjectDeclaration> next(){var key=keys.next();return new SimpleImmutableEntry<>(key,get(key));}
                    };
                }
            });
        }
    }

    /** Native Cell associations are derived from the immutable cold declaration;
     * no second per-Object identity inventory is needed. Required checks/counts
     * still execute in IndexBuilder before ownership transfers. */
    static final class CellAssociations extends AbstractMap<ObjectId,Memory.Cell> {
        private final Declarations declarations;
        private final Map<StorageId,Memory.Storage> storage;
        private int count;private boolean frozen;
        CellAssociations(Declarations declarations,Map<StorageId,Memory.Storage> storage){this.declarations=declarations;this.storage=storage;}
        void include(){if(frozen)throw new IllegalStateException("Cell associations are frozen");count=Math.incrementExact(count);}
        void freeze(){frozen=true;}
        private Memory.Cell cell(Memory.ObjectDeclaration object){
            if(object==null||!(object.storage() instanceof Memory.CellBinding binding))return null;
            var value=storage.get(binding.storage());return value instanceof Memory.Cell cell?cell:null;
        }
        @Override public int size(){declarations.size();return count;}
        @Override public Memory.Cell get(Object key){return cell(declarations.get(key));}
        @Override public boolean containsKey(Object key){return get(key)!=null;}
        @Override public Set<Entry<ObjectId,Memory.Cell>> entrySet(){return Collections.unmodifiableSet(new AbstractSet<>() {
            @Override public int size(){return CellAssociations.this.size();}
            @Override public Iterator<Entry<ObjectId,Memory.Cell>> iterator(){
                var source=declarations.entrySet().iterator();return new Iterator<>() {
                    private Entry<ObjectId,Memory.Cell> next;
                    @Override public boolean hasNext(){
                        declarations.size();
                        while(next==null&&source.hasNext()){
                            var declaration=source.next();var cell=cell(declaration.getValue());
                            if(cell!=null)next=new SimpleImmutableEntry<>(declaration.getKey(),cell);
                        }
                        return next!=null;
                    }
                    @Override public Entry<ObjectId,Memory.Cell> next(){
                        if(!hasNext())throw new NoSuchElementException();var value=next;next=null;return value;
                    }
                };
            }
        });}
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
