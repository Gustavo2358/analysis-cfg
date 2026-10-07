package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PersistentLongMapTest {
    @TempDir Path directory;
    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(32_000_000, 8192, 0, 128_000_000, 2, 1_000_000_000, 1_000_000));
    }
    private static CanonicalTupleArena arena(PageStore pages, AnalysisResources resources) {
        return new CanonicalTupleArena(pages, resources, AnalysisResources.Phase.DOMAIN, 6, new int[]{2, 4, 5});
    }
    @Test void residentAndForcedSpillVersionsMatchIndependentOrderedMaps() {
        for (boolean disk : new boolean[]{false, true}) {
            var resources = resources();
            try (PageStore pages = disk ? new FilePageStore(directory, 512, 8, resources) : new MemoryPageStore(512, resources);
                 var arena = arena(pages, resources); var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
                var random = new Random(80721); var expected = new TreeMap<Long, Long>(); long root = 0;
                long retained = 0; var old = new TreeMap<Long, Long>(); long oldRoot = 0;
                for (int i = 0; i < 1000; i++) {
                    long key = random.nextInt(150) - 75, value = random.nextInt(13) - 6;
                    if (random.nextBoolean()) { root = maps.put(root, key, value); expected.put(key, value); }
                    else { root = maps.remove(root, key); expected.remove(key); }
                    assertEquals(expected.containsKey(key), maps.contains(root, key));
                    assertEquals(expected.getOrDefault(key, 0L).longValue(), maps.get(root, key));
                    if (i % 71 == 0) {
                        if (retained != 0) arena.release(retained);
                        oldRoot = root; old = new TreeMap<>(expected); retained = root == 0 ? 0 : arena.retain(root);
                    }
                    if (i % 83 == 0) {
                        long current = root == 0 ? 0 : arena.retain(root);
                        arena.collect(); assertMap(maps, root, expected); assertMap(maps, oldRoot, old);
                        if (current != 0) arena.release(current);
                    }
                }
                assertMap(maps, root, expected); assertMap(maps, oldRoot, old);
                if (retained != 0) arena.release(retained);
            }
            assertEquals(0, resources.heapUsed()); assertEquals(0, resources.used(AnalysisResources.Pool.TEMPORARY));
        }
    }
    private static void assertMap(PersistentLongMap maps, long root, Map<Long, Long> expected) {
        var observed = new TreeMap<Long, Long>();
        try (var cursor = maps.cursor(root)) {
            Long previous = null;
            while (cursor.advance()) {
                if (previous != null) assertTrue(previous < cursor.key()); previous = cursor.key();
                assertFalse(cursor.reference()); observed.put(cursor.key(), cursor.value());
            }
        }
        assertEquals(expected, observed);
    }
    @Test void fullSignedKeysZeroValuesAndInsertionOrderHaveCanonicalRoots() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 2, resources); var arena = arena(pages, resources);
             var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
            long[] keys = {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE}; long left = 0, right = 0;
            var expected = new TreeMap<Long, Long>();
            for (long key : keys) { left = maps.put(left, key, key); expected.put(key, key); }
            for (int i = keys.length - 1; i >= 0; i--) right = maps.put(right, keys[i], keys[i]);
            assertEquals(left, right); long copies = maps.pathCopies(); assertEquals(left, maps.put(left, 0, 0));
            assertEquals(copies, maps.pathCopies());
            assertEquals(keys.length, maps.size(left));
            assertTrue(maps.contains(left, 0)); assertFalse(maps.contains(left, 3)); assertMap(maps, left, expected);
            long version = maps.remove(left, 0); assertFalse(maps.contains(version, 0)); assertTrue(maps.contains(left, 0));
            assertEquals(left, maps.put(version, 0, 0)); assertEquals(version, maps.remove(version, 0));
            for (long key : keys) version = maps.remove(version, key);
            assertEquals(0, version);
            assertEquals(0, maps.size(version));
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void referenceValuesAndCursorLeasesRetainExactlyTheirTransitiveRecords() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 2, resources); var arena = arena(pages, resources);
             var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
            long proof = arena.intern(9, 71, 0, 0, 0, 0), root = maps.putReference(0, 17, proof);
            long scratch = resources.used(AnalysisResources.Pool.SCRATCH);
            var cursor = maps.cursor(root); assertTrue(cursor.advance());
            assertTrue(cursor.reference()); assertEquals(proof, cursor.value()); assertEquals(17, cursor.key());
            assertEquals(0, arena.collect()); assertEquals(71, arena.field(proof, 1));
            cursor.close(); assertEquals(scratch, resources.used(AnalysisResources.Pool.SCRATCH));
            assertEquals(2, arena.collect()); assertEquals(0, arena.size());
            assertThrows(IllegalArgumentException.class, () -> maps.get(root, 17));
            assertThrows(IllegalStateException.class, cursor::advance);
        }
    }
    @Test void manySingleWriteSnapshotsShareUnchangedPathsAndRetireAfterRootRelease() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 8, resources); var arena = arena(pages, resources);
             var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
            int n = 1024; long[] roots = new long[n], tokens = new long[n]; long root = 0, heap = resources.heapUsed();
            for (int i = 0; i < n; i++) { root = maps.put(root, i, i + 1); roots[i] = root; tokens[i] = arena.retain(root); }
            assertEquals(0, arena.collect()); assertTrue(arena.size() < 16L * n, "unchanged state duplicated");
            assertEquals(heap, resources.heapUsed());
            for (int i = 0; i < n; i += 31) { assertEquals(i + 1, maps.get(roots[i], i)); assertFalse(maps.contains(roots[i], i + 1)); }
            for (int i = 0; i < n - 1; i++) arena.release(tokens[i]);
            assertTrue(arena.collect() > 0); assertEquals(2L * n - 1, arena.size());
            arena.release(tokens[n - 1]); arena.collect(); assertEquals(0, arena.size()); assertEquals(0, pages.statistics().livePages());
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void cursorDenialAndSchemaMismatchCannotAlterPreviouslyBuiltFacts() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 2, resources); var arena = arena(pages, resources);
             var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
            long root = maps.put(0, 0, 23);
            try (var held = resources.reserve(AnalysisResources.Pool.SCRATCH, 8192, AnalysisResources.Phase.DOMAIN)) {
                assertEquals(8192, held.amount()); assertThrows(AnalysisResources.Exhausted.class, () -> maps.cursor(root));
            }
            assertEquals(23, maps.get(root, 0));
            try (var wrong = new CanonicalTupleArena(pages, resources, AnalysisResources.Phase.DOMAIN, 6, new int[]{4, 5})) {
                assertThrows(IllegalArgumentException.class, () -> new PersistentLongMap(wrong, resources, AnalysisResources.Phase.DOMAIN));
            }
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void ownerCloseReleasesActiveCursorRootsAndScratchWithoutClosingBorrowedArena() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 2, resources); var arena = arena(pages, resources)) {
            var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN);
            try {
                long root = maps.put(0, -5, 19); var cursor = maps.cursor(root);
                assertEquals(0, arena.collect()); maps.close();
                assertEquals(0, resources.used(AnalysisResources.Pool.SCRATCH));
                assertThrows(IllegalStateException.class, cursor::advance);
                assertThrows(IllegalStateException.class, () -> maps.get(root, -5));
                assertEquals(1, arena.collect()); assertEquals(0, arena.size());
            } finally { maps.close(); }
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void controlReservationDenialLeavesBorrowedStorageAndArenaUsable() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 2, resources); var arena = arena(pages, resources)) {
            long baseline = resources.heapUsed();
            try (var held = resources.reserve(AnalysisResources.Pool.RESIDENT, resources.limits().heapBytes() - baseline, AnalysisResources.Phase.DOMAIN)) {
                assertTrue(held.amount() > 0);
                assertThrows(AnalysisResources.Exhausted.class, () -> new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN));
            }
            assertEquals(baseline, resources.heapUsed());
            try (var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
                long root = maps.putReference(0, Long.MAX_VALUE, 0);
                assertTrue(maps.contains(root, Long.MAX_VALUE)); assertTrue(maps.reference(root, Long.MAX_VALUE));
                assertEquals(0, maps.get(root, Long.MAX_VALUE));
            }
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void joinsSkipSharedSubtreesAndAgreeWithIndependentPointwiseUnion() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 8, resources); var arena = arena(pages, resources);
             var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
            long common = 0;
            for (int i = 0; i < 1024; i++) common = maps.put(common, i, 1);
            long changed = maps.put(common, 501, 2); int[] calls = {0};
            long joined = maps.join(common, changed, false, (left, right) -> { calls[0]++; return Math.max(left, right); });
            assertEquals(changed, joined); assertEquals(1, calls[0]);
            assertEquals(common, maps.join(common, common, false, (left, right) -> { throw new AssertionError("identical state rejoined"); }));
            var random = new Random(346092);
            for (int repeat = 0; repeat < 30; repeat++) {
                long left = 0, right = 0; var expected = new TreeMap<Long, Long>();
                for (int i = 0; i < 50; i++) {
                    long key = random.nextLong(), value = random.nextInt(8);
                    left = maps.put(left, key, value); expected.merge(key, value, Math::max);
                    key = i % 3 == 0 ? key : random.nextLong(); value = random.nextInt(8);
                    right = maps.put(right, key, value); expected.merge(key, value, Math::max);
                }
                long result = maps.join(left, right, false, Math::max);
                assertMap(maps, result, expected); assertEquals(result, maps.join(right, left, false, Math::max));
                assertEquals(result, maps.join(result, left, false, Math::max));
            }
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void referenceValueJoinsCanReenterIndependentPrimitiveJoinsAndRemainCollectable() {
        var resources = resources();
        try (var pages = new FilePageStore(directory, 512, 8, resources); var arena = arena(pages, resources);
             var maps = new PersistentLongMap(arena, resources, AnalysisResources.Phase.DOMAIN)) {
            long a = maps.put(maps.put(0, 1, 1), 2, 5), b = maps.put(maps.put(0, 2, 7), 3, 3);
            long av = arena.intern(9, 1, a, 0, 0, 0), bv = arena.intern(9, 2, b, 0, 0, 0);
            long left = maps.putReference(0, 0, av), right = maps.putReference(0, 0, bv);
            long root = maps.join(left, right, true, (x, y) -> {
                long joined = maps.join(arena.field(x, 2), arena.field(y, 2), false, Math::max);
                return arena.intern(9, arena.field(x, 1) | arena.field(y, 1), joined, 0, 0, 0);
            });
            long token = arena.retain(root);
            try {
                arena.collect(); long value = maps.get(root, 0), joined = arena.field(value, 2);
                assertTrue(maps.reference(root, 0)); assertEquals(3, arena.field(value, 1));
                assertMap(maps, joined, Map.of(1L, 1L, 2L, 7L, 3L, 3L));
                assertEquals(0, resources.used(AnalysisResources.Pool.SCRATCH));
            } finally { arena.release(token); }
            arena.collect(); assertEquals(0, arena.size());
        }
        assertEquals(0, resources.heapUsed());
    }
}
