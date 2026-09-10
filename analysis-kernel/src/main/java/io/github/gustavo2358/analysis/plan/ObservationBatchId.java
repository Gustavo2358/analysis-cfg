package io.github.gustavo2358.analysis.plan;

import java.util.Objects;

/** Explicit batch/projection identity and typed boundary; an ID cannot alias a different analysis or shape. */
public record ObservationBatchId<T,V>(String id, AnalysisKey analysisKey, String projection,
                                      Class<T> subjectType, Class<V> factType) {
    public ObservationBatchId {
        Objects.requireNonNull(analysisKey); Objects.requireNonNull(subjectType); Objects.requireNonNull(factType);
        if (id.isBlank() || projection.isBlank()) throw new IllegalArgumentException("blank batch identity");
    }
}
