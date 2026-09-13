package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.ObservationBatchId;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.ValueFact;
import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Lookup-only consumer of the observations declared by CallDependencyPlan before execution. */
final class CallDependencyConsumer implements FactConsumer<DependencySiteFact> {
    private final ObservationBatchId<LabelId,ReachabilityProvider.Fact> reach;
    private final ObservationBatchId<ObjectId,ValueFact> values;
    CallDependencyConsumer(ObservationBatchId<LabelId,ReachabilityProvider.Fact> reach,ObservationBatchId<ObjectId,ValueFact> values){this.reach=reach;this.values=values;}
    public void consume(SiteView site,PreparedFacts facts,FactSink<DependencySiteFact> sink) {
        var invoke=(Operations.Invoke)site.operation();boolean computed=invoke.target() instanceof Interactions.ComputedTarget;
        var reachable=lookup(facts,reach,CallDependencyPlan.reachQuery(site));
        OriginId targetOrigin;Interactions.NamePolicy policy;String literal=null;
        if(invoke.target() instanceof Interactions.LiteralTarget target){targetOrigin=target.origin();policy=target.namePolicy();literal=target.name();}
        else {var target=(Interactions.ComputedTarget)invoke.target();targetOrigin=target.origin();policy=target.namePolicy();}
        var raw=new ArrayList<RawCandidate>();var candidates=new ArrayList<Candidate>();
        var evidence=new LinkedHashSet<Id>();var origins=new LinkedHashSet<OriginId>();var premises=new LinkedHashSet<PremiseId>();
        evidence.add(site.operationId());origins.add(site.origin());origins.add(targetOrigin);
        Boolean model=null;boolean source=reachable.sourceUnknownRemainder();boolean interpretation=policy instanceof Interactions.UnknownName;
        ObjectId subject=null;ProgramPoint point=null;TargetStatus status;
        if(!reachable.reachable())status=TargetStatus.UNREACHABLE_IN_MODEL;
        else if(!CallDependencyPlan.shape(invoke)){status=TargetStatus.UNSUPPORTED_INVOCATION_SHAPE;interpretation=true;}
        else if(computed&&!CallDependencyPlan.readable(invoke)){status=TargetStatus.UNSUPPORTED_TARGET_EXPRESSION;interpretation=true;}
        else {
            if(computed) {
                // Reconstruct the same immutable lookup key; PreparedFacts cannot request or execute work.
                var query=CallDependencyPlan.valueQuery(site);subject=query.subject();point=query.point();
                var value=lookup(facts,values,query);
                if(value.reachability()!=ValueFact.Reachability.REACHABLE)throw new ConsumerException("reachability/value disagreement");
                model=value.modelValueRemainder();source=value.sourceUnknownRemainder();evidence.addAll(value.evidence());origins.addAll(value.provenance());premises.addAll(value.premises());
                for(var candidate:value.candidateSupports())raw.add(new RawCandidate(candidate.candidate().value(),candidate.producers().stream().map(s->new Support(SupportKind.VALUE_PRODUCER,s.evidence(),s.origin(),s.premises())).toList()));
                if(raw.size()!=value.candidates().size())throw new ConsumerException("candidate support association missing");
            } else {model=false;raw.add(new RawCandidate(literal,List.of(new Support(SupportKind.CALL_LITERAL,site.operationId(),targetOrigin,List.of()))));}
            for(var candidate:raw) {
                var interpreted=CallNameInterpreter.interpret(candidate.rawValue(),computed,policy);interpretation|=interpreted.unknownRemainder();
                if(interpreted.referenceName()!=null)candidates.add(new Candidate(interpreted.referenceName(),candidate.rawValue(),candidate.supports()));
            }
            status=candidates.isEmpty()?TargetStatus.OPEN_TARGET:TargetStatus.RESOLVED_CANDIDATES;
        }
        if(computed&&CallDependencyPlan.readable(invoke)&&subject==null){var query=CallDependencyPlan.valueQuery(site);subject=query.subject();point=query.point();}
        raw.sort(Comparator.comparing(RawCandidate::rawValue));candidates.sort(Comparator.comparing(Candidate::referenceName).thenComparing(Candidate::rawValue));
        var uncertainties=new LinkedHashSet<>(invoke.header().uncertainties());if(policy instanceof Interactions.UnknownName unknown)uncertainties.add(unknown.uncertainty());
        sink.emit(new DependencySiteFact(site.entry().unit(),site.entry(),site.sequence(),site.operationId(),site.offset(),site.origin(),targetOrigin,
            computed?TargetKind.COMPUTED:TargetKind.LITERAL,subject,point,reachable.reachable()?Reachability.REACHABLE:Reachability.UNREACHABLE_IN_MODEL,status,raw,candidates,
            model,source,interpretation,Boolean.TRUE.equals(model)||source||interpretation,reachable.controlUnknown()||invoke.outcomes().remainder() instanceof Scopes.WithinControl,
            List.copyOf(evidence),List.copyOf(origins),List.copyOf(premises),List.copyOf(uncertainties)));
    }
    private static <T,V> V lookup(PreparedFacts facts,ObservationBatchId<T,V> batch,PointQuery<T> query) {
        var result=facts.lookup(batch,query);
        if(result.status()!=PreparedFacts.LookupStatus.AVAILABLE||result.observation().status()!=ObservationBatch.QueryStatus.VALUE)throw new ConsumerException("planned observation unavailable");
        return result.observation().value();
    }
}
