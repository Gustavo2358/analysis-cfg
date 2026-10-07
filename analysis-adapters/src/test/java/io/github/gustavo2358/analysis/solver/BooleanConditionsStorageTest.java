package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BooleanConditionsStorageTest {
    @TempDir Path directory;
    private static AnalysisResources memory(long heap){return new AnalysisResources(new AnalysisResources.Limits(heap,100_000,0,64_000_000,2,500_000_000,1_000_000));}
    @Test void deniedConditionPayloadAbortsEvenTerminalShortcutsAndReleasesOwnership() {
        var memory=memory(20000);
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
            var conditions=new BooleanConditions(2,memory,pages);
            assertThrows(AnalysisResources.Exhausted.class,()->{for(int key=0;key<1024;key++)conditions.variable(key);});
            assertThrows(IllegalStateException.class,()->conditions.or(0,1),"an interrupted owner cannot expose a stable shortcut");
            assertThrows(IllegalStateException.class,()->conditions.atEmpty(1));
            conditions.close();assertEquals(0,pages.statistics().livePages());long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void fixedComputedAndNodeCachesAreReservedBeforeTheirArraysAreCreated() {
        var memory=memory(8192);
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
            long before=memory.heapUsed();
            assertEquals(AnalysisResources.Resource.HEAP,assertThrows(AnalysisResources.Exhausted.class,
                    ()->new BooleanConditions(65536,memory,pages)).resource());
            assertEquals(before,memory.heapUsed());assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void literalJunctionsGeneralDecisionsAndRootLifetimesAgreeWithOnePageSpill() {
        var memory=memory(1_000_000);
        try(var pages=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
            try(var resident=new BooleanConditions(2);var spilled=new BooleanConditions(2,memory,pages)) {
                var a=new ArrayList<Integer>();var c=new ArrayList<Integer>();var truths=new ArrayList<Long>();
                for(int v=0;v<6;v++) {
                    a.add(resident.variable(v));c.add(spilled.variable(v));long mask=0;
                    for(int bits=0;bits<64;bits++)if((bits&(1<<v))!=0)mask|=1L<<bits;truths.add(mask);
                }
                var random=new Random(374995);
                for(int step=0;step<36;step++) {
                    int left=random.nextInt(a.size()),right=random.nextInt(a.size());boolean union=random.nextBoolean();
                    int x=union?resident.or(a.get(left),a.get(right)):resident.and(a.get(left),a.get(right));
                    int y=union?spilled.or(c.get(left),c.get(right)):spilled.and(c.get(left),c.get(right));
                    long truth=union?truths.get(left)|truths.get(right):truths.get(left)&truths.get(right);
                    if(random.nextBoolean()){x=resident.not(x);y=spilled.not(y);truth=~truth;}
                    a.add(x);c.add(y);truths.add(truth);
                    for(int bits=0;bits<64;bits++) {
                        var word=BitSet.valueOf(new long[]{bits});boolean expected=((truth>>>bits)&1)!=0;
                        assertEquals(expected,resident.test(x,word));assertEquals(expected,spilled.test(y,word));
                    }
                    int cp=resident.checkpoint(),sp=spilled.checkpoint();
                    int temporary=spilled.or(spilled.and(y,spilled.variable(99)),spilled.not(y));
                    assertEquals(spilled.atEmpty(temporary),resident.atEmpty(resident.or(resident.and(x,resident.variable(99)),resident.not(x))));
                    resident.discardAfter(cp);spilled.discardAfter(sp);
                    if(step%8==0){resident.collect(mark->{for(int root:a)mark.accept(root);});spilled.collect(mark->{for(int root:c)mark.accept(root);});}
                }
                for(int i=0;i<a.size();i++)for(int j=0;j<i;j++)assertEquals(a.get(i).equals(a.get(j)),c.get(i).equals(c.get(j)),"canonical truth classes agree across backends");
                spilled.collect(mark->{});assertEquals(2,spilled.retainedNodes());
                int rebuilt=spilled.or(spilled.variable(0),spilled.not(spilled.variable(1)));
                for(int bits=0;bits<4;bits++)assertEquals((bits&1)!=0||(bits&2)==0,spilled.test(rebuilt,BitSet.valueOf(new long[]{bits})));
                assertTrue(pages.statistics().evictions()>100,"must really spill literals, tokens and canonical indices");
            }
            assertEquals(0,pages.statistics().livePages());long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
    }
    @Test void deniedLiteralAllocationReleasesOwnerAndLeavesBorrowedBackendUsable() {
        for(long quota:new long[]{5000,6000,8000,12000,16000}) {
            var memory=memory(quota);
            try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var conditions=new BooleanConditions(2,memory,pages);boolean denied=false;
                try {
                    int conjunction=1;for(int key=0;key<256;key++)conjunction=conditions.and(conjunction,conditions.variable(key));
                }catch(AnalysisResources.Exhausted expected){denied=true;}
                finally {try{conditions.close();}catch(AnalysisResources.Exhausted expected){denied=true;}}
                assertTrue(denied,"quota must deny growth="+quota);assertEquals(0,pages.statistics().livePages());
                long page=pages.allocate();pages.release(page);
            }
            assertEquals(0,memory.heapUsed(),"quota="+quota);
        }
    }
    @Test void interruptedReadsAbortTheWholeConditionOwnerIncludingTerminalQueries() {
        for(int operation=0;operation<5;operation++) {
            var memory=memory(1000000);
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new ReadInterruptedPages(backend);
                try(var conditions=new BooleanConditions(2,memory,pages);
                    var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
                    var assignment=new PersistentLongMap(arena,memory,AnalysisResources.Phase.CONTROL)) {
                int root=1;for(int key=0;key<128;key++)root=conditions.and(root,conditions.variable(key));
                pages.interrupt=true;
                int condition=root,read=operation;
                assertThrows(PageStore.Failure.class,()->{
                    if(read==0)conditions.test(condition,new BitSet());
                    else if(read==1)conditions.atEmpty(condition);
                    else if(read==2)conditions.requiredPresent(condition);
                    else if(read==3)conditions.possiblePresent(condition);
                    else conditions.test(condition,assignment,0);
                });
                assertThrows(IllegalStateException.class,()->conditions.test(1,new BitSet()),"operation="+operation);
                assertThrows(IllegalStateException.class,()->conditions.or(0,1),"operation="+operation);
                pages.interrupt=false;
                }
                assertEquals(0,backend.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    private static final class ReadInterruptedPages implements PageStore {
        final PageStore delegate;boolean interrupt;
        ReadInterruptedPages(PageStore delegate){this.delegate=delegate;}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){
            if(interrupt){interrupt=false;throw new Failure(Reason.IO,"synthetic condition read interruption");}
            delegate.read(page,offset,target,start,length);
        }
        public void write(long page,int offset,byte[] source,int start,int length){delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
}
