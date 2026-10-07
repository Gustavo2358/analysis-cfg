package io.github.gustavo2358.analysis.solver;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.LongBinaryOperator;

/**
 * Immutable canonical compressed binary trie over complete signed long keys. One write copies
 * only its search path (at most 64 branches); unchanged subtrees are shared. Root zero is empty.
 * The caller retains live versions in the arena: this view, its lookup cache and old numeric
 * handles do not create roots. Values may be primitive longs or references to immutable records
 * in the same arena; reference values are traced transitively by its collector.
 *
 * The borrowed arena has six fields with references exactly at 2,4,5. Map leaves use kind 1/2
 * (primitive/reference), key at 1 and value at 3/2. Branches use kind 3, bit ordinal at 1 and
 * children at 4/5. Other record kinds may encode domain values/proofs in that arena. Storage
 * owns spill; this view retains only leased fixed path/cache buffers. Closing does not close
 * the borrowed arena/store. It does close cursors and release their retention/scratch leases.
 */
public final class PersistentLongMap implements AutoCloseable {
    private static final long PRIMITIVE = 1, REFERENCE = 2, BRANCH = 3;
    private final CanonicalTupleArena arena;
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation resident;
    private long[] path, tuple, cacheRoots, cacheKeys, cacheLeaves, representativeRoots, representativeKeys;
    private byte[] sides, bits;
    private int depth;
    private long nodeVisits, pathCopies;
    private Cursor cursors;
    private boolean closed, failed;

    public PersistentLongMap(CanonicalTupleArena arena, AnalysisResources resources, AnalysisResources.Phase phase) {
        this.arena = Objects.requireNonNull(arena); this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase);
        if (arena.arity() != 6) throw new IllegalArgumentException("persistent map needs six-field arena");
        for (int n = 0; n < 6; n++) if (arena.referenceColumn(n) != (n == 2 || n == 4 || n == 5))
            throw new IllegalArgumentException("persistent map arena reference schema mismatch");
        resident = resources.reserve(AnalysisResources.Pool.RESIDENT, 4096, phase);
        try {
            path = new long[64]; sides = new byte[64]; bits = new byte[64]; tuple = new long[6];
            cacheRoots = new long[32]; cacheKeys = new long[32]; cacheLeaves = new long[32];
            representativeRoots = new long[32]; representativeKeys = new long[32];
        } catch (RuntimeException | Error exception) { resident.close(); throw exception; }
    }

    public synchronized long nodeVisits() { open(); return nodeVisits; }
    public synchronized long pathCopies() { open(); return pathCopies; }
    public synchronized long size(long root) {
        open();
        try { return root == 0 ? 0 : kind(root) == BRANCH ? arena.field(root, 3) : 1; }
        catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }

    /** Absent and a present zero both return zero; contains distinguishes them. */
    public synchronized long get(long root, long key) {
        open();
        try { long leaf = lookup(root, key); return leaf == 0 ? 0 : value(leaf); }
        catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }
    public synchronized boolean contains(long root, long key) {
        open();
        try { return lookup(root, key) != 0; }
        catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }
    public synchronized boolean reference(long root, long key) {
        open();
        try { long leaf = lookup(root, key); return leaf != 0 && arena.field(leaf, 0) == REFERENCE; }
        catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }
    public synchronized long put(long root, long key, long value) { return put(root, key, value, false); }
    public synchronized long putReference(long root, long key, long value) {
        if (value < 0) throw new IllegalArgumentException("nonnegative arena reference required");
        return put(root, key, value, true);
    }
    private long put(long root, long key, long value, boolean reference) {
        open();
        try {
            long old = descend(root, key);
            long leaf = leaf(key, value, reference);
            if (old == 0) return leaf;
            long previousKey = arena.field(old, 1);
            if (previousKey == key) return leaf == old ? root : rebuild(leaf, depth);
            int bit = Long.numberOfLeadingZeros(previousKey ^ key), cut = 0;
            while (cut < depth && bits[cut] < bit) cut++;
            long previous = cut == depth ? old : path[cut];
            long merged = direction(key, bit) == 0 ? branch(bit, leaf, previous) : branch(bit, previous, leaf);
            return rebuild(merged, cut);
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }
    public synchronized long remove(long root, long key) {
        open();
        try {
            long leaf = descend(root, key);
            if (leaf == 0 || arena.field(leaf, 1) != key) return root;
            if (depth == 0) return 0;
            long sibling = arena.field(path[depth - 1], sides[depth - 1] == 0 ? 5 : 4);
            return rebuild(sibling, depth - 1);
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }
    private long rebuild(long replacement, int count) {
        while (count != 0) {
            int n = --count;
            long sibling = arena.field(path[n], sides[n] == 0 ? 5 : 4);
            replacement = sides[n] == 0 ? branch(bits[n], replacement, sibling) : branch(bits[n], sibling, replacement);
            pathCopies = Math.addExact(pathCopies, 1);
        }
        return replacement;
    }
    private long leaf(long key, long value, boolean reference) {
        tuple[0] = reference ? REFERENCE : PRIMITIVE; tuple[1] = key;
        tuple[2] = reference ? value : 0; tuple[3] = reference ? 0 : value; tuple[4] = 0; tuple[5] = 0;
        return arena.intern(tuple);
    }
    private long branch(int bit, long left, long right) {
        long count = Math.addExact(size(left), size(right));
        tuple[0] = BRANCH; tuple[1] = bit; tuple[2] = 0; tuple[3] = count; tuple[4] = left; tuple[5] = right;
        return arena.intern(tuple);
    }
    /**
     * Pointwise union for an associative, commutative, idempotent value join. Equal subtree roots
     * are already equal facts and are skipped. Unmatched entries preserve their original kinds;
     * collisions must both match referenceValues. The callback may reenter map operations: each
     * active join owns an independent leased fixed iterative stack. No whole-state flattening.
     */
    public synchronized long join(long left, long right, boolean referenceValues, LongBinaryOperator values) {
        open(); Objects.requireNonNull(values);
        if (left < 0 || right < 0) throw new IllegalArgumentException("nonnegative arena roots required");
        try {
            if (left != 0) kind(left); if (right != 0 && right != left) kind(right);
            if (left == right || right == 0) return left;
            if (left == 0) return right;
            try (var scratch = resources.reserve(AnalysisResources.Pool.SCRATCH, 4096, phase)) {
                if (scratch.amount() != 4096) throw new IllegalStateException("join scratch reservation mismatch");
                long[] a = new long[65], b = new long[65], first = new long[65], other = new long[65];
                byte[] stage = new byte[65], mode = new byte[65], split = new byte[65];
                a[0] = left; b[0] = right; int top = 0; long result = 0;
                while (top >= 0) {
                    if (stage[top] == 0) {
                        long x = a[top], y = b[top];
                        if (x == y || y == 0) { result = x; top--; continue; }
                        if (x == 0) { result = y; top--; continue; }
                        long xKind = kind(x), yKind = kind(y);
                        int xBit = xKind == BRANCH ? bit(x, -1) : 64, yBit = yKind == BRANCH ? bit(y, -1) : 64;
                        long xKey = representative(x), yKey = representative(y);
                        int different = Long.numberOfLeadingZeros(xKey ^ yKey);
                        if (different < Math.min(xBit, yBit)) {
                            result = direction(xKey, different) == 0 ? branch(different, x, y) : branch(different, y, x);
                            top--; continue;
                        }
                        if (xBit == 64 && yBit == 64) {
                            long expected = referenceValues ? REFERENCE : PRIMITIVE;
                            if (xKind != expected || yKind != expected) throw new IllegalArgumentException("collision value kind mismatch");
                            result = leaf(xKey, values.applyAsLong(value(x), value(y)), referenceValues); top--; continue;
                        }
                        if (top == 64) throw new IllegalArgumentException("join path exceeds key width");
                        split[top] = (byte) Math.min(xBit, yBit); stage[top] = 1;
                        if (xBit == yBit) {
                            mode[top] = 0; a[top + 1] = arena.field(x, 4); b[top + 1] = arena.field(y, 4);
                        } else if (xBit < yBit) {
                            byte side = direction(yKey, xBit); mode[top] = (byte) (side + 1);
                            other[top] = arena.field(x, side == 0 ? 5 : 4);
                            a[top + 1] = arena.field(x, side == 0 ? 4 : 5); b[top + 1] = y;
                        } else {
                            byte side = direction(xKey, yBit); mode[top] = (byte) (side + 1);
                            other[top] = arena.field(y, side == 0 ? 5 : 4);
                            a[top + 1] = x; b[top + 1] = arena.field(y, side == 0 ? 4 : 5);
                        }
                        stage[++top] = 0;
                    } else if (stage[top] == 1 && mode[top] == 0) {
                        first[top] = result; stage[top] = 2;
                        a[top + 1] = arena.field(a[top], 5); b[top + 1] = arena.field(b[top], 5); stage[++top] = 0;
                    } else {
                        result = mode[top] == 0 ? branch(split[top], first[top], result)
                            : mode[top] == 1 ? branch(split[top], result, other[top]) : branch(split[top], other[top], result);
                        top--;
                    }
                }
                return result;
            }
        } catch (AnalysisResources.Exhausted | PageStore.Failure exception) { failed = true; throw exception; }
    }
    private long representative(long root) {
        int slot = (int) (root ^ (root >>> 32)) & 31;
        if (representativeRoots[slot] == root) { kind(root); return representativeKeys[slot]; }
        long node = root; int previous = -1;
        while (kind(node) == BRANCH) { previous = bit(node, previous); node = arena.field(node, 4); }
        long key = arena.field(node, 1); representativeRoots[slot] = root; representativeKeys[slot] = key; return key;
    }
    private static byte direction(long key, int bit) { return (byte) (((key ^ Long.MIN_VALUE) >>> (63 - bit)) & 1); }
    private long kind(long node) {
        nodeVisits = Math.addExact(nodeVisits, 1);
        long kind = arena.field(node, 0);
        if (kind != PRIMITIVE && kind != REFERENCE && kind != BRANCH)
            throw new IllegalArgumentException("not a persistent map node");
        return kind;
    }
    private int bit(long node, int previous) {
        long bit = arena.field(node, 1);
        if (bit <= previous || bit > 63 || arena.field(node, 4) == 0 || arena.field(node, 5) == 0)
            throw new IllegalArgumentException("invalid persistent map branch");
        return (int) bit;
    }
    private long descend(long root, long key) {
        if (root < 0) throw new IllegalArgumentException("nonnegative arena root required");
        depth = 0; long node = root; int previous = -1;
        while (node != 0) {
            if (kind(node) != BRANCH) return node;
            int bit = bit(node, previous); previous = bit;
            if (depth == 64) throw new IllegalArgumentException("persistent map path exceeds key width");
            path[depth] = node; sides[depth] = direction(key, bit); bits[depth++] = (byte) bit;
            node = arena.field(node, direction(key, bit) == 0 ? 4 : 5);
        }
        return 0;
    }
    private long lookup(long root, long key) {
        if (root == 0) return 0;
        // A cache hit validates the root's liveness; stale numeric handles cannot resurrect facts.
        if (root < 0) throw new IllegalArgumentException("nonnegative arena root required");
        long hash = root * 0x9e3779b97f4a7c15L + key; hash ^= hash >>> 33;
        int slot = (int) hash & 31;
        if (cacheRoots[slot] == root && cacheKeys[slot] == key) { kind(root); return cacheLeaves[slot]; }
        long leaf = descend(root, key);
        if (leaf != 0 && arena.field(leaf, 1) != key) leaf = 0;
        cacheRoots[slot] = root; cacheKeys[slot] = key; cacheLeaves[slot] = leaf; return leaf;
    }
    private long value(long leaf) { return arena.field(leaf, arena.field(leaf, 0) == REFERENCE ? 2 : 3); }

    /** Ordered signed-key traversal. Its lease retains the immutable root through collection. */
    public synchronized Cursor cursor(long root) {
        open();
        if (root < 0) throw new IllegalArgumentException("nonnegative arena root required");
        AnalysisResources.Reservation scratch = resources.reserve(AnalysisResources.Pool.SCRATCH, root == 0 ? 256 : 2048, phase);
        long token = 0;
        try {
            if (root != 0) { kind(root); token = arena.retain(root); }
            var cursor = new Cursor(this, root, token, scratch);
            cursor.next = cursors; if (cursors != null) cursors.previous = cursor; cursors = cursor;
            return cursor;
        } catch (RuntimeException | Error exception) {
            if (token != 0) try { arena.release(token); } catch (RuntimeException | Error cleanup) { if (cleanup != exception) exception.addSuppressed(cleanup); }
            scratch.close(); throw exception;
        }
    }
    public static final class Cursor implements AutoCloseable {
        private volatile PersistentLongMap owner;
        private AnalysisResources.Reservation scratch;
        private long[] rights;
        private byte[] rightBits;
        private long pending, current, token;
        private int depth, previousBit = -1;
        private boolean exhausted;
        private Cursor previous, next;
        private Cursor(PersistentLongMap owner, long root, long token, AnalysisResources.Reservation scratch) {
            this.owner = owner; pending = root; this.token = token; this.scratch = scratch;
            if (root != 0) { rights = new long[64]; rightBits = new byte[64]; }
        }
        public boolean advance() {
            PersistentLongMap map = owner;
            if (map == null) { if (exhausted) return false; throw new IllegalStateException("map cursor closed"); }
            synchronized (map) {
                requireOwner(map); map.open(); current = 0;
                try {
                    if (pending == 0 && depth != 0) { pending = rights[--depth]; previousBit = rightBits[depth]; }
                    while (pending != 0) {
                        long node = pending; pending = 0;
                        if (map.kind(node) != BRANCH) { current = node; return true; }
                        int bit = map.bit(node, previousBit); previousBit = bit;
                        if (depth == 64) throw new IllegalArgumentException("map cursor path exceeds key width");
                        rights[depth] = map.arena.field(node, 5); rightBits[depth++] = (byte) bit;
                        pending = map.arena.field(node, 4);
                    }
                    exhausted = true; close(); return false;
                } catch (RuntimeException | Error exception) {
                    try { close(); } catch (RuntimeException | Error cleanup) { if (cleanup != exception) exception.addSuppressed(cleanup); }
                    throw exception;
                }
            }
        }
        public long key() { return field(1); }
        public long value() {
            PersistentLongMap map = owner; if (map == null) return missing();
            synchronized (map) { requireCurrent(map); return map.value(current); }
        }
        public boolean reference() { return field(0) == REFERENCE; }
        private long field(int field) {
            PersistentLongMap map = owner; if (map == null) return missing();
            synchronized (map) { requireCurrent(map); return map.arena.field(current, field); }
        }
        private long missing() {
            if (exhausted) throw new NoSuchElementException("no current map entry");
            throw new IllegalStateException("map cursor closed");
        }
        private void requireOwner(PersistentLongMap map) { if (owner != map) throw new IllegalStateException("map cursor closed"); }
        private void requireCurrent(PersistentLongMap map) {
            requireOwner(map); map.open(); if (current == 0) throw new NoSuchElementException("no current map entry");
        }
        @Override public void close() {
            PersistentLongMap map = owner; if (map == null) return;
            synchronized (map) {
                if (owner == null) return;
                if (previous == null) map.cursors = next; else previous.next = next;
                if (next != null) next.previous = previous;
                long retained = token; var capacity = scratch;
                owner = null; token = 0; scratch = null; rights = null; rightBits = null;
                previous = null; next = null; pending = 0; current = 0;
                try { if (retained != 0) map.arena.release(retained); } finally { capacity.close(); }
            }
        }
    }
    private void open() { if (closed || failed) throw new IllegalStateException("persistent map closed or aborted"); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; Throwable failure = null;
        while (cursors != null) try { cursors.close(); } catch (RuntimeException | Error exception) {
            if (failure == null) failure = exception; else if (failure != exception) failure.addSuppressed(exception);
        }
        path = null; tuple = null; cacheRoots = null; cacheKeys = null; cacheLeaves = null;
        representativeRoots = null; representativeKeys = null; sides = null; bits = null;
        resident.close();
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
