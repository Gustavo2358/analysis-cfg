package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PagedWorklist;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PagedWorklistTest {
    @TempDir Path directory;
    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(16_384, 16_384, 0,
                16_000_000, 2, 10_000_000, 1_000_000));
    }

    @Test void forcedSpillPreservesFairFifoAndDuplicateSuppressionAgainstIndependentOracle() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 2, resources);
             var work = new PagedWorklist(store, resources, AnalysisResources.Phase.CONTROL)) {
            long heap = resources.heapUsed();
            var oracle = new ArrayDeque<Long>(); var members = new HashSet<Long>();
            var random = new Random(27329);
            for (int n = 0; n < 5000; n++) {
                long point = random.nextInt(1024);
                boolean expected = members.add(point);
                if (expected) oracle.addLast(point);
                assertEquals(expected, work.add(point));
                if (n % 3 == 0) {
                    long next = oracle.removeFirst(); members.remove(next);
                    assertEquals(next, work.remove());
                }
                assertEquals(oracle.size(), work.size());
            }
            while (!oracle.isEmpty()) assertEquals(oracle.removeFirst().longValue(), work.remove());
            assertEquals(0, work.size());
            assertEquals(heap, resources.heapUsed());
            assertTrue(store.statistics().evictions() > 100);
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void removedMembershipClearsBeforeSelfLoopCanEnqueueAgain() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 1, resources);
             var work = new PagedWorklist(store, resources, AnalysisResources.Phase.DOMAIN)) {
            for (int n = 0; n < 1000; n++) {
                assertTrue(work.add(17)); assertFalse(work.add(17));
                assertEquals(17, work.remove());
                assertEquals(0, work.size());
            }
            assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY) < 4096,
                    "consumed work must not remain as historical disk allocations");
        }
    }

    @Test void pendingLongIdsRemainDistinctAndCloseOnlyReleasesOwnedPages() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 1, resources);
             var second = new PagedWorklist(store, resources, AnalysisResources.Phase.CONTROL)) {
            var first = new PagedWorklist(store, resources, AnalysisResources.Phase.CONTROL);
            assertTrue(first.add(1L << 42)); assertTrue(second.add(1L << 42));
            assertTrue(first.add(Long.MAX_VALUE - 1)); first.close(); first.close();
            assertThrows(IllegalStateException.class, () -> first.add(1));
            assertEquals(1L << 42, second.remove());
            assertTrue(second.add(Long.MAX_VALUE - 1)); assertEquals(Long.MAX_VALUE - 1, second.remove());
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void invalidPointsAndEmptyRemovalDoNotMutatePendingSet() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 1, resources);
             var work = new PagedWorklist(store, resources, AnalysisResources.Phase.CONTROL)) {
            assertThrows(java.util.NoSuchElementException.class, work::remove);
            assertThrows(IndexOutOfBoundsException.class, () -> work.add(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> work.add(Long.MAX_VALUE));
            assertEquals(0, work.size()); assertEquals(0, store.statistics().pagesIssued());
            assertTrue(work.add(0)); assertFalse(work.add(0)); assertEquals(0, work.remove());
        }
    }

    @Test void operationalFailureAbortsSchedulingAndCleanupReleasesReservations() {
        var resources = new AnalysisResources(new AnalysisResources.Limits(16_384, 16_384, 0,
                16_000_000, 2, 1, 1_000_000));
        try (var store = new FilePageStore(directory, 64, 1, resources)) {
            var work = new PagedWorklist(store, resources, AnalysisResources.Phase.CONTROL);
            assertEquals(AnalysisResources.Phase.CONTROL,
                    assertThrows(AnalysisResources.Exhausted.class, () -> work.add(0)).phase());
            assertThrows(IllegalStateException.class, () -> work.add(1));
            assertThrows(IllegalStateException.class, work::size);
            work.close();
            assertEquals(0, store.statistics().livePages());
        }
        assertEquals(0, resources.heapUsed());
        assertEquals(0, resources.used(AnalysisResources.Pool.TEMPORARY));
    }
}
