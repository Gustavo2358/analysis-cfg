package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.nio.file.*;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SnapshotDependencyOutputScaleTest {
    @TempDir Path directory;

    @Test void cursorPublishesOutputLargerThanManagedHeapAndFailureIsAtomic() throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(131_072,131_072,0,64_000_000,4,1_000_000_000,64_000_000));
        Path pagesDirectory=directory.resolve("pages");Files.createDirectories(pagesDirectory);
        try(var pages=new FilePageStore(pagesDirectory,128,1,resources);
            var result=new SnapshotDependencyAnalysis().open(new ScaleProgram(),new PagedSnapshotDependencyStorage(pages,resources))) {
            Path output=directory.resolve("dependencies.json");new SnapshotDependencyFileWriter().write(result,output,resources);
            long bytes=Files.size(output);assertEquals(bytes,resources.outputUsed());assertTrue(bytes>resources.heapPeak(),"output must exceed managed heap peak");
            assertEquals(new SnapshotDependencyCursorResult.Metrics(1024,1024),result.metrics());

            Path failed=directory.resolve("failed.json");Files.writeString(failed,"sentinel");
            var limited=new AnalysisResources(new AnalysisResources.Limits(1,1,0,1,1,1_000_000,1024));
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->new SnapshotDependencyFileWriter().write(result,failed,limited));
            assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals("sentinel",Files.readString(failed));
            try(var files=Files.list(directory)){assertTrue(files.noneMatch(path->path.getFileName().toString().startsWith(".dependencies-")));}
            System.out.println("SNAPSHOT_DEPENDENCY_OUTPUT_METRICS heap="+resources.heapPeak()+" output="+bytes+" sites=1024 origins=1024 artifacts=1024 evictions="+pages.statistics().evictions());
        }
        assertEquals(0,resources.heapUsed());
    }

    private static final class ScaleProgram implements DependencyProgramStore {
        private static final PublicationId PUBLICATION=new PublicationId("output-scale");
        private static final UnitId UNIT=new UnitId(PUBLICATION,"caller");
        @Override public PublicationId publicationId(){return PUBLICATION;}
        @Override public Evidence.InventoryStatus inventory(){return Evidence.InventoryStatus.COMPLETE;}
        @Override public List<Origins.Artifact> artifacts(){throw new AssertionError("cursor path must not materialize artifact inventory");}
        @Override public List<Origins.Origin> origins(){throw new AssertionError("cursor path must not materialize origin inventory");}
        @Override public void definitions(Consumer<Definition> consumer){consumer.accept(new Definition(7,1,0,0,"",List.of(new Producer(1,1))));}
        @Override public void computedCalls(Consumer<ComputedCall> consumer){for(int i=1023;i>=0;i--){String suffix=String.format("%04d",i);consumer.accept(new ComputedCall(7,UNIT,new EntryId(UNIT,"entry-"+suffix),new LabelId(UNIT,"sequence-"+suffix),new OperationId(UNIT,"operation-"+suffix),new OriginId(PUBLICATION,"site-"+suffix),new OriginId(PUBLICATION,"target-"+suffix),Evidence.CoverageStatus.MODELED,"cobol.program",new ObjectId(UNIT,"subject-"+suffix)));}}
        @Override public void artifactHandles(MetadataHandleConsumer consumer){for(int i=1023;i>=0;i--)consumer.accept(i+1,"artifact-"+String.format("%04d",i));}
        @Override public void originHandles(MetadataHandleConsumer consumer){for(int i=1023;i>=0;i--)consumer.accept(i+1,"origin-"+String.format("%04d",i));}
        @Override public void originInputHandles(long originHandle,MetadataHandleConsumer consumer){if(originHandle==1)for(int i=4095;i>=0;i--)consumer.accept(i+5000,"input-"+String.format("%04d",i));}
        @Override public Origins.Artifact materializeArtifact(long handle){String suffix=String.format("%04d",handle-1);return new Origins.Artifact(new ArtifactId(PUBLICATION,"artifact-"+suffix),"source-"+suffix+".cbl",java.util.Optional.empty());}
        @Override public Origins.Origin materializeOrigin(long handle){String suffix=String.format("%04d",handle-1);return new Origins.Unavailable(new OriginId(PUBLICATION,"origin-"+suffix),"scale evidence "+suffix);}
        @Override public OriginView originView(long handle){String suffix=String.format("%04d",handle-1);return handle==1?new OriginView.Derived(new OriginId(PUBLICATION,"origin-"+suffix),"scale-derived"):new OriginView.Unavailable(new OriginId(PUBLICATION,"origin-"+suffix),"scale evidence "+suffix);}
        @Override public OriginId materializeOriginInput(long handle){return new OriginId(PUBLICATION,"input-"+String.format("%04d",handle-5000));}
        @Override public Iterable<Origins.IncludeFrame> cursorOriginIncludes(long originHandle){return List.of();}
        @Override public long materializationBytes(Definition definition){return 16;}
        @Override public String materialize(Definition definition){return "TARGET  ";}
        @Override public OperationId operationId(long handle){return new OperationId(UNIT,"producer");}
        @Override public OriginId originId(long handle){return new OriginId(PUBLICATION,"producer-origin");}
        @Override public void close(){ }
    }
}
