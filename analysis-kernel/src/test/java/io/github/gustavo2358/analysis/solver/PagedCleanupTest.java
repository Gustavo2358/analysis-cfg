package io.github.gustavo2358.analysis.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagedCleanupTest {
    private static AnalysisResources resources(){
        return new AnalysisResources(new AnalysisResources.Limits(4000000,4000000,0,0,0,2000000,0));
    }
    private static void spendWork(AnalysisResources resources){
        resources.work(resources.limits().workUnits()-resources.workUsed(),AnalysisResources.Phase.CONTROL);
    }
    @Test void spentWorkCannotPreventArrayFromReleasingBorrowedResidentPages(){
        var resources=resources();
        try(var pages=new ResidentPageStore(128,resources,AnalysisResources.Phase.CONTROL);
            var values=new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)){
            values.set(0,42);values.set(1L<<42,81);assertTrue(pages.statistics().livePages()>4);
            spendWork(resources);
            assertEquals(AnalysisResources.Resource.WORK,assertThrows(AnalysisResources.Exhausted.class,()->values.get(0)).resource());
            assertDoesNotThrow(values::close,"teardown must release owned pages after the analysis budget is spent");
            assertEquals(0,pages.statistics().livePages());assertEquals(resources.limits().workUnits(),resources.workUsed());
            assertTrue(resources.cleanupWorkUsed()>0);
            assertDoesNotThrow(values::close);
        }
        assertEquals(0,resources.heapUsed());
    }
    @Test void spentWorkCannotPreventIndexFromReleasingPayloadAndOwnershipPages(){
        var resources=resources();
        try(var pages=new ResidentPageStore(128,resources,AnalysisResources.Phase.CONTROL);
            var index=new PagedLongIndex(pages,resources,AnalysisResources.Phase.CONTROL,Long::compare)){
            for(int key=0;key<128;key++)index.intern(key,key+1);
            for(int key=0;key<64;key++)assertTrue(index.remove(key));
            spendWork(resources);assertThrows(AnalysisResources.Exhausted.class,()->index.find(100));
            assertDoesNotThrow(index::close,"payload directory teardown cannot depend on remaining analysis work");
            assertEquals(0,pages.statistics().livePages());assertEquals(resources.limits().workUnits(),resources.workUsed());
        }
        assertEquals(0,resources.heapUsed());
    }
}
