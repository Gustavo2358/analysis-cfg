package io.github.gustavo2358.analysis.plan;

import java.util.*;

/** Detached run completion, with no state, solver or observation materializer. */
public record AnalysisOutcome(AnalysisKey key, Status status, String reason, Map<String,Long> metrics) {
    public enum Status { STABLE, UNSUPPORTED, INVALID_INPUT }
    public AnalysisOutcome {
        Objects.requireNonNull(key); Objects.requireNonNull(status); metrics = Map.copyOf(metrics);
        if (status == Status.STABLE ? reason != null : reason == null || reason.isBlank()) throw new IllegalArgumentException("run completion shape");
    }
}
