package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual file store, no substitute algorithm; diagnostic counter is private. */
final class NativeFilePageLookupTest {
    @TempDir Path directory;
    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(64L<<20,64L<<20,0,64L<<20,2,20_000_000,0));
    }
    private static long probes(FilePageStore store) throws Exception {
        var field=FilePageStore.class.getDeclaredField("directoryProbes");field.setAccessible(true);return field.getLong(store);
    }
    private static void empty(AnalysisResources resources) {
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
    }
    @Test void repeatedSameFullHandleAvoidsGeneralDirectoryProbesWithoutCachingPayload() throws Exception {
        var failures=new ArrayList<String>();
        for(int cache:new int[]{1,16,128})for(int count:new int[]{64,128,256,512,1024}) {
            var resources=resources();
            try(var store=new FilePageStore(directory,64,cache,resources)) {
                long page=store.allocate();byte[] value=new byte[1],actual=new byte[1];
                store.read(page,0,actual,0,1);
                long before=probes(store),heap=resources.heapUsed();var statistics=store.statistics();
                for(int n=0;n<count;n++) {
                    int offset=n%64;value[0]=(byte)(n*17+3);
                    store.write(page,offset,value,0,1);store.read(page,offset,actual,0,1);
                    assertArrayEquals(value,actual,"a lookup memo must not retain payload");
                }
                long delta=probes(store)-before;
                assertEquals(heap,resources.heapUsed());
                assertEquals(statistics.cacheHits()+2L*count,store.statistics().cacheHits());
                assertEquals(statistics.cacheMisses(),store.statistics().cacheMisses());
                assertEquals(statistics.evictions(),store.statistics().evictions());
                assertEquals(statistics.bytesRead(),store.statistics().bytesRead());
                assertEquals(statistics.bytesWritten(),store.statistics().bytesWritten());
                System.out.println("PAGE_LOOKUP_CONTROL cache="+cache+" count="+count+" probes="+delta+" heap="+heap);
                if(delta!=0)failures.add("cache="+cache+" count="+count+" probes="+delta);
            }
            empty(resources);
        }
        assertTrue(failures.isEmpty(),"NATIVE_PAGE_REPEAT_LOOKUP_RED "+failures);
    }
    @Test void actualProbeCounterAndGenerationEvictionCorruptionRangesAndCloseRemainExact() throws Exception {
        var resources=resources();
        try(var store=new FilePageStore(directory,64,16,resources)) {
            long[] live=new long[40];byte[] one={1},read=new byte[1];
            for(int n=0;n<live.length;n++){live[n]=store.allocate();one[0]=(byte)n;store.write(live[n],0,one,0,1);}
            var keysField=FilePageStore.class.getDeclaredField("directoryKeys");keysField.setAccessible(true);
            var slotsField=FilePageStore.class.getDeclaredField("directorySlots");slotsField.setAccessible(true);
            var bucket=FilePageStore.class.getDeclaredMethod("bucket",long.class);bucket.setAccessible(true);
            var find=FilePageStore.class.getDeclaredMethod("find",long.class);find.setAccessible(true);
            long[] keys=((long[])keysField.get(store)).clone();int[] slots=((int[])slotsField.get(store)).clone();
            for(long key:new long[]{0,1,7,19,29,40,5000}) {
                int at=(Integer)bucket.invoke(store,key),expected=-1,visits=0;
                do {visits++;if(keys[at]==0)break;if(keys[at]==key){expected=slots[at];break;}at=(at+1)&(keys.length-1);}while(visits<keys.length);
                long before=probes(store);assertEquals(expected,(Integer)find.invoke(store,key));assertEquals(visits,probes(store)-before);
            }
            for(int n=0;n<live.length;n++){store.read(live[n],0,read,0,1);assertEquals((byte)n,read[0]);}
            long warm=live[live.length-1];var before=store.statistics();long probeBefore=probes(store);
            assertThrows(IndexOutOfBoundsException.class,()->store.read(warm,64,new byte[1],0,1));
            assertThrows(IndexOutOfBoundsException.class,()->store.write(warm,0,new byte[0],0,1));
            assertEquals(before,store.statistics());assertEquals(probeBefore,probes(store));
        }
        empty(resources);
        for(int cache:new int[]{1,2,16}) {
            var runResources=resources();FilePageStore store=new FilePageStore(directory,64,cache,runResources);
            try(store) {
                long retired=store.allocate();store.write(retired,0,new byte[]{9},0,1);store.read(retired,0,new byte[1],0,1);
                store.release(retired);long fresh=store.allocate();assertNotEquals(retired,fresh);
                byte[] read=new byte[64];Arrays.fill(read,(byte)99);store.read(fresh,0,read,0,64);assertArrayEquals(new byte[64],read);
                assertEquals(PageStore.Reason.INVALID_HANDLE,assertThrows(PageStore.Failure.class,()->store.read(retired,0,new byte[0],0,0)).reason());
                assertEquals(PageStore.Reason.INVALID_HANDLE,assertThrows(PageStore.Failure.class,()->store.release(retired)).reason());
                store.write(fresh,7,new byte[]{42},0,1);store.readForCleanup(fresh,0,read,0,64);assertEquals(42,read[7]);
                long warm=0;for(int n=0;n<2*cache;n++){warm=store.allocate();store.write(warm,0,new byte[]{3},0,1);}
                var find=FilePageStore.class.getDeclaredMethod("find",long.class);find.setAccessible(true);
                assertEquals(-1,(Integer)find.invoke(store,fresh),"corruption witness requires a cold page;CLOCK order is not allocation order");
                store.flush();
                try(var channel=FileChannel.open(store.backingFile(),StandardOpenOption.WRITE)) {
                    channel.write(ByteBuffer.wrap(new byte[]{99}),FilePageStore.FILE_HEADER_BYTES+FilePageStore.PAGE_HEADER_BYTES+7);
                }
                assertEquals(PageStore.Reason.CORRUPT,assertThrows(PageStore.Failure.class,()->store.read(fresh,0,read,0,64)).reason());
                long last=warm;
                assertEquals(PageStore.Reason.CORRUPT,assertThrows(PageStore.Failure.class,()->store.read(last,0,read,0,1)).reason());
            }
            assertFalse(java.nio.file.Files.exists(store.backingFile()));store.close();
            assertEquals(PageStore.Reason.CLOSED,assertThrows(PageStore.Failure.class,()->store.read(1,0,new byte[0],0,0)).reason());
            empty(runResources);
        }
    }
}
