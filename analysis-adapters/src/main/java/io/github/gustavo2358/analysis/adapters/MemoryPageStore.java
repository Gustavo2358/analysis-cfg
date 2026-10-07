package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.util.Arrays;
import java.util.Objects;

/**
 * Explicit resident backend of the exact page port. Capacity is quota-limited; it is not a
 * bounded-working-set backend. Primitive slot/generation metadata grows with a reservation for
 * the old-plus-new peak. Freed payload arrays are reused and stay charged until store close.
 */
public final class MemoryPageStore implements PageStore {
    private static final long SLOT_MASK = 0xffffffffL;
    private final int pageBytes;
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private AnalysisResources.Reservation metadata, payload;
    private byte[][] pages;
    private long[] generations;
    private int[] freeNext;
    private boolean[] live;
    private int physical, freeHead = -1;
    private long issued, liveCount, hits, bytesRead, bytesWritten;
    private boolean closed;

    public MemoryPageStore(int pageBytes, AnalysisResources resources) {
        this(pageBytes, resources, AnalysisResources.Phase.DOMAIN);
    }
    public MemoryPageStore(int pageBytes, AnalysisResources resources, AnalysisResources.Phase phase) {
        if (pageBytes <= 0) throw new IllegalArgumentException("positive page size required");
        this.pageBytes = pageBytes; this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase);
        metadata = resources.reserve(AnalysisResources.Pool.RESIDENT, capacity(4), phase);
        try {
            payload = resources.reserve(AnalysisResources.Pool.RESIDENT, 0, phase);
            pages = new byte[4][]; generations = new long[4]; freeNext = new int[4]; live = new boolean[4];
        } catch (RuntimeException | Error exception) {
            metadata.close(); if (payload != null) payload.close(); throw exception;
        }
    }

    @Override public int pageBytes() { return pageBytes; }
    @Override public synchronized long allocate() {
        open();
        if (issued == Long.MAX_VALUE) throw new Failure(Reason.INVALID_HANDLE, "page handle space exhausted");
        resources.work(1, phase);
        int slot;
        if (freeHead >= 0) {
            slot = freeHead; freeHead = freeNext[slot]; generations[slot]++;
            Arrays.fill(pages[slot], (byte) 0);
        } else {
            try (var staged = resources.reserve(AnalysisResources.Pool.RESIDENT, (long) pageBytes + 64, phase)) {
                if (physical == pages.length) grow();
                slot = physical; pages[slot] = new byte[pageBytes];
                payload.absorb(staged); physical++;
            }
        }
        live[slot] = true; issued++; liveCount++;
        return (generations[slot] << 32) | ((long) slot + 1);
    }

    @Override public synchronized void read(long page, int offset, byte[] target, int targetOffset, int length) {
        open();
        Objects.checkFromIndexSize(offset, length, pageBytes);
        Objects.checkFromIndexSize(targetOffset, length, Objects.requireNonNull(target).length);
        int slot = slot(page); resources.work(1, phase);
        System.arraycopy(pages[slot], offset, target, targetOffset, length); hits++; bytesRead += length;
    }
    @Override public synchronized void write(long page, int offset, byte[] source, int sourceOffset, int length) {
        open();
        Objects.checkFromIndexSize(offset, length, pageBytes);
        Objects.checkFromIndexSize(sourceOffset, length, Objects.requireNonNull(source).length);
        int slot = slot(page); resources.work(1, phase);
        System.arraycopy(source, sourceOffset, pages[slot], offset, length); hits++; bytesWritten += length;
    }
    @Override public synchronized void release(long page) {
        open();
        int slot = slot(page); resources.work(1, phase);
        live[slot] = false; liveCount--;
        if (generations[slot] != Integer.MAX_VALUE) {
            freeNext[slot] = freeHead; freeHead = slot;
        } // Exhausted generations are retired, never wrapped into an old handle.
    }
    @Override public synchronized void flush() { open(); }
    @Override public synchronized Statistics statistics() {
        return new Statistics(issued, liveCount, hits, 0, 0, bytesRead, bytesWritten);
    }

    private int slot(long page) {
        long address = page & SLOT_MASK;
        if (page <= 0 || address == 0 || address > physical)
            throw new Failure(Reason.INVALID_HANDLE, "unknown page address");
        int slot = (int) (address - 1);
        if (!live[slot] || generations[slot] != (page >>> 32))
            throw new Failure(Reason.INVALID_HANDLE, "released or obsolete page generation");
        return slot;
    }
    private static long capacity(int slots) { return 256L + (long) slots * 32; }
    private void grow() {
        if (pages.length > (Integer.MAX_VALUE - 8) / 2)
            throw new Failure(Reason.INVALID_HANDLE, "resident slot array representability exhausted");
        int slots = pages.length * 2;
        AnalysisResources.Reservation replacement = resources.reserve(AnalysisResources.Pool.RESIDENT, capacity(slots), phase);
        try {
            byte[][] newPages = Arrays.copyOf(pages, slots);
            long[] newGenerations = Arrays.copyOf(generations, slots);
            int[] newFreeNext = Arrays.copyOf(freeNext, slots);
            boolean[] newLive = Arrays.copyOf(live, slots);
            pages = newPages; generations = newGenerations; freeNext = newFreeNext; live = newLive;
        } catch (RuntimeException | Error exception) { replacement.close(); throw exception; }
        metadata.close(); metadata = replacement;
    }
    private void open() { if (closed) throw new Failure(Reason.CLOSED, "page store is closed"); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        pages = null; generations = null; freeNext = null; live = null;
        metadata.close(); payload.close();
    }
}
