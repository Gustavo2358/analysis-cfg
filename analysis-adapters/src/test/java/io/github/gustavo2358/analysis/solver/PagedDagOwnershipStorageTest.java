package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PagedDagOwnershipStorageTest {
    @TempDir Path directory;
    private static final PagedDagOwnership.Graph CHAIN=new PagedDagOwnership.Graph(){
        public void children(long node,LongConsumer accept){if(node>2)accept.accept(node-1);}
        public void retire(long node){}
    };
    @Test void rootsEdgesAndConstructionJournalSpillWithOneResidentPage(){
        long peak=-1,previousDisk=0;
        for(int count:new int[]{64,256,1024}){
            var resources=new AnalysisResources(new AnalysisResources.Limits(40000,0,0,64000000,1,500000000,0));
            try(var pages=new FilePageStore(directory,128,1,resources,AnalysisResources.Phase.CONTROL)){
                try(var owner=new PagedDagOwnership(pages,resources,AnalysisResources.Phase.CONTROL,CHAIN)){
                    for(long node=2;node<count+2;node++)owner.created(node);
                    long root=owner.root(count+1);owner.commitCreated();assertEquals(count+1,owner.value(root));
                    if(peak<0)peak=resources.heapPeak();else assertEquals(peak,resources.heapPeak());
                    long disk=resources.used(AnalysisResources.Pool.TEMPORARY);assertTrue(disk>previousDisk);previousDisk=disk;
                    owner.closeRoot(root);
                }
                assertEquals(0,pages.statistics().livePages());assertTrue(pages.statistics().evictions()>100);
                long page=pages.allocate();pages.release(page);
            }
            assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
            assertEquals(0,resources.used(AnalysisResources.Pool.OPEN_FILES));
        }
    }
    @Test void interruptedDeclarationAndRootReplacementAbortAndReleaseBorrowedPages(){
        int denied=0;
        for(boolean replacement:new boolean[]{false,true})for(int boundary=1;boundary<=32;boundary++){
            var resources=new AnalysisResources(new AnalysisResources.Limits(1000000,10000,0,0,0,10000000,0));
            try(var backend=new ResidentPageStore(128,resources,AnalysisResources.Phase.CONTROL)){
                var pages=new InterruptedPages(backend);
                try(var owner=new PagedDagOwnership(pages,resources,AnalysisResources.Phase.CONTROL,CHAIN)){
                    owner.created(2);long root=owner.root(2);owner.commitCreated();
                    pages.remaining=boundary;
                    try{owner.created(3);if(replacement){owner.bind(root,3);owner.commitCreated();}}
                    catch(PageStore.Failure expected){
                        denied++;assertEquals(PageStore.Reason.IO,expected.reason());
                        assertThrows(IllegalStateException.class,()->owner.value(root));
                        assertThrows(IllegalStateException.class,()->owner.root(1));
                    }
                    pages.remaining=-1;
                }
                assertEquals(0,backend.statistics().livePages());long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,resources.heapUsed());
        }
        assertEquals(64,denied,"all declared interruption boundaries must execute");
    }
    @Test void managedConditionVersionsUseOnePageAndReleaseEveryBorrowedPayload(){
        long peak=-1,previousDisk=0;
        for(int count:new int[]{16,64,256}){
            var resources=new AnalysisResources(new AnalysisResources.Limits(256000,0,0,64000000,1,500000000,0));
            try(var pages=new FilePageStore(directory,128,1,resources,AnalysisResources.Phase.CONTROL)){
                try(var conditions=new BooleanConditions(8,resources,pages)){
                    conditions.enableOwnership();int a=conditions.variable(0),b=conditions.variable(1);
                    long ar=conditions.retainRoot(a),br=conditions.retainRoot(b),root=conditions.retainRoot(0);conditions.publishCreated();
                    for(int key=2;key<count+2;key++){
                        conditions.beginMutation();
                        try{
                            int value=conditions.or(conditions.and(a,b),conditions.and(conditions.not(a),conditions.variable(key)));
                            conditions.bindRoot(root,value);
                        }finally{conditions.endMutation();}
                        var bits=new java.util.BitSet();bits.set(key);assertTrue(conditions.test((int)conditions.rootValue(root),bits));
                        assertTrue(conditions.retainedNodes()<=12);
                    }
                    if(peak<0)peak=resources.heapPeak();else assertEquals(peak,resources.heapPeak());
                    long disk=resources.used(AnalysisResources.Pool.TEMPORARY);assertTrue(disk>previousDisk);previousDisk=disk;
                    conditions.releaseRoot(root);conditions.releaseRoot(ar);conditions.releaseRoot(br);assertEquals(2,conditions.retainedNodes());
                }
                assertEquals(0,pages.statistics().livePages());long page=pages.allocate();pages.release(page);
            }
            assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
            assertEquals(0,resources.used(AnalysisResources.Pool.OPEN_FILES));
        }
    }
    /** 60KB cannot admit even the fixed ownership buffers. Preserve that
     * attempted envelope as an operational-denial law, not a growth curve. */
    @Test void insufficientFixedManagerCapacityAbortsAndReleasesItsPartialHandoff(){
        var resources=new AnalysisResources(new AnalysisResources.Limits(60000,0,0,64000000,1,500000000,0));
        try(var pages=new FilePageStore(directory,128,1,resources,AnalysisResources.Phase.CONTROL)){
            try(var conditions=new BooleanConditions(8,resources,pages)){
                var failure=assertThrows(AnalysisResources.Exhausted.class,conditions::enableOwnership);
                assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(60000,failure.limit());
                assertThrows(IllegalStateException.class,()->conditions.or(0,1));
            }
            assertEquals(0,pages.statistics().livePages());long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void interruptedManagedHandoffAndCreationAbortTerminalShortcuts(){
        int denied=0;
        for(boolean handoff:new boolean[]{false,true})for(int boundary=1;boundary<=32;boundary++){
            var resources=new AnalysisResources(new AnalysisResources.Limits(2000000,100000,0,0,0,100000000,0));
            try(var backend=new ResidentPageStore(128,resources,AnalysisResources.Phase.CONTROL)){
                var pages=new InterruptedPages(backend);
                try(var conditions=new BooleanConditions(8,resources,pages)){
                    int a=conditions.variable(0),b=conditions.variable(1);
                    if(!handoff){conditions.enableOwnership();conditions.retainRoot(a);conditions.retainRoot(b);conditions.publishCreated();}
                    pages.remaining=boundary;
                    try{
                        if(handoff)conditions.enableOwnership();
                        else conditions.or(conditions.and(a,b),conditions.and(conditions.not(a),conditions.variable(2)));
                    }catch(PageStore.Failure expected){
                        denied++;assertEquals(PageStore.Reason.IO,expected.reason());
                        assertThrows(IllegalStateException.class,()->conditions.or(0,1));
                        assertThrows(IllegalStateException.class,()->conditions.atEmpty(1));
                    }
                    pages.remaining=-1;
                }
                assertEquals(0,backend.statistics().livePages());long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,resources.heapUsed());
        }
        assertEquals(64,denied,"every managed interruption boundary must execute");
    }
    @Test void interruptedLiteralCollectionAfterPublicationAbortsTheWholeManagedOwner() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(2000000,100000,0,0,0,100000000,0));
        try(var backend=new ResidentPageStore(128,resources,AnalysisResources.Phase.CONTROL)){
            var pages=new InterruptedPages(backend);
            try(var conditions=new BooleanConditions(8,resources,pages)){
                conditions.enableOwnership();conditions.retainRoot(conditions.and(conditions.variable(0),conditions.variable(1)));conditions.publishCreated();
                // The construction journal is empty. Inject into the distinct
                // literal-arena collection path without creating 65K test rows.
                var threshold=BooleanConditions.class.getDeclaredField("literalCollectionThreshold");threshold.setAccessible(true);threshold.setLong(conditions,0);
                pages.remaining=1;
                assertThrows(PageStore.Failure.class,conditions::publishCreated);
                assertThrows(IllegalStateException.class,()->conditions.or(0,1));
                assertThrows(IllegalStateException.class,()->conditions.atEmpty(1));pages.remaining=-1;
            }
            assertEquals(0,backend.statistics().livePages());long page=backend.allocate();backend.release(page);
        }
        assertEquals(0,resources.heapUsed());
    }
    private static final class InterruptedPages implements PageStore {
        private final PageStore delegate;int remaining=-1;
        InterruptedPages(PageStore delegate){this.delegate=delegate;}
        private void interrupt(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic ownership interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){interrupt();return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){interrupt();delegate.read(page,offset,target,start,length);}
        public void readForCleanup(long page,int offset,byte[] target,int start,int length){interrupt();delegate.readForCleanup(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){interrupt();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
}
