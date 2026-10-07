package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class MemoryPageStoreTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap) {
        return new AnalysisResources(new AnalysisResources.Limits(heap, heap, 0,
                16_000_000, 2, 20_000_000, 1_000_000));
    }
    private static java.util.List<Long> transcript(PageStore store, AnalysisResources resources) {
        var output = new ArrayList<Long>(); var oracle = new TreeMap<Long, Long>();
        try (var array = new PagedLongArray(store, 1024, resources, AnalysisResources.Phase.INDEX);
             var index = new PagedLongIndex(store, resources, AnalysisResources.Phase.INDEX);
             var queue = new PagedWorklist(store, resources, AnalysisResources.Phase.CONTROL)) {
            var random = new Random(97531);
            for (int n = 0; n < 2048; n++) {
                long point = random.nextInt(1024), value = n + 1L;
                array.set(point, value); output.add(array.get(point));
                Long old = oracle.putIfAbsent(point, value);
                assertEquals(old == null ? value : old.longValue(), index.intern(point, value));
                queue.add(point);
                if (n % 4 == 0) output.add(queue.remove());
            }
            while (queue.size() > 0) output.add(queue.remove());
            try (var cursor = index.cursor()) {
                for (var entry : oracle.entrySet()) {
                    assertTrue(cursor.advance()); assertEquals(entry.getKey().longValue(), cursor.key());
                    assertEquals(entry.getValue().longValue(), cursor.value());
                    output.add(cursor.key()); output.add(cursor.value());
                }
                assertFalse(cursor.advance());
            }
            for (long key : oracle.keySet()) assertTrue(index.remove(key));
        }
        assertEquals(0, store.statistics().livePages());
        return java.util.List.copyOf(output);
    }

    @Test void memoryAndForcedFileBackendsProduceTheSamePrimitiveExecutionTranscript() {
        var memoryResources = resources(1_000_000); var fileResources = resources(32_768);
        try (var memory = new MemoryPageStore(128, memoryResources);
             var file = new FilePageStore(directory, 128, 2, fileResources)) {
            assertEquals(transcript(memory, memoryResources), transcript(file, fileResources));
            assertTrue(file.statistics().evictions() > 100);
            assertEquals(0, memoryResources.used(AnalysisResources.Pool.TEMPORARY));
            assertEquals(0, memoryResources.used(AnalysisResources.Pool.OPEN_FILES));
        }
        assertEquals(0, memoryResources.heapUsed()); assertEquals(0, fileResources.heapUsed());
    }

    @Test void metadataGrowthReservesTheOldPlusNewPeakBeforeAllocatingAndKeepsOldPagesExact() {
        var resources = resources(1100);
        try (var store = new MemoryPageStore(64, resources)) {
            long[] pages = new long[4];
            for (int i = 0; i < 4; i++) {
                pages[i] = store.allocate(); store.write(pages[i], 0, new byte[] {(byte) (i + 1)}, 0, 1);
            }
            long before = resources.heapUsed();
            assertEquals(AnalysisResources.Resource.HEAP, assertThrows(AnalysisResources.Exhausted.class,
                    store::allocate).resource());
            assertEquals(before, resources.heapUsed()); assertEquals(4, store.statistics().pagesIssued());
            byte[] actual = new byte[1];
            for (int i = 0; i < 4; i++) { store.read(pages[i], 0, actual, 0, 1); assertEquals((byte) (i + 1), actual[0]); }
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void staleGenerationsNeverReviveAndReusedPayloadIsZeroWithFixedCapacity() {
        var resources = resources(4096);
        var store = new MemoryPageStore(64, resources);
        try (store) {
            long first = store.allocate(); store.write(first, 0, new byte[] {7}, 0, 1);
            long capacity = resources.heapUsed(); store.release(first);
            for (int n = 0; n < 1000; n++) {
                long fresh = store.allocate(); byte[] actual = new byte[64];
                store.read(fresh, 0, actual, 0, actual.length); assertArrayEquals(new byte[64], actual);
                assertEquals(PageStore.Reason.INVALID_HANDLE, assertThrows(PageStore.Failure.class,
                        () -> store.read(first, 0, new byte[0], 0, 0)).reason());
                store.release(fresh); assertEquals(capacity, resources.heapUsed());
            }
        }
        assertEquals(0, resources.heapUsed()); store.close();
        assertEquals(PageStore.Reason.CLOSED, assertThrows(PageStore.Failure.class, store::allocate).reason());
    }
}
