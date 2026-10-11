package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Full existing wire, scoped consumption and original atomic failure contract. */
final class ManagedExecutableDependencyWriterTest {
    @FunctionalInterface private interface NativeWork {void run(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources execution,AnalysisResources root)throws Exception;}
    private static void nativeInput(int count,NativeWork action)throws Exception {
        var harness=new ManagedExecutableDependencyResultTest();harness.directory=Files.createTempDirectory(Path.of("target"),"managed-writer-law-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{action.run((SnapshotProgram)args[0],(CfgBuildResult)args[1],(PageStore)args[2],(AnalysisResources)args[3],(AnalysisResources)args[4]);return null;});
        var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
        try{input.invoke(harness,count,work);}catch(InvocationTargetException failure){raise(failure.getCause());}
    }
    private static void raise(Throwable failure)throws Exception{if(failure instanceof Exception error)throw error;if(failure instanceof Error error)throw error;throw new AssertionError(failure);}
    private static void call(Object writer,Class<?>[] types,Object... args)throws Exception {
        Method method;
        try{method=writer.getClass().getMethod(writer instanceof DependencyJson?"writeSnapshot":"write",types);}
        catch(NoSuchMethodException absent){throw new AssertionError("GENERAL_MANAGED_OWNER_WRITER_PORT_ABSENT_RED",absent);}
        try{method.invoke(writer,args);}catch(InvocationTargetException failure){raise(failure.getCause());}
    }
    @Test void wholeWireMatchesGeneralCompatibilityAndProjectsOneSitePerSectionWithoutWholeSort()throws Exception {
        nativeInput(16,(program,cfg,pages,resources,root)->{
            var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);
            assertEquals(16,expected.sites().size());assertEquals(16,expected.edges().size());
            for(var fact:expected.sites()){assertEquals("PROGA",fact.candidates().getFirst().referenceName());assertEquals("PROGA   ",fact.candidates().getFirst().rawValue());assertEquals("seed",fact.candidates().getFirst().supports().getFirst().producer().localId());}
            var reference=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(expected,program,reference);
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)){
                var actual=new ByteArrayOutputStream();long before=resources.used(AnalysisResources.Pool.SCRATCH);
                call(new DependencyJson(),new Class<?>[]{ManagedExecutableDependencyResult.class,SnapshotProgram.class,AnalysisResources.class,OutputStream.class},owner,program,resources,actual);
                assertArrayEquals(reference.toByteArray(),actual.toByteArray(),"complete native wire bytes including all fields,metrics,coverage,programs and proofs");
                assertEquals(48,owner.fullFactProjections(),"one complete site per programs/edges/sites section, no whole product sort or candidate-count scan");
                assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));
            }
        });
    }
    @Test void atomicOutputQuotaFailurePreservesDestinationAndBorrowedProgram()throws Exception {
        nativeInput(4,(program,cfg,pages,resources,root)->{
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)){
                var parent=Files.createTempDirectory(Path.of("target"),"managed-atomic-law-");var destination=parent.resolve("dependencies.json");var old=new byte[]{1,7,9,3};Files.write(destination,old);
                root.output(root.limits().outputBytes()-root.outputUsed()-1,AnalysisResources.Phase.CONTROL);
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->call(new SnapshotDependencyFileWriter(),new Class<?>[]{ManagedExecutableDependencyResult.class,SnapshotProgram.class,Path.class,AnalysisResources.class},owner,program,destination,resources));
                assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals(AnalysisResources.Phase.ENCODE,failure.phase());assertArrayEquals(old,Files.readAllBytes(destination));
                try(var files=Files.list(parent)){assertEquals(1,files.count(),"no partial temporary product can remain");}
                assertNotNull(program.admission().snapshot());
            }
        });
    }
}
