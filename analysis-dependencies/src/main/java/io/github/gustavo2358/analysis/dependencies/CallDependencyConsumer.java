package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.ObservationBatchId;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.ValueFact;
import io.github.gustavo2358.analysis.values.TextValueFact;
import java.util.*;
import io.github.gustavo2358.analysis.storage.StorageSubject;
import io.github.gustavo2358.analysis.values.StorageValueFact;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Lookup-only consumer of the observations declared by CallDependencyPlan before execution. */
final class CallDependencyConsumer implements FactConsumer<DependencySiteFact> {
    private final Set<OperationId> cicsAreas;
    private final ObservationBatchId<LabelId,ReachabilityProvider.Fact> reach;
    private final ObservationBatchId<ObjectId,? extends TextValueFact> values;
    private final ObservationBatchId<StorageSubject,StorageValueFact> storageValues;
    CallDependencyConsumer(Set<OperationId> cicsAreas,ObservationBatchId<LabelId,ReachabilityProvider.Fact> reach,ObservationBatchId<ObjectId,? extends TextValueFact> values,ObservationBatchId<StorageSubject,StorageValueFact> storageValues){this.cicsAreas=cicsAreas;this.reach=reach;this.values=values;this.storageValues=storageValues;}
    public void consume(SiteView site,PreparedFacts facts,FactSink<DependencySiteFact> sink) {
        try { consumePrepared(site,facts,sink); }
        catch(ConsumerException incomplete) {
            var observation=facts.lookup(reach,CallDependencyPlan.reachQuery(site));
            var known=observation.status()==PreparedFacts.LookupStatus.AVAILABLE&&observation.observation().status()==ObservationBatch.QueryStatus.VALUE
                ?Optional.of(observation.observation().value()):Optional.<ReachabilityProvider.Fact>empty();
            sink.emit(partial(site,known,List.of("CONSUMER_OBLIGATION_INCOMPLETE: "+incomplete.getMessage())));
        }
    }
    private void consumePrepared(SiteView site,PreparedFacts facts,FactSink<DependencySiteFact> sink) {
        var invoke=(Operations.Invoke)site.operation();boolean computed=invoke.target() instanceof Interactions.ComputedTarget;
        var reachable=lookup(facts,reach,CallDependencyPlan.reachQuery(site));
        OriginId targetOrigin;Interactions.NamePolicy policy;String literal=null;String namespace;
        if(invoke.target() instanceof Interactions.LiteralTarget target){targetOrigin=target.origin();policy=target.namePolicy();namespace=target.namespace();literal=target.name();}
        else {var target=(Interactions.ComputedTarget)invoke.target();targetOrigin=target.origin();policy=target.namePolicy();namespace=target.namespace();}
        boolean cics=namespace.equals("cics.program");
        String command=cics?(invoke.action().equals("call")?"LINK":invoke.action().equals("execute")?"XCTL":"UNKNOWN"):"CALL";
        String nameProfile=cics?(policy instanceof Interactions.ExtensionName extension?extension.name()+"@"+extension.version():"unknown"):CallNameInterpreter.PROFILE;
        var raw=new ArrayList<RawCandidate>();var candidates=new ArrayList<Candidate>();
        var evidence=new LinkedHashSet<Id>();var origins=new LinkedHashSet<OriginId>();var premises=new LinkedHashSet<PremiseId>();
        evidence.add(site.operationId());origins.add(site.origin());origins.add(targetOrigin);
        Boolean model=null;boolean source=reachable.sourceUnknownRemainder();boolean interpretation=policy instanceof Interactions.UnknownName||cics&&(command.equals("UNKNOWN")||!(policy instanceof Interactions.ExtensionName e&&e.name().equals("cics-ts.program")&&e.version().equals("1")));
        if(invoke.target() instanceof Interactions.ComputedTarget t&&t.name() instanceof Expressions.Read read
            &&read.place() instanceof Places.Choice choice&&choice.typeRef() instanceof Types.UnknownType)interpretation=true;
        if(cics&&computed&&!cicsAreas.contains(site.operationId()))interpretation=true;
        ObjectId subject=null;ProgramPoint point=null;TargetStatus status;
        if(!reachable.reachable())status=TargetStatus.UNREACHABLE_IN_MODEL;
        else if(computed&&(!CallDependencyPlan.readable(invoke))){status=TargetStatus.UNSUPPORTED_TARGET_EXPRESSION;interpretation=true;}
        else {
            if(computed) {
                // Reconstruct the same immutable lookup key; PreparedFacts cannot request or execute work.
                TextValueFact value;
                if(storageValues!=null) {
                    var query=CallDependencyPlan.storageQuery(site);point=query.point();
                    if(query.subject() instanceof StorageSubject.NamedObject object)subject=object.object();
                    value=lookup(facts,storageValues,query);
                } else {
                    var query=CallDependencyPlan.valueQuery(site);subject=query.subject();point=query.point();value=lookup(facts,values,query);
                }
                if(value.reachability()!=ValueFact.Reachability.REACHABLE)throw new ConsumerException("reachability/value disagreement");
                model=value.modelValueRemainder();source=value.sourceUnknownRemainder();evidence.addAll(value.evidence());origins.addAll(value.provenance());premises.addAll(value.premises());
                for(var candidate:value.candidateSupports())raw.add(new RawCandidate(candidate.candidate().value(),candidate.producers().stream().map(s->new Support(SupportKind.VALUE_PRODUCER,s.evidence(),s.origin(),s.premises())).toList()));
                if(raw.size()!=value.candidates().size())throw new ConsumerException("candidate support association missing");
            } else {model=false;raw.add(new RawCandidate(literal,List.of(new Support(cics?SupportKind.CICS_LITERAL:SupportKind.CALL_LITERAL,site.operationId(),targetOrigin,List.of()))));}
            for(var candidate:raw) {
                var interpreted=cics?CicsNameInterpreter.interpret(candidate.rawValue(),computed,policy):CallNameInterpreter.interpret(candidate.rawValue(),computed,policy);interpretation|=interpreted.unknownRemainder();
                if(interpreted.referenceName()!=null)candidates.add(new Candidate(interpreted.referenceName(),candidate.rawValue(),candidate.supports()));
            }
            status=candidates.isEmpty()?TargetStatus.OPEN_TARGET:TargetStatus.RESOLVED_CANDIDATES;
        }
        if(computed&&CallDependencyPlan.readable(invoke)&&point==null) {
            if(storageValues!=null){var query=CallDependencyPlan.storageQuery(site);point=query.point();if(query.subject() instanceof StorageSubject.NamedObject object)subject=object.object();}
            else {var query=CallDependencyPlan.valueQuery(site);subject=query.subject();point=query.point();}
        }
        raw.sort(Comparator.comparing(RawCandidate::rawValue));candidates.sort(Comparator.comparing(Candidate::referenceName).thenComparing(Candidate::rawValue));
        var uncertainties=new LinkedHashSet<>(invoke.header().uncertainties());if(invoke.target() instanceof Interactions.ComputedTarget t&&t.name() instanceof Expressions.Read read
            &&read.place() instanceof Places.Choice choice&&choice.typeRef() instanceof Types.UnknownType unknown)uncertainties.add(unknown.uncertainty());if(policy instanceof Interactions.UnknownName unknown)uncertainties.add(unknown.uncertainty());
        sink.emit(new DependencySiteFact(site.entry().unit(),site.entry(),site.sequence(),site.operationId(),site.offset(),site.origin(),targetOrigin,cics?"CICS":"COBOL",command,namespace,nameProfile,
            computed?TargetKind.COMPUTED:TargetKind.LITERAL,subject,point,reachable.reachable()?Reachability.REACHABLE:Reachability.UNREACHABLE_IN_MODEL,status,raw,candidates,
            model,source,interpretation,Boolean.TRUE.equals(model)||source||interpretation,reachable.controlUnknown()||invoke.outcomes().remainder() instanceof Scopes.WithinControl,
            List.copyOf(evidence),List.copyOf(origins),List.copyOf(premises),List.copyOf(uncertainties)));
    }
    /** Inventoried evidence with explicit missing analysis; never evaluates a computed expression. */
    static DependencySiteFact partial(SiteView site,Optional<ReachabilityProvider.Fact> known,List<String> reasons) {
        var invoke=(Operations.Invoke)site.operation();var literal=invoke.target() instanceof Interactions.LiteralTarget;
        var targetOrigin=literal?((Interactions.LiteralTarget)invoke.target()).origin():((Interactions.ComputedTarget)invoke.target()).origin();
        var namespace=literal?((Interactions.LiteralTarget)invoke.target()).namespace():((Interactions.ComputedTarget)invoke.target()).namespace();
        var policy=literal?((Interactions.LiteralTarget)invoke.target()).namePolicy():((Interactions.ComputedTarget)invoke.target()).namePolicy();
        boolean cics=namespace.equals("cics.program");
        String command=cics?(invoke.action().equals("call")?"LINK":invoke.action().equals("execute")?"XCTL":"UNKNOWN"):"CALL";
        String profile=cics?(policy instanceof Interactions.ExtensionName e?e.name()+"@"+e.version():"unknown"):CallNameInterpreter.PROFILE;
        var reachable=known.map(f->f.reachable()?Reachability.REACHABLE:Reachability.UNREACHABLE_IN_MODEL).orElse(Reachability.UNKNOWN);
        var raw=new ArrayList<RawCandidate>();var candidates=new ArrayList<Candidate>();boolean interpretation=policy instanceof Interactions.UnknownName;
        if(literal&&reachable!=Reachability.UNREACHABLE_IN_MODEL) {
            var name=((Interactions.LiteralTarget)invoke.target()).name();
            var support=List.of(new Support(cics?SupportKind.CICS_LITERAL:SupportKind.CALL_LITERAL,site.operationId(),targetOrigin,List.of()));
            raw.add(new RawCandidate(name,support));
            var interpreted=cics?CicsNameInterpreter.interpret(name,false,policy):CallNameInterpreter.interpret(name,false,policy);
            interpretation|=interpreted.unknownRemainder();
            if(interpreted.referenceName()!=null)candidates.add(new Candidate(interpreted.referenceName(),name,support));
        }
        ObjectId subject=null;ProgramPoint point=null;
        if(!literal&&CallDependencyPlan.readable(invoke)) {
            point=ProgramPoint.before(site.entry(),site.operationId());
            var place=((Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name()).place();
            if(place instanceof Places.ObjectPlace object)subject=object.object();
        }
        var status=reachable==Reachability.UNREACHABLE_IN_MODEL?TargetStatus.UNREACHABLE_IN_MODEL:
            !literal?TargetStatus.ANALYSIS_INCOMPLETE:candidates.isEmpty()?TargetStatus.OPEN_TARGET:TargetStatus.RESOLVED_CANDIDATES;
        var uncertainties=new LinkedHashSet<>(invoke.header().uncertainties());
        if(policy instanceof Interactions.UnknownName unknown)uncertainties.add(unknown.uncertainty());
        return new DependencySiteFact(site.entry().unit(),site.entry(),site.sequence(),site.operationId(),site.offset(),site.origin(),targetOrigin,
            cics?"CICS":"COBOL",command,namespace,profile,literal?TargetKind.LITERAL:TargetKind.COMPUTED,subject,point,reachable,status,raw,candidates,
            reachable==Reachability.UNREACHABLE_IN_MODEL?null:!literal,true,interpretation,true,
            known.isEmpty()||known.get().controlUnknown()||invoke.outcomes().remainder() instanceof Scopes.WithinControl,
            List.of(site.operationId()),new ArrayList<>(new LinkedHashSet<>(List.of(site.origin(),targetOrigin))),List.of(),List.copyOf(uncertainties),AnalysisStatus.PARTIAL,
            reasons.stream().distinct().sorted().toList());
    }
    private static <T,V> V lookup(PreparedFacts facts,ObservationBatchId<T,V> batch,PointQuery<T> query) {
        var result=facts.lookup(batch,query);
        if(result.status()!=PreparedFacts.LookupStatus.AVAILABLE||result.observation().status()!=ObservationBatch.QueryStatus.VALUE)throw new ConsumerException("planned observation unavailable");
        return result.observation().value();
    }
}
