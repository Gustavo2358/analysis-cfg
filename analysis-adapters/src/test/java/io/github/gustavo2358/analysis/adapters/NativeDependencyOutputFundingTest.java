package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshot;
import io.github.gustavo2358.air.validation.SnapshotValidator;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class NativeDependencyOutputFundingTest {
    @TempDir Path directory;

    @Test void failedPlainNativeOutputKeepsPrimaryAndReleasesTheObservedRootLease() throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);
        var operation=publication.units().getFirst().sequences().getFirst().terminator().header().id();
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,0));
        var primary=new IOException("synthetic output write failure");boolean[] observed={false};
        try(var pages=new FilePageStore(directory,128,1,resources);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
            assertTrue(checked.result().isStructurallyValid(),checked.result().toString());program.invokeCoverage(operation).orElseThrow();
            var result=SourceTargetPlanTest.result(publication,List.of(SourceTargetPlanTest.site(publication,operation,false,true,false)));
            long baseline=resources.used(AnalysisResources.Pool.RESIDENT),pagesBefore=pages.statistics().livePages();
            var stream=new OutputStream(){
                @Override public void write(int value)throws IOException{throw primary;}
                @Override public void write(byte[] values,int offset,int length)throws IOException {
                    assertTrue(length>0);assertEquals(baseline+65536+4096,resources.used(AnalysisResources.Pool.RESIDENT));observed[0]=true;throw primary;
                }
            };
            assertSame(primary,assertThrows(IOException.class,()->new DependencyJson().writeSnapshot(result,program,stream)));
            assertTrue(observed[0]);assertEquals(baseline,resources.used(AnalysisResources.Pool.RESIDENT));
            assertEquals(pagesBefore,pages.statistics().livePages());assertEquals(publication.id(),program.publicationId());
            assertTrue(program.invokeCoverage(operation).isPresent());
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }

    @Test void plainNativeRootBufferDenialOccursBeforeOutputAndDoesNotExpireBorrowedInput() throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);
        var operation=publication.units().getFirst().sequences().getFirst().terminator().header().id();
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,0));
        try(var pages=new FilePageStore(directory,128,1,resources);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
            assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
            program.operationDirectory().orElseThrow();program.invokeCoverage(operation).orElseThrow();
            var result=SourceTargetPlanTest.result(publication,List.of(SourceTargetPlanTest.site(publication,operation,false,true,false)));
            long before=resources.used(AnalysisResources.Pool.RESIDENT),pagesBefore=pages.statistics().livePages();
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-before-(65536+4096-1),AnalysisResources.Phase.ENCODE)) {
                assertTrue(pressure.amount()>0);long pressured=resources.used(AnalysisResources.Pool.RESIDENT);
                var bytes=new ByteArrayOutputStream();
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->new DependencyJson().writeSnapshot(result,program,bytes),"PLAIN_NATIVE_ROOT_BUFFER_UNFUNDED_RED");
                assertEquals(AnalysisResources.Phase.ENCODE,failure.phase());assertEquals(65536+4096,failure.requested());
                assertEquals(0,bytes.size());assertEquals(pressured,resources.used(AnalysisResources.Pool.RESIDENT));
                assertEquals(pagesBefore,pages.statistics().livePages());assertEquals(publication.id(),program.publicationId());
                assertTrue(program.invokeCoverage(operation).isPresent());
            }
            assertEquals(before,resources.used(AnalysisResources.Pool.RESIDENT));
            var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,program,bytes);assertTrue(bytes.size()>0);
            assertEquals(before,resources.used(AnalysisResources.Pool.RESIDENT));
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
}
