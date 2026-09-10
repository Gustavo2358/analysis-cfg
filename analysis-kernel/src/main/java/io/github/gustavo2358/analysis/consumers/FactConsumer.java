package io.github.gustavo2358.analysis.consumers;

/** Receives only the selected occurrence and its explicitly prepared dependencies. No analysis/replay capability. */
@FunctionalInterface
public interface FactConsumer<F> {
    void consume(SiteView site, PreparedFacts facts, FactSink<F> sink);
    /** Controlled consumer failure. Infrastructure errors are not partial semantic completion. */
    final class ConsumerException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public ConsumerException(String message) { super(message); }
    }
}
