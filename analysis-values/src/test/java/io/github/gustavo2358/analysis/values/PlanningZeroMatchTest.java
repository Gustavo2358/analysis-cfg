package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.ObjectId;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.plan.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;
import static io.github.gustavo2358.analysis.values.PlanningFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

/** A declared observation dependency is independent of whether its site selector matches. */
class PlanningZeroMatchTest {
    @Test void absentKindKeepsDeclaredEmptyBatch() {
        verifyEmptySelection(vertical(),Operations.Branch.class,true,0);
    }

    @Test void rejectingFilterKeepsDeclaredEmptyBatch() {
        verifyEmptySelection(graph(new String[]{null},new int[][]{{}},1,false,false),Operations.Return.class,false,1);
    }

    private static void verifyEmptySelection(Publication publication,Class<? extends Operation> kind,
                                            boolean accepted,long candidates) {
        var key=key(publication);var batch=PossibleValuesProvider.batch("derived",key);
        var declaration=new SiteInterest.SiteQuery<ObjectId,ValueFact>(batch,site -> {
            throw new AssertionError("query factory must not run without matched sites");
        });
        var consumer=new ConsumerRegistration<TestFact>(new ConsumerPlan("sparse",List.of(key),List.of(batch.id())),
            List.of(new SiteInterest(kind,key.entry(),site -> accepted,List.of(declaration))),List.of(),
            (site,facts,sink) -> { throw new AssertionError("consumer must not run without matched sites"); });
        var provider=new ProviderWrapper();
        try(var runtime=new PlanningExecution(session(publication),new AnalysisRegistry(List.of(provider)))) {
            var plan=assertDoesNotThrow(() -> runtime.plan(List.of(consumer)),"declared SiteQuery batch survives zero matches");
            assertEquals(List.of(),consumer.requests(),"no empty request sentinel");
            assertTrue(plan.sites().get("sparse").isEmpty());assertEquals(1,plan.observations().size());
            assertEquals(batch,plan.observations().getFirst().batchId());
            assertEquals(0,plan.observations().getFirst().queryRequests());assertTrue(plan.observations().getFirst().queries().isEmpty());
            var result=runtime.execute("empty-selection",plan);
            assertEquals(1,provider.starts,"declared analysis executes once");assertEquals(1,provider.observations,"real W3 empty batch materializes once");
            assertEquals(AnalysisOutcome.Status.STABLE,result.analyses().getFirst().status());
            assertEquals(BatchStatus.COMPLETE,result.results().getFirst().status());assertNull(result.results().getFirst().reason());
            assertTrue(result.results().getFirst().observations().isEmpty());
            assertEquals(ConsumerStatus.COMPLETE,result.consumers().getFirst().status());assertTrue(result.consumers().getFirst().facts().isEmpty());
            assertEquals(PreparationStatus.COMPLETE,result.preparationStatus());
            assertEquals(candidates,metric(result,"planning","candidateSites"));
            assertEquals(candidates,metric(result,"planning","structuralVisits"));
            assertEquals(candidates,metric(result,"planning","filterEvaluations"));
            for(String metric:List.of("siteMatches","planningCallbacks","queryRequests","uniqueQueries"))
                assertEquals(0,metric(result,"planning",metric),"empty planning "+metric);
            assertEquals(1,metric(result,"planning","observationBatchesPlanned"));
            assertEquals(1,metric(result,"observation","observationBatchesExecuted"));
            for(String metric:List.of("queryRequests","uniqueQueries","queriesAnswered","queriesNotMaterialized","sequencesReplayed","operationsReplayed","observationFailures"))
                assertEquals(0,metric(result,"observation",metric),"empty batch "+metric);
            for(String metric:List.of("consumerInvocations","factsStaged","factsCommitted","factsDiscarded","consumersNotStarted","consumerFailures"))
                assertEquals(0,metric(result,"consumer",metric),"empty consumer "+metric);
            assertEquals(1,metric(result,"consumer","consumersComplete"));
        }
    }

    @Test void zeroMatchDeclarationsValidateBindingsBeforeSelection() {
        var publication=vertical();var key=key(publication);var batch=PossibleValuesProvider.batch("derived",key);
        var wrongProjection=new ObservationBatchId<ObjectId,ValueFact>(batch.id(),key,"wrong",ObjectId.class,ValueFact.class);
        var wrongType=new ObservationBatchId<ObjectId,String>(batch.id(),key,batch.projection(),ObjectId.class,String.class);
        var otherKey=new AnalysisKey(key.implementation(),key.version(),key.profile(),key.direction(),key.precisionPolicy(),Map.of("unknown","value"),key.entry());
        var unsupported=PossibleValuesProvider.batch(batch.id(),otherKey);
        var good=new ConsumerPlan("sparse",List.of(key),List.of(batch.id()));
        try(var runtime=new PlanningExecution(session(publication),registry())) {
            assertInvalidDeclaration(runtime,key,new ConsumerPlan("sparse",List.of(),List.of(batch.id())),batch,"request missing explicit consumer dependency");
            assertInvalidDeclaration(runtime,key,new ConsumerPlan("sparse",List.of(key),List.of()),batch,"request missing explicit consumer dependency");
            assertInvalidDeclaration(runtime,key,good,wrongProjection,"batch/provider binding mismatch");
            assertInvalidDeclaration(runtime,key,good,wrongType,"batch/provider binding mismatch");
            assertInvalidDeclaration(runtime,key,good,unsupported,"unregistered semantic options");
        }
    }

    private static <T,V> void assertInvalidDeclaration(PlanningExecution runtime,AnalysisKey key,
            ConsumerPlan dependencies,ObservationBatchId<T,V> batch,String diagnostic) {
        var query=new SiteInterest.SiteQuery<T,V>(batch,site -> { throw new AssertionError("no query for invalid declaration"); });
        var interest=new SiteInterest(Operations.Return.class,key.entry(),site -> {
            throw new AssertionError("invalid declaration must fail before filtering");
        },List.of(query));
        var registration=new ConsumerRegistration<TestFact>(dependencies,List.of(interest),List.of(),
            (site,facts,sink) -> { throw new AssertionError("no consumer for invalid declaration"); });
        var error=assertThrows(IllegalArgumentException.class,() -> runtime.plan(List.of(registration)));
        assertEquals(diagnostic,error.getMessage());
    }
}
