package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Independent mode ordering/count/membership and bounded residence; no full admission claim. */
final class PagedSnapshotSignatureStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work){return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));}
    private static long source(int n){return (1L<<44)+37L*n;}
    @Test void coldBadRowsHaveSeparateModePrefixesAndReadonlyQueries() {
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)) {
            assertCold(memory,memoryResources);assertCold(file,fileResources);assertTrue(fileResources.heapPeak()<=65536);assertTrue(file.statistics().evictions()>1000);
        }
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertCold(PageStore pages,AnalysisResources resources) {
        long before=resources.heapUsed();int size=4096;
        try(var store=new PagedSnapshotSignatureStorage(pages,resources)) {
            long table=store.begin(source(0),0);
            for(int at=0;at<size;at++){store.member(table,source(at+1));store.issue(table,source(2*size+at),source(at+1),at,at%8==7,true);}
            store.finish(table,size);assertEquals(table,store.find(source(0),0));assertEquals(0,store.find(source(0),1));
            assertEquals(size,store.rows(table));assertEquals(size,store.issues(table,true));assertEquals(size/8,store.issues(table,false));
            store.select(table,true);for(int at=0;at<size;at++){assertTrue(store.advanceIssue());assertEquals(at,store.ordinal());assertEquals(source(2*size+at),store.row());assertEquals(source(at+1),store.position());}assertFalse(store.advanceIssue());
            store.select(table,false);for(int at=7;at<size;at+=8){assertTrue(store.advanceIssue());assertEquals(at,store.ordinal());}assertFalse(store.advanceIssue());
            long live=pages.statistics().livePages(),temp=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed(),work=resources.workUsed();
            for(int q=0;q<8192;q++) {
                assertEquals(table,store.find(source(0),0));assertEquals(0,store.find(source(10_000+q),q&1));
                assertTrue(store.contains(table,source(q%size+1)));assertFalse(store.contains(table,source(20_000+q)));
                // An open prefix must jump directly past closed-only rows and be reselectable.
                store.select(table,false);assertTrue(store.advanceIssue());assertEquals(7,store.ordinal());
                store.select(table,true);assertTrue(store.advanceIssue());assertEquals(0,store.ordinal());
            }
            assertEquals(live,pages.statistics().livePages());assertEquals(temp,resources.used(AnalysisResources.Pool.TEMPORARY));
            if(pages instanceof FilePageStore){assertEquals(heap,resources.heapUsed());assertTrue(temp>65536);System.out.println("SNAPSHOT_SIGNATURE_STORAGE_METRICS heap="+resources.heapPeak()+" temporary="+temp+" rows="+size+" queries=8192 queryWork="+(resources.workUsed()-work));}
        }
        assertEquals(0,pages.statistics().livePages());if(pages instanceof FilePageStore)assertEquals(before,resources.heapUsed());long borrowed=pages.allocate();pages.release(borrowed);
    }
    @Test void manyDistinctListsKeepLiteralKeysDensePayloadsAndEmptyContextsSeparate() {
        var resources=resources(65536,1_000_000_000);
        try(var pages=new FilePageStore(directory,128,1,resources);var store=new PagedSnapshotSignatureStorage(pages,resources)) {
            for(int n=0;n<1024;n++) {
                long handle=store.begin(source(n),0);for(int m=0;m<8;m++)store.member(handle,source(10000+n*8+m));
                store.issue(handle,source(30000+n),source(10000+n*8),0,false,true);store.finish(handle,8);
                long empty=store.begin(source(n),1);store.finish(empty,0);
            }
            long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed();
            for(int n=1023;n>=0;n--) {
                long handle=store.find(source(n),0),empty=store.find(source(n),1);assertNotEquals(handle,empty);assertEquals(8,store.rows(handle));assertEquals(0,store.rows(empty));
                assertEquals(1,store.issues(handle,true));assertEquals(0,store.issues(handle,false));assertTrue(store.contains(handle,source(10000+n*8)));assertFalse(store.contains(handle,source(10000+((n+1)%1024)*8)));
                store.select(empty,true);assertFalse(store.advanceIssue());store.select(handle,true);assertTrue(store.advanceIssue());assertEquals(source(30000+n),store.row());assertEquals(0,store.ordinal());assertFalse(store.advanceIssue());
            }
            assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));assertEquals(heap,resources.heapUsed());assertTrue(temporary>65536);
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void sharedInvalidSignatureCountsAndMembershipMatchBothInputBackends() {
        Publication p=PagedAirStorageTest.publication("shared positions");Unit body=p.units().get(0);var seed=body.entries().get(0);var parameters=new ArrayList<Interactions.Parameter>();
        for(int at=0;at<256;at++)parameters.add(new Interactions.Parameter(BigInteger.valueOf(at+1L),new Interactions.KnownMode(Interactions.PassingMode.VALUE),new Types.Known(Types.Builtin.TEXT),Interactions.ExternalBinding.INSTANCE,body.origin()));
        var signature=new Interactions.Signature(new Interactions.ParameterInventory(parameters,Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),body.origin());
        var entries=new ArrayList<Entries.Entry>();for(int at=0;at<1024;at++)entries.add(new Entries.Entry(new Ids.EntryId(body.id(),"entry"+at),seed.initialLabel(),signature,seed.state(),body.origin()));
        var unit=new Unit(body.id(),body.containingUnit(),body.objects(),body.visibleObjects(),entries,body.sequences(),body.completionPorts(),body.body(),body.bodyUnavailable(),body.coverage(),body.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(131072,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)){assertTyped(p,memory,memoryResources,false);assertTyped(p,file,fileResources,true);}
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertTyped(Publication p,PageStore pages,AnalysisResources resources,boolean paged) {
        try(var resident=AirSnapshot.fromPublication(p);var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
            long root=copyShared(resident,resident.root(),null,builder,new java.util.HashMap<>());
            try(var managed=builder.finish(root)) {
                AirSnapshot input=paged?managed:resident;
                try(var keys=new SnapshotIdentityKeys(input,new PagedSnapshotIdentityStorage(pages,resources));var index=new SnapshotSignatureIndex(input,keys,new PagedSnapshotSignatureStorage(pages,resources))) {
                    long unit=input.element(input.field(input.root(),AirShape.PUBLICATION,4),AirShape.UNIT,0),entries=input.field(unit,AirShape.UNIT,4);var report=new Report(2);
                    try(var cursor=input.elements(entries,AirShape.ENTRIES_ENTRY)){while(cursor.advance())index.checkEntry(cursor.value(),report);}
                    assertEquals(262144,report.count);assertEquals(List.of(0L,1L),report.ordinals);assertEquals(2,index.counts().lists());assertEquals(256,index.counts().rows());assertEquals(256,index.counts().closedErrors());assertEquals(0,index.counts().openErrors());
                    if(pages instanceof FilePageStore){assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY)>131072);System.out.println("SNAPSHOT_SIGNATURE_INPUT_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" owners=1024 errors="+report.count+" retained=2 rows="+index.counts().rows());}
                }
            }
        }
        assertEquals(0,pages.statistics().livePages());
    }
    private static final class Report implements SnapshotSignatureIndex.Reports {
        long capacity,count;final ArrayList<Long> ordinals=new ArrayList<>();
        Report(long capacity){this.capacity=capacity;}
        public long remaining(){return capacity;}
        public void occurrences(SnapshotSignatureIndex.Rule rule,long owner,long count){assertEquals(SnapshotSignatureIndex.Rule.POSITIONS,rule);this.count=Math.addExact(this.count,count);}
        public void retain(SnapshotSignatureIndex.Rule rule,long owner,long row,long position,long ordinal){assertTrue(capacity>0);capacity--;ordinals.add(ordinal);}
    }
    @Test void everyWorkInterruptionAndDeniedConstructorReleasesTransferredPages() {
        for(long heap:new long[]{1000,4000,7000,10000,13000,16000}) {
            var resources=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,resources)){long initial=resources.heapUsed();assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotSignatureStorage(pages,resources)).phase());assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());}
            assertEquals(0,resources.heapUsed());
        }
        var measured=resources(65536,1_000_000);long calls;
        try(var pages=new MemoryPageStore(128,measured);var store=new PagedSnapshotSignatureStorage(pages,measured)){sequence(store);calls=measured.workUsed();}
        for(long budget=0;budget<calls;budget++) {
            var resources=resources(65536,budget);
            try(var pages=new MemoryPageStore(128,resources)){var store=new PagedSnapshotSignatureStorage(pages,resources);try{assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->sequence(store)).resource());assertThrows(IllegalStateException.class,()->store.find(source(0),0));}finally{store.close();}assertEquals(0,pages.statistics().livePages());}
            assertEquals(0,resources.heapUsed());
        }
    }
    private static void sequence(PagedSnapshotSignatureStorage store) {
        assertEquals(0,store.find(source(0),0));long table=store.begin(source(0),0);store.member(table,source(1));store.member(table,source(1));store.issue(table,source(2),source(1),1,true,true);store.finish(table,2);
        assertEquals(table,store.find(source(0),0));assertEquals(2,store.rows(table));assertEquals(1,store.issues(table,false));assertTrue(store.contains(table,source(1)));assertFalse(store.contains(table,source(99)));store.select(table,true);assertTrue(store.advanceIssue());assertEquals(source(2),store.row());assertEquals(source(1),store.position());assertEquals(1,store.ordinal());assertFalse(store.advanceIssue());store.select(table,false);assertTrue(store.advanceIssue());
    }
    @Test void unfinishedTablesWrongOrderAndFrozenWritesNeverPublishFacts() {
        for(int bad=0;bad<5;bad++) {
            var resources=resources(65536,1_000_000);final int at=bad;
            try(var pages=new MemoryPageStore(128,resources);var store=new PagedSnapshotSignatureStorage(pages,resources)) {
                long table=store.begin(source(0),0);
                assertThrows(IllegalStateException.class,()->{switch(at){case 0->store.find(source(0),0);case 1->store.begin(source(0),0);case 2->{store.issue(table,source(1),source(2),1,false,true);store.issue(table,source(2),source(3),1,true,false);}case 3->{store.issue(table,source(1),source(2),1,false,true);store.finish(table,1);}default->{store.finish(table,0);store.member(table,source(1));}}});
                assertThrows(IllegalStateException.class,()->store.find(source(0),0));
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    private record CopyKey(long node,AirShape element) { }
    // Test-only shallow copy preserves the input DAG. Publication/memo are outside decoder claims.
    private static long copyShared(AirSnapshot source, long node, AirShape element, AirSnapshotBuilder target,java.util.Map<CopyKey,Long> memo) {
        AirShape shape = source.shape(node);
        var key=new CopyKey(node,shape==AirShape.LIST||shape==AirShape.OPTIONAL?element:null);
        Long known=memo.get(key);if(known!=null)return known;
        long result=switch (shape.form()) {
            case RECORD -> {
                long[] children = new long[shape.fieldCount()];
                for (int n = 0; n < children.length; n++) children[n] = copyShared(source, source.field(node, shape, n), shape.field(n).element(), target,memo);
                yield target.record(shape, children);
            }
            case LIST -> {
                try (var list = target.list(element)) {
                    for (long n = 0; n < source.size(node); n++) list.add(copyShared(source, source.element(node, element, n), null, target,memo));
                    yield list.finish();
                }
            }
            case OPTIONAL -> target.optional(element, source.size(node) == 0 ? 0 : copyShared(source, source.element(node, element, 0), null, target,memo));
            case TEXT, INTEGER -> {
                try (var text = target.text(shape)) {
                    char[] block = new char[128]; long at = 0;
                    while (at < source.characterCount(node)) { int n = source.readCharacters(node, at, block, 0, block.length); text.append(block, 0, n); at += n; }
                    yield text.finish();
                }
            }
            case BOOLEAN, SMALL_INTEGER, ENUM -> target.scalar(shape, source.scalar(node));
            case UNION -> throw new AssertionError("concrete value required");
        };
        memo.put(key,result);return result;
    }
}
