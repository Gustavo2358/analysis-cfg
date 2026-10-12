package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exact posting/complement oracles, managed residence and input/projection composition. */
final class PagedSnapshotLabelStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap,long work){return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,2,work,1_000_000));}
    private static long source(int n){return (1L<<44)+37L*n;}
    @Test void coldSortedPostingsAndComplementsPreserveDuplicateRowsWithoutQueryHistory() {
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(65536,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)){assertCold(memory,memoryResources);assertCold(file,fileResources);}
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    private static void assertCold(PageStore pages,AnalysisResources resources) {
        int size=4096;
        try(var store=new PagedSnapshotLabelStorage(pages,resources)) {
            long table=store.begin(source(0));for(int n=0;n<size;n++)store.row(table,source(n/2+1),source(5000+n%8),n%31==0);store.finish(table);
            assertEquals(size,store.rows(table));assertEquals((size+30)/31,store.missing(table));
            for(int n=0;n<(size+30)/31;n++)assertEquals(31L*n,store.missingAt(table,n));
            for(int unit=0;unit<8;unit++){assertEquals(size/8,store.matching(table,source(5000+unit)));int at=0;for(int row=0;row<size;row++)if(row%8!=unit){assertEquals(row,store.foreignAt(table,source(5000+unit),at++));assertEquals(source(row/2+1),store.label(table,row));}}
            long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed(),work=resources.workUsed();
            for(int q=0;q<4096;q++){assertEquals(0,store.find(source(10_000+q)));assertEquals(0,store.matching(table,source(20_000+q)));assertEquals(q,store.foreignAt(table,source(20_000+q),q));assertEquals(table,store.find(source(0)));}
            assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));
            if(pages instanceof FilePageStore){assertEquals(heap,resources.heapUsed());assertTrue(temporary>65536);assertTrue(resources.heapPeak()<=65536);System.out.println("SNAPSHOT_LABEL_STORAGE_METRICS heap="+resources.heapPeak()+" temporary="+temporary+" rows="+size+" queryWork="+(resources.workUsed()-work));}
        }
        assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
    }
    @Test void thousandsOfUnitBucketsUseSharedPagesAndLateForeignSelectsByRank() {
        var resources=resources(65536,1_000_000_000);int size=4096;
        try(var pages=new FilePageStore(directory,128,1,resources);var store=new PagedSnapshotLabelStorage(pages,resources)) {
            long table=store.begin(source(0));for(int n=0;n<size;n++)store.row(table,source(n+1),source(10_000+n),false);store.finish(table);
            long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY),heap=resources.heapUsed();
            for(int n=0;n<size;n++){assertEquals(1,store.matching(table,source(10_000+n)));assertEquals(n==0?1:0,store.foreignAt(table,source(10_000+n),0));assertEquals(n==size-1?size-2:size-1,store.foreignAt(table,source(10_000+n),size-2));}
            assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));assertEquals(heap,resources.heapUsed());assertTrue(temporary>65536);assertTrue(resources.heapPeak()<=65536);
            long second=store.begin(source(1));for(int n=0;n<size-1;n++)store.row(second,source(n+1),source(20_000),false);store.row(second,source(size),source(20_001),false);store.finish(second);
            long before=resources.workUsed();assertEquals(size-1,store.foreignAt(second,source(20_000),0));assertTrue(resources.workUsed()-before<4096,"late foreign lookup must use posting gaps, never scan rows");
            System.out.println("SNAPSHOT_LABEL_BUCKET_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" buckets="+size+" lateSelectWork="+(resources.workUsed()-before));
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void typedSharedLocalScopesAndProjectedErrorsMatchBothInputBackends() {
        Publication p=sharedScopePublication();
        var memoryResources=resources(32_000_000,1_000_000_000);var fileResources=resources(131072,1_000_000_000);
        try(var memory=new MemoryPageStore(128,memoryResources);var file=new FilePageStore(directory,128,1,fileResources)){assertTyped(p,memory,memoryResources,false);assertTyped(p,file,fileResources,true);}
        assertEquals(0,memoryResources.heapUsed());assertEquals(0,fileResources.heapUsed());
    }
    static Publication sharedScopePublication() {
        Publication p=PagedAirStorageTest.publication("local labels");Unit body=p.units().get(0);var labels=new ArrayList<Ids.LabelId>();
        var foreign=new Ids.UnitId(new Ids.PublicationId("other"),body.id().localId());
        for(int n=0;n<512;n++)labels.add(n%4==0?new Ids.LabelId(foreign,"missing"):body.sequences().get(0).label());
        var control=new Control.ControlEnvelope(List.of(),new Scopes.WithinControl(new Scopes.LabelsControl(labels)));
        var envelope=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),control,new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        var seed=body.sequences().get(0);var op=new Operations.Opaque(seed.terminator().header(),"synthetic",List.of(),List.of(),envelope);
        var sequence=new Sequence(seed.label(),List.of(),op,body.origin());
        var unit=new Unit(body.id(),body.containingUnit(),body.objects(),body.visibleObjects(),body.entries(),List.of(sequence),body.completionPorts(),body.body(),body.bodyUnavailable(),body.coverage(),body.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        return p;
    }
    private static void assertTyped(Publication p,PageStore pages,AnalysisResources resources,boolean paged) {
        try(var resident=AirSnapshot.fromPublication(p);var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(resident,resident.root(),null,builder);
            try(var managed=builder.finish(root)) {
                AirSnapshot input=paged?managed:resident;
                try(var keys=new SnapshotIdentityKeys(input,new PagedSnapshotIdentityStorage(pages,resources));
                    var declarations=SnapshotDeclarations.build(input,keys,new PagedSnapshotDeclarationsStorage(pages,resources),Long.MAX_VALUE,Long.MAX_VALUE,(r,i,n)->fail(r.toString()));
                    var index=new SnapshotLocalLabels(input,keys,declarations,new PagedSnapshotLabelStorage(pages,resources));
                    var tape=new SnapshotDiagnosticTemplates(new PagedSnapshotDiagnosticStorage(pages,resources),index)) {
                    long unit=input.element(input.field(input.root(),AirShape.PUBLICATION,4),AirShape.UNIT,0);
                    long owner=input.field(unit,AirShape.UNIT,0),sequence=input.element(input.field(unit,AirShape.UNIT,5),AirShape.SEQUENCE,0),op=input.field(sequence,AirShape.SEQUENCE,2);
                    long envelope=input.field(op,AirShape.OPERATIONS_OPAQUE,4),control=input.field(envelope,AirShape.ENVELOPES_ENVELOPE,1),remainder=input.field(control,AirShape.CONTROL_CONTROL_ENVELOPE,1),scope=input.field(remainder,AirShape.SCOPES_WITHIN_CONTROL,0),list=input.field(scope,AirShape.SCOPES_LABELS_CONTROL,0);
                    long label=input.element(list,AirShape.IDS_LABEL_ID,0),foreign=input.field(label,AirShape.IDS_LABEL_ID,0);
                    long a=index.template(list,owner,tape),b=index.template(list,foreign,tape);var report=new Report(4);tape.emit(a,source(1),report);assertEquals(256,report.count);assertEquals(List.of(1,2,1,2),report.rules);
                    report=new Report(2);tape.emit(b,source(2),report);assertEquals(512,report.count);assertEquals(List.of(1,2),report.rules);
                    long live=pages.statistics().livePages(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY);for(int q=0;q<1024;q++){var none=new Report(0);tape.emit(index.template(list,owner,tape),source(q),none);assertEquals(256,none.count);}
                    assertEquals(live,pages.statistics().livePages());assertEquals(temporary,resources.used(AnalysisResources.Pool.TEMPORARY));assertEquals(1,index.counts().lists());assertEquals(512,index.counts().rows());
                    if(paged){assertTrue(resources.heapPeak()<=131072);assertTrue(temporary>131072);System.out.println("SNAPSHOT_LABEL_INPUT_METRICS heap="+resources.heapPeak()+" temporary="+temporary+" rows=512 contexts=1026 errors=256 retained=4");}
                }
            }
        }
        assertEquals(0,pages.statistics().livePages());
    }
    private static final class Report implements SnapshotDiagnosticTemplates.Reports {
        long capacity,count;final List<Integer> rules=new ArrayList<>();Report(long capacity){this.capacity=capacity;}
        public long remaining(){return capacity;}
        public void occurrences(ValidationIssue.Kind kind,long owner,long count){assertEquals(ValidationIssue.Kind.INVALID_IR,kind);this.count+=count;}
        public void retain(ValidationIssue.Kind kind,int rule,long owner,long anchor,int field,long detail){assertEquals(ValidationIssue.Kind.INVALID_IR,kind);assertEquals(-1,field);assertEquals(anchor,detail);assertTrue(capacity-->0);rules.add(rule);}
    }
    @Test void everyWorkInterruptionAndUnfinishedMutationFailsClosedAndReleasesPages() {
        var measured=resources(65536,1_000_000);long work;
        try(var pages=new MemoryPageStore(128,measured);var store=new PagedSnapshotLabelStorage(pages,measured)){sequence(store);work=measured.workUsed();}
        for(long budget=0;budget<work;budget++) {
            var resources=resources(65536,budget);
            try(var pages=new MemoryPageStore(128,resources);var store=new PagedSnapshotLabelStorage(pages,resources)){assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->sequence(store)).resource());assertThrows(IllegalStateException.class,()->store.find(source(0)));}
            assertEquals(0,resources.heapUsed());
        }
        for(int fault=0;fault<4;fault++) {
            var resources=resources(65536,1_000_000);final int at=fault;
            try(var pages=new MemoryPageStore(128,resources);var store=new PagedSnapshotLabelStorage(pages,resources)) {long table=store.begin(source(0));assertThrows(IllegalStateException.class,()->{switch(at){case 0->store.find(source(0));case 1->store.begin(source(0));case 2->store.begin(source(1));default->{store.finish(table);store.row(table,source(1),source(2),false);}}});assertThrows(IllegalStateException.class,()->store.find(source(9)));}
            assertEquals(0,resources.heapUsed());
        }
    }
    @Test void deniedConstructionAndEmptyOrdinalQueriesNeverLeakOrPublishPartialTables() {
        for(long heap:new long[]{1000,3000,5000,7000}) {
            var resources=resources(heap,1_000_000);
            try(var pages=new FilePageStore(directory,128,1,resources)){long initial=resources.heapUsed();assertEquals(AnalysisResources.Phase.VALIDATION,assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotLabelStorage(pages,resources)).phase());assertEquals(initial,resources.heapUsed());assertEquals(0,pages.statistics().livePages());}
            assertEquals(0,resources.heapUsed());
        }
        for(int fault=0;fault<3;fault++) {
            var resources=resources(65536,1_000_000);final int at=fault;
            try(var pages=new MemoryPageStore(128,resources);var store=new PagedSnapshotLabelStorage(pages,resources)) {
                long table=store.begin(source(0));store.finish(table);assertEquals(table,store.find(source(0)));assertEquals(0,store.rows(table));assertEquals(0,store.missing(table));assertEquals(0,store.matching(table,source(1)));
                assertThrows(IndexOutOfBoundsException.class,()->{switch(at){case 0->store.label(table,0);case 1->store.missingAt(table,0);default->store.foreignAt(table,source(1),0);}});assertThrows(IllegalStateException.class,()->store.find(source(0)));
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    private static void sequence(PagedSnapshotLabelStorage store){assertEquals(0,store.find(source(0)));long table=store.begin(source(0));store.row(table,source(1),source(2),true);store.row(table,source(1),source(3),false);store.finish(table);assertEquals(2,store.rows(table));assertEquals(1,store.missing(table));assertEquals(1,store.matching(table,source(2)));assertEquals(0,store.missingAt(table,0));assertEquals(1,store.foreignAt(table,source(2),0));assertEquals(0,store.foreignAt(table,source(3),0));assertEquals(source(1),store.label(table,1));}
}
