package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Independent Boolean closure/repeated occurrence and ownership laws, not full AIR admission. */
final class PagedSnapshotGroundingStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work){return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));}
    private static long node(int n){return (1L<<44)+37L*n;}
    @Test void seededAndUnseededColdCyclesFreezeToOnlyReusableTruthFacts() {
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)) {
            for(boolean seeded:new boolean[]{false,true}){assertClosure(memory,memoryResources,seeded);assertClosure(file,fileResources,seeded);}
            assertTrue(fileResources.heapPeak()<=65536);assertTrue(file.statistics().evictions()>1000);
        }
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertClosure(PageStore pages,AnalysisResources resources,boolean seeded) {
        long initial=resources.heapUsed();int size=4096;
        try(var store=new PagedSnapshotGroundingStorage(pages,resources)) {
            assertThrows(NoSuchElementException.class,store::node);assertThrows(NoSuchElementException.class,store::dependent);
            for(int n=0;n<size;n++){assertTrue(store.expand(node(n)));assertFalse(store.expand(node(n)));}
            long truthPages=pages.statistics().livePages();
            if(seeded){assertTrue(store.prove(node(0)));assertFalse(store.prove(node(0)));store.enqueue(node(0));}
            // A collected seed cannot seal topology or propagate before later edges exist.
            for(int n=0;n<size;n++){store.link(node((n+1)%size),node(n));store.link(node((n+1)%size),node(n));}
            if(pages instanceof FilePageStore)assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY)>65536);
            store.start();long visits=0,occurrences=0;
            while(store.advance()) {
                visits++;store.dependents(store.node());
                while(store.advanceDependent()){long parent=store.dependent();occurrences++;if(store.prove(parent))store.enqueue(parent);}
            }
            assertEquals(seeded?size:0,visits);assertEquals(seeded?2L*size:0,occurrences);
            store.freeze();assertEquals(truthPages,pages.statistics().livePages());
            long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed(),work=resources.workUsed();
            for(int repeat=0;repeat<8;repeat++)for(int n=0;n<size;n++)assertEquals(seeded,store.grounded(node(n)));
            assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));
            if(pages instanceof FilePageStore)assertEquals(heap,resources.heapUsed());
            assertThrows(IllegalStateException.class,()->store.expand(node(0)));assertThrows(IllegalStateException.class,store::advance);
            if(pages instanceof FilePageStore)System.out.println("SNAPSHOT_GROUNDING_STORAGE_METRICS seeded="+seeded+" heap="+resources.heapPeak()+" temporary="+temporary+" equations="+size+" edges="+(2L*size)+" queries="+(8L*size)+" queryWork="+(resources.workUsed()-work));
        }
        assertEquals(0,pages.statistics().livePages());if(pages instanceof FilePageStore)assertEquals(initial,resources.heapUsed());long borrowed=pages.allocate();pages.release(borrowed);
    }
    @Test void typedGroundedAlternativeAndCyclicNegativesMatchBothInputBackends() {
        Publication p=PagedAirStorageTest.publication("shared grounding");Unit body=p.units().get(0);var precision=body.sequences().get(0).terminator().header().precision();
        var a=new Ids.ObjectId(body.id(),"a");var b=new Ids.ObjectId(body.id(),"b");var dead=new Ids.ObjectId(body.id(),"dead");var dead2=new Ids.ObjectId(body.id(),"dead2");
        var type=new Types.Known(Types.Builtin.TEXT);var objects=new ArrayList<Memory.ObjectDeclaration>();
        objects.add(object(a,type,new Memory.AliasBinding(b),body,precision));
        objects.add(object(b,type,new Memory.AlternativesBinding(List.of(new Memory.AliasBinding(a),new Memory.CellBinding(new Ids.StorageId(p.id(),"absent"))),Scopes.NoMemory.INSTANCE),body,precision));
        objects.add(object(dead,type,new Memory.AliasBinding(dead2),body,precision));objects.add(object(dead2,type,new Memory.AliasBinding(dead),body,precision));
        var unit=new Unit(body.id(),body.containingUnit(),objects,body.visibleObjects(),body.entries(),body.sequences(),body.completionPorts(),body.body(),body.bodyUnavailable(),body.coverage(),body.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(131072,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)) {
            assertTyped(p,memory,memoryResources,false);assertTyped(p,file,fileResources,true);
        }
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static Memory.ObjectDeclaration object(Ids.ObjectId id,Types.TypeRef type,Memory.Binding binding,Unit unit,Evidence.Precision precision){return new Memory.ObjectDeclaration(id,Optional.empty(),type,binding,Memory.Visibility.PRIVATE,unit.origin(),Evidence.CoverageStatus.MODELED,precision);}
    private static void assertTyped(Publication p,PageStore pages,AnalysisResources resources,boolean paged) {
        try(var resident=AirSnapshot.fromPublication(p);var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(resident,resident.root(),null,builder);
            try(var managed=builder.finish(root)) {
                AirSnapshot input=paged?managed:resident;
                try(var keys=new SnapshotIdentityKeys(input,new PagedSnapshotIdentityStorage(pages,resources));
                    var declarations=SnapshotDeclarations.build(input,keys,new PagedSnapshotDeclarationsStorage(pages,resources),1000,1000,(r,i,n)->{throw new AssertionError(r);});
                    var grounding=SnapshotGrounding.build(input,declarations,new PagedSnapshotGroundingStorage(pages,resources),new PagedSnapshotGraphStorage(pages,resources),1000,1000)) {
                    long unit=input.element(input.field(input.root(),AirShape.PUBLICATION,4),AirShape.UNIT,0),objects=input.field(unit,AirShape.UNIT,2);
                    for(int n=0;n<4;n++){long object=input.element(objects,AirShape.MEMORY_OBJECT_DECLARATION,n),id=input.field(object,AirShape.MEMORY_OBJECT_DECLARATION,0);assertEquals(n<2,grounding.groundedObject(id));assertEquals(n<2,grounding.groundedNode(object));}
                    assertEquals(11,grounding.counts().nodes());assertEquals(6,grounding.counts().grounded());
                    long live=pages.statistics().livePages();
                    for(int repeat=0;repeat<128;repeat++)for(int n=0;n<4;n++)assertEquals(n<2,grounding.groundedNode(input.element(objects,AirShape.MEMORY_OBJECT_DECLARATION,n)));
                    assertEquals(live,pages.statistics().livePages());assertEquals(AirShape.PUBLICATION,input.shape(input.root()));
                    if(pages instanceof FilePageStore)System.out.println("SNAPSHOT_GROUNDING_INPUT_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" equations="+grounding.counts().nodes()+" edges="+grounding.counts().edges());
                }
            }
        }
        assertEquals(0,pages.statistics().livePages());
    }
    @Test void everyPrimitiveWorkInterruptionAndDeniedConstructorClosesTransferredState() {
        for(long heap:new long[]{1000,4000,7000,10000,13000,16000}) {
            var resources=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,resources)) {
                long initial=resources.heapUsed();assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotGroundingStorage(pages,resources)).phase());
                assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,resources.heapUsed());
        }
        var measured=resources(65536,1_000_000);long calls;
        try(var pages=new MemoryPageStore(128,measured);var store=new PagedSnapshotGroundingStorage(pages,measured)){sequence(store);calls=measured.workUsed();}
        for(long budget=0;budget<calls;budget++) {
            var resources=resources(65536,budget);
            try(var pages=new MemoryPageStore(128,resources)) {
                var store=new PagedSnapshotGroundingStorage(pages,resources);
                try{assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->sequence(store)).resource());assertThrows(IllegalStateException.class,()->store.grounded(node(0)));}
                finally{store.close();}assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    private static void sequence(PagedSnapshotGroundingStorage store) {
        store.expand(node(0));store.link(node(0),node(1));store.expand(node(1));assertTrue(store.prove(node(0)));store.enqueue(node(0));store.start();
        while(store.advance()){store.dependents(store.node());while(store.advanceDependent())if(store.prove(store.dependent()))store.enqueue(store.dependent());}
        store.freeze();assertTrue(store.grounded(node(1)));
    }
    @Test void linkedRowsMustExpandAndUncollectedQueriesNeverCreateFalseFacts() {
        var resources=resources(65536,1_000_000);
        try(var pages=new MemoryPageStore(128,resources);var store=new PagedSnapshotGroundingStorage(pages,resources)) {
            store.expand(node(0));store.link(node(0),node(1));assertThrows(IllegalStateException.class,store::start);assertThrows(IllegalStateException.class,()->store.expand(node(1)));
        }
        assertEquals(0,resources.heapUsed());
        var unpublished=resources(65536,1_000_000);
        try(var pages=new MemoryPageStore(128,unpublished);var store=new PagedSnapshotGroundingStorage(pages,unpublished)) {
            store.expand(node(0));store.prove(node(0));assertThrows(IllegalStateException.class,store::start);
        }
        assertEquals(0,unpublished.heapUsed());
        var lostChange=resources(65536,1_000_000);
        try(var pages=new MemoryPageStore(128,lostChange);var store=new PagedSnapshotGroundingStorage(pages,lostChange)) {
            store.expand(node(0));store.start();store.prove(node(0));assertThrows(IllegalStateException.class,store::freeze);
        }
        assertEquals(0,lostChange.heapUsed());
        var query=resources(65536,1_000_000);
        try(var pages=new FilePageStore(directory,128,1,query);var store=new PagedSnapshotGroundingStorage(pages,query)) {
            assertThrows(IllegalArgumentException.class,()->store.expand(0));store.expand(node(0));store.start();assertFalse(store.advance());store.freeze();
            long live=pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->store.grounded(node(1)));assertEquals(live,pages.statistics().livePages());
            assertThrows(IllegalStateException.class,()->store.grounded(node(0)));
        }
        assertEquals(0,query.heapUsed());
    }
}
