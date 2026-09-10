package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;
import static io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;

/** Sequential session-owned lifetime. Every late registration creates an explicit new immutable planning epoch. */
public final class PlanningExecution implements AutoCloseable {
    private final Object identity = new Object();
    private AnalysisSession session;
    private AnalysisRegistry registry;
    private final Map<AnalysisKey,CachedRun> runs = new HashMap<>();
    private final Set<Long> executed = new HashSet<>();
    private long epoch;
    private boolean executing;
    public PlanningExecution(AnalysisSession session, AnalysisRegistry registry) {
        this.session = Objects.requireNonNull(session); this.registry = Objects.requireNonNull(registry);
    }
    public <F> ExecutionPlan<F> plan(List<ConsumerRegistration<F>> registrations) {
        requireOpen(); if (executing) throw new IllegalStateException("registration during execution requires a later explicit epoch");
        long next = Math.incrementExact(epoch);
        var plan = SitePlanner.compile(identity,next,session,registry,registrations); epoch = next; return plan;
    }
    public <F> PreparedAnalysisResult<F> execute(String resultId, ExecutionPlan<F> plan) {
        requireOpen(); Objects.requireNonNull(resultId);
        if (executing || plan.owner != identity || !executed.add(plan.epoch)) throw new IllegalStateException("foreign or already executed planning epoch");
        executing = true;
        try { return executePlan(resultId,plan); } finally { executing = false; }
    }
    private <F> PreparedAnalysisResult<F> executePlan(String resultId, ExecutionPlan<F> plan) {
        var analysis = new Counts("analysisRuns","analysisCacheHits","analysisAdmissionCacheHits");
        var observations = new Counts("observationBatchesExecuted","queryRequests","uniqueQueries","sequencesReplayed","operationsReplayed",
            "queriesAnswered","unsupportedQueries","queriesNotMaterialized","observationFailures",
            "modelOpenResults","sourceOpenResults","effectiveOpenResults","closedInModelResults");
        var consumers = new Counts("consumerInvocations","consumerFailures","consumersComplete","consumersNotStarted",
            "factsStaged","factsCommitted","factsDiscarded","factsEmitted","notRequested");
        var selectedRuns = new TreeMap<AnalysisKey,CachedRun>(AnalysisKey.ORDER);
        for (var registration : plan.registrations) for (var key : registration.dependencies().requiredAnalysisKeys()) {
            var cached = runs.get(key);
            if (cached == null) {
                cached = start(registry.require(key),key,analysis); runs.put(key,cached);
            } else analysis.add(cached.outcome().status() == AnalysisOutcome.Status.STABLE ? "analysisCacheHits" : "analysisAdmissionCacheHits",1);
            selectedRuns.put(key,cached);
        }
        var outcomes = new TreeMap<AnalysisKey,AnalysisOutcome>(AnalysisKey.ORDER);
        selectedRuns.forEach((key,run) -> outcomes.put(key,run.outcome()));
        var batches = new TreeMap<String,BatchResult>();
        var answers = new TreeMap<String,Map<PointQuery<?>,ObservationBatch.Observation<?,?>>>();
        for (var batch : plan.batches.values()) {
            var run = selectedRuns.get(batch.id.analysisKey()); BatchResult result;
            if (run.outcome().status() != AnalysisOutcome.Status.STABLE) {
                result = new BatchResult(batch.id,BatchStatus.NOT_STARTED,"DEPENDENCY_UNAVAILABLE",List.of(),
                    Map.of("queryRequests",batch.rawRequests,"uniqueQueries",(long)batch.queries.size(),"queriesNotMaterialized",(long)batch.queries.size()));
            } else {
                observations.add("observationBatchesExecuted",1); result = run.observe(batch);
            }
            for (String name : observations.snapshot().keySet()) if (!name.equals("observationBatchesExecuted"))
                observations.add(name,result.metrics().getOrDefault(name,0L));
            batches.put(batch.id.id(),result);
            var index = new HashMap<PointQuery<?>,ObservationBatch.Observation<?,?>>();
            for (var observation : result.observations()) index.put(observation.query(),observation);
            answers.put(batch.id.id(),Map.copyOf(index));
        }
        var completed = new ArrayList<ConsumerOutcome<F>>();
        for (var registration : plan.registrations) {
            var dependencies = registration.dependencies(); String id = dependencies.consumerId();
            boolean available = dependencies.requiredAnalysisKeys().stream().allMatch(k -> outcomes.containsKey(k) && outcomes.get(k).status() == AnalysisOutcome.Status.STABLE)
                && dependencies.requiredObservationBatchIds().stream().allMatch(b -> batches.containsKey(b) && batches.get(b).status() == BatchStatus.COMPLETE);
            if (!available) {
                consumers.add("consumersNotStarted",1); completed.add(new ConsumerOutcome<>(id,ConsumerStatus.NOT_STARTED,"DEPENDENCY_UNAVAILABLE",List.of())); continue;
            }
            var facts = new ResolvedFacts(dependencies,plan.requests.get(id),outcomes,batches,answers,consumers);
            var staging = new Staging<F>(consumers);
            try {
                for (var site : plan.sites.get(id)) {
                    consumers.add("consumerInvocations",1); registration.consumer().consume(site,facts,staging);
                }
                var bundle = staging.commit(); consumers.add("consumersComplete",1);
                completed.add(new ConsumerOutcome<>(id,ConsumerStatus.COMPLETE,null,bundle));
            } catch (FactConsumer.ConsumerException failure) {
                staging.discard(); consumers.add("consumerFailures",1);
                completed.add(new ConsumerOutcome<>(id,ConsumerStatus.FAILED,"CONSUMER_ERROR",List.of()));
            } finally { staging.close(); }
        }
        boolean complete = outcomes.values().stream().allMatch(r -> r.status() == AnalysisOutcome.Status.STABLE)
            && batches.values().stream().allMatch(b -> b.status() == BatchStatus.COMPLETE)
            && completed.stream().allMatch(c -> c.status() == ConsumerStatus.COMPLETE);
        return new PreparedAnalysisResult<>(resultId,session.index().publication().id(),plan.epoch,List.copyOf(outcomes.values()),List.copyOf(batches.values()),
            plan.consumers(),completed,complete ? PreparationStatus.COMPLETE : PreparationStatus.INCOMPLETE,
            Map.of("planning",plan.metrics,"analysis",analysis.snapshot(),"observation",observations.snapshot(),"consumer",consumers.snapshot()));
    }
    private <T,V> CachedRun start(AnalysisProvider<T,V> provider, AnalysisKey key, Counts counts) {
        var prepared = Objects.requireNonNull(provider.prepare(session,key));
        if (!key.equals(prepared.key())) throw new IllegalArgumentException("provider prepared wrong AnalysisKey");
        var refusal = prepared.refusal();
        if (refusal != null) {
            if (!key.equals(refusal.key()) || refusal.status() == AnalysisOutcome.Status.STABLE) throw new IllegalArgumentException("provider refusal binding mismatch");
            return new CachedRun() {
                public AnalysisOutcome outcome() { return refusal; }
                public BatchResult observe(ExecutionPlan.BatchPlan batch) { throw new IllegalStateException("refused run"); }
            };
        }
        counts.add("analysisRuns",1); var run = Objects.requireNonNull(prepared.execute());
        var outcome = Objects.requireNonNull(run.outcome());
        if (!key.equals(outcome.key()) || outcome.status() != AnalysisOutcome.Status.STABLE) throw new IllegalArgumentException("provider execution binding mismatch");
        return new BoundRun<>(provider.subjectType(),provider.factType(),key,outcome,run);
    }
    private interface CachedRun {
        AnalysisOutcome outcome();
        BatchResult observe(ExecutionPlan.BatchPlan batch);
    }
    /** Only a registered provider can supply this inseparable execution/materializer. No external run injection. */
    private static final class BoundRun<T,V> implements CachedRun {
        private final Class<T> subjects;
        private final Class<V> facts;
        private final AnalysisKey key;
        private final AnalysisOutcome outcome;
        private final AnalysisProvider.Run<T,V> execution;
        BoundRun(Class<T> subjects, Class<V> facts, AnalysisKey key, AnalysisOutcome outcome, AnalysisProvider.Run<T,V> execution) {
            this.subjects = subjects; this.facts = facts; this.key = key; this.outcome = outcome; this.execution = execution;
        }
        public AnalysisOutcome outcome() { return outcome; }
        public BatchResult observe(ExecutionPlan.BatchPlan batch) {
            if (!key.equals(batch.id.analysisKey()) || subjects != batch.id.subjectType() || facts != batch.id.factType())
                throw new IllegalArgumentException("wrong batch bound to analysis key");
            var queries = new ArrayList<PointQuery<T>>();
            for (var query : batch.queries) queries.add(new PointQuery<>(query.point(),subjects.cast(query.subject())));
            AnalysisProvider.Materialized<T,V> materialized;
            try { materialized = execution.observe(List.copyOf(queries)); }
            catch (ObservationBatch.ObservationException failure) {
                materialized = new AnalysisProvider.Materialized<>(new ObservationBatch<>(ObservationBatch.Status.FAILED,"OBSERVATION_ERROR",List.of(),
                    new ObservationBatch.Metrics(queries.size(),queries.size(),0,0,0,0,0,queries.size(),1)),Map.of());
            }
            var result = materialized.batch(); var m = result.metrics();
            if (result.status() == ObservationBatch.Status.COMPLETE) {
                var expected = new HashSet<>(queries); var received = new HashSet<PointQuery<T>>();
                for (var observation : result.observations()) {
                    if (!received.add(observation.query()) || !expected.contains(observation.query())) throw new IllegalStateException("provider observation coverage mismatch");
                    if (observation.value() != null) facts.cast(observation.value());
                }
                if (!received.equals(expected)) throw new IllegalStateException("provider omitted requested observation");
            }
            var metrics = new TreeMap<>(materialized.metrics());
            metrics.put("queryRequests",batch.rawRequests); metrics.put("uniqueQueries",m.uniqueQueries());
            metrics.put("sequencesReplayed",m.sequencesReplayed()); metrics.put("operationsReplayed",m.operationsReplayed());
            metrics.put("queriesAnswered",m.queriesAnswered()); metrics.put("unsupportedQueries",m.unsupportedQueries());
            metrics.put("queriesNotMaterialized",m.queriesNotMaterialized()); metrics.put("observationFailures",m.observationFailures());
            return new BatchResult(batch.id,result.status() == ObservationBatch.Status.COMPLETE ? BatchStatus.COMPLETE : BatchStatus.FAILED,
                result.reason(),result.observations(),metrics);
        }
    }
    private static final class Staging<F> implements FactSink<F> {
        private final List<F> facts = new ArrayList<>();
        private final Counts counts;
        private boolean open = true;
        Staging(Counts counts) { this.counts = counts; }
        public void emit(F fact) {
            if (!open) throw new IllegalStateException("closed consumer staging");
            facts.add(Objects.requireNonNull(fact)); counts.add("factsStaged",1);
        }
        List<F> commit() {
            var bundle = List.copyOf(facts); counts.add("factsCommitted",bundle.size()); counts.add("factsEmitted",bundle.size());
            close(); return bundle;
        }
        void discard() { counts.add("factsDiscarded",facts.size()); close(); }
        void close() { open = false; facts.clear(); }
    }
    private static final class ResolvedFacts implements PreparedFacts {
        private final Map<AnalysisKey,AnalysisOutcome> analyses;
        private final Map<String,ObservationBatchId<?,?>> batches;
        private final Map<String,Map<PointQuery<?>,ObservationBatch.Observation<?,?>>> observations;
        private final Map<String,Set<PointQuery<?>>> requested;
        private final Counts counts;
        ResolvedFacts(ConsumerPlan dependencies, Map<String,Set<PointQuery<?>>> requested,
                      Map<AnalysisKey,AnalysisOutcome> analyses, Map<String,BatchResult> batches,
                      Map<String,Map<PointQuery<?>,ObservationBatch.Observation<?,?>>> observations, Counts counts) {
            var runs = new HashMap<AnalysisKey,AnalysisOutcome>(); dependencies.requiredAnalysisKeys().forEach(k -> runs.put(k,analyses.get(k))); this.analyses = Map.copyOf(runs);
            var ids = new HashMap<String,ObservationBatchId<?,?>>(); var selected = new HashMap<String,Map<PointQuery<?>,ObservationBatch.Observation<?,?>>>();
            dependencies.requiredObservationBatchIds().forEach(id -> { ids.put(id,batches.get(id).batchId()); selected.put(id,observations.get(id)); });
            this.batches = Map.copyOf(ids); this.observations = Map.copyOf(selected); this.requested = requested; this.counts = counts;
        }
        public AnalysisOutcome analysis(AnalysisKey key) {
            var result = analyses.get(key); if (result == null) throw new IllegalArgumentException("analysis NOT_REQUESTED"); return result;
        }
        public <T,V> Lookup<T,V> lookup(ObservationBatchId<T,V> batch, PointQuery<T> query) {
            if (!batch.equals(batches.get(batch.id())) || !requested.getOrDefault(batch.id(),Set.of()).contains(query)) {
                counts.add("notRequested",1); return new Lookup<>(LookupStatus.NOT_REQUESTED,null);
            }
            var observation = Objects.requireNonNull(observations.get(batch.id()).get(query));
            // Only the immutable observation is shared. Casts check the registered typed boundary, never discover an implementation.
            @SuppressWarnings("unchecked") var typed = (ObservationBatch.Observation<T,V>) observation;
            return new Lookup<>(LookupStatus.AVAILABLE,typed);
        }
    }
    private void requireOpen() { if (session == null) throw new IllegalStateException("closed execution lifetime"); }
    @Override public void close() {
        if (executing) throw new IllegalStateException("cannot close during execution");
        runs.clear(); executed.clear(); session = null; registry = null;
    }
}
