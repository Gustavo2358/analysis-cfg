package io.github.gustavo2358.analysis.consumers;

import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.Objects;

/** Lookup-only capability; a late/foreign query is NOT_REQUESTED and can never start replay. */
public interface PreparedFacts {
    AnalysisOutcome analysis(AnalysisKey key);
    <T,V> Lookup<T,V> lookup(ObservationBatchId<T,V> batch, PointQuery<T> query);
    enum LookupStatus { AVAILABLE, NOT_REQUESTED }
    record Lookup<T,V>(LookupStatus status, ObservationBatch.Observation<T,V> observation) {
        public Lookup {
            Objects.requireNonNull(status);
            if ((status == LookupStatus.AVAILABLE) != (observation != null)) throw new IllegalArgumentException("lookup shape");
        }
    }
}
