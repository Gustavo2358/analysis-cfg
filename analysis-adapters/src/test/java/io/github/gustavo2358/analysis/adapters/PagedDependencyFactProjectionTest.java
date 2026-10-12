package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** A managed cursor must keep each full DTO funded until its scoped projection closes.
 * Detached get remains explicit compatibility; no fabricated expiry of copied Java records. */
final class PagedDependencyFactProjectionTest {
    @TempDir Path directory;
    private static DependencySiteFact fixture(int ordinal)throws Exception {
        var method=PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class);method.setAccessible(true);
        return (DependencySiteFact)method.invoke(null,ordinal);
    }
    private static AnalysisResources resources(long work){return new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,work,Long.MAX_VALUE));}
    private static Object call(Object target,String name,Class<?>[] types,Object... args)throws Exception {
        var method=target.getClass().getMethod(name,types);method.setAccessible(true);
        try{return method.invoke(target,args);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static Object fact(Object projection)throws Exception{return call(projection,"fact",new Class<?>[0]);}
    private static AutoCloseable project(PagedDependencyFactStorage storage,int ordinal)throws Exception {
        try{return (AutoCloseable)call(storage,"project",new Class<?>[]{int.class},ordinal);}
        catch(NoSuchMethodException absent){throw new AssertionError("FUNDED_SCOPED_COMPLETE_FACT_PROJECTION_PORT_ABSENT_RED",absent);}
    }
    private static void close(AutoCloseable projection)throws Exception{projection.close();}
    private static void released(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}

    @Test void completeFactsRemainFundedUntilEachLeaseClosesAndRootCloseRetiresLiveLeases()throws Exception {
        var resources=resources(Long.MAX_VALUE);
        try(var pages=new FilePageStore(directory,256,2,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                var expected=fixture(0);var other=fixture(1);storage.append(expected);storage.append(other);
                assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
                assertEquals(expected,storage.get(0));assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH),"detached compatibility releases its projection envelope");
                var first=project(storage,0);
                try(first) {
                    long one=resources.used(AnalysisResources.Pool.SCRATCH);assertTrue(one>1024);
                    assertEquals(expected,fact(first));assertSame(fact(first),fact(first));assertEquals(one,resources.used(AnalysisResources.Pool.SCRATCH));
                    var second=project(storage,1);
                    try(second) {
                        assertEquals(other,fact(second));long both=resources.used(AnalysisResources.Pool.SCRATCH);assertTrue(both>one);
                        close(first);assertThrows(IllegalStateException.class,()->fact(first));assertEquals(other,fact(second));
                        assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)>0);assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<both);
                        close(storage);assertThrows(IllegalStateException.class,()->fact(second));
                        assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(live,pages.statistics().livePages());
                    }
                }
            }
            byte[] data=new byte[8];pages.read(borrowed,0,data,0,8);assertArrayEquals(new byte[8],data);pages.release(borrowed);
        }
        released(resources);
    }
    @Test void deniedLaterProjectionAbortsOwnedStorageAndExistingLeasesWithoutClosingBorrowedPages()throws Exception {
        var resources=resources(Long.MAX_VALUE);
        try(var pages=new FilePageStore(directory,256,2,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),before=resources.heapUsed();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                var expected=fixture(2);storage.append(expected);
                try(var first=project(storage,0)) {
                    assertEquals(expected,fact(first));long remaining=resources.limits().heapBytes()-resources.heapUsed();
                    try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,remaining,AnalysisResources.Phase.CONTROL)) {
                        assertEquals(remaining,pressure.amount());
                        var failure=assertThrows(AnalysisResources.Exhausted.class,()->project(storage,0));
                        assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                        assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertThrows(IllegalStateException.class,()->fact(first));
                        assertThrows(IllegalStateException.class,storage::size);assertEquals(live,pages.statistics().livePages());
                    }
                }
            }
            assertEquals(before,resources.heapUsed());pages.release(borrowed);
        }
        released(resources);
    }
    @Test void workDenialOnScopedFactKeepsPrimaryAndImmediatelyRetiresEveryLiveProjection()throws Exception {
        var resources=resources(10_000_000);
        try(var pages=new FilePageStore(directory,256,2,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                var expected=fixture(3);storage.append(expected);
                try(var projection=project(storage,0)) {
                    assertEquals(expected,fact(projection));resources.work(resources.limits().workUnits()-resources.workUsed(),AnalysisResources.Phase.CONTROL);
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->fact(projection));assertEquals(AnalysisResources.Resource.WORK,failure.resource());
                    assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(live,pages.statistics().livePages());
                    assertThrows(IllegalStateException.class,()->fact(projection));assertThrows(IllegalStateException.class,storage::size);
                }
            }
            // WORK is deliberately exhausted; teardown must use the cleanup port.
            pages.releaseForCleanup(borrowed);
        }
        released(resources);
    }
}
