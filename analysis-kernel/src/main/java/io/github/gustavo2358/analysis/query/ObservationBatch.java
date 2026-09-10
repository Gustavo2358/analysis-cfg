package io.github.gustavo2358.analysis.query;

import java.util.List;
import java.util.Objects;

/** Atomic materialization; unsupported individual points remain in a COMPLETE batch. */
public record ObservationBatch<T,V>(Status status,String reason,List<Observation<T,V>> observations,Metrics metrics) {
    public enum Status { COMPLETE, FAILED }
    public enum QueryStatus { VALUE, UNSUPPORTED_POINT }
    public enum PointReason { CONTEXT_NOT_SELECTED, UNKNOWN_OPERATION, FOREIGN_UNIT, AFTER_TERMINATOR, OUTCOME_UNAVAILABLE, UNSUPPORTED_SUBJECT }
    public record Observation<T,V>(PointQuery<T> query,QueryStatus status,PointReason reason,V value) {
        public Observation {
            Objects.requireNonNull(query);Objects.requireNonNull(status);
            if(status==QueryStatus.VALUE ? reason!=null||value==null : reason==null||value!=null)throw new IllegalArgumentException("observation shape");
        }
    }
    public record Metrics(long queryRequests,long uniqueQueries,long sequencesReplayed,long operationsReplayed,
                          long maxRequestedOffsetObserved,long queriesAnswered,long unsupportedQueries,
                          long queriesNotMaterialized,long observationFailures) { }
    public ObservationBatch {
        Objects.requireNonNull(status);Objects.requireNonNull(metrics); observations=List.copyOf(observations);
        if(status==Status.COMPLETE ? reason!=null : !"OBSERVATION_ERROR".equals(reason)||!observations.isEmpty())throw new IllegalArgumentException("batch shape");
    }
    /** Explicit controlled projection/replay failure; execution/resource errors are never caught as coverage. */
    public static final class ObservationException extends RuntimeException {
        private static final long serialVersionUID=1L;
        public ObservationException(String message) { super(message); }
    }
}
