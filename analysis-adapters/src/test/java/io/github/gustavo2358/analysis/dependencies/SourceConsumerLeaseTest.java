package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceConsumerLeaseTest {
    @TempDir Path directory;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));}
    private static SourceAdmissionSnapshot admit(PageStore pages,AnalysisResources resources)throws Exception {
        byte[] wire=Files.readAllBytes(Path.of("src/test/resources/qualified-source-r9/ordinary.source.json"));
        return SourceAdmissionSnapshot.admit(new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);
    }
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    @Test void fundedOpaqueLeaseCannotBeForgedAndClosingItBorrowsAdmission()throws Exception {
        for(var constructor:SourceAdmissionSnapshot.ConsumerLease.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        var resources=resources();int[] calls={0};
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admitted=admit(pages,resources)) {
            long before=resources.heapUsed();var lease=admitted.attachResource(()->calls[0]++,pages,resources);
            assertEquals(before+512,resources.heapUsed());lease.close();lease.close();assertEquals(1,calls[0]);assertEquals(before,resources.heapUsed());assertTrue(admitted.unitCount()>0);
            admitted.attachResource(()->calls[0]++,pages,resources);admitted.attachResource(()->calls[0]++,pages,resources);
            admitted.snapshot().close();assertEquals(3,calls[0]);assertEquals(0,pages.statistics().livePages());
        }zero(resources);
    }
    @Test void contextAndControlDenialsNeverClaimAnUnrelatedDependent()throws Exception {
        var resources=resources();var foreign=resources();int[] calls={0};
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admitted=admit(pages,resources)) {
            assertThrows(IllegalArgumentException.class,()->admitted.attachResource(()->calls[0]++,other,resources));
            assertThrows(IllegalArgumentException.class,()->admitted.attachResource(()->calls[0]++,pages,foreign));
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-511,AnalysisResources.Phase.INDEX)) {
                assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->admitted.attachResource(()->calls[0]++,pages,resources));
            }
            assertEquals(0,calls[0]);assertTrue(admitted.unitCount()>0);
        }zero(resources);zero(foreign);
    }
    @Test void failingAndReentrantCleanupCannotPreventSiblingOrStorageRetirement()throws Exception {
        for(boolean reentrant:new boolean[]{false,true}) {
            var resources=resources();var primary=new PageStore.Failure(PageStore.Reason.IO,"independent source dependent teardown failure");int[] calls={0};
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                var admitted=admit(pages,resources);admitted.attachResource(()->calls[0]++,pages,resources);
                admitted.attachResource(()->{calls[0]++;if(reentrant)admitted.close();else throw primary;},pages,resources);
                if(reentrant)admitted.close();else assertSame(primary,assertThrows(PageStore.Failure.class,admitted::close));
                assertEquals(2,calls[0]);assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,admitted::snapshot);admitted.close();
                long borrowed=pages.allocate();pages.release(borrowed);
            }zero(resources);
        }
    }
}
