package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PagedCleanupStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(){
        return new AnalysisResources(new AnalysisResources.Limits(40000,0,0,64000000,1,2000000,0));
    }
    private static void spendWork(AnalysisResources resources){
        resources.work(resources.limits().workUnits()-resources.workUsed(),AnalysisResources.Phase.CONTROL);
    }
    @Test void spentWorkCannotPreventSpilledArrayFromReleasingBorrowedPages(){
        var resources=resources();
        try(var pages=new FilePageStore(directory,128,1,resources,AnalysisResources.Phase.CONTROL);
            var values=new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)){
            values.set(0,42);values.set(1L<<42,81);assertTrue(pages.statistics().livePages()>4);
            spendWork(resources);assertThrows(AnalysisResources.Exhausted.class,()->values.get(0));
            assertDoesNotThrow(values::close,"one-page eviction and retirement must finish after work exhaustion");
            assertEquals(0,pages.statistics().livePages());assertEquals(resources.limits().workUnits(),resources.workUsed());
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,resources.used(AnalysisResources.Pool.OPEN_FILES));
    }
    @Test void closingTemporaryBackendDoesNotRequireWritingDiscardedDirtyPayload(){
        var resources=resources();
        try(var pages=new FilePageStore(directory,128,1,resources,AnalysisResources.Phase.CONTROL)){
            long page=pages.allocate();pages.write(page,0,new byte[]{42},0,1);
            spendWork(resources);
            assertDoesNotThrow(pages::close,"the temporary spill file is discarded at close, not published");
            assertEquals(0,pages.statistics().livePages());
            assertEquals(resources.limits().workUnits(),resources.workUsed());
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,resources.used(AnalysisResources.Pool.OPEN_FILES));
    }
}
