package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.analysis.plan.*;
import java.util.*;

/** Immutable explicit factory binding. No discovery, classpath scan or consumer-controlled factory injection. */
public final class AnalysisRegistry {
    private record Identity(String implementation, String version) { }
    private final Map<Identity,AnalysisProvider<?,?>> providers;
    public AnalysisRegistry(Collection<? extends AnalysisProvider<?,?>> registrations) {
        var map = new HashMap<Identity,AnalysisProvider<?,?>>();
        for (var p : registrations) {
            Objects.requireNonNull(p);
            if (map.putIfAbsent(new Identity(p.implementation(),p.version()),p) != null)
                throw new IllegalArgumentException("duplicate provider identity");
        }
        providers = Map.copyOf(map);
    }
    AnalysisProvider<?,?> require(AnalysisKey key) {
        var p = providers.get(new Identity(key.implementation(),key.version()));
        if (p == null) throw new IllegalArgumentException("unknown analysis provider");
        if (!p.semanticOptionNames().containsAll(key.options().keySet())) throw new IllegalArgumentException("unregistered semantic options");
        if (!p.supports(key)) throw new IllegalArgumentException("unsupported semantic key");
        return p;
    }
    void require(ObservationBatchId<?,?> batch) {
        var p = require(batch.analysisKey());
        if (!p.projection().equals(batch.projection()) || p.subjectType() != batch.subjectType() || p.factType() != batch.factType())
            throw new IllegalArgumentException("batch/provider binding mismatch");
    }
}
