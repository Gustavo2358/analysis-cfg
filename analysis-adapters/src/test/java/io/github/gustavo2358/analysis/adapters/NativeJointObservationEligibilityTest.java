package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.structure.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Observe actual native capacity calls, not unrelated physical I/O requirements. */
final class NativeJointObservationEligibilityTest {
    @Test void emptyNativePlansNeverReserveJointCapacity()throws Exception {
        var harness=new ManagedExecutableDependencyResultTest();harness.directory=Files.createTempDirectory(Path.of("target"),"native-joint-empty-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
            var program=(SnapshotProgram)args[0];var cfg=(CfgBuildResult)args[1];var root=(AnalysisResources)args[4];
            var calls=new AtomicInteger();var budget=program.executionBudget().orElseThrow();
            ProgramStore.ExecutionBudget observed=new ProgramStore.ExecutionBudget() {
                public ProgramStore.CapacityLease reserve(ProgramStore.CapacityPool pool,long amount,ProgramStore.ExecutionPhase phase) {
                    calls.incrementAndGet();return budget.reserve(pool,amount,phase);
                }
                public void output(long bytes,ProgramStore.ExecutionPhase phase){budget.output(bytes,phase);}
            };
            var store=(ProgramStore.Structural)Proxy.newProxyInstance(ProgramStore.Structural.class.getClassLoader(),new Class<?>[]{ProgramStore.Structural.class},(owner,call,arguments)->{
                if(call.getName().equals("executionBudget"))return Optional.of(observed);
                try{return call.invoke(program,arguments);}catch(InvocationTargetException failure){throw failure.getCause();}
            });
            var entry=program.units().getFirst().entries().getFirst().id();
            var admission=AnalysisSession.open(cfg,store,ProjectionPolicy.KNOWN_SUBSET,List.of(entry));
            assertEquals(AnalysisSession.Status.ACCEPTED,admission.status(),admission.reason());
            try(var execution=new PlanningExecution(admission.session().orElseThrow(),new AnalysisRegistry(List.of()))) {
                var plan=execution.plan(List.of());calls.set(0);long scratch=root.used(AnalysisResources.Pool.SCRATCH);
                var result=execution.execute("empty",plan);
                assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());assertTrue(result.analyses().isEmpty());assertTrue(result.results().isEmpty());assertTrue(result.consumers().isEmpty());
                assertEquals(scratch,root.used(AnalysisResources.Pool.SCRATCH));
                assertEquals(0,calls.get(),"EMPTY_NATIVE_PLAN_RESERVES_JOINT_CAPACITY_RED");
            }
            return null;
        });
        var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
        try{input.invoke(harness,1,work);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
}
