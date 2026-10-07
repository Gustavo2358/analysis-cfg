package io.github.gustavo2358.analysis.solver;

import java.util.Objects;

/**
 * Sparse mutable primitive column with a page-backed radix directory. Missing cells are zero.
 * Both payload and directory spill through the same store; no array/map grows with cardinality.
 * Directory height grows only with the largest written index and is bounded by 63 levels.
 * Per-page nonzero counts reclaim empty leaves/ancestors without scanning their contents.
 * The array owns its pages, not the shared store. Session teardown must close that store even if
 * an operational failure interrupts an array close. Access after close is always rejected.
 */
public final class PagedLongArray implements AutoCloseable {
    private final PageStore store;
    private final long length;
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation resident;
    private final int bits, fanout, mask;
    private byte[] word;
    private long[] teardownPages;
    private int[] teardownNext;
    private long[] pathPages;
    private int[] pathSlots;
    private long root;
    private int height;
    private boolean closed, failed;

    public PagedLongArray(PageStore store, long length, AnalysisResources resources,
                          AnalysisResources.Phase phase) {
        this.store = Objects.requireNonNull(store);
        this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase);
        if (length < 0 || store.pageBytes() < 24)
            throw new IllegalArgumentException("nonnegative length and at least three words per page required");
        this.length = length;
        bits = 31 - Integer.numberOfLeadingZeros(store.pageBytes() / Long.BYTES - 1);
        fanout = 1 << bits; mask = fanout - 1;
        // Fixed control state and teardown stack, including array/object headers and reservation.
        resident = resources.reserve(AnalysisResources.Pool.RESIDENT, 3072, phase);
        word = new byte[Long.BYTES];
        teardownPages = new long[64]; teardownNext = new int[64];
        pathPages = new long[64]; pathSlots = new int[64];
    }

    public long length() { return length; }

    public synchronized long get(long index) {
        check(index);
        try {
            resources.work(1, phase);
            if (root == 0 || requiredHeight(index) > height) return 0;
            long page = root;
            for (int level = height; level > 0; level--) {
                page = readWord(page, digit(index, level) + 1);
                if (page == 0) return 0;
            }
            return readWord(page, digit(index, 0) + 1);
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    public synchronized void set(long index, long value) {
        check(index);
        try { setValue(index, value); }
        catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    private void setValue(long index, long value) {
        resources.work(1, phase);
        int required = requiredHeight(index);
        if (value == 0 && (root == 0 || required > height)) return;
        if (root == 0) { root = store.allocate(); height = required; }
        while (height < required) {
            long replacement = store.allocate();
            writeWord(replacement, 1, root); writeWord(replacement, 0, 1);
            root = replacement; height++;
        }
        long page = root;
        for (int level = height; level > 0; level--) {
            int depth = height - level, slot = digit(index, level) + 1;
            pathPages[depth] = page; pathSlots[depth] = slot;
            long child = readWord(page, slot);
            if (child == 0) {
                if (value == 0) return;
                child = store.allocate(); writeWord(page, slot, child);
                writeWord(page, 0, readWord(page, 0) + 1);
            }
            page = child;
        }
        pathPages[height] = page;
        int slot = digit(index, 0) + 1;
        long previous = readWord(page, slot);
        if (previous == value) return;
        writeWord(page, slot, value);
        if (previous == 0) writeWord(page, 0, readWord(page, 0) + 1);
        else if (value == 0) {
            long remaining = readWord(page, 0) - 1;
            writeWord(page, 0, remaining);
            if (remaining == 0) prune(height);
        }
    }

    private void prune(int depth) {
        while (true) {
            store.release(pathPages[depth]);
            if (depth == 0) { root = 0; height = 0; return; }
            depth--;
            long parent = pathPages[depth];
            writeWord(parent, pathSlots[depth], 0);
            long remaining = readWord(parent, 0) - 1;
            writeWord(parent, 0, remaining);
            if (remaining != 0) break;
        }
        // An all-low single-child prefix no longer distinguishes any live index.
        while (height > 0 && readWord(root, 0) == 1) {
            long child = readWord(root, 1);
            if (child == 0) break;
            store.release(root); root = child; height--;
        }
    }

    private int requiredHeight(long index) {
        int result = 0;
        for (long rest = index >>> bits; rest != 0; rest >>>= bits) result++;
        return result;
    }
    private int digit(long index, int level) { return (int) (index >>> (level * bits)) & mask; }

    private long readWord(long page, int slot) {
        resources.work(1, phase);
        store.read(page, slot * Long.BYTES, word, 0, Long.BYTES);
        long value = 0;
        for (int i = 0; i < Long.BYTES; i++) value |= (word[i] & 255L) << (i * 8);
        return value;
    }
    private void writeWord(long page, int slot, long value) {
        resources.work(1, phase);
        for (int i = 0; i < Long.BYTES; i++) word[i] = (byte) (value >>> (i * 8));
        store.write(page, slot * Long.BYTES, word, 0, Long.BYTES);
    }
    private void check(long index) {
        if (closed || failed) throw new IllegalStateException("primitive array is closed or aborted");
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException("primitive array index " + index);
    }

    @Override public synchronized void close() {
        if (closed) return;
        try {
            if (root == 0) return;
            int depth = 0;
            teardownPages[0] = root; teardownNext[0] = 0;
            while (depth >= 0) {
                if (depth == height || teardownNext[depth] == fanout) {
                    store.release(teardownPages[depth]); depth--;
                } else {
                    long child = readWord(teardownPages[depth], teardownNext[depth]++ + 1);
                    if (child != 0) {
                        depth++; teardownPages[depth] = child; teardownNext[depth] = 0;
                    }
                }
            }
        } finally {
            closed = true; root = 0;
            word = null; teardownPages = null; teardownNext = null;
            pathPages = null; pathSlots = null;
            resident.close();
        }
    }
}
