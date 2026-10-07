package io.github.gustavo2358.analysis.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagedLongArrayOwnershipTest {
    @Test void interruptedPruningAndRootCollapseNeverLeaveReleasedPagesInTheOwnershipTree() {
        int failures=0;
        for(boolean neighbor:new boolean[]{false,true})for(int boundary=1;boundary<=64;boundary++) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(100000,10000,0,0,0,1000000,0));
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptedPages(backend);
                var array=new PagedLongArray(pages,Long.MAX_VALUE,memory,AnalysisResources.Phase.CONTROL);
                long high=1L<<40;array.set(high,91);if(neighbor)array.set(0,17);
                pages.remaining=boundary;
                try{array.set(high,0);}catch(PageStore.Failure expected){failures++;assertThrows(IllegalStateException.class,()->array.get(0));}
                pages.remaining=-1;array.close();array.close();assertEquals(0,backend.statistics().livePages(),"neighbor="+neighbor+" boundary="+boundary);
                long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed());
        }
        assertTrue(failures>=64,"must interrupt parent unlink, count updates and root compression");
    }
    @Test void indexOwnershipIsDenseEvenWhenBorrowedPageHandlesAreWidelyScattered() {
        var memory=new AnalysisResources(new AnalysisResources.Limits(100000,10000,0,0,0,10000000,0));
        try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var index=new PagedLongIndex(new ScatteredPages(backend),memory,AnalysisResources.Phase.CONTROL)) {
            for(int key=0;key<128;key++)assertEquals(key+1,index.intern(key,key+1));
            for(int key=127;key>=0;key--)assertEquals(key+1,index.find(key));
            for(int key=0;key<128;key++)assertTrue(index.remove(key));assertEquals(0,index.size());
        }
        assertEquals(0,memory.heapUsed());
    }
    /** Bijective renaming modulo2^63; no slot/generation encoding is promised by PageStore. */
    private static final class ScatteredPages implements PageStore {
        static final long FACTOR=0x1e3779b97f4a7c15L;
        static final long INVERSE=java.math.BigInteger.valueOf(FACTOR).modInverse(java.math.BigInteger.ONE.shiftLeft(63)).longValueExact();
        final PageStore delegate;
        ScatteredPages(PageStore delegate){this.delegate=delegate;}
        long original(long page){return (page*INVERSE)&Long.MAX_VALUE;}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){return (delegate.allocate()*FACTOR)&Long.MAX_VALUE;}
        public void read(long page,int offset,byte[] target,int start,int length){delegate.read(original(page),offset,target,start,length);}
        public void readForCleanup(long page,int offset,byte[] target,int start,int length){delegate.readForCleanup(original(page),offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){delegate.write(original(page),offset,source,start,length);}
        public void release(long page){delegate.release(original(page));}
        public void releaseForCleanup(long page){delegate.releaseForCleanup(original(page));}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
    private static final class InterruptedPages implements PageStore {
        final PageStore delegate;int remaining=-1;
        InterruptedPages(PageStore delegate){this.delegate=delegate;}
        void step(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic radix retirement interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){step();delegate.read(page,offset,target,start,length);}
        public void readForCleanup(long page,int offset,byte[] target,int start,int length){step();delegate.readForCleanup(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){step();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
}
