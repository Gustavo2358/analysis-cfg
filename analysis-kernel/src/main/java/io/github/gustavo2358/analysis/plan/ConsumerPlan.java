package io.github.gustavo2358.analysis.plan;

import java.util.*;

/** Explicit dependencies, frozen before execution. Empty lists are valid structural-only requirements. */
public record ConsumerPlan(String consumerId, List<AnalysisKey> requiredAnalysisKeys,
                           List<String> requiredObservationBatchIds) {
    public ConsumerPlan {
        if (consumerId.isBlank()) throw new IllegalArgumentException("blank consumer ID");
        var keys = new TreeSet<AnalysisKey>(AnalysisKey.ORDER); keys.addAll(requiredAnalysisKeys); requiredAnalysisKeys = List.copyOf(keys);
        requiredObservationBatchIds = List.copyOf(new TreeSet<>(requiredObservationBatchIds));
    }
}
