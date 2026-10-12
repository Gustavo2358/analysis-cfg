package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PagedSnapshotOrderStorageTest {
    @TempDir Path directory;

    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,
                64_000_000,2,1_000_000_000,1_000_000));
    }

    @Test void reverseCanonicalInputSpillsWithoutInputCardinalityHeapMetadata() {
        var resources=resources();
        try(var pages=new FilePageStore(directory,128,1,resources);
            var storage=new PagedSnapshotOrderStorage(pages,resources);
            var order=storage.open(Long::compare)) {
            long fixedHeap=resources.heapUsed();
            for(long handle=4096;handle>=1;handle--)order.add(handle);
            assertEquals(fixedHeap,resources.heapUsed(),"ordering metadata must not grow in managed heap");
            try(var cursor=order.cursor()) {
                for(long expected=1;expected<=4096;expected++) {
                    assertTrue(cursor.advance());assertEquals(expected,cursor.handle());
                }
                assertFalse(cursor.advance());assertFalse(cursor.advance());
            }
            assertTrue(pages.statistics().evictions()>100);
            assertTrue(resources.heapPeak()<=65_536);
            System.out.println("SNAPSHOT_ORDER_STORAGE_METRICS heap="+resources.heapPeak()+" temporary="+
                    resources.used(AnalysisResources.Pool.TEMPORARY)+" rows=4096 evictions="+pages.statistics().evictions());
        }
        assertEquals(0,resources.heapUsed());
    }

    @Test void comparatorFailureAbortsFactoryAndClosingOwnerReleasesEveryPage() {
        var resources=resources();
        try(var pages=new FilePageStore(directory,128,1,resources)) {
            var storage=new PagedSnapshotOrderStorage(pages,resources);
            var order=storage.open((first,second)->{throw new IllegalStateException("injected snapshot order comparison failure");});
            order.add(1);
            assertEquals("injected snapshot order comparison failure",
                    assertThrows(IllegalStateException.class,()->order.add(2)).getMessage());
            assertThrows(IllegalStateException.class,()->storage.open(Long::compare));
            assertThrows(IllegalStateException.class,order::cursor);
            assertThrows(IllegalStateException.class,storage::close);
            order.close();storage.close();storage.close();
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,resources.heapUsed());
    }

    @Test void canonicalTapePayloadExceedsResidencyAndRetainsNoOrdinalDirectoryInHeap() {
        var resources=resources();
        try(var pages=new FilePageStore(directory,128,1,resources)) {
            var storage=new PagedSnapshotOrderStorage(pages,resources);var tape=storage.tape();
            long fixed=resources.heapUsed();
            for(long ordinal=0;ordinal<16384;ordinal++)tape.append(ordinal+1);
            assertEquals(16384,tape.size());assertEquals(fixed,resources.heapUsed());
            assertTrue(16384L*Long.BYTES>65_536,"primitive payload must exceed managed residency");
            for(long ordinal=16383;ordinal>=0;ordinal--)assertEquals(ordinal+1,tape.handle(ordinal));
            assertThrows(IndexOutOfBoundsException.class,()->tape.handle(-1));
            assertThrows(IndexOutOfBoundsException.class,()->tape.handle(16384));
            assertThrows(IllegalArgumentException.class,()->tape.append(0));
            assertEquals(16384,tape.size());assertThrows(IllegalStateException.class,storage::close);
            tape.close();tape.close();
            assertThrows(IllegalStateException.class,tape::size);
            assertThrows(IllegalStateException.class,()->tape.handle(0));
            assertEquals(0,pages.statistics().livePages());storage.close();
            System.out.println("SNAPSHOT_UNIT_TAPE_METRICS rows=16384 heap="+resources.heapPeak()+" evictions="+pages.statistics().evictions());
        }
        assertEquals(0,resources.heapUsed());
    }

    @Test void exhaustedCanonicalTapeAbortsFactoryAndStillReleasesOwnedPages() {
        var resources=new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,64_000_000,2,10,1_000_000));
        try(var pages=new FilePageStore(directory,128,1,resources)) {
            var storage=new PagedSnapshotOrderStorage(pages,resources);var tape=storage.tape();
            assertThrows(AnalysisResources.Exhausted.class,()->{for(int i=0;i<100;i++)tape.append(i+1);});
            assertThrows(IllegalStateException.class,tape::size);
            assertThrows(IllegalStateException.class,storage::tape);
            tape.close();storage.close();assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,resources.heapUsed());
    }
}
