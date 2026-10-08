package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Small literal sequence oracle and huge algebraic multiplicity, separate from full admission. */
final class PagedSnapshotDiagnosticStorageTest {
    @TempDir Path directory;
    private static final ValidationIssue.Kind[] KINDS=ValidationIssue.Kind.values();
    private static AnalysisResources resources(long heap,long work){return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));}
    private static long source(int n){return (1L<<44)+37L*n;}
    @Test void coldBalancedSequencesPreserveKindsAnchorsAndRepeatedPrefixResidence() {
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)){assertCold(memory,memoryResources);assertCold(file,fileResources);assertTrue(fileResources.heapPeak()<=65536);assertTrue(file.statistics().evictions()>1000);}
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertCold(PageStore pages,AnalysisResources resources) {
        long before=resources.heapUsed();int size=512;
        try(var tape=new SnapshotDiagnosticTemplates(new PagedSnapshotDiagnosticStorage(pages,resources))) {
            long root=0;for(int n=0;n<size;n++)root=tape.concat(root,tape.leaf(KINDS[n%5],100+n,source(n),n%7-1,n+1L));
            assertEquals(size,tape.size(root));assertTrue(tape.height(root)<=19);var complete=new Report(size);tape.emit(root,source(2000),complete);assertLiteral(complete,size,source(2000));
            long live=pages.statistics().livePages(),temp=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed(),work=resources.workUsed();
            for(int q=0;q<512;q++){var r=new Report(3);tape.emit(root,source(3000+q),r);assertLiteral(r,3,source(3000+q));assertEquals(size,r.total());var empty=new Report(0);tape.emit(root,source(3000+q),empty);assertEquals(size,empty.total());assertEquals(0,empty.items.size());}
            assertEquals(live,pages.statistics().livePages());assertEquals(temp,resources.used(AnalysisResources.Pool.TEMPORARY));
            if(pages instanceof FilePageStore){assertEquals(heap,resources.heapUsed());assertTrue(temp>65536);System.out.println("SNAPSHOT_DIAGNOSTIC_STORAGE_METRICS heap="+resources.heapPeak()+" temporary="+temp+" leaves="+size+" queries=1024 queryWork="+(resources.workUsed()-work)+" height="+tape.height(root));}
        }
        assertEquals(0,pages.statistics().livePages());if(pages instanceof FilePageStore)assertEquals(before,resources.heapUsed());long borrowed=pages.allocate();pages.release(borrowed);
    }
    private static void assertLiteral(Report report,int retained,long owner) {
        assertEquals(retained,report.items.size());assertEquals(owner,report.owner);
        for(int n=0;n<retained;n++){var item=report.items.get(n);assertEquals(KINDS[n%5],item.kind());assertEquals(100+n,item.rule());assertEquals(source(n),item.anchor());assertEquals(n%7-1,item.field());assertEquals(n+1L,item.detail());}
    }
    @Test void enormousSharedMultiplicityEmitsOnlyRequestedAnchorsAndRejectsOverflow() {
        var resources=resources(65536,1_000_000);
        try(var pages=new FilePageStore(directory,128,1,resources);var tape=new SnapshotDiagnosticTemplates(new PagedSnapshotDiagnosticStorage(pages,resources))) {
            long root=tape.leaf(ValidationIssue.Kind.INVALID_IR,101,source(0),-1,source(1));for(int n=0;n<62;n++)root=tape.concat(root,root);
            assertEquals(1L<<62,tape.size(root));assertEquals(63,tape.height(root));long live=pages.statistics().livePages(),temp=resources.used(AnalysisResources.Pool.TEMPORARY);
            var report=new Report(3);tape.emit(root,source(20),report);assertEquals(1L<<62,report.total());assertEquals(3,report.items.size());for(var item:report.items){assertEquals(101,item.rule());assertEquals(source(0),item.anchor());assertEquals(-1,item.field());assertEquals(source(1),item.detail());}
            assertEquals(live,pages.statistics().livePages());assertEquals(temp,resources.used(AnalysisResources.Pool.TEMPORARY));long frozen=root;
            assertThrows(ArithmeticException.class,()->tape.concat(frozen,frozen));assertThrows(IllegalStateException.class,()->tape.size(frozen));
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void everyWorkInterruptionAndDeniedControlClosesTransferredTupleState() {
        for(long heap:new long[]{1000,3000,5000,7000}) {
            var resources=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,resources)){long initial=resources.heapUsed();assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotDiagnosticStorage(pages,resources)).phase());assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());}
            assertEquals(0,resources.heapUsed());
        }
        var denied=resources(65536,1_000_000);
        try(var pages=new FilePageStore(directory,128,1,denied)) {
            var port=new PagedSnapshotDiagnosticStorage(pages,denied);
            try(var fill=denied.reserve(AnalysisResources.Pool.RESIDENT,65536-denied.heapUsed()-1024,AnalysisResources.Phase.VALIDATION)) {
                assertTrue(fill.amount()>0);assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new SnapshotDiagnosticTemplates(port)).phase());assertThrows(IllegalStateException.class,()->port.word(1,SnapshotDiagnosticTemplates.Word.TOTAL));port.close();assertEquals(0,pages.statistics().livePages());
            }
        }
        assertEquals(0,denied.heapUsed());
        var measured=resources(65536,1_000_000);long calls;
        try(var pages=new MemoryPageStore(128,measured);var tape=new SnapshotDiagnosticTemplates(new PagedSnapshotDiagnosticStorage(pages,measured))){sequence(tape);calls=measured.workUsed();}
        for(long budget=0;budget<calls;budget++) {
            var resources=resources(65536,budget);
            try(var pages=new MemoryPageStore(128,resources);var tape=new SnapshotDiagnosticTemplates(new PagedSnapshotDiagnosticStorage(pages,resources))) {
                assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->sequence(tape)).resource());assertThrows(IllegalStateException.class,()->tape.size(0));
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    private static void sequence(SnapshotDiagnosticTemplates tape) {
        long a=tape.leaf(ValidationIssue.Kind.INVALID_IR,101,source(0),-1,source(1)),b=tape.leaf(ValidationIssue.Kind.SEMANTIC_OBLIGATION,102,source(1),2,0),root=tape.concat(a,b);
        assertEquals(2,tape.size(root));assertEquals(1,tape.count(root,ValidationIssue.Kind.SEMANTIC_OBLIGATION));var report=new Report(2);tape.emit(root,source(9),report);assertEquals(2,report.total());assertEquals(101,report.items.get(0).rule());assertEquals(102,report.items.get(1).rule());
    }
    private record Item(ValidationIssue.Kind kind,int rule,long anchor,int field,long detail) { }
    private static final class Report implements SnapshotDiagnosticTemplates.Reports {
        long capacity,owner;final long[] counts=new long[5];final ArrayList<Item> items=new ArrayList<>();
        Report(long capacity){this.capacity=capacity;}
        public long remaining(){return capacity;}
        public void occurrences(ValidationIssue.Kind kind,long owner,long count){this.owner=owner;counts[kind.ordinal()]=Math.addExact(counts[kind.ordinal()],count);}
        public void retain(ValidationIssue.Kind kind,int rule,long owner,long anchor,int field,long detail){assertTrue(capacity>0);capacity--;this.owner=owner;items.add(new Item(kind,rule,anchor,field,detail));}
        long total(){long total=0;for(long count:counts)total=Math.addExact(total,count);return total;}
    }
}
