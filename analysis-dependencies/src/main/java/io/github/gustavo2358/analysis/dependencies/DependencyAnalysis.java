package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.query.ObservationBatch;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.PossibleValuesProvider;
import io.github.gustavo2358.analysis.values.RegionalValuesProvider;
import io.github.gustavo2358.analysis.values.StorageValuesProvider;
import java.util.*;

/** CP6 W1D application boundary. */
public final class DependencyAnalysis {
    public DependencyResult prepare(Publication publication) {
        Objects.requireNonNull(publication);
        var defaults=BuildOptions.defaults();var options=new BuildOptions(defaults.validation(),io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.PARTIAL_ANALYSIS);var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,options);
        switch(cfg.status()) {
            case CFG_BUILT -> { }
            case INVALID_IR -> throw new Failure(Kind.INVALID_INPUT,"INVALID_IR");
            case UNSUPPORTED_INPUT,UNSUPPORTED_CAPABILITY -> { return partialInventory(publication,"CFG_UNSUPPORTED"); }
            case RESOURCE_LIMIT -> throw new Failure(Kind.RESOURCE_LIMIT,"RESOURCE_LIMIT");
            case VALIDATION_LIMIT,INCOMPLETE_VALIDATION -> throw new Failure(Kind.INPUT_INCOMPLETE,"INCOMPLETE_VALIDATION");
        }
        var opened=AnalysisSession.open(cfg,publication,options.projectionPolicy(),publication.units().stream().flatMap(u->u.entries().stream()).filter(e->e.initialLabel().isPresent()).toList());
        if(opened.status()!=AnalysisSession.Status.ACCEPTED)return partialInventory(publication,opened.reason());
        var session=opened.session().orElseThrow();
        try(var execution=new PlanningExecution(session,new AnalysisRegistry(List.of(new PossibleValuesProvider(),new RegionalValuesProvider(),new StorageValuesProvider(),new ReachabilityProvider())))) {
            var plan=execution.plan(CallDependencyPlan.select(session));var result=execution.execute("dependencies@1",plan);
            for(var analysis:result.analyses())if(analysis.status()==AnalysisOutcome.Status.INVALID_INPUT)throw new Failure(Kind.INVALID_INPUT,analysis.reason());
            var reasons=new TreeSet<String>();
            if(session.index().hasUnprovedPreconditions())reasons.add("UNPROVED_OPERATION_PRECONDITION");
            if(publication.units().stream().anyMatch(u->u.body()!=Unit.BodyAvailability.AVAILABLE))reasons.add("UNIT_BODY_UNAVAILABLE");
            if(publication.units().stream().anyMatch(u->session.index().partialControl(u.id())))reasons.add("PARTIAL_CONTROL_PROJECTION");
            var entryReasons=new HashMap<EntryId,Set<String>>();
            for(var analysis:result.analyses())if(analysis.status()!=AnalysisOutcome.Status.STABLE) {
                reasons.add(analysis.reason());entryReasons.computeIfAbsent(analysis.key().entry(),ignored->new TreeSet<>()).add(analysis.reason());
            }
            if(result.preparationStatus()!=PreparedAnalysisResult.PreparationStatus.COMPLETE)reasons.add("DEPENDENCY_PREPARATION_INCOMPLETE");
            var retained=new HashMap<SiteKey,DependencySiteFact>();
            result.consumers().stream().flatMap(c->c.facts().stream()).forEach(f->retained.put(new SiteKey(f.entry(),f.operation()),f));
            var reachability=new HashMap<SiteKey,ReachabilityProvider.Fact>();
            for(var batch:result.results())if(batch.batchId().analysisKey().implementation().equals("Reachability"))
                for(var observation:batch.observations())if(observation.status()==ObservationBatch.QueryStatus.VALUE&&observation.value() instanceof ReachabilityProvider.Fact fact)
                    reachability.put(new SiteKey(observation.query().point().entry(),observation.query().point().operation()),fact);
            for(var site:inventory(publication)) {
                var key=new SiteKey(site.entry(),site.operationId());
                if(!retained.containsKey(key))retained.put(key,CallDependencyConsumer.partial(site,Optional.ofNullable(reachability.get(key)),
                    List.copyOf(entryReasons.getOrDefault(site.entry(),Set.of("DEPENDENCY_PREPARATION_INCOMPLETE")))));
            }
            var sites=retained.values().stream().map(f->session.index().partialControl(f.caller())?f.withPartialAnalysis("PARTIAL_CONTROL_PROJECTION"):f)
                .map(f->session.index().unprovedPreconditions(f.caller())?f.withPartialAnalysis("UNPROVED_OPERATION_PRECONDITION"):f).sorted(Comparator.comparing(DependencySiteFact::entry,AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).toList();
            var edges=new ArrayList<DependencyResult.Edge>();
            for(var site:sites)if(site.reachability()!=DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL)
                for(var candidate:site.candidates())edges.add(new DependencyResult.Edge(site.caller(),site.entry(),site.operation(),candidate,site.effectiveUnknownRemainder()));
            var metrics=new TreeMap<String,Long>();
            result.metrics().forEach((phase,counts)->counts.forEach((name,value)->metrics.put(phase+"."+name,value)));
            long values=result.analyses().stream().filter(a->a.key().implementation().equals(PossibleValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(RegionalValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(StorageValuesProvider.IMPLEMENTATION)).count();
            metrics.put("possibleValuesPreparations",values);metrics.put("possibleValuesRuns",result.analyses().stream().filter(a->a.status()==AnalysisOutcome.Status.STABLE&&(a.key().implementation().equals(PossibleValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(RegionalValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(StorageValuesProvider.IMPLEMENTATION))).count());
            metrics.put("partialSites",sites.stream().filter(f->f.analysisStatus()==DependencySiteFact.AnalysisStatus.PARTIAL).count());
            metrics.put("reachabilityRuns",result.analyses().stream().filter(a->a.key().implementation().equals("Reachability")).count());
            metrics.put("indexedOperations",session.index().metrics().operationsIndexed());
            for(var analysis:result.analyses())analysis.metrics().forEach((name,value)->metrics.merge(analysis.key().implementation()+"."+name,value,Math::addExact));
            return new DependencyResult(publication.id(),publication.airVersion(),sites,edges,metrics,publication.coverage().inventory(),publication.origins(),publication.artifacts(),publication.uncertainties().stream().map(Evidence.Uncertainty::id).toList(),List.copyOf(reasons),FileDependencyAnalysis.prepare(publication,session,execution,null));
        }
    }
    private record SiteKey(EntryId entry,OperationId operation) { }
    private static List<SiteView> inventory(Publication publication) {
        var result=new ArrayList<SiteView>();
        for(var unit:publication.units())for(var entry:unit.entries())for(var sequence:unit.sequences())
            if(sequence.terminator() instanceof Operations.Invoke invoke&&CallDependencyPlan.selected(invoke))
                result.add(new SiteView(entry.id(),sequence.label(),sequence.instructions().size(),invoke));
        return List.copyOf(result);
    }
    private static DependencyResult partialInventory(Publication publication,String reason) {
        var sites=inventory(publication).stream().map(s->CallDependencyConsumer.partial(s,Optional.empty(),List.of(reason)))
            .sorted(Comparator.comparing(DependencySiteFact::entry,AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).toList();
        var edges=new ArrayList<DependencyResult.Edge>();
        for(var site:sites)for(var candidate:site.candidates())edges.add(new DependencyResult.Edge(site.caller(),site.entry(),site.operation(),candidate,true));
        return new DependencyResult(publication.id(),publication.airVersion(),sites,edges,
            Map.of("possibleValuesPreparations",0L,"possibleValuesRuns",0L,"reachabilityRuns",0L,"partialSites",(long)sites.size()),
            publication.coverage().inventory(),publication.origins(),publication.artifacts(),publication.uncertainties().stream().map(Evidence.Uncertainty::id).toList(),List.of(reason),FileDependencyAnalysis.prepare(publication,null,null,reason));
    }
    public enum Kind { INVALID_INPUT,INPUT_INCOMPLETE,CFG_UNSUPPORTED,ANALYSIS_UNSUPPORTED,RESOURCE_LIMIT,CONSUMER_FAILURE }
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final Kind kind;
        public Failure(Kind kind,String message){super(message);this.kind=kind;}
        public Kind kind(){return kind;}
    }
}
