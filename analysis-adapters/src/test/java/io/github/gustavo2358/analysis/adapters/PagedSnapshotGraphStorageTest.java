package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Managed frontier/grammar laws, not full structural admission. */
final class PagedSnapshotGraphStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work) {
        return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));
    }
    @Test void orderedPrimitiveFrontierAndExactContextMemoSpillWithoutHistoricalStackGrowth() {
        var resident=resources(32_000_000,1_000_000_000);var spilled=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,resident);var file=new FilePageStore(directory,128,1,spilled)) {
            completionQueries(memory,resident);completionQueries(file,spilled);
            frontier(memory,resident);frontier(file,spilled);assertTrue(spilled.heapPeak()<=65536);
        }
        assertEquals(0,resident.heapUsed());assertEquals(0,spilled.heapUsed());
        assertEquals(0,spilled.used(AnalysisResources.Pool.TEMPORARY));
    }
    /** Query history is not required state; complete alone adds full source/context pairs. */
    private static void completionQueries(PageStore pages,AnalysisResources ledger) {
        long initial=ledger.heapUsed(),borrowed=pages.allocate(),node=(1L<<42)+7;
        try {
            try(var port=new PagedSnapshotGraphStorage(pages,ledger)) {
                long live=pages.statistics().livePages(),heap=ledger.heapUsed(),temporary=ledger.used(AnalysisResources.Pool.TEMPORARY);
                for(int n=0;n<1024;n++) {
                    assertFalse(port.completed(node+n,n%7));
                    assertEquals(live,pages.statistics().livePages());assertEquals(heap,ledger.heapUsed());
                    assertEquals(temporary,ledger.used(AnalysisResources.Pool.TEMPORARY));
                }
                port.complete(node,1);port.complete(Long.MAX_VALUE,Integer.MAX_VALUE);
                assertTrue(port.completed(node,1));assertTrue(port.completed(Long.MAX_VALUE,Integer.MAX_VALUE));
                assertFalse(port.completed(node,2));assertFalse(port.completed(node+1,1));
                assertFalse(port.completed(Long.MAX_VALUE-1,Integer.MAX_VALUE));
                assertFalse(port.completed(Long.MAX_VALUE,Integer.MAX_VALUE-1));
                port.active(node,true);assertFalse(port.completed(node,0));port.active(node,false);
                port.complete(node+1,0);assertTrue(port.completed(node+1,0));assertFalse(port.completed(node+1,1));
                port.complete(Long.MAX_VALUE,0);assertTrue(port.completed(Long.MAX_VALUE,0));
                assertTrue(port.completed(Long.MAX_VALUE,Integer.MAX_VALUE));assertFalse(port.completed(Long.MAX_VALUE-1,0));
                live=pages.statistics().livePages();heap=ledger.heapUsed();temporary=ledger.used(AnalysisResources.Pool.TEMPORARY);
                for(int n=0;n<1024;n++) {
                    assertFalse(port.completed(node+20+n,n%7));assertTrue(port.completed(node,1));
                    assertEquals(live,pages.statistics().livePages());assertEquals(heap,ledger.heapUsed());
                    assertEquals(temporary,ledger.used(AnalysisResources.Pool.TEMPORARY));
                }
            }
            assertEquals(1,pages.statistics().livePages());
            if(pages instanceof FilePageStore)assertEquals(initial,ledger.heapUsed());
            byte[] probe=new byte[1];assertDoesNotThrow(()->pages.read(borrowed,0,probe,0,1));
        } finally {pages.release(borrowed);}
        assertEquals(0,pages.statistics().livePages());
    }
    private static void frontier(PageStore pages,AnalysisResources ledger) {
        long initial=ledger.heapUsed();
        try(var port=new PagedSnapshotGraphStorage(pages,ledger)) {
            long node=(1L<<42)+7;assertFalse(port.active(node));port.active(node,true);assertTrue(port.active(node));
            assertFalse(port.completed(node,1));port.complete(node,1);assertTrue(port.completed(node,1));assertFalse(port.completed(node,2));
            port.complete(node,2);assertTrue(port.completed(node,2));port.active(node,false);assertFalse(port.active(node));
            port.push(10,0,1,true);port.push(20,0,2,false);long from=port.size();
            for(int n=0;n<4096;n++)port.push(node+n,n%3,3+n%7,n%2==0);
            port.reverse(from);
            if(pages instanceof FilePageStore) {
                assertTrue(ledger.used(AnalysisResources.Pool.TEMPORARY)>65536);
                System.out.println("SNAPSHOT_GRAPH_STORAGE_METRICS {\"managedHeapPeak\":"+ledger.heapPeak()+",\"temporaryBytes\":"+ledger.used(AnalysisResources.Pool.TEMPORARY)+",\"frontierRows\":4096}");
            }
            for(int n=0;n<4096;n++) {
                assertTrue(port.advance());assertEquals(node+n,port.node());assertEquals(n%3,port.element());
                assertEquals(3+n%7,port.depth());assertEquals(n%2==0,port.exiting());
            }
            assertTrue(port.advance());assertEquals(20,port.node());assertTrue(port.advance());assertEquals(10,port.node());assertFalse(port.advance());
            long live=pages.statistics().livePages(),temporary=ledger.used(AnalysisResources.Pool.TEMPORARY);
            for(int epoch=0;epoch<64;epoch++) {
                port.push(node,2,3,false);port.push(node,2,3,true);port.reverse(0);
                assertTrue(port.advance());assertFalse(port.exiting());assertTrue(port.advance());assertTrue(port.exiting());assertFalse(port.advance());
                assertEquals(live,pages.statistics().livePages());assertEquals(temporary,ledger.used(AnalysisResources.Pool.TEMPORARY));
            }
            assertEquals(0,port.size());assertThrows(IndexOutOfBoundsException.class,()->port.reverse(1));
        }
        assertEquals(0,pages.statistics().livePages());if(pages instanceof FilePageStore)assertEquals(initial,ledger.heapUsed());
        long borrowed=pages.allocate();pages.release(borrowed);
    }
    @Test void zeroContextUsesExactSourceDictionaryWithoutDuplicatedTupleWorkOrPayload() {
        for(int count:new int[]{1024,2048,4096})for(boolean file:new boolean[]{false,true}) {
            var graphLedger=resources(32_000_000,1_000_000_000);var directLedger=resources(32_000_000,1_000_000_000);
            long[] actual,baseline;
            try(PageStore pages=file?new FilePageStore(directory,128,1,graphLedger):new MemoryPageStore(128,graphLedger)) {
                actual=contextZeroCost(pages,graphLedger,count,true);
                assertEquals(0,pages.statistics().livePages());
            }
            try(PageStore pages=file?new FilePageStore(directory,128,1,directLedger):new MemoryPageStore(128,directLedger)) {
                baseline=contextZeroCost(pages,directLedger,count,false);
                assertEquals(0,pages.statistics().livePages());
            }
            assertArrayEquals(baseline,actual,"full(source,0) must need no duplicated tuple payload or indirect comparison work");
            assertEquals(0,graphLedger.heapUsed());assertEquals(0,directLedger.heapUsed());
            assertEquals(0,graphLedger.used(AnalysisResources.Pool.TEMPORARY));assertEquals(0,directLedger.used(AnalysisResources.Pool.TEMPORARY));
        }
    }
    /** Primitive dictionary is only a representation cost comparator, not a dependency oracle. */
    private static long[] contextZeroCost(PageStore pages,AnalysisResources ledger,int count,boolean managedGraph) {
        long beforePages=pages.statistics().livePages(),beforeWork=ledger.workUsed();
        if(managedGraph)try(var graph=new PagedSnapshotGraphStorage(pages,ledger)) {
            beforePages=pages.statistics().livePages();beforeWork=ledger.workUsed();
            for(int at=count-1;at>=0;at--){long source=Long.MAX_VALUE-8192+at;assertFalse(graph.completed(source,0));graph.complete(source,0);}
            for(int at=0;at<count;at++)assertTrue(graph.completed(Long.MAX_VALUE-8192+at,0));
            return new long[]{pages.statistics().livePages()-beforePages,ledger.workUsed()-beforeWork};
        } else try(var direct=new PagedLongIndex(pages,ledger,AnalysisResources.Phase.VALIDATION)) {
            beforePages=pages.statistics().livePages();beforeWork=ledger.workUsed();
            for(int at=count-1;at>=0;at--){long source=Long.MAX_VALUE-8192+at;assertEquals(0,direct.find(source));direct.intern(source,1);}
            for(int at=0;at<count;at++)assertEquals(1,direct.find(Long.MAX_VALUE-8192+at));
            return new long[]{pages.statistics().livePages()-beforePages,ledger.workUsed()-beforeWork};
        }
    }
    @Test void completeTypedGrammarUsesManagedDeepFramesAndLeavesBorrowedInputAvailable() {
        var resident=resources(32_000_000,1_000_000_000);var spilled=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,resident);var file=new FilePageStore(directory,128,1,spilled)) {
            assertArrayEquals(grammar(memory,resident),grammar(file,spilled));assertTrue(spilled.heapPeak()<=65536);
        }
        assertEquals(0,resident.heapUsed());assertEquals(0,spilled.heapUsed());
    }
    private static long[] grammar(PageStore pages,AnalysisResources ledger) {
        var publication=PagedAirStorageTest.publication("managed typed grammar");
        try(var original=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long[] fields=new long[12];long root=original.root();
            for(int n=0;n<fields.length;n++)fields[n]=PagedAirStorageTest.copy(original,original.field(root,AirShape.PUBLICATION,n),AirShape.PUBLICATION.field(n).element(),builder);
            long leaf=builder.scalar(AirShape.PROOFS_PUBLICATION_DOMAIN,0),scope=leaf;
            for(int n=0;n<512;n++)scope=builder.record(AirShape.PROOFS_INTERSECTION,scope,leaf);
            long unit=builder.record(AirShape.IDS_UNIT_ID,fields[0],text(builder,"u"));
            long entry=builder.record(AirShape.IDS_ENTRY_ID,unit,text(builder,"e"));
            long subject=builder.record(AirShape.PROOFS_PARAMETER_DOMAIN,entry,text(builder,AirShape.INTEGER,"0"));
            long assertion=builder.record(AirShape.PROOFS_SAME_DOMAIN,subject,subject,scope);
            long premise=builder.record(AirShape.PROOFS_PREMISE,builder.record(AirShape.IDS_PREMISE_ID,fields[0],text(builder,"p")),text(builder,"a"),text(builder,"j"),
                builder.record(AirShape.IDS_ORIGIN_ID,fields[0],text(builder,"origin")),assertion);
            try(var list=builder.list(AirShape.PROOFS_PREMISE)){list.add(premise);fields[11]=list.finish();}
            long built=builder.record(AirShape.PUBLICATION,fields);
            try(var snapshot=builder.finish(built)) {
                int[] intersections={0};long scratch=ledger.used(AnalysisResources.Pool.SCRATCH);
                var counts=SnapshotGraphWalk.scan(snapshot,new PagedSnapshotGraphStorage(pages,ledger),10000,10000,(node,shape,element)->{
                    if(shape==AirShape.PROOFS_INTERSECTION)intersections[0]++;assertEquals(scratch,ledger.used(AnalysisResources.Pool.SCRATCH));
                });
                assertEquals(512,intersections[0]);assertEquals(AirShape.PUBLICATION,snapshot.shape(built));
                assertEquals(scratch,ledger.used(AnalysisResources.Pool.SCRATCH));
                if(pages instanceof FilePageStore) {
                    assertTrue(ledger.used(AnalysisResources.Pool.TEMPORARY)>65536);
                    System.out.println("SNAPSHOT_GRAPH_GRAMMAR_METRICS {\"managedHeapPeak\":"+ledger.heapPeak()+",\"temporaryBytesAfterWalk\":"+ledger.used(AnalysisResources.Pool.TEMPORARY)+",\"nodes\":"+counts.nodes()+",\"edges\":"+counts.edges()+",\"intersections\":512}");
                }
                return new long[]{counts.nodes(),counts.edges(),intersections[0]};
            }
        } finally {assertEquals(0,pages.statistics().livePages());}
    }
    private static long text(AirSnapshotBuilder builder,String value){return text(builder,AirShape.TEXT,value);}
    private static long text(AirSnapshotBuilder builder,AirShape shape,String value) {
        try(var out=builder.text(shape)){char[] chars=value.toCharArray();out.append(chars,0,chars.length);return out.finish();}
    }
    @Test void constructionAndOperationalFailureCloseEveryOwnedIndexAndKeepBorrowedRunStore() {
        for(long heap:new long[]{1000,4000,9000,14000}) {
            var ledger=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,ledger)) {
                long initial=ledger.heapUsed();var failure=assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotGraphStorage(pages,ledger));
                assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());assertEquals(initial,ledger.heapUsed());assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,ledger.heapUsed());
        }
        var ledger=resources(65536,1);
        try(var pages=new FilePageStore(directory,128,1,ledger)) {
            long initial=ledger.heapUsed();var port=new PagedSnapshotGraphStorage(pages,ledger);
            try {
                assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->port.push(1,0,1,false)).resource());
                assertThrows(IllegalStateException.class,port::advance);
            } finally {port.close();}
            assertEquals(initial,ledger.heapUsed());assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,ledger.heapUsed());assertEquals(0,ledger.used(AnalysisResources.Pool.TEMPORARY));
    }
}
