package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.cfg.adapters.CfgJsonWriter;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PipelineCliTest {
    @TempDir Path dir;
    Path resource(String path){var root=Files.isDirectory(Path.of("analysis-adapters"))?Path.of("."):Path.of("..");return root.resolve("analysis-adapters/src/test/resources").resolve(path);}
    Path fixture(){return resource("cp6/dynamic-x8.air.json");}
    PrintStream errors(){return new PrintStream(new ByteArrayOutputStream());}
    String[] args(Path input,Path cfg,Path dependencies){return new String[]{input.toString(),cfg.toString(),dependencies.toString()};}
    @Test void oneReadAndCompleteProductsEqualIndependentSeparateRoutes() throws Exception {
        var expectedCfg=dir.resolve("expected.cfg");var expectedDependencies=dir.resolve("expected.dependencies");
        var independent=new DataflowAirReader().read(fixture());
        new CfgJsonWriter().write(new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(independent.checked().orElseThrow(),BuildOptions.defaults()),expectedCfg);
        assertEquals(0,AnalysisDependencies.run(new String[]{fixture().toString(),expectedDependencies.toString()},errors()));
        var cfg=dir.resolve("cfg");var dependencies=dir.resolve("dependencies");var count=new AtomicInteger();
        for(int n=1;n<=2;n++) {
            assertEquals(0,AnalysisPipeline.run(args(fixture(),cfg,dependencies),errors(),path->{count.incrementAndGet();return new DataflowAirReader().read(path);}));
            assertEquals(n,count.get());assertArrayEquals(Files.readAllBytes(expectedCfg),Files.readAllBytes(cfg));assertArrayEquals(Files.readAllBytes(expectedDependencies),Files.readAllBytes(dependencies));
        }
    }
    @Test void invalidIncompleteDigestAndAliasesRejectBeforeAnyDestination() throws Exception {
        var input=dir.resolve("input");var cfg=dir.resolve("cfg");var dependencies=dir.resolve("dependencies");Files.writeString(cfg,"cfg sentinel");Files.writeString(dependencies,"dependencies sentinel");
        Files.writeString(input,"{");assertEquals(3,AnalysisPipeline.run(args(input,cfg,dependencies),errors()));
        assertEquals("cfg sentinel",Files.readString(cfg));assertEquals("dependencies sentinel",Files.readString(dependencies));
        assertEquals(3,AnalysisPipeline.run(args(resource("ep/unproved-codec.air.json"),cfg,dependencies),errors()));
        Files.copy(fixture(),input,StandardCopyOption.REPLACE_EXISTING);
        assertEquals(2,AnalysisPipeline.run(args(input,cfg,cfg),errors()));assertEquals(2,AnalysisPipeline.run(args(input,input,dependencies),errors()));
        var alias=dir.resolve("alias");Files.createSymbolicLink(alias,input);assertEquals(2,AnalysisPipeline.run(args(input,alias,dependencies),errors()));
        var hard=dir.resolve("hard");Files.createLink(hard,input);assertEquals(2,AnalysisPipeline.run(args(input,hard,dependencies),errors()));
        var source=dir.resolve("source");Files.writeString(source,"{}");
        assertEquals(3,AnalysisPipeline.run(new String[]{input.toString(),cfg.toString(),dependencies.toString(),"--source-evidence",source.toString()},errors()));
        assertEquals("cfg sentinel",Files.readString(cfg));assertEquals("dependencies sentinel",Files.readString(dependencies));
    }
    @Test void fullCorrelatedSourceAndDigestMismatchPreserveProducts() throws Exception {
        var air=dir.resolve("air.json");var source=dir.resolve("source.json");var cfg=dir.resolve("cfg");var dependencies=dir.resolve("dependencies");
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.air.json")){Files.write(air,stream.readAllBytes());}
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.source.json")){Files.write(source,stream.readAllBytes());}
        var arguments=new String[]{air.toString(),cfg.toString(),dependencies.toString(),"--source-evidence",source.toString()};
        assertEquals(0,AnalysisPipeline.run(arguments,errors()));var originalCfg=Files.readAllBytes(cfg);var originalDependencies=Files.readAllBytes(dependencies);
        var separate=dir.resolve("separate.dependencies");assertEquals(0,AnalysisDependencies.run(new String[]{air.toString(),separate.toString(),"--source-evidence",source.toString()},errors()));assertArrayEquals(Files.readAllBytes(separate),originalDependencies);
        Files.writeString(air,Files.readString(air)+" ");assertEquals(3,AnalysisPipeline.run(arguments,errors()));assertArrayEquals(originalCfg,Files.readAllBytes(cfg));assertArrayEquals(originalDependencies,Files.readAllBytes(dependencies));
    }
    @Test void explicitCodecLimitNeverStartsExportOrPublishesFallback() throws Exception {
        var cfg=dir.resolve("cfg");var dependencies=dir.resolve("dependencies");Files.writeString(cfg,"cfg sentinel");Files.writeString(dependencies,"dependencies sentinel");
        var codec=new io.github.gustavo2358.air.json.AirJson(new io.github.gustavo2358.air.json.AirJson.Limits(1,128),io.github.gustavo2358.air.validation.ValidationOptions.defaults());
        assertEquals(7,AnalysisPipeline.run(args(fixture(),cfg,dependencies),errors(),new DataflowAirReader(codec)::read));
        assertEquals("cfg sentinel",Files.readString(cfg));assertEquals("dependencies sentinel",Files.readString(dependencies));
    }
    @Test void writerFailuresKeepCompletedProductsAndCleanStaging() throws Exception {
        var cfg=dir.resolve("cfg");var dependencies=dir.resolve("dependencies");Files.writeString(cfg,"cfg sentinel");Files.writeString(dependencies,"dependencies sentinel");
        assertEquals(6,AnalysisPipeline.run(args(fixture(),dir.resolve("missing/cfg"),dependencies),errors()));assertEquals("dependencies sentinel",Files.readString(dependencies));
        assertEquals(6,AnalysisPipeline.run(args(fixture(),cfg,dir.resolve("missing/dependencies")),errors()));assertNotEquals("cfg sentinel",Files.readString(cfg));
        try(var files=Files.list(dir)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".")));}
        assertEquals(2,AnalysisPipeline.run(new String[]{},errors()));
        assertEquals(2,AnalysisPipeline.run(new String[]{fixture().toString(),cfg.toString(),dependencies.toString(),"--unknown"},errors()));
    }
}
