package io.github.gustavo2358.analysis.solver;

import java.util.Objects;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Consumer;
import io.github.gustavo2358.analysis.structure.ProgramStore;

/**
 * Session-owned capacity accounting. Reserve before growing stores, including capacity and scratch,
 * not just live record counts. No I/O, clock, process inspection, implicit GC or semantic cutoff.
 * Calls are thread-safe; reservations are coarse store/page allocations, never per abstract fact.
 */
public final class AnalysisResources {
    public enum Phase { DECODE, VALIDATION, INDEX, DEMAND, CONTROL, DOMAIN, REPLAY, SORT, ENCODE }
    public enum Pool { RESIDENT, SCRATCH, DIRECT, TEMPORARY, OPEN_FILES }
    public enum Resource { HEAP, SCRATCH, DIRECT, TEMPORARY, OPEN_FILES, WORK, OUTPUT, TIME }

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
    private final LongSupplier clock;
    private final Consumer<Phase> executionProgress;
    private final long startedNanos,timeoutNanos;
    private final long[] used = new long[Pool.values().length];
    private final long[] peak = new long[Pool.values().length];
    private long heapUsed, heapPeak, workUsed, outputUsed;
    private long cleanupWorkUsed;
    private boolean cleanupWorkSaturated;

    public AnalysisResources(Limits limits) { this(limits,0,()->0); }
    /** One wall-clock budget shared by decode, validation, analysis and output. */
    public static AnalysisResources withDeadline(Limits limits,Duration timeout) {
        Objects.requireNonNull(timeout);long nanos;
        try{nanos=timeout.toNanos();}catch(ArithmeticException overflow){throw new IllegalArgumentException("analysis timeout exceeds nanosecond range",overflow);}
        if(nanos<=0)throw new IllegalArgumentException("analysis timeout must be positive");
        return new AnalysisResources(limits,nanos,System::nanoTime);
    }
    AnalysisResources(Limits limits,long timeoutNanos,LongSupplier clock) {
        this(limits,timeoutNanos,clock,null);
    }
    private AnalysisResources(Limits limits,long timeoutNanos,LongSupplier clock,Consumer<Phase> executionProgress) {
        this.executionProgress=executionProgress;
        this.limits=Objects.requireNonNull(limits);if(timeoutNanos<0)throw new IllegalArgumentException("analysis timeout must be nonnegative");this.timeoutNanos=timeoutNanos;this.clock=Objects.requireNonNull(clock);startedNanos=clock.getAsLong();
    }
    /** Resident stores retain local capacity accounting, but productive work
     * observes the admitted program's budget even inside symbolic decisions.
     * Their capacities have not thereby joined the managed heap. */
    static AnalysisResources executionFor(ProgramStore program) {
        Objects.requireNonNull(program);
        return new AnalysisResources(new Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE),0,()->0,
            phase->program.progress(switch(phase){
                case DECODE,VALIDATION,INDEX->ProgramStore.ExecutionPhase.INDEX;
                case DEMAND->ProgramStore.ExecutionPhase.DEMAND;
                case CONTROL->ProgramStore.ExecutionPhase.CONTROL;
                case DOMAIN->ProgramStore.ExecutionPhase.DOMAIN;
                case REPLAY->ProgramStore.ExecutionPhase.REPLAY;
                case SORT->ProgramStore.ExecutionPhase.SORT;
                case ENCODE->ProgramStore.ExecutionPhase.ENCODE;
            }));
    }
    public Limits limits() { return limits; }

    public synchronized Reservation reserve(Pool pool, long amount, Phase phase) {
        charge(pool, amount, phase);
        return new Reservation(pool, amount);
    }

    private void charge(Pool pool, long amount, Phase phase) {
        Objects.requireNonNull(pool); Objects.requireNonNull(phase);
        time(phase);
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
        time(phase);
        require(Resource.WORK, phase, limits.workUnits(), workUsed, units);
        workUsed += units;
    }

    /** Work to dismantle already-owned storage, never to compute an analysis
     * result. Keep it observable without requiring remaining analysis quota or
     * allocating another reservation after exhaustion. */
    public synchronized void cleanupWork(long units, Phase phase) {
        Objects.requireNonNull(phase); nonnegative(units);
        if (units > Long.MAX_VALUE-cleanupWorkUsed) {
            cleanupWorkUsed=Long.MAX_VALUE;cleanupWorkSaturated=true;
        } else cleanupWorkUsed+=units;
    }
    public synchronized long cleanupWorkUsed(){return cleanupWorkUsed;}
    public synchronized boolean cleanupWorkSaturated(){return cleanupWorkSaturated;}

    public synchronized void output(long bytes, Phase phase) {
        Objects.requireNonNull(phase); nonnegative(bytes);
        time(phase);
        require(Resource.OUTPUT, phase, limits.outputBytes(), outputUsed, bytes);
        outputUsed += bytes;
    }

    public synchronized long used(Pool pool) { return used[Objects.requireNonNull(pool).ordinal()]; }
    public synchronized long peak(Pool pool) { return peak[Objects.requireNonNull(pool).ordinal()]; }
    public synchronized long heapUsed() { return heapUsed; }
    public synchronized long heapPeak() { return heapPeak; }
    public synchronized long workUsed() { return workUsed; }
    public synchronized long outputUsed() { return outputUsed; }

    private void time(Phase phase) {
        if(executionProgress!=null)executionProgress.accept(phase);
        if(timeoutNanos==0)return;long elapsed=clock.getAsLong()-startedNanos;if(elapsed<0)elapsed=0;
        if(elapsed>=timeoutNanos)throw new Exhausted(Resource.TIME,phase,timeoutNanos,elapsed,0);
    }

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
