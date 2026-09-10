package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;

/** Explicitly registered, trusted semantic implementation. All profiles are independent in CP5.
 * Provider owns execution/direction/materializer binding. The planner never receives transfer or state. */
public interface AnalysisProvider<T,V> {
    String implementation();
    String version();
    Set<String> semanticOptionNames();
    boolean supports(AnalysisKey key);
    String projection();
    Class<T> subjectType();
    Class<V> factType();
    Comparator<T> subjectOrder();
    Prepared<T,V> prepare(AnalysisSession session, AnalysisKey key);
    interface Prepared<T,V> {
        AnalysisKey key();
        /** Null means admitted. Refusal must be detached and never starts a solver. */
        AnalysisOutcome refusal();
        Run<T,V> execute();
    }
    interface Run<T,V> {
        AnalysisOutcome outcome();
        Materialized<T,V> observe(List<PointQuery<T>> queries);
    }
    record Materialized<T,V>(ObservationBatch<T,V> batch, Map<String,Long> metrics) {
        public Materialized { Objects.requireNonNull(batch); metrics = Map.copyOf(metrics); }
    }
}
