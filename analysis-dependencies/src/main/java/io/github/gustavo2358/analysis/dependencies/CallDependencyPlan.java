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
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session){return select(session,"call",false);}
    /** Explicit registration namespace and duplicate requests support composition/testing of shared W4 batches. */
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session,String namespace,boolean duplicateQuery) {
        // Only the indexed Invoke bucket is inspected, once, to avoid demanding values for literal-only units.
        var cicsAreas=new HashSet<OperationId>();var groups=new HashMap<UnitId,Set<Integer>>();var slicedUnits=new HashSet<UnitId>();
        for(var site:session.index().sites(Operations.Invoke.class)) {
            var invoke=(Operations.Invoke)site.operation();if(selected(invoke)) {
                if(readable(invoke)) {
                    var place=((Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name()).place();
                    if(cicsArea(place,session))cicsAreas.add(invoke.header().id());
                }
                groups.computeIfAbsent(site.owner().id(),ignored->new HashSet<>()).add(group(invoke));
                if(readable(invoke)&&!(((Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name()).place() instanceof Places.ObjectPlace))slicedUnits.add(site.owner().id());
            }
        }
        var registrations=new ArrayList<ConsumerRegistration<DependencySiteFact>>();
        // Result assignments belong to normal-return edges. Select the existing regional
        // provider that models those edges, including Cell storage, without changing solvers.
        boolean regional=session.index().hasUnprovedPreconditions() || session.index().publication().storage().stream().anyMatch(Memory.Region.class::isInstance)
            ||session.index().publication().capabilities().required().contains(Capabilities.ENTRY_POSSIBILITIES_V2)
            ||session.index().sites(Operations.Invoke.class).stream().anyMatch(s->!((Operations.Invoke)s.operation()).results().isEmpty());
        // Probe the optimization's semantic admission, not a keyword/feature list.
        // This prepares no solver run. A wider existing domain retains evidence on refusal.
        if(!regional && groups.values().stream().anyMatch(g->g.contains(1)))
            regional=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE).status()==PossibleValuesAnalysis.Status.UNSUPPORTED;
        for(var context:session.contexts()) {
            var entry=context.entry().id();String id=part(entry.publication().localId())+part(entry.unit().localId())+part(entry.localId());
            var reach=ReachabilityProvider.batch("reach:"+id,entry);boolean physical=slicedUnits.contains(entry.unit());
            var storageValues=StorageValuesProvider.batch("call-values:"+id,StorageValuesProvider.key(entry));
            ObservationBatchId<ObjectId,? extends TextValueFact> values=regional
                ?RegionalValuesProvider.batch("call-values:"+id,RegionalValuesProvider.key(entry))
                :PossibleValuesProvider.batch("call-values:"+id,PossibleValuesProvider.key(entry,PossibleValuesAnalysis.EFFECTS_PROFILE));
            for(int group:groups.getOrDefault(entry.unit(),Set.of())) {
                var keys=new ArrayList<AnalysisKey>();keys.add(reach.analysisKey());var batches=new ArrayList<String>();batches.add(reach.id());
                List<SiteInterest.SiteQuery<?,?>> queries=new ArrayList<>();queries.add(new SiteInterest.SiteQuery<>(reach,CallDependencyPlan::reachQuery));
                if(group==1) {
                    if(physical) {
                        keys.add(storageValues.analysisKey());batches.add(storageValues.id());
                        var query=new SiteInterest.SiteQuery<>(storageValues,CallDependencyPlan::storageQuery);queries.add(query);if(duplicateQuery)queries.add(query);
                    } else {
                        keys.add(values.analysisKey());batches.add(values.id());
                        var query=new SiteInterest.SiteQuery<>(values,CallDependencyPlan::valueQuery);queries.add(query);if(duplicateQuery)queries.add(query);
                    }
                }
                var interest=new SiteInterest(Operations.Invoke.class,entry,s->selected((Operations.Invoke)s.operation())&&group((Operations.Invoke)s.operation())==group,queries);
                registrations.add(new ConsumerRegistration<>(new ConsumerPlan(namespace+":"+id+":"+group,keys,batches),List.of(interest),List.of(),new CallDependencyConsumer(Set.copyOf(cicsAreas),reach,group==1&&!physical?values:null,group==1&&physical?storageValues:null)));
            }
        }
        return List.copyOf(registrations);
    }
    /** A closed Choice adds no area semantics: every alternative must satisfy the existing leaf proof. */
    private static boolean cicsArea(Place place,AnalysisSession session) {
        var pending=new ArrayDeque<Place>();pending.add(place);
        while(!pending.isEmpty()) {
            var current=pending.removeLast();
            if(current instanceof Places.Choice choice) {
                if(!(choice.typeRef() instanceof Types.Known type&&type.type()==Types.Builtin.TEXT)
                    ||!(choice.remainder() instanceof Scopes.NoMemory)||choice.candidates().isEmpty())return false;
                pending.addAll(choice.candidates());
            } else {
                var binding=current instanceof Places.ObjectPlace object?session.index().object(object.object()).storage():null;
                if(!CicsNameInterpreter.area(current,binding))return false;
            }
        }
        return true;
    }
    private static String part(String text){return text.length()+":"+text;}
}
