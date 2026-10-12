package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.PlanningFixtures.*;
import static io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;

/** AS-W08 law: batch ID is not a replay root; values/support stay per point. */
final class JointObservationReplayTest {
    @Test void compatibleIndependentBatchesReplayOnePrefixAndKeepExactLocalObservations() {
        for(int n:new int[]{4,16,64,256,1024})for(boolean independent:new boolean[]{false,true}) {
            var publication=linear(1,n,2,1);var unit=publication.units().getFirst();var sequence=unit.sequences().getFirst();
            var key=key(publication);var registrations=new ArrayList<ConsumerRegistration<TestFact>>();
            var expectedQueries=new ArrayList<PointQuery<io.github.gustavo2358.air.model.Ids.ObjectId>>();
            for(int i=1;i<=n;i++) {
                var batch=PossibleValuesProvider.batch(independent?"batch-"+i:"shared",key);
                var query=new PointQuery<>(ProgramPoint.before(key.entry(),i==n?sequence.terminator().header().id():sequence.instructions().get(i).header().id()),unit.objects().get(i%2).id());
                expectedQueries.add(query);
                registrations.add(queryConsumer("consumer-"+String.format("%04d",i),key,batch,List.of(query,query)));
            }
            try(var execution=new PlanningExecution(session(publication),registry())) {
                var result=execution.execute("joint",execution.plan(registrations));
                assertEquals(PreparationStatus.COMPLETE,result.preparationStatus());
                assertEquals(n,result.consumers().size());assertEquals(1,metric(result,"analysis","analysisRuns"));
                assertEquals(2L*n,metric(result,"observation","queryRequests"));
                assertEquals(n,metric(result,"observation","uniqueQueries"));
                assertEquals(n,metric(result,"consumer","factsCommitted"));
                for(int i=1;i<=n;i++) {
                    var observation=result.consumers().get(i-1).facts().getFirst().observation();
                    assertEquals(expectedQueries.get(i-1),observation.query());
                    assertEquals(ObservationBatch.QueryStatus.VALUE,observation.status());assertNull(observation.reason());
                    var fact=observation.value();var producer=sequence.instructions().get(i-1).header();
                    assertEquals(List.of(new Values.TextValue("literal")),fact.candidates());
                    assertFalse(fact.modelValueRemainder());assertFalse(fact.sourceUnknownRemainder());assertFalse(fact.effectiveUnknownRemainder());
                    assertEquals(ValueFact.Reachability.REACHABLE,fact.reachability());
                    assertEquals(List.of(producer.id()),fact.evidence());assertEquals(List.of(producer.origin()),fact.provenance());assertTrue(fact.premises().isEmpty());
                    assertEquals(1,fact.candidateSupports().size());assertEquals(1,fact.candidateSupports().getFirst().producers().size());
                    assertEquals(producer.id(),fact.candidateSupports().getFirst().producers().getFirst().evidence());
                }
                long transfers=metric(result,"observation","operationsReplayed");
                System.out.println("JOINT_OBSERVATION_COST n="+n+" independent="+independent+" operations="+transfers);
                assertEquals(n,transfers,"JOINT_OBSERVATION_REPLAYS_PREFIX_PER_BATCH_RED");
                assertEquals(1,metric(result,"observation","sequencesReplayed"),"same exact run/root/sequence must replay once");
            }
        }
    }
    @Test void jointBatchesKeepUnsupportedPointsLocalOffsetsAndDistinctOverwriteFacts() {
        var base=linear(1,2,2,1);var unit=base.units().getFirst();var sequence=unit.sequences().getFirst();
        var first=assign(unit.id(),"alpha",unit.objects().getFirst().id(),"ALPHA");
        var second=assign(unit.id(),"beta",unit.objects().get(1).id(),"BETA");
        var publication=publication(base.id(),List.of(unit(unit.id(),unit.entries(),List.of(
            new Sequence(sequence.label(),List.of(first,second),sequence.terminator(),sequence.origin())),unit.objects())),base.storage());
        var entry=unit.entries().getFirst().id();var subject=unit.objects().getFirst().id();
        var initial=new PointQuery<>(ProgramPoint.entry(entry),subject);
        var alpha=new PointQuery<>(ProgramPoint.after(entry,first.header().id()),subject);
        var beta=new PointQuery<>(ProgramPoint.before(entry,sequence.terminator().header().id()),subject);
        var unsupported=new PointQuery<>(ProgramPoint.after(entry,sequence.terminator().header().id()),subject);
        List<List<PointQuery<ObjectId>>> requests=List.of(List.of(),List.of(initial,initial),List.of(alpha),List.of(beta,beta),List.of(unsupported));
        var admission=PossibleValuesAnalysis.prepare(session(publication));
        ValueFact detachedAlpha;
        try(var analysis=admission.analysis().orElseThrow();var execution=analysis.execute()) {
            var independent=requests.stream().map(execution::observe).toList();
            var joint=execution.observeBatches(requests);assertEquals(requests.size(),joint.size());
            for(int at=0;at<requests.size();at++) {
                var expected=independent.get(at);var actual=joint.get(at);
                assertEquals(expected.batch().observations(),actual.batch().observations(),"complete point fact/support/remainder equality");
                assertEquals(expected.batch().metrics().queryRequests(),actual.batch().metrics().queryRequests());
                assertEquals(expected.batch().metrics().uniqueQueries(),actual.batch().metrics().uniqueQueries());
                assertEquals(expected.batch().metrics().maxRequestedOffsetObserved(),actual.batch().metrics().maxRequestedOffsetObserved());
                assertEquals(expected.batch().metrics().queriesAnswered(),actual.batch().metrics().queriesAnswered());
                assertEquals(expected.batch().metrics().unsupportedQueries(),actual.batch().metrics().unsupportedQueries());
                assertEquals(expected.quality(),actual.quality());
            }
            var initialFact=joint.get(1).batch().observations().getFirst().value();assertTrue(initialFact.candidates().isEmpty());assertTrue(initialFact.modelValueRemainder());
            var alphaFact=joint.get(2).batch().observations().getFirst().value();assertEquals(List.of(new Values.TextValue("ALPHA")),alphaFact.candidates());assertEquals(List.of(first.header().id()),alphaFact.evidence());
            var betaFact=joint.get(3).batch().observations().getFirst().value();assertEquals(List.of(new Values.TextValue("BETA")),betaFact.candidates());assertEquals(List.of(second.header().id()),betaFact.evidence());
            var refused=joint.get(4).batch().observations().getFirst();assertEquals(ObservationBatch.QueryStatus.UNSUPPORTED_POINT,refused.status());assertEquals(ObservationBatch.PointReason.AFTER_TERMINATOR,refused.reason());assertNull(refused.value());
            assertEquals(2,joint.stream().mapToLong(value->value.batch().metrics().operationsReplayed()).sum());
            assertEquals(1,joint.stream().mapToLong(value->value.batch().metrics().sequencesReplayed()).sum());
            detachedAlpha=alphaFact;
        }
        assertEquals(List.of(new Values.TextValue("ALPHA")),detachedAlpha.candidates(),"facts detach from closed replay");
    }
    @Test void jointControlledFailureFallsBackWithoutWideningAnotherBatch() {
        var publication=linear(1,2,1,1);var key=key(publication);var query=query(publication);
        var provider=jointFailingProvider(false);
        try(var execution=new PlanningExecution(session(publication),new AnalysisRegistry(List.of(provider)))) {
            var result=execution.execute("failure",execution.plan(List.of(
                queryConsumer("A",key,PossibleValuesProvider.batch("A",key),List.of(query)),
                queryConsumer("B",key,PossibleValuesProvider.batch("B",key),List.of(query)),analysisOnly("stable",key))));
            assertEquals(AnalysisOutcome.Status.STABLE,result.analyses().getFirst().status());
            assertEquals(BatchStatus.FAILED,result.results().getFirst().status());assertEquals(BatchStatus.COMPLETE,result.results().get(1).status());
            assertEquals(ConsumerStatus.NOT_STARTED,result.consumers().getFirst().status());assertEquals(ConsumerStatus.COMPLETE,result.consumers().get(1).status());
            assertEquals(List.of(new Values.TextValue("literal")),result.consumers().get(1).facts().getFirst().observation().value().candidates());
            assertEquals(ConsumerStatus.COMPLETE,result.consumers().get(2).status());assertEquals(2,provider.observations);
        }
        assertEquals(1,provider.closes);
    }
    @Test void jointOperationalFailurePropagatesAndNeverInvokesFallback() {
        var publication=linear(1,2,1,1);var key=key(publication);var query=query(publication);var provider=jointFailingProvider(true);
        try(var execution=new PlanningExecution(session(publication),new AnalysisRegistry(List.of(provider)))) {
            var plan=execution.plan(List.of(queryConsumer("A",key,PossibleValuesProvider.batch("A",key),List.of(query)),
                queryConsumer("B",key,PossibleValuesProvider.batch("B",key),List.of(query))));
            assertThrows(OutOfMemoryError.class,()->execution.execute("operational",plan));assertEquals(0,provider.observations);
        }
        assertEquals(1,provider.closes);
    }
    private static ProviderWrapper jointFailingProvider(boolean operational) {
        return new ProviderWrapper() {
            @Override Materialized<ObjectId,ValueFact> observe(Run<ObjectId,ValueFact> run,List<PointQuery<ObjectId>> queries) {
                if(observations==1)throw new ObservationBatch.ObservationException("selected independent batch");return run.observe(queries);
            }
            @Override public Prepared<ObjectId,ValueFact> prepare(AnalysisSession owner,AnalysisKey key) {
                var prepared=super.prepare(owner,key);
                return new Prepared<>() {
                    public AnalysisKey key(){return prepared.key();}public AnalysisOutcome refusal(){return prepared.refusal();}public void close(){prepared.close();}
                    public Run<ObjectId,ValueFact> execute() {
                        var original=prepared.execute();
                        return new JointRun<>() {
                            public AnalysisOutcome outcome(){return original.outcome();}public void close(){original.close();}
                            public Materialized<ObjectId,ValueFact> observe(List<PointQuery<ObjectId>> queries){return original.observe(queries);}
                            public List<Materialized<ObjectId,ValueFact>> observeBatches(List<List<PointQuery<ObjectId>>> batches) {
                                if(operational)throw new OutOfMemoryError("test-only simulated operational exhaustion");
                                throw new ObservationBatch.ObservationException("joint path controlled failure");
                            }
                        };
                    }
                };
            }
        };
    }
}
