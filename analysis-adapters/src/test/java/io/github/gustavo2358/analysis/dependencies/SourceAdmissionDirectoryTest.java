package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Internal complete-key building block; no semantic certificate is issued. */
final class SourceAdmissionDirectoryTest {
    @TempDir Path directory;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(32768,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    @Test void everyKeyComponentAndNamespaceParticipatesInExactIdentity() {
        var resources=resources();
        try(var pages=new FilePageStore(directory,512,4,resources,AnalysisResources.Phase.VALIDATION);
            var rows=new SourceAdmissionDirectory(pages,resources)) {
            long a=rows.intern(1,1,7,9,101,0);
            long b=rows.intern(2,1,7,9,102,1),c=rows.intern(1,2,7,9,103,2);
            long d=rows.intern(1,1,8,9,104,3),e=rows.intern(1,1,7,10,105,4);
            assertEquals(5,rows.size());assertNotEquals(a,b);assertNotEquals(a,c);assertNotEquals(a,d);assertNotEquals(a,e);
            assertEquals(a,rows.intern(1,1,7,9,999,999));assertEquals(101,rows.reference(a));assertEquals(0,rows.ordinal(a));
            assertEquals(0,rows.find(0,1,7,9));assertEquals(0,rows.find(1,1,7,11));
            assertEquals(b,rows.find(2,1,7,9));assertEquals(c,rows.find(1,2,7,9));assertEquals(d,rows.find(1,1,8,9));assertEquals(e,rows.find(1,1,7,10));
            assertEquals(5,rows.size());assertEquals(101,rows.reference(a));
        }zero(resources);
    }
    @Test void ordinalPayloadAndStateSpillWithoutResidentGrowthOrLookupInsertion() {
        var resources=resources();
        try(var pages=new FilePageStore(directory,512,4,resources,AnalysisResources.Phase.VALIDATION);
            var rows=new SourceAdmissionDirectory(pages,resources)) {
            long fixed=resources.heapUsed();
            for(int i=8191;i>=0;i--) {
                long row=rows.intern(1,i%5+1,Long.MAX_VALUE-i,Long.MIN_VALUE+i,10000L+i,i);
                rows.state(row,0,i);rows.state(row,1,i*2L);
            }
            assertEquals(8192,rows.size());assertEquals(fixed,resources.heapUsed());assertTrue(resources.peak(AnalysisResources.Pool.TEMPORARY)>32768);
            for(int i=0;i<8192;i++) {
                long row=rows.find(1,i%5+1,Long.MAX_VALUE-i,Long.MIN_VALUE+i);
                assertEquals(10000L+i,rows.reference(row));assertEquals(i,rows.ordinal(row));assertEquals(i,rows.state(row,0));assertEquals(i*2L,rows.state(row,1));
                assertEquals(0,rows.find(2,i%5+1,Long.MAX_VALUE-i,Long.MIN_VALUE+i));
            }
            assertEquals(8192,rows.size());assertEquals(fixed,resources.heapUsed());
            System.out.println("SOURCE_ADMISSION_DIRECTORY rows="+rows.size()+" fixed="+fixed+" temporary="+resources.peak(AnalysisResources.Pool.TEMPORARY)+" bytesRead="+pages.statistics().bytesRead()+" certificate=NOT_ISSUED");
        }zero(resources);
    }
    @Test void constructorDenialAndExplicitCloseReleaseOnlyOwnedPages() {
        var resources=resources();
        try(var pages=new FilePageStore(directory,512,4,resources,AnalysisResources.Phase.VALIDATION)) {
            long borrowed=pages.allocate();long before=pages.statistics().livePages();
            for(long remaining:new long[]{511,512+3072+2047,512+3072+2048+3071}) {
                try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-remaining,AnalysisResources.Phase.VALIDATION)) {
                    assertNotNull(pressure);var failure=assertThrows(AnalysisResources.Exhausted.class,()->new SourceAdmissionDirectory(pages,resources));
                    assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());
                    assertEquals(before,pages.statistics().livePages());
                }
            }
            var rows=new SourceAdmissionDirectory(pages,resources);long row=rows.intern(1,1,1,1,1,0);
            assertEquals(1,rows.reference(row));rows.close();rows.close();assertEquals(before,pages.statistics().livePages());
            assertThrows(IllegalStateException.class,rows::size);assertThrows(IllegalStateException.class,()->rows.find(1,1,1,1));
            pages.release(borrowed);
        }zero(resources);
    }
    @Test void primitiveFailurePoisonsTheDirectoryPreservingPrimaryFailureAndBorrowedBackend() {
        for(int operation=0;operation<3;operation++) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,512,4,resources,AnalysisResources.Phase.VALIDATION)) {
                long borrowed=pages.allocate();long before=pages.statistics().livePages();
                var failure=new PageStore.Failure(PageStore.Reason.IO,"independent admission directory interruption");
                var port=new FaultPages(pages,operation,failure);
                try(var rows=new SourceAdmissionDirectory(port,resources)) {
                    assertSame(failure,assertThrows(PageStore.Failure.class,()->rows.intern(1,1,1,1,1,0)));
                    assertEquals(before,pages.statistics().livePages());assertThrows(IllegalStateException.class,rows::size);assertFalse(port.closed);
                }
                pages.release(borrowed);long page=pages.allocate();pages.release(page);
            }zero(resources);
        }
    }
    @Test void nativeCommonOwnerExpiryImmediatelyRetiresAllDirectoryPagesAndState()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
            var pages=new FilePageStore(directory,512,4,resources,AnalysisResources.Phase.VALIDATION)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var execution=AnalysisResources.executionFor(program);
                try(var rows=new SourceAdmissionDirectory(pages,execution)) {
                    long row=rows.intern(1,1,1,1,1,0);rows.state(row,0,37);assertTrue(pages.statistics().livePages()>0);
                    program.close();assertEquals(0,pages.statistics().livePages());zero(execution);
                    assertThrows(IllegalStateException.class,rows::size);assertThrows(IllegalStateException.class,()->rows.state(row,0));
                }
            }finally{program.close();}
            long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final int operation;private final Failure primary;private boolean fired,closed;
        FaultPages(PageStore delegate,int operation,Failure primary){this.delegate=delegate;this.operation=operation;this.primary=primary;}
        private void fail(int candidate){if(candidate==operation&&!fired){fired=true;throw primary;}}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){fail(0);return delegate.allocate();}
        @Override public void read(long p,int offset,byte[] bytes,int at,int count){fail(1);delegate.read(p,offset,bytes,at,count);}
        @Override public void write(long p,int offset,byte[] bytes,int at,int count){fail(2);delegate.write(p,offset,bytes,at,count);}
        @Override public void release(long p){delegate.release(p);}
        @Override public void readForCleanup(long p,int offset,byte[] bytes,int at,int count){delegate.readForCleanup(p,offset,bytes,at,count);}
        @Override public void releaseForCleanup(long p){delegate.releaseForCleanup(p);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
