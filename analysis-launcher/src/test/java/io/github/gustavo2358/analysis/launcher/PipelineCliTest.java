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
        new DependencyFileWriter().write(new DependencyAnalysis().prepare(independent.publication()),expectedDependencies);
        var cfg=dir.resolve("cfg");var dependencies=dir.resolve("dependencies");var count=new AtomicInteger();
        for(int n=1;n<=2;n++) {
            assertEquals(0,AnalysisPipeline.run(args(fixture(),cfg,dependencies),errors(),path->{count.incrementAndGet();return new DataflowAirReader().read(path);}));
            assertEquals(n,count.get());assertArrayEquals(Files.readAllBytes(expectedCfg),Files.readAllBytes(cfg));assertArrayEquals(Files.readAllBytes(expectedDependencies),Files.readAllBytes(dependencies));
        }
    }
    @Test void defaultRoutePublishesCfgAndDependenciesFromTheValidatedSnapshot() throws Exception {
        var cfg=dir.resolve("snapshot.cfg");var dependencies=dir.resolve("snapshot.dependencies");
        assertEquals(0,AnalysisPipeline.run(args(fixture(),cfg,dependencies),errors()));
        var expectedCfg=dir.resolve("resident-reference.cfg");var reference=new DataflowAirReader().read(fixture());
        new CfgJsonWriter().write(new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(
                reference.checked().orElseThrow(),BuildOptions.defaults()),expectedCfg);
        assertArrayEquals(Files.readAllBytes(expectedCfg),Files.readAllBytes(cfg));
        var json=Files.readString(dependencies);
        assertTrue(json.contains("\"modelScope\":\"VALIDATED_SNAPSHOT_DEPENDENCY\""));
        assertTrue(json.contains("\"version\":\"3.0.0\""));
        assertTrue(json.contains("\"referenceName\":\"PROGA\""));
        assertTrue(json.contains("\"rawValue\":\"PROGA   \""));
        assertTrue(json.contains("\"kind\":\"VALUE_PRODUCER\""));
        assertTrue(json.contains("\"analysisStatus\":\"PARTIAL\""));
    }
    @Test void sharedLabelEntriesReachEverySiteThroughTheRealSnapshotPipeline() throws Exception {
        var codec=new io.github.gustavo2358.air.json.AirJson();var base=codec.decode(Files.readAllBytes(fixture()));
        var unit=base.units().getFirst();var first=unit.entries().getFirst();
        // Preserve the original Entry: existing coverage may refer to its full identity.
        var entries=java.util.stream.IntStream.range(0,4).mapToObj(i->i==0?first:new io.github.gustavo2358.air.model.Entries.Entry(
                new io.github.gustavo2358.air.model.Ids.EntryId(unit.id(),"entry-"+i),first.initialLabel(),first.signature(),first.state(),first.origin())).toList().reversed();
        var changed=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),entries,
                unit.sequences().reversed(),unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var publication=new io.github.gustavo2358.air.model.Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),java.util.List.of(changed),
                base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var input=dir.resolve("shared.air.json");Files.write(input,codec.encode(publication));
        var cfg=dir.resolve("shared.cfg");var dependencies=dir.resolve("shared.dependencies");var diagnostic=new ByteArrayOutputStream();
        assertEquals(0,AnalysisPipeline.run(args(input,cfg,dependencies),new PrintStream(diagnostic)),diagnostic.toString());
        var expected=dir.resolve("resident.cfg");var reference=new DataflowAirReader().read(input);
        new CfgJsonWriter().write(new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(reference.checked().orElseThrow(),BuildOptions.defaults()),expected);
        assertArrayEquals(Files.readAllBytes(expected),Files.readAllBytes(cfg));
        var json=Files.readString(dependencies);assertTrue(json.contains("\"metrics\":{\"candidates\":4,\"sites\":4}"),json);
        for(var entry:entries)assertTrue(json.contains("\"localId\":\""+entry.id().localId()+"\""),json);
        assertEquals(8,json.split("\\\"referenceName\\\":\\\"PROGA\\\"",-1).length-1,"each site and edge must retain its candidate");
        assertTrue(json.contains("\"rawValue\":\"PROGA   \""));assertTrue(json.contains("\"kind\":\"VALUE_PRODUCER\""));assertTrue(json.contains("\"analysisStatus\":\"PARTIAL\""));
        var separate=dir.resolve("standalone.dependencies");
        assertEquals(0,AnalysisDependencies.run(new String[]{input.toString(),separate.toString()},new PrintStream(diagnostic)),diagnostic.toString());
        assertArrayEquals(Files.readAllBytes(dependencies),Files.readAllBytes(separate));
        var completedCfg=Files.readAllBytes(cfg);var completedDependencies=Files.readAllBytes(dependencies);
        var different=new java.util.ArrayList<>(entries);var later=different.getLast();
        different.set(different.size()-1,new io.github.gustavo2358.air.model.Entries.Entry(later.id(),java.util.Optional.of(unit.sequences().getLast().label()),later.signature(),later.state(),later.origin()));
        var unprovedUnit=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),different,
                unit.sequences(),unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var unproved=new io.github.gustavo2358.air.model.Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),java.util.List.of(unprovedUnit),
                base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        Files.write(input,codec.encode(unproved));
        assertEquals(3,AnalysisPipeline.run(args(input,cfg,dependencies),new PrintStream(diagnostic)),diagnostic.toString());
        assertEquals(7,AnalysisDependencies.run(new String[]{input.toString(),separate.toString()},new PrintStream(diagnostic)),diagnostic.toString());
        assertArrayEquals(completedCfg,Files.readAllBytes(cfg));assertArrayEquals(completedDependencies,Files.readAllBytes(dependencies));assertArrayEquals(completedDependencies,Files.readAllBytes(separate));
    }
    @Test void largerUnitKeepsOrphanReturnHaltAndDependencyThroughTheRealPipeline() throws Exception {
        var codec=new io.github.gustavo2358.air.json.AirJson();var base=codec.decode(Files.readAllBytes(fixture()));
        var unit=base.units().getFirst();var original=unit.sequences().getFirst().terminator().header();
        var sequences=new java.util.ArrayList<>(unit.sequences());
        for(int i=0;i<64;i++) {
            var header=new io.github.gustavo2358.air.model.Operations.Header(new io.github.gustavo2358.air.model.Ids.OperationId(unit.id(),"orphan-control-"+i),original.origin(),original.coverage(),original.precision(),original.uncertainties());
            io.github.gustavo2358.air.model.Terminator control=i%2==0?new io.github.gustavo2358.air.model.Operations.Return(header,java.util.List.of()):new io.github.gustavo2358.air.model.Operations.Halt(header,io.github.gustavo2358.air.model.Operations.HaltKind.ABNORMAL);
            sequences.add(new io.github.gustavo2358.air.model.Sequence(new io.github.gustavo2358.air.model.Ids.LabelId(unit.id(),String.format("orphan-%04d",i)),java.util.List.of(),control,unit.origin()));
        }
        java.util.Collections.reverse(sequences);
        var changed=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),unit.entries(),sequences,unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var publication=new io.github.gustavo2358.air.model.Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),java.util.List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var input=dir.resolve("larger-unit.air.json");Files.write(input,codec.encode(publication));
        var cfg=dir.resolve("larger-unit.cfg");var dependencies=dir.resolve("larger-unit.dependencies");var diagnostic=new ByteArrayOutputStream();
        assertEquals(0,AnalysisPipeline.run(args(input,cfg,dependencies),new PrintStream(diagnostic)),diagnostic.toString());
        var reference=new DataflowAirReader().read(input);var expected=dir.resolve("larger-unit.resident.cfg");
        new CfgJsonWriter().write(new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(reference.checked().orElseThrow(),BuildOptions.defaults()),expected);
        assertArrayEquals(Files.readAllBytes(expected),Files.readAllBytes(cfg));
        int halts=0,returns=0;
        for(var transition:new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(cfg)).get("transitions")) {
            if(transition.get("kind").asText().equals("HALT"))halts++;
            if(transition.get("kind").asText().equals("RETURN"))returns++;
        }
        assertEquals(32,halts);assertEquals(33,returns);
        var json=Files.readString(dependencies);assertTrue(json.contains("\"metrics\":{\"candidates\":1,\"sites\":1}"),json);
        assertTrue(json.contains("\"referenceName\":\"PROGA\""));assertTrue(json.contains("\"rawValue\":\"PROGA   \""));assertTrue(json.contains("\"kind\":\"VALUE_PRODUCER\""));assertTrue(json.contains("\"analysisStatus\":\"PARTIAL\""));
        var standalone=dir.resolve("larger-unit.standalone.dependencies");assertEquals(0,AnalysisDependencies.run(new String[]{input.toString(),standalone.toString()},new PrintStream(diagnostic)),diagnostic.toString());
        assertArrayEquals(Files.readAllBytes(dependencies),Files.readAllBytes(standalone));
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
