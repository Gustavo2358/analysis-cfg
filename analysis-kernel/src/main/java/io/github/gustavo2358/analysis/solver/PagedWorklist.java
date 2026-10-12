package io.github.gustavo2358.analysis.solver;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Fair primitive FIFO with exact page-backed pending membership. Removed points may enqueue again;
 * duplicate arrivals coalesce while pending. Queue pages are released on consumption, not kept as
 * history. Both queue and membership directories spill. Any resource/store failure aborts this
 * worklist: subsequent scheduling is rejected and the session must perform teardown.
 */
public final class PagedWorklist implements AutoCloseable {
    private final PageStore store;
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation resident;
    private final PagedLongArray pending;
    private final int wordsPerPage;
    private byte[] word;
    private long head, tail, size;
    private int headSlot = 1, tailSlot = 1;
    private boolean closed, failed;

    public PagedWorklist(PageStore store, AnalysisResources resources, AnalysisResources.Phase phase) {
        this.store = Objects.requireNonNull(store);
        this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase);
        wordsPerPage = store.pageBytes() / Long.BYTES;
        if (wordsPerPage < 2) throw new IllegalArgumentException("work pages need a link and at least one point");
        resident = resources.reserve(AnalysisResources.Pool.RESIDENT, 256, phase);
        try {
            pending = new PagedLongArray(store, 1L << 57, resources, phase);
            word = new byte[Long.BYTES];
        } catch (RuntimeException exception) { resident.close(); throw exception; }
    }

    public synchronized long size() { open(); return size; }

    public synchronized boolean add(long point) {
        open();
        if (point < 0 || point == Long.MAX_VALUE) throw new IndexOutOfBoundsException("work point " + point);
        try {
            long index = point >>> 6, flag = 1L << (point & 63), bits = pending.get(index);
            if ((bits & flag) != 0) return false;
            if (size == Long.MAX_VALUE) throw new IllegalStateException("pending size exceeds signed 64-bit range");
            if (tail == 0) { head = tail = store.allocate(); headSlot = tailSlot = 1; }
            else if (tailSlot == wordsPerPage) {
                long next = store.allocate(); write(tail, 0, next); tail = next; tailSlot = 1;
            }
            write(tail, tailSlot, point);
            pending.set(index, bits | flag);
            tailSlot++; size++;
            return true;
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    public synchronized long remove() {
        open();
        if (size == 0) throw new NoSuchElementException("empty paged worklist");
        try {
            long point = read(head, headSlot);
            long index = point >>> 6, flag = 1L << (point & 63);
            pending.set(index, pending.get(index) & ~flag);
            if (size == 1) {
                store.release(head); head = tail = 0; headSlot = tailSlot = 1;
            } else if (headSlot + 1 == wordsPerPage) {
                long next = read(head, 0);
                if (next == 0) throw new PageStore.Failure(PageStore.Reason.CORRUPT, "missing pending page link");
                store.release(head); head = next; headSlot = 1;
            } else headSlot++;
            size--;
            return point;
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    private long read(long page, int slot) {
        resources.work(1, phase);
        store.read(page, slot * Long.BYTES, word, 0, Long.BYTES);
        long result = 0;
        for (int i = 0; i < Long.BYTES; i++) result |= (word[i] & 255L) << (i * 8);
        return result;
    }
    private void write(long page, int slot, long value) {
        resources.work(1, phase);
        for (int i = 0; i < Long.BYTES; i++) word[i] = (byte) (value >>> (i * 8));
        store.write(page, slot * Long.BYTES, word, 0, Long.BYTES);
    }
    private void open() {
        if (closed || failed) throw new IllegalStateException("worklist is closed or aborted");
    }

    @Override public synchronized void close() {
        if (closed) return;
        RuntimeException failure = null;
        try {
            while (head != 0) {
                long next = read(head, 0);
                store.release(head); head = next;
            }
        } catch (RuntimeException exception) { failure = exception; }
        try { pending.close(); } catch (RuntimeException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        } finally {
            closed = true; head = tail = size = 0; word = null; resident.close();
        }
        if (failure != null) throw failure;
    }
}
