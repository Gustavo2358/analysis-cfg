package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

final class PlanningFixtures {
    private PlanningFixtures() { }
    static AnalysisKey key(Publication p) { return PossibleValuesProvider.key(p.units().getFirst().entries().getFirst().id()); }
    static PointQuery<ObjectId> query(Publication p) {
        var u=p.units().getFirst(); return new PointQuery<>(ProgramPoint.before(u.entries().getFirst().id(),u.sequences().getLast().terminator().header().id()),u.objects().getFirst().id());
    }
    static AnalysisRegistry registry() { return new AnalysisRegistry(List.of(new PossibleValuesProvider())); }
    static Publication vertical() { return graph(new String[]{"PROGA"},new int[][]{{}},1,false,false); }
    record TestFact(OperationId site, ObservationBatch.Observation<ObjectId,ValueFact> observation) { }
    static ConsumerRegistration<TestFact> queryConsumer(String id, AnalysisKey key, ObservationBatchId<ObjectId,ValueFact> batch,
                                                        List<PointQuery<ObjectId>> queries) {
        return new ConsumerRegistration<>(new ConsumerPlan(id,List.of(key),List.of(batch.id())),
            List.of(new SiteInterest(Operations.Return.class,key.entry(),s->true)),List.of(new ObservationRequest<>(batch,queries)),
            (site,facts,sink)-> {
                assertEquals(AnalysisOutcome.Status.STABLE,facts.analysis(key).status(),"consumer requires fixed point");
                for(var q:new LinkedHashSet<>(queries)) {
                    var found=facts.lookup(batch,q); assertEquals(PreparedFacts.LookupStatus.AVAILABLE,found.status(),"planned observation available");
                    sink.emit(new TestFact(site.operationId(),found.observation()));
                }
            });
    }
    static ConsumerRegistration<TestFact> structural(String id, AnalysisKey key) {
        return new ConsumerRegistration<>(new ConsumerPlan(id,List.of(),List.of()),List.of(new SiteInterest(Operations.Return.class,key.entry(),s->true)),List.of(),
            (site,facts,sink)->sink.emit(new TestFact(site.operationId(),null)));
    }
    static ConsumerRegistration<TestFact> analysisOnly(String id, AnalysisKey key) {
        return new ConsumerRegistration<>(new ConsumerPlan(id,List.of(key),List.of()),List.of(new SiteInterest(Operations.Return.class,key.entry(),s->true)),List.of(),
            (site,facts,sink)->{ assertEquals(AnalysisOutcome.Status.STABLE,facts.analysis(key).status()); sink.emit(new TestFact(site.operationId(),null)); });
    }
    static long metric(PreparedAnalysisResult<?> r,String phase,String name) { return r.metrics().get(phase).get(name); }
    /** Controlled provider seam, delegates real admission/solve/replay; fails only a selected batch observation call. */
    static class ProviderWrapper implements AnalysisProvider<ObjectId,ValueFact> {
        final PossibleValuesProvider delegate=new PossibleValuesProvider();
        int starts, observations;
        public String implementation(){return delegate.implementation();} public String version(){return delegate.version();}
        public Set<String> semanticOptionNames(){return delegate.semanticOptionNames();}
        public boolean supports(AnalysisKey k){return delegate.supports(k);} public String projection(){return delegate.projection();}
        public Class<ObjectId> subjectType(){return ObjectId.class;} public Class<ValueFact> factType(){return ValueFact.class;}
        public Comparator<ObjectId> subjectOrder(){return delegate.subjectOrder();}
        AnalysisKey preparedKey(AnalysisKey key){return key;} AnalysisOutcome outcome(AnalysisOutcome result){return result;}
        Materialized<ObjectId,ValueFact> observe(Run<ObjectId,ValueFact> run,List<PointQuery<ObjectId>> queries){return run.observe(queries);}
        public Prepared<ObjectId,ValueFact> prepare(AnalysisSession session,AnalysisKey key) {
            var prepared=delegate.prepare(session,key);
            return new Prepared<>() {
                public AnalysisKey key(){return preparedKey(key);} public AnalysisOutcome refusal(){return prepared.refusal();}
                public Run<ObjectId,ValueFact> execute() {
                    starts++; var run=prepared.execute();
                    return new Run<>() {
                        public AnalysisOutcome outcome(){return ProviderWrapper.this.outcome(run.outcome());}
                        public Materialized<ObjectId,ValueFact> observe(List<PointQuery<ObjectId>> queries){observations++;return ProviderWrapper.this.observe(run,queries);}
                    };
                }
            };
        }
    }
    /** Test-only semantic profiles/options reuse the real solver; projection explicitly incorporates the declared semantics. */
    static final class VariantProvider implements AnalysisProvider<ObjectId,String> {
        int starts;
        public String implementation(){return "Variant";} public String version(){return "1";}
        public Set<String> semanticOptionNames(){return Set.of("label");}
        public boolean supports(AnalysisKey k){return k.direction()==io.github.gustavo2358.analysis.solver.Direction.FORWARD && Set.of("a","b").contains(k.profile());}
        public String projection(){return "variant";} public Class<ObjectId> subjectType(){return ObjectId.class;} public Class<String> factType(){return String.class;}
        public Comparator<ObjectId> subjectOrder(){return new PossibleValuesProvider().subjectOrder();}
        public Prepared<ObjectId,String> prepare(AnalysisSession session,AnalysisKey key) {
            var prepared=new PossibleValuesProvider().prepare(session,PossibleValuesProvider.key(key.entry()));
            return new Prepared<>() {
                public AnalysisKey key(){return key;} public AnalysisOutcome refusal(){return null;}
                public Run<ObjectId,String> execute(){starts++;var run=prepared.execute();return new Run<>() {
                    public AnalysisOutcome outcome(){return new AnalysisOutcome(key,AnalysisOutcome.Status.STABLE,null,run.outcome().metrics());}
                    public Materialized<ObjectId,String> observe(List<PointQuery<ObjectId>> queries){
                        var batch=run.observe(queries).batch();var obs=batch.observations().stream().map(o->new ObservationBatch.Observation<>(o.query(),o.status(),o.reason(),o.value()==null?null:key.profile()+":"+key.options()+":"+key.entry().localId()+":"+o.value().candidates())).toList();
                        return new Materialized<>(new ObservationBatch<>(batch.status(),batch.reason(),obs,batch.metrics()),Map.of());
                    }
                };}
            };
        }
    }
}
