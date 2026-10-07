package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FilePageStoreTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap, long temporary) {
        return new AnalysisResources(new AnalysisResources.Limits(heap, heap, 0, temporary, 2, 1_000_000, 1_000_000));
    }

    @Test void manyPagesExceedResidentCacheAndRemainExactAfterDirtyEviction() {
        var resources = resources(4096, 1_000_000);
        long heap;
        try (var store = new FilePageStore(directory, 256, 2, resources)) {
            heap = resources.heapUsed();
            long[] handles = new long[100];
            byte[] value = new byte[256], actual = new byte[256];
            for (int i = 0; i < handles.length; i++) {
                handles[i] = store.allocate();
                Arrays.fill(value, (byte) (i + 1));
                store.write(handles[i], 0, value, 0, value.length);
            }
            assertEquals(heap, resources.heapUsed());
            store.flush();
            for (int i = handles.length - 1; i >= 0; i--) {
                store.read(handles[i], 0, actual, 0, actual.length);
                Arrays.fill(value, (byte) (i + 1));
                assertArrayEquals(value, actual);
            }
            assertEquals(100, store.statistics().pagesIssued());
            assertEquals(100, store.statistics().livePages());
            assertTrue(store.statistics().evictions() >= 98);
            assertTrue(store.statistics().bytesRead() > 0);
            assertTrue(store.statistics().bytesWritten() > 0);
        }
        assertEquals(0, resources.heapUsed());
        assertEquals(0, resources.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0, resources.used(AnalysisResources.Pool.OPEN_FILES));
        assertTrue(heap <= 4096);
    }

    @Test void newPagesAreZeroAndPartialWritesPreserveOtherBytes() {
        try (var store = new FilePageStore(directory, 64, 1, resources(4096, 4096))) {
            long page = store.allocate();
            byte[] all = new byte[64];
            Arrays.fill(all, (byte) 9);
            store.read(page, 0, all, 0, all.length);
            assertArrayEquals(new byte[64], all);
            store.write(page, 7, new byte[] {1, 2, 3}, 0, 3);
            store.read(page, 0, all, 0, all.length);
            byte[] expected = new byte[64];
            expected[7] = 1; expected[8] = 2; expected[9] = 3;
            assertArrayEquals(expected, all);
        }
    }

    @Test void releasedHandlesNeverAliasNewPagesAndRejectZeroLengthReads() {
        try (var store = new FilePageStore(directory, 64, 1, resources(4096, 4096))) {
            long old = store.allocate();
            store.release(old);
            long fresh = store.allocate();
            assertNotEquals(old, fresh);
            assertEquals(PageStore.Reason.INVALID_HANDLE, assertThrows(PageStore.Failure.class,
                    () -> store.read(old, 0, new byte[0], 0, 0)).reason());
            assertEquals(PageStore.Reason.INVALID_HANDLE, assertThrows(PageStore.Failure.class,
                    () -> store.release(old)).reason());
            assertEquals(1, store.statistics().livePages());
        }
    }

    @Test void corruptColdPayloadIsRejectedBeforeItCanBecomeAValidFact() throws Exception {
        try (var store = new FilePageStore(directory, 64, 1, resources(4096, 4096))) {
            long first = store.allocate();
            store.write(first, 0, new byte[] {1}, 0, 1);
            store.allocate(); // Evict and checksum the first page.
            store.flush();
            try (var channel = FileChannel.open(store.backingFile(), StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(new byte[] {99}), FilePageStore.FILE_HEADER_BYTES + FilePageStore.PAGE_HEADER_BYTES);
            }
            assertEquals(PageStore.Reason.CORRUPT, assertThrows(PageStore.Failure.class,
                    () -> store.read(first, 0, new byte[1], 0, 1)).reason());
        }
    }

    @Test void quotasFailBeforeCacheAllocationAndCloseCleansTheTemporaryFile() {
        var tooSmall = resources(1, 4096);
        assertThrows(AnalysisResources.Exhausted.class, () -> new FilePageStore(directory, 64, 1, tooSmall));
        assertEquals(0, tooSmall.heapUsed());
        var resources = resources(4096, FilePageStore.FILE_HEADER_BYTES);
        Path backing;
        var store = new FilePageStore(directory, 64, 1, resources);
        try (store) {
            backing = store.backingFile();
            assertEquals(AnalysisResources.Resource.TEMPORARY,
                    assertThrows(AnalysisResources.Exhausted.class, store::allocate).resource());
            assertEquals(0, store.statistics().pagesIssued());
        }
        assertFalse(java.nio.file.Files.exists(backing));
        assertEquals(0, resources.heapUsed());
        assertEquals(0, resources.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0, resources.used(AnalysisResources.Pool.OPEN_FILES));
        store.close();
        assertEquals(PageStore.Reason.CLOSED, assertThrows(PageStore.Failure.class, store::allocate).reason());
    }

    @Test void invalidRangesDoNotChangePageContentsOrEmitIo() {
        try (var store = new FilePageStore(directory, 64, 1, resources(4096, 4096))) {
            long page = store.allocate();
            var before = store.statistics();
            assertThrows(IndexOutOfBoundsException.class, () -> store.write(page, 63, new byte[2], 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> store.read(page, 0, new byte[1], 0, 2));
            assertEquals(before, store.statistics());
            byte[] actual = new byte[64]; store.read(page, 0, actual, 0, actual.length);
            assertArrayEquals(new byte[64], actual);
        }
    }

    @Test void closedStoreDetachesCacheAndDirectoryEvenWhenTheAdapterRemainsReferenced() throws Exception {
        var store = new FilePageStore(directory, 64, 4, resources(4096, 4096));
        store.allocate(); store.close();
        for (var field : FilePageStore.class.getDeclaredFields()) if (field.getType().isArray()) {
            field.setAccessible(true);
            assertNull(field.get(store), "closed store retains " + field.getName());
        }
        assertEquals(1, store.statistics().pagesIssued());
    }

    @Test void churnRetainsEmptyBucketsAndExactLookupAcrossWrappedProbeClusters() throws Exception {
        var random = new java.util.Random(41723);
        try (var store = new FilePageStore(directory, 64, 16, resources(8192, 1_000_000))) {
            long[] live = new long[64];
            byte[] value = new byte[1], actual = new byte[1];
            for (int i = 0; i < live.length; i++) {
                live[i] = store.allocate(); value[0] = (byte) (i + 1);
                store.write(live[i], 0, value, 0, 1);
            }
            var field = FilePageStore.class.getDeclaredField("directoryKeys");
            field.setAccessible(true);
            for (int n = 0; n < 4096; n++) {
                int i = random.nextInt(live.length);
                store.read(live[i], 0, actual, 0, 1);
                assertEquals((byte) (i + 1), actual[0]);
                if (n % 17 == 0) {
                    store.release(live[i]); live[i] = store.allocate();
                    value[0] = (byte) (i + 1); store.write(live[i], 0, value, 0, 1);
                }
                int occupied = 0;
                for (long key : (long[]) field.get(store)) {
                    assertTrue(key >= 0, "removed buckets must not accumulate tombstones");
                    if (key != 0) occupied++;
                }
                assertTrue(occupied <= 16, "cache directory grows with live cache only");
            }
        }
    }

    @Test void physicalReuseBoundsDiskByPeakLivePagesWithoutRevivingOldHandles() {
        long capacity = FilePageStore.FILE_HEADER_BYTES + 2L * (64 + FilePageStore.PAGE_HEADER_BYTES);
        var resources = resources(4096, capacity);
        try (var store = new FilePageStore(directory, 64, 1, resources)) {
            long previous = 0;
            byte[] actual = new byte[64];
            for (int i = 0; i < 1000; i++) {
                long page = store.allocate();
                assertNotEquals(previous, page);
                Arrays.fill(actual, (byte) 99); store.read(page, 0, actual, 0, actual.length);
                assertArrayEquals(new byte[64], actual, "reused storage exposes old payload");
                if (previous != 0) {
                    long stale = previous;
                    assertEquals(PageStore.Reason.INVALID_HANDLE, assertThrows(PageStore.Failure.class,
                            () -> store.read(stale, 0, new byte[1], 0, 1)).reason());
                }
                store.write(page, 0, new byte[] {7}, 0, 1);
                store.release(page); previous = page;
                assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY) <= capacity);
            }
            assertEquals(1000, store.statistics().pagesIssued());
            assertEquals(0, store.statistics().livePages());
        }
        assertEquals(0, resources.used(AnalysisResources.Pool.TEMPORARY));
    }

    @Test void corruptFreeListCannotOverwriteLiveFactsOrBecomeAnAllocation() throws Exception {
        try (var store = new FilePageStore(directory, 64, 1, resources(4096, 4096))) {
            long page = store.allocate(); store.release(page);
            try (var channel = FileChannel.open(store.backingFile(), StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(new byte[] {99}), FilePageStore.FILE_HEADER_BYTES + 16);
            }
            assertEquals(PageStore.Reason.CORRUPT, assertThrows(PageStore.Failure.class, store::allocate).reason());
        }
    }
}
