package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PagedLongArray;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PagedLongArrayTest {
    @TempDir Path directory;
    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(16_384, 16_384, 0,
                16_000_000, 2, 10_000_000, 1_000_000));
    }

    @Test void sequentialInterleavedReadsDoNotReplayEveryColumnDirectoryForEveryWord() {
        long base=1L<<40;
        for(int count:new int[]{16,64,256,1024,4096}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(64000,64000,0,64000000,1,1000000000,1000000));
            try(var store=new FilePageStore(directory,512,16,memory);
                var a=new PagedLongArray(store,Long.MAX_VALUE,memory,AnalysisResources.Phase.INDEX);
                var b=new PagedLongArray(store,Long.MAX_VALUE,memory,AnalysisResources.Phase.INDEX);
                var c=new PagedLongArray(store,Long.MAX_VALUE,memory,AnalysisResources.Phase.INDEX);
                var d=new PagedLongArray(store,Long.MAX_VALUE,memory,AnalysisResources.Phase.INDEX)) {
                var columns=new PagedLongArray[]{a,b,c,d};
                for(int row=0;row<count;row++)for(int column=0;column<columns.length;column++)columns[column].set(base+row,100L*row+column+1);
                long resident=memory.heapUsed(),work=memory.workUsed();var before=store.statistics();
                for(int scan=0;scan<2;scan++)for(int row=0;row<count;row++)for(int column=0;column<columns.length;column++)
                    assertEquals(100L*row+column+1,columns[column].get(base+row));
                long visits=8L*count,leafRoutes=8L*((count+31)/32);
                // 32 payload slots per leaf; the high base requires eight radix levels.
                // One route per changed leaf, plus bounded page/header reloads, not per word.
                long evictions=store.statistics().evictions()-before.evictions();
                assertTrue(evictions<=16+12*leafRoutes,"directory replay amplified paged reads: rows="+count+" evictions="+evictions);
                assertTrue(memory.workUsed()-work<=4*visits+36*leafRoutes+64,"directory traversal repeated per payload word");
                assertEquals(resident,memory.heapUsed(),"route metadata must not grow with column cardinality");
                System.out.println("PAGED_COLUMN_ROUTE_METRICS rows="+count+" work="+(memory.workUsed()-work)+" evictions="+evictions+" heap="+resident);
            }
            assertEquals(0,memory.heapUsed());
        }
    }

    @Test void readRoutesInvalidateAfterMissingLeavesWritesPruningAndRootGrowth() {
        var memory=resources();
        try(var store=new FilePageStore(directory,64,1,memory);
            var array=new PagedLongArray(store,Long.MAX_VALUE,memory,AnalysisResources.Phase.INDEX)) {
            long high=1L<<40;
            assertEquals(0,array.get(high));assertEquals(0,array.get(high+1));
            array.set(high,17);assertEquals(17,array.get(high));assertEquals(0,array.get(high+1));
            array.set(high+1,-9);assertEquals(-9,array.get(high+1));
            array.set(high,0);assertEquals(0,array.get(high));assertEquals(-9,array.get(high+1));
            array.set(high+1,0);assertEquals(0,array.get(high));assertEquals(0,store.statistics().livePages());
            array.set(0,31);assertEquals(31,array.get(0));
            array.set(Long.MAX_VALUE-1,Long.MIN_VALUE);assertEquals(Long.MIN_VALUE,array.get(Long.MAX_VALUE-1));
            assertEquals(31,array.get(0));array.set(Long.MAX_VALUE-1,0);
            assertEquals(31,array.get(0));assertEquals(0,array.get(Long.MAX_VALUE-1));
        }
        assertEquals(0,memory.heapUsed());
    }

    @Test void spilledDirectoriesAndValuesMatchAnIndependentRandomAccessOracle() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 128, 3, resources)) {
            long baseline = resources.heapUsed();
            try (var array = new PagedLongArray(store, 8192, resources, AnalysisResources.Phase.INDEX)) {
                long resident = resources.heapUsed();
                var expected = new HashMap<Long, Long>();
                var random = new Random(72013);
                for (int n = 0; n < 5000; n++) {
                    long index = random.nextInt(8192), value = random.nextLong();
                    array.set(index, value); expected.put(index, value);
                }
                for (long i = 8191; i >= 0; i--) assertEquals(expected.getOrDefault(i, 0L).longValue(), array.get(i));
                assertEquals(resident, resources.heapUsed(), "directories must not grow in heap");
                assertTrue(store.statistics().pagesIssued() > 3);
                assertTrue(store.statistics().evictions() > 100);
                assertEquals(8192, array.length());
            }
            assertEquals(baseline, resources.heapUsed());
            assertEquals(0, store.statistics().livePages());
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void sparseIndexesBeyondIntegerRangeAndAllSignedValuesRemainExact() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 32, 2, resources);
             var array = new PagedLongArray(store, Long.MAX_VALUE, resources, AnalysisResources.Phase.DOMAIN)) {
            long[] indexes = {0, 3, 4, 1L << 33, Long.MAX_VALUE - 1};
            long[] values = {Long.MIN_VALUE, -1, 0x0123456789abcdefL, Long.MAX_VALUE, -723};
            for (int i = 0; i < indexes.length; i++) array.set(indexes[i], values[i]);
            for (int i = indexes.length - 1; i >= 0; i--) assertEquals(values[i], array.get(indexes[i]));
            assertEquals(0, array.get((1L << 33) + 1));
            assertEquals(0, array.get(Long.MAX_VALUE - 2));
        }
    }

    @Test void emptyAndUnwrittenZerosAllocateNoPages() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 1, resources);
             var empty = new PagedLongArray(store, 0, resources, AnalysisResources.Phase.INDEX);
             var array = new PagedLongArray(store, Long.MAX_VALUE, resources, AnalysisResources.Phase.INDEX)) {
            assertThrows(IndexOutOfBoundsException.class, () -> empty.get(0));
            assertThrows(IndexOutOfBoundsException.class, () -> empty.set(0, 1));
            assertEquals(0, array.get(17));
            array.set(Long.MAX_VALUE - 1, 0);
            assertEquals(0, store.statistics().pagesIssued());
        }
    }

    @Test void arraysShareBackendButNeverShareRootsOrReleaseAnotherArraysPages() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 2, resources);
             var second = new PagedLongArray(store, 1000, resources, AnalysisResources.Phase.DOMAIN)) {
            var first = new PagedLongArray(store, 1000, resources, AnalysisResources.Phase.DOMAIN);
            first.set(81, 42); second.set(81, 99); first.close(); first.close();
            assertThrows(IllegalStateException.class, () -> first.get(81));
            assertThrows(IllegalStateException.class, () -> first.set(81, 5));
            assertEquals(99, second.get(81)); second.set(999, -4);
            assertEquals(-4, second.get(999));
        }
    }

    @Test void invalidIndexesAndInvalidConstructionCannotAllocatePages() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 1, resources);
             var array = new PagedLongArray(store, 128, resources, AnalysisResources.Phase.INDEX)) {
            assertThrows(IllegalArgumentException.class, () -> new PagedLongArray(store, -1, resources, AnalysisResources.Phase.INDEX));
            for (long index : new long[] {-1, 128, Long.MAX_VALUE}) {
                assertThrows(IndexOutOfBoundsException.class, () -> array.get(index));
                assertThrows(IndexOutOfBoundsException.class, () -> array.set(index, 7));
            }
            assertEquals(0, store.statistics().pagesIssued());
        }
    }

    @Test void clearingExistingCellPreservesNeighborsAcrossDirectoryGrowth() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 1, resources);
             var array = new PagedLongArray(store, 8192, resources, AnalysisResources.Phase.INDEX)) {
            array.set(0, 10); array.set(1, 11); array.set(8191, 12);
            array.set(0, 0); array.set(8191, 0);
            assertEquals(0, array.get(0)); assertEquals(11, array.get(1)); assertEquals(0, array.get(8191));
            array.set(4096, 13); assertEquals(11, array.get(1)); assertEquals(13, array.get(4096));
        }
    }

    @Test void clearingSparseHistoryReclaimsEmptyLeavesAndDirectoryPathsImmediately() {
        var resources = resources();
        try (var store = new FilePageStore(directory, 64, 2, resources);
             var array = new PagedLongArray(store, Long.MAX_VALUE, resources, AnalysisResources.Phase.INDEX)) {
            for (int n = 0; n < 128; n++) {
                long index = (1L << 48) + n * 8192L;
                array.set(index, 7); assertTrue(store.statistics().livePages() > 0);
                array.set(index, 0); assertEquals(0, array.get(index));
                assertEquals(0, store.statistics().livePages(), "cleared history retains radix pages");
            }
            assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY) < 8192);
        }
    }
}
