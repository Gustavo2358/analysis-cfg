package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Typed leaf projection must preserve complete identity/premise ordinals without decoding a whole site. */
final class PagedDependencySupportProjectionTest {
    @TempDir Path directory;
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception {
        try{return target.getClass().getMethod(name,types).invoke(target,args);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static DependencySiteFact fixture()throws Exception {
        var method=PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class);method.setAccessible(true);return (DependencySiteFact)method.invoke(null,0);
    }
    private static AutoCloseable project(PagedDependencyFactStorage storage,int candidate,int support)throws Exception {
        return (AutoCloseable)invoke(storage,"projectSupport",new Class<?>[]{int.class,int.class,int.class},0,candidate,support);
    }
    private static Object support(Object scope)throws Exception{return invoke(scope,"support",new Class<?>[0]);}
    private static void expire(AutoCloseable owner)throws Exception{owner.close();}
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));}
    @Test void everyTypedProducerAndRepeatedPremiseStaysFundedUntilLeafClosesAndRootExpiresAllLeaves()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages();var fact=fixture();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fact);long scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                for(int i=0;i<fact.candidates().getFirst().supports().size();i++)try(var leaf=project(storage,0,i)) {
                    assertEquals(fact.candidates().getFirst().supports().get(i),support(leaf));assertSame(support(leaf),support(leaf));
                    assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)>scratch);
                    assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<30_000,"leaf envelope must not fund/decode unrelated site candidates and proofs");
                }
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
                assertThrows(IndexOutOfBoundsException.class,()->project(storage,1,0));assertEquals(1,storage.size());
                try(var leaf=project(storage,0,0)) {
                    expire(storage);assertThrows(IllegalStateException.class,()->support(leaf));assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(live,pages.statistics().livePages());
                }
            }pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
    }
    @Test void deniedLeafProjectionKeepsPrimaryExpiresEarlierLeavesAndRetiresOnlyOwnedTapes()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fixture());try(var leaf=project(storage,0,0)) {
                    long remaining=resources.limits().heapBytes()-resources.heapUsed();
                    try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,remaining,AnalysisResources.Phase.CONTROL)) {
                        assertEquals(remaining,pressure.amount());var failure=assertThrows(AnalysisResources.Exhausted.class,()->project(storage,0,1));
                        assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                        assertThrows(IllegalStateException.class,()->support(leaf));assertThrows(IllegalStateException.class,storage::size);
                        assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(live,pages.statistics().livePages());
                    }
                }
            }pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
    }
    @Test void coldDirectoryReadFailurePreservesPrimaryAndExpiresEveryLiveTypedLeafWithoutClosingBackend()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),before=resources.heapUsed();
            var fault=new SourceCorrelationSnapshotTest.FaultPages(pages);
            try(var storage=new PagedDependencyFactStorage(fault,resources)) {
                storage.append(fixture());try(var leaf=project(storage,0,0)) {
                    assertNotNull(support(leaf));fault.arm(1);
                    assertSame(fault.primary,assertThrows(PageStore.Failure.class,()->project(storage,0,1)));
                    assertThrows(IllegalStateException.class,()->support(leaf));assertThrows(IllegalStateException.class,storage::size);
                    assertFalse(fault.closed);assertEquals(live,pages.statistics().livePages());assertEquals(before,resources.heapUsed());
                }
            }byte[] bytes=new byte[8];pages.read(borrowed,0,bytes,0,8);assertArrayEquals(new byte[8],bytes);pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
    }
}
