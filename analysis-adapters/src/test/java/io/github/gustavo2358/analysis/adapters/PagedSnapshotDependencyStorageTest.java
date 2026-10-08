package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.DependencyProgramStore;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PagedSnapshotDependencyStorageTest {
    @Test void callCursorOrdersPageBackedSitesWithBoundedManagedHeap() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,64_000_000,4,1_000_000_000,1_000_000));
        var directory=Files.createTempDirectory("snapshot-dependency-sites-");
        try(var pages=new FilePageStore(directory,128,1,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
            var publication=new PublicationId("cursor-sites");var unit=new UnitId(publication,"caller");
            for(int i=1023;i>=0;i--) {
                String suffix=String.format("%04d",i);
                storage.addCall(new DependencyProgramStore.ComputedCall(1,unit,new EntryId(unit,"entry-"+suffix),
                    new LabelId(unit,"sequence-"+suffix),new OperationId(unit,"operation-"+suffix),
                    new OriginId(publication,"site-"+suffix),new OriginId(publication,"target-"+suffix),
                    Evidence.CoverageStatus.MODELED,"cobol.program",new ObjectId(unit,"subject-"+suffix)));
            }
            storage.selectCalls();long count=0;
            while(storage.advanceCall())assertEquals("entry-"+String.format("%04d",count++),storage.callEntry());
            assertEquals(1024,count);assertEquals(1024,storage.callCount());
            assertTrue(resources.heapPeak()<=65_536);assertTrue(pages.statistics().evictions()>1000);
            System.out.println("SNAPSHOT_DEPENDENCY_CURSOR_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" sites="+count+" evictions="+pages.statistics().evictions());
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,resources.heapUsed());
    }

    @Test void candidateLowerBoundReadsOnlyOneObjectsSortedPayload() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,64_000_000,4,1_000_000_000,1_000_000));
        var directory=Files.createTempDirectory("snapshot-dependency-candidates-");
        try(var pages=new FilePageStore(directory,128,1,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
            for(int i=1023;i>=0;i--){String value="value-"+String.format("%04d",i);storage.add(7,value,java.util.List.of(new DependencyProgramStore.Producer(i+1,i+2)));storage.add(9,"decoy-"+value,java.util.List.of(new DependencyProgramStore.Producer(i+3,i+4)));}
            storage.select(7);long count=0;
            while(storage.advance()){assertEquals("value-"+String.format("%04d",count),storage.rawText());assertEquals(count+1,storage.producer(0));count++;}
            assertEquals(1024,count);assertTrue(resources.heapPeak()<=65_536);assertTrue(pages.statistics().evictions()>1000);
            System.out.println("SNAPSHOT_DEPENDENCY_CANDIDATE_CURSOR_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" candidates="+count+" evictions="+pages.statistics().evictions());
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,resources.heapUsed());
    }
}
