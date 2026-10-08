package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PagedSnapshotDeclarationsStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work) {
        return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));
    }
    @Test void declarationPayloadAndEveryFrontierOccurrenceSpillWithoutChangingPrimitiveFacts() {
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)) {
            assertStore(memory,memoryResources);assertStore(file,fileResources);
            assertTrue(fileResources.heapPeak()<=65536);assertTrue(file.statistics().evictions()>1000);
            assertEquals(0,file.statistics().livePages());
        }
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertStore(PageStore pages,AnalysisResources resources) {
        long initial=resources.heapUsed();int count=4096;
        try(var store=new PagedSnapshotDeclarationsStorage(pages,resources)) {
            for(int n=0;n<count;n++) {
                long key=(1L<<44)+n*37L,node=(1L<<42)+n,identity=(1L<<43)+n;
                assertTrue(store.define(key,node,identity,10+n%7,20+n%11,30+n%13));
                assertFalse(store.define(key,node+1,identity+1,999,999,999));
                store.enqueue(node,identity,n%127);store.enqueue(node,identity,n%127);
            }
            // Required declaration payload alone exceeds the managed resident quota (file run).
            if(pages instanceof FilePageStore)assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY)>65536);
            for(int n=0;n<count;n++) {
                long key=(1L<<44)+n*37L;
                for(var field:SnapshotDeclarations.Fact.values()) {
                    long expected=switch(field) {
                        case NODE -> (1L<<42)+n;case IDENTITY -> (1L<<43)+n;
                        case UNIT -> 10+n%7;case SEQUENCE -> 20+n%11;case OWNER -> 30+n%13;
                    };
                    assertEquals(expected,store.fact(key,field));assertEquals(expected,store.declaration(n,field));
                }
                for(int repeat=0;repeat<2;repeat++) {
                    assertTrue(store.advance());assertEquals((1L<<42)+n,store.node());
                    assertEquals((1L<<43)+n,store.owner());assertEquals(n%127,store.depth());
                }
            }
            assertFalse(store.advance());assertFalse(store.advance());
            assertEquals(0,store.fact(1,SnapshotDeclarations.Fact.NODE));
            assertThrows(IndexOutOfBoundsException.class,()->store.declaration(count,SnapshotDeclarations.Fact.NODE));
            // The empty frontier starts a fresh bounded address sequence; repeated nodes are still kept.
            long pagesBeforeEpochs=pages.statistics().livePages();
            long temporaryBeforeEpochs=resources.used(AnalysisResources.Pool.TEMPORARY);
            for(int epoch=0;epoch<64;epoch++) {
                store.enqueue(100,200,300);store.enqueue(100,200,300);
                assertTrue(store.advance());assertEquals(100,store.node());
                assertTrue(store.advance());assertEquals(100,store.node());assertFalse(store.advance());
                assertEquals(pagesBeforeEpochs,pages.statistics().livePages());
                assertEquals(temporaryBeforeEpochs,resources.used(AnalysisResources.Pool.TEMPORARY));
            }
            if(pages instanceof FilePageStore)System.out.println("SNAPSHOT_DECLARATION_STORAGE_METRICS {\"managedHeapPeak\":"+resources.heapPeak()+",\"temporaryBytes\":"+resources.used(AnalysisResources.Pool.TEMPORARY)+",\"livePages\":"+pages.statistics().livePages()+",\"evictions\":"+pages.statistics().evictions()+",\"declarationRows\":4096,\"frontierOccurrences\":8192,\"emptyEpochs\":64}");
            store.freeze();
            assertThrows(IllegalStateException.class,()->store.define(999,1,2,3,4,5));
            assertThrows(IllegalStateException.class,()->store.enqueue(1,2,0));
            assertThrows(IllegalStateException.class,store::advance);
            assertEquals(1L<<42,store.declaration(0,SnapshotDeclarations.Fact.NODE));
        }
        assertEquals(0,pages.statistics().livePages());
        if(pages instanceof FilePageStore)assertEquals(initial,resources.heapUsed());
        long borrowed=pages.allocate();pages.release(borrowed); // Closing the port does not close the run store.
    }
    @Test void completeDeclarationInventoryMatchesIndependentFactsAcrossBorrowedResidentAndPagedInputs() {
        var p=PagedAirStorageTest.publication("synthetic inventory");var body=p.units().get(0);var sequence=body.sequences().get(0);
        var header=sequence.terminator().header();var values=new ArrayList<Expression>();
        for(int n=0;n<64;n++)values.add(new Expressions.Literal(new Operand.Header(
            new Ids.OperandId(new Ids.OperationOwner(header.id()),"operand-"+n),Operand.Role.VALUE_READ,body.origin()),new Values.TextValue("a")));
        var replacement=new Sequence(sequence.label(),List.of(),new Operations.Return(header,values),body.origin());
        var unit=new Unit(body.id(),body.containingUnit(),body.objects(),body.visibleObjects(),body.entries(),List.of(replacement),body.completionPorts(),body.body(),body.bodyUnavailable(),body.coverage(),body.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        // Input is explicit caller-owned Publication; complete semantic validity is not inferred.
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)) {
            assertInventory(p,memory,memoryResources);assertInventory(p,file,fileResources);
        }
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertInventory(Publication publication,PageStore pages,AnalysisResources resources) {
        try(var original=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
            try(var snapshot=builder.finish(root);
                var keys=new SnapshotIdentityKeys(snapshot,new PagedSnapshotIdentityStorage(pages,resources))) {
                long pagesBefore=pages.statistics().livePages();var rules=new ArrayList<SnapshotDeclarations.Rule>();
                try(var index=SnapshotDeclarations.build(snapshot,keys,new PagedSnapshotDeclarationsStorage(pages,resources),Long.MAX_VALUE,Long.MAX_VALUE,(rule,id,node)->rules.add(rule))) {
                    assertEquals(List.of(),rules);assertEquals(70,index.entities());assertEquals(64,index.operands());assertEquals(1,index.operations());
                    long unit=snapshot.element(snapshot.field(root,AirShape.PUBLICATION,4),AirShape.UNIT,0);
                    long sequence=snapshot.element(snapshot.field(unit,AirShape.UNIT,5),AirShape.SEQUENCE,0);
                    long operation=snapshot.field(sequence,AirShape.SEQUENCE,2);
                    long operationId=snapshot.field(snapshot.field(operation,AirShape.OPERATIONS_RETURN,0),AirShape.OPERATIONS_HEADER,0);
                    assertEquals(operation,index.fact(operationId,SnapshotDeclarations.Fact.NODE));
                    int count=0;
                    for(long n=0;n<index.entities();n++) {
                        long node=index.declaration(n,SnapshotDeclarations.Fact.NODE);
                        if(snapshot.shape(node)==AirShape.EXPRESSIONS_LITERAL) {
                            assertEquals(keys.key(operationId),keys.key(index.declaration(n,SnapshotDeclarations.Fact.OWNER)));count++;
                        }
                    }
                    assertEquals(64,count);
                }
                assertEquals(AirShape.PUBLICATION,snapshot.shape(root));
                assertTrue(keys.key(snapshot.field(root,AirShape.PUBLICATION,0))>0);
                assertTrue(pages.statistics().livePages()>=pagesBefore); // Borrowed input/identity catalogue stays available.
            }
        }
        assertEquals(0,pages.statistics().livePages());
    }
    @Test void deniedConstructionAndSpentWorkAbortCleanlyWithoutClosingBorrowedPageStore() {
        for(long heap:new long[]{1000,4000,7000,10000}) {
            var resources=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,resources)) {
                long initial=resources.heapUsed();
                assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotDeclarationsStorage(pages,resources)).phase());
                assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,resources.heapUsed());
        }
        var resources=resources(65536,1);
        try(var pages=new FilePageStore(directory,128,1,resources)) {
            long initial=resources.heapUsed();var store=new PagedSnapshotDeclarationsStorage(pages,resources);
            try {
                assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->store.define(1,2,3,4,5,6)).resource());
                assertThrows(IllegalStateException.class,()->store.enqueue(1,2,0));
            } finally {store.close();}
            assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
    }
}
