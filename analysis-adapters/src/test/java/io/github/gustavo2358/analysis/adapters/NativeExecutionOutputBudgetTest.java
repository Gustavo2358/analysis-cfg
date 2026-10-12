package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.lang.reflect.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native execution views must charge output to the admitted common ledger. */
final class NativeExecutionOutputBudgetTest {
    @FunctionalInterface private interface Work{void run(SnapshotProgram program,AnalysisResources execution,AnalysisResources root);}
    private static void nativeInput(Work action)throws Exception {
        var harness=new ManagedExecutableDependencyResultTest();harness.directory=Files.createTempDirectory(Path.of("target"),"native-output-law-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{action.run((SnapshotProgram)args[0],(AnalysisResources)args[3],(AnalysisResources)args[4]);return null;});
        var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
        try{input.invoke(harness,1,work);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    @Test void distinctExecutionViewsShareExactCommonOutputChargeWithoutDoubleCharging()throws Exception {
        nativeInput((program,execution,root)->{
            long before=root.outputUsed();execution.output(10,AnalysisResources.Phase.ENCODE);
            assertEquals(before+10,root.outputUsed(),"NATIVE_OUTPUT_CHARGE_ESCAPES_COMMON_OWNER_RED");assertEquals(10,execution.outputUsed());
            var other=AnalysisResources.executionFor(program);other.output(7,AnalysisResources.Phase.ENCODE);
            assertEquals(before+17,root.outputUsed());assertEquals(10,execution.outputUsed());assertEquals(7,other.outputUsed());
            program.close();assertThrows(IllegalStateException.class,()->other.output(1,AnalysisResources.Phase.ENCODE));assertEquals(before+17,root.outputUsed());assertEquals(7,other.outputUsed());
        });
    }
    @Test void commonOutputDenialLeavesBothCountersUnchangedAndPreservesTypedPhase()throws Exception {
        nativeInput((program,execution,root)->{
            root.output(root.limits().outputBytes()-root.outputUsed()-1,AnalysisResources.Phase.CONTROL);long before=root.outputUsed(),local=execution.outputUsed();
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->execution.output(2,AnalysisResources.Phase.ENCODE),"NATIVE_OUTPUT_DENIAL_ESCAPES_COMMON_OWNER_RED");
            assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals(AnalysisResources.Phase.ENCODE,failure.phase());
            assertEquals(before,root.outputUsed());assertEquals(local,execution.outputUsed());assertNotNull(program.admission().snapshot());
        });
    }
}
