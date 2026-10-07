package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.solver.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongBinaryOperator;

/**
 * Source nominal value/support DAG in primitive paged records. Candidate maps reference support
 * sets; value records retain every flag and candidate root. This is not executable AIR control.
 * The supplied store is borrowed. Callers retain live values explicitly across collect safepoints.
 * The current String bridge is an explicitly resident dictionary, capacity charged conservatively;
 * it is not the managed large-text decoder/dictionary promised by the complete architecture.
 */
final class SourceValueStore implements AutoCloseable {
    static final int OPEN = 1, MODEL = 2, TABLE = 4;
    private static final long VALUE = 4, STATE = 5;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation textsCapacity;
    private final CanonicalTupleArena arena;
    private final PersistentLongMap maps;
    private Map<String, Long> textIds;
    private ArrayList<String> texts;
    private final LongBinaryOperator supportUnion, valueUnion;
    private boolean closed;
    private final long[] joinLeft,joinRight,joinResult;

    SourceValueStore(PageStore pages, AnalysisResources resources) {
        this.resources = Objects.requireNonNull(resources); Objects.requireNonNull(pages);
        textsCapacity = resources.reserve(AnalysisResources.Pool.RESIDENT, 2560, AnalysisResources.Phase.DOMAIN);
        CanonicalTupleArena records = null; PersistentLongMap index = null;
        try {
            records = new CanonicalTupleArena(pages, resources, AnalysisResources.Phase.DOMAIN, 6, new int[]{2, 4, 5});
            index = new PersistentLongMap(records, resources, AnalysisResources.Phase.DOMAIN);
            textIds = new HashMap<>(); texts = new ArrayList<>();
            joinLeft = new long[64]; joinRight = new long[64]; joinResult = new long[64];
        } catch (RuntimeException | Error exception) {
            suppress(index, exception); suppress(records, exception); textsCapacity.close(); throw exception;
        }
        arena = records; maps = index;
        supportUnion = (a, b) -> maps.join(a, b, false, Math::max);
        valueUnion = this::join;
    }

    long unknown(boolean model) { return value(OPEN | (model ? MODEL : 0), 0); }
    int flags(long value) {
        open(); requireValue(value); return (int) arena.field(value, 1);
    }
    private long candidatesRoot(long value) { requireValue(value); return arena.field(value, 2); }
    long literal(String text, int flags, long evidence) {
        open(); checkFlags(flags);
        if (evidence < 0) throw new IllegalArgumentException("nonnegative source evidence atom required");
        long support = evidence == 0 ? 0 : maps.put(0, evidence, 1);
        return value(flags, maps.putReference(0, textId(text), support));
    }
    long join(long left, long right) {
        open(); requireValue(left); requireValue(right);
        if (left == right) return left;
        return value(flags(left) | flags(right), maps.join(candidatesRoot(left), candidatesRoot(right), true, supportUnion));
    }
    long withFlags(long source, int flags) {
        open(); checkFlags(flags); requireValue(source);
        return flags(source) == flags ? source : value(flags, candidatesRoot(source));
    }
    long addSupport(long source, long evidence) {
        open(); requireValue(source);
        if (evidence <= 0) throw new IllegalArgumentException("positive source evidence atom required");
        long result = 0;
        try (var cursor = maps.cursor(candidatesRoot(source))) {
            while (cursor.advance()) result = maps.putReference(result, cursor.key(), maps.put(cursor.value(), evidence, 1));
        }
        return value(flags(source), result);
    }
    long emptyState() { open(); return state(0); }
    private long state(long entries) { return arena.intern(STATE, 0, entries, 0, 0, 0); }
    private long stateEntries(long state) {
        open(); if (state <= 0 || arena.field(state, 0) != STATE) throw new IllegalArgumentException("reachable source state required");
        return arena.field(state, 2);
    }
    long get(long state, long symbol, boolean model) {
        long result = maps.get(stateEntries(state), symbol); return result == 0 ? unknown(model) : result;
    }
    long put(long state, long symbol, long value) {
        requireValue(value); return state(maps.putReference(stateEntries(state), symbol, value));
    }
    long remove(long state, long symbol) { return state(maps.remove(stateEntries(state), symbol)); }
    long joinStates(long left, long right) {
        if (left == right) return left;
        if(left>right){long temporary=left;left=right;right=temporary;}
        long hash=left*0x9e3779b97f4a7c15L+right;hash^=hash>>>33;int slot=(int)hash&63;
        if(joinLeft[slot]==left&&joinRight[slot]==right)return joinResult[slot];
        // All explicit model-symbol overrides carry MODEL already. Missing ordinary/model
        // defaults contribute OPEN; no known candidate or support is removed.
        long result=state(maps.join(stateEntries(left), stateEntries(right), true, valueUnion,
            value -> withFlags(value, flags(value) | OPEN)));
        joinLeft[slot]=left;joinRight[slot]=right;joinResult[slot]=result;return result;
    }
    long emptyValue(int flags) { return value(flags, 0); }
    long addCandidate(long source, String text, long support) {
        open(); long key = textId(text), root = candidatesRoot(source), previous = maps.get(root, key);
        long combined = maps.contains(root, key) ? supportUnion.applyAsLong(previous, support) : support;
        return value(flags(source), maps.putReference(root, key, combined));
    }
    long oneCandidate(long source, long text, long support) {
        return value(flags(source) & ~OPEN, maps.putReference(0, text, support));
    }
    PersistentLongMap.Cursor candidates(long value) { open(); return maps.cursor(candidatesRoot(value)); }
    PersistentLongMap.Cursor supports(long root) { open(); return maps.cursor(root); }
    String text(long id) {
        open(); if (id < 1 || id > texts.size()) throw new IllegalArgumentException("unknown source text ID");
        return texts.get((int) id - 1);
    }
    long retain(long root) { open(); return arena.retain(root); }
    void release(long token) { open(); arena.release(token); }
    long collect() {
        open();java.util.Arrays.fill(joinLeft,0);java.util.Arrays.fill(joinRight,0);java.util.Arrays.fill(joinResult,0);
        return arena.collect();
    }
    long records() { open(); return arena.size(); }
    long pathCopies() { open(); return maps.pathCopies(); }
    long nodeVisits() { open(); return maps.nodeVisits(); }

    private long value(int flags, long candidates) {
        open(); checkFlags(flags); return arena.intern(VALUE, flags, candidates, 0, 0, 0);
    }
    private long textId(String text) {
        Objects.requireNonNull(text); Long old = textIds.get(text); if (old != null) return old;
        // Funds entries, boxing, resident text and old-plus-new array/hash-table growth peaks.
        // Existing source APIs are int-sized; do not reinterpret that as an AIR cardinality rule.
        textsCapacity.grow(192 + 2L * text.length(), AnalysisResources.Phase.DOMAIN);
        long id = texts.size() + 1L; texts.add(text); textIds.put(text, id); return id;
    }
    private void requireValue(long value) {
        if (value <= 0 || arena.field(value, 0) != VALUE) throw new IllegalArgumentException("source value record required");
    }
    private static void checkFlags(int flags) { if (flags < 0 || flags > 7) throw new IllegalArgumentException("source value flag mask"); }
    private void open() { if (closed) throw new IllegalStateException("source value store closed"); }
    private static void suppress(AutoCloseable owner, Throwable primary) {
        if (owner != null) try { owner.close(); } catch (Exception | Error cleanup) { if (primary != cleanup) primary.addSuppressed(cleanup); }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; Throwable failure = null;
        try { maps.close(); } catch (RuntimeException | Error exception) { failure = exception; }
        try { arena.close(); } catch (RuntimeException | Error exception) {
            if (failure == null) failure = exception; else if (failure != exception) failure.addSuppressed(exception);
        }
        textIds = null; texts = null; textsCapacity.close();
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
