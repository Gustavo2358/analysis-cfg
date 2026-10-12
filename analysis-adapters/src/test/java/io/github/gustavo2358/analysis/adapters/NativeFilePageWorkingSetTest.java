package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** A bounded address-working-set law, not a deadline or global complexity proof. */
final class NativeFilePageWorkingSetTest {
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
    @Test void boundedTwoThreeFourPageTracesAvoidGeneralProbesAndPreservePayloadAndPhysicalCache() throws Exception {
        var failures=new ArrayList<String>();
        for(int count:new int[]{64,128,256,512,1024})for(int width:new int[]{2,3,4})for(int order=0;order<3;order++) {
            var resources=resources();
            try(var store=new FilePageStore(directory,64,16,resources)) {
                long[] pages=new long[width];byte[][] expected=new byte[width][64];byte[] value=new byte[1],actual=new byte[64];
                for(int n=0;n<width;n++)pages[n]=store.allocate();
                for(long page:pages)store.read(page,0,actual,0,64);
                long before=probes(store),heap=resources.heapUsed();var statistics=store.statistics();
                for(int n=0;n<count;n++)for(int at=0;at<width;at++) {
                    int selected=order==0?at:order==1?width-1-at:(at+n)%width;
                    int offset=(n*3+selected)%64;value[0]=(byte)(n*17+selected*29+3);
                    expected[selected][offset]=value[0];store.write(pages[selected],offset,value,0,1);
                    store.read(pages[selected],0,actual,0,64);assertArrayEquals(expected[selected],actual);
                }
                long delta=probes(store)-before;
                assertEquals(heap,resources.heapUsed());assertEquals(statistics.livePages(),store.statistics().livePages());
                assertEquals(statistics.pagesIssued(),store.statistics().pagesIssued());
                assertEquals(statistics.cacheHits()+2L*count*width,store.statistics().cacheHits());
                assertEquals(statistics.cacheMisses(),store.statistics().cacheMisses());
                assertEquals(statistics.evictions(),store.statistics().evictions());
                assertEquals(statistics.bytesRead(),store.statistics().bytesRead());
                assertEquals(statistics.bytesWritten(),store.statistics().bytesWritten());
                System.out.println("PAGE_WORKING_SET_CONTROL count="+count+" width="+width+" order="+order+" probes="+delta+" heap="+heap);
                if(delta!=0)failures.add("count="+count+" width="+width+" order="+order+" probes="+delta);
            }
            empty(resources);
        }
        assertTrue(failures.isEmpty(),"NATIVE_PAGE_WORKING_SET_LOOKUP_RED "+failures);
    }
    @Test void allCandidatePositionsRejectStaleGenerationsAndEvictedRowsAndLargerWorkingSetsRemainExact() throws Exception {
        for(int cache:new int[]{1,2,4,16})for(int retiredIndex=0;retiredIndex<4;retiredIndex++) {
            var resources=resources();
            try(var store=new FilePageStore(directory,64,cache,resources)) {
                long[] pages=new long[4];byte[] actual=new byte[1];
                for(int n=0;n<4;n++){pages[n]=store.allocate();store.write(pages[n],0,new byte[]{(byte)(n+11)},0,1);}
                for(int n=0;n<4;n++){store.read(pages[n],0,actual,0,1);assertEquals((byte)(n+11),actual[0]);}
                long old=pages[retiredIndex];store.release(old);long fresh=store.allocate();assertNotEquals(old,fresh);
                store.read(fresh,0,actual,0,1);assertEquals(0,actual[0]);
                assertEquals(PageStore.Reason.INVALID_HANDLE,assertThrows(PageStore.Failure.class,()->store.read(old,0,new byte[0],0,0)).reason());
                assertEquals(PageStore.Reason.INVALID_HANDLE,assertThrows(PageStore.Failure.class,()->store.write(old,0,new byte[0],0,0)).reason());
                pages[retiredIndex]=fresh;
                for(int pass=0;pass<8;pass++)for(int n=3;n>=0;n--) {
                    store.read(pages[n],0,actual,0,1);assertEquals(n==retiredIndex?0:(byte)(n+11),actual[0]);
                }
                long[] larger=new long[37];
                for(int n=0;n<larger.length;n++){larger[n]=store.allocate();store.write(larger[n],0,new byte[]{(byte)(n*7)},0,1);}
                for(int pass=0;pass<3;pass++)for(int n=0;n<larger.length;n++) {
                    int at=(n*11+pass)%larger.length;store.read(larger[at],0,actual,0,1);assertEquals((byte)(at*7),actual[0]);
                }
                for(long page:pages)store.releaseForCleanup(page);
                for(long page:larger)store.releaseForCleanup(page);
                assertEquals(0,store.statistics().livePages());
            }
            empty(resources);
        }
    }
}
