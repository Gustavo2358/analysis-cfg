package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.DependencyProgramStore;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PagedSnapshotDependencyStorageTest {
    @Test void metadataHandlesArePageBackedAndOrderedWithoutMaterializingCollections() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(131_072,131_072,0,64_000_000,4,1_000_000_000,1_000_000));
        var directory=Files.createTempDirectory("snapshot-dependency-metadata-");
        try(var pages=new FilePageStore(directory,128,1,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
            for(int i=1023;i>=0;i--){String suffix=String.format("%04d",i);storage.addOrigin(i+1,"origin-"+suffix);storage.addArtifact(i+2049,"artifact-"+suffix);}
            for(int i=4095;i>=0;i--)storage.addOriginInput(1,i+4097,"input-"+String.format("%04d",i));
            storage.selectOrigins();long origin=1;while(storage.advanceOrigin())assertEquals(origin++,storage.originHandle());assertEquals(1025,origin);
            storage.selectArtifacts();long artifact=2049;while(storage.advanceArtifact())assertEquals(artifact++,storage.artifactHandle());assertEquals(3073,artifact);
            storage.selectOriginInputs(1);long input=4097;while(storage.advanceOriginInput())assertEquals(input++,storage.originInputHandle());assertEquals(8193,input);
            storage.selectOriginInputs(2);assertFalse(storage.advanceOriginInput());
            assertTrue(resources.heapPeak()<=131_072);assertTrue(pages.statistics().evictions()>1000);
            System.out.println("SNAPSHOT_DEPENDENCY_METADATA_CURSOR_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" records=6144 evictions="+pages.statistics().evictions());
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,resources.heapUsed());
    }

    @Test void callCursorOrdersPageBackedSitesWithBoundedManagedHeap() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,64_000_000,4,1_000_000_000,1_000_000));
        var directory=Files.createTempDirectory("snapshot-dependency-sites-");
        try(var pages=new FilePageStore(directory,128,1,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
            var publication=new PublicationId("cursor-sites");var unit=new UnitId(publication,"caller");
            storage.add(1,"TARGET-A");storage.add(1,"TARGET-B");
            for(int i=1023;i>=0;i--) {
                String suffix=String.format("%04d",i);
                storage.addCall(new DependencyProgramStore.ComputedCall(1,unit,new EntryId(unit,"entry-"+suffix),
                    new LabelId(unit,"sequence-"+suffix),new OperationId(unit,"operation-"+suffix),
                    new OriginId(publication,"site-"+suffix),new OriginId(publication,"target-"+suffix),
                    Evidence.CoverageStatus.MODELED,"cobol.program",new ObjectId(unit,"subject-"+suffix)));
            }
            storage.selectCalls();long count=0;
            while(storage.advanceCall()){assertEquals("entry-"+String.format("%04d",count++),storage.callEntry());assertEquals(2,storage.callCandidateCount());}
            assertEquals(1024,count);assertEquals(1024,storage.callCount());assertEquals(2048,storage.candidateCount());assertEquals(0,storage.unknownRemainderCount());
            assertTrue(resources.heapPeak()<=65_536);assertTrue(pages.statistics().evictions()>1000);
            System.out.println("SNAPSHOT_DEPENDENCY_CURSOR_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" sites="+count+" evictions="+pages.statistics().evictions());
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,resources.heapUsed());
    }

    @Test void candidateLowerBoundReadsOnlyOneObjectsSortedPayload() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,64_000_000,4,1_000_000_000,1_000_000));
        var directory=Files.createTempDirectory("snapshot-dependency-candidates-");
        try(var pages=new FilePageStore(directory,128,1,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
            var publication=new PublicationId("cursor-candidates");var unit=new UnitId(publication,"unit");
            for(int i=1023;i>=0;i--){String value="value-"+String.format("%04d",i);long candidate=storage.add(7,value);storage.addSupport(candidate,new OperationId(unit,"producer-"+i),new OriginId(publication,"origin-"+i));
                long decoy=storage.add(9,"decoy-"+value);storage.addSupport(decoy,new OperationId(unit,"decoy-"+i),new OriginId(publication,"decoy-origin-"+i));}
            storage.select(7);long count=0;
            while(storage.advance()){assertEquals("value-"+String.format("%04d",count),storage.rawText());storage.selectSupports(storage.candidate());assertTrue(storage.advanceSupport());assertEquals("producer-"+count,storage.supportProducer().localId());count++;}
            assertEquals(1024,count);assertTrue(resources.heapPeak()<=65_536);assertTrue(pages.statistics().evictions()>1000);
            System.out.println("SNAPSHOT_DEPENDENCY_CANDIDATE_CURSOR_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" candidates="+count+" evictions="+pages.statistics().evictions());
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,resources.heapUsed());
    }

    @Test void supportCursorDeduplicatesAndOrdersOneCandidatesSpilledProofs() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,64_000_000,4,1_000_000_000,1_000_000));
        var directory=Files.createTempDirectory("snapshot-dependency-supports-");
        try(var pages=new FilePageStore(directory,128,1,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
            var publication=new PublicationId("cursor-supports");var unit=new UnitId(publication,"unit");long candidate=storage.add(7,"TARGET"),decoy=storage.add(9,"DECOY");
            for(int i=1023;i>=0;i--){String suffix=String.format("%04d",i);var producer=new OperationId(unit,"producer-"+suffix);var origin=new OriginId(publication,"origin-"+suffix);
                storage.addSupport(candidate,producer,origin);storage.addSupport(candidate,producer,origin);
                storage.addSupport(decoy,new OperationId(unit,"decoy-"+suffix),new OriginId(publication,"decoy-origin-"+suffix));}
            storage.selectSupports(candidate);long insertion=1023;
            while(storage.advanceSupport())assertEquals("producer-"+String.format("%04d",insertion--),storage.supportProducer().localId());
            assertEquals(-1,insertion);
            storage.selectOrderedSupports(candidate);long count=0;
            while(storage.advanceSupport()){String suffix=String.format("%04d",count);assertEquals("producer-"+suffix,storage.supportProducer().localId());assertEquals("origin-"+suffix,storage.supportOrigin().localId());count++;}
            assertEquals(1024,count);assertTrue(resources.heapPeak()<=65_536);assertTrue(pages.statistics().evictions()>1000);
            System.out.println("SNAPSHOT_DEPENDENCY_SUPPORT_CURSOR_METRICS heap="+resources.heapPeak()+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY)+" supports="+count+" evictions="+pages.statistics().evictions());
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,resources.heapUsed());
    }
}
