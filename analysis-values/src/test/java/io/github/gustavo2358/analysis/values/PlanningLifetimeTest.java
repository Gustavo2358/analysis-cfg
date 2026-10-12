package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.plan.AnalysisOutcome;
import io.github.gustavo2358.analysis.plan.AnalysisKey;
import io.github.gustavo2358.air.model.Ids.ObjectId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.PlanningFixtures.*;
import static io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;

/** Execution ownership laws, separate from the fixed historical W4 semantic families. */
class PlanningLifetimeTest {
    @Test void closedProviderHandlesDiscardEnginesWithoutErasingDeliveredFacts() {
        var p=vertical();
        providerLifetime(new PossibleValuesProvider(),key(p));
        providerLifetime(new RegionalValuesProvider(),RegionalValuesProvider.key(key(p).entry()));
    }
    private static <V extends TextValueFact> void providerLifetime(AnalysisProvider<ObjectId,V> provider,AnalysisKey key) {
        var p=vertical();var prepared=provider.prepare(session(p),key);assertNull(prepared.refusal());
        var run=prepared.execute();var outcome=run.outcome();
        var fact=run.observe(List.of(query(p))).batch().observations().getFirst().value();
        assertEquals(List.of(new io.github.gustavo2358.air.model.Values.TextValue("PROGA")),fact.candidates());
        var support=fact.candidateSupports();var evidence=fact.evidence();var provenance=fact.provenance();
        run.close();run.close();
        assertSame(outcome,run.outcome(),"detached metadata remains available after idempotent close");
        assertThrows(IllegalStateException.class,()->run.observe(List.of(query(p))));
        var retained=PlanningRetention.count(run);
        for(String name:List.of("DataflowResult","TextProfile","ValueUniverse","Engine","RegionalValuesAnalysis","AnalysisSession"))
            assertEquals(0L,retained.getOrDefault(name,0L),provider.implementation()+" closed handle retains "+name);
        assertEquals(List.of(new io.github.gustavo2358.air.model.Values.TextValue("PROGA")),fact.candidates());
        assertEquals(support,fact.candidateSupports());assertEquals(evidence,fact.evidence());assertEquals(provenance,fact.provenance());
    }
    @Test void executionClosesEveryCachedRunOnceDespiteCleanupFailure() {
        var p=linear(1,1,1,2);var key=key(p);var other=PossibleValuesProvider.key(p.units().getFirst().entries().get(1).id());
        var provider=new ProviderWrapper();
        var first=new IllegalStateException("first cleanup failure");var second=new IllegalArgumentException("second cleanup failure");
        provider.closeFailures.add(first);provider.closeFailures.add(second);
        var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)));
        var result=runtime.execute("two",runtime.plan(List.of(analysisOnly("A",key),analysisOnly("B",other))));
        runtime.execute("reuse",runtime.plan(List.of(analysisOnly("A",key))));
        assertEquals(2,provider.starts);assertEquals(0,provider.closes,"live runs survive planning epochs");
        var failure=assertThrows(IllegalStateException.class,runtime::close);
        assertSame(first,failure);assertArrayEquals(new Throwable[]{second},failure.getSuppressed());
        assertEquals(2,provider.closes,"one cleanup failure cannot abandon another owned run");
        runtime.close();assertEquals(2,provider.closes,"closed lifetime never retries released runs");
        assertThrows(IllegalStateException.class,()->runtime.plan(List.of()));
        assertEquals(PreparationStatus.COMPLETE,result.preparationStatus(),"detached metadata survives cleanup failure");
    }
    @Test void rejectedExecutionClosesItsRunAndPreservesBindingFailure() {
        var p=linear(1,1,1,2);var key=key(p);var other=PossibleValuesProvider.key(p.units().getFirst().entries().get(1).id());
        var cleanup=new IllegalStateException("cleanup of rejected run");
        var provider=new ProviderWrapper(){
            @Override AnalysisOutcome outcome(AnalysisOutcome o){return new AnalysisOutcome(other,o.status(),o.reason(),o.metrics());}
        };
        provider.closeFailures.add(cleanup);
        try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)))) {
            var failure=assertThrows(IllegalArgumentException.class,()->runtime.execute("wrong",runtime.plan(List.of(analysisOnly("A",key)))));
            assertArrayEquals(new Throwable[]{cleanup},failure.getSuppressed());
            assertEquals(1,provider.closes,"rejected run never enters the cache but still owns cleanup");
        }
        assertEquals(1,provider.closes);
    }
    @Test void failedPreparationCloseRetiresStartedRunAndNeverCachesIt() {
        var p=vertical();var key=key(p);var retired=new int[2];
        var cleanup=new IllegalStateException("preparation cleanup");var runCleanup=new IllegalArgumentException("run cleanup");
        var provider=new ProviderWrapper(){
            @Override public Prepared<ObjectId,ValueFact> prepare(io.github.gustavo2358.analysis.structure.AnalysisSession session,AnalysisKey requested) {
                var owned=delegate.prepare(session,requested);
                return new Prepared<>() {
                    public AnalysisKey key(){return requested;}public AnalysisOutcome refusal(){return owned.refusal();}
                    public Run<ObjectId,ValueFact> execute(){var run=owned.execute();return new Run<>() {
                        public AnalysisOutcome outcome(){return run.outcome();}
                        public Materialized<ObjectId,ValueFact> observe(List<io.github.gustavo2358.analysis.query.PointQuery<ObjectId>> queries){return run.observe(queries);}
                        public void close(){retired[1]++;run.close();throw runCleanup;}
                    };}
                    public void close(){retired[0]++;owned.close();throw cleanup;}
                };
            }
        };
        try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)))) {
            var failure=assertThrows(IllegalStateException.class,()->runtime.execute("close-failure",runtime.plan(List.of(analysisOnly("A",key)))));
            assertSame(cleanup,failure);assertArrayEquals(new Throwable[]{runCleanup},failure.getSuppressed());
            assertArrayEquals(new int[]{1,1},retired,"unsuccessful ownership handoff must retire preparation and run");
        }
        assertArrayEquals(new int[]{1,1},retired,"failed handoff must never enter the runtime cache");
    }
    @Test void preparationClosesOnRefusalBindingFailureAndFailedExecute() {
        var p=vertical();var key=key(p);var other=PossibleValuesProvider.key(key.entry(),PossibleValuesAnalysis.EFFECTS_PROFILE);
        for(int mode=0;mode<3;mode++) {
            int selected=mode;var retired=new int[1];var failure=new IllegalStateException("execute failure");var cleanup=new IllegalArgumentException("preparation close failure");
            var provider=new ProviderWrapper(){
                @Override public Prepared<ObjectId,ValueFact> prepare(io.github.gustavo2358.analysis.structure.AnalysisSession session,AnalysisKey requested) {
                    return new Prepared<>() {
                        public AnalysisKey key(){return selected==0?other:requested;}
                        public AnalysisOutcome refusal(){return selected==1?new AnalysisOutcome(requested,AnalysisOutcome.Status.UNSUPPORTED,"synthetic refusal",java.util.Map.of()):null;}
                        public Run<ObjectId,ValueFact> execute(){throw failure;}
                        public void close(){retired[0]++;if(selected==2)throw cleanup;}
                    };
                }
            };
            try(var runtime=new PlanningExecution(session(p),new AnalysisRegistry(List.of(provider)))) {
                var plan=runtime.plan(List.of(analysisOnly("A",key)));
                if(mode==0)assertThrows(IllegalArgumentException.class,()->runtime.execute("wrong-key",plan));
                else if(mode==1)assertEquals(PreparationStatus.INCOMPLETE,runtime.execute("refused",plan).preparationStatus());
                else {var actual=assertThrows(IllegalStateException.class,()->runtime.execute("failed-execute",plan));assertSame(failure,actual);assertArrayEquals(new Throwable[]{cleanup},actual.getSuppressed());}
                assertEquals(1,retired[0]);
            }
            assertEquals(1,retired[0]);
        }
    }
}
