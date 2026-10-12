package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Independent duplicate occurrence/degree laws and exact nominal residuals. */
final class PagedSnapshotCycleStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work) {
        return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));
    }
    private static long key(int n){return (1L<<44)+37L*n;}
    @Test void reverseOccurrencesAndDenseQueueSpillWithExactRepeatedDegrees() {
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)) {
            assertRelation(memory,memoryResources);assertRelation(file,fileResources);
            assertTrue(fileResources.heapPeak()<=65536);assertTrue(file.statistics().evictions()>1000);
        }
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertRelation(PageStore pages,AnalysisResources resources) {
        long initial=resources.heapUsed();int size=4096;
        try(var store=new PagedSnapshotCycleStorage(pages,resources)) {
            assertThrows(NoSuchElementException.class,store::node);assertThrows(NoSuchElementException.class,store::child);
            for(int n=0;n<size;n++)store.define(key(n));
            long cataloguePages=pages.statistics().livePages();
            for(int n=1;n<size;n++){store.link(key(n-1),key(n));store.link(key(n-1),key(n));}
            assertEquals(0,store.degree(key(0)));
            for(int n=1;n<size;n++)assertEquals(2,store.degree(key(n)));
            if(pages instanceof FilePageStore)assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY)>65536);
            store.enqueue(key(0));int visited=0;long edges=0;
            while(store.advance()) {
                assertEquals(key(visited++),store.node());store.children(store.node());
                while(store.advanceChild()){long child=store.child();edges++;if(store.decrement(child)==0)store.enqueue(child);}
                assertThrows(NoSuchElementException.class,store::child);
            }
            assertEquals(size,visited);assertEquals(2L*(size-1),edges);assertFalse(store.advance());
            assertThrows(NoSuchElementException.class,store::node);
            for(int n=0;n<size;n++)assertEquals(0,store.degree(key(n)));
            assertEquals(cataloguePages,pages.statistics().livePages());
            long emptyPages=pages.statistics().livePages(),emptyTemporary=resources.used(AnalysisResources.Pool.TEMPORARY);
            // Consumed FIFO/adjacency leaves are reclaimed; epochs cannot retain queue history.
            for(int epoch=0;epoch<64;epoch++) {
                store.enqueue(key(0));assertTrue(store.advance());assertEquals(key(0),store.node());store.children(key(0));assertFalse(store.advanceChild());assertFalse(store.advance());
                assertEquals(emptyPages,pages.statistics().livePages());assertEquals(emptyTemporary,resources.used(AnalysisResources.Pool.TEMPORARY));
            }
            if(pages instanceof FilePageStore)System.out.println("SNAPSHOT_CYCLE_STORAGE_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" nodes="+size+" edges="+edges+" epochs=64");
        }
        assertEquals(0,pages.statistics().livePages());
        if(pages instanceof FilePageStore)assertEquals(initial,resources.heapUsed());
        long borrowed=pages.allocate();pages.release(borrowed);
    }
    @Test void completeOriginResidualsMatchAcrossResidentAndPagedInput() {
        Publication p=PagedAirStorageTest.publication("synthetic nominal relation");
        Ids.OriginId a=new Ids.OriginId(p.id(),"a"),b=new Ids.OriginId(p.id(),"b"),tail=new Ids.OriginId(p.id(),"tail"),multi=new Ids.OriginId(p.id(),"multi"),absent=new Ids.OriginId(p.id(),"absent");
        var origins=new ArrayList<Origins.Origin>(p.origins());
        origins.add(new Origins.Derived(a,List.of(b,b),"r"));origins.add(new Origins.Derived(b,List.of(a),"r"));origins.add(new Origins.Derived(tail,List.of(a),"r"));
        origins.add(new Origins.Derived(multi,List.of(p.origins().get(0).id(),a),"r"));
        origins.add(new Origins.Derived(new Ids.OriginId(p.id(),"dangling"),List.of(absent),"r"));
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),origins,p.coverage(),p.uncertainties(),p.premises());
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(131072,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)) {
            assertResiduals(p,memory,memoryResources,false);assertResiduals(p,file,fileResources,true);
        }
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertResiduals(Publication p,PageStore pages,AnalysisResources resources,boolean paged) {
        try(var resident=AirSnapshot.fromPublication(p);var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(resident,resident.root(),null,builder);
            try(var managed=builder.finish(root)) {
                AirSnapshot input=paged?managed:resident;
                try(var keys=new SnapshotIdentityKeys(input,new PagedSnapshotIdentityStorage(pages,resources));
                    var declarations=SnapshotDeclarations.build(input,keys,new PagedSnapshotDeclarationsStorage(pages,resources),1000,1000,(r,i,n)->{throw new AssertionError(r);})) {
                    var subjects=new ArrayList<Long>();
                    var counts=SnapshotNominalCycles.scan(input,keys,declarations,new PagedSnapshotCycleStorage(pages,resources),(rule,id,node)->{assertEquals(SnapshotNominalCycles.Rule.ORIGIN,rule);subjects.add(keys.key(id));});
                    assertEquals(4,counts.residual());assertEquals(6,counts.edges());
                    long originList=input.field(input.root(),AirShape.PUBLICATION,8);
                    int baseline=p.origins().size()-5;
                    for(int n=0;n<4;n++){long origin=input.element(originList,AirShape.ORIGINS_DERIVED,baseline+n);assertEquals(keys.key(input.field(origin,AirShape.ORIGINS_DERIVED,0)),subjects.get(n));}
                    assertEquals(AirShape.PUBLICATION,input.shape(input.root()));assertTrue(declarations.entities()>counts.nodes());
                    if(pages instanceof FilePageStore)System.out.println("SNAPSHOT_CYCLE_INPUT_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" nodes="+counts.nodes()+" edges="+counts.edges());
                }
            }
        }
        assertEquals(0,pages.statistics().livePages());
    }
    @Test void deniedConstructionAndInterruptedMutationPreserveBorrowedStore() {
        for(long heap:new long[]{1000,4000,7000,10000,13000}) {
            var resources=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,resources)) {
                long initial=resources.heapUsed();assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotCycleStorage(pages,resources)).phase());
                assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,resources.heapUsed());
        }
        var measured=resources(65536,1_000_000);
        long calls;
        try(var pages=new MemoryPageStore(128,measured);var store=new PagedSnapshotCycleStorage(pages,measured)) {
            workSequence(store);calls=measured.workUsed();
        }
        // Every primitive interruption within definition/link/FIFO/cursor/decrement must abort.
        for(long budget=0;budget<calls;budget++) {
            var resources=resources(65536,budget);
            try(var pages=new MemoryPageStore(128,resources)) {
                var store=new PagedSnapshotCycleStorage(pages,resources);
                try {
                    assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->workSequence(store)).resource());
                    assertThrows(IllegalStateException.class,()->store.degree(key(0)));
                } finally{store.close();}
                assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    private static void workSequence(PagedSnapshotCycleStorage store) {
        store.define(key(0));store.define(key(1));store.link(key(0),key(1));store.enqueue(key(0));
        assertTrue(store.advance());store.children(store.node());assertTrue(store.advanceChild());assertEquals(0,store.decrement(store.child()));assertFalse(store.advanceChild());assertFalse(store.advance());
    }
    @Test void invalidProtocolNeverChangesCompleteKeyAssociationOrUnderflowsDegree() {
        var resources=resources(65536,1_000_000);
        try(var pages=new MemoryPageStore(128,resources);var store=new PagedSnapshotCycleStorage(pages,resources)) {
            assertThrows(IllegalArgumentException.class,()->store.define(0));store.define(key(0));
            assertThrows(IllegalStateException.class,()->store.define(key(0)));
            // An operational/protocol failure poisons this transferred owner.
            assertThrows(IllegalStateException.class,()->store.degree(key(0)));
        }
        assertEquals(0,resources.heapUsed());
        var underflow=resources(65536,1_000_000);
        try(var pages=new MemoryPageStore(128,underflow);var store=new PagedSnapshotCycleStorage(pages,underflow)) {
            store.define(key(0));assertThrows(IllegalStateException.class,()->store.decrement(key(0)));assertThrows(IllegalStateException.class,store::advance);
        }
        assertEquals(0,underflow.heapUsed());
    }
}
