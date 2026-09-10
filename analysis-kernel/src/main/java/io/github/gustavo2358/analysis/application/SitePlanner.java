package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.UnitId;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;

/** Compiles each AIR-kind bucket once, then tests only interests registered for that bucket and owner. */
final class SitePlanner {
    private SitePlanner() { }
    private record Binding(String consumer, SiteInterest interest) { }
    static final Comparator<SiteView> SITE_ORDER = Comparator.comparing(SiteView::entry,AnalysisKey.ENTRY_ORDER)
        .thenComparing(s -> s.operationId().unit().publication().localId()).thenComparing(s -> s.operationId().unit().localId())
        .thenComparing(s -> s.operationId().localId());
    static <F> ExecutionPlan<F> compile(Object owner, long epoch, AnalysisSession session, AnalysisRegistry registry,
                                        List<ConsumerRegistration<F>> registrations) {
        var consumers = new TreeMap<String,ConsumerRegistration<F>>();
        var byKind = new TreeMap<Class<? extends Operation>,Map<UnitId,List<Binding>>>(Comparator.comparing(Class::getName));
        var sites = new TreeMap<String,TreeSet<SiteView>>();
        var requests = new TreeMap<String,List<ObservationRequest<?,?>>>();
        var keys = new TreeSet<AnalysisKey>(AnalysisKey.ORDER);
        var counts = new Counts("planningEpochs","candidateSites","structuralVisits","siteMatches","filterEvaluations",
            "planningCallbacks","queryRequests","uniqueQueries","observationBatchesPlanned","analysisRequests");
        counts.add("planningEpochs",1);
        for (var registration : registrations) {
            var dependency = registration.dependencies(); String id = dependency.consumerId();
            if (consumers.putIfAbsent(id,registration) != null) throw new IllegalArgumentException("duplicate consumer ID");
            sites.put(id,new TreeSet<>(SITE_ORDER)); requests.put(id,new ArrayList<>(registration.requests()));
            for (var key : dependency.requiredAnalysisKeys()) {
                requireContext(session,key.entry()); registry.require(key); keys.add(key); counts.add("analysisRequests",1);
            }
            for (var interest : registration.interests()) {
                requireContext(session,interest.entry());
                byKind.computeIfAbsent(interest.kind(),ignored -> new HashMap<>())
                    .computeIfAbsent(interest.entry().unit(),ignored -> new ArrayList<>()).add(new Binding(id,interest));
            }
        }
        for (var bucket : byKind.entrySet()) {
            for (var candidate : session.index().sites(bucket.getKey())) {
                counts.add("structuralVisits",1); counts.add("candidateSites",1);
                var bindings = bucket.getValue().getOrDefault(candidate.owner().id(),List.of());
                for (var binding : bindings) {
                    var interest = binding.interest();
                    var site = new SiteView(interest.entry(),candidate.sequence().label(),candidate.offset(),candidate.operation());
                    counts.add("filterEvaluations",1);
                    if (!interest.filter().test(site)) continue;
                    if (sites.get(binding.consumer()).add(site)) counts.add("siteMatches",1);
                    for (var query : interest.queries()) {
                        counts.add("planningCallbacks",1); requests.get(binding.consumer()).add(query.at(site));
                    }
                }
            }
        }
        var batches = new TreeMap<String,PendingBatch>();
        var localRequests = new TreeMap<String,Map<String,Set<PointQuery<?>>>>();
        for (var consumer : consumers.values()) {
            var dependencies = consumer.dependencies(); var requested = new TreeMap<String,Set<PointQuery<?>>>();
            for (var request : requests.get(dependencies.consumerId())) {
                var batch = request.batch(); registry.require(batch);
                if (!dependencies.requiredAnalysisKeys().contains(batch.analysisKey()) || !dependencies.requiredObservationBatchIds().contains(batch.id()))
                    throw new IllegalArgumentException("request missing explicit consumer dependency");
                var pending = batches.computeIfAbsent(batch.id(),ignored -> new PendingBatch(batch));
                if (!pending.id.equals(batch)) throw new IllegalArgumentException("incompatible batch ID binding");
                var local = requested.computeIfAbsent(batch.id(),ignored -> new HashSet<>());
                for (var query : request.queries()) {
                    if (!query.point().entry().equals(batch.analysisKey().entry()) || !batch.subjectType().isInstance(query.subject()))
                        throw new IllegalArgumentException("query/key Entry or subject binding mismatch");
                    pending.raw = Math.incrementExact(pending.raw); counts.add("queryRequests",1);
                    pending.queries.add(query); local.add(query);
                }
            }
            localRequests.put(dependencies.consumerId(),requested);
        }
        // Required IDs must resolve, including analysis-only and structural consumers with empty lists.
        for (var consumer : consumers.values()) for (String batchId : consumer.dependencies().requiredObservationBatchIds()) {
            var batch = batches.get(batchId);
            if (batch == null || !consumer.dependencies().requiredAnalysisKeys().contains(batch.id.analysisKey()))
                throw new IllegalArgumentException("missing or mismatched consumer batch dependency");
        }
        var frozen = new TreeMap<String,ExecutionPlan.BatchPlan>();
        for (var batch : batches.values()) {
            var ordered = new ArrayList<PointQuery<?>>(batch.queries);
            ordered.sort(queryOrder(registry.require(batch.id.analysisKey())));
            frozen.put(batch.id.id(),new ExecutionPlan.BatchPlan(batch.id,ordered,batch.raw));
            counts.add("uniqueQueries",ordered.size()); counts.add("observationBatchesPlanned",1);
        }
        var selected = new TreeMap<String,List<SiteView>>(); sites.forEach((id,values) -> selected.put(id,List.copyOf(values)));
        return new ExecutionPlan<>(owner,epoch,List.copyOf(consumers.values()),selected,List.copyOf(keys),frozen,localRequests,counts.snapshot());
    }
    private static void requireContext(AnalysisSession session, io.github.gustavo2358.air.model.Ids.EntryId entry) {
        if (session.context(entry) == null) throw new IllegalArgumentException("Entry not selected in this session");
    }
    private static <T,V> Comparator<PointQuery<?>> queryOrder(AnalysisProvider<T,V> provider) {
        return Comparator.comparing(PointQuery<?>::point,ProgramPoint.ORDER)
            .thenComparing(q -> provider.subjectType().cast(q.subject()),provider.subjectOrder());
    }
    private static final class PendingBatch {
        final ObservationBatchId<?,?> id;
        final Set<PointQuery<?>> queries = new HashSet<>();
        long raw;
        PendingBatch(ObservationBatchId<?,?> id) { this.id = id; }
    }
}
