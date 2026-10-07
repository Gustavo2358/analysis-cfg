package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PagedLongIndex;
import java.nio.file.Path;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PagedLongIndexTest {
    @TempDir Path directory;
    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(32_768, 32_768, 0,
                32_000_000, 2, 40_000_000, 1_000_000));
    }

    @Test void forcedSpillLookupInsertionAndDeletionMatchIndependentOrderedMap() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 2, resources);
             var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.INDEX)) {
            long heap = resources.heapUsed();
            var expected = new TreeMap<Long, Long>(); var random = new Random(45391);
            for (int n = 0; n < 6000; n++) {
                long key = random.nextInt(2048) - 1024, value = n + 1L;
                if (n % 4 == 0) assertEquals(expected.remove(key) != null, index.remove(key));
                else {
                    Long old = expected.putIfAbsent(key, value);
                    assertEquals(old == null ? value : old.longValue(), index.intern(key, value));
                }
                assertEquals(expected.getOrDefault(key, 0L).longValue(), index.find(key));
                assertEquals(expected.size(), index.size());
            }
            for (long key = -1024; key < 1024; key++) assertEquals(expected.getOrDefault(key, 0L).longValue(), index.find(key));
            assertEquals(heap, resources.heapUsed(), "index directories/records must not grow in heap");
            assertTrue(store.statistics().evictions() > 100);
            for (long key : expected.keySet()) assertTrue(index.remove(key));
            assertEquals(0, index.size()); assertEquals(0, store.statistics().livePages());
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void sortedReverseAndSignedBoundaryKeysKeepTheirOriginalCanonicalValues() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 256, 3, resources);
             var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.DOMAIN)) {
            assertEquals(7, index.intern(Long.MIN_VALUE, 7)); assertEquals(9, index.intern(Long.MAX_VALUE, 9));
            for (long key = 0; key < 1024; key++) assertEquals(key + 11, index.intern(key, key + 11));
            for (long key = 1023; key >= 0; key--) assertEquals(key + 11, index.intern(key, 99999));
            assertEquals(7, index.find(Long.MIN_VALUE)); assertEquals(9, index.find(Long.MAX_VALUE));
            for (long key = 1023; key >= 0; key--) assertTrue(index.remove(key));
            assertTrue(index.remove(Long.MIN_VALUE)); assertTrue(index.remove(Long.MAX_VALUE));
            assertEquals(0, index.size()); assertEquals(0, store.statistics().livePages());
        }
    }

    @Test void exactExternalKeyComparisonSeparatesCollisionsAndUnifiesOnlyEqualKeys() {
        var resources = resources();
        long[] high = {0, 42, 42, 42, 99}, low = {0, 1, 2, 1, -8};
        PagedLongIndex.Order order = (first, second) -> {
            int a = (int) first, b = (int) second, comparison = Long.compare(high[a], high[b]);
            return comparison != 0 ? comparison : Long.compare(low[a], low[b]);
        };
        try (var store = new FilePageStore(directory, 128, 1, resources);
             var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.INDEX, order)) {
            assertEquals(101, index.intern(1, 101)); assertEquals(102, index.intern(2, 102));
            assertEquals(101, index.intern(3, 999)); assertEquals(103, index.intern(4, 103));
            assertEquals(3, index.size()); assertEquals(101, index.find(3));
            assertTrue(index.remove(3)); assertEquals(0, index.find(1)); assertEquals(102, index.find(2));
        }
    }

    @Test void repeatedRetirementReleasesPagesAndBoundsDiskByLiveIndexSize() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 2, resources);
             var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.DOMAIN)) {
            for (int round = 0; round < 16; round++) {
                for (long key = 0; key < 128; key++) index.intern(round * 8192L + key, key + 1);
                for (long key = 0; key < 128; key++) assertTrue(index.remove(round * 8192L + key));
                assertEquals(0, store.statistics().livePages());
            }
            assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY) < 32_768);
        }
    }

    @Test void closingOneIndexKeepsOtherOwnersAndRejectsUseAfterClose() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 1, resources);
             var second = new PagedLongIndex(store, resources, AnalysisResources.Phase.INDEX)) {
            var first = new PagedLongIndex(store, resources, AnalysisResources.Phase.INDEX);
            for (long i = 0; i < 128; i++) { first.intern(i, i + 1); second.intern(i, i + 129); }
            first.close(); first.close(); assertThrows(IllegalStateException.class, () -> first.find(0));
            for (long i = 0; i < 128; i++) assertEquals(i + 129, second.find(i));
            assertThrows(IllegalArgumentException.class, () -> second.intern(999, 0));
            assertEquals(128, second.size()); assertFalse(second.remove(999));
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void comparatorFailureCannotExposeAnIndexAsStableOrReturnPartialLookup() {
        var resources = resources();
        PagedLongIndex.Order order = (a, b) -> { throw new IllegalStateException("injected comparator failure"); };
        try (var store = new FilePageStore(directory, 128, 1, resources)) {
            var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.INDEX, order);
            assertEquals(9, index.intern(1, 9));
            assertEquals("injected comparator failure", assertThrows(IllegalStateException.class,
                    () -> index.find(1)).getMessage());
            assertThrows(IllegalStateException.class, () -> index.intern(2, 10));
            assertThrows(IllegalStateException.class, index::size);
            index.close(); assertEquals(0, store.statistics().livePages());
        }
    }

    @Test void orderedCursorEnumeratesEveryCanonicalRecordUnderForcedSpill() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 1, resources);
             var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.SORT)) {
            for (long key = 1023; key >= 0; key--) index.intern(key, key + 1);
            long baseline = resources.heapUsed();
            try (var cursor = index.cursor()) {
                for (long key = 0; key < 1024; key++) {
                    assertTrue(cursor.advance()); assertEquals(key, cursor.key()); assertEquals(key + 1, cursor.value());
                }
                assertFalse(cursor.advance()); assertFalse(cursor.advance());
                assertThrows(java.util.NoSuchElementException.class, cursor::key);
            }
            assertEquals(baseline, resources.heapUsed());
            assertEquals(0, resources.used(AnalysisResources.Pool.SCRATCH));
        }
    }

    @Test void mutationAndOwnerCloseInvalidateCursorLeasesWithoutLeakingScratch() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 1, resources)) {
            var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.DOMAIN);
            index.intern(1, 11);
            var first = index.cursor(); var second = index.cursor();
            assertTrue(first.advance()); index.intern(2, 12);
            assertThrows(IllegalStateException.class, first::advance);
            assertThrows(IllegalStateException.class, second::advance);
            first.close(); second.close();
            var remaining = index.cursor(); assertTrue(remaining.advance());
            index.close(); assertThrows(IllegalStateException.class, remaining::value);
            remaining.close(); assertEquals(0, resources.used(AnalysisResources.Pool.SCRATCH));
            assertEquals(0, store.statistics().livePages());
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void emptyCursorAndDeniedScratchNeverMaterializeTheIndexOrLoseFacts() {
        var resources = new AnalysisResources(new AnalysisResources.Limits(16_384, 0, 0,
                16_000_000, 2, 1_000_000, 1_000_000));
        try (var store = new FilePageStore(directory, 128, 1, resources);
             var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.REPLAY)) {
            try (var empty = index.cursor()) { assertFalse(empty.advance()); }
            index.intern(0, 9);
            assertEquals(AnalysisResources.Resource.SCRATCH, assertThrows(AnalysisResources.Exhausted.class,
                    index::cursor).resource());
            assertEquals(9, index.find(0)); assertEquals(1, index.size());
        }
    }
}
