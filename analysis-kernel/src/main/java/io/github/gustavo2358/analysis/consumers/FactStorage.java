package io.github.gustavo2358.analysis.consumers;

/** Exclusive consumer transaction storage supplied explicitly by a typed owner.
 * The kernel cannot infer a codec, equality, or spill policy for arbitrary Java F.
 * Append copies the complete immutable fact into the chosen representation. Get is
 * an explicit detached projection of one fact, not permission to retain all facts.
 * Each factory invocation supplies a new empty owner. Close is idempotent, retires
 * payload/index/control storage, and never closes borrowed input or shared backend.
 * A committed view expires with its PlanningExecution; ordinary execute remains
 * the explicitly resident, detached compatibility API. No admission certificate. */
public interface FactStorage<F> extends AutoCloseable {
    void append(F fact);
    int size();
    F get(int ordinal);
    @Override void close();
}
