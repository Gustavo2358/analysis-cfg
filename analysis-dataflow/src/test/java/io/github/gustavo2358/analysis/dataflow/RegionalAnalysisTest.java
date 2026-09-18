package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionalAnalysisTest {
    static PointQuery<StorageSubject> query(Publication p,int entry,String operation) {
        var u=p.units().getFirst();return new PointQuery<>(ProgramPoint.before(u.entries().get(entry).id(),new OperationId(u.id(),operation)),new StorageSubject.NamedObject(u.objects().getFirst().id()));
    }
    @Test void rdAndValuesShareSelectedEntriesAndOneBatchPerExecution() {
        var p=FlowFixtures.linear(1,2,1,2);var q=query(p,0,"return-seq-0");var q2=query(p,1,"return-seq-0");
        var result=new RegionalAnalysis(io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(p,"regional",List.of(q,q2,q));assertEquals(2,result.observations().size());
        assertEquals(1L,result.statistics().get("composition").get("cfgBuilds"));assertEquals(1L,result.statistics().get("composition").get("rdRuns"));assertEquals(1L,result.statistics().get("composition").get("valueRuns"));
        assertEquals(3L,result.statistics().get("valueObservation").get("queryRequests"));assertEquals(2L,result.statistics().get("valueObservation").get("uniqueQueries"));
        for(var observation:result.observations()) {
            var value=observation.values().value();var rd=observation.rd().value();assertEquals(List.of(new Values.TextValue("value-1")),value.candidates());
            assertEquals(rd.definitions().getFirst().definition(),value.alternatives().getFirst().fragments().getFirst().producer().orElseThrow().definition());
            assertEquals(Optional.of(observation.query().point().entry()),value.interpretations().getFirst().location().activation());
        }
        assertTrue(result.inventory().ids().contains(new OperandId(new OperationOwner(new OperationId(p.units().getFirst().id(),"instruction-0")),"value")),"inventory includes dead/noncontributing declarations independently of output");
        assertEquals(2,result.inventory().scopes().size());
    }
    @Test void unselectedAndMissingQueriesAreExplicitWithoutDiscardingValidResults() {
        var p=FlowFixtures.linear(1,1,1,2);var first=p.units().getFirst().entries().getFirst().id();
        var result=new RegionalAnalysis().prepare(p,"selected",List.of(query(p,0,"return-seq-0"),query(p,1,"return-seq-0"),query(p,0,"absent")),List.of(first));
        assertEquals(3,result.observations().size());assertEquals(1,result.observations().stream().filter(o->o.values().status()==ObservationBatch.QueryStatus.VALUE).count());
        for(var o:result.observations())assertEquals(o.rd().reason(),o.values().reason());assertEquals(1,result.inventory().scopes().size());
    }
    @Test void resourcePreflightFailsBeforeAnySemanticResultAndRecovers() {
        var p=FlowFixtures.linear(1,1,1,1);var requests=List.of(query(p,0,"return-seq-0"));var entries=List.of(p.units().getFirst().entries().getFirst().id());
        var options=new io.github.gustavo2358.analysis.cfg.application.BuildOptions(new io.github.gustavo2358.air.validation.ValidationOptions(128,1,1));
        var failure=assertThrows(AnalysisDataflow.PreparationException.class,()->new RegionalAnalysis().prepare(p,"resource",requests,entries,options));
        assertEquals(AnalysisDataflow.Failure.EXTERNAL_RESOURCE_LIMIT,failure.failure());assertEquals(1,new RegionalAnalysis().prepare(p,"recovered",requests).observations().size());
    }
}
