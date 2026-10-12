package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Program lookups must not project every other cold complete fact per request. */
final class ManagedExecutableDependencyProgramsTest {
    @Test void plainProgramRequestsProjectOnlyMatchingCompleteFactsNotRequestsTimesAllSites()throws Exception {
        var harness=new ManagedExecutableDependencyResultTest();
        harness.directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"managed-program-law-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
            var program=(SnapshotProgram)args[0];var cfg=(CfgBuildResult)args[1];var pages=(PageStore)args[2];var resources=(AnalysisResources)args[3];
            var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);
            assertEquals(16,expected.programDependencies().size());
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)) {
                assertEquals(0,owner.fullFactProjections());var actual=owner.result();
                assertEquals(expected.programDependencies().size(),actual.programDependencies().size());
                for(int i=0;i<expected.programDependencies().size();i++) {
                    var resolution=actual.programDependencies().get(i);
                    assertEquals(expected.programDependencies().get(i),resolution,"all occurrence,authority,candidate,proof,remainder and site fields");
                    assertEquals(1,resolution.executableSites().size());
                    assertEquals("PROGA",resolution.candidates().getFirst().referenceName());
                    assertEquals("PROGA   ",resolution.candidates().getFirst().rawValue());
                    assertEquals("seed",resolution.candidates().getFirst().executableSupports().getFirst().producer().localId());
                }
                assertEquals(16,owner.fullFactProjections(),"PLAIN_PROGRAM_LOOKUP_PROJECTS_REQUESTS_TIMES_WHOLE_FACT_RESULT_RED");
            }
            return null;
        });
        var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
        try{input.invoke(harness,16,work);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
}
