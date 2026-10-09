package io.github.gustavo2358.analysis.solver;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class AnalysisResourcesTest {
    @Test void oneDeadlineCoversEveryProductivePhaseButNeverBlocksCleanup(){
        long[] now={100};var resources=new AnalysisResources(new AnalysisResources.Limits(10,10,0,0,0,10,10),5,()->now[0]);
        try(var reservation=resources.reserve(AnalysisResources.Pool.RESIDENT,1,AnalysisResources.Phase.DECODE)){
            assertEquals(1,reservation.amount());
            now[0]=104;resources.work(1,AnalysisResources.Phase.VALIDATION);resources.output(1,AnalysisResources.Phase.ENCODE);
            now[0]=105;var failure=assertThrows(AnalysisResources.Exhausted.class,()->resources.work(1,AnalysisResources.Phase.DOMAIN));
            assertEquals(AnalysisResources.Resource.TIME,failure.resource());assertEquals(AnalysisResources.Phase.DOMAIN,failure.phase());assertEquals(5,failure.limit());assertEquals(5,failure.used());
            resources.cleanupWork(1,AnalysisResources.Phase.DOMAIN);assertEquals(1,resources.cleanupWorkUsed());
        }
    }
    @Test void cleanupAccountingCannotResetAnalysisQuotaAndReportsCounterSaturation(){
        var resources=new AnalysisResources(new AnalysisResources.Limits(0,0,0,0,0,1,0));
        resources.work(1,AnalysisResources.Phase.CONTROL);resources.cleanupWork(7,AnalysisResources.Phase.CONTROL);
        assertEquals(1,resources.workUsed());assertEquals(7,resources.cleanupWorkUsed());
        assertThrows(AnalysisResources.Exhausted.class,()->resources.work(1,AnalysisResources.Phase.CONTROL));
        resources.cleanupWork(Long.MAX_VALUE-7,AnalysisResources.Phase.CONTROL);
        assertEquals(Long.MAX_VALUE,resources.cleanupWorkUsed());assertFalse(resources.cleanupWorkSaturated());
        resources.cleanupWork(1,AnalysisResources.Phase.CONTROL);assertTrue(resources.cleanupWorkSaturated());
        assertEquals(Long.MAX_VALUE,resources.cleanupWorkUsed());assertEquals(1,resources.workUsed());
        assertThrows(IllegalArgumentException.class,()->resources.cleanupWork(-1,AnalysisResources.Phase.CONTROL));
    }
    private static AnalysisResources resources(long heap, long scratch) {
        return new AnalysisResources(new AnalysisResources.Limits(heap, scratch, 64, 128, 2, 10, 100));
    }

    @Test void scratchAndResidentShareHeapAndFailedReservationDoesNotChangeAccounting() {
        var resources = resources(100, 50);
        try (var resident = resources.reserve(AnalysisResources.Pool.RESIDENT, 70, AnalysisResources.Phase.CONTROL)) {
            assertEquals(70, resident.amount());
            var failure = assertThrows(AnalysisResources.Exhausted.class, () ->
                    resources.reserve(AnalysisResources.Pool.SCRATCH, 40, AnalysisResources.Phase.DOMAIN));
            assertEquals(AnalysisResources.Resource.HEAP, failure.resource());
            assertEquals(AnalysisResources.Phase.DOMAIN, failure.phase());
            assertEquals(100, failure.limit());
            assertEquals(70, resources.heapUsed());
            assertEquals(0, resources.used(AnalysisResources.Pool.SCRATCH));
            try (var scratch = resources.reserve(AnalysisResources.Pool.SCRATCH, 30, AnalysisResources.Phase.DOMAIN)) {
                assertEquals(30, scratch.amount());
                assertEquals(100, resources.heapUsed());
                assertEquals(100, resources.heapPeak());
            }
            assertEquals(70, resources.heapUsed());
        }
        assertEquals(0, resources.heapUsed());
        assertEquals(100, resources.heapPeak());
    }

    @Test void reservationGrowthIsAtomicAndDoubleCloseDoesNotFreeAnotherOwnersBytes() {
        var resources = resources(100, 100);
        var first = resources.reserve(AnalysisResources.Pool.RESIDENT, 60, AnalysisResources.Phase.INDEX);
        var second = resources.reserve(AnalysisResources.Pool.RESIDENT, 20, AnalysisResources.Phase.INDEX);
        assertThrows(AnalysisResources.Exhausted.class, () -> first.grow(30, AnalysisResources.Phase.INDEX));
        assertEquals(60, first.amount());
        assertEquals(80, resources.heapUsed());
        first.grow(20, AnalysisResources.Phase.INDEX);
        assertEquals(100, resources.heapUsed());
        first.close(); first.close();
        assertEquals(20, resources.heapUsed());
        assertThrows(IllegalStateException.class, () -> first.grow(1, AnalysisResources.Phase.INDEX));
        second.close();
        assertEquals(0, resources.heapUsed());
    }

    @Test void directDiskAndDescriptorsHaveIndependentQuotas() {
        var resources = resources(1, 1);
        try (var direct = resources.reserve(AnalysisResources.Pool.DIRECT, 64, AnalysisResources.Phase.DECODE);
             var disk = resources.reserve(AnalysisResources.Pool.TEMPORARY, 128, AnalysisResources.Phase.SORT);
             var files = resources.reserve(AnalysisResources.Pool.OPEN_FILES, 2, AnalysisResources.Phase.ENCODE)) {
            assertEquals(64, direct.amount());
            assertEquals(128, disk.amount());
            assertEquals(2, files.amount());
            assertEquals(0, resources.heapUsed());
            assertEquals(AnalysisResources.Resource.DIRECT, assertThrows(AnalysisResources.Exhausted.class,
                    () -> resources.reserve(AnalysisResources.Pool.DIRECT, 1, AnalysisResources.Phase.DECODE)).resource());
            assertEquals(AnalysisResources.Resource.TEMPORARY, assertThrows(AnalysisResources.Exhausted.class,
                    () -> resources.reserve(AnalysisResources.Pool.TEMPORARY, 1, AnalysisResources.Phase.SORT)).resource());
            assertEquals(AnalysisResources.Resource.OPEN_FILES, assertThrows(AnalysisResources.Exhausted.class,
                    () -> resources.reserve(AnalysisResources.Pool.OPEN_FILES, 1, AnalysisResources.Phase.ENCODE)).resource());
        }
        for (var pool : AnalysisResources.Pool.values()) assertEquals(0, resources.used(pool));
    }

    @Test void workAndOutputAreCumulativeRatherThanEvictableAllocations() {
        var resources = resources(1, 1);
        resources.work(7, AnalysisResources.Phase.DEMAND);
        resources.work(3, AnalysisResources.Phase.CONTROL);
        resources.output(100, AnalysisResources.Phase.ENCODE);
        assertEquals(10, resources.workUsed());
        assertEquals(100, resources.outputUsed());
        assertEquals(AnalysisResources.Resource.WORK, assertThrows(AnalysisResources.Exhausted.class,
                () -> resources.work(1, AnalysisResources.Phase.REPLAY)).resource());
        assertEquals(AnalysisResources.Resource.OUTPUT, assertThrows(AnalysisResources.Exhausted.class,
                () -> resources.output(1, AnalysisResources.Phase.ENCODE)).resource());
        assertEquals(10, resources.workUsed());
        assertEquals(100, resources.outputUsed());
    }

    @Test void hugeAmountsCannotWrapAroundAndNegativeChargesAreInvalid() {
        var max = Long.MAX_VALUE;
        var resources = new AnalysisResources(new AnalysisResources.Limits(max, max, max, max, max, max, max));
        try (var reservation = resources.reserve(AnalysisResources.Pool.RESIDENT, max - 1, AnalysisResources.Phase.INDEX)) {
            assertThrows(AnalysisResources.Exhausted.class,
                    () -> resources.reserve(AnalysisResources.Pool.RESIDENT, 2, AnalysisResources.Phase.INDEX));
            assertEquals(max - 1, resources.heapUsed());
            assertThrows(IllegalArgumentException.class, () -> reservation.grow(-1, AnalysisResources.Phase.INDEX));
        }
        resources.work(max, AnalysisResources.Phase.VALIDATION);
        assertThrows(AnalysisResources.Exhausted.class, () -> resources.work(1, AnalysisResources.Phase.VALIDATION));
        assertThrows(IllegalArgumentException.class, () -> resources.output(-1, AnalysisResources.Phase.ENCODE));
    }

    @Test void zeroScratchQuotaIsIndependentFromHeapQuota() {
        var resources = resources(100, 0);
        var exhausted = assertThrows(AnalysisResources.Exhausted.class,
                () -> resources.reserve(AnalysisResources.Pool.SCRATCH, 1, AnalysisResources.Phase.CONTROL));
        assertEquals(AnalysisResources.Resource.SCRATCH, exhausted.resource());
        assertEquals(0, resources.heapUsed());
    }

    @Test void stagedCapacityTransfersWithoutRechargingOrPrematurelyReleasingBytes() {
        var resources = resources(100, 100);
        var staged = resources.reserve(AnalysisResources.Pool.RESIDENT, 30, AnalysisResources.Phase.INDEX);
        try (var owner = resources.reserve(AnalysisResources.Pool.RESIDENT, 70, AnalysisResources.Phase.INDEX)) {
            owner.absorb(staged);
            assertEquals(100, owner.amount()); assertEquals(0, staged.amount());
            assertEquals(100, resources.heapUsed()); assertEquals(100, resources.heapPeak());
            staged.close(); assertEquals(100, resources.heapUsed());
            assertThrows(IllegalStateException.class, () -> staged.grow(1, AnalysisResources.Phase.INDEX));
        } finally { staged.close(); }
        assertEquals(0, resources.heapUsed());
    }

    @Test void capacityTransferRejectsForeignPoolOwnerSelfAndClosedReservationsAtomically() {
        var resources = resources(100, 100); var foreign = resources(100, 100);
        try (var owner = resources.reserve(AnalysisResources.Pool.RESIDENT, 10, AnalysisResources.Phase.DOMAIN);
             var scratch = resources.reserve(AnalysisResources.Pool.SCRATCH, 20, AnalysisResources.Phase.DOMAIN);
             var other = foreign.reserve(AnalysisResources.Pool.RESIDENT, 30, AnalysisResources.Phase.DOMAIN)) {
            assertThrows(IllegalArgumentException.class, () -> owner.absorb(owner));
            assertThrows(IllegalArgumentException.class, () -> owner.absorb(scratch));
            assertThrows(IllegalArgumentException.class, () -> owner.absorb(other));
            var released = resources.reserve(AnalysisResources.Pool.RESIDENT, 1, AnalysisResources.Phase.DOMAIN);
            released.close(); assertThrows(IllegalStateException.class, () -> owner.absorb(released));
            assertEquals(10, owner.amount()); assertEquals(30, resources.heapUsed()); assertEquals(30, foreign.heapUsed());
        }
        assertEquals(0, resources.heapUsed()); assertEquals(0, foreign.heapUsed());
    }
}
