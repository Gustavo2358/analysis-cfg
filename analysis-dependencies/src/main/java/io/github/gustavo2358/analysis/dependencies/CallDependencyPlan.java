package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.ConsumerRegistration;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.values.*;
import java.util.*;
import io.github.gustavo2358.analysis.storage.*;

/** Registers separate literal/computed consumers through the existing W4 planner. */
public final class CallDependencyPlan {
    private CallDependencyPlan(){ }
    public static boolean selected(Operations.Invoke i) {
        return switch(i.target()) {
            case Interactions.LiteralTarget t -> t.category().equals("program")&&(t.namespace().equals("cobol.program")||t.namespace().equals("cics.program"));
            case Interactions.ComputedTarget t -> t.category().equals("program")&&(t.namespace().equals("cobol.program")||t.namespace().equals("cics.program"));
            default -> false;
        };
    }
    static boolean readable(Operations.Invoke i) {
        return readable(i.target());
    }
    private static boolean readable(Interactions.Target target) {
        if(!(target instanceof Interactions.ComputedTarget t&&t.name() instanceof Expressions.Read r))return false;
        return r.place() instanceof Places.ObjectPlace||r.place() instanceof Places.Choice||r.place() instanceof Places.RegionSlice slice
            &&slice.offset() instanceof Expressions.Literal offset&&offset.value() instanceof Values.IntValue
            &&slice.length() instanceof Expressions.Literal length&&length.value() instanceof Values.IntValue;
    }
    static int group(Operations.Invoke i){return i.target() instanceof Interactions.LiteralTarget?0:readable(i)?1:2;}
    static PointQuery<ObjectId> valueQuery(SiteView site) {
        var target=(Interactions.ComputedTarget)((Operations.Invoke)site.operation()).target();
        var object=(Places.ObjectPlace)((Expressions.Read)target.name()).place();
        return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),object.object());
    }
    static PointQuery<StorageSubject> storageQuery(SiteView site) {
        var read=(Expressions.Read)((Interactions.ComputedTarget)((Operations.Invoke)site.operation()).target()).name();
        StorageSubject subject;
        if(read.place() instanceof Places.ObjectPlace object)subject=new StorageSubject.NamedObject(object.object());
        else if(read.place() instanceof Places.Choice choice)subject=new StorageSubject.PlaceOccurrence(choice.header().id());
        else {
            var slice=(Places.RegionSlice)read.place();var offset=((Values.IntValue)((Expressions.Literal)slice.offset()).value()).value();
            var length=((Values.IntValue)((Expressions.Literal)slice.length()).value()).value();
            subject=new StorageSubject.PhysicalRange(slice.region(),StorageRange.exact(offset,length),slice.codec());
        }
        return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),subject);
    }
    static PointQuery<LabelId> reachQuery(SiteView site){return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),site.sequence());}
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session){return select(session,StorageAnalysisMode.LOGICAL_ONLY);}
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session,StorageAnalysisMode mode){return select(session,"call",false,mode);}
    /** Explicit registration namespace and duplicate requests support composition/testing of shared W4 batches. */
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session,String namespace,boolean duplicateQuery) { return select(session,namespace,duplicateQuery,StorageAnalysisMode.LOGICAL_ONLY); }
    public enum Provider { LITERAL, SCALAR, REGIONAL, STORAGE, UNRESOLVED }
    public record SiteKey(EntryId entry,OperationId operation) { }
    public record Selection(List<ConsumerRegistration<DependencySiteFact>> registrations,
                            Map<SiteKey,Provider> providers,Map<String,Long> metrics) {
        public Selection { registrations=List.copyOf(registrations);providers=Map.copyOf(providers);metrics=Map.copyOf(metrics); }
    }
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session,String namespace,boolean duplicateQuery,StorageAnalysisMode mode) {
        return choose(session,namespace,duplicateQuery,mode).registrations();
    }
    /** Admission belongs to the exact Entry/subject. Accepted demands share one execution per Entry. */
    public static Selection choose(AnalysisSession session,String namespace,boolean duplicateQuery,StorageAnalysisMode mode) {
        Objects.requireNonNull(mode);
        var registrations=new ArrayList<ConsumerRegistration<DependencySiteFact>>();
        var providers=new HashMap<SiteKey,Provider>();var metrics=new TreeMap<String,Long>();
        for(var key:List.of("qualifiedComputedOccurrences","targetResolutionRequests","scalarAdmissionAttempts",
                "scalarAdmissionAccepted","scalarAdmissionRefused","regionalAdmissionAttempts","regionalSelections",
                "scalarSelections","possibleValuesQueries","deduplicatedValueQueries","sourceQualifiedResolvedByExistingQuery","entryCallCandidatesVisited"))metrics.put(key,0L);
        var invokes=new HashMap<UnitId,List<io.github.gustavo2358.analysis.structure.ProgramIndex.Site>>();
        var nativeProgram=session.index().store() instanceof SnapshotProgram program?program:null;
        for(var site:session.index().sites(Operations.Invoke.class))if(nativeProgram!=null
            ?nativeProgram.plainDependencyOccurrenceFlags(nativeProgram.dependencyCallSource(site))!=0
            :selected((Operations.Invoke)site.operation()))
            invokes.computeIfAbsent(site.owner().id(),ignored->new ArrayList<>()).add(site);
        for(var context:session.contexts()) {
            var entry=context.entryView().id();String id=part(entry.publication().localId())+part(entry.unit().localId())+part(entry.localId());
            try(var admission=new ScalarAdmissions(session.index().store())) {
            var demand=new HashSet<ObjectId>();
            var selected=new EnumMap<Provider,Set<OperationId>>(Provider.class);
            var queries=new HashSet<PointQuery<ObjectId>>();
            var scoped=session.selectEntries(List.of(entry));
            for(var indexed:invokes.getOrDefault(entry.unit(),List.of())) {
                metrics.merge("entryCallCandidatesVisited",1L,Math::addExact);
                long source=nativeProgram==null?0:nativeProgram.dependencyCallSource(indexed);
                int flags=nativeProgram==null?0:nativeProgram.plainDependencyOccurrenceFlags(source);
                var invoke=nativeProgram==null?(Operations.Invoke)indexed.operation():null;
                OperationId operation=nativeProgram==null?invoke.header().id():nativeProgram.dependencyCallIdentity(source);
                // Literal choice observes no target spelling/policy/body. A computed
                // target still supplies the exact original expression to admission.
                var target=nativeProgram==null?invoke.target():(flags&1)!=0?null:nativeProgram.dependencyCallTarget(source);
                Provider provider;
                metrics.merge("targetResolutionRequests",1L,Long::sum);
                if((flags&1)!=0||target instanceof Interactions.LiteralTarget)provider=Provider.LITERAL;
                else {
                    metrics.merge("qualifiedComputedOccurrences",1L,Long::sum);
                    if(!readable(target))provider=Provider.UNRESOLVED;
                    else if(((Expressions.Read)((Interactions.ComputedTarget)target).name()).place() instanceof Places.ObjectPlace object) {
                        admission.request(object.object());provider=Provider.SCALAR; // Pending exact admission, not a published decision.
                        queries.add(new PointQuery<>(ProgramPoint.before(entry,operation),object.object()));
                    } else provider=Provider.STORAGE;
                }
                providers.put(new SiteKey(entry,operation),provider);
                selected.computeIfAbsent(provider,k->new HashSet<>()).add(operation);
                if(provider==Provider.SCALAR)metrics.merge("scalarSelections",1L,Long::sum);
                if(provider==Provider.REGIONAL||provider==Provider.STORAGE)metrics.merge("regionalSelections",1L,Long::sum);
            }
            admission.prepare(scoped,metrics);
            for(var query:queries) {
                if(admission.accepted(query.subject()))demand.add(query.subject());
                else {
                    var operation=query.point().operation();
                    providers.put(new SiteKey(entry,operation),Provider.REGIONAL);
                    selected.get(Provider.SCALAR).remove(operation);
                    selected.computeIfAbsent(Provider.REGIONAL,k->new HashSet<>()).add(operation);
                    metrics.merge("scalarSelections",-1L,Long::sum);metrics.merge("regionalSelections",1L,Long::sum);
                }
            }
            if(selected.containsKey(Provider.SCALAR)&&selected.get(Provider.SCALAR).isEmpty())selected.remove(Provider.SCALAR);
            // Both wider projections use the same regional engine. After scalar
            // admission, combine wider-only demands into its storage projection.
            // This never changes an accepted scalar selection.
            if(selected.containsKey(Provider.STORAGE)&&selected.containsKey(Provider.REGIONAL)) {
                for(var operation:selected.remove(Provider.REGIONAL)) {
                    selected.get(Provider.STORAGE).add(operation);
                    providers.put(new SiteKey(entry,operation),Provider.STORAGE);
                }
            }
            metrics.merge("possibleValuesQueries",(long)queries.size(),Long::sum);
            metrics.merge("deduplicatedValueQueries",(long)queries.size(),Long::sum);
            var reach=ReachabilityProvider.batch("reach:"+id,entry);
            for(var selection:selected.entrySet()) {
                var provider=selection.getKey();var operations=Set.copyOf(selection.getValue());
                String batchId="call-values:"+id+":"+provider;
                ObservationBatchId<ObjectId,? extends TextValueFact> values=switch(provider) {
                    case SCALAR -> PossibleValuesProvider.batch(batchId,PossibleValuesProvider.key(entry,PossibleValuesAnalysis.EFFECTS_PROFILE,demand));
                    case REGIONAL -> RegionalValuesProvider.batch(batchId,RegionalValuesProvider.key(entry,mode));
                    default -> null;
                };
                var storage=provider==Provider.STORAGE?StorageValuesProvider.batch(batchId,StorageValuesProvider.key(entry,mode)):null;
                if(provider==Provider.REGIONAL||provider==Provider.STORAGE)metrics.merge("regionalAdmissionAttempts",1L,Long::sum);
                var keys=new ArrayList<AnalysisKey>();keys.add(reach.analysisKey());var batches=new ArrayList<String>();batches.add(reach.id());
                List<SiteInterest.SiteQuery<?,?>> requests=new ArrayList<>();requests.add(new SiteInterest.SiteQuery<>(reach,CallDependencyPlan::reachQuery));
                if(values!=null) {
                    keys.add(values.analysisKey());batches.add(values.id());
                    var query=new SiteInterest.SiteQuery<>(values,CallDependencyPlan::valueQuery);requests.add(query);if(duplicateQuery)requests.add(query);
                }
                if(storage!=null) {
                    keys.add(storage.analysisKey());batches.add(storage.id());
                    var query=new SiteInterest.SiteQuery<>(storage,CallDependencyPlan::storageQuery);requests.add(query);if(duplicateQuery)requests.add(query);
                }
                var interest=new SiteInterest(Operations.Invoke.class,entry,s->operations.contains(s.operationId()),requests);
                registrations.add(new ConsumerRegistration<>(new ConsumerPlan(namespace+":"+id+":"+provider,keys,batches),List.of(interest),List.of(),new CallDependencyConsumer(reach,values,storage)));
            }
            }
        }
        return new Selection(registrations,providers,metrics);
    }
    /** Exact per-subject decisions from one session/profile/Entry compiler.
     * Necessary storage rejection precedes expression compilation; it is not
     * full admission. Refusal order and INVALID_INPUT stay subject-specific.
     * ACCEPTED owns no execution, ValueUniverse or completed value facts.
     * Original execution demand and typed provider choices are preserved.
     * Operational failures propagate and never become semantic fallback.
     * First-seen request controls and the compiler retire on close. */
    private static final class ScalarAdmissions implements AutoCloseable {
        private final ProgramStore store;
        private final Map<ObjectId,Boolean> decisions=new LinkedHashMap<>();
        private ProgramStore.CapacityLease orderCapacity;
        ScalarAdmissions(ProgramStore store){this.store=store;}
        void request(ObjectId subject) {
            Objects.requireNonNull(subject);if(decisions.containsKey(subject))return;
            // New insertion-order links only; existing typed keys/query/storage
            // controls remain explicit resident debt. No paging/allstore claim.
            if(orderCapacity==null) {
                var budget=store.executionBudget();
                if(budget.isPresent()) {
                    orderCapacity=budget.orElseThrow().reserve(ProgramStore.CapacityPool.SCRATCH,160,ProgramStore.ExecutionPhase.DEMAND);
                    orderCapacity.onOwnerClose(decisions::clear);
                }
            } else orderCapacity.grow(32,ProgramStore.ExecutionPhase.DEMAND);
            decisions.put(subject,null);
        }
        void prepare(AnalysisSession session,Map<String,Long> metrics) {
            if(decisions.isEmpty())return;
            // This view borrows existing first-seen controls; no new input-sized
            // eligibility map or subject set. Full AIR admission already happened.
            // Unknown/indirect storage refuses before expression closure in the
            // original preparation, and cannot poison unrelated scalar choices.
            int eligible=0;
            for(var decision:decisions.entrySet()) {
                if(PossibleValuesAnalysis.supportsDemandStorage(session,decision.getKey()))eligible=Math.incrementExact(eligible);
                else decision.setValue(false);
            }
            if(eligible>0) {
                // Fixed borrowed-view/iterator controls, separate from first-seen
                // links. Fund before allocation; no per-subject extra capacity.
                var budget=store.executionBudget();
                var viewCapacity=eligible>1&&budget.isPresent()?budget.orElseThrow().reserve(ProgramStore.CapacityPool.SCRATCH,512,ProgramStore.ExecutionPhase.DEMAND):null;
                try(viewCapacity;var compiler=PossibleValuesAnalysis.admissionDecisions(session,PossibleValuesAnalysis.EFFECTS_PROFILE)) {
                    for(var subject:candidates()) {
                        var decision=compiler.decide(subject);
                        if(decision.status()==PossibleValuesAnalysis.Status.INVALID_INPUT)
                            throw new DependencyAnalysis.Failure(DependencyAnalysis.Kind.INVALID_INPUT,decision.reason());
                        decisions.put(subject,decision.status()==PossibleValuesAnalysis.Status.ACCEPTED);
                    }
                }
            }
            for(var decision:decisions.entrySet()) {
                store.progress(ProgramStore.ExecutionPhase.DEMAND);metrics.merge("scalarAdmissionAttempts",1L,Long::sum);
                metrics.merge(Boolean.TRUE.equals(decision.getValue())?"scalarAdmissionAccepted":"scalarAdmissionRefused",1L,Long::sum);
            }
        }
        private Set<ObjectId> candidates() {
            return new AbstractSet<ObjectId>() {
                @Override public int size(){int count=0;for(var decision:decisions.values())if(decision==null)count=Math.incrementExact(count);return count;}
                @Override public Iterator<ObjectId> iterator(){
                    var entries=decisions.entrySet().iterator();return new Iterator<>() {
                        private ObjectId next;
                        @Override public boolean hasNext(){while(next==null&&entries.hasNext()){var value=entries.next();if(value.getValue()==null)next=value.getKey();}return next!=null;}
                        @Override public ObjectId next(){if(!hasNext())throw new NoSuchElementException();var result=next;next=null;return result;}
                    };
                }
            };
        }
        boolean accepted(ObjectId subject){return Objects.requireNonNull(decisions.get(subject),"subject not admitted");}
        @Override public void close(){decisions.clear();if(orderCapacity!=null){orderCapacity.close();orderCapacity=null;}}
    }
    private static String part(String text){return text.length()+":"+text;}
}
