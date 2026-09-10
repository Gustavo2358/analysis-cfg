package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.PreparedAnalysisResult.PreparationStatus;
import io.github.gustavo2358.analysis.values.ValueFact;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CompositionTest {
    @Test void genericOverwriteUsesRealPipelineAndLastProducer() {
        var p=FlowFixtures.linear(1,2,1,1);
        var r=new AnalysisDataflow().analyze(p,"stable-id");
        assertEquals(PreparationStatus.COMPLETE,r.preparationStatus());
        assertEquals(1,r.results().size()); assertEquals(1,r.results().getFirst().observations().size(),"same object/sequence dedup");
        var f=(ValueFact)r.results().getFirst().observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("value-1")),f.candidates(),"last overwrite only");
        assertEquals(List.of(new OperationId(p.units().getFirst().id(),"instruction-1")),f.evidence(),"last Assign support");
        assertEquals(1,r.consumers().getFirst().facts().size());
        assertEquals(1L,r.metrics().get("analysis").get("analysisRuns"));
    }
    @Test void noWritesProducesCompleteWithoutInventedStableRun() {
        var r=new AnalysisDataflow().analyze(FlowFixtures.linear(3,0,0,2),"empty");
        assertEquals(PreparationStatus.COMPLETE,r.preparationStatus());
        assertTrue(r.analyses().isEmpty()); assertTrue(r.results().isEmpty()); assertTrue(r.consumers().isEmpty());
        assertEquals(0L,r.metrics().get("analysis").get("analysisRuns"));
    }
    @Test void everyTerminatorAndOrphanAreObservedBefore() {
        for(String kind:List.of("return","jump","branch","halt")) {
            var p=FlowFixtures.terminators(kind); var r=new AnalysisDataflow().analyze(p,kind);
            assertEquals(PreparationStatus.COMPLETE,r.preparationStatus());
            var obs=r.results().getFirst().observations(); assertEquals(2,obs.size());
            for(var o:obs) assertEquals(io.github.gustavo2358.analysis.query.ProgramPoint.Kind.BEFORE,o.query().point().kind());
            assertEquals(1,obs.stream().filter(o->((ValueFact)o.value()).reachability()==ValueFact.Reachability.UNREACHABLE_IN_MODEL).count());
        }
    }

    @Test void resourcePreflightNeverProducesSemanticResultAndRecovers() {
        var p=FlowFixtures.linear(1,2,1,1);
        var service=new AnalysisDataflow();
        var options=new io.github.gustavo2358.analysis.cfg.application.BuildOptions(
                new io.github.gustavo2358.air.validation.ValidationOptions(128,1,1));
        var failure=assertThrows(AnalysisDataflow.PreparationException.class,()->service.prepare(p,"resource",options),
                "no prepared result or source-open substitute");
        assertEquals(AnalysisDataflow.Failure.EXTERNAL_RESOURCE_LIMIT,failure.failure(),"typed operational preparation failure");
        assertTrue(failure.getMessage().startsWith("EXTERNAL_RESOURCE_LIMIT:"));
        var recovered=service.prepare(p,"resource");
        assertEquals(PreparationStatus.COMPLETE,recovered.result().preparationStatus());
        assertEquals(1,recovered.result().results().size());
    }
    @Test void incompleteAndLegacyPreparationFailuresRemainDistinct() {
        var p=FlowFixtures.linear(1,0,0,1);
        var cfg=new io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator(
                io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry.empty())
                .build(p,io.github.gustavo2358.analysis.cfg.application.BuildOptions.defaults());
        var statuses=List.of(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.INCOMPLETE_VALIDATION,
                io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.VALIDATION_LIMIT);
        var expected=List.of(AnalysisDataflow.Failure.INCOMPLETE_VALIDATION,AnalysisDataflow.Failure.EXTERNAL_SIZE_CAP_DEBT);
        for(int i=0;i<statuses.size();i++) {
            var result=new io.github.gustavo2358.analysis.cfg.application.CfgBuildResult(statuses.get(i),p.id(),p.airVersion(),
                    cfg.options(),cfg.preflight(),List.of(),List.of(),Optional.empty());
            var failure=assertThrows(AnalysisDataflow.PreparationException.class,()->AnalysisDataflow.requireBuilt(result));
            assertEquals(expected.get(i),failure.failure());
        }
    }
}
