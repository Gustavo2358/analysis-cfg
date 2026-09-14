package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.math.BigInteger;
import java.util.*;

/** Physical storage facet prepared once and shared by analyses of an immutable session. */
public final class StorageIndex {
    public static final String PROFILE="regional-storage@1";
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
        public Resolution { candidates=List.copyOf(candidates);Objects.requireNonNull(remainder);reasons=List.copyOf(reasons);uncertainties=List.copyOf(uncertainties); }
        public Resolution(List<Candidate> candidates,Scopes.MemoryBound remainder,List<String> reasons) { this(candidates,remainder,reasons,List.of()); }
        public boolean exact() { return remainder instanceof Scopes.NoMemory && candidates.stream().map(Candidate::location).distinct().count()==1; }
    }
    private final AnalysisSession session;
    private final Map<StorageId,Memory.Storage> bases=new LinkedHashMap<>();
    private final Map<ObjectId,Memory.ObjectDeclaration> declarations=new LinkedHashMap<>();
    private final Map<ObjectId,Resolution> objects=new HashMap<>();
    private final Map<StorageId,Set<PremiseId>> separation=new HashMap<>();
    private final Map<ObjectId,List<ObjectId>> aliasDependencies=new HashMap<>();
    private final Set<UncertaintyId> uncertaintyIds=new HashSet<>();
    private long bindingVisits,premiseMembers;
    public StorageIndex(AnalysisSession session) {
        this.session=Objects.requireNonNull(session);
        var publication=session.index().publication();
        publication.uncertainties().forEach(u->uncertaintyIds.add(u.id()));
        for(var base:publication.storage())bases.put(base.header().id(),base);
        for(var unit:publication.units())for(var object:unit.objects())declarations.put(object.id(),object);
        for(var premise:publication.premises())if(premise.assertion() instanceof Proofs.DisjointStorage d)
            for(var id:d.storage()) { separation.computeIfAbsent(id,ignored->new HashSet<>()).add(premise.id());premiseMembers++; }
        // Explicit DFS avoids call-stack depth proportional to an exact-alias chain.
        var dependencies=aliasDependencies;
        for(var object:declarations.values()) {
            var refs=new ArrayList<ObjectId>();var pending=new ArrayDeque<Memory.Binding>();pending.push(object.storage());
            while(!pending.isEmpty()) {
                var b=pending.pop();bindingVisits++;
                if(b instanceof Memory.AliasBinding a)refs.add(a.object());
                else if(b instanceof Memory.AlternativesBinding a)for(var child:a.alternatives())pending.push(child);
            }
            dependencies.put(object.id(),List.copyOf(refs));
        }
        var active=new HashSet<ObjectId>();
        record Frame(ObjectId id,Iterator<ObjectId> dependencies) { }
        for(var root:declarations.keySet()) {
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
    public Resolution resolve(StorageSubject subject) {
        if(subject instanceof StorageSubject.NamedObject named)return object(named.object());
        var range=(StorageSubject.PhysicalRange)subject;
        if(!(bases.get(range.storage()) instanceof Memory.Region region)||!new StorageRange(BigInteger.ZERO,region.extent()).contains(range.range()))throw new IllegalArgumentException("query range outside storage snapshot");
        return new Resolution(List.of(new Candidate(new Location(region.header(),Optional.of(range.range())),Optional.of(range.codec()),List.of(region.header().origin()))),
            region.extent().isPresent()?Scopes.NoMemory.INSTANCE:within(range.storage()),region.extent().isPresent()?List.of():List.of("UNKNOWN_EXTENT"),region.extentUnknown().stream().toList());
    }
    public Set<OriginId> subjectOrigins(StorageSubject subject) {
        return subject instanceof StorageSubject.NamedObject named?objectOrigins(named.object()):Set.of(whole(((StorageSubject.PhysicalRange)subject).storage()).base().origin());
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
        return !separationPremises(a,b).isEmpty();
    }
    public Resolution select(Scopes.MemoryScope scope) {
        var result=new Accumulator();var pending=new ArrayDeque<Scopes.MemoryScope>();pending.push(scope);
        while(!pending.isEmpty()) {
            var s=pending.pop();
            if(s instanceof Scopes.MemoryUnion u){for(var member:u.members())pending.push(member);}
            else if(s instanceof Scopes.ObjectsMemory o){for(var id:o.objects())result.add(object(id));}
            else if(s instanceof Scopes.StorageMemory m){for(var id:m.storage())result.candidates.add(wholeCandidate(id));}
            else if(s instanceof Scopes.AllMemory a) {
                for(var id:bases.keySet())result.candidates.add(wholeCandidate(id));
                if(a.includingEnvironment()){result.bound(new Scopes.WithinMemory(a));result.reasons.add("ENVIRONMENT_STORAGE");}
            } else if(s instanceof Scopes.VisibleMemory v) {
                var unit=session.index().unit(v.unit());if(unit==null)throw new IllegalArgumentException("foreign visible scope");
                for(var object:unit.objects())result.add(object(object.id()));
                for(var id:unit.visibleObjects())result.add(object(id));
                if(v.includingExternal())for(var base:bases.values())if(base.header().visibility()!=Memory.Visibility.PRIVATE || base.header().lifetime()==Memory.Lifetime.EXTERNAL)result.candidates.add(wholeCandidate(base.header().id()));
            }
        }
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
