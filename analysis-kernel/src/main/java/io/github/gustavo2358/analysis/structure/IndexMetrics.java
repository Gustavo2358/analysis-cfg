package io.github.gustavo2358.analysis.structure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Observations of successful index construction, never admission thresholds. */
public record IndexMetrics(long nodesIndexed, long edgesIndexed, long operationsIndexed,
                           long referencesResolved, long objectsIndexed, long locationsIndexed,
                           long structuralVisits, Map<String, Long> visitsByCollection) {
    public IndexMetrics {
        visitsByCollection = Collections.unmodifiableMap(new LinkedHashMap<>(visitsByCollection));
    }

    static final class Counter {
        long nodes, edges, operations, references, objects, locations;
        private final Map<String, Long> visits = new LinkedHashMap<>();
        void visit(String collection) { visits.merge(collection, 1L, Math::addExact); }
        void reference(String collection) {
            visit(collection);
            references = Math.incrementExact(references);
        }
        IndexMetrics snapshot() {
            long total = 0;
            for (long count : visits.values()) total = Math.addExact(total, count);
            return new IndexMetrics(nodes, edges, operations, references, objects, locations, total, visits);
        }
    }
}
