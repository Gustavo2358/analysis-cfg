package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import io.github.gustavo2358.analysis.solver.PagedLongArray;
import java.util.Objects;

/**
 * Primitive bridge to official AIR storage; no parser or duplicated model vocabulary.
 * All four payload/directory columns share the runtime page cache and resource ledger.
 * Closing retires only these columns. The runtime still owns/finally closes the shared store,
 * particularly if an operational quota prevents complete column traversal during teardown.
 */
public final class PagedAirStorage implements AirSnapshotBuilder.Storage {
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation resident;
    private PagedLongArray[] columns;
    private boolean closed, frozen, failed;

    public PagedAirStorage(PageStore store, AnalysisResources resources, AnalysisResources.Phase phase) {
        Objects.requireNonNull(store); this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase);
        resident = resources.reserve(AnalysisResources.Pool.RESIDENT, 256, phase);
        try {
            columns = new PagedLongArray[AirSnapshotBuilder.Column.values().length];
            for (int n = 0; n < columns.length; n++) columns[n] = new PagedLongArray(store, Long.MAX_VALUE, resources, phase);
        } catch (RuntimeException | Error exception) {
            if (columns != null) for (var column : columns) if (column != null) {
                try { column.close(); } catch (RuntimeException | Error cleanup) {
                    if (exception != cleanup) exception.addSuppressed(cleanup);
                }
            }
            columns = null; resident.close(); throw exception;
        }
    }
    @Override public synchronized long get(AirSnapshotBuilder.Column column, long index) {
        open(); Objects.requireNonNull(column);
        try { return columns[column.ordinal()].get(index); }
        catch (RuntimeException exception) { failed = true; throw exception; }
    }
    @Override public synchronized void set(AirSnapshotBuilder.Column column, long index, long value) {
        writable(); Objects.requireNonNull(column);
        try { columns[column.ordinal()].set(index, value); }
        catch (RuntimeException exception) { failed = true; throw exception; }
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        writable();
        var capacity = resources.reserve(AnalysisResources.Pool.RESIDENT, bytes, phase);
        return capacity::close;
    }
    @Override public synchronized AirSnapshotBuilder.Lease readLease(long bytes) {
        open();
        var capacity = resources.reserve(AnalysisResources.Pool.SCRATCH, bytes, phase);
        return capacity::close;
    }
    @Override public synchronized void freeze() { open(); frozen = true; }
    private void open() { if (closed || failed) throw new IllegalStateException("AIR paged storage is closed or aborted"); }
    private void writable() { open(); if (frozen) throw new IllegalStateException("AIR paged storage is frozen"); }

    @Override public synchronized void close() {
        if (closed) return; closed = true;
        Throwable failure = null;
        for (var column : columns) try { column.close(); } catch (RuntimeException | Error exception) {
            if (failure == null) failure = exception; else if (failure != exception) failure.addSuppressed(exception);
        }
        columns = null; resident.close();
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
