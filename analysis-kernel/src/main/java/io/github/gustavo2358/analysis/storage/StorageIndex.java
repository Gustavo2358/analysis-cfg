package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.math.BigInteger;
import java.util.*;

/** Physical storage facet prepared once and shared by analyses of an immutable session. */
public final class StorageIndex {
    public static final String PROFILE="regional-storage@1";
    public static final class UngroundedBound extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public UngroundedBound(){super("ungrounded circular location bound");}
    }
    /** Empty range means a whole logical Cell, not zero bytes. */
    public record Location(Memory.StorageHeader base, Optional<StorageRange> range) {
        public Location { Objects.requireNonNull(base);Objects.requireNonNull(range); }
        public ContextualLocation in(EntryId entry) {
            return new ContextualLocation(this,base.lifetime()==Memory.Lifetime.ACTIVATION?Optional.of(entry):Optional.empty());
        }
    }
    public record ContextualLocation(Location location, Optional<EntryId> activation) { }
    public record Candidate(Location location, Optional<Memory.Codec> codec, List<OriginId> origins) {
        public Candidate { Objects.requireNonNull(location); Objects.requireNonNull(codec); origins=List.copyOf(origins); }
    }
    public record Resolution(List<Candidate> candidates, Scopes.MemoryBound remainder, List<String> reasons,List<UncertaintyId> uncertainties) {
        public Resolution { if(!(candidates instanceof WholeCandidates))candidates=List.copyOf(candidates);Objects.requireNonNull(remainder);reasons=List.copyOf(reasons);uncertainties=List.copyOf(uncertainties); }
        public Resolution(List<Candidate> candidates,Scopes.MemoryBound remainder,List<String> reasons) { this(candidates,remainder,reasons,List.of()); }
        public boolean exact() { return remainder instanceof Scopes.NoMemory && (candidates instanceof WholeCandidates?candidates.size()==1:candidates.stream().map(Candidate::location).distinct().count()==1); }
    }
    /** Complete admitted inventory: one unique base per ordinal, no copied headers.
     * Materialization is a caller operation; the borrowed owner checks lifetime
     * on every size/access. Only this private immutable view bypasses copyOf. */
    private static final class WholeCandidates extends AbstractList<Candidate> implements RandomAccess {
        private final List<Memory.Storage> source;
        private final ProgramStore.StorageInventory inventory;
        private final boolean filtered;
        WholeCandidates(List<Memory.Storage> source,ProgramStore.StorageInventory inventory){this(source,inventory,false);}
        WholeCandidates(List<Memory.Storage> source,ProgramStore.StorageInventory inventory,boolean filtered){this.source=Objects.requireNonNull(source);this.inventory=Objects.requireNonNull(inventory);this.filtered=filtered;}
        @Override public int size(){return source.size();}
        @Override public Candidate get(int ordinal){
            var base=source.get(ordinal);
            var range=base instanceof Memory.Region region?Optional.of(new StorageRange(BigInteger.ZERO,region.extent())):Optional.<StorageRange>empty();
            return new Candidate(new Location(base.header(),range),Optional.empty(),List.of(base.header().origin()));
        }
        @Override public void clear(){throw new UnsupportedOperationException("immutable borrowed whole storage scope");}
        BaseAddress address(int ordinal){Objects.checkIndex(ordinal,size());return new BaseAddress(inventory,filtered?inventory.nonEmptyOrdinal(ordinal):ordinal);}
    }
    /** Owner-local descriptor obtained from an admitted immutable catalogue view.
     * It is not an AIR ID; consumers must check the same inventory owner. */
    public record BaseAddress(ProgramStore.StorageInventory owner,int ordinal){
        public BaseAddress{Objects.requireNonNull(owner);Objects.checkIndex(ordinal,owner.size());}
    }
    public static Optional<BaseAddress> address(List<Candidate> candidates,int ordinal){
        Objects.checkIndex(ordinal,candidates.size());return candidates instanceof WholeCandidates whole?Optional.of(whole.address(ordinal)):Optional.empty();
    }
    /** Null means the general candidate/dedup route is required. */
    static List<Candidate> nonEmptyWholeCandidates(Resolution resolution){
        return resolution.candidates() instanceof WholeCandidates whole
            ?new WholeCandidates(whole.inventory.nonEmptyStorage(),whole.inventory,true):null;
    }
    /** Only the private admitted view proves all boundaries are already whole bases. */
    static boolean wholeCandidates(List<Candidate> candidates){return candidates instanceof WholeCandidates;}
    private final AnalysisSession session;
    private final Map<StorageId,Memory.Storage> bases;
    private final Map<ObjectId,Memory.ObjectDeclaration> declarations;
    private final Map<ObjectId,Resolution> objects=new HashMap<>();
    private final Map<StorageId,Set<PremiseId>> separation=new HashMap<>();
    private final Map<ObjectId,List<ObjectId>> aliasDependencies=new LinkedHashMap<>();
    private final Set<UncertaintyId> uncertaintyIds=new HashSet<>();
    private long bindingVisits,premiseMembers;
    public StorageIndex(AnalysisSession session) {
        this.session=Objects.requireNonNull(session);
        var store=session.index().store();
        store.uncertainties().forEach(u->uncertaintyIds.add(u.id()));
        if(store.storageInventory().isPresent())bases=session.index().storageDeclarations();
        else {
            bases=new LinkedHashMap<>();
            for(var base:store.storage())bases.put(base.header().id(),base);
        }
        declarations=session.index().objectDeclarations();
        for(var premise:store.premises())if(premise.assertion() instanceof Proofs.DisjointStorage d)
            for(var id:d.storage()) { separation.computeIfAbsent(id,ignored->new HashSet<>()).add(premise.id());premiseMembers++; }
        // Explicit DFS avoids call-stack depth proportional to an exact-alias chain.
        var dependencies=aliasDependencies;
        for(var declaration:declarations.entrySet()) {
            var object=declaration.getValue();
            var refs=new ArrayList<ObjectId>();var pending=new ArrayDeque<Memory.Binding>();pending.push(object.storage());
            while(!pending.isEmpty()) {
                var b=pending.pop();bindingVisits++;
                if(b instanceof Memory.AliasBinding a)refs.add(a.object());
                else if(b instanceof Memory.AlternativesBinding a)for(var child:a.alternatives())pending.push(child);
            }
            // Borrow the canonical key: native bodies reconstruct complete IDs on each read.
            dependencies.put(declaration.getKey(),List.copyOf(refs));
        }
        var active=new HashSet<ObjectId>();
        record Frame(ObjectId id,Iterator<ObjectId> dependencies) { }
        // Preserve declaration insertion order while borrowing the identities
        // already owned by the required edges, not another cold projection.
        for(var root:dependencies.keySet()) {
            if(objects.containsKey(root))continue;
            var stack=new ArrayDeque<Frame>();stack.push(new Frame(root,dependencies.get(root).iterator()));active.add(root);
            while(!stack.isEmpty()) {
                var frame=stack.peek();
                if(frame.dependencies().hasNext()) {
                    var child=frame.dependencies().next();
                    if(!declarations.containsKey(child))throw new IllegalArgumentException("dangling alias");
                    if(active.contains(child))throw new IllegalArgumentException("cyclic alias");
                    if(!objects.containsKey(child)){active.add(child);stack.push(new Frame(child,dependencies.get(child).iterator()));}
                } else {
                    var object=declarations.get(frame.id());objects.put(frame.id(),binding(object.storage(),object.origin()));
                    stack.pop();active.remove(frame.id());
                }
            }
        }
    }
    public AnalysisSession session() { return session; }
    public Collection<Memory.Storage> bases() { return Collections.unmodifiableCollection(bases.values()); }
    public Collection<Memory.ObjectDeclaration> declarations() { return Collections.unmodifiableCollection(declarations.values()); }
    /** On-demand evidence closure; preparation never retains a copied path per alias. */
    public Set<OriginId> objectOrigins(ObjectId id) {
        var visited=new HashSet<ObjectId>();var pending=new ArrayDeque<ObjectId>();var origins=new LinkedHashSet<OriginId>();pending.push(id);
        while(!pending.isEmpty()) { var next=pending.pop();if(!visited.add(next))continue;
            var declaration=declarations.get(next);if(declaration==null)throw new IllegalArgumentException("foreign object");
            origins.add(declaration.origin());for(var child:aliasDependencies.get(next))pending.push(child);
        }
        return Set.copyOf(origins);
    }
    public Map<String,Long> preparationMetrics() {
        return Map.of("basesIndexed",(long)bases.size(),"objectsResolved",(long)objects.size(),"bindingVisits",bindingVisits,
            "premiseMemberships",premiseMembers,"objectPairsMaterialized",0L);
    }
    public Resolution object(ObjectId object) {
        var resolution=objects.get(object);
        if(resolution==null)throw new IllegalArgumentException("object outside storage snapshot");return resolution;
    }
    /** Query admission is separate from a value interpretation or a proof of source completeness. */
    public boolean supports(StorageSubject subject,UnitId atUnit) {
        var unit=session.index().unit(atUnit);if(unit==null)return false;
        if(subject instanceof StorageSubject.NamedObject named)
            return declarations.containsKey(named.object())&&(named.object().unit().equals(atUnit)||unit.visibleObjects().contains(named.object()));
        if(subject instanceof StorageSubject.PlaceOccurrence p)
            return p.occurrence().owner().unit().equals(atUnit)&&session.index().place(p.occurrence())!=null;
        var range=(StorageSubject.PhysicalRange)subject;
        if(!codecReferences(range.codec())||!(bases.get(range.storage()) instanceof Memory.Region region)||!new StorageRange(BigInteger.ZERO,region.extent()).contains(range.range()))return false;
        var header=region.header();
        if(header.owner().filter(atUnit::equals).isPresent()||header.visibility()==Memory.Visibility.SHARED||header.lifetime()==Memory.Lifetime.EXTERNAL)return true;
        var visible=new ArrayList<ObjectId>(unit.visibleObjects());unit.objects().forEach(o->visible.add(o.id()));
        for(var object:visible)for(var candidate:object(object).candidates())
            if(candidate.location().base().id().equals(range.storage())&&candidate.location().range().filter(r->r.contains(range.range())).isPresent())return true;
        return false;
    }
    private boolean codecReferences(Memory.Codec codec) {
        if(codec instanceof Memory.UnknownCodec unknown)return uncertaintyIds.contains(unknown.reason())&&typeReferences(unknown.logicalType());
        return !(codec instanceof Memory.ExtensionCodec extension)||typeReferences(extension.logicalType());
    }
    private boolean typeReferences(Types.TypeRef type) {
        if(type instanceof Types.UnknownType unknown)return uncertaintyIds.contains(unknown.uncertainty());
        var known=((Types.Known)type).type();
        if(known instanceof Types.LabelType labels)return session.index().unit(labels.unit())!=null
            &&labels.labels().stream().allMatch(id->id.unit().equals(labels.unit())&&session.index().sequence(id)!=null);
        return true;
    }
    /** Canonical value identities only: no alias expansion or address-expression traversal. */
    public List<ObjectId> explicitObjects(StorageSubject subject) {
        if(subject instanceof StorageSubject.NamedObject named)return List.of(named.object());
        if(subject instanceof StorageSubject.PlaceOccurrence occurrence) {
            var place=session.index().place(occurrence.occurrence());
            if(place==null)throw new IllegalArgumentException("foreign place occurrence");
            return explicitObjects(place);
        }
        return List.of();
    }
    public List<ObjectId> explicitObjects(Place root) {
        var objects=new HashSet<ObjectId>();var pending=new ArrayDeque<Place>();pending.push(root);
        while(!pending.isEmpty()) {
            var place=pending.pop();
            if(place instanceof Places.ObjectPlace object)objects.add(object.object());
            else if(place instanceof Places.Choice choice)pending.addAll(choice.candidates());
        }
        return objects.stream().sorted(Comparator.comparing((ObjectId id)->id.publication().localId())
            .thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId)).toList();
    }
    public Resolution resolve(StorageSubject subject) {
        if(subject instanceof StorageSubject.NamedObject named)return object(named.object());
        if(subject instanceof StorageSubject.PlaceOccurrence p) {
            var place=session.index().place(p.occurrence());if(place==null)throw new IllegalArgumentException("foreign place occurrence");
            return resolve(place);
        }
        var range=(StorageSubject.PhysicalRange)subject;
        if(!(bases.get(range.storage()) instanceof Memory.Region region)||!new StorageRange(BigInteger.ZERO,region.extent()).contains(range.range()))throw new IllegalArgumentException("query range outside storage snapshot");
        return new Resolution(List.of(new Candidate(new Location(region.header(),Optional.of(range.range())),Optional.of(range.codec()),List.of(region.header().origin()))),
            region.extent().isPresent()?Scopes.NoMemory.INSTANCE:within(range.storage()),region.extent().isPresent()?List.of():List.of("UNKNOWN_EXTENT"),region.extentUnknown().stream().toList());
    }
    public Set<OriginId> subjectOrigins(StorageSubject subject) {
        if(subject instanceof StorageSubject.NamedObject named)return objectOrigins(named.object());
        if(subject instanceof StorageSubject.PhysicalRange range)return Set.of(whole(range.storage()).base().origin());
        var occurrence=(StorageSubject.PlaceOccurrence)subject;var root=session.index().place(occurrence.occurrence());
        if(root==null)throw new IllegalArgumentException("foreign place occurrence");
        var origins=new LinkedHashSet<OriginId>();var pending=new ArrayDeque<Operand>();pending.push(root);
        while(!pending.isEmpty()) {var operand=pending.pop();origins.add(operand.header().origin());
            if(operand instanceof Places.ObjectPlace object)origins.addAll(objectOrigins(object.object()));
            pending.addAll(Operands.children(operand));}
        return Set.copyOf(origins);
    }
    public Location whole(StorageId id) {
        var base=bases.get(id);if(base==null)throw new IllegalArgumentException("storage outside snapshot");
        return new Location(base.header(),base instanceof Memory.Region r?Optional.of(new StorageRange(BigInteger.ZERO,r.extent())):Optional.empty());
    }
    private Resolution binding(Memory.Binding root,OriginId origin) {
        var result=new Accumulator();var pending=new ArrayDeque<Memory.Binding>();pending.push(root);
        while(!pending.isEmpty()) {
            var b=pending.pop();bindingVisits++;
            if(b instanceof Memory.CellBinding c) {
                var base=bases.get(c.storage());if(!(base instanceof Memory.Cell))throw new IllegalArgumentException("not a cell");
                result.candidates.add(new Candidate(whole(c.storage()),Optional.empty(),List.of(origin)));
            } else if(b instanceof Memory.ViewBinding v)result.add(region(v.region(),v.offset(),v.extent(),v.codec(),origin));
            else if(b instanceof Memory.AliasBinding a) {
                var resolved=Objects.requireNonNull(objects.get(a.object()),"unresolved alias");
                result.add(resolved);
            } else if(b instanceof Memory.AlternativesBinding a) {
                result.bound(a.remainder());for(int i=a.alternatives().size()-1;i>=0;i--)pending.push(a.alternatives().get(i));
            } else if(b instanceof Memory.UnknownBinding u){result.bound(new Scopes.WithinMemory(u.scope()));result.reasons.add("UNKNOWN_BINDING");result.uncertainties.add(u.reason());}
        }
        return result.finish();
    }
    private Resolution region(StorageId id,BigInteger start,BigInteger length,Memory.Codec codec,OriginId origin) {
        if(!(bases.get(id) instanceof Memory.Region base))throw new IllegalArgumentException("not a region");
        var range=StorageRange.exact(start,length);
        if(base.extent().isPresent() && !new StorageRange(BigInteger.ZERO,base.extent()).contains(range))throw new IllegalArgumentException("outside region bounds");
        var candidate=new Candidate(new Location(base.header(),Optional.of(range)),Optional.of(codec),List.of(origin));
        return new Resolution(List.of(candidate),base.extent().isPresent()?Scopes.NoMemory.INSTANCE:within(id),base.extent().isPresent()?List.of():List.of("UNKNOWN_EXTENT"),base.extentUnknown().stream().toList());
    }
    public Resolution resolve(Place place) {
        if(place instanceof Places.ObjectPlace p)return object(p.object());
        if(place instanceof Places.RegionSlice s) {
            var start=constant(s.offset());var length=constant(s.length());
            if(start.isPresent()&&length.isPresent())return region(s.region(),start.get(),length.get(),s.codec(),s.header().origin());
            return new Resolution(List.of(),within(s.region()),List.of("CALCULATED_BOUNDS"));
        }
        var choice=(Places.Choice)place;var result=new Accumulator();
        for(var candidate:choice.candidates())result.add(resolve(candidate));result.bound(choice.remainder());return result.finish();
    }
    public Resolution byteRange(Memory.ByteRange range,BigInteger length,OriginId origin) {
        var start=constant(range.offset());var extent=constant(range.extent());
        if(start.isPresent()&&extent.isPresent()) {
            if(length.signum()<0||length.compareTo(extent.get())>0)throw new IllegalArgumentException("copy exceeds declared range");
            return region(range.region(),start.get(),length,Memory.IdentityBytes.INSTANCE,origin);
        }
        return new Resolution(List.of(),within(range.region()),List.of("CALCULATED_BOUNDS"));
    }
    public static Optional<BigInteger> constant(Expression expression) {
        return expression instanceof Expressions.Literal l && l.value() instanceof Values.IntValue n?Optional.of(n.value()):Optional.empty();
    }
    public Set<PremiseId> separationPremises(Location a,Location b) {
        if(a.base().id().equals(b.base().id()))return Set.of();
        var left=separation.getOrDefault(a.base().id(),Set.of());var right=separation.getOrDefault(b.base().id(),Set.of());
        var result=new HashSet<PremiseId>();for(var id:left)if(right.contains(id))result.add(id);return Set.copyOf(result);
    }
    public boolean disjoint(Location a,Location b) {
        if(a.range().isPresent()&&a.range().get().empty()||b.range().isPresent()&&b.range().get().empty())return true;
        if(a.base().id().equals(b.base().id()))return a.range().isPresent()&&b.range().isPresent()&&a.range().get().intersect(b.range().get()).isEmpty();
        return true; // Distinct StorageIds are independent state bases in the supported model.
    }
    public Resolution select(Scopes.MemoryScope scope) {
        if(scope instanceof Scopes.AllMemory all&&session.index().store().storageInventory().isPresent())
            return new Resolution(new WholeCandidates(session.index().store().storage(),session.index().store().storageInventory().orElseThrow()),
                all.includingEnvironment()?new Scopes.WithinMemory(all):Scopes.NoMemory.INSTANCE,
                all.includingEnvironment()?List.of("ENVIRONMENT_STORAGE"):List.of());
        var result=new Accumulator();
        record Frame(Scopes.MemoryScope scope,boolean exit) { }
        var pending=new ArrayDeque<Frame>();var visiting=new HashSet<Scopes.MemoryScope>();var resolved=new HashSet<Scopes.MemoryScope>();
        pending.push(new Frame(scope,false));boolean cycle=false;
        while(!pending.isEmpty()) {
            var frame=pending.pop();var s=frame.scope();
            if(frame.exit()){visiting.remove(s);resolved.add(s);continue;}
            if(resolved.contains(s))continue;
            if(!visiting.add(s)){cycle=true;continue;}
            pending.push(new Frame(s,true));
            if(s instanceof Scopes.MemoryUnion u){for(var member:u.members())pending.push(new Frame(member,false));}
            else if(s instanceof Scopes.ObjectsMemory o){for(var id:o.objects()) {
                var target=object(id);result.candidates.addAll(target.candidates());result.reasons.addAll(target.reasons());result.uncertainties.addAll(target.uncertainties());
                if(target.remainder() instanceof Scopes.WithinMemory w)pending.push(new Frame(w.scope(),false));
            }}
            else if(s instanceof Scopes.StorageMemory m){for(var id:m.storage())result.candidates.add(wholeCandidate(id));}
            else if(s instanceof Scopes.AllMemory a) {
                for(var id:bases.keySet())result.candidates.add(wholeCandidate(id));
                if(a.includingEnvironment()){result.bound(new Scopes.WithinMemory(a));result.reasons.add("ENVIRONMENT_STORAGE");}
            } else if(s instanceof Scopes.VisibleMemory v) {
                var unit=session.index().unit(v.unit());if(unit==null)throw new IllegalArgumentException("foreign visible scope");
                var ids=new LinkedHashSet<ObjectId>();unit.objects().forEach(o->ids.add(o.id()));ids.addAll(unit.visibleObjects());
                for(var id:ids){var target=object(id);result.candidates.addAll(target.candidates());result.reasons.addAll(target.reasons());result.uncertainties.addAll(target.uncertainties());
                    if(target.remainder() instanceof Scopes.WithinMemory w)pending.push(new Frame(w.scope(),false));}
                if(v.includingExternal())for(var base:bases.values())if(base.header().visibility()!=Memory.Visibility.PRIVATE || base.header().lifetime()==Memory.Lifetime.EXTERNAL)result.candidates.add(wholeCandidate(base.header().id()));
            }
        }
        if(cycle&&result.candidates.isEmpty()&&result.scopes.isEmpty())throw new UngroundedBound();
        return result.finish();
    }
    private Candidate wholeCandidate(StorageId id) { var location=whole(id);return new Candidate(location,Optional.empty(),List.of(location.base().origin())); }
    private static Scopes.MemoryBound within(StorageId id) { return new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(id))); }
    private static final class Accumulator {
        final LinkedHashSet<Candidate> candidates=new LinkedHashSet<>();
        final LinkedHashSet<Scopes.MemoryScope> scopes=new LinkedHashSet<>();
        final LinkedHashSet<String> reasons=new LinkedHashSet<>();
        final LinkedHashSet<UncertaintyId> uncertainties=new LinkedHashSet<>();
        void add(Resolution r){candidates.addAll(r.candidates());bound(r.remainder());reasons.addAll(r.reasons());uncertainties.addAll(r.uncertainties());}
        void bound(Scopes.MemoryBound b){if(b instanceof Scopes.WithinMemory w)scopes.add(w.scope());}
        Resolution finish(){return new Resolution(List.copyOf(candidates),scopes.isEmpty()?Scopes.NoMemory.INSTANCE:new Scopes.WithinMemory(scopes.size()==1?scopes.iterator().next():new Scopes.MemoryUnion(List.copyOf(scopes))),List.copyOf(reasons),List.copyOf(uncertainties));}
    }
}
