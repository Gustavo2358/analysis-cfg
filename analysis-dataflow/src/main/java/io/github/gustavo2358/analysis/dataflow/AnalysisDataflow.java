package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.PossibleValuesProvider;
import java.util.*;

/** Publication boundary for the generic production observation plan. */
public final class AnalysisDataflow {
    public PreparedAnalysisResult<ObservedValueFact> analyze(Publication publication, String resultId) {
        return prepare(publication,resultId).result();
    }
    public PreparedDataflowResult prepare(Publication publication, String resultId) {
        return prepare(publication, resultId, BuildOptions.defaults());
    }
    PreparedDataflowResult prepare(Publication publication, String resultId, BuildOptions options) {
        Objects.requireNonNull(publication); Objects.requireNonNull(resultId);
        if(resultId.isBlank()) throw new IllegalArgumentException("caller must supply a stable resultId");
        BuildCfg builder=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        var cfg=builder.build(publication,options);
        requireBuilt(cfg);
        var entries=publication.units().stream().flatMap(u->u.entries().stream()).toList();
        var admission=AnalysisSession.open(cfg,publication,options.projectionPolicy(),entries);
        if(admission.status()!=AnalysisSession.Status.ACCEPTED)
            throw new PreparationException(admission.status()==AnalysisSession.Status.INVALID_INPUT?Failure.INVALID_INPUT:Failure.UNSUPPORTED_PROFILE,admission.reason());
        var session=admission.session().orElseThrow();
        var selected=DefaultValuePlan.select(session);
        try(var execution=new PlanningExecution(session,new AnalysisRegistry(List.of(new PossibleValuesProvider())))) {
            var plan=execution.plan(selected.registrations());
            var result=execution.execute(resultId,plan);
            return PreparedDataflowResult.capture(result,publication,session.index().metrics(),selected.metrics());
        }
    }
    static void requireBuilt(CfgBuildResult cfg) {
        switch(cfg.status()) {
            case CFG_BUILT -> { }
            case INVALID_IR -> throw new PreparationException(Failure.INVALID_INPUT,"BuildCfg INVALID_IR");
            case UNSUPPORTED_INPUT, UNSUPPORTED_CAPABILITY -> throw new PreparationException(Failure.UNSUPPORTED_PROFILE,"BuildCfg unsupported profile");
            case VALIDATION_LIMIT -> throw new PreparationException(Failure.EXTERNAL_SIZE_CAP_DEBT,"EXTERNAL SIZE-CAP DEBT: pinned AirValidator");
            case RESOURCE_LIMIT -> throw new PreparationException(Failure.EXTERNAL_RESOURCE_LIMIT,"EXTERNAL_RESOURCE_LIMIT: AirValidator operational budget exhausted");
            case INCOMPLETE_VALIDATION -> throw new PreparationException(Failure.INCOMPLETE_VALIDATION,"BuildCfg validation did not complete");
        }
    }
    public enum Failure { INVALID_INPUT, UNSUPPORTED_PROFILE, EXTERNAL_SIZE_CAP_DEBT, EXTERNAL_RESOURCE_LIMIT, INCOMPLETE_VALIDATION }
    public static final class PreparationException extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final Failure failure;
        public PreparationException(Failure failure,String message) { super(message); this.failure=failure; }
        public Failure failure() { return failure; }
    }
}
