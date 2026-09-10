package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.PointQuery;
import java.util.*;

/** Immutable planning epoch; callbacks are retained only until the caller releases the plan. */
public final class ExecutionPlan<F> {
    final Object owner;
    final long epoch;
    final List<ConsumerRegistration<F>> registrations;
    final Map<String,List<SiteView>> sites;
    final List<AnalysisKey> keys;
    final Map<String,BatchPlan> batches;
    final Map<String,Map<String,Set<PointQuery<?>>>> requests;
    final Map<String,Long> metrics;
    ExecutionPlan(Object owner, long epoch, List<ConsumerRegistration<F>> registrations,
                  Map<String,List<SiteView>> sites, List<AnalysisKey> keys, Map<String,BatchPlan> batches,
                  Map<String,Map<String,Set<PointQuery<?>>>> requests, Map<String,Long> metrics) {
        this.owner = owner; this.epoch = epoch; this.registrations = List.copyOf(registrations);
        var selected = new TreeMap<String,List<SiteView>>(); sites.forEach((k,v) -> selected.put(k,List.copyOf(v))); this.sites = Collections.unmodifiableMap(selected);
        this.keys = List.copyOf(keys); this.batches = Collections.unmodifiableMap(new TreeMap<>(batches));
        var requested = new TreeMap<String,Map<String,Set<PointQuery<?>>>>();
        requests.forEach((consumer, byBatch) -> {
            var copy = new TreeMap<String,Set<PointQuery<?>>>(); byBatch.forEach((id,qs) -> copy.put(id,Set.copyOf(qs)));
            requested.put(consumer,Collections.unmodifiableMap(copy));
        });
        this.requests = Collections.unmodifiableMap(requested); this.metrics = Map.copyOf(metrics);
    }
    public long epoch() { return epoch; }
    public Map<String,List<SiteView>> sites() { return sites; }
    public List<AnalysisKey> analysisKeys() { return keys; }
    public List<ConsumerPlan> consumers() { return registrations.stream().map(ConsumerRegistration::dependencies).toList(); }
    public Map<String,Long> metrics() { return metrics; }
    public List<ObservationPlan> observations() {
        return batches.values().stream().map(b -> new ObservationPlan(b.id,b.queries,b.rawRequests)).toList();
    }
    public record ObservationPlan(ObservationBatchId<?,?> batchId, List<PointQuery<?>> queries, long queryRequests) {
        public ObservationPlan { queries = List.copyOf(queries); }
    }
    static final class BatchPlan {
        final ObservationBatchId<?,?> id;
        final List<PointQuery<?>> queries;
        final long rawRequests;
        BatchPlan(ObservationBatchId<?,?> id, List<PointQuery<?>> queries, long rawRequests) {
            this.id = id; this.queries = List.copyOf(queries); this.rawRequests = rawRequests;
        }
    }
}
