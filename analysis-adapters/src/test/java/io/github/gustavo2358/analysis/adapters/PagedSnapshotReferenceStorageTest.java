package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Successful empty roots are facts, not misses; template handles are borrowed literal values. */
final class PagedSnapshotReferenceStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work){return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));}
    private static long source(int n){return (1L<<44)+37L*n;}
    @Test void coldSuccessfulAndInvalidRootsHaveExactRecipeKeysAndReadonlyMisses() {
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)){assertCold(memory,memoryResources);assertCold(file,fileResources);}
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertCold(PageStore pages,AnalysisResources resources) {
        int size=8192;
        try(var memo=new PagedSnapshotReferenceStorage(pages,resources)) {
            for(int n=0;n<size;n++){long table=memo.begin(source(n),n%3);memo.finish(table,n%2==0?0:source(n+size));}
            long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed(),work=resources.workUsed();
            for(int n=0;n<2*size;n++){int at=n%size;long table=memo.find(source(at),at%3);assertTrue(table>0);assertEquals(at%2==0?0:source(at+size),memo.root(table));assertEquals(0,memo.find(source(at),(at+1)%3));assertEquals(0,memo.find(source(20_000+n),n%3));}
            assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));
            if(pages instanceof FilePageStore){assertEquals(heap,resources.heapUsed());assertTrue(temporary>65536);assertTrue(resources.heapPeak()<=65536);System.out.println("SNAPSHOT_REFERENCE_STORAGE_METRICS heap="+resources.heapPeak()+" temporary="+temporary+" roots="+size+" reads="+(2*size)+" queryWork="+(resources.workUsed()-work));}
        }
        assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
    }
    @Test void typedReferenceRootsAndCarryAssemblyPreserveBothInputBackends() {
        var p=PagedSnapshotLabelStorageTest.sharedScopePublication();var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(131072,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)){assertTyped(p,memory,memoryResources,false);assertTyped(p,file,fileResources,true);}
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertTyped(Publication p,PageStore pages,AnalysisResources resources,boolean paged) {
        try(var resident=AirSnapshot.fromPublication(p);var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
            long built=PagedAirStorageTest.copy(resident,resident.root(),null,builder);
            try(var managed=builder.finish(built)) {
                AirSnapshot input=paged?managed:resident;
                try(var keys=new SnapshotIdentityKeys(input,new PagedSnapshotIdentityStorage(pages,resources));
                    var declarations=SnapshotDeclarations.build(input,keys,new PagedSnapshotDeclarationsStorage(pages,resources),Long.MAX_VALUE,Long.MAX_VALUE,(r,i,n)->fail(r.toString()));
                    var tape=new SnapshotDiagnosticTemplates(new PagedSnapshotDiagnosticStorage(pages,resources));
                    var refs=new SnapshotReferenceLists(input,declarations,tape,new PagedSnapshotReferenceStorage(pages,resources))) {
                    long unit=input.element(input.field(input.root(),AirShape.PUBLICATION,4),AirShape.UNIT,0),sequence=input.element(input.field(unit,AirShape.UNIT,5),AirShape.SEQUENCE,0),op=input.field(sequence,AirShape.SEQUENCE,2);
                    long owner=input.field(input.field(op,AirShape.OPERATIONS_OPAQUE,0),AirShape.OPERATIONS_HEADER,0),envelope=input.field(op,AirShape.OPERATIONS_OPAQUE,4),control=input.field(envelope,AirShape.ENVELOPES_ENVELOPE,1),remainder=input.field(control,AirShape.CONTROL_CONTROL_ENVELOPE,1),scope=input.field(remainder,AirShape.SCOPES_WITHIN_CONTROL,0),list=input.field(scope,AirShape.SCOPES_LABELS_CONTROL,0);
                    var expected=new ArrayList<Long>();for(int n=0;n<512;n+=4)expected.add(input.element(list,AirShape.IDS_LABEL_ID,n));
                    long root=refs.references(list,AirShape.IDS_LABEL_ID);var report=new Report(2);tape.emit(root,owner,report);assertEquals(128,report.count);assertEquals(expected.subList(0,2),report.ids);assertEquals(owner,report.owner);
                    long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY);for(int q=0;q<1024;q++){assertEquals(root,refs.references(list,AirShape.IDS_LABEL_ID));tape.emit(root,owner,report);}
                    assertEquals(128L*1025,report.count);assertEquals(expected.subList(0,2),report.ids);assertEquals(1,refs.counts().lists());assertEquals(512,refs.counts().rows());assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));
                    if(paged){assertTrue(resources.heapPeak()<=131072);assertTrue(temporary>131072);System.out.println("SNAPSHOT_REFERENCE_INPUT_METRICS heap="+resources.heapPeak()+" temporary="+temporary+" rows=512 uses=1025 errors="+report.count+" retained=2");}
                }
            }
        }
        assertEquals(0,pages.statistics().livePages());
    }
    private static final class Report implements SnapshotDiagnosticTemplates.Reports {
        long capacity,count,owner;final List<Long> ids=new ArrayList<>();Report(long capacity){this.capacity=capacity;}
        public long remaining(){return capacity;}public void occurrences(ValidationIssue.Kind kind,long owner,long count){assertEquals(ValidationIssue.Kind.INVALID_IR,kind);this.count+=count;this.owner=owner;}
        public void retain(ValidationIssue.Kind kind,int rule,long owner,long anchor,int field,long detail){assertEquals(1,rule);assertEquals(-1,field);assertEquals(anchor,detail);assertEquals(this.owner,owner);assertTrue(capacity-->0);ids.add(anchor);}
    }
    @Test void everyPrimitiveWorkFailureAbortsTheMemoAndReleasesPages() {
        var measured=resources(65536,1_000_000);long work;
        try(var pages=new MemoryPageStore(128,measured);var memo=new PagedSnapshotReferenceStorage(pages,measured)){sequence(memo);work=measured.workUsed();}
        for(long budget=0;budget<work;budget++) {
            var resources=resources(65536,budget);
            try(var pages=new MemoryPageStore(128,resources);var memo=new PagedSnapshotReferenceStorage(pages,resources)){assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->sequence(memo)).resource());assertThrows(IllegalStateException.class,()->memo.find(source(0),0));}
            assertEquals(0,resources.heapUsed());
        }
    }
    @Test void unfinishedReuseAndDoubleFinishNeverBecomeSuccessfulEmptyEvidence() {
        for(int fault=0;fault<5;fault++) {
            var resources=resources(65536,1_000_000);final int at=fault;
            try(var pages=new MemoryPageStore(128,resources);var memo=new PagedSnapshotReferenceStorage(pages,resources)) {long table=memo.begin(source(0),0);assertThrows(IllegalStateException.class,()->{switch(at){case 0->memo.find(source(0),0);case 1->memo.begin(source(0),0);case 2->memo.root(table);case 3->{memo.finish(table,0);memo.finish(table,0);}default->memo.root(table+100);}});assertThrows(IllegalStateException.class,()->memo.find(source(1),0));}
            assertEquals(0,resources.heapUsed());
        }
    }
    @Test void deniedConstructionReturnsEveryTransferredLease() {
        for(long heap:new long[]{1000,3000,5000,7000}) {
            var resources=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,resources)){long initial=resources.heapUsed();assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotReferenceStorage(pages,resources)).phase());assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());}
            assertEquals(0,resources.heapUsed());
        }
    }
    private static void sequence(PagedSnapshotReferenceStorage memo){assertEquals(0,memo.find(source(0),0));long empty=memo.begin(source(0),0);memo.finish(empty,0);long full=memo.begin(source(0),1);memo.finish(full,source(9));assertEquals(empty,memo.find(source(0),0));assertEquals(full,memo.find(source(0),1));assertEquals(0,memo.root(empty));assertEquals(source(9),memo.root(full));assertEquals(0,memo.find(source(1),0));}
}
