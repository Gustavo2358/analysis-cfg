package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.OperationId;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Full general result ownership/cursor laws; absent productive API is a runtime RED.
 * No limited direct-profile engine, no root heap claim from only a List class name. */
final class ManagedExecutableDependencyResultTest {
    @TempDir Path directory;
    @FunctionalInterface private interface Work {
        void run(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources resources,AnalysisResources root)throws Exception;
    }
    private void nativeInput(int count,Work work)throws Exception {
        var fixture=SnapshotDependencyAnalysisTest.class.getDeclaredMethod("directCalls",int.class);fixture.setAccessible(true);
        var publication=(Publication)fixture.invoke(null,count);
        var resources=new AnalysisResources(new AnalysisResources.Limits(64_000_000,16_000_000,0,256_000_000,8,Long.MAX_VALUE,Long.MAX_VALUE));
        try(var pages=new FilePageStore(directory,512,1,resources);var original=AirSnapshot.fromPublication(publication)) {
            var copy=PagedAirStorageTest.class.getDeclaredMethod("copy",AirSnapshot.class,long.class,AirShape.class,AirSnapshotBuilder.class);copy.setAccessible(true);
            try(var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
                long root=(long)copy.invoke(null,original,original.root(),null,builder);
                try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
                    var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
                    assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                    var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                    assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());work.run(program,cfg,pages,AnalysisResources.executionFor(program),resources);
                }
            }
        }finally{for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    }
    private static Object call(Object target,String name,Class<?>[] types,Object... arguments)throws Exception {
        var method=target.getClass().getMethod(name,types);method.setAccessible(true);
        try{return method.invoke(target,arguments);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static Object call(Object target,String name)throws Exception{return call(target,name,new Class<?>[0]);}
    private static AutoCloseable managed(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources resources)throws Exception {
        try{return (AutoCloseable)call(new DependencyAnalysis(),"prepareManaged",new Class<?>[]{SnapshotProgram.class,CfgBuildResult.class,PageStore.class,AnalysisResources.class},program,cfg,pages,resources);}
        catch(NoSuchMethodException absent){throw new AssertionError("GENERAL_MANAGED_EXECUTABLE_RESULT_OWNER_PORT_ABSENT_RED",absent);}
    }
    private static long projections(Object owner)throws Exception{return ((Number)call(owner,"fullFactProjections")).longValue();}
    private static DependencyResult view(Object owner)throws Exception{return (DependencyResult)call(owner,"result");}
    private static void expire(AutoCloseable owner)throws Exception{owner.close();}
    private static void manual(DependencySiteFact fact) {
        assertEquals(DependencySiteFact.Reachability.REACHABLE,fact.reachability());
        assertEquals(List.of("PROGA"),fact.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertEquals("PROGA   ",fact.candidates().getFirst().rawValue());
        assertEquals(new OperationId(fact.caller(),"seed"),fact.candidates().getFirst().supports().getFirst().producer());
        assertEquals(fact.entry().unit(),fact.caller());assertEquals(fact.operation().unit(),fact.caller());
    }
    @Test void completeGeneralFactsHaveCanonicalPagedResultAndFundedScopedCursorUntilOwnerClose()throws Exception {
        nativeInput(16,(program,cfg,pages,resources,root)-> {
            var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);
            assertEquals(16,expected.sites().size());assertEquals(16,expected.edges().size());expected.sites().forEach(ManagedExecutableDependencyResultTest::manual);
            try(var owner=managed(program,cfg,pages,resources)) {
                var result=view(owner);assertEquals(expected.metrics(),result.metrics());
                assertEquals(16,result.sites().size());assertEquals(16,result.edges().size());
                assertFalse(result.sites().getClass().getName().startsWith("java.util.ImmutableCollections$"));
                assertFalse(result.edges().getClass().getName().startsWith("java.util.ImmutableCollections$"));
                assertEquals(0,projections(owner),"lookup,inventory completion and canonical sorting cannot decode complete cold facts");
                assertThrows(UnsupportedOperationException.class,result.sites()::clear);
                long before=resources.used(AnalysisResources.Pool.SCRATCH);int count=0;
                var cursor=(AutoCloseable)call(owner,"cursor");
                try(cursor) {
                    long control=resources.used(AnalysisResources.Pool.SCRATCH);
                    while((Boolean)call(cursor,"advance")) {
                        var fact=(DependencySiteFact)call(cursor,"fact");manual(fact);
                        assertSame(fact,call(cursor,"fact"),"one full projection per cursor position");
                        assertEquals(expected.sites().get(count++),fact,"all29fields,full ownership,proofs and remainder preserved");
                        assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)>control,"complete fact projection must remain funded while consumed");
                    }
                    assertEquals(control,resources.used(AnalysisResources.Pool.SCRATCH));
                    assertThrows(IllegalStateException.class,()->call(cursor,"fact"));
                }
                assertEquals(16,count);assertEquals(16,projections(owner));assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));
                assertThrows(IllegalStateException.class,()->call(cursor,"advance"));
                expire(owner);assertThrows(IllegalStateException.class,result.sites()::size);assertThrows(IllegalStateException.class,result.edges()::size);
                assertNotNull(program.admission().snapshot());assertTrue(pages.statistics().livePages()>0,"borrowed input pages stay alive");
            }
        });
    }
    @Test void resultOwnerRetiresOnlyDerivedPagesAndProgramExpiryInvalidatesTheLiveCursor()throws Exception {
        nativeInput(4,(program,cfg,pages,resources,root)-> {
            var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);expected.sites().forEach(ManagedExecutableDependencyResultTest::manual);
            var bytes=new java.io.ByteArrayOutputStream();new DependencyJson().writeSnapshot(expected,program,bytes);
            long heap=root.heapUsed(),live=pages.statistics().livePages();
            try(var owner=managed(program,cfg,pages,resources)) {
                var result=view(owner);assertEquals(4,result.sites().size());expire(owner);
                assertEquals(heap,root.heapUsed());assertEquals(live,pages.statistics().livePages());
                assertThrows(IllegalStateException.class,()->result.sites().getFirst());assertNotNull(program.admission().snapshot());
            }
            try(var owner=managed(program,cfg,pages,resources);var cursor=(AutoCloseable)call(owner,"cursor")) {
                assertTrue((Boolean)call(cursor,"advance"));manual((DependencySiteFact)call(cursor,"fact"));
                program.close();assertThrows(IllegalStateException.class,()->call(cursor,"fact"));
                assertThrows(IllegalStateException.class,()->view(owner));
            }
        });
    }
}
