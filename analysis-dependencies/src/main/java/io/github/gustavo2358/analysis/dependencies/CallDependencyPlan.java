package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.ConsumerRegistration;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
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
        if(!(i.target() instanceof Interactions.ComputedTarget t&&t.name() instanceof Expressions.Read r))return false;
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
                "scalarSelections","possibleValuesQueries","deduplicatedValueQueries","sourceQualifiedResolvedByExistingQuery"))metrics.put(key,0L);
        var invokes=session.index().sites(Operations.Invoke.class).stream().filter(s->selected((Operations.Invoke)s.operation())).toList();
        for(var context:session.contexts()) {
            var entry=context.entry().id();String id=part(entry.publication().localId())+part(entry.unit().localId())+part(entry.localId());
            var admission=new HashMap<ObjectId,Boolean>();var demand=new HashSet<ObjectId>();
            var selected=new EnumMap<Provider,Set<OperationId>>(Provider.class);
            var queries=new HashSet<PointQuery<ObjectId>>();
            var scoped=session.selectEntries(List.of(entry));
            for(var indexed:invokes) {
                if(!indexed.owner().id().equals(entry.unit()))continue;
                var invoke=(Operations.Invoke)indexed.operation();Provider provider;
                metrics.merge("targetResolutionRequests",1L,Long::sum);
                if(invoke.target() instanceof Interactions.LiteralTarget)provider=Provider.LITERAL;
                else {
                    metrics.merge("qualifiedComputedOccurrences",1L,Long::sum);
                    if(!readable(invoke))provider=Provider.UNRESOLVED;
                    else if(((Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name()).place() instanceof Places.ObjectPlace object) {
                        boolean accepted=admission.computeIfAbsent(object.object(),subject->{
                            metrics.merge("scalarAdmissionAttempts",1L,Long::sum);
                            var probe=PossibleValuesAnalysis.prepare(scoped,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(subject));
                            if(probe.status()==PossibleValuesAnalysis.Status.INVALID_INPUT)throw new DependencyAnalysis.Failure(DependencyAnalysis.Kind.INVALID_INPUT,probe.reason());
                            boolean ok=probe.status()==PossibleValuesAnalysis.Status.ACCEPTED;
                            metrics.merge(ok?"scalarAdmissionAccepted":"scalarAdmissionRefused",1L,Long::sum);return ok;
                        });
                        provider=accepted?Provider.SCALAR:Provider.REGIONAL;
                        if(accepted)demand.add(object.object());
                        queries.add(new PointQuery<>(ProgramPoint.before(entry,invoke.header().id()),object.object()));
                    } else provider=Provider.STORAGE;
                }
                providers.put(new SiteKey(entry,invoke.header().id()),provider);
                selected.computeIfAbsent(provider,k->new HashSet<>()).add(invoke.header().id());
                if(provider==Provider.SCALAR)metrics.merge("scalarSelections",1L,Long::sum);
                if(provider==Provider.REGIONAL||provider==Provider.STORAGE)metrics.merge("regionalSelections",1L,Long::sum);
            }
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
        return new Selection(registrations,providers,metrics);
    }
    private static String part(String text){return text.length()+":"+text;}
}
