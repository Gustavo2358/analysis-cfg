package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.CanonicalTupleArena;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class CanonicalTupleArenaTest {
    @TempDir Path directory;
    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(65_536, 8192, 0,
                64_000_000, 2, 100_000_000, 1_000_000));
    }

    @Test void completePrimitiveKeysCanonicalizeWithoutRetainingCallerBuffers() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 1, resources);
             var arena = new CanonicalTupleArena(store, resources, AnalysisResources.Phase.DOMAIN, 3, new int[0])) {
            long[] tuple = {Long.MIN_VALUE, 31, Long.MAX_VALUE};
            long first = arena.intern(tuple); tuple[1] = 32;
            long second = arena.intern(tuple);
            assertNotEquals(first, second);
            assertEquals(first, arena.intern(Long.MIN_VALUE, 31, Long.MAX_VALUE));
            assertEquals(31, arena.field(first, 1)); assertEquals(32, arena.field(second, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> arena.field(first, 3));
            assertThrows(IllegalArgumentException.class, () -> arena.intern(1, 2));
            assertEquals(2, arena.size());
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void lookupDoesNotInternMissesOrRetainHistoricalQueryPayload() {
        var resources=resources();
        try(var pages=new FilePageStore(directory,128,1,resources);
            var arena=new CanonicalTupleArena(pages,resources,AnalysisResources.Phase.VALIDATION,3,new int[]{1})) {
            long child=arena.intern(7,0,Long.MAX_VALUE),first=arena.intern(Long.MIN_VALUE,child,41);
            long[] probe={Long.MIN_VALUE,child,41};assertEquals(first,arena.find(probe));probe[2]=42;assertEquals(0,arena.find(probe));
            long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed();
            for(int query=0;query<16_384;query++){probe[2]=42+query;assertEquals(0,arena.find(probe));}
            assertEquals(2,arena.size());assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));assertEquals(heap,resources.heapUsed());
            assertEquals(first,arena.find(Long.MIN_VALUE,child,41));assertThrows(IllegalArgumentException.class,()->arena.find(1,child));
            assertThrows(IllegalArgumentException.class,()->arena.find(1,child+100,0));
            long root=arena.retain(first);assertEquals(0,arena.collect());arena.release(root);assertEquals(2,arena.collect());
            assertEquals(0,arena.find(7,0,Long.MAX_VALUE));assertThrows(IllegalArgumentException.class,()->arena.find(1,child,0));
            long replacement=arena.intern(8,0,1);resources.work(resources.limits().workUnits()-resources.workUsed(),AnalysisResources.Phase.VALIDATION);
            assertThrows(AnalysisResources.Exhausted.class,()->arena.find(8,0,1));assertThrows(IllegalStateException.class,()->arena.field(replacement,0));
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
    }

    @Test void rootsPreserveSharedChildrenUntilTheLastCallerReleasesThem() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 1, resources);
             var arena = new CanonicalTupleArena(store, resources, AnalysisResources.Phase.DOMAIN, 2, new int[]{1})) {
            long child = arena.intern(7, 0), left = arena.intern(1, child), right = arena.intern(2, child);
            long a = arena.retain(left), b = arena.retain(right), c = arena.retain(left);
            arena.release(a);
            assertEquals(0, arena.collect()); assertEquals(3, arena.size());
            arena.release(c);
            assertEquals(1, arena.collect()); assertEquals(7, arena.field(child, 0));
            assertThrows(IllegalArgumentException.class, () -> arena.field(left, 0));
            assertThrows(IllegalArgumentException.class, () -> arena.release(a));
            assertThrows(IllegalArgumentException.class, () -> arena.retain(left));
            assertThrows(IllegalArgumentException.class, () -> arena.intern(3, left));
            long replacement = arena.intern(1, child); assertTrue(replacement > right);
            arena.release(b);
            assertEquals(3, arena.collect()); assertEquals(0, arena.size());
            assertEquals(0, store.statistics().livePages());
            assertTrue(arena.intern(7, 0) > replacement);
        }
    }

    @Test void deepProofDagCollectsIterativelyUnderForcedSpillAndFixedResidency() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 1, resources);
             var arena = new CanonicalTupleArena(store, resources, AnalysisResources.Phase.REPLAY, 2, new int[]{1})) {
            long heap = resources.heapUsed(), last = 0;
            for (int n = 0; n < 4096; n++) last = arena.intern(n, last);
            long root = arena.retain(last);
            assertEquals(0, arena.collect()); assertEquals(4096, arena.size());
            assertEquals(heap, resources.heapUsed());
            arena.release(root);
            assertEquals(4096, arena.collect()); assertEquals(0, store.statistics().livePages());
            assertEquals(heap, resources.heapUsed());
            assertTrue(store.statistics().evictions() > 4096);
        }
    }

    @Test void randomSharedDagAndRootChurnMatchIndependentGraphTracing() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 2, resources);
             var arena = new CanonicalTupleArena(store, resources, AnalysisResources.Phase.DOMAIN, 3, new int[]{1, 2})) {
            var random = new Random(80917); var rows = new HashMap<Long, long[]>();
            var roots = new HashMap<Long, Long>(); var live = new ArrayList<Long>();
            for (int round = 0; round < 12; round++) {
                for (int n = 0; n < 80; n++) {
                    long a = live.isEmpty() || random.nextInt(4) == 0 ? 0 : live.get(random.nextInt(live.size()));
                    long b = live.isEmpty() || random.nextInt(4) == 0 ? 0 : live.get(random.nextInt(live.size()));
                    long[] key = {random.nextInt(16), a, b};
                    long prior = 0;
                    for (var row : rows.entrySet()) if (Arrays.equals(key, row.getValue())) { prior = row.getKey(); break; }
                    long handle = arena.intern(key);
                    if (prior != 0) assertEquals(prior, handle);
                    else { rows.put(handle, key); live.add(handle); }
                    if (random.nextInt(6) == 0) roots.put(arena.retain(handle), handle);
                }
                var released = new ArrayList<Long>();
                for (long token : roots.keySet()) if (random.nextBoolean()) released.add(token);
                for (long token : released) { roots.remove(token); arena.release(token); }
                var reachable = new HashSet<Long>(); var pending = new ArrayDeque<Long>(roots.values());
                while (!pending.isEmpty()) {
                    long handle = pending.removeFirst();
                    if (!reachable.add(handle)) continue;
                    long[] row = rows.get(handle);
                    if (row[1] != 0) pending.addLast(row[1]);
                    if (row[2] != 0) pending.addLast(row[2]);
                }
                assertEquals(rows.size() - reachable.size(), arena.collect());
                rows.keySet().retainAll(reachable); live.retainAll(reachable);
                assertEquals(rows.size(), arena.size());
                for (var row : rows.entrySet()) for (int column = 0; column < 3; column++)
                    assertEquals(row.getValue()[column], arena.field(row.getKey(), column));
            }
            for (long token : roots.keySet()) arena.release(token);
            arena.collect(); assertEquals(0, store.statistics().livePages());
        }
    }

    @Test void collectionQuotaFailureAbortsTheArenaInsteadOfExposingPartiallyRetiredFacts() {
        var resources = resources();
        var store = new FilePageStore(directory, 128, 1, resources);
        try {
            var arena = new CanonicalTupleArena(store, resources, AnalysisResources.Phase.DOMAIN, 2, new int[]{1});
            long first = arena.intern(1, 0); arena.retain(first);
            resources.work(100_000_000 - resources.workUsed(), AnalysisResources.Phase.DOMAIN);
            assertThrows(AnalysisResources.Exhausted.class, arena::collect);
            assertThrows(IllegalStateException.class, arena::size);
            assertThrows(IllegalStateException.class, () -> arena.field(first, 0));
            // Analysis remains aborted, but teardown uses its separate protocol
            // and must release all borrowed pages without resetting WORK.
            assertDoesNotThrow(arena::close);
            assertEquals(0,store.statistics().livePages());
            assertEquals(100_000_000,resources.workUsed());assertTrue(resources.cleanupWorkUsed()>0);
            arena.close();
        } finally { assertDoesNotThrow(store::close); }
        assertEquals(0, resources.heapUsed()); assertEquals(0, resources.used(AnalysisResources.Pool.TEMPORARY));
    }

    @Test void rejectedSchemaAndConstructorQuotaReleaseAllPartiallyBuiltOwners() {
        var resources = new AnalysisResources(new AnalysisResources.Limits(6000, 8192, 0,
                64_000_000, 2, 100_000_000, 1_000_000));
        try (var store = new FilePageStore(directory, 128, 1, resources)) {
            long before = resources.heapUsed();
            assertThrows(IllegalArgumentException.class,
                    () -> new CanonicalTupleArena(store, resources, AnalysisResources.Phase.DOMAIN, 2, new int[]{1, 1}));
            assertEquals(before, resources.heapUsed());
            assertThrows(AnalysisResources.Exhausted.class,
                    () -> new CanonicalTupleArena(store, resources, AnalysisResources.Phase.DOMAIN, 2, new int[]{1}));
            assertEquals(before, resources.heapUsed()); assertEquals(0, store.statistics().livePages());
        }
        assertEquals(0, resources.heapUsed());
    }
}
