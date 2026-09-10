package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.PlanningFixtures.*;
import static io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;

class PlanningRuntimeTest {
    @Test void realVerticalSharesRunQueryReplayAndProducerSupport() {
        var p=partial(vertical());var key=key(p);var batch=PossibleValuesProvider.batch("shared",key);var q=query(p);
        var provider=new ProviderWrapper();
        try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)))) {
            var result=runtime.execute("vertical",runtime.plan(List.of(queryConsumer("B",key,batch,List.of(q,q)),queryConsumer("A",key,batch,List.of(q)))));
            assertEquals(PreparationStatus.COMPLETE,result.preparationStatus());assertEquals(List.of("A","B"),result.consumerPlan().stream().map(ConsumerPlan::consumerId).toList());
            assertEquals(1,provider.starts,"one actual provider solve");assertEquals(1,provider.observations,"one actual observation batch");
            assertEquals(1,metric(result,"analysis","analysisRuns"));assertEquals(1,metric(result,"analysis","analysisCacheHits"));
            assertEquals(3,metric(result,"observation","queryRequests"));assertEquals(1,metric(result,"observation","uniqueQueries"));
            assertEquals(1,metric(result,"observation","sequencesReplayed"));assertEquals(1,metric(result,"observation","operationsReplayed"));
            var first=result.consumers().get(0).facts().getFirst().observation();var second=result.consumers().get(1).facts().getFirst().observation();
            assertSame(first,second,"immutable observation shared between consumers");assertEquals(ObservationBatch.QueryStatus.VALUE,first.status(),"query consumer receives supported value");var fact=first.value();
            assertEquals(List.of(new Values.TextValue("PROGA")),fact.candidates(),"all real candidates reach sink");
            var producer=p.units().getFirst().sequences().getFirst().instructions().getFirst().header();
            assertEquals(List.of(producer.id()),fact.evidence(),"producer Assign survives to FactSink");
            assertEquals(List.of(producer.origin()),fact.provenance());assertEquals(1,fact.candidateSupports().size(),"candidate support preserved at consumer boundary");assertEquals(producer.id(),fact.candidateSupports().getFirst().producers().getFirst().evidence());
            assertFalse(fact.modelValueRemainder());assertTrue(fact.sourceUnknownRemainder(),"source remainder survives consumer");assertTrue(fact.effectiveUnknownRemainder());
            assertEquals(1,metric(result,"observation","sourceOpenResults"));assertEquals(1,metric(result,"observation","closedInModelResults"));
            assertThrows(UnsupportedOperationException.class,()->fact.candidateSupports().clear());
        }
    }
    @Test void f3FailureIsLocalAndIndependentBatchesShareStableRun() {
        var p=vertical();var key=key(p);var bad=PossibleValuesProvider.batch("A-failed",key);var good=PossibleValuesProvider.batch("B-good",key);var q=query(p);
        var provider=new ProviderWrapper(){
            @Override Materialized<ObjectId,ValueFact> observe(Run<ObjectId,ValueFact> run,List<PointQuery<ObjectId>> queries){
                if(observations==1)throw new ObservationBatch.ObservationException("controlled first batch failure");return super.observe(run,queries);
            }
        };
        try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)))) {
            var result=runtime.execute("f3",runtime.plan(List.of(structural("S",key),analysisOnly("A",key),queryConsumer("Qbad",key,bad,List.of(q)),queryConsumer("Qgood",key,good,List.of(q)))));
            assertEquals(AnalysisOutcome.Status.STABLE,result.analyses().getFirst().status(),"later failure preserves STABLE");
            assertEquals(1,provider.starts,"batches reuse stable run");assertEquals(2,provider.observations);
            assertEquals(BatchStatus.FAILED,result.results().getFirst().status());assertEquals("OBSERVATION_ERROR",result.results().getFirst().reason());assertTrue(result.results().getFirst().observations().isEmpty());
            var outcomes=new HashMap<String,ConsumerOutcome<TestFact>>();result.consumers().forEach(c->outcomes.put(c.consumerId(),c));
            assertEquals(ConsumerStatus.COMPLETE,outcomes.get("S").status(),"independent structural consumer completes");
            assertEquals(ConsumerStatus.COMPLETE,outcomes.get("A").status(),"analysis-only does not depend on batch");
            assertEquals(ConsumerStatus.NOT_STARTED,outcomes.get("Qbad").status());assertEquals("DEPENDENCY_UNAVAILABLE",outcomes.get("Qbad").reason());
            assertEquals(ConsumerStatus.COMPLETE,outcomes.get("Qgood").status(),"independent batch remains executable");
            assertEquals(PreparationStatus.INCOMPLETE,result.preparationStatus(),"phase-local failure makes preparation incomplete");
            assertEquals(1,metric(result,"observation","observationFailures"));assertEquals(1,metric(result,"observation","queriesNotMaterialized"));
            assertEquals(3,metric(result,"consumer","consumersComplete"));assertEquals(1,metric(result,"consumer","consumersNotStarted"));
        }
    }
    @Test void failingConsumerDiscardsEveryStagedFactAndOthersComplete() {
        var p=vertical();var key=key(p);var safe=structural("B",key);var base=structural("A",key);
        var failing=new ConsumerRegistration<TestFact>(base.dependencies(),base.interests(),base.requests(),(s,f,sink)->{
            sink.emit(new TestFact(s.operationId(),null));sink.emit(new TestFact(s.operationId(),null));throw new FactConsumer.ConsumerException("controlled error");
        });
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var result=runtime.execute("failure",runtime.plan(List.of(failing,safe)));
            assertEquals(ConsumerStatus.FAILED,result.consumers().getFirst().status());assertEquals("CONSUMER_ERROR",result.consumers().getFirst().reason());
            assertTrue(result.consumers().getFirst().facts().isEmpty(),"failed consumer commits no partial bundle");
            assertEquals(ConsumerStatus.COMPLETE,result.consumers().getLast().status());assertEquals(PreparationStatus.INCOMPLETE,result.preparationStatus());
            assertEquals(3,metric(result,"consumer","factsStaged"));assertEquals(2,metric(result,"consumer","factsDiscarded"));assertEquals(1,metric(result,"consumer","factsCommitted"));assertEquals(1,metric(result,"consumer","consumerFailures"));
        }
    }
    @Test void analysisOnlyDoesNotCreateObservationAndStructuralStartsNoAnalysis() {
        var p=vertical();var key=key(p);
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var structural=runtime.execute("structural",runtime.plan(List.of(structural("S",key))));
            assertEquals(0,metric(structural,"analysis","analysisRuns"));assertTrue(structural.results().isEmpty());
            var analysis=runtime.execute("analysis",runtime.plan(List.of(analysisOnly("A",key))));
            assertEquals(1,metric(analysis,"analysis","analysisRuns"));assertEquals(0,metric(analysis,"observation","observationBatchesExecuted"));assertTrue(analysis.results().isEmpty());
            assertEquals(ConsumerStatus.COMPLETE,analysis.consumers().getFirst().status());
        }
    }
    @Test void lateQueryIsNotRequestedAndNewEpochExplicitlyReusesRun() {
        var p=vertical();var key=key(p);var batch=PossibleValuesProvider.batch("B",key);var q=query(p);
        var late=new PointQuery<>(ProgramPoint.after(key.entry(),q.point().operation()),q.subject());var base=queryConsumer("A",key,batch,List.of(q));
        var registration=new ConsumerRegistration<TestFact>(base.dependencies(),base.interests(),base.requests(),(s,f,k)-> {
            assertEquals(PreparedFacts.LookupStatus.NOT_REQUESTED,f.lookup(batch,late).status(),"unplanned query must not materialize");
            assertEquals(PreparedFacts.LookupStatus.AVAILABLE,f.lookup(batch,q).status());
        });
        var provider=new ProviderWrapper();
        try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)))) {
            var old=runtime.plan(List.of(registration));var first=runtime.execute("old",old);
            assertEquals(1,metric(first,"consumer","notRequested"));assertEquals(1,provider.observations,"late access cannot replay");
            assertThrows(IllegalStateException.class,()->runtime.execute("again",old));
            var next=runtime.plan(List.of(queryConsumer("A",key,batch,List.of(q,late))));assertEquals(old.epoch()+1,next.epoch());
            var second=runtime.execute("new",next);assertEquals(1,provider.starts);assertEquals(2,provider.observations);
            assertEquals(0,metric(second,"analysis","analysisRuns"));assertEquals(1,metric(second,"analysis","analysisCacheHits"));
            assertEquals(2,metric(second,"observation","uniqueQueries"));assertEquals(1,metric(second,"observation","unsupportedQueries"));
            assertEquals(1,old.observations().getFirst().queries().size(),"old plan stays frozen");
        }
    }
    @Test void cannotReadAnotherConsumersUnrequestedSubjectInSharedBatch() {
        var p=linear(1,1,2,1);var key=key(p);var batch=PossibleValuesProvider.batch("B",key);var q=query(p);
        var foreign=new PointQuery<>(q.point(),p.units().getFirst().objects().get(1).id());var base=queryConsumer("A",key,batch,List.of(q));
        var a=new ConsumerRegistration<TestFact>(base.dependencies(),base.interests(),base.requests(),(s,f,k)->assertEquals(PreparedFacts.LookupStatus.NOT_REQUESTED,f.lookup(batch,foreign).status()));
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var result=runtime.execute("local",runtime.plan(List.of(a,queryConsumer("B",key,batch,List.of(foreign)))));
            assertEquals(2,metric(result,"observation","uniqueQueries"));assertEquals(1,metric(result,"consumer","notRequested"));
        }
    }
    @Test void missingDependenciesAndWrongBindingsNeverProduceSuccess() {
        var p=vertical();var key=key(p);var q=query(p);var batch=PossibleValuesProvider.batch("B",key);var base=queryConsumer("A",key,batch,List.of(q));
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var missing=new ConsumerRegistration<TestFact>(new ConsumerPlan("A",List.of(key),List.of("absent")),base.interests(),List.of(),base.consumer());
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(missing)),"missing batch dependency");
            var missingKey=new ConsumerRegistration<TestFact>(new ConsumerPlan("A",List.of(),List.of("B")),base.interests(),base.requests(),base.consumer());
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(missingKey)),"batch requires its exact key");
            var wrongShape=new ObservationBatchId<ObjectId,String>("B",key,PossibleValuesProvider.PROJECTION,ObjectId.class,String.class);
            var wrong=new ConsumerRegistration<TestFact>(base.dependencies(),base.interests(),List.of(new ObservationRequest<>(wrongShape,List.of(q))),base.consumer());
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(wrong)),"registered fact type binding");
            var foreignQuery=new PointQuery<>(ProgramPoint.before(new EntryId(key.entry().unit(),"absent"),q.point().operation()),q.subject());
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(queryConsumer("A",key,batch,List.of(foreignQuery)))),"query Entry bound to key");
        }
    }
    @Test void registryRejectsWrongPreparedAndExecutionKey() {
        var p=linear(1,1,1,2);var key=key(p);var other=PossibleValuesProvider.key(p.units().getFirst().entries().get(1).id());
        for(boolean prepared:List.of(true,false)) {
            var provider=new ProviderWrapper(){
                @Override AnalysisKey preparedKey(AnalysisKey k){return prepared?other:k;}
                @Override AnalysisOutcome outcome(AnalysisOutcome o){return prepared?o:new AnalysisOutcome(other,o.status(),o.reason(),o.metrics());}
            };
            try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)))) {
                assertThrows(IllegalArgumentException.class,()->runtime.execute("mismatch",runtime.plan(List.of(analysisOnly("A",key)))),"provider binding mismatch rejected");
            }
        }
        assertThrows(IllegalArgumentException.class,()->new AnalysisRegistry(List.of(new PossibleValuesProvider(),new PossibleValuesProvider())));
    }
    @Test void sinkIsClosedAndInfrastructureErrorsPropagateWithoutPartialResult() {
        var p=vertical();var key=key(p);var base=structural("A",key);var sinks=new ArrayList<FactSink<TestFact>>();
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var r=new ConsumerRegistration<TestFact>(base.dependencies(),base.interests(),List.of(),(s,f,k)->sinks.add(k));
            runtime.execute("done",runtime.plan(List.of(r)));
            assertThrows(IllegalStateException.class,()->sinks.getFirst().emit(new TestFact(query(p).point().operation(),null)));
            var fail=new ConsumerRegistration<TestFact>(base.dependencies(),base.interests(),List.of(),(s,f,k)->{throw new OutOfMemoryError("test-only simulated external exhaustion");});
            assertThrows(OutOfMemoryError.class,()->runtime.execute("error",runtime.plan(List.of(fail))),"resource exhaustion is not semantic completion");
        }
    }
    @Test void finalFactsReflectFixedPointAfterOverwritesAndLoop() {
        var p=graph(new String[]{null,"A","B",null},new int[][]{{1},{2},{1,3},{}},1,false,false);var key=key(p);var q=query(p);var batch=PossibleValuesProvider.batch("B",key);
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var result=runtime.execute("loop",runtime.plan(List.of(queryConsumer("A",key,batch,List.of(q)))));
            var fact=result.consumers().getFirst().facts().getFirst().observation().value();
            assertEquals(List.of(new Values.TextValue("B")),fact.candidates(),"only final stable overwrite reaches sink");
            assertEquals(List.of(new OperationId(key.entry().unit(),"assign-2")),fact.evidence());
        }
    }
    @Test void preparedResultAndClosedRuntimeDetachSessionRunAndConsumer() {
        var p=vertical();var key=key(p);var batch=PossibleValuesProvider.batch("B",key);
        var runtime=new PlanningExecution(session(p),registry());
        var result=runtime.execute("detached",runtime.plan(List.of(queryConsumer("A",key,batch,List.of(query(p))))));runtime.close();
        var retained=PlanningRetention.count(result);var closed=PlanningRetention.count(runtime);
        for(String forbidden:List.of("AnalysisSession","ProgramIndex","DataflowResult","PossibleValuesState","ValueUniverse","TextProfile","ExecutionPlan","ConsumerRegistration","BoundRun")) {
            assertEquals(0L,retained.getOrDefault(forbidden,0L),"detached result cannot retain "+forbidden);
            assertEquals(0L,closed.getOrDefault(forbidden,0L),"closed epoch releases "+forbidden);
        }
        assertEquals(1L,retained.get("ValueFact"));assertThrows(IllegalStateException.class,()->runtime.plan(List.of()));
    }
}
