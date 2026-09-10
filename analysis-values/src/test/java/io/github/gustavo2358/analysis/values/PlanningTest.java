package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.plan.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

class PlanningTest {
    @Test void structuralConsumerCompletesWithoutAnalysis() {
        var p=linear(1,2,0,1); var entry=p.units().getFirst().entries().getFirst().id();
        var registration=new ConsumerRegistration<String>(new ConsumerPlan("structural",List.of(),List.of()),
            List.of(new SiteInterest(Operations.Return.class,entry,s->true)),List.of(),
            (site,facts,sink)->sink.emit(site.operationId().localId()));
        try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of()))) {
            var plan=runtime.plan(List.of(registration)); var result=runtime.execute("structural-result",plan);
            assertEquals(1,plan.sites().getOrDefault("structural",List.of()).size(),"exact selected structural site");
            assertEquals(1,result.consumers().size(),"complete consumer outcome coverage");
            assertEquals(List.of("return-seq-0"),result.consumers().getFirst().facts(),"atomic final facts");
            assertTrue(result.analyses().isEmpty(),"structural consumer needs no run");
            assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());
        }
    }
    @Test void duplicateConsumerIdsArePlanningErrors() {
        var p=linear(1,0,0,1);
        var r=new ConsumerRegistration<String>(new ConsumerPlan("same",List.of(),List.of()),List.of(),List.of(),(s,f,k)->{});
        try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of()))) {
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(r,r)),"duplicate IDs never silently merge");
        }
    }
}
