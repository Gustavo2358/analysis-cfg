package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** The admission owner uses the same typed content with resident and spilling page backends. */
final class PagedSnapshotValidationStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap){return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,256_000_000,4,1_000_000_000,1_000_000));}

    @Test void validTypedSnapshotHasIdenticalManagedAdmissionPrefixAcrossBackends() {
        var memoryLedger=resources(32_000_000);var fileLedger=resources(1_000_000);
        try(var memory=new MemoryPageStore(128,memoryLedger);var file=new FilePageStore(directory,128,1,fileLedger)) {
            var a=validate(memory,memoryLedger);var b=validate(file,fileLedger);
            assertEquals(a,b);assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION,a.status());
            assertTrue(a.issues().isEmpty());assertEquals(6,a.statistics().entities());
            assertTrue(fileLedger.heapPeak()<=1_000_000);
        }
        assertEquals(0,memoryLedger.heapUsed());assertEquals(0,fileLedger.heapUsed());assertEquals(0,fileLedger.used(AnalysisResources.Pool.TEMPORARY));
    }
    private static ValidationResult validate(PageStore pages,AnalysisResources ledger) {
        try(var original=AirSnapshot.fromPublication(PagedAirStorageTest.publication("managed admission"));
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
            var snapshot=builder.finish(root);
            try(var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertSame(snapshot,checked.snapshot());return checked.result();
            }
        }
    }
}
