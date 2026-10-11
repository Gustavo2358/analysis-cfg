package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.consumers.FactConsumer;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.dependencies.DependencyAnalysis;
import io.github.gustavo2358.analysis.dependencies.DependencyResult;
import io.github.gustavo2358.analysis.solver.PageStore;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.air.model.Operations;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Port transaction laws on actual admitted native planning, not a paged-codec oracle.
 * Reflection lets the identical source expose the missing productive port before implementation. */
final class NativeFactStoragePortTest {
    private static Class<?> storageType() {
        try{return Class.forName("io.github.gustavo2358.analysis.consumers.FactStorage");}
        catch(ClassNotFoundException absent){throw new AssertionError("NATIVE_GENERAL_FACT_STORAGE_PORT_ABSENT_RED",absent);}
    }
    private static final class Probe implements InvocationHandler {
        final List<String> values=new ArrayList<>();
        final RuntimeException primary=new IllegalStateException("typed storage append failure");
        boolean closed,deny;int appends,reads,closes;RuntimeException closeFailure;
        Object port(){return Proxy.newProxyInstance(storageType().getClassLoader(),new Class<?>[]{storageType()},this);}
        @Override public Object invoke(Object proxy,Method method,Object[] arguments) {
            return switch(method.getName()) {
                case "append" -> {if(closed)throw new IllegalStateException("closed storage");appends++;if(deny)throw primary;values.add((String)arguments[0]);yield null;}
                case "size" -> {if(closed)throw new IllegalStateException("closed storage");yield values.size();}
                case "get" -> {if(closed)throw new IllegalStateException("closed storage");reads++;yield values.get((Integer)arguments[0]);}
                case "close" -> {if(!closed){closed=true;values.clear();closes++;if(closeFailure!=null)throw closeFailure;}yield null;}
                case "toString" -> "exclusive typed storage probe";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==arguments[0];
                default -> throw new AssertionError(method);
            };
        }
    }
    @FunctionalInterface private interface Work {void run(PlanningExecution runtime,ConsumerRegistration<String> registration)throws Exception;}
    private static void nativePlan(FactConsumer<String> consumer,Work work)throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
            assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
            var entry=program.units().getFirst().entries().getFirst().id();
            var opened=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry));assertEquals(AnalysisSession.Status.ACCEPTED,opened.status());
            var registration=new ConsumerRegistration<>(new ConsumerPlan("complete-native-facts",List.of(),List.of()),
                List.of(new SiteInterest(Operations.Invoke.class,entry,site->true)),List.of(),consumer);
            try(var runtime=new PlanningExecution(opened.session().orElseThrow(),new AnalysisRegistry(List.of()))){work.run(runtime,registration);}
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
    }
    private static PreparedAnalysisResult<?> execute(PlanningExecution runtime,ExecutionPlan<String> plan,Probe storage)throws Exception {
        storageType();
        try {
            var method=PlanningExecution.class.getMethod("executeStored",String.class,ExecutionPlan.class,Function.class);
            return (PreparedAnalysisResult<?>)method.invoke(runtime,"typed-storage",plan,(Function<String,Object>)id->{assertEquals("complete-native-facts",id);return storage.port();});
        }catch(InvocationTargetException failure) {
            if(failure.getCause() instanceof RuntimeException runtimeFailure)throw runtimeFailure;
            if(failure.getCause() instanceof Error error)throw error;
            throw failure;
        }
    }
    @Test void nativeStoredStagingCommitsOnlyThroughTheSelectedTypedPortAndExpiresWithExecution()throws Exception {
        var storage=new Probe();
        nativePlan((site,facts,sink)->sink.emit("full-unit="+site.entry().unit().localId()+";op="+site.operationId().localId()),(runtime,registration)-> {
            var result=execute(runtime,runtime.plan(List.of(registration)),storage);
            assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());
            var delivered=result.consumers().getFirst().facts();assertFalse(delivered.isEmpty());
            assertEquals(delivered.size(),storage.appends);assertEquals(0,storage.reads,"commit must not materialize the complete typed store");
            assertEquals(storage.values.getFirst(),delivered.getFirst());assertEquals(1,storage.reads);
            assertThrows(UnsupportedOperationException.class,delivered::clear);
            runtime.close();assertEquals(1,storage.closes);assertTrue(storage.values.isEmpty());
            assertThrows(IllegalStateException.class,delivered::size);assertThrows(IllegalStateException.class,delivered::getFirst);
        });
    }
    @Test void consumerFailureDiscardsTheWholeTypedTransactionAndCannotPublishStagedFacts()throws Exception {
        var storage=new Probe();
        nativePlan((site,facts,sink)->{sink.emit("not-published");throw new FactConsumer.ConsumerException("late callback failure");},(runtime,registration)-> {
            var result=execute(runtime,runtime.plan(List.of(registration)),storage);
            assertEquals(PreparedAnalysisResult.PreparationStatus.INCOMPLETE,result.preparationStatus());
            assertEquals(PreparedAnalysisResult.ConsumerStatus.FAILED,result.consumers().getFirst().status());
            assertTrue(result.consumers().getFirst().facts().isEmpty());assertEquals(1,storage.appends);assertEquals(1,storage.closes);assertTrue(storage.values.isEmpty());
            runtime.close();assertEquals(1,storage.closes);
        });
    }
    @Test void storageFailureKeepsItsPrimaryAbortsStagingAndNeverFallsBackToResidentFacts()throws Exception {
        var storage=new Probe();storage.deny=true;
        nativePlan((site,facts,sink)->sink.emit("must-use-typed-port"),(runtime,registration)-> {
            assertSame(storage.primary,assertThrows(IllegalStateException.class,()->execute(runtime,runtime.plan(List.of(registration)),storage)));
            assertEquals(1,storage.appends);assertEquals(1,storage.closes);assertTrue(storage.values.isEmpty());
            runtime.close();assertEquals(1,storage.closes);
        });
    }
    private static DependencyResult application(DependencyAnalysis analysis,SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources resources)throws Exception {
        Method method;
        try{method=DependencyAnalysis.class.getMethod("prepareLeased",SnapshotProgram.class,CfgBuildResult.class,PageStore.class,AnalysisResources.class);}
        catch(NoSuchMethodException absent){throw new AssertionError("NATIVE_APPLICATION_TYPED_STAGING_PORT_ABSENT_RED",absent);}
        try{return (DependencyResult)method.invoke(analysis,program,cfg,pages,resources);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException runtime)throw runtime;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    @Test void nativeApplicationUsesActualTypedStagingPreservesWholeWireAndKeepsStorageFailurePrimary()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),BuildOptions.defaults());assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
            var analysis=new DependencyAnalysis();var legacy=analysis.prepareLeased(program,cfg);
            var expected=new java.io.ByteArrayOutputStream();new DependencyJson().writeSnapshot(legacy,program,expected);
            boolean[] deny={false};int[] writes={0};var primary=new IllegalStateException("actual typed CALL staging write failed");
            var pages=(PageStore)Proxy.newProxyInstance(PageStore.class.getClassLoader(),new Class<?>[]{PageStore.class},(proxy,method,args)-> {
                if(method.getName().equals("close"))throw new AssertionError("borrowed pages cannot close");
                if(method.getName().equals("write")&&StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals("io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage")))) {
                    writes[0]++;if(deny[0])throw primary;
                }
                try{return method.invoke(input.borrowedPages(),args);}
                catch(InvocationTargetException failure){throw failure.getCause();}
            });
            var execution=AnalysisResources.executionFor(program);long before=resources.heapUsed(),live=input.borrowedPages().statistics().livePages();
            var result=application(analysis,program,cfg,pages,execution);assertTrue(writes[0]>0,"actual general native CALL facts must be encoded into the selected typed store");
            var actual=new java.io.ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,program,actual);assertArrayEquals(expected.toByteArray(),actual.toByteArray());
            assertEquals(before,resources.heapUsed());assertEquals(live,input.borrowedPages().statistics().livePages());
            deny[0]=true;int prior=writes[0];
            assertSame(primary,assertThrows(IllegalStateException.class,()->application(analysis,program,cfg,pages,execution)));assertTrue(writes[0]>prior);
            assertEquals(before,resources.heapUsed());assertEquals(live,input.borrowedPages().statistics().livePages());program.admission().snapshot().root();
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
    }
    @Test void typedCleanupFailureDoesNotMaskTheOriginalConsumerFailure()throws Exception {
        var storage=new Probe();storage.closeFailure=new IllegalStateException("typed storage cleanup failed");
        var primary=new FactConsumer.ConsumerException("original consumer callback failed");
        nativePlan((site,facts,sink)->{sink.emit("not-published");throw primary;},(runtime,registration)-> {
            var failure=assertThrows(RuntimeException.class,()->execute(runtime,runtime.plan(List.of(registration)),storage));
            assertSame(primary,failure,"NATIVE_STAGING_CONSUMER_PRIMARY_MASKED_RED");
            assertArrayEquals(new Throwable[]{storage.closeFailure},failure.getSuppressed());assertEquals(1,storage.closes);assertTrue(storage.values.isEmpty());
        });
    }
    @Test void failedLaterEpochRetiresOnlyItsOwnTypedStoresAndKeepsPriorCommittedFactsAlive()throws Exception {
        var first=new Probe();var second=new Probe();second.deny=true;
        nativePlan((site,facts,sink)->sink.emit("stable-first-epoch"),(runtime,registration)-> {
            var result=execute(runtime,runtime.plan(List.of(registration)),first);var facts=result.consumers().getFirst().facts();assertFalse(facts.isEmpty());
            assertSame(second.primary,assertThrows(IllegalStateException.class,()->execute(runtime,runtime.plan(List.of(registration)),second)));
            assertEquals(1,second.closes);assertEquals(0,first.closes);assertEquals("stable-first-epoch",facts.getFirst());
            runtime.close();assertEquals(1,first.closes);assertThrows(IllegalStateException.class,facts::size);
        });
    }
}
