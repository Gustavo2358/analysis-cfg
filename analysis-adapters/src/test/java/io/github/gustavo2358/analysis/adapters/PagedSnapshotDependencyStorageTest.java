package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.DependencyProgramStore;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import io.github.gustavo2358.analysis.solver.ResidentPageStore;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PagedSnapshotDependencyStorageTest {
    @Test void oneCandidatePayloadCanExceedManagedHeapWithoutMaterializingAString() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(65_536,65_536,0,64_000_000,4,1_000_000_000,1_000_000));
        var directory=Files.createTempDirectory("snapshot-dependency-large-candidate-");
        final int characters=262_144;final int[] emitted={0};
        try(var pages=new FilePageStore(directory,128,1,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
            long candidate=storage.add(7,new DependencyProgramStore.TextCursor(){public int read(char[] target,int start,int length){if(emitted[0]==characters)return -1;int count=Math.min(length,characters-emitted[0]);java.util.Arrays.fill(target,start,start+count,'x');emitted[0]+=count;return count;}});
            assertTrue(candidate>0);assertEquals(characters,emitted[0]);assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY)>resources.heapPeak());assertTrue(resources.heapPeak()<=65_536);
            System.out.println("SNAPSHOT_DEPENDENCY_LARGE_CANDIDATE heap="+resources.heapPeak()+" characters="+characters+" temporary="+resources.used(AnalysisResources.Pool.TEMPORARY));
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,resources.heapUsed());
    }

    @Test void nestedMetadataIoGrowsBelowWholeBaseRewriteAcrossGeometricInputs() throws Exception {
        long previousWork=0,previousIo=0,peak=-1;
        for(int count:new int[]{32,64,128,256,512}) {
            var resources=new AnalysisResources(new AnalysisResources.Limits(131_072,131_072,0,64_000_000,4,1_000_000_000,1_000_000));
            var directory=Files.createTempDirectory("snapshot-dependency-amplification-");
            try(var pages=new FilePageStore(directory,128,4,resources);var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
                storage.addOrigin(1,"owner");
                for(int i=count-1;i>=0;i--)storage.addOriginInput(1,i+2,"input-"+String.format("%04d",i));
                storage.selectOriginInputs(1);long seen=0;while(storage.advanceOriginInput())assertEquals(++seen+1,storage.originInputHandle());
                assertEquals(count,seen);
                long work=resources.workUsed(),io=Math.addExact(pages.statistics().bytesRead(),pages.statistics().bytesWritten());
                if(previousWork!=0) {
                    assertTrue(work<previousWork*3,"doubling nested metadata must stay below quadratic WORK: "+previousWork+" -> "+work);
                    assertTrue(io<previousIo*3,"doubling nested metadata must stay below whole-base I/O rewrite: "+previousIo+" -> "+io);
                }
                if(peak<0)peak=resources.heapPeak();else assertEquals(peak,resources.heapPeak());
                previousWork=work;previousIo=io;
                System.out.println("SNAPSHOT_DEPENDENCY_METADATA_AMPLIFICATION records="+count+" work="+work+" bytes="+io+" evictions="+pages.statistics().evictions());
            } finally {Files.deleteIfExists(directory);}
            assertEquals(0,resources.heapUsed());
        }
    }

    @Test void interruptedNestedMetadataMutationAbortsAndReleasesBorrowedPages() {
        int denied=0;
        for(int boundary=1;boundary<=64;boundary++) {
            var resources=new AnalysisResources(new AnalysisResources.Limits(1_000_000,100_000,0,0,0,10_000_000,0));
            try(var backend=new ResidentPageStore(128,resources,AnalysisResources.Phase.DOMAIN)) {
                var pages=new InterruptedPages(backend);
                try(var storage=new PagedSnapshotDependencyStorage(pages,resources)) {
                    storage.addOrigin(1,"owner");pages.remaining=boundary;
                    try{storage.addOriginInput(1,2,"input-with-enough-payload-to-cross-pages");}
                    catch(PageStore.Failure expected){denied++;assertEquals(PageStore.Reason.IO,expected.reason());assertThrows(IllegalStateException.class,storage::selectOrigins);}
                    pages.remaining=-1;
                }
                assertEquals(0,backend.statistics().livePages());long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,resources.heapUsed());
        }
        assertEquals(64,denied,"every injected metadata I/O boundary must execute");
    }

    private static final class InterruptedPages implements PageStore {
        private final PageStore delegate;private int remaining=-1;
        private InterruptedPages(PageStore delegate){this.delegate=delegate;}
        private void interrupt(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic dependency metadata interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){interrupt();return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){interrupt();delegate.read(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){interrupt();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void readForCleanup(long page,int offset,byte[] target,int start,int length){delegate.readForCleanup(page,offset,target,start,length);}
        public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }

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
