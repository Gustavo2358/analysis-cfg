package io.github.gustavo2358.analysis.solver;

import java.util.Arrays;
import java.util.Objects;

/**
 * Session-local immutable fixed-shape primitive records, canonicalized by the complete tuple.
 * Payload, unique table, root tokens and collection marks spill; only fixed control state remains
 * resident. Reference columns are part of the key and point to existing records in this arena.
 * Thus records form a DAG, not a mutable summary graph. Callers encode domain/profile/operation
 * identity in the tuple or bind a separate arena to that identity. Hashes never establish equality.
 *
 * Handles and root tokens are monotonically issued and never reused. Unrooted records may expire
 * at an explicit collection. Every active caller, queue, cache dependency or observation that must
 * keep a record alive must retain a root token (or be reachable through a retained record). Collection
 * traces all those roots before retirement. This is storage lifetime, not domain subsumption: it
 * does not prove that an earlier abstract-state version can be dropped.
 *
 * The arena owns its tables, not the supplied store. An operational failure aborts access; the
 * session must close the shared store even if the exhausted quota interrupts arena teardown.
 */
public final class CanonicalTupleArena implements AutoCloseable {
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation resident;
    private final PageStore store;
    private final int arity;
    private final long width, maximumHandle;
    private int[] references;
    private long[] staged;
    private PagedLongArray rows;
    private PagedLongIndex unique, roots;
    private long issued, rootTokens, epoch;
    private boolean closed, failed;

    public CanonicalTupleArena(PageStore store, AnalysisResources resources, AnalysisResources.Phase phase,
                               int arity, int[] referenceColumns) {
        this.store = Objects.requireNonNull(store); this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase); Objects.requireNonNull(referenceColumns);
        if (arity <= 0) throw new IllegalArgumentException("tuple arity must be positive");
        for (int column : referenceColumns) if (column < 0 || column >= arity)
            throw new IllegalArgumentException("reference column outside tuple");
        this.arity = arity; width = (long) arity + 2; maximumHandle = Long.MAX_VALUE / width;
        resident = resources.reserve(AnalysisResources.Pool.RESIDENT,
                384 + (long) arity * Long.BYTES + (long) referenceColumns.length * Integer.BYTES, phase);
        try {
            references = referenceColumns.clone(); Arrays.sort(references);
            for (int n = 1; n < references.length; n++) if (references[n - 1] == references[n])
                throw new IllegalArgumentException("duplicate reference column");
            staged = new long[arity];
            rows = new PagedLongArray(store, Long.MAX_VALUE, resources, phase);
            unique = new PagedLongIndex(store, resources, phase, this::compare);
            roots = new PagedLongIndex(store, resources, phase);
        } catch (RuntimeException | Error exception) {
            closeSuppressed(roots, exception); closeSuppressed(unique, exception); closeSuppressed(rows, exception);
            resident.close(); throw exception;
        }
    }

    public synchronized long size() { open(); return unique.size(); }
    public synchronized int arity() { open(); return arity; }
    public synchronized boolean referenceColumn(int column) {
        open();
        if (column < 0 || column >= arity) throw new IndexOutOfBoundsException("tuple column " + column);
        return Arrays.binarySearch(references, column) >= 0;
    }

    /** Callers own the supplied buffer; the arena copies only the fixed staging key. */
    public synchronized long intern(long... tuple) {
        open(); Objects.requireNonNull(tuple);
        if (tuple.length != arity) throw new IllegalArgumentException("wrong tuple arity");
        try {
            System.arraycopy(tuple, 0, staged, 0, arity);
            for (int column : references) if (staged[column] != 0) requireHandle(staged[column]);
            long found = unique.find(0);
            if (found != 0) return found;
            if (issued == maximumHandle) throw new IllegalStateException("arena identity space exhausted");
            long handle = ++issued, base = base(handle);
            rows.set(base, 1);
            for (int column = 0; column < arity; column++) rows.set(base + 2 + column, staged[column]);
            if (unique.intern(handle, handle) != handle) throw new IllegalStateException("canonical arena disagreement");
            return handle;
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    public synchronized long field(long handle, int column) {
        open();
        if (column < 0 || column >= arity) throw new IndexOutOfBoundsException("tuple column " + column);
        try { requireHandle(handle); return rows.get(base(handle) + 2 + column); }
        catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    /** A distinct token per retention; releasing one caller cannot release another caller's root. */
    public synchronized long retain(long handle) {
        open();
        try {
            requireHandle(handle);
            if (rootTokens == Long.MAX_VALUE) throw new IllegalStateException("root token space exhausted");
            long token = ++rootTokens;
            roots.intern(token, handle); return token;
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    public synchronized void release(long token) {
        open();
        try {
            if (token <= 0 || roots.find(token) == 0) throw new IllegalArgumentException("unknown or released root token");
            if (!roots.remove(token)) throw new IllegalStateException("root registry disagreement");
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    /** Trace current roots/edges, then sweep current live tuples; cost is independent of issued history. */
    public synchronized long collect() {
        open();
        if (epoch == Long.MAX_VALUE) { failed = true; throw new IllegalStateException("collection epoch space exhausted"); }
        epoch++;
        try (var pending = new PagedWorklist(store, resources, phase)) {
            try (var cursor = roots.cursor()) { while (cursor.advance()) mark(cursor.value(), pending); }
            while (pending.size() != 0) {
                long handle = pending.remove(), base = base(handle);
                for (int column : references) {
                    long child = rows.get(base + 2 + column);
                    if (child != 0) mark(child, pending);
                }
            }
            // Do not mutate the B-tree during its ordered traversal. The retirement queue spills.
            try (var cursor = unique.cursor()) {
                while (cursor.advance()) if (rows.get(base(cursor.value()) + 1) != epoch)
                    pending.add(cursor.value());
            }
            long retired = 0;
            while (pending.size() != 0) {
                long handle = pending.remove(), base = base(handle);
                if (!unique.remove(handle)) throw new IllegalStateException("arena unique table disagreement");
                for (long slot = 0; slot < width; slot++) rows.set(base + slot, 0);
                retired++;
            }
            return retired;
        } catch (RuntimeException exception) { failed = true; throw exception; }
    }

    private void mark(long handle, PagedWorklist pending) {
        long slot = base(handle) + 1;
        if (rows.get(slot) == epoch) return;
        rows.set(slot, epoch); pending.add(handle);
    }
    private int compare(long first, long second) {
        if (first == second) return 0;
        long firstBase = first == 0 ? 0 : base(first) + 2;
        long secondBase = second == 0 ? 0 : base(second) + 2;
        for (int column = 0; column < arity; column++) {
            long a = first == 0 ? staged[column] : rows.get(firstBase + column);
            long b = second == 0 ? staged[column] : rows.get(secondBase + column);
            int order = Long.compare(a, b);
            if (order != 0) return order;
        }
        return 0;
    }
    private long base(long handle) { return (handle - 1) * width; }
    private void requireHandle(long handle) {
        if (handle <= 0 || handle > issued || rows.get(base(handle)) == 0)
            throw new IllegalArgumentException("unknown or retired arena handle");
    }
    private void open() { if (closed || failed) throw new IllegalStateException("canonical arena is closed or aborted"); }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        try { roots.close(); } catch (RuntimeException exception) { failure = exception; }
        try { unique.close(); } catch (RuntimeException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        try { rows.close(); } catch (RuntimeException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        roots = null; unique = null; rows = null; references = null; staged = null; resident.close();
        if (failure != null) throw failure;
    }
    private static void closeSuppressed(AutoCloseable owner, Throwable failure) {
        if (owner == null) return;
        try { owner.close(); } catch (Exception exception) { failure.addSuppressed(exception); }
    }
}
