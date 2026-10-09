package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.DefinitionEvent;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.*;
import java.math.BigInteger;
import java.util.*;

/** Factorized relations over finite storage segments; canonical effects and the shared solver/replayer. */
public final class RegionalValuesAnalysis {
    public static final String PROFILE="regional-text-images@2";
    static final Comparator<ObjectId> OBJECT_ORDER=Comparator.comparing((ObjectId id)->id.unit().publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId);
    private final AnalysisSession session;
    private final StorageAnalysisMode mode;
    private final Map<StorageId,List<ObjectId>> logicalCellAliases;
    private final StatementEffects effects;
    private final StoragePartition partition;
    private final List<StorageIndex.Location> bases;
    private final Map<StorageId,Integer> residentOrdinals=new HashMap<>();
    private final NativeMetadata nativeMetadata;
    private final List<List<Integer>> groups;
    private final List<Integer> groupOf,groupSizes;
    private final List<List<Integer>> groupSegments;
    private final Map<StoragePartition.Segment,Integer> residentLevels=new HashMap<>();
    private final Map<StatementEffects.Write,StorageIndex.Resolution> preparedReads=new IdentityHashMap<>();
    // Each compiled batch retains all writes, including scoped writes without logical candidates.
    private final Map<List<Plan>,List<StatementEffects.Write>> batchWrites=new IdentityHashMap<>();
    private final Map<StatementEffects.Write,Set<ObjectId>> closureImpacts=new IdentityHashMap<>();
    private final Map<OperationId,List<Plan>> operations=new HashMap<>();
    private final Map<OperationId,Map<Control.OutcomeKey,List<Plan>>> outcomes=new HashMap<>();
    private final Map<OperationId,List<Plan>> otherwise=new HashMap<>();
    private final Map<EntryId,List<Plan>> initial=new HashMap<>();
    private final List<ValueFact.Support> events=new ArrayList<>();
    private final List<PreparedEvent> eventDetails=new ArrayList<>();
    private final Set<UnitId> controlOpen=new HashSet<>();
    private record TargetRef(StatementEffects.Write write,int ordinal,StatementEffects.Target replacement) {
        StatementEffects.Target value(){return replacement!=null?replacement:write.targets().get(ordinal);}
        Optional<StorageIndex.BaseAddress> address(){return replacement!=null?Optional.empty():StatementEffects.address(write,ordinal);}
        boolean sourceApplicable(){return replacement!=null?replacement.sourceApplicable():StatementEffects.sourceApplicable(write,ordinal);}
        Optional<StorageRange> range(){
            var address=address();if(address.isEmpty())return value().location().range();var source=address.orElseThrow();
            return source.owner().regionAt(source.ordinal())?Optional.of(new StorageRange(BigInteger.ZERO,source.owner().extentAt(source.ordinal()))):Optional.empty();
        }
    }
    private record Plan(StatementEffects.Write write,TargetRef reference,int event,StatementEffects.LogicalTarget logical) {
        StatementEffects.Target target(){return reference==null?null:reference.value();}
    }
    private record LogicalValue(LogicalText text,int event) {
        LogicalValue(Values.TextValue text,int event){this(LogicalText.of(text.value()),event);}
    }
    private record PreparedEvent(StatementEffects.OperationRef occurrence,Entries.InitialCondition initial,StatementEffects.Write write,TargetRef reference,Optional<Control.OutcomeKey> outcome,StatementEffects.LogicalTarget logical) {
        StatementEffects.Target target(){return reference==null?null:reference.value();}
        Operation operation(){return occurrence==null?null:occurrence.value();}
        DefinitionEvent definition(EntryId entry) {
            var operation=operation();
            if(logical!=null)return operation==null?DefinitionEvent.logicalInitial(entry,initial,write.slot(),logical.object(),write.destination()):DefinitionEvent.logicalWrite(entry,operation,write,logical,outcome);
            return operation==null?DefinitionEvent.initial(entry,initial,write.slot(),target(),write.destination()):DefinitionEvent.write(entry,operation,write,target(),outcome);
        }
    }
    private sealed interface Content permits Bytes,Scalar { }
    private record Bytes(ByteImage image) implements Content { }
    private record ReadCapture(Map<Integer,Content> contents,Map<StatementEffects.Write,Integer> choices) { }
    /** A private evidence address, not another owning copy of an AIR header. */
    private static final class LocationRef {
        private final ProgramStore.StorageInventory owner;
        private final int ordinal;
        private final Optional<StorageRange> range;
        private final StorageIndex.Location resident;
        LocationRef(StorageIndex.Location resident){this.resident=Objects.requireNonNull(resident);owner=null;ordinal=-1;range=resident.range();}
        LocationRef(StorageIndex.BaseAddress address,Optional<StorageRange> range){owner=address.owner();ordinal=address.ordinal();this.range=Objects.requireNonNull(range);resident=null;}
        LocationRef borrow(StorageIndex.Location location){
            if(owner==null)return new LocationRef(location);
            int at=owner.ordinal(location.base().id());if(at<0)throw new IllegalArgumentException("foreign trace location");
            return new LocationRef(new StorageIndex.BaseAddress(owner,at),location.range());
        }
        StorageIndex.Location value(){return owner==null?resident:new StorageIndex.Location(owner.at(ordinal).header(),range);}
        Optional<StorageRange> range(){return range;}
        StorageIndex.ContextualLocation in(EntryId entry){return value().in(entry);}
        @Override public boolean equals(Object other){
            return this==other||other instanceof LocationRef reference&&owner==reference.owner
                &&(owner==null?resident.equals(reference.resident):ordinal==reference.ordinal&&range.equals(reference.range));
        }
        // Never hash the inventory: Map.hashCode would decode every cold header.
        // Omitting owner from the hash is legal; equality still checks owner identity.
        @Override public int hashCode(){return owner==null?resident.hashCode():31*(31+ordinal)+range.hashCode();}
    }
    private LocationRef reference(StorageIndex.Location location){
        if(nativeMetadata==null)return new LocationRef(location);
        int source=nativeMetadata.inventory.ordinal(location.base().id());if(source<0)throw new IllegalArgumentException("foreign trace location");
        return new LocationRef(new StorageIndex.BaseAddress(nativeMetadata.inventory,source),location.range());
    }
    private record CapturedRead(LocationRef sourceRange,LocationRef sourceContribution,LocationRef destinationContribution) { }
    private record Trace(LocationRef observed,Optional<Values.BytesValue> bytes,int producer,Optional<LocationRef> original,
                         Map<Integer,Set<CapturedRead>> captures,Set<Integer> gaps,Set<String> reasons,Set<ByteImage.LogicalSupport> logicalSupports) {
        Trace(LocationRef observed,Optional<Values.BytesValue> bytes,int producer,Optional<LocationRef> original,Map<Integer,Set<CapturedRead>> captures,Set<Integer> gaps,Set<String> reasons){this(observed,bytes,producer,original,captures,gaps,reasons,Set.of());}
        Trace {
            logicalSupports=Set.copyOf(logicalSupports);
            var immutable=new HashMap<Integer,Set<CapturedRead>>();captures.forEach((key,value)->immutable.put(key,Set.copyOf(value)));captures=Map.copyOf(immutable);gaps=Set.copyOf(gaps);reasons=Set.copyOf(reasons);
        }
        Trace withGap(int gap){var next=new HashSet<>(gaps);next.add(gap);return new Trace(observed,bytes,producer,original,captures,next,reasons,logicalSupports);}
        Trace captured(int event,StorageIndex.Location source,StorageIndex.Location destination) {
            var next=new HashMap<>(captures);var contributions=new HashSet<>(next.getOrDefault(event,Set.of()));
            var target=observed.borrow(destination);
            contributions.add(new CapturedRead(observed.borrow(source),observed,target));next.put(event,Set.copyOf(contributions));
            return new Trace(target,bytes,producer,original,next,gaps,reasons,logicalSupports);
        }
    }
    private record Scalar(Optional<LogicalText> text,Set<Integer> producers,Set<String> reasons,Set<Integer> sourceGaps,List<Trace> traces) implements Content {
        Scalar { producers=Set.copyOf(producers);reasons=Set.copyOf(reasons);sourceGaps=Set.copyOf(sourceGaps);traces=List.copyOf(traces); }
        Scalar captured(int event,StorageIndex.Location source,StorageIndex.Location destination) {
            return new Scalar(text,producers,reasons,sourceGaps,traces.stream().map(t->t.captured(event,source,destination)).toList());
        }
    }
    public enum Status { ACCEPTED, UNSUPPORTED, INVALID_INPUT }
    public record Admission(Status status,String reason,Optional<RegionalValuesAnalysis> analysis) { }
    public static Admission prepare(AnalysisSession session) { return prepare(session,StorageAnalysisMode.LOGICAL_ONLY); }
    public static Admission prepare(AnalysisSession session,StorageAnalysisMode mode) {
        Objects.requireNonNull(mode);
        var effects=new StatementEffects(new StorageIndex(session));
        // Partition is structural preparation; do not compile a discarded RD engine to obtain it.
        return new Admission(Status.ACCEPTED,null,Optional.of(new RegionalValuesAnalysis(effects,new StoragePartition(effects),mode)));
    }
    private RegionalValuesAnalysis(StatementEffects effects,StoragePartition partition,StorageAnalysisMode mode) {
        this.mode=mode;
        this.effects=effects;this.partition=partition;session=effects.storage().session();
        logicalCellAliases=effects.storage().declarations().stream().filter(o->o.storage() instanceof Memory.CellBinding)
            .collect(java.util.stream.Collectors.groupingBy(o->((Memory.CellBinding)o.storage()).storage(),
                java.util.stream.Collectors.mapping(Memory.ObjectDeclaration::id,java.util.stream.Collectors.toList())));
        var inventory=mode.physical()?session.index().store().storageInventory():Optional.<ProgramStore.StorageInventory>empty();
        nativeMetadata=inventory.isPresent()?new NativeMetadata(inventory.orElseThrow(),partition):null;
        bases=nativeMetadata!=null?nativeMetadata.bases:mode.physical()?effects.storage().bases().stream().map(b->effects.storage().whole(b.header().id()))
            .sorted(Comparator.comparing((StorageIndex.Location l)->l.base().id().localId())).toList():List.of();
        if(nativeMetadata==null)for(int i=0;i<bases.size();i++)residentOrdinals.put(bases.get(i).base().id(),i);
        // Partition ordinals follow the AIR storage inventory. DAG variable order must
        // instead be canonical, including allocation/work metrics in the public wire.
        if(nativeMetadata==null)for(var base:bases)for(var segment:partition.intersecting(base))residentLevels.put(segment,residentLevels.size());
        for(var statement:effects.statements()) {
            var op=statement.operation();operations.put(op.header().id(),compile(statement.writes(),op.header().id(),op.header().origin(),List.of(),statement.reference(),null,Optional.of(Control.NormalOutcome.INSTANCE)));
            otherwise.put(op.header().id(),compile(statement.otherwise(),op.header().id(),op.header().origin(),List.of(),statement.reference(),null,Optional.empty()));
            var choices=new HashMap<Control.OutcomeKey,List<Plan>>();
            statement.outcomes().forEach((key,writes)->choices.put(key,compile(writes,op.header().id(),op.header().origin(),List.of(),statement.reference(),null,Optional.of(key))));
            outcomes.put(op.header().id(),Map.copyOf(choices));
            if(op instanceof Operations.Invoke i&&i.outcomes().remainder() instanceof Scopes.WithinControl)
                controlOpen.add(op.header().id().unit());
        }
        // Coverage describes the projection, never a write, target, or propagated image.
        for(var unit:session.index().store().units())if(session.index().partialControl(unit.id()) || session.index().unprovedPreconditions(unit.id()))controlOpen.add(unit.id());
        for(var context:session.contexts()) {
            var seeds=new ArrayList<Plan>();int slot=0;var seededLocations=new HashSet<StorageIndex.Location>();
            // Simultaneous strong facts initialize first; possible support and open entry
            // content then widen that boundary. Input order is not overwrite authority.
            for(var fact:EntryFacts.admitted(effects.storage(),context.entry())) {
                var condition=fact.condition();var resolution=fact.resolution();
                var sources=new ArrayList<StatementEffects.Source>();
                boolean possible=condition.value() instanceof Entries.PossibleLiterals;
                if(condition.value() instanceof Entries.PossibleLiterals p)
                    p.candidates().forEach(l->sources.add(new StatementEffects.ExpressionSource(l)));
                else if(condition.value() instanceof Entries.LiteralInitial l)sources.add(new StatementEffects.ExpressionSource(l.value()));
                else sources.add(new StatementEffects.UnknownSource("ENTRY_CONTENT_NOT_LITERAL"));
                var strength=fact.strength();
                for(var source:sources) {
                    boolean logical=possible&&condition.place() instanceof Places.ObjectPlace&&!resolution.exact();
                    var write=new StatementEffects.Write(slot++,Optional.of(condition.place().header().id()),resolution,source,logical?List.of():effects.targets(resolution,strength),StatementEffects.Selection.SINGLE_DESTINATION,strength,
                        logical?List.of(new StatementEffects.LogicalTarget(((Places.ObjectPlace)condition.place()).object(),true)):List.of());
                    for(var plan:compile(List.of(write),condition.place().header().id(),condition.origin(),condition.premises(),null,condition,Optional.empty())) {
                        if(plan.logical()!=null){seeds.add(plan);continue;}
                        var target=plan.target();
                        var reference=plan.reference();
                        // Equal simultaneous strong literals retain both supports; a possible
                        // entry uses MAY from the outset, preserving unspecified entry content.
                        var selectedWrite=plan.write();
                        if(target.location().range().isEmpty()&&target.strength()==StatementEffects.Strength.MUST&&!seededLocations.add(target.location())) {
                            target=new StatementEffects.Target(target.location(),StatementEffects.Strength.MAY,target.sourceApplicable(),target.premises(),target.reasons());
                            selectedWrite=new StatementEffects.Write(selectedWrite.slot(),selectedWrite.occurrence(),selectedWrite.destination(),selectedWrite.source(),selectedWrite.targets(),selectedWrite.selection(),StatementEffects.Strength.MAY);
                            reference=new TargetRef(selectedWrite,reference.ordinal(),target);
                        }
                        seeds.add(new Plan(selectedWrite,reference,plan.event(),null));
                    }
                }
            }
            var batch=Collections.unmodifiableList(new ArrayList<>(seeds));
            var seedWrites=new OccurrenceMap<Boolean>();for(var plan:seeds)seedWrites.put(plan.write(),Boolean.TRUE);
            batchWrites.put(batch,List.copyOf(seedWrites.keySet()));
            initial.put(context.entry().id(),batch);
        }
        // Static read/write connectivity preserves branch correlations before the first copy executes.
        try(var parent=nativeMetadata!=null?nativeMetadata.column(bases.size()):ProgramStore.residentColumn(bases.size())){
            for(int i=0;i<bases.size();i++)parent.set(i,i);
            for(var batch:operations.values())connect(parent,batch);
            for(var batch:otherwise.values())connect(parent,batch);
            for(var choices:outcomes.values())for(var batch:choices.values())connect(parent,batch);
            for(var batch:initial.values())connect(parent,batch);
            GroupDirectory directory;
            if(nativeMetadata!=null)directory=nativeMetadata.groupDirectory(parent);
            else{
                var components=new TreeMap<Integer,List<Integer>>();
                for(int i=0;i<bases.size();i++)components.computeIfAbsent(representative(parent,i),ignored->new ArrayList<>()).add(i);
                var rows=components.values().stream().map(List::copyOf).toList();
                var owners=new ArrayList<Integer>(Collections.nCopies(bases.size(),0));
                for(int group=0;group<rows.size();group++)for(int rank:rows.get(group))owners.set(rank,group);
                directory=new GroupDirectory(rows,List.copyOf(owners),rows.stream().map(List::size).toList());
            }
            groups=directory.groups();groupOf=directory.owners();groupSizes=directory.sizes();
            groupSegments=nativeMetadata!=null?nativeMetadata.groupSegments(groups):groups.stream().map(group->group.stream().flatMap(i->partition.intersecting(bases.get(i)).stream())
                .sorted(Comparator.comparingInt(this::level)).map(StoragePartition.Segment::ordinal).toList()).toList();
        }catch(RuntimeException|Error failure){if(nativeMetadata!=null)nativeMetadata.release(failure);throw failure;}
    }
    private record GroupDirectory(List<List<Integer>> groups,List<Integer> owners,List<Integer> sizes) { }
    private void connect(ProgramStore.OrdinalColumn parent,List<Plan> plans){
        for(var plan:plans)if(plan.logical==null&&plan.reference.sourceApplicable()) {
            var reads=readSource(plan.write);
            if(reads!=null)for(var source:reads.candidates()) {
                int a=representative(parent,ordinal(source.location().base().id()));
                int b=representative(parent,ordinal(plan));parent.set(Math.max(a,b),Math.min(a,b));
            }
        }
    }
    private int ordinal(StorageId identity){
        if(nativeMetadata!=null)return nativeMetadata.ordinal(identity);
        return Objects.requireNonNull(residentOrdinals.get(identity),"foreign storage base");
    }
    private int ordinal(Plan plan){
        if(nativeMetadata!=null){var address=plan.reference.address();if(address.isPresent())return nativeMetadata.ordinal(address.orElseThrow());}
        return ordinal(plan.target().location().base().id());
    }
    private List<Integer> segmentOrdinals(Plan plan){
        if(nativeMetadata!=null){var address=plan.reference.address();if(address.isPresent())return partition.ordinals(address.orElseThrow(),plan.reference.range());}
        return partition.intersecting(plan.target().location()).stream().map(StoragePartition.Segment::ordinal).toList();
    }
    /** The compiler's canonical Write occurrence is reused across its target plans.
     * Preserve first-seen occurrence order without hashing all cold targets per plan. */
    private static final class OccurrenceMap<V> extends AbstractMap<StatementEffects.Write,V> {
        private final IdentityHashMap<StatementEffects.Write,V> values=new IdentityHashMap<>();
        private final List<StatementEffects.Write> order=new ArrayList<>();
        @Override public V get(Object key){return values.get(key);}
        @Override public boolean containsKey(Object key){return values.containsKey(key);}
        @Override public V put(StatementEffects.Write key,V value){if(!values.containsKey(key))order.add(key);return values.put(key,value);}
        @Override public int size(){return order.size();}
        @Override public Set<Entry<StatementEffects.Write,V>> entrySet(){return new AbstractSet<>(){
            @Override public int size(){return order.size();}
            @Override public Iterator<Entry<StatementEffects.Write,V>> iterator(){var rows=order.iterator();return new Iterator<>(){
                @Override public boolean hasNext(){return rows.hasNext();}
                @Override public Entry<StatementEffects.Write,V> next(){var key=rows.next();return new SimpleImmutableEntry<>(key,values.get(key));}
            };}
        };}
    }
    private int level(StoragePartition.Segment segment){
        if(nativeMetadata!=null)return level(segment.ordinal());
        return Objects.requireNonNull(residentLevels.get(segment),"foreign storage segment");
    }
    private int level(int ordinal){
        if(nativeMetadata!=null)return Math.toIntExact(nativeMetadata.segmentLevels.get(ordinal));
        return Objects.requireNonNull(residentLevels.get(partition.segments().get(ordinal)),"foreign storage segment");
    }
    /** Canonical base/segment order is working metadata owned by the admitted source.
     * No full StorageId, header or Segment is cached by these private columns. */
    private static final class NativeMetadata {
        private final ProgramStore.StorageInventory inventory;
        private final StoragePartition partition;
        private final List<ProgramStore.OrdinalColumn> owned=new ArrayList<>();
        private ProgramStore.OrdinalColumn sourceOrder,ranks,segmentLevels;
        private final List<StorageIndex.Location> bases;
        NativeMetadata(ProgramStore.StorageInventory inventory,StoragePartition partition){
            this.inventory=inventory;this.partition=partition;
            try{
                sourceOrder=inventory.canonicalOrder();owned.add(sourceOrder);ranks=column(inventory.size());
                segmentLevels=column(partition.segments().size());
                for(int rank=0;rank<inventory.size();rank++)ranks.set(sourceOrder.get(rank),rank);
                bases=new ProgramStore.BorrowedList<>(inventory.size(),rank->{
                    var base=inventory.at(Math.toIntExact(sourceOrder.get(rank)));
                    return new StorageIndex.Location(base.header(),base instanceof Memory.Region region
                        ?Optional.of(new StorageRange(BigInteger.ZERO,region.extent())):Optional.empty());
                },()->inventory.size());
                int level=0;
                for(int rank=0;rank<inventory.size();rank++)for(var segment:segments(rank)){
                    segmentLevels.set(segment,level++);
                }
            }catch(RuntimeException|Error failure){release(failure);throw failure;}
        }
        private ProgramStore.OrdinalColumn column(long length){var result=inventory.column(length);owned.add(result);return result;}
        private void release(Throwable failure){for(var column:owned)try{column.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
        int ordinal(StorageId identity){int source=inventory.ordinal(identity);if(source<0)throw new IllegalArgumentException("foreign storage base");return Math.toIntExact(ranks.get(source));}
        int ordinal(StorageIndex.BaseAddress address){if(address.owner()!=inventory)throw new IllegalArgumentException("foreign storage descriptor owner");return Math.toIntExact(ranks.get(address.ordinal()));}
        private List<Integer> segments(int rank){return partition.ordinals(new StorageIndex.BaseAddress(inventory,Math.toIntExact(sourceOrder.get(rank))),Optional.empty());}
        GroupDirectory groupDirectory(ProgramStore.OrdinalColumn parent){
            // Compress before sorting: comparison is immutable and ties retain canonical rank.
            int count=inventory.size();for(int rank=0;rank<count;rank++)parent.set(rank,representative(parent,rank));
            try(var order=inventory.order(count,(a,b)->Long.compare(parent.get(a),parent.get(b)))){
                var members=column(count);var offsets=column(count+1L);var owners=column(count);var sizes=column(count);
                int groups=0;long previous=-1,start=0;
                for(int at=0;at<count;at++){
                    int rank=Math.toIntExact(order.get(at));long root=parent.get(rank);
                    if(root!=previous){
                        if(groups>0)sizes.set(groups-1,at-start);
                        offsets.set(groups++,at);start=at;previous=root;
                    }
                    members.set(at,rank);owners.set(rank,groups-1);
                }
                if(groups>0)sizes.set(groups-1,count-start);offsets.set(groups,count);
                return new GroupDirectory(new ProgramStore.BorrowedList<>(groups,group->{
                    long from=offsets.get(group),to=offsets.get(group+1L);
                    return new ProgramStore.BorrowedList<>(Math.toIntExact(to-from),at->Math.toIntExact(members.get(from+at)),()->inventory.size());
                },()->inventory.size()),new ProgramStore.BorrowedList<>(count,rank->Math.toIntExact(owners.get(rank)),()->inventory.size()),
                    new ProgramStore.BorrowedList<>(groups,group->Math.toIntExact(sizes.get(group)),()->inventory.size()));
            }
        }
        List<List<Integer>> groupSegments(List<List<Integer>> groups){
            try{
                var offsets=column(groups.size()+1L);var members=column(partition.segments().size());long at=0;
                for(int group=0;group<groups.size();group++){
                    offsets.set(group,at);
                    for(var rank:groups.get(group))for(var segment:segments(rank))members.set(at++,segment);
                }
                offsets.set(groups.size(),at);
                return new ProgramStore.BorrowedList<>(groups.size(),group->{
                    long start=offsets.get(group),end=offsets.get(group+1L);
                    return new ProgramStore.BorrowedList<>(Math.toIntExact(end-start),ordinal->Math.toIntExact(members.get(start+ordinal)),()->inventory.size());
                },()->inventory.size());
            }catch(RuntimeException|Error failure){release(failure);throw failure;}
        }
    }
    private StorageIndex.Resolution readSource(StatementEffects.Write write) {
        if(write.source() instanceof StatementEffects.CapturedBytes copy)return copy.source();
        return preparedReads.computeIfAbsent(write,w->{
            if(!(w.source() instanceof StatementEffects.ExpressionSource e))return null;
            Expression value=e.value();if(value instanceof Expressions.FitText fit)value=fit.value();
            return value instanceof Expressions.Read read?effects.storage().resolve(read.place()):null;
        });
    }
    private static int representative(ProgramStore.OrdinalColumn parent,int member) {
        int root=member;while(parent.get(root)!=root)root=Math.toIntExact(parent.get(root));
        while(parent.get(member)!=member){int next=Math.toIntExact(parent.get(member));parent.set(member,root);member=next;}return root;
    }
    private List<Plan> compile(List<StatementEffects.Write> writes,Id evidence,OriginId origin,List<PremiseId> initialPremises,StatementEffects.OperationRef operation,Entries.InitialCondition initial,Optional<Control.OutcomeKey> outcome) {
        var result=new ArrayList<Plan>();
        for(var write:writes)closureImpacts.computeIfAbsent(write,w->{
            var impacted=new HashSet<ObjectId>();
            if(logicalCellAliases.isEmpty())return Set.of();
            for(var target:w.targets())impacted.addAll(logicalCellAliases.getOrDefault(target.location().base().id(),List.of()));
            return Set.copyOf(impacted);
        });
        if(mode.physical())for(var write:writes)for(int ordinal=0;ordinal<write.targets().size();ordinal++) {
            var reference=new TargetRef(write,ordinal,null);
            var premises=new LinkedHashSet<>(initialPremises);premises.addAll(StatementEffects.targetPremises(write,ordinal));
            var event=events.size();events.add(new ValueFact.Support(evidence,origin,ordered(premises)));
            eventDetails.add(new PreparedEvent(operation,initial,write,reference,outcome,null));
            result.add(new Plan(write,reference,event,null));
        }
        for(var write:writes)for(var logical:logicalTargets(write)) {
            int event=events.size();events.add(new ValueFact.Support(evidence,origin,ordered(initialPremises)));
            eventDetails.add(new PreparedEvent(operation,initial,write,null,outcome,logical));
            result.add(new Plan(write,null,event,logical));
        }
        var batch=Collections.unmodifiableList(new ArrayList<>(result));
        batchWrites.put(batch,List.copyOf(writes));return batch;
    }
    private List<StatementEffects.LogicalTarget> logicalTargets(StatementEffects.Write write) {
        if(mode.physical())return write.logicalTargets();
        // Only explicit named writes supply logical candidates; alias scopes never invent text.
        var place=write.occurrence().map(session.index()::place).orElse(null);
        if(place==null)return List.of();
        var ids=new TreeSet<ObjectId>(OBJECT_ORDER);
        for(var id:effects.storage().explicitObjects(place)) {
            var binding=session.index().object(id).storage();
            if(binding instanceof Memory.CellBinding cell)ids.addAll(logicalCellAliases.get(cell.storage()));else ids.add(id);
        }
        return ids.stream().map(id->new StatementEffects.LogicalTarget(id,true)).toList();
    }
    public static final class State {
        private final EntryId entry;
        private final SegmentMap<RegionalAlternatives.Node<Content>> bindings;
        private final Map<ObjectId,Set<LogicalValue>> logical;
        private final Set<ObjectId> closed;
        private final List<Integer> groupSizes;
        private final long fingerprint;
        private State(EntryId entry,SegmentMap<RegionalAlternatives.Node<Content>> bindings,Map<ObjectId,Set<LogicalValue>> logical,Set<ObjectId> closed,List<Integer> groupSizes){this.entry=entry;this.bindings=bindings;this.logical=Map.copyOf(logical);this.closed=Set.copyOf(closed);this.groupSizes=groupSizes;fingerprint=Objects.hashCode(entry)+Long.rotateLeft(bindings.fingerprint(),13)+Long.rotateLeft(this.logical.hashCode(),29)+Long.rotateLeft(this.closed.hashCode(),47);}
        private RegionalAlternatives.Size size(){var roots=new ArrayList<RegionalAlternatives.Node<Content>>();bindings.forEach((g,node)->roots.add(node));return RegionalAlternatives.size(roots);}
        /** Encoded structural edges (one compact edge may carry multiple events), not worlds. */
        public long materializedAlternatives(){return size().alternatives();}
        public long decisionNodes(){return size().nodes();}
        public long maxComponentCardinality(){return size().maxComponent();}
        public boolean reached(){return entry!=null;}
        public int explicitBases(){int[] count={0};bindings.forEach((g,node)->count[0]+=groupSizes.get(g));return count[0];}
    }
    private static final State BOTTOM=new State(null,new SegmentMap<>(),Map.of(),Set.of(),List.of());
    private Content unknown(StorageIndex.Location location,String reason) {return unknown(location,reason,-1);}
    private Content unknown(StorageIndex.Location location,String reason,int event) {
        if(location.range().isPresent())return new Bytes(ByteImage.unknown(location.range().get().end().map(e->e.subtract(location.range().get().start())),reason,event));
        return unknown(reference(location),reason,event);
    }
    private Content unknown(LocationRef location,String reason,int event) {
        return new Scalar(Optional.empty(),Set.of(),Set.of(reason),Set.of(),List.of(new Trace(location,Optional.empty(),event,Optional.empty(),Map.of(),Set.of(),Set.of(reason))));
    }
    private Content unknownSegment(int ordinal,String reason){
        var range=partition.range(ordinal);
        if(range.isPresent()){var selected=range.orElseThrow();return new Bytes(ByteImage.unknown(selected.end().map(end->end.subtract(selected.start())),reason,-1));}
        var address=partition.address(ordinal);if(address.isPresent())return unknown(new LocationRef(address.orElseThrow(),range),reason,-1);
        return unknown(partition.segments().get(ordinal).location(),reason);
    }
    final class Engine implements AnalysisDefinition<State> {
        long contentReads,contentUpdates,alternativeVisits,physicalGroupsApplied,physicalWritesApplied,maxLogicalCells,maxLogicalValues;
        @Override public Direction direction(){return Direction.FORWARD;}
        @Override public State bottom(){return BOTTOM;}
        private final RegionalAlternatives<Content> relations=new RegionalAlternatives<>(content -> content instanceof Bytes bytes ? bytes.image() : null,Bytes::new);
        private final Map<Integer,RegionalAlternatives.Node<Content>> defaults=new HashMap<>();
        private long maxStateAlternatives,maxDecisionNodes,maxComponentCardinality,maxProvenanceRows,maxExpandedAlternatives,boundaryAlternatives;
        private State track(State state) {
            maxLogicalCells=Math.max(maxLogicalCells,state.logical.size());
            maxLogicalValues=Math.max(maxLogicalValues,state.logical.values().stream().mapToLong(Set::size).sum());
            // Correlation groups partition segment levels: nodes/edges cannot overlap
            // across groups and each component's distinct labels belong to one group.
            var totals=new long[5];
            state.bindings.forEach((group,root)->{
                var size=relations.componentSize(root);
                totals[0]+=size.nodes();totals[1]+=size.alternatives();totals[2]=Math.max(totals[2],size.maxComponent());totals[3]+=size.provenanceRows();totals[4]+=size.expandedAlternatives();
            });
            maxStateAlternatives=Math.max(maxStateAlternatives,totals[1]);maxProvenanceRows=Math.max(maxProvenanceRows,totals[3]);maxExpandedAlternatives=Math.max(maxExpandedAlternatives,totals[4]);
            maxDecisionNodes=Math.max(maxDecisionNodes,totals[0]);maxComponentCardinality=Math.max(maxComponentCardinality,totals[2]);return state;
        }
        private RegionalAlternatives.Node<Content> value(State state,int group) {
            contentReads++;var present=state.bindings.get(group);
            return present!=null?present:defaults.computeIfAbsent(group,g->{
                var values=new TreeMap<Integer,Content>();for(var segment:groupSegments.get(g))values.put(level(segment),unknownSegment(segment,"UNSPECIFIED_ENTRY_CONTENT"));
                return relations.singleton(values);
            });
        }
        /** Recompose only the captured/requested pieces; unrelated bytes stay unspecified. */
        private Content content(Map<Integer,Content> selected,int ordinal) {
            var location=bases.get(ordinal);Content result=unknown(location,"UNSPECIFIED_ENTRY_CONTENT");
            for(var segment:partition.intersecting(location)) {
                var piece=selected.get(level(segment));if(piece==null)continue;
                result=result instanceof Bytes bytes?new Bytes(bytes.image().write(segment.location().range().orElseThrow(),((Bytes)piece).image())):piece;
            }
            return result;
        }
        private List<Map<StatementEffects.Write,Integer>> sourceSelections(List<Plan> plans) {
            // An alternative place reads ONE candidate. Combining all candidates' components
            // would enumerate unrelated worlds even though the expression is a choice.
            var writes=new OccurrenceMap<Boolean>();
            for(var plan:plans)if(plan.logical==null&&plan.reference.sourceApplicable()&&readSource(plan.write)!=null)writes.put(plan.write,Boolean.TRUE);
            List<Map<StatementEffects.Write,Integer>> selections=List.of(Map.of());
            for(var write:writes.keySet()) {
                var next=new ArrayList<Map<StatementEffects.Write,Integer>>();int count=readSource(write).candidates().size();
                for(var prior:selections)for(int i=0;i<Math.max(1,count);i++) {
                    var choice=new OccurrenceMap<Integer>();choice.putAll(prior);choice.put(write,count==0?-1:i);next.add(Collections.unmodifiableMap(choice));
                }
                selections=List.copyOf(next);
            }
            return selections;
        }
        private Set<Integer> readSegments(Map<StatementEffects.Write,Integer> choices) {
            var selected=new HashSet<Integer>();
            choices.forEach((write,i)->{if(i>=0)for(var segment:partition.intersecting(readSource(write).candidates().get(i).location()))selected.add(level(segment));});
            return Set.copyOf(selected);
        }
        private List<Content> contents(State state,StorageIndex.Location location) {
            int ordinal=ordinal(location.base().id());var selected=new HashSet<Integer>();
            for(var segment:partition.intersecting(location))selected.add(level(segment));
            return relations.selections(relations.project(value(state,groupOf.get(ordinal)),selected)).stream().map(c->content(c,ordinal)).toList();
        }
        @Override public Iterable<Boundary<State>> boundaries(AnalysisSession selected) {
            if(session!=selected)throw new IllegalArgumentException("foreign session");
            var result=new ArrayList<Boundary<State>>();
            for(var context:session.contexts()) {
                var seed=new State(context.entry().id(),new SegmentMap<>(),Map.of(),Set.of(),groupSizes);
                var boundary=track(apply(seed,initial.get(seed.entry),false));boundaryAlternatives=Math.max(boundaryAlternatives,boundary.materializedAlternatives());
                result.add(new Boundary<>(context,context.entryNode(),boundary));
            }
            return result;
        }
        @Override public Join<State> joinInto(State a,State b,DomainWork work) {
            if(!b.reached())return new Join<>(a,false);if(!a.reached())return new Join<>(b,true);
            if(!a.entry.equals(b.entry))throw new IllegalArgumentException("different entries");
            final class Accumulator { SegmentMap<RegionalAlternatives.Node<Content>> root=a.bindings; }
            var acc=new Accumulator();
            b.bindings.forEach((key,v)->{work.joinEntryVisited();acc.root=acc.root.put(key,relations.union(value(a,key),v));});
            a.bindings.forEach((key,v)->{if(b.bindings.get(key)==null){work.joinEntryVisited();acc.root=acc.root.put(key,relations.union(v,value(b,key)));}});
            var logical=new HashMap<>(a.logical);b.logical.forEach((key,v)->{work.joinEntryVisited();logical.merge(key,v,Engine::unionLogical);});
            var closed=new HashSet<>(a.closed);closed.retainAll(b.closed);
            return acc.root==a.bindings&&logical.equals(a.logical)&&closed.equals(a.closed)?new Join<>(a,false):new Join<>(track(new State(a.entry,acc.root,logical,closed,groupSizes)),true);
        }
        @Override public long stateFingerprint(State state){return state.fingerprint;}
        @Override public boolean equivalent(State a,State b,DomainWork work) {
            if(a==b)return true;if(!Objects.equals(a.entry,b.entry)||a.bindings.size()!=b.bindings.size()||!a.logical.equals(b.logical)||!a.closed.equals(b.closed))return false;
            var same=new boolean[]{true};a.bindings.forEach((key,v)->{work.stateCompareEntry();if(!v.equals(b.bindings.get(key)))same[0]=false;});return same[0];
        }
        private State apply(State before,List<Plan> plans,boolean forceMay) {
            if(!before.reached())return before;
            var writes=batchWrites.getOrDefault(plans,List.of());
            if(plans.isEmpty()&&writes.isEmpty())return before;
            // Preserve write occurrence grouping; each source is captured in the same pre-operation store.
            var grouped=new LinkedHashMap<Integer,OccurrenceMap<List<Plan>>>();
            for(var plan:plans)if(plan.logical==null)grouped.computeIfAbsent(groupOf.get(ordinal(plan)),ignored->new OccurrenceMap<>())
                .computeIfAbsent(plan.write,ignored->new ArrayList<>()).add(plan);
            var root=before.bindings;
            if(mode.physical())for(var entry:grouped.entrySet()) {
                physicalGroupsApplied++;physicalWritesApplied+=entry.getValue().values().stream().mapToLong(List::size).sum();
                int group=entry.getKey();RegionalAlternatives.Node<Content> all=null;
                var original=value(before,group);
                for(var choices:sourceSelections(entry.getValue().values().stream().flatMap(List::stream).toList())) {
                    var selected=readSegments(choices);
                    // Enumerate only the chosen local read, retaining other factors symbolically.
                    for(var parts:relations.selections(relations.project(original,selected))) {
                        var captured=new ReadCapture(parts,choices);var current=relations.restrict(original,parts);
                        for(var occurrence:entry.getValue().entrySet())current=write(current,captured,occurrence.getKey(),occurrence.getValue(),forceMay,before.logical);
                        all=relations.union(all,current);
                    }
                }
                var updated=root.put(group,all);if(updated!=root)contentUpdates++;root=updated;
            }
            var logical=new HashMap<>(before.logical);
            for(var plan:plans)if(plan.logical!=null&&plan.logical.sourceApplicable()) {
                var supported=logicalReplacements(before,plan);
                boolean strong=!forceMay&&plan.write.destination().exact()
                    &&plan.write.occurrenceStrength()==StatementEffects.Strength.MUST
                    &&plan.write.selection()==StatementEffects.Selection.SINGLE_DESTINATION
                    &&plan.write.occurrence().map(session.index()::place).orElse(null) instanceof Places.ObjectPlace;
                if(!mode.physical()&&strong) {
                    if(supported.isEmpty())logical.remove(plan.logical.object());else logical.put(plan.logical.object(),supported);
                } else if(!supported.isEmpty())logical.merge(plan.logical.object(),supported,KillAuthority::weakUpdate);
            }
            var closed=new HashSet<>(before.closed);var invalidated=new HashSet<ObjectId>();
            var supplied=new IdentityHashMap<StatementEffects.Write,Set<ObjectId>>();
            if(!mode.physical())for(var plan:plans)if(plan.logical!=null&&plan.logical.sourceApplicable()
                    &&plan.write.destination().exact()&&plan.write.selection()==StatementEffects.Selection.SINGLE_DESTINATION
                    &&plan.write.occurrence().map(session.index()::place).orElse(null) instanceof Places.ObjectPlace
                    &&session.index().object(plan.logical.object()).storage() instanceof Memory.CellBinding
                    &&session.index().object(plan.logical.object()).typeRef().equals(Types.known(Types.Builtin.TEXT))
                    &&closedSource(before,plan.write))
                supplied.computeIfAbsent(plan.write,w->new HashSet<>()).add(plan.logical.object());
            for(var write:writes)for(var object:closureImpacts.getOrDefault(write,Set.of())) {
                if(!supplied.getOrDefault(write,Set.of()).contains(object))invalidated.add(object);
                else if(!forceMay&&write.occurrenceStrength()==StatementEffects.Strength.MUST)closed.add(object);
            }
            closed.removeAll(invalidated);
            return root==before.bindings&&logical.equals(before.logical)&&closed.equals(before.closed)?before:track(new State(before.entry,root,logical,closed,groupSizes));
        }
        private TextPredicate.Text predicateRead(State state,Place place) {
            if(!(place instanceof Places.ObjectPlace named)||!state.closed.contains(named.object()))return TextPredicate.Text.unknown();
            var values=new HashSet<LogicalText>();for(var value:state.logical.getOrDefault(named.object(),Set.of()))values.add(value.text());
            return new TextPredicate.Text(values,false);
        }
        private boolean closedSource(State before,StatementEffects.Write write) {
            if(!(write.source() instanceof StatementEffects.ExpressionSource source))return false;
            // This proof covers exactly the expressions supported by logicalReplacements.
            Expression value=source.value();while(value instanceof Expressions.FitText fit)value=fit.value();
            if(!(value instanceof Expressions.Read||value instanceof Expressions.Literal literal&&literal.value() instanceof Values.TextValue))return false;
            var result=TextPredicate.text(source.value(),place->predicateRead(before,place));
            return !result.open()&&!result.values().isEmpty();
        }
        private static Set<LogicalValue> unionLogical(Set<LogicalValue> a,Set<LogicalValue> b) {
            if(a.containsAll(b))return a;var result=new HashSet<>(a);result.addAll(b);return Set.copyOf(result);
        }
        private Set<LogicalValue> logicalReplacements(State captured,Plan plan) {
            // Open physical binding gives no authority to kill. Unknown widens the already-open
            // domain; only explicit supported expression values add logical candidates.
            if(!(plan.write.source() instanceof StatementEffects.ExpressionSource expression))return Set.of();
            return logicalExpression(captured,plan,expression.value());
        }
        private Set<LogicalValue> logicalExpression(State captured,Plan plan,Expression expression) {
            if(expression instanceof Expressions.FitText fit) {
                var result=new HashSet<LogicalValue>();
                for(var value:logicalExpression(captured,plan,fit.value())) {
                    result.add(new LogicalValue(value.text().fit(fit.length().intValueExact(),fit.pad().codePointAt(0)),value.event()));
                }
                return Set.copyOf(result);
            }
            if(expression instanceof Expressions.Literal literal&&literal.value() instanceof Values.TextValue text)
                return Set.of(new LogicalValue(text,plan.event));
            if(expression instanceof Expressions.Read read) {
                var result=new HashSet<LogicalValue>();
                for(var object:effects.storage().explicitObjects(read.place()))result.addAll(captured.logical.getOrDefault(object,Set.of()));
                if(mode.physical())for(var candidate:effects.storage().resolve(read.place()).candidates()) {
                    int ordinal=ordinal(candidate.location().base().id());
                    for(var contents:contents(captured,candidate.location())) {
                        var value=RegionalValuesAnalysis.this.project(contents,candidate);
                        if(value.text().isPresent())for(int event:value.producers())result.add(new LogicalValue(value.text().get(),event));
                    }
                }
                for(var value:List.copyOf(result))result.add(new LogicalValue(value.text(),plan.event));
                return Set.copyOf(result);
            }
            return Set.of();
        }
        private RegionalAlternatives.Node<Content> write(RegionalAlternatives.Node<Content> current,ReadCapture captured,StatementEffects.Write write,List<Plan> plans,boolean forceMay,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            if(write.selection()==StatementEffects.Selection.MAY_SET) {
                for(var plan:plans)current=weak(current,captured,plan,logicalInputs);return current;
            }
            RegionalAlternatives.Node<Content> next=null;
            var execution=forceMay?KillAuthority.Execution.POSSIBLE:KillAuthority.Execution.REQUIRED;
            if(!KillAuthority.exhaustive(write,plans.stream().map(Plan::target).toList(),execution))next=current;
            for(var plan:plans)if(plan.reference.sourceApplicable()) {
                var replacement=replace(current,captured,plan,logicalInputs);var authority=KillAuthority.selected(write,plan.target(),execution);
                next=relations.union(next,authority.isPresent()?KillAuthority.strongOverwrite(authority.get(),replacement):weakUnion(current,replacement));
            }
            if(next==null)next=current;
            current=next;
            for(var plan:plans)if(!plan.reference.sourceApplicable())current=weak(current,captured,plan,logicalInputs);
            return current;
        }
        private RegionalAlternatives.Node<Content> weakUnion(RegionalAlternatives.Node<Content> old,RegionalAlternatives.Node<Content> supplied) {
            // Symbolic counterpart of KillAuthority.weakUpdate: both relations survive.
            return relations.union(old,supplied);
        }
        private RegionalAlternatives.Node<Content> weak(RegionalAlternatives.Node<Content> current,ReadCapture captured,Plan plan,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            return weakUnion(current,replace(current,captured,plan,logicalInputs));
        }
        private RegionalAlternatives.Node<Content> replace(RegionalAlternatives.Node<Content> old,ReadCapture captured,Plan plan,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            RegionalAlternatives.Node<Content> result=null;
            for(var replacement:replacements(plan,captured,logicalInputs)) {
                alternativeVisits++;var suppliedValues=new HashMap<Integer,Content>();var updates=new HashMap<Integer,java.util.function.UnaryOperator<Content>>();
                for(var segment:segmentOrdinals(plan)) {
                    Content piece=replacement;
                    if(replacement instanceof Bytes bytes) {
                        var range=partition.range(segment).orElseThrow();var destination=plan.reference.range().orElseThrow();
                        var relative=new StorageRange(range.start().subtract(destination.start()),range.end().map(e->e.subtract(destination.start())));
                        piece=new Bytes(bytes.image().slice(relative));
                    }
                    var supplied=piece;suppliedValues.put(level(segment),supplied);
                    updates.put(level(segment),prior->{
                        if(prior instanceof Bytes bytes&&eventDetails.get(plan.event).initial()!=null&&eventDetails.get(plan.event).initial().value() instanceof Entries.LiteralInitial)
                            return new Bytes(bytes.image().initialize(new StorageRange(BigInteger.ZERO,bytes.image().extent()),((Bytes)supplied).image()));
                        return supplied;
                    });
                }
                boolean initialLiteral=eventDetails.get(plan.event).initial()!=null&&eventDetails.get(plan.event).initial().value() instanceof Entries.LiteralInitial;
                result=relations.union(result,initialLiteral?relations.update(old,updates):relations.overwrite(old,suppliedValues));
            }
            return result;
        }
        private Set<Content> replacements(Plan plan,ReadCapture captured,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            var targetRange=plan.reference.range();
            // Whole Region unknown content needs its exact range/event, not a decoded header.
            if(targetRange.isPresent()&&(!plan.reference.sourceApplicable()||plan.write.source() instanceof StatementEffects.UnknownSource)){
                var range=targetRange.orElseThrow();String reason=!plan.reference.sourceApplicable()?"UNPROVEN_WRITE_DESTINATION":((StatementEffects.UnknownSource)plan.write.source()).reason();
                return Set.of(new Bytes(ByteImage.unknown(range.end().map(end->end.subtract(range.start())),reason,plan.event)));
            }
            var target=plan.target().location();
            if(!plan.reference.sourceApplicable())return Set.of(unknown(target,"UNPROVEN_WRITE_DESTINATION",plan.event));
            var source=plan.write.source();
            if(source instanceof StatementEffects.CapturedBytes copy) {
                var c=copy.source().candidates().getFirst();int ordinal=ordinal(c.location().base().id());
                var sourceRange=c.location().range().orElseThrow();
                return Set.of(new Bytes(((Bytes)capture(content(captured.contents(),ordinal),c.location())).image().slice(sourceRange).copied(plan.event,sourceRange.start())));
            }
            if(source instanceof StatementEffects.UnknownSource u)return Set.of(unknown(target,u.reason(),plan.event));
            var expression=((StatementEffects.ExpressionSource)source).value();
            var logicalRead=expression instanceof Expressions.Read r?r:expression instanceof Expressions.FitText f&&f.value() instanceof Expressions.Read r?r:null;
            if(target.range().isPresent()&&logicalRead!=null) {
                var reads=readSource(plan.write);var result=new HashSet<Content>();
                var codecs=plan.write.destination().candidates().stream().filter(c->c.location().equals(target)).map(StorageIndex.Candidate::codec).distinct().toList();
                var extent=target.range().get().end().map(e->e.subtract(target.range().get().start()));
                if(!(reads.remainder() instanceof Scopes.NoMemory))result.add(unknown(target,"READ_LOCATION_REMAINDER",plan.event));
                // Keep each admitted physical alternative and its selected source range. A Choice
                // remainder is not evidence against bytes already supported by another alternative.
                for(int i=0;i<reads.candidates().size();i++) {
                    if(captured.choices().get(plan.write)!=i)continue;
                    var candidate=reads.candidates().get(i);var range=candidate.location().range();
                    if(expression instanceof Expressions.FitText fit&&extent.filter(fit.length()::equals).isPresent()
                            &&codecs.size()==1&&codecs.getFirst().filter(MemoryCodecs::isIbm1047).isPresent()
                            &&range.isPresent()&&range.get().end().isPresent()&&candidate.codec().filter(MemoryCodecs::isIbm1047).isPresent()) {
                        var pad=MemoryCodecs.encodeText(codecs.getFirst().orElseThrow(),new Values.TextValue(fit.pad()),BigInteger.ONE);
                        if(pad.status()==MemoryCodecs.Status.EXACT) {
                            int ordinal=ordinal(candidate.location().base().id());
                            var image=((Bytes)capture(content(captured.contents(),ordinal),candidate.location())).image().slice(range.get()).copied(plan.event,range.get().start(),i);
                            result.add(new Bytes(image.fit(fit.length(),pad.value().orElseThrow().octets().getFirst(),plan.event)));continue;
                        }
                    }
                    result.add(unknown(target,"UNINTERPRETED_VALUE_EXPRESSION",plan.event));
                }
                for(var object:effects.storage().explicitObjects(logicalRead.place()))for(var value:logicalInputs.getOrDefault(object,Set.of())) {
                    result.add(unknown(target,"LOGICAL_READ_REMAINDER",plan.event));
                    if(codecs.size()!=1||codecs.getFirst().isEmpty()||extent.isEmpty())continue;
                    var text=value.text();
                    if(expression instanceof Expressions.FitText fit)text=text.fit(fit.length().intValueExact(),fit.pad().codePointAt(0));
                    LogicalTextEncoding.encode(text,codecs.getFirst().get(),extent.get(),plan.event).ifPresent(image->
                        result.add(new Bytes(image.withLogicalSupport(Set.of(new ByteImage.LogicalSupport(object,value.event()))))));
                }
                if(!result.isEmpty())return Set.copyOf(result);
            }
            if(expression instanceof Expressions.Literal literal) {
                var v=literal.value();
                if(target.range().isEmpty())return Set.of(v instanceof Values.TextValue t
                    ?new Scalar(Optional.of(LogicalText.of(t.value())),Set.of(plan.event),Set.of(),Set.of(),List.of(new Trace(reference(target),Optional.empty(),plan.event,Optional.of(reference(target)),Map.of(),Set.of(),Set.of())))
                    :unknown(target,"NON_TEXT_LOGICAL_VALUE",plan.event));
                var range=target.range().get();var extent=range.end().map(e->e.subtract(range.start()));
                if(v instanceof Values.BytesValue bytes&&extent.equals(Optional.of(BigInteger.valueOf(bytes.octets().size()))))
                    return Set.of(new Bytes(ByteImage.literal(bytes,plan.event)));
                if(v instanceof Values.TextValue text&&extent.isPresent()) {
                    var codecs=plan.write.destination().candidates().stream().filter(c->c.location().equals(target)).map(StorageIndex.Candidate::codec).distinct().toList();
                    if(codecs.size()==1&&codecs.getFirst().isPresent()) {
                        var encoded=MemoryCodecs.encodeText(codecs.getFirst().get(),text,extent.get());
                        if(encoded.value().isPresent())return Set.of(new Bytes(ByteImage.literal(encoded.value().get(),plan.event)));
                    }
                }
                return Set.of(unknown(target,"UNINTERPRETED_LITERAL_REPRESENTATION",plan.event));
            }
            if(target.range().isEmpty()&&expression instanceof Expressions.Read read) {
                var resolution=effects.storage().resolve(read.place());var result=new HashSet<Content>();
                if(!(resolution.remainder() instanceof Scopes.NoMemory))result.add(unknown(target,"READ_LOCATION_REMAINDER",plan.event));
                for(var object:effects.storage().explicitObjects(read.place()))for(var value:logicalInputs.getOrDefault(object,Set.of()))
                    result.add(new Scalar(Optional.of(value.text()),Set.copyOf(List.of(value.event(),plan.event)),Set.of(),Set.of(),
                        List.of(new Trace(reference(target),Optional.empty(),plan.event,Optional.of(reference(target)),Map.of(),Set.of(),Set.of(),Set.of(new ByteImage.LogicalSupport(object,value.event()))))));

                for(int i=0;i<resolution.candidates().size();i++) {
                    if(captured.choices().get(plan.write)!=i)continue;
                    var candidate=resolution.candidates().get(i);int ordinal=ordinal(candidate.location().base().id());
                    result.add(project(capture(content(captured.contents(),ordinal),candidate.location()),candidate).captured(plan.event,candidate.location(),target));
                }
                if(!result.isEmpty())return Set.copyOf(result);
            }
            return Set.of(unknown(target,"UNINTERPRETED_VALUE_EXPRESSION",plan.event));
        }
        State operation(State state,Operation operation) {
            session.index().requireOperation(operation);
            var plans=operations.get(operation.header().id());if(plans==null)throw new IllegalArgumentException("foreign operation");return apply(state,plans,false);
        }
        @Override public State transferBlock(AnalysisPoint point,State anchor,DomainWork work) {
            if(!(point.node().source() instanceof CfgNode.SequenceNode node))return anchor;
            var sequence=session.index().sequence(point.node());
            var state=anchor;for(var op:sequence.instructions()){work.operationTransferred();state=operation(state,op);}
            work.operationTransferred();return operation(state,sequence.terminator());
        }
        @Override public State transferEdge(AnalysisPoint point,CfgTransition edge,State state,DomainWork work) {
            if(!state.reached()||!(point.node().source() instanceof CfgNode.SequenceNode))return state;
            var terminator=session.index().sequence(point.node()).terminator();
            if(terminator instanceof Operations.Branch branch) {
                if(session.index().unprovedPreconditions(branch.header().id()))return state;
                int requested=edge.kind()==CfgTransition.Kind.BRANCH_TRUE?TextPredicate.TRUE:edge.kind()==CfgTransition.Kind.BRANCH_FALSE?TextPredicate.FALSE:TextPredicate.BOTH;
                var at=state;int possible=TextPredicate.truth(branch.predicate(),place->predicateRead(at,place));
                return (possible&requested)==0?BOTTOM:state;
            }
            if(!(terminator instanceof Operations.Invoke invoke))return state;
            if(edge.kind()==CfgTransition.Kind.INVOKE_NORMAL)return apply(state,outcomes.get(invoke.header().id()).get(Control.NormalOutcome.INSTANCE),false);
            state=apply(state,otherwise.get(invoke.header().id()),true);for(var plans:outcomes.get(invoke.header().id()).values())state=apply(state,plans,true);return state;
        }
        private Map<String,Long> metrics(){var result=new TreeMap<>(relations.metrics());result.put("maxLogicalCells",maxLogicalCells);result.put("maxLogicalValues",maxLogicalValues);result.put("physicalGroupsApplied",physicalGroupsApplied);result.put("physicalWritesApplied",physicalWritesApplied);result.put("logicalOnlyMode",mode.physical()?0L:1L);result.put("contentReads",contentReads);result.put("contentUpdates",contentUpdates);result.put("alternativeVisits",alternativeVisits);result.put("maxStateAlternatives",maxStateAlternatives);result.put("maxDecisionNodes",maxDecisionNodes);result.put("maxComponentCardinality",maxComponentCardinality);result.put("boundaryAlternatives",boundaryAlternatives);result.put("maxProvenanceRows",maxProvenanceRows);result.put("maxExpandedAlternatives",maxExpandedAlternatives);return Map.copyOf(result);}
    }
    private Content capture(Content content,StorageIndex.Location selected) {
        return content; // Only executable writes and copies contribute to content.
    }
    private Scalar project(Content content,StorageIndex.Candidate candidate) {
        if(content instanceof Scalar scalar)return scalar;
        var range=candidate.location().range().orElseThrow();var read=((Bytes)content).image().read(range);
        var traces=read.parts().stream().flatMap(p->traces(p,candidate.location()).stream()).toList();
        var gaps=new HashSet<Integer>();read.parts().forEach(p->gaps.addAll(p.sourceGaps()));
        if(read.bytes().isEmpty())return new Scalar(Optional.empty(),Set.of(),read.reasons(),gaps,traces);
        if(candidate.codec().isEmpty()||range.end().isEmpty())return new Scalar(Optional.empty(),Set.of(),Set.of("UNINTERPRETED_VIEW"),gaps,traces);
        var decoded=MemoryCodecs.decodeText(candidate.codec().get(),read.bytes().get(),range.end().get().subtract(range.start()));
        if(decoded.value().isEmpty())return new Scalar(Optional.empty(),Set.of(),Set.of(decoded.status().name()),gaps,traces);
        var producers=new HashSet<Integer>();for(var part:read.parts()){producers.addAll(part.contributors().keySet());part.logicalSupports().forEach(support->producers.add(support.event()));}
        return new Scalar(decoded.value().map(v->LogicalText.of(v.value())),producers,Set.of(),gaps,traces);
    }
    private List<Trace> traces(ByteImage.Part part,StorageIndex.Location selected) {
        if(part.payload().isEmpty()||part.coInitial().isEmpty())return List.of(trace(part,selected));
        return part.contributors().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->trace(part.contributor(e.getKey(),e.getValue()),selected)).toList();
    }
    private Trace trace(ByteImage.Part part,StorageIndex.Location selected) {
        var readStart=selected.range().orElseThrow().start();
        var observed=new StorageIndex.Location(selected.base(),Optional.of(new StorageRange(readStart.add(part.range().start()),part.range().end().map(e->e.add(readStart)))));
        var length=part.range().end().map(e->e.subtract(part.range().start()));
        var bytes=part.materialize();
        Optional<LocationRef> original=Optional.empty();
        if(bytes.isPresent()) {
            var written=eventDetails.get(part.producer()).target().location();
            original=Optional.of(reference(new StorageIndex.Location(written.base(),Optional.of(StorageRange.exact(written.range().orElseThrow().start().add(part.producerOffset()),length.orElseThrow())))));
        }
        var captures=new HashMap<Integer,Set<CapturedRead>>();
        for(var entry:part.capturedOffsets().entrySet()) {
            var event=eventDetails.get(entry.getKey());var sources=readSource(event.write()).candidates();
            var destination=event.target().location();var portions=new HashSet<CapturedRead>();
            for(var position:entry.getValue()) {
                var source=sources.get(position.alternative()).location();var offset=position.offset();
                var src=new StorageIndex.Location(source.base(),Optional.of(StorageRange.exact(offset,length.orElseThrow())));
                var dst=new StorageIndex.Location(destination.base(),Optional.of(StorageRange.exact(destination.range().orElseThrow().start().add(offset.subtract(source.range().orElseThrow().start())),length.orElseThrow())));
                portions.add(new CapturedRead(reference(source),reference(src),reference(dst)));
            }
            captures.put(entry.getKey(),Set.copyOf(portions));
        }
        return new Trace(reference(observed),bytes,part.producer(),original,captures,part.sourceGaps(),part.reasons(),part.logicalSupports());
    }
    public Execution execute(){var engine=new Engine();return new Execution(engine,DataflowSolver.solve(session,engine));}
    public final class Execution {
        private final Engine engine;private final DataflowResult<State> stable;private final Map<String,Long> solveMetrics;
        private Execution(Engine engine,DataflowResult<State> stable){this.engine=engine;this.stable=stable;solveMetrics=engine.metrics();}
        public DataflowResult<State> dataflow(){return stable;}
        public Map<String,Long> solveMetrics(){return solveMetrics;}
        /** Cumulative domain work, including observed replay; no semantic budget. */
        public Map<String,Long> metrics(){return engine.metrics();}
        public Map<String,Long> preparationMetrics(){var result=new TreeMap<>(effects.preparationMetrics());result.putAll(effects.storage().preparationMetrics());result.put("partitionSegments",(long)partition.segments().size());result.put("eventsPrepared",(long)events.size());result.put("physicalPlansPrepared",eventDetails.stream().filter(e->e.logical()==null).count());result.put("logicalPlansPrepared",eventDetails.stream().filter(e->e.logical()!=null).count());result.put("correlationGroups",(long)groups.size());result.put("maxGroupBases",groups.stream().mapToLong(List::size).max().orElse(0));return Map.copyOf(result);}
        public ObservationBatch<ObjectId,RegionalValueFact> observe(Iterable<PointQuery<ObjectId>> requests) {
            return observe(requests,OBJECT_ORDER,StorageSubject.NamedObject::new,StorageValueFact::asObjectFact,true);
        }
        public ObservationBatch<StorageSubject,StorageValueFact> observeStorage(Iterable<PointQuery<StorageSubject>> requests) {
            return observe(requests,StorageSubject.ORDER,java.util.function.Function.identity(),java.util.function.Function.identity(),false);
        }
        private <T,V> ObservationBatch<T,V> observe(Iterable<PointQuery<T>> requests,Comparator<T> order,
                java.util.function.Function<T,StorageSubject> subject,java.util.function.Function<StorageValueFact,V> projection,boolean textOnly) {
            return BatchReplayer.materialize(session,stable,Direction.FORWARD,BOTTOM,requests,order,engine::operation,new BatchReplayer.Projection<State,T,V>() {
                @Override public State mergeStates(State a,State b){return engine.joinInto(a,b,new DomainWork()).state();}
                @Override public boolean supports(PointQuery<T> query) {
                    var selected=subject.apply(query.subject());
                    if(!effects.storage().supports(selected,query.point().entry().unit()))return false;
                    return !textOnly||selected instanceof StorageSubject.NamedObject named&&session.index().object(named.object()).typeRef().equals(Types.known(Types.Builtin.TEXT));
                }
                @Override public V project(PointQuery<T> query,State state){return projection.apply(fact(new PointQuery<>(query.point(),subject.apply(query.subject())),state));}
                @Override public boolean supportsOutcome(PointQuery<T> query) {
                    var site=session.index().site(query.point().operation());return site!=null&&site.operation() instanceof Operations.Invoke invoke
                        &&query.point().outcome()==Control.NormalOutcome.INSTANCE&&invoke.outcomes().known().stream().anyMatch(Control.Normal.class::isInstance);
                }
                @Override public State transferOutcome(PointQuery<T> query,State before){return engine.apply(before,outcomes.get(query.point().operation()).get(query.point().outcome()),false);}
            });
        }
        private StorageValueFact fact(PointQuery<StorageSubject> query,State state) {
            var storage=effects.storage();var resolution=storage.resolve(query.subject());var reasons=new TreeSet<String>(resolution.reasons());
            boolean model=!(resolution.remainder() instanceof Scopes.NoMemory);if(model)reasons.add("READ_LOCATION_REMAINDER");
            var supports=new TreeMap<String,Set<Integer>>();var origins=new LinkedHashSet<>(storage.subjectOrigins(query.subject()));
            var alternatives=new HashSet<StorageValueFact.Alternative>();
            boolean source=sourceOpen(query);
            if(!mode.physical()){model=true;reasons.add("PHYSICAL_PROPAGATION_DISABLED");}
            if(mode.physical()&&state.reached())for(var candidate:resolution.candidates()) {
                origins.addAll(candidate.origins());
                int ordinal=ordinal(candidate.location().base().id());
                for(var contents:engine.contents(state,candidate.location())) {
                    var value=RegionalValuesAnalysis.this.project(contents,candidate);
                    if(value.text().isEmpty()){model=true;reasons.addAll(value.reasons());}
                    else supports.computeIfAbsent(value.text().get().text(),ignored->new HashSet<>()).addAll(value.producers());
                    var fragments=value.traces().stream().flatMap(t->fragments(t,query.point().entry(),value.text().isPresent()).stream()).distinct().sorted(StorageValueOrder.FRAGMENT).toList();
                    var interpretation=new RegionalValueFact.Interpretation(candidate.location().in(query.point().entry()),candidate.codec());
                    if(candidate.location().range().isEmpty())alternatives.add(new StorageValueFact.Alternative(interpretation,value.text().map(t->new Values.TextValue(t.text())),fragments));
                    else {
                        // Co-initial contributors prove the same image, not alternative byte values.
                        // Keep the established nonoverlapping-fragment wire: a canonical complete
                        // cover plus one complete cover for each additional contribution suffices
                        // to retain every interval/provenance association without Cartesian products.
                        var byRange=new LinkedHashMap<StorageIndex.ContextualLocation,List<StorageValueFact.Fragment>>();
                        for(var fragment:fragments)byRange.computeIfAbsent(fragment.location(),ignored->new ArrayList<>()).add(fragment);
                        var groups=new ArrayList<>(byRange.values());var cover=groups.stream().map(List::getFirst).toList();
                        alternatives.add(new StorageValueFact.Alternative(interpretation,value.text().map(t->new Values.TextValue(t.text())),cover));
                        for(int i=0;i<groups.size();i++)for(int j=1;j<groups.get(i).size();j++) {
                            var variant=new ArrayList<>(cover);variant.set(i,groups.get(i).get(j));
                            alternatives.add(new StorageValueFact.Alternative(interpretation,value.text().map(t->new Values.TextValue(t.text())),variant));
                        }
                    }
                }
            }
            var logicalAlternatives=new ArrayList<StorageValueFact.LogicalAlternative>();
            if(state.reached())for(var object:storage.explicitObjects(query.subject())) {
                var logicalSupport=new TreeMap<String,Set<Integer>>();
                for(var value:state.logical.getOrDefault(object,Set.of()))logicalSupport.computeIfAbsent(value.text().text(),ignored->new HashSet<>()).add(value.event());
                logicalSupport.forEach((text,producers)->{
                    supports.computeIfAbsent(text,ignored->new HashSet<>()).addAll(producers);
                    logicalAlternatives.add(new StorageValueFact.LogicalAlternative(object,new Values.TextValue(text),producers.stream().map(events::get).distinct()
                        .sorted(Comparator.comparing(ValueFact.Support::evidence,StorageValueOrder.ID).thenComparing(ValueFact.Support::origin,StorageValueOrder.ID)).toList()));
                });
            }
            if(state.reached()&&supports.isEmpty()){model=true;reasons.add("NO_KNOWN_TEXT_PROJECTION");}
            var candidates=new ArrayList<Values.TextValue>();var associations=new ArrayList<ValueFact.CandidateSupport>();
            var premises=new LinkedHashSet<PremiseId>();var evidence=new LinkedHashSet<Id>();
            for(var entry:supports.entrySet()) {
                var text=new Values.TextValue(entry.getKey());candidates.add(text);
                var producers=entry.getValue().stream().map(events::get).distinct().sorted(Comparator.comparing(ValueFact.Support::evidence,StorageValueOrder.ID).thenComparing(ValueFact.Support::origin,StorageValueOrder.ID)).toList();
                associations.add(new ValueFact.CandidateSupport(text,producers));
                for(var producer:producers){premises.addAll(producer.premises());evidence.add(producer.evidence());origins.add(producer.origin());}
            }
            // Detached provenance includes capture/unknown events as well as original literal supports.
            for(var alternative:alternatives)for(var fragment:alternative.fragments()) {
                var definitions=new ArrayList<DefinitionEvent>();fragment.producer().ifPresent(p->definitions.add(p.definition()));
                fragment.unknownWriter().ifPresent(definitions::add);fragment.captures().forEach(c->definitions.add(c.definition()));
                for(var definition:definitions) {
                    premises.addAll(definition.premises());origins.add(definition.origin());
                    if(definition.operation().isPresent())evidence.add(definition.operation().get());else definition.destination().ifPresent(evidence::add);
                }
                fragment.sourceGaps().forEach(g->origins.add(g.origin()));
            }
            return new StorageValueFact(query.point(),query.subject(),resolution.candidates().stream().map(c->new RegionalValueFact.Interpretation(c.location().in(query.point().entry()),c.codec())).distinct().sorted(StorageValueOrder.INTERPRETATION).toList(),
                state.reached()?ValueFact.Reachability.REACHABLE:ValueFact.Reachability.UNREACHABLE_IN_MODEL,state.reached()?candidates:null,state.reached()?model:null,
                source,state.reached()&&model,ordered(premises),ordered(evidence),ordered(origins),associations,state.reached()?List.copyOf(reasons):List.of(),alternatives.stream().sorted(StorageValueOrder.ALTERNATIVE).toList(),logicalAlternatives);
        }
        private List<StorageValueFact.Fragment> fragments(Trace trace,EntryId entry,boolean knownText) {
            if(trace.logicalSupports().isEmpty())return List.of(fragment(trace,entry,knownText));
            return trace.logicalSupports().stream().map(ByteImage.LogicalSupport::object).distinct().map(object->
                fragment(new Trace(trace.observed(),trace.bytes(),trace.producer(),trace.original(),trace.captures(),trace.gaps(),trace.reasons(),
                    trace.logicalSupports().stream().filter(s->s.object().equals(object)).collect(java.util.stream.Collectors.toSet())),entry,knownText)).toList();
        }
        private StorageValueFact.Fragment fragment(Trace trace,EntryId entry,boolean knownText) {
            var kind=trace.observed().range().isPresent()?(trace.bytes().isPresent()?StorageValueFact.FragmentKind.KNOWN_BYTES:StorageValueFact.FragmentKind.UNKNOWN_BYTES)
                :trace.bytes().isPresent()?StorageValueFact.FragmentKind.LOGICAL_CAPTURE:knownText?StorageValueFact.FragmentKind.LOGICAL_VALUE:StorageValueFact.FragmentKind.UNKNOWN_LOGICAL;
            var producer=trace.original().map(location->new StorageValueFact.Producer(eventDetails.get(trace.producer()).definition(entry),location.in(entry)));
            var unknownWriter=producer.isEmpty()&&trace.producer()>=0?Optional.of(eventDetails.get(trace.producer()).definition(entry)):Optional.<DefinitionEvent>empty();
            var captures=new ArrayList<StorageValueFact.Capture>();
            trace.captures().forEach((event,reads)->{
                var prepared=eventDetails.get(event);var definition=prepared.definition(entry);
                for(var read:reads)captures.add(new StorageValueFact.Capture(definition,ProgramPoint.before(entry,prepared.operation().header().id()),read.sourceRange().in(entry),prepared.target().location().in(entry),read.sourceContribution().in(entry),read.destinationContribution().in(entry)));
            });
            var publicGaps=List.<StorageValueFact.SourceGap>of();
            Optional<StorageValueFact.LogicalCapture> logicalCapture=Optional.empty();
            if(!trace.logicalSupports().isEmpty()) {
                var assign=(Operations.Assign)eventDetails.get(trace.producer()).operation();
                var object=trace.logicalSupports().iterator().next().object();
                var supports=trace.logicalSupports().stream().map(s->events.get(s.event())).distinct().sorted(Comparator.comparing(ValueFact.Support::evidence,StorageValueOrder.ID).thenComparing(ValueFact.Support::origin,StorageValueOrder.ID)).toList();
                logicalCapture=Optional.of(new StorageValueFact.LogicalCapture(object,ProgramPoint.before(entry,assign.header().id()),supports));
            }
            return new StorageValueFact.Fragment(trace.observed().in(entry),kind,trace.bytes(),producer,unknownWriter,captures.stream().distinct().sorted(StorageValueOrder.CAPTURE).toList(),publicGaps,trace.reasons().stream().sorted().toList(),logicalCapture);
        }
    }
    private boolean sourceOpen(PointQuery<StorageSubject> query) {
        var store=session.index().store();var unit=session.index().unit(query.point().entry().unit());
        return store.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!store.coverage().uncertainties().isEmpty()
            ||unit.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!unit.coverage().uncertainties().isEmpty()||controlOpen.contains(unit.id())
            ||!session.context(query.point().entry()).entry().state().uncertainties().isEmpty()
            ||effects.storage().explicitObjects(query.subject()).stream().map(session.index()::object)
                .anyMatch(object->object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values()));
    }
    private static boolean open(Evidence.Claim c){return c.status()!=Evidence.PrecisionStatus.EXACT&&c.status()!=Evidence.PrecisionStatus.NOT_APPLICABLE;}
    private static <T extends Id> List<T> ordered(Collection<T> ids){return ids.stream().distinct().sorted(StorageValueOrder.ID).toList();}
}
