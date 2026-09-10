package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;

/** Detached in-memory preparation. COMPLETE does not certify delivery; the wire belongs to a later checkpoint. */
public record PreparedAnalysisResult<F>(String resultId, PublicationId publicationId, long planningEpoch,
        List<AnalysisOutcome> analyses, List<BatchResult> results, List<ConsumerPlan> consumerPlan,
        List<ConsumerOutcome<F>> consumers, PreparationStatus preparationStatus, Map<String,Map<String,Long>> metrics) {
    public enum PreparationStatus { COMPLETE, INCOMPLETE }
    public enum ConsumerStatus { COMPLETE, FAILED, NOT_STARTED }
    public enum BatchStatus { COMPLETE, FAILED, NOT_STARTED }
    public enum PartialPolicy { EXPLICIT_PARTIAL_BY_DEPENDENCY }
    public PartialPolicy partialPolicy() { return PartialPolicy.EXPLICIT_PARTIAL_BY_DEPENDENCY; }
    public PreparedAnalysisResult {
        Objects.requireNonNull(resultId); Objects.requireNonNull(publicationId); Objects.requireNonNull(preparationStatus);
        analyses = List.copyOf(analyses); results = List.copyOf(results); consumerPlan = List.copyOf(consumerPlan); consumers = List.copyOf(consumers);
        var copy = new TreeMap<String,Map<String,Long>>(); metrics.forEach((k,v) -> copy.put(k,Map.copyOf(v))); metrics = Collections.unmodifiableMap(copy);
    }
    public record ConsumerOutcome<F>(String consumerId, ConsumerStatus status, String reason, List<F> facts) {
        public ConsumerOutcome {
            Objects.requireNonNull(consumerId); Objects.requireNonNull(status); facts = List.copyOf(facts);
            if (status == ConsumerStatus.COMPLETE ? reason != null : reason == null || !facts.isEmpty()) throw new IllegalArgumentException("consumer completion shape");
        }
    }
    public record BatchResult(ObservationBatchId<?,?> batchId, BatchStatus status, String reason,
                              List<? extends ObservationBatch.Observation<?,?>> observations, Map<String,Long> metrics) {
        public BatchResult {
            Objects.requireNonNull(batchId); Objects.requireNonNull(status); observations = List.copyOf(observations); metrics = Map.copyOf(metrics);
            if (status == BatchStatus.COMPLETE ? reason != null : reason == null || !observations.isEmpty()) throw new IllegalArgumentException("batch completion shape");
        }
    }
}
