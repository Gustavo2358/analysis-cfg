package io.github.gustavo2358.analysis.solver;

/**
 * Exact fixed-size mutable pages behind a session-owned port. Positive handles are store-local,
 * stable and never reused during a session. Callers retain owner identity alongside graph roots.
 * Implementations account for payload, directories and buffers; no filesystem type enters core.
 * A released page is invalid; a newly allocated page reads as all zero bytes.
 */
public interface PageStore extends AutoCloseable {
    enum Reason { IO, CORRUPT, INVALID_HANDLE, CLOSED }
    final class Failure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;
        public Failure(Reason reason, String message) { super(message); this.reason = reason; }
        public Failure(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
        public Reason reason() { return reason; }
    }
    record Statistics(long pagesIssued, long livePages, long cacheHits, long cacheMisses,
                      long evictions, long bytesRead, long bytesWritten) { }
    int pageBytes();
    long allocate();
    void read(long page, int offset, byte[] target, int targetOffset, int length);
    void write(long page, int offset, byte[] source, int sourceOffset, int length);
    void release(long page);
    /** Teardown-only metadata access. Implementations must not admit allocations or
     * spend analysis WORK here. Normal analysis must use read/release above.
     * Address/generation checks and genuine I/O failures still apply. */
    void readForCleanup(long page, int offset, byte[] target, int targetOffset, int length);
    void releaseForCleanup(long page);
    void flush();
    Statistics statistics();
    @Override void close();
}
