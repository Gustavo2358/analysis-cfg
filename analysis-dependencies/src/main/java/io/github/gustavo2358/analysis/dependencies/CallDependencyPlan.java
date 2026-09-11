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

/** Registers separate literal/computed consumers through the existing W4 planner. */
public final class CallDependencyPlan {
    private CallDependencyPlan(){ }
    public static boolean selected(Operations.Invoke i) {
        return switch(i.target()) {
            case Interactions.LiteralTarget t -> t.category().equals("program")&&t.namespace().equals("cobol.program");
            case Interactions.ComputedTarget t -> t.category().equals("program")&&t.namespace().equals("cobol.program");
            default -> false;
        };
    }
    static boolean readable(Operations.Invoke i){return i.target() instanceof Interactions.ComputedTarget t&&t.name() instanceof Expressions.Read r&&r.place() instanceof Places.ObjectPlace;}
    static boolean shape(Operations.Invoke i){return i.arguments().isEmpty()&&i.results().isEmpty();}
    static int group(Operations.Invoke i){return !shape(i)?2:i.target() instanceof Interactions.LiteralTarget?0:readable(i)?1:2;}
    static PointQuery<ObjectId> valueQuery(SiteView site) {
        var target=(Interactions.ComputedTarget)((Operations.Invoke)site.operation()).target();
        var object=(Places.ObjectPlace)((Expressions.Read)target.name()).place();
        return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),object.object());
    }
    static PointQuery<LabelId> reachQuery(SiteView site){return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),site.sequence());}
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session){return select(session,"call",false);}
    /** Explicit registration namespace and duplicate requests support composition/testing of shared W4 batches. */
    public static List<ConsumerRegistration<DependencySiteFact>> select(AnalysisSession session,String namespace,boolean duplicateQuery) {
        // Only the indexed Invoke bucket is inspected, once, to avoid demanding values for literal-only units.
        var groups=new HashMap<UnitId,Set<Integer>>();
        for(var site:session.index().sites(Operations.Invoke.class)) {
            var invoke=(Operations.Invoke)site.operation();if(selected(invoke))groups.computeIfAbsent(site.owner().id(),ignored->new HashSet<>()).add(group(invoke));
        }
        var registrations=new ArrayList<ConsumerRegistration<DependencySiteFact>>();
        for(var context:session.contexts()) {
            var entry=context.entry().id();String id=part(entry.publication().localId())+part(entry.unit().localId())+part(entry.localId());
            var reach=ReachabilityProvider.batch("reach:"+id,entry);
            var values=PossibleValuesProvider.batch("call-values:"+id,PossibleValuesProvider.key(entry,PossibleValuesAnalysis.EFFECTS_PROFILE));
            for(int group:groups.getOrDefault(entry.unit(),Set.of())) {
                var keys=new ArrayList<AnalysisKey>();keys.add(reach.analysisKey());var batches=new ArrayList<String>();batches.add(reach.id());
                List<SiteInterest.SiteQuery<?,?>> queries=new ArrayList<>();queries.add(new SiteInterest.SiteQuery<>(reach,CallDependencyPlan::reachQuery));
                if(group==1) {
                    keys.add(values.analysisKey());batches.add(values.id());
                    var query=new SiteInterest.SiteQuery<>(values,CallDependencyPlan::valueQuery);queries.add(query);if(duplicateQuery)queries.add(query);
                }
                var interest=new SiteInterest(Operations.Invoke.class,entry,s->selected((Operations.Invoke)s.operation())&&group((Operations.Invoke)s.operation())==group,queries);
                registrations.add(new ConsumerRegistration<>(new ConsumerPlan(namespace+":"+id+":"+group,keys,batches),List.of(interest),List.of(),new CallDependencyConsumer(reach,group==1?values:null)));
            }
        }
        return List.copyOf(registrations);
    }
    private static String part(String text){return text.length()+":"+text;}
}
