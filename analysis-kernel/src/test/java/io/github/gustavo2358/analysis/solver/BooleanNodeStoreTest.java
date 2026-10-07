package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanNodeStoreTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(4_000_000,100_000,0,0,0,500_000_000,0));}
    @Test void provedRepresentativeReplacementPreservesIdAndMarksButRetiresTheOldStructuralKey() {
        var memory=resources();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var nodes=new BooleanNodeStore(pages,memory,1)) {
            int id=nodes.create(-1,17,23,0,3,0,false);nodes.mark(id,731);
            assertEquals(0,nodes.replace(id,91,0,0,194,2,833));
            assertEquals(-1,nodes.find(-1,17,23,0,3));assertEquals(id,nodes.find(91,0,0,194,2));
            assertEquals(731,nodes.mark(id));assertEquals(833,nodes.token(id));assertEquals(3,nodes.retainedNodes());
            int other=nodes.create(92,0,1,0,0,0,false);
            assertThrows(IllegalArgumentException.class,()->nodes.replace(id,92,0,1,0,0,0));
            assertEquals(id,nodes.find(91,0,0,194,2));assertEquals(other,nodes.find(92,0,1,0,0));
            assertEquals(833,nodes.replace(id,93,1,0,0,0,0));assertEquals(731,nodes.mark(id));
            nodes.retire(id);int reused=nodes.create(94,0,1,0,0,0,false);assertEquals(id,reused);
            assertEquals(-1,nodes.find(93,1,0,0,0));
        }
        assertEquals(0,memory.heapUsed());
    }

    @Test void interruptedRepresentativeChangesAbortAndCloseWithoutIntactUniqueLinks() {
        for(int boundary:new int[]{1,2,4,16,64,256,1024}) {
            var memory=resources();
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptiblePages(backend);var nodes=new BooleanNodeStore(pages,memory,1);
                int[] ids=new int[64];for(int i=0;i<ids.length;i++)ids[i]=nodes.create(-1,i+100,i+200,0,3,0,false);
                pages.remaining=boundary;
                assertThrows(PageStore.Failure.class,()->{for(int i=0;i<ids.length;i++)nodes.replace(ids[i],i,0,1,0,0,0);});
                assertThrows(IllegalStateException.class,()->nodes.find(0,0,1,0,0));pages.remaining=-1;nodes.close();
                assertEquals(0,backend.statistics().livePages());long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed());
        }
    }

    @Test void constructorAndGrowthDenialReleaseEveryOwnedPage() {
        for(long quota:new long[]{512,4096,8192,12000,20000,50000}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(quota,100000,0,0,0,500000000,0));
            try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                BooleanNodeStore nodes=null;boolean denied=false;
                try {
                    nodes=new BooleanNodeStore(pages,memory,1);
                    for(int key=0;key<1024;key++)nodes.create(key,0,1,0,0,0,false);
                }catch(AnalysisResources.Exhausted expected){denied=true;}
                finally {if(nodes!=null)nodes.close();}
                assertTrue(denied,"must deny constructor or actual growth at quota="+quota);
                assertEquals(0,pages.statistics().livePages(),"quota="+quota);
                if(quota>=8192){long page=pages.allocate();pages.release(page);}
            }
            assertEquals(0,memory.heapUsed(),"quota="+quota);
        }
    }
    @Test void interruptedWritesAbortCatalogAndCanBeClosedWithoutClosingBorrowedStore() {
        int[] boundaries=new int[55];for(int i=0;i<48;i++)boundaries[i]=i+1;
        for(int i=48,value=64;i<boundaries.length;i++,value*=2)boundaries[i]=value;
        for(int boundary:boundaries) {
            var memory=resources();
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptiblePages(backend);
                var nodes=new BooleanNodeStore(pages,memory,1);
                nodes.create(0,0,1,0,0,0,false);pages.remaining=boundary;
                try {
                    for(int key=1;key<128;key++)nodes.create(key,0,1,0,0,0,false);
                    fail("injection did not interrupt boundary="+boundary);
                }catch(PageStore.Failure expected){assertEquals(PageStore.Reason.IO,expected.reason());}
                assertThrows(IllegalStateException.class,()->nodes.find(0,0,1,0,0));
                pages.remaining=-1;nodes.close();nodes.close();
                assertEquals(0,backend.statistics().livePages(),"boundary="+boundary);long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed(),"boundary="+boundary);
        }
    }
    @Test void interruptedRetirementMergesAndTrimDoNotDependOnValidTreeLinksForCleanup() {
        for(int boundary:new int[]{1,2,3,4,8,16,32,64,128,256,512,1024,2048,4096}) {
            var memory=resources();
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptiblePages(backend);var nodes=new BooleanNodeStore(pages,memory,1);
                int[] ids=new int[64];for(int key=0;key<ids.length;key++)ids[key]=nodes.create(key,0,1,0,0,0,false);
                pages.remaining=boundary;
                try {
                    for(int id:ids)nodes.retire(id);nodes.trim();
                    fail("injection did not interrupt retirement boundary="+boundary);
                }catch(PageStore.Failure expected){assertEquals(PageStore.Reason.IO,expected.reason());}
                assertThrows(IllegalStateException.class,()->nodes.find(0,0,1,0,0));
                pages.remaining=-1;nodes.close();
                assertEquals(0,backend.statistics().livePages(),"retirement boundary="+boundary);
                long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed(),"retirement boundary="+boundary);
        }
    }
    private static final class InterruptiblePages implements PageStore {
        final PageStore delegate;int remaining=-1;
        InterruptiblePages(PageStore delegate){this.delegate=delegate;}
        void step(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic page interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){step();return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){step();delegate.read(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){step();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
    @Test void completePrimitiveKeysAndMetadataMatchIndependentCanonicalMaps() {
        var memory=resources();var expected=new HashMap<List<Long>,Integer>();var random=new Random(365201);
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var nodes=new BooleanNodeStore(pages,memory,2)) {
            for(int step=0;step<600;step++) {
                int variable=random.nextInt(20),low=random.nextInt(2),high=1-low,kind=random.nextInt(3);long literals=kind==0?0:random.nextInt(20)+2;
                var key=List.of((long)variable,(long)low,(long)high,literals,(long)kind);
                int found=nodes.find(variable,low,high,literals,kind);
                assertEquals(expected.getOrDefault(key,-1).intValue(),found);
                if(found<0){found=nodes.create(variable,low,high,literals,kind,step+1,true);expected.put(key,found);}
                assertEquals(variable,nodes.variable(found));assertEquals(low,nodes.low(found));assertEquals(high,nodes.high(found));
                assertEquals(literals,nodes.literals(found));assertEquals(kind,nodes.junction(found));
                nodes.mark(found,step+1);assertEquals(step+1,nodes.mark(found));
                assertEquals(found,nodes.find(variable,low,high,literals,kind));
            }
            assertEquals(expected.size()+2,nodes.retainedNodes());
            var ids=new ArrayList<>(expected.values());Collections.shuffle(ids,random);
            for(int id:ids){assertTrue(nodes.live(id));assertTrue(nodes.token(id)>0);nodes.retire(id);assertFalse(nodes.live(id));}
            assertEquals(2,nodes.retainedNodes());
            int id=nodes.create(999,0,1,0,0,0,false);assertTrue(id>=2);assertEquals(id,nodes.find(999,0,1,0,0));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void appendOnlyScopesAndFreeLinksDoNotReuseOlderRetiredSlots() {
        var memory=resources();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var nodes=new BooleanNodeStore(pages,memory,1)) {
            int first=nodes.create(0,0,1,0,0,0,false),second=nodes.create(1,0,1,0,0,0,false);
            nodes.retire(first);int floor=nodes.size();
            int append=nodes.create(2,0,1,0,0,0,true);assertTrue(append>=floor);assertEquals(1,nodes.variable(second));
            nodes.retire(append);nodes.trim();assertEquals(floor,nodes.size());
            int reused=nodes.create(3,0,1,0,0,0,false);assertEquals(first,reused);assertEquals(3,nodes.variable(reused));
            assertEquals(-1,nodes.find(0,0,1,0,0));assertEquals(-1,nodes.find(2,0,1,0,0));
            assertEquals(reused,nodes.find(3,0,1,0,0));
        }
        assertEquals(0,memory.heapUsed());
    }
}
