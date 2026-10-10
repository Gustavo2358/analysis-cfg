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
        private final ProgramStore.CapacityLease backing;
        private long amount;
        private boolean closed;
        private Reservation(Pool pool, long amount,ProgramStore.CapacityLease backing) { this.pool = pool; this.amount = amount;this.backing=backing; }
        public long amount() { synchronized (AnalysisResources.this) { return amount; } }
        /** Register cleanup on native owner expiry; explicit close unregisters it.
         * Resident compatibility has no native expiry and still requires explicit close. */
        public void onOwnerClose(Runnable cleanup) {
            synchronized(AnalysisResources.this){if(closed)throw new IllegalStateException("reservation is closed");Objects.requireNonNull(cleanup);if(backing!=null)backing.onOwnerClose(cleanup);}
        }
        private AnalysisResources owner() { return AnalysisResources.this; }
        /** Commit staged capacity into this owner without double-charging or releasing live bytes. */
        public void absorb(Reservation staged) {
            Objects.requireNonNull(staged);
            synchronized (AnalysisResources.this) {
                if (staged == this || staged.owner() != AnalysisResources.this || staged.pool != pool)
                    throw new IllegalArgumentException("capacity transfer requires distinct reservations of the same owner and pool");
                if (closed || staged.closed) throw new IllegalStateException("reservation is closed");
                if(backing!=null)backing.absorb(staged.backing);
                amount += staged.amount; // Both capacities are already in the same bounded pool total.
                staged.amount = 0; staged.closed = true;
            }
        }
        public void grow(long additional, Phase phase) {
            synchronized (AnalysisResources.this) {
                if (closed) throw new IllegalStateException("reservation is closed");
                preflight(pool, additional, phase);
                if(backing!=null)backing.grow(additional,executionPhase(phase));
                commit(pool,additional);
                amount += additional; // charge proves the pool total and hence this amount fit in long.
            }
        }
        @Override public void close() {
            synchronized (AnalysisResources.this) {
                if (closed) return;
                if(backing!=null)backing.close();
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
    private final ProgramStore.ExecutionBudget executionBudget;
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
        this(limits,timeoutNanos,clock,null,null);
    }
    private AnalysisResources(Limits limits,long timeoutNanos,LongSupplier clock,Consumer<Phase> executionProgress,ProgramStore.ExecutionBudget executionBudget) {
        this.executionBudget=executionBudget;
        this.executionProgress=executionProgress;
        this.limits=Objects.requireNonNull(limits);if(timeoutNanos<0)throw new IllegalArgumentException("analysis timeout must be nonnegative");this.timeoutNanos=timeoutNanos;this.clock=Objects.requireNonNull(clock);startedNanos=clock.getAsLong();
    }
    /** An execution view records its own capacities while native reservations
     * borrow the admitted common ledger. Resident compatibility stays explicit.
     * This does not fund stores that never request capacity from the view. */
    public static AnalysisResources executionFor(ProgramStore program) {
        Objects.requireNonNull(program);
        return new AnalysisResources(new Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE),0,()->0,
            phase->program.progress(executionPhase(phase)),program.executionBudget().orElse(null));
    }
    private static ProgramStore.ExecutionPhase executionPhase(Phase phase) {
        return switch(phase){
                case DECODE,VALIDATION,INDEX->ProgramStore.ExecutionPhase.INDEX;
                case DEMAND->ProgramStore.ExecutionPhase.DEMAND;
                case CONTROL->ProgramStore.ExecutionPhase.CONTROL;
                case DOMAIN->ProgramStore.ExecutionPhase.DOMAIN;
                case REPLAY->ProgramStore.ExecutionPhase.REPLAY;
                case SORT->ProgramStore.ExecutionPhase.SORT;
                case ENCODE->ProgramStore.ExecutionPhase.ENCODE;
            };
    }
    public Limits limits() { return limits; }

    public synchronized Reservation reserve(Pool pool, long amount, Phase phase) {
        preflight(pool, amount, phase);
        var backing=executionBudget==null?null:executionBudget.reserve(ProgramStore.CapacityPool.valueOf(pool.name()),amount,executionPhase(phase));
        try {
            var reservation=new Reservation(pool,amount,backing);
            commit(pool,amount);return reservation;
        }catch(RuntimeException|Error failure){if(backing!=null)backing.close();throw failure;}
    }

    private void preflight(Pool pool, long amount, Phase phase) {
        Objects.requireNonNull(pool); Objects.requireNonNull(phase);
        time(phase);
        nonnegative(amount);
        int p = pool.ordinal();
        if (pool != Pool.RESIDENT) require(poolResource(pool), phase, limit(pool), used[p], amount);
        if (isHeap(pool)) require(Resource.HEAP, phase, limits.heapBytes(), heapUsed, amount);
    }
    private void commit(Pool pool,long amount) {
        int p=pool.ordinal();
        // Commit only after local and common checks; rejected capacity changes no counters/peaks.
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
    static void closeCapacities(Reservation first,Reservation second) {
        Throwable failure=null;
        try{if(first!=null)first.close();}catch(RuntimeException|Error error){failure=error;}
        try{if(second!=null)second.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
