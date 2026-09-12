package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.PossibleValuesProvider;
import java.util.*;

/** CP6 W1D application boundary. */
public final class DependencyAnalysis {
    public DependencyResult prepare(Publication publication) {
        Objects.requireNonNull(publication);
        var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,options);
        switch(cfg.status()) {
            case CFG_BUILT -> { }
            case INVALID_IR -> throw new Failure(Kind.INVALID_INPUT,"INVALID_IR");
            case UNSUPPORTED_INPUT,UNSUPPORTED_CAPABILITY -> throw new Failure(Kind.CFG_UNSUPPORTED,"CFG_UNSUPPORTED");
            case RESOURCE_LIMIT -> throw new Failure(Kind.RESOURCE_LIMIT,"RESOURCE_LIMIT");
            case VALIDATION_LIMIT,INCOMPLETE_VALIDATION -> throw new Failure(Kind.INPUT_INCOMPLETE,"INCOMPLETE_VALIDATION");
        }
        var opened=AnalysisSession.open(cfg,publication,options.projectionPolicy(),publication.units().stream().flatMap(u->u.entries().stream()).toList());
        if(opened.status()!=AnalysisSession.Status.ACCEPTED)throw new Failure(Kind.CFG_UNSUPPORTED,opened.reason());
        var session=opened.session().orElseThrow();
        try(var execution=new PlanningExecution(session,new AnalysisRegistry(List.of(new PossibleValuesProvider(),new ReachabilityProvider())))) {
            var plan=execution.plan(CallDependencyPlan.select(session));var result=execution.execute("dependencies@1",plan);
            for(var analysis:result.analyses())if(analysis.status()!=AnalysisOutcome.Status.STABLE)throw new Failure(Kind.ANALYSIS_UNSUPPORTED,analysis.reason());
            if(result.preparationStatus()!=PreparedAnalysisResult.PreparationStatus.COMPLETE)throw new Failure(Kind.CONSUMER_FAILURE,"dependency preparation incomplete");
            var sites=result.consumers().stream().flatMap(c->c.facts().stream()).sorted(Comparator.comparing(DependencySiteFact::entry,AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).toList();
            var edges=new ArrayList<DependencyResult.Edge>();
            for(var site:sites)if(site.reachability()==DependencySiteFact.Reachability.REACHABLE)
                for(var candidate:site.candidates())edges.add(new DependencyResult.Edge(site.caller(),site.entry(),site.operation(),candidate,site.effectiveUnknownRemainder()));
            var metrics=new TreeMap<String,Long>();
            result.metrics().forEach((phase,counts)->counts.forEach((name,value)->metrics.put(phase+"."+name,value)));
            long values=result.analyses().stream().filter(a->a.key().implementation().equals(PossibleValuesProvider.IMPLEMENTATION)).count();
            metrics.put("possibleValuesPreparations",values);metrics.put("possibleValuesRuns",values);
            metrics.put("reachabilityRuns",result.analyses().stream().filter(a->a.key().implementation().equals("Reachability")).count());
            metrics.put("indexedOperations",session.index().metrics().operationsIndexed());
            for(var analysis:result.analyses())analysis.metrics().forEach((name,value)->metrics.merge(analysis.key().implementation()+"."+name,value,Math::addExact));
            return new DependencyResult(publication.id(),publication.airVersion(),sites,edges,metrics,publication.coverage().inventory(),publication.origins(),publication.artifacts(),publication.uncertainties().stream().map(Evidence.Uncertainty::id).toList());
        }
    }
    public enum Kind { INVALID_INPUT,INPUT_INCOMPLETE,CFG_UNSUPPORTED,ANALYSIS_UNSUPPORTED,RESOURCE_LIMIT,CONSUMER_FAILURE }
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final Kind kind;
        public Failure(Kind kind,String message){super(message);this.kind=kind;}
        public Kind kind(){return kind;}
    }
}
