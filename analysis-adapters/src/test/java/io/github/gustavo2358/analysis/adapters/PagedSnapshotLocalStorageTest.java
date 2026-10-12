package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Primitive local-index and typed whole-graph parity, not complete admission/decoder laws. */
final class PagedSnapshotLocalStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work){return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));}
    @Test void exactMembershipAndFactKindsSpillAndMissesNeverGrowRequiredState() {
        var resident=resources(32_000_000,1_000_000_000);var spilled=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,resident);var file=new FilePageStore(directory,128,1,spilled)) {
            port(memory,resident);port(file,spilled);assertTrue(spilled.heapPeak()<=65536);
        }
        assertEquals(0,resident.heapUsed());assertEquals(0,spilled.heapUsed());assertEquals(0,spilled.used(AnalysisResources.Pool.TEMPORARY));
    }
    private static void port(PageStore pages,AnalysisResources ledger) {
        long initial=ledger.heapUsed(),list=(1L<<42)+9,key=Long.MAX_VALUE-4096;
        try(var port=new PagedSnapshotLocalStorage(pages,ledger)) {
            for(int n=0;n<4096;n++)assertTrue(port.addLabel(list,key+n));
            for(int n=0;n<4096;n++){assertFalse(port.addLabel(list,key+n));assertTrue(port.containsLabel(list,key+n));}
            assertFalse(port.containsLabel(list+1,key));
            for(int kind=0;kind<3;kind++){assertFalse(port.known(list,kind));port.remember(list,kind);assertTrue(port.known(list,kind));assertFalse(port.known(list+1,kind));}
            long live=pages.statistics().livePages(),temporary=ledger.used(AnalysisResources.Pool.TEMPORARY),heap=ledger.heapUsed();
            for(int n=0;n<16_384;n++){assertFalse(port.containsLabel(list+100+n,key));assertFalse(port.known(list+100+n,n%3));}
            assertEquals(live,pages.statistics().livePages());assertEquals(temporary,ledger.used(AnalysisResources.Pool.TEMPORARY));assertEquals(heap,ledger.heapUsed());
            assertThrows(IllegalArgumentException.class,()->port.known(list,3));assertThrows(IllegalArgumentException.class,()->port.addLabel(0,key));
            if(pages instanceof FilePageStore){assertTrue(temporary>65536);System.out.println("SNAPSHOT_LOCAL_STORAGE_METRICS {\"managedHeapPeak\":"+ledger.heapPeak()+",\"temporaryBytes\":"+temporary+",\"members\":4096,\"misses\":32768}");}
        }
        assertEquals(0,pages.statistics().livePages());if(pages instanceof FilePageStore)assertEquals(initial,ledger.heapUsed());long borrowed=pages.allocate();pages.release(borrowed);
    }
    @Test void builderWrittenSharedLabelUniverseMatchesBothBackendsAndWholeLocalGrammar() {
        var resident=resources(32_000_000,1_000_000_000);var spilled=resources(131072,1_000_000_000);
        try(var memory=new MemoryPageStore(128,resident);var file=new FilePageStore(directory,128,1,spilled)) {
            assertArrayEquals(local(memory,resident),local(file,spilled));assertTrue(spilled.heapPeak()<=131072);
        }
        assertEquals(0,resident.heapUsed());assertEquals(0,spilled.heapUsed());assertEquals(0,spilled.used(AnalysisResources.Pool.TEMPORARY));
    }
    private static long[] local(PageStore pages,AnalysisResources ledger) {
        try(var original=AirSnapshot.fromPublication(PagedAirStorageTest.publication("local predicates"));var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long[] fields=new long[12];long root=original.root();
            for(int n=0;n<fields.length;n++)if(n!=4)fields[n]=PagedAirStorageTest.copy(original,original.field(root,AirShape.PUBLICATION,n),AirShape.PUBLICATION.field(n).element(),builder);
            long unit=original.element(original.field(root,AirShape.PUBLICATION,4),AirShape.UNIT,0);long[] body=new long[11];
            for(int n=0;n<body.length;n++)if(n!=4)body[n]=PagedAirStorageTest.copy(original,original.field(unit,AirShape.UNIT,n),AirShape.UNIT.field(n).element(),builder);
            long labels,selected=0;
            try(var list=builder.list(AirShape.IDS_LABEL_ID)){for(int n=0;n<256;n++){long label=builder.record(AirShape.IDS_LABEL_ID,body[0],text(builder,"L"+n));list.add(label);selected=label;}labels=list.finish();}
            long domain=builder.record(AirShape.TYPES_LABEL_TYPE,body[0],labels),known=builder.record(AirShape.TYPES_KNOWN,domain);
            long parameter=builder.record(AirShape.INTERACTIONS_PARAMETER,text(builder,AirShape.INTEGER,"0"),builder.record(AirShape.INTERACTIONS_KNOWN_MODE,builder.scalar(AirShape.INTERACTIONS_PASSING_MODE,0)),known,builder.scalar(AirShape.INTERACTIONS_EXTERNAL_BINDING,0),body[10]);
            long parameters;try(var list=builder.list(AirShape.INTERACTIONS_PARAMETER)){for(int n=0;n<1024;n++)list.add(parameter);parameters=list.finish();}
            long results;try(var list=builder.list(AirShape.INTERACTIONS_RESULT_SLOT)){results=list.finish();}
            long signature=builder.record(AirShape.INTERACTIONS_SIGNATURE,builder.record(AirShape.INTERACTIONS_PARAMETER_INVENTORY,parameters,builder.scalar(AirShape.INTERACTIONS_NO_REMAINDER,0)),builder.record(AirShape.INTERACTIONS_RESULT_INVENTORY,results,builder.scalar(AirShape.INTERACTIONS_NO_REMAINDER,0)),body[10]);
            long entry=original.element(original.field(unit,AirShape.UNIT,4),AirShape.ENTRIES_ENTRY,0);long[] entryFields=new long[5];
            for(int n=0;n<5;n++)entryFields[n]=n==2?signature:PagedAirStorageTest.copy(original,original.field(entry,AirShape.ENTRIES_ENTRY,n),AirShape.ENTRIES_ENTRY.field(n).element(),builder);
            try(var list=builder.list(AirShape.ENTRIES_ENTRY)){list.add(builder.record(AirShape.ENTRIES_ENTRY,entryFields));body[4]=list.finish();}
            try(var list=builder.list(AirShape.UNIT)){list.add(builder.record(AirShape.UNIT,body));fields[4]=list.finish();}
            long value=builder.record(AirShape.VALUES_LABEL_VALUE,selected,domain),built=builder.record(AirShape.PUBLICATION,fields);
            try(var snapshot=builder.finish(built);var keys=new SnapshotIdentityKeys(snapshot,new PagedSnapshotIdentityStorage(pages,ledger))) {
                SnapshotGraphWalk.Counts counts;
                try(var check=new SnapshotLocalConstraints(snapshot,keys,new PagedSnapshotLocalStorage(pages,ledger))) {
                    long scratch=ledger.used(AnalysisResources.Pool.SCRATCH);
                    counts=SnapshotGraphWalk.scan(snapshot,new PagedSnapshotGraphStorage(pages,ledger),10000,10000,check);
                    assertEquals(scratch,ledger.used(AnalysisResources.Pool.SCRATCH));
                    check.node(value,AirShape.VALUES_LABEL_VALUE,null);long live=pages.statistics().livePages();
                    for(int n=0;n<128;n++)check.node(value,AirShape.VALUES_LABEL_VALUE,null);
                    assertEquals(live,pages.statistics().livePages());assertEquals(AirShape.PUBLICATION,snapshot.shape(built));
                    if(pages instanceof FilePageStore){assertTrue(ledger.used(AnalysisResources.Pool.TEMPORARY)>131072);System.out.println("SNAPSHOT_LOCAL_GRAPH_METRICS {\"managedHeapPeak\":"+ledger.heapPeak()+",\"temporaryBytes\":"+ledger.used(AnalysisResources.Pool.TEMPORARY)+",\"nodes\":"+counts.nodes()+",\"edges\":"+counts.edges()+",\"labels\":256,\"parameterOccurrences\":1024}");}
                }
                return new long[]{counts.nodes(),counts.edges()};
            }
        } finally {assertEquals(0,pages.statistics().livePages());}
    }
    private static long text(AirSnapshotBuilder builder,String value){return text(builder,AirShape.TEXT,value);}
    private static long text(AirSnapshotBuilder builder,AirShape shape,String value){try(var atom=builder.text(shape)){char[] chars=value.toCharArray();atom.append(chars,0,chars.length);return atom.finish();}}
    @Test void constructionAndSpentWorkAbortOwnedLocalStateAndKeepBorrowedInput() {
        for(long heap:new long[]{1000,2000,4000,6000}) {
            var ledger=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,ledger)) {
                long initial=ledger.heapUsed();var failure=assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotLocalStorage(pages,ledger));assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());assertEquals(initial,ledger.heapUsed());assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,ledger.heapUsed());
        }
        var ledger=resources(65536,1_000_000);
        try(var pages=new FilePageStore(directory,128,1,ledger);var snapshot=AirSnapshot.fromPublication(PagedAirStorageTest.publication("borrowed"))) {
            long initial=ledger.heapUsed();var port=new PagedSnapshotLocalStorage(pages,ledger);assertTrue(port.addLabel(1L<<42,Long.MAX_VALUE));
            ledger.work(ledger.limits().workUnits()-ledger.workUsed(),AnalysisResources.Phase.VALIDATION);
            var first=assertThrows(AnalysisResources.Exhausted.class,()->port.containsLabel(1L<<42,Long.MAX_VALUE));assertEquals(AnalysisResources.Resource.WORK,first.resource());assertEquals(AnalysisResources.Phase.VALIDATION,first.phase());
            assertThrows(IllegalStateException.class,()->port.known(1L<<42,0));assertDoesNotThrow(port::close);assertDoesNotThrow(port::close);
            assertEquals(initial,ledger.heapUsed());assertEquals(0,pages.statistics().livePages());assertEquals(AirShape.PUBLICATION,snapshot.shape(snapshot.root()));
        }
        assertEquals(0,ledger.heapUsed());assertEquals(0,ledger.used(AnalysisResources.Pool.TEMPORARY));
    }
}
