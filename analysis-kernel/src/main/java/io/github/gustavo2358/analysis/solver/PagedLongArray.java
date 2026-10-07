package io.github.gustavo2358.analysis.solver;

import java.util.Objects;

/**
 * Sparse mutable primitive column with a page-backed radix directory. Missing cells are zero.
 * Both payload and directory spill through the same store; no array/map grows with cardinality.
 * Directory height grows only with the largest written index and is bounded by 63 levels.
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
    private long root;
    private int height;
    private boolean closed;

    public PagedLongArray(PageStore store, long length, AnalysisResources resources,
                          AnalysisResources.Phase phase) {
        this.store = Objects.requireNonNull(store);
        this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase);
        if (length < 0 || store.pageBytes() < 16)
            throw new IllegalArgumentException("nonnegative length and at least two words per page required");
        this.length = length;
        bits = 31 - Integer.numberOfLeadingZeros(store.pageBytes() / Long.BYTES);
        fanout = 1 << bits; mask = fanout - 1;
        // Fixed control state and teardown stack, including array/object headers and reservation.
        resident = resources.reserve(AnalysisResources.Pool.RESIDENT, 2048, phase);
        word = new byte[Long.BYTES];
        teardownPages = new long[64]; teardownNext = new int[64];
    }

    public long length() { return length; }

    public synchronized long get(long index) {
        check(index);
        resources.work(1, phase);
        if (root == 0 || requiredHeight(index) > height) return 0;
        long page = root;
        for (int level = height; level > 0; level--) {
            page = readWord(page, digit(index, level));
            if (page == 0) return 0;
        }
        return readWord(page, digit(index, 0));
    }

    public synchronized void set(long index, long value) {
        check(index);
        resources.work(1, phase);
        int required = requiredHeight(index);
        if (value == 0 && (root == 0 || required > height)) return;
        if (root == 0) { root = store.allocate(); height = required; }
        while (height < required) {
            long replacement = store.allocate();
            writeWord(replacement, 0, root);
            root = replacement; height++;
        }
        long page = root;
        for (int level = height; level > 0; level--) {
            int slot = digit(index, level);
            long child = readWord(page, slot);
            if (child == 0) {
                if (value == 0) return;
                child = store.allocate(); writeWord(page, slot, child);
            }
            page = child;
        }
        writeWord(page, digit(index, 0), value);
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
        if (closed) throw new IllegalStateException("primitive array is closed");
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
                    long child = readWord(teardownPages[depth], teardownNext[depth]++);
                    if (child != 0) {
                        depth++; teardownPages[depth] = child; teardownNext[depth] = 0;
                    }
                }
            }
        } finally {
            closed = true; root = 0;
            word = null; teardownPages = null; teardownNext = null;
            resident.close();
        }
    }
}
