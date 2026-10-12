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
    interface Prepared<T,V> extends AutoCloseable {
        AnalysisKey key();
        /** Null means admitted. Refusal must be detached and never starts a solver. */
        AnalysisOutcome refusal();
        Run<T,V> execute();
        /** Retire preparation-owned resources. Already started runs own their
         * independent execution lease; they remain valid until Run.close(). */
        @Override default void close() { }
    }
    interface Run<T,V> extends AutoCloseable {
        AnalysisOutcome outcome();
        Materialized<T,V> observe(List<PointQuery<T>> queries);
        /** Release execution-owned state after its last planning epoch. Detached facts remain valid.
         * Stateless providers need no cleanup; state-owning providers override this idempotently. */
        @Override default void close() { }
    }
    /** Explicit opt-in for immutable observations of this exact stable run. Results
     * correspond to input batches in order and retain each batch's atomic failure
     * scope, point identities and local query metrics. Physical replay work is
     * counted once, not once per recipient. Ordinary Run implementations are never
     * inferred to have this property. No sharing across runs or planning epochs. */
    interface JointRun<T,V> extends Run<T,V> {
        List<Materialized<T,V>> observeBatches(List<List<PointQuery<T>>> batches);
    }
    record Materialized<T,V>(ObservationBatch<T,V> batch, Map<String,Long> metrics) {
        public Materialized { Objects.requireNonNull(batch); metrics = Map.copyOf(metrics); }
    }
}
