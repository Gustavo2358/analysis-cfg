package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Operational deadline configuration only: unchanged defaults, other quotas and product. */
final class ConfiguredDeadlineCliTest {
    @TempDir Path directory;
    private Path fixture() { return Path.of("../analysis-adapters/src/test/resources/cp6/dynamic-x8.air.json"); }
    @Test void deadlineOptionsRespectExistingPipelineValuesAndFiniteIntegerBoundary() {
        assertEquals(Duration.ofMinutes(8),AnalysisDeadline.parse(new String[]{"input","cfg","deps","--source-evidence","--deadline-seconds"},3,true),"an option-shaped source path stays a path");
        for(String[] options:new String[][]{
            {"--deadline-seconds","1200","--source-evidence","source","--experimental-physical"},
            {"--experimental-physical","--source-evidence","source","--deadline-seconds","1200"},
            {"--source-evidence","--deadline-seconds","--deadline-seconds","1200"}}) {
            var args=new String[options.length+3];args[0]="input";args[1]="cfg";args[2]="deps";System.arraycopy(options,0,args,3,options.length);
            assertEquals(Duration.ofSeconds(1200),AnalysisDeadline.parse(args,3,true));
        }
        assertEquals(Duration.ofSeconds(9223372036L),AnalysisDeadline.parse(new String[]{"input","deps","--deadline-seconds","9223372036"},2,false));
        assertEquals(Duration.ofSeconds(1),AnalysisDeadline.parse(new String[]{"input","deps","--deadline-seconds","0001"},2,false));
        assertThrows(IllegalArgumentException.class,()->AnalysisDeadline.parse(new String[]{"input","deps","--deadline-seconds","\u0661"},2,false));
    }
    @Test void explicitLongerDeadlinePreservesCompleteDependencyProduct() throws Exception {
        Path original=directory.resolve("original.json"),configured=directory.resolve("configured.json");
        var errors=new ByteArrayOutputStream();
        assertEquals(0,AnalysisDependencies.run(new String[]{fixture().toString(),original.toString()},new PrintStream(errors)),errors.toString());
        assertEquals(0,AnalysisDependencies.run(new String[]{fixture().toString(),configured.toString(),"--deadline-seconds","1200"},new PrintStream(errors)),"CONFIGURED_DEADLINE_OPTION_REJECTED_RED: "+errors);
        assertArrayEquals(Files.readAllBytes(original),Files.readAllBytes(configured),"all fields/metrics unchanged by operational deadline");
    }
    @Test void pipelineDeadlineChangesOnlySharedTimeAndDefaultRemainsEightMinutes() throws Exception {
        Path cfg=directory.resolve("cfg.json"),dependencies=directory.resolve("dependencies.json");
        byte[] expectedCfg=null,expectedDependencies=null;
        for(boolean configured:new boolean[]{false,true}) {
            var seen=new AtomicReference<AnalysisResources>();var errors=new ByteArrayOutputStream();
            var arguments=configured?new String[]{fixture().toString(),cfg.toString(),dependencies.toString(),"--deadline-seconds","1200"}:new String[]{fixture().toString(),cfg.toString(),dependencies.toString()};
            AnalysisPipeline.SnapshotAirRead reader=(path,resources)->{seen.set(resources);return new DataflowAirReader().readSnapshot(path,resources);};
            assertEquals(0,AnalysisPipeline.runSnapshot(arguments,new PrintStream(errors),reader),errors.toString());
            var resources=seen.get();assertNotNull(resources);
            var field=AnalysisResources.class.getDeclaredField("timeoutNanos");field.setAccessible(true);
            assertEquals((configured?1200L:480L)*1_000_000_000L,field.getLong(resources));
            assertEquals(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,16L*1024*1024*1024,8,Long.MAX_VALUE,4L*1024*1024*1024),resources.limits());
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
            if(!configured){expectedCfg=Files.readAllBytes(cfg);expectedDependencies=Files.readAllBytes(dependencies);}
            else{assertArrayEquals(expectedCfg,Files.readAllBytes(cfg));assertArrayEquals(expectedDependencies,Files.readAllBytes(dependencies));}
        }
    }
    @Test void malformedAndDuplicateDeadlinesNeverReadInputOrReplaceDestinations() throws Exception {
        Path missing=directory.resolve("missing.air.json"),cfg=directory.resolve("cfg.json"),output=directory.resolve("dependencies.json");
        for(String value:new String[]{"","0","-1","+1","1.0","1e3"," 1200","NaN","Infinity","9223372037","9223372036854775808"}) {
            Files.writeString(cfg,"cfg sentinel");Files.writeString(output,"dependency sentinel");
            var errors=new ByteArrayOutputStream();
            assertEquals(2,AnalysisDependencies.run(new String[]{missing.toString(),output.toString(),"--deadline-seconds",value},new PrintStream(errors)),value+" "+errors);
            assertEquals(2,AnalysisPipeline.run(new String[]{missing.toString(),cfg.toString(),output.toString(),"--deadline-seconds",value},new PrintStream(errors)),value+" "+errors);
            assertEquals("cfg sentinel",Files.readString(cfg));assertEquals("dependency sentinel",Files.readString(output));
        }
        for(String[] arguments:new String[][]{
            {missing.toString(),output.toString(),"--deadline-seconds"},
            {missing.toString(),output.toString(),"--deadline-seconds","1200","--deadline-seconds","1200"},
            {missing.toString(),output.toString(),"--deadline-seconds","1200","--experimental-physical"}})
            assertEquals(2,AnalysisDependencies.run(arguments,new PrintStream(new ByteArrayOutputStream())));
        for(String[] arguments:new String[][]{
            {missing.toString(),cfg.toString(),output.toString(),"--deadline-seconds"},
            {missing.toString(),cfg.toString(),output.toString(),"--deadline-seconds","1200","--deadline-seconds","1200"}})
            assertEquals(2,AnalysisPipeline.run(arguments,new PrintStream(new ByteArrayOutputStream())));
        assertFalse(Files.exists(missing));
        try(var files=Files.list(directory)){assertEquals(2,files.count(),"no staged or input file created for bad configuration");}
    }
}
