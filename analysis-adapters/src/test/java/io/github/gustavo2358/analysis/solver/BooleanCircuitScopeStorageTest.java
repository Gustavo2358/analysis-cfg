package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BooleanCircuitScopeStorageTest {
    @TempDir Path directory;
    /** Odd handles are primary keys; even handles extend a conjunction prefix.
     * The high range contains exact aliases, without a resident graph fixture. */
    private static final BooleanCircuitView PREFIX=new BooleanCircuitView(){
        public long normalize(long root){return root;}
        public int primary(long handle){return handle<(1L<<20)&&(handle&1)!=0?(int)(handle>>>1):-1;}
        public long left(long handle){return handle>=(1L<<20)?4*(handle-(1L<<20)+1):handle==2?1:(handle-2)<<1;}
        public long right(long handle){return handle>=(1L<<20)?1:(handle-1)<<1;}
    };
    @Test void retainedScopedDefinitionsSpillWithConstantResidentCapacityAndOnePageEviction(){
        long peak=-1,previousDisk=0;
        for(int count:new int[]{16,64,256}){
            var memory=new AnalysisResources(new AnalysisResources.Limits(40000,0,0,64000000,1,500000000,0));
            try(var pages=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)){
                try(var decisions=new BooleanCircuitDecisions(pages,memory,PREFIX)){
                    decisions.beginScope();
                    for(int key=0;key<count;key++)assertTrue(decisions.equivalent(4L*(key+1),((1L<<20)+key)<<1));
                    if(peak<0)peak=memory.heapPeak();else assertEquals(peak,memory.heapPeak());
                    long disk=memory.used(AnalysisResources.Pool.TEMPORARY);assertTrue(disk>previousDisk);previousDisk=disk;
                    decisions.endScope();assertEquals(0,pages.statistics().livePages());assertTrue(pages.statistics().evictions()>100);
                }
                long page=pages.allocate();pages.release(page);
            }
            assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
        }
    }
    @Test void interruptedInitialAndExtendedScopesAbortAndReleaseEveryBorrowedPage(){
        int denied=0;
        for(boolean extended:new boolean[]{false,true})for(int boundary=1;boundary<=48;boundary++){
            var memory=new AnalysisResources(new AnalysisResources.Limits(1000000,10000,0,0,0,10000000,0));
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)){
                var pages=new InterruptedPages(backend);
                try(var decisions=new BooleanCircuitDecisions(pages,memory,PREFIX)){
                    decisions.beginScope();if(extended)assertTrue(decisions.equivalent(4,1L<<21));
                    pages.remaining=boundary;
                    try{assertTrue(decisions.equivalent(8,(1L<<21)+2));}
                    catch(PageStore.Failure expected){denied++;assertEquals(PageStore.Reason.IO,expected.reason());assertThrows(IllegalStateException.class,()->decisions.satisfiable(1));}
                    pages.remaining=-1;decisions.endScope();assertEquals(0,backend.statistics().livePages());
                }
                assertEquals(0,backend.statistics().livePages());long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed());
        }
        assertEquals(96,denied,"all injected initial/extension operations must be reached");
    }
    private static final class InterruptedPages implements PageStore{
        final PageStore delegate;int remaining=-1;
        InterruptedPages(PageStore delegate){this.delegate=delegate;}
        void interrupt(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic scoped formula interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){interrupt();return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){interrupt();delegate.read(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){interrupt();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
}
