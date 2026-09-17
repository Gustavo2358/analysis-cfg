package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.FileDependencyResult.*;

/** Lookup-only FILE consumer; orchestration and future general value planning remain outside. */
final class FileDependencyConsumer implements FactConsumer<Site> {
    private final Map<OperationId,List<Binding>> bindings;
    private final ObservationBatchId<LabelId,ReachabilityProvider.Fact> reach;
    FileDependencyConsumer(Map<OperationId,List<Binding>> bindings,ObservationBatchId<LabelId,ReachabilityProvider.Fact> reach){this.bindings=bindings;this.reach=reach;}
    static boolean selected(Operation operation,Set<OperationId> locals){return operation instanceof Operations.Invoke i&&externalFile(i)||locals.contains(operation.header().id());}
    static boolean externalFile(Operations.Invoke i){return i.target() instanceof Interactions.LiteralTarget t&&t.category().equals("file")||i.target() instanceof Interactions.ComputedTarget c&&c.category().equals("file");}
    static PointQuery<LabelId> query(SiteView site){return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),site.sequence());}
    public void consume(SiteView view,PreparedFacts facts,FactSink<Site> sink){
        var result=facts.lookup(reach,query(view));
        var known=result.status()==PreparedFacts.LookupStatus.AVAILABLE&&result.observation().status()==ObservationBatch.QueryStatus.VALUE?Optional.of(result.observation().value()):Optional.<ReachabilityProvider.Fact>empty();
        sink.emit(site(view,bindings,known,known.isEmpty()?List.of("FILE_REACHABILITY_UNAVAILABLE"):List.of()));
    }
    static Site site(SiteView view,Map<OperationId,List<Binding>> bindings,Optional<ReachabilityProvider.Fact> known,List<String> missing){
        var reachable=known.map(r->r.reachable()?Reachability.REACHABLE:Reachability.UNREACHABLE_IN_MODEL).orElse(Reachability.UNKNOWN);
        if(!(view.operation() instanceof Operations.Invoke candidate&&externalFile(candidate))) {
            var header=view.operation().header();
            return new Site(view.entry().unit(),view.entry(),view.sequence(),view.operationId(),"resource-use",null,"LOCAL",bindings.getOrDefault(view.operationId(),List.of()),null,List.of(),false,reachable,header.precision().effects().status(),header.precision().control().status(),view.origin(),view.origin(),header.uncertainties(),missing);
        }
        var invoke=(Operations.Invoke)view.operation();boolean literal=invoke.target() instanceof Interactions.LiteralTarget;
        String namespace=literal?((Interactions.LiteralTarget)invoke.target()).namespace():((Interactions.ComputedTarget)invoke.target()).namespace();
        var policy=literal?((Interactions.LiteralTarget)invoke.target()).namePolicy():((Interactions.ComputedTarget)invoke.target()).namePolicy();
        var origin=literal?((Interactions.LiteralTarget)invoke.target()).origin():((Interactions.ComputedTarget)invoke.target()).origin();
        var candidates=new ArrayList<Candidate>();var reasons=new TreeSet<>(missing);
        boolean nameKnown=literal&&policy instanceof Interactions.ExactName;
        if(nameKnown&&reachable!=Reachability.UNREACHABLE_IN_MODEL){var value=((Interactions.LiteralTarget)invoke.target()).name();candidates.add(new Candidate(value,value,List.of(new Support("FILE_LITERAL",view.operationId(),origin,List.of()))));}
        if(!literal)reasons.add("FILE_VALUES_NOT_YET_ANALYZED");else if(!nameKnown)reasons.add("FILE_NAME_POLICY_UNSUPPORTED");
        var uncertainties=new LinkedHashSet<>(invoke.header().uncertainties());if(policy instanceof Interactions.UnknownName n)uncertainties.add(n.uncertainty());
        return new Site(view.entry().unit(),view.entry(),view.sequence(),view.operationId(),invoke.action(),namespace,literal?"LITERAL":"COMPUTED",bindings.getOrDefault(view.operationId(),List.of()),literal?null:ProgramPoint.before(view.entry(),view.operationId()),candidates,!nameKnown,reachable,invoke.header().precision().effects().status(),invoke.header().precision().control().status(),view.origin(),origin,List.copyOf(uncertainties),List.copyOf(reasons));
    }
}
