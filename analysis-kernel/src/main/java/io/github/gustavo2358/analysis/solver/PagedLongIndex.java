package io.github.gustavo2358.analysis.solver;

import java.util.Objects;

/**
 * Exact canonical key index with page-backed B-tree nodes and a fixed resident control state.
 * Values are positive handles; zero means absent. Signed primitive keys use natural order by
 * default. A custom order compares complete immutable keys behind stable handles, never just
 * hashes. Only comparison equality unifies keys. Transfers, unique tables and retirement can use
 * this required state without treating it as an evictable memo cache. No per-key heap objects.
 * Operational/callback failure aborts the index; the shared store must close after teardown.
 */
public final class PagedLongIndex implements AutoCloseable {
    @FunctionalInterface public interface Order { int compare(long first, long second); }
    private final PageStore store;
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation resident;
    private final Order order;
    private final PagedLongArray ownedPages;
    private final int degree, maximum, pairBase;
    private byte[] word;
    private long[] teardownPages;
    private int[] teardownNext;
    private int[] teardownLimits;
    private long root, size, version, provisional;
    private Cursor cursors;
    private boolean closed, failed;

    public PagedLongIndex(PageStore store, AnalysisResources resources, AnalysisResources.Phase phase) {
        this(store, resources, phase, Long::compare);
    }
    public PagedLongIndex(PageStore store, AnalysisResources resources, AnalysisResources.Phase phase, Order order) {
        this.store = Objects.requireNonNull(store); this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase); this.order = Objects.requireNonNull(order);
        degree = store.pageBytes() / Long.BYTES / 6;
        if (degree < 2) throw new IllegalArgumentException("index pages need at least 96 bytes");
        maximum = degree * 2 - 1; pairBase = 2 + degree * 2;
        resident = resources.reserve(AnalysisResources.Pool.RESIDENT, 2048, phase);
        try {
            ownedPages=new PagedLongArray(store,Long.MAX_VALUE,resources,phase);
            word = new byte[Long.BYTES]; teardownPages = new long[64]; teardownNext = new int[64];
            teardownLimits = new int[64];
        }catch(RuntimeException|Error failure){resident.close();throw failure;}
    }

    public synchronized long size() { open(); return size; }

    /** Exact ordered view; mutation attempts invalidate it, and owner close releases its scratch. */
    public synchronized Cursor cursor() {
        open();
        Cursor cursor = new Cursor(this);
        cursor.next = cursors;
        if (cursors != null) cursors.previous = cursor;
        cursors = cursor;
        return cursor;
    }

    public synchronized long find(long key) {
        open();
        try {
            long page = root;
            while (page != 0) {
                int at = search(page, count(page), key);
                if (at < 0) return value(page, ~at);
                if (leaf(page)) return 0;
                page = child(page, at);
            }
            return 0;
        } catch (RuntimeException exception) { failed = true; throw exception; }
    }

    /** Return the prior canonical value, or insert/return the supplied positive value. */
    public synchronized long intern(long key, long value) {
        open();
        if (value <= 0) throw new IllegalArgumentException("canonical values must be positive handles");
        mutate();
        try {
            if (root == 0) root = node(true);
            if (count(root) == maximum) {
                int found = search(root, maximum, key);
                if (found < 0) return value(root, ~found);
                long replacement = node(false); child(replacement, 0, root);
                split(replacement, 0); root = replacement;
            }
            long page = root;
            while (true) {
                int n = count(page), at = search(page, n, key);
                if (at < 0) return value(page, ~at);
                if (leaf(page)) {
                    if (size == Long.MAX_VALUE) throw new IllegalStateException("index size exceeds signed 64-bit range");
                    for (int i = n; i > at; i--) copy(page, i - 1, page, i);
                    pair(page, at, key, value); count(page, n + 1); size++;
                    return value;
                }
                long next = child(page, at);
                if (count(next) == maximum) {
                    split(page, at);
                    int comparison = compare(key, key(page, at));
                    if (comparison == 0) return value(page, at);
                    if (comparison > 0) at++;
                    next = child(page, at);
                }
                page = next;
            }
        } catch (RuntimeException exception) { failed = true; throw exception; }
    }

    public synchronized boolean remove(long key) {
        open();
        mutate();
        try {
            boolean removed = delete(key);
            if (root != 0 && count(root) == 0) {
                long previous = root;
                root = leaf(previous) ? 0 : child(previous, 0);
                release(previous);
            }
            return removed;
        } catch (RuntimeException exception) { failed = true; throw exception; }
    }

    private boolean delete(long sought) {
        long page = root, key = sought;
        while (page != 0) {
            int n = count(page), found = search(page, n, key), at = found < 0 ? ~found : found;
            boolean isLeaf = leaf(page);
            if (found < 0) {
                if (isLeaf) {
                    for (int i = at; i + 1 < n; i++) copy(page, i + 1, page, i);
                    count(page, n - 1); size--; return true;
                }
                long left = child(page, at), right = child(page, at + 1);
                boolean predecessor = count(left) >= degree;
                if (predecessor || count(right) >= degree) {
                    long next = predecessor ? left : right, candidate = next;
                    while (!leaf(candidate)) candidate = child(candidate, predecessor ? count(candidate) : 0);
                    int position = predecessor ? count(candidate) - 1 : 0;
                    key = key(candidate, position);
                    pair(page, at, key, value(candidate, position));
                    page = next;
                } else page = merge(page, at);
            } else {
                if (isLeaf) return false;
                long next = child(page, at);
                if (count(next) == degree - 1) {
                    if (at > 0 && count(child(page, at - 1)) >= degree) borrowLeft(page, at);
                    else if (at < n && count(child(page, at + 1)) >= degree) borrowRight(page, at);
                    else next = merge(page, at < n ? at : at - 1);
                }
                page = next;
            }
        }
        return false;
    }

    private void split(long parent, int at) {
        long left = child(parent, at), right = node(leaf(left));
        boolean isLeaf = leaf(left);
        for (int i = 0; i < degree - 1; i++) copy(left, i + degree, right, i);
        if (!isLeaf) for (int i = 0; i < degree; i++) child(right, i, child(left, i + degree));
        long middleKey = key(left, degree - 1), middleValue = value(left, degree - 1);
        count(left, degree - 1); count(right, degree - 1);
        int n = count(parent);
        for (int i = n; i > at; i--) child(parent, i + 1, child(parent, i));
        for (int i = n; i > at; i--) copy(parent, i - 1, parent, i);
        child(parent, at + 1, right); pair(parent, at, middleKey, middleValue); count(parent, n + 1);
    }

    private void borrowLeft(long parent, int at) {
        long page = child(parent, at), left = child(parent, at - 1);
        int n = count(page), m = count(left);
        for (int i = n; i > 0; i--) copy(page, i - 1, page, i);
        if (!leaf(page)) {
            for (int i = n; i >= 0; i--) child(page, i + 1, child(page, i));
            child(page, 0, child(left, m));
        }
        copy(parent, at - 1, page, 0); copy(left, m - 1, parent, at - 1);
        count(left, m - 1); count(page, n + 1);
    }
    private void borrowRight(long parent, int at) {
        long page = child(parent, at), right = child(parent, at + 1);
        int n = count(page), m = count(right);
        copy(parent, at, page, n); copy(right, 0, parent, at);
        if (!leaf(page)) {
            child(page, n + 1, child(right, 0));
            for (int i = 0; i < m; i++) child(right, i, child(right, i + 1));
        }
        for (int i = 0; i + 1 < m; i++) copy(right, i + 1, right, i);
        count(page, n + 1); count(right, m - 1);
    }
    private long merge(long parent, int at) {
        long left = child(parent, at), right = child(parent, at + 1);
        int n = count(left), m = count(right), p = count(parent);
        copy(parent, at, left, n);
        for (int i = 0; i < m; i++) copy(right, i, left, n + 1 + i);
        if (!leaf(left)) for (int i = 0; i <= m; i++) child(left, n + 1 + i, child(right, i));
        count(left, n + m + 1);
        for (int i = at; i + 1 < p; i++) copy(parent, i + 1, parent, i);
        for (int i = at + 1; i < p; i++) child(parent, i, child(parent, i + 1));
        count(parent, p - 1); release(right);
        return left;
    }

    private int search(long page, int count, long key) {
        int low = 0, high = count - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1, comparison = compare(key, key(page, middle));
            if (comparison == 0) return ~middle;
            if (comparison < 0) high = middle - 1; else low = middle + 1;
        }
        return low;
    }
    private int compare(long first, long second) { resources.work(1, phase); return order.compare(first, second); }
    private long node(boolean leaf) {
        long page=provisional=store.allocate();
        ownedPages.set(page,page);provisional=0;
        write(page,1,leaf?1:0);return page;
    }
    private void release(long page) {
        provisional=page;ownedPages.set(page,0);store.release(page);provisional=0;
    }
    private int count(long page) {
        long result = read(page, 0);
        if (result < 0 || result > maximum) throw new PageStore.Failure(PageStore.Reason.CORRUPT, "invalid index node count");
        return (int) result;
    }
    private void count(long page, int count) { write(page, 0, count); }
    private boolean leaf(long page) {
        long result = read(page, 1);
        if (result != 0 && result != 1) throw new PageStore.Failure(PageStore.Reason.CORRUPT, "invalid index node kind");
        return result == 1;
    }
    private long child(long page, int at) { return read(page, 2 + at); }
    private void child(long page, int at, long value) { write(page, 2 + at, value); }
    private long key(long page, int at) { return read(page, pairBase + at * 2); }
    private long value(long page, int at) { return read(page, pairBase + at * 2 + 1); }
    private void pair(long page, int at, long key, long value) {
        write(page, pairBase + at * 2, key); write(page, pairBase + at * 2 + 1, value);
    }
    private void copy(long source, int from, long target, int to) {
        long key = key(source, from), value = value(source, from); pair(target, to, key, value);
    }
    private long read(long page, int slot) {
        resources.work(1, phase); store.read(page, slot * Long.BYTES, word, 0, Long.BYTES);
        long result = 0;
        for (int i = 0; i < Long.BYTES; i++) result |= (word[i] & 255L) << (i * 8);
        return result;
    }
    private void write(long page, int slot, long value) {
        resources.work(1, phase);
        for (int i = 0; i < Long.BYTES; i++) word[i] = (byte) (value >>> (i * 8));
        store.write(page, slot * Long.BYTES, word, 0, Long.BYTES);
    }
    private void open() { if (closed || failed) throw new IllegalStateException("index is closed or aborted"); }
    private void mutate() {
        if (version == Long.MAX_VALUE) throw new IllegalStateException("index version space exhausted");
        version++;
    }

    /** Fixed-size leased traversal state; no row objects or whole-index materialization. */
    public static final class Cursor implements AutoCloseable {
        private volatile PagedLongIndex owner;
        private final long version;
        private final AnalysisResources.Reservation reservation;
        private Cursor previous, next;
        private long[] pages;
        private int[] events, counts;
        private boolean[] leaves;
        private int depth = -1;
        private long key, value;
        private volatile boolean closed, exhausted;
        private boolean positioned;

        private Cursor(PagedLongIndex owner) {
            this.owner = owner; version = owner.version;
            boolean empty = owner.root == 0;
            reservation = owner.resources.reserve(empty ? AnalysisResources.Pool.RESIDENT : AnalysisResources.Pool.SCRATCH,
                    empty ? 256 : 2048, owner.phase);
            try {
                if (!empty) {
                    pages = new long[64]; events = new int[64]; counts = new int[64]; leaves = new boolean[64];
                    push(owner, owner.root);
                }
            } catch (RuntimeException exception) {
                reservation.close(); this.owner = null;
                if (exception instanceof PageStore.Failure) owner.failed = true;
                throw exception;
            }
        }
        private void push(PagedLongIndex owner, long page) {
            depth++;
            if (depth == pages.length) throw new PageStore.Failure(PageStore.Reason.CORRUPT, "cursor depth exceeds 64-bit representability");
            pages[depth] = page; events[depth] = 0;
            leaves[depth] = owner.leaf(page); counts[depth] = owner.count(page);
        }
        public boolean advance() {
            if (closed) throw new IllegalStateException("cursor is closed");
            if (exhausted) return false;
            PagedLongIndex current = owner;
            if (current == null) throw new IllegalStateException("cursor has no live owner");
            synchronized (current) {
                requireVersion(current); positioned = false;
                try {
                    while (depth >= 0) {
                        int event = events[depth], n = counts[depth];
                        long page = pages[depth];
                        if (leaves[depth]) {
                            if (event == n) { depth--; continue; }
                            events[depth]++; key = current.key(page, event); value = current.value(page, event);
                            positioned = true; return true;
                        }
                        if (event > n * 2) { depth--; continue; }
                        events[depth]++;
                        if ((event & 1) == 0) push(current, current.child(page, event / 2));
                        else {
                            int at = event / 2;
                            key = current.key(page, at); value = current.value(page, at);
                            positioned = true; return true;
                        }
                    }
                    exhausted = true; detach(current); return false;
                } catch (RuntimeException exception) {
                    current.failed = true; closed = true; detach(current); throw exception;
                }
            }
        }
        public long key() { return selected(true); }
        public long value() { return selected(false); }
        private long selected(boolean keyRequested) {
            if (closed) throw new IllegalStateException("cursor is closed");
            PagedLongIndex current = owner;
            if (current == null) throw new java.util.NoSuchElementException("cursor has no current record");
            synchronized (current) {
                requireVersion(current);
                if (!positioned) throw new java.util.NoSuchElementException("cursor has no current record");
                return keyRequested ? key : value;
            }
        }
        private void requireVersion(PagedLongIndex current) {
            if (closed) throw new IllegalStateException("cursor is closed");
            current.open();
            if (version != current.version) {
                closed = true; detach(current);
                throw new IllegalStateException("index mutation invalidated cursor");
            }
        }
        private void detach(PagedLongIndex current) {
            if (previous == null) current.cursors = next; else previous.next = next;
            if (next != null) next.previous = previous;
            previous = next = null; positioned = false; key = value = 0;
            pages = null; events = null; counts = null; leaves = null;
            reservation.close(); owner = null;
        }
        @Override public void close() {
            PagedLongIndex current = owner;
            if (current == null) { closed = true; return; }
            synchronized (current) {
                if (owner == null) { closed = true; return; }
                closed = true; detach(current);
            }
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        while (cursors != null) cursors.close();
        try {
            // Ownership is independent of an interrupted split/merge's tree shape.
            // Never follow possibly duplicated or detached child links during teardown.
            ownedPages.visitAllocatedValues(page->{if(page!=provisional)store.release(page);});
        } finally {
            try {if(provisional!=0)store.release(provisional);}
            finally {
                try {ownedPages.close();}
                finally {
                    closed = true; root = size = provisional = 0;
                    word = null; teardownPages = null; teardownNext = null; teardownLimits = null; resident.close();
                }
            }
        }
    }
}
