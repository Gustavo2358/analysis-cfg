package io.github.gustavo2358.analysis.solver;

import java.util.Objects;

/**
 * Session-owned capacity accounting. Reserve before growing stores, including capacity and scratch,
 * not just live record counts. No I/O, clock, process inspection, implicit GC or semantic cutoff.
 * Calls are thread-safe; reservations are coarse store/page allocations, never per abstract fact.
 */
public final class AnalysisResources {
    public enum Phase { DECODE, VALIDATION, INDEX, DEMAND, CONTROL, DOMAIN, REPLAY, SORT, ENCODE }
    public enum Pool { RESIDENT, SCRATCH, DIRECT, TEMPORARY, OPEN_FILES }
    public enum Resource { HEAP, SCRATCH, DIRECT, TEMPORARY, OPEN_FILES, WORK, OUTPUT }

    /** Byte limits except openFiles/workUnits. Zero is a real quota, never an unlimited sentinel. */
    public record Limits(long heapBytes, long scratchBytes, long directBytes, long temporaryBytes,
                         long openFiles, long workUnits, long outputBytes) {
        public Limits {
            if (heapBytes < 0 || scratchBytes < 0 || directBytes < 0 || temporaryBytes < 0
                    || openFiles < 0 || workUnits < 0 || outputBytes < 0)
                throw new IllegalArgumentException("resource limits must be nonnegative");
        }
    }

    /** Operational failure; callers must not publish a semantic result from an interrupted run. */
    public static final class Exhausted extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Resource resource;
        private final Phase phase;
        private final long limit, used, requested;
        private Exhausted(Resource resource, Phase phase, long limit, long used, long requested) {
            super("analysis resource exhausted: " + resource + " phase=" + phase + " limit=" + limit
                    + " used=" + used + " requested=" + requested);
            this.resource = resource; this.phase = phase; this.limit = limit;
            this.used = used; this.requested = requested;
        }
        public Resource resource() { return resource; }
        public Phase phase() { return phase; }
        public long limit() { return limit; }
        public long used() { return used; }
        public long requested() { return requested; }
    }

    /** A single owner's capacity; closing twice cannot release another owner's reservation. */
    public final class Reservation implements AutoCloseable {
        private final Pool pool;
        private long amount;
        private boolean closed;
        private Reservation(Pool pool, long amount) { this.pool = pool; this.amount = amount; }
        public long amount() { synchronized (AnalysisResources.this) { return amount; } }
        private AnalysisResources owner() { return AnalysisResources.this; }
        /** Commit staged capacity into this owner without double-charging or releasing live bytes. */
        public void absorb(Reservation staged) {
            Objects.requireNonNull(staged);
            synchronized (AnalysisResources.this) {
                if (staged == this || staged.owner() != AnalysisResources.this || staged.pool != pool)
                    throw new IllegalArgumentException("capacity transfer requires distinct reservations of the same owner and pool");
                if (closed || staged.closed) throw new IllegalStateException("reservation is closed");
                amount += staged.amount; // Both capacities are already in the same bounded pool total.
                staged.amount = 0; staged.closed = true;
            }
        }
        public void grow(long additional, Phase phase) {
            synchronized (AnalysisResources.this) {
                if (closed) throw new IllegalStateException("reservation is closed");
                charge(pool, additional, phase);
                amount += additional; // charge proves the pool total and hence this amount fit in long.
            }
        }
        @Override public void close() {
            synchronized (AnalysisResources.this) {
                if (closed) return;
                int p = pool.ordinal();
                used[p] -= amount;
                if (isHeap(pool)) heapUsed -= amount;
                amount = 0; closed = true;
            }
        }
    }

    private final Limits limits;
    private final long[] used = new long[Pool.values().length];
    private final long[] peak = new long[Pool.values().length];
    private long heapUsed, heapPeak, workUsed, outputUsed;

    public AnalysisResources(Limits limits) { this.limits = Objects.requireNonNull(limits); }
    public Limits limits() { return limits; }

    public synchronized Reservation reserve(Pool pool, long amount, Phase phase) {
        charge(pool, amount, phase);
        return new Reservation(pool, amount);
    }

    private void charge(Pool pool, long amount, Phase phase) {
        Objects.requireNonNull(pool); Objects.requireNonNull(phase);
        nonnegative(amount);
        int p = pool.ordinal();
        if (pool != Pool.RESIDENT) require(poolResource(pool), phase, limit(pool), used[p], amount);
        if (isHeap(pool)) require(Resource.HEAP, phase, limits.heapBytes(), heapUsed, amount);
        // Commit only after all checks, so failed reservations and growth leave every counter intact.
        used[p] += amount;
        peak[p] = Math.max(peak[p], used[p]);
        if (isHeap(pool)) { heapUsed += amount; heapPeak = Math.max(heapPeak, heapUsed); }
    }

    public synchronized void work(long units, Phase phase) {
        Objects.requireNonNull(phase); nonnegative(units);
        require(Resource.WORK, phase, limits.workUnits(), workUsed, units);
        workUsed += units;
    }

    public synchronized void output(long bytes, Phase phase) {
        Objects.requireNonNull(phase); nonnegative(bytes);
        require(Resource.OUTPUT, phase, limits.outputBytes(), outputUsed, bytes);
        outputUsed += bytes;
    }

    public synchronized long used(Pool pool) { return used[Objects.requireNonNull(pool).ordinal()]; }
    public synchronized long peak(Pool pool) { return peak[Objects.requireNonNull(pool).ordinal()]; }
    public synchronized long heapUsed() { return heapUsed; }
    public synchronized long heapPeak() { return heapPeak; }
    public synchronized long workUsed() { return workUsed; }
    public synchronized long outputUsed() { return outputUsed; }

    private static boolean isHeap(Pool pool) { return pool == Pool.RESIDENT || pool == Pool.SCRATCH; }
    private long limit(Pool pool) {
        return switch (pool) {
            case RESIDENT -> limits.heapBytes();
            case SCRATCH -> limits.scratchBytes();
            case DIRECT -> limits.directBytes();
            case TEMPORARY -> limits.temporaryBytes();
            case OPEN_FILES -> limits.openFiles();
        };
    }
    private static Resource poolResource(Pool pool) {
        return switch (pool) {
            case RESIDENT -> Resource.HEAP;
            case SCRATCH -> Resource.SCRATCH;
            case DIRECT -> Resource.DIRECT;
            case TEMPORARY -> Resource.TEMPORARY;
            case OPEN_FILES -> Resource.OPEN_FILES;
        };
    }
    private static void require(Resource resource, Phase phase, long limit, long current, long additional) {
        // Subtraction avoids overflow even for a Long.MAX_VALUE quota.
        if (additional > limit - current) throw new Exhausted(resource, phase, limit, current, additional);
    }
    private static void nonnegative(long amount) {
        if (amount < 0) throw new IllegalArgumentException("resource amount must be nonnegative");
    }
}
