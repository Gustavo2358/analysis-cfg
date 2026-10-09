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
    /** Differential reference keeps its historical wrapper coverage; native facts retain the actual AIR header. */
    static void assertCompleteDependencySections(io.github.gustavo2358.air.model.Publication publication,
            DependencyResult expected,Path output) throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var bytes=new ByteArrayOutputStream();
        new DependencyJson().write(expected,bytes);
        var resident=mapper.readTree(bytes.toByteArray());var actual=mapper.readTree(Files.readAllBytes(output));
        for(var site:resident.path("sites")) {
            var id=site.path("operation");
            var header=publication.units().stream().flatMap(u->u.sequences().stream())
                .flatMap(s->java.util.stream.Stream.<io.github.gustavo2358.air.model.Operation>concat(s.instructions().stream(),java.util.stream.Stream.of(s.terminator())))
                .map(io.github.gustavo2358.air.model.Operation::header)
                .filter(h->h.id().localId().equals(id.path("localId").asText())
                    &&h.id().unit().localId().equals(id.path("unit").asText())
                    &&h.id().publication().localId().equals(id.path("publication").asText())).findFirst().orElseThrow();
            ((com.fasterxml.jackson.databind.node.ObjectNode)site).put("coverage",header.coverage().name());
        }
        for(String field:java.util.List.of("sites","edges","origins","artifacts","sourceUncertaintyRefs",
                "fileDependencies","sourceDependencies","sourceQualifiedDependencies","dependencies"))
            assertEquals(resident.get(field),actual.get(field),"complete dependency section: "+field);
        assertEquals("VALIDATED_SNAPSHOT_DEPENDENCY",actual.path("modelScope").asText());
        assertEquals("3.0.0",actual.path("version").asText());
    }
    @TempDir Path dir;
    Path resource(String path){var root=Files.isDirectory(Path.of("analysis-adapters"))?Path.of("."):Path.of("..");return root.resolve("analysis-adapters/src/test/resources").resolve(path);}
    Path fixture(){return resource("cp6/dynamic-x8.air.json");}
    PrintStream errors(){return new PrintStream(new ByteArrayOutputStream());}
    String[] args(Path input,Path cfg,Path dependencies){return new String[]{input.toString(),cfg.toString(),dependencies.toString()};}
    @Test void exhaustedCommonBudgetStopsBeforeSourceParsingAndPreservesProducts() throws Exception {
        var limits=new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(
                64_000_000,64_000_000,0,256_000_000,4,Long.MAX_VALUE,64_000_000);
        var measured=new io.github.gustavo2358.analysis.solver.AnalysisResources(limits);
        try(var admitted=new DataflowAirReader().readSnapshot(fixture(),measured)) {
            assertTrue(admitted.checked().result().isStructurallyValid());
        }
        var budget=new io.github.gustavo2358.analysis.solver.AnalysisResources(
                new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(
                        limits.heapBytes(),limits.scratchBytes(),limits.directBytes(),limits.temporaryBytes(),
                        limits.openFiles(),measured.workUsed(),limits.outputBytes()));
        var source=dir.resolve("unfinished.source.json");Files.writeString(source,"{");
        var cfg=dir.resolve("budget.cfg");var dependencies=dir.resolve("budget.dependencies");
        Files.writeString(cfg,"cfg-sentinel");Files.writeString(dependencies,"dependencies-sentinel");
        var errors=new ByteArrayOutputStream();
        assertEquals(7,AnalysisPipeline.runSnapshot(new String[]{fixture().toString(),cfg.toString(),dependencies.toString(),
                "--source-evidence",source.toString()},new PrintStream(errors),new DataflowAirReader(),budget),errors.toString());
        assertTrue(errors.toString().contains("WORK phase=DECODE"),errors.toString());
        assertEquals("cfg-sentinel",Files.readString(cfg));assertEquals("dependencies-sentinel",Files.readString(dependencies));
        assertEquals(0,budget.heapUsed());assertEquals(0,budget.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,budget.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.OPEN_FILES));
    }
    @Test void exhaustedCommonBudgetStopsSourceValidationAndPreservesProducts() throws Exception {
        var input=dir.resolve("validation.air.json");var source=dir.resolve("validation.source.json");
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.air.json")){Files.write(input,stream.readAllBytes());}
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.source.json")){Files.write(source,stream.readAllBytes());}
        var limits=new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(
            64_000_000,64_000_000,0,256_000_000,4,Long.MAX_VALUE,64_000_000);
        var measured=new io.github.gustavo2358.analysis.solver.AnalysisResources(limits);
        try(var admitted=new DataflowAirReader().readSnapshot(input,measured)) {
            assertTrue(admitted.checked().result().isStructurallyValid());
        }
        long[] decoded={0};var boundary=new IllegalStateException("source validation boundary");
        try(var stream=JsonFiles.input(source)) {
            assertSame(boundary,assertThrows(IllegalStateException.class,
                ()->new QualifiedSourceJson().decode(stream,()->decoded[0]++,()->{throw boundary;})));
        }
        var budget=new io.github.gustavo2358.analysis.solver.AnalysisResources(
            new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(
                limits.heapBytes(),limits.scratchBytes(),limits.directBytes(),limits.temporaryBytes(),
                limits.openFiles(),Math.addExact(measured.workUsed(),Math.addExact(1,decoded[0])),limits.outputBytes()));
        var cfg=dir.resolve("validation.cfg");var dependencies=dir.resolve("validation.dependencies");
        Files.writeString(cfg,"cfg-sentinel");Files.writeString(dependencies,"dependencies-sentinel");
        var errors=new ByteArrayOutputStream();
        assertEquals(7,AnalysisPipeline.runSnapshot(new String[]{input.toString(),cfg.toString(),dependencies.toString(),
            "--source-evidence",source.toString()},new PrintStream(errors),new DataflowAirReader(),budget),errors.toString());
        assertTrue(errors.toString().contains("WORK phase=VALIDATION"),errors.toString());
        assertEquals("cfg-sentinel",Files.readString(cfg));assertEquals("dependencies-sentinel",Files.readString(dependencies));
        assertEquals(0,budget.heapUsed());assertEquals(0,budget.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,budget.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.OPEN_FILES));
    }
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
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var residentOutput=new ByteArrayOutputStream();
        new DependencyJson().write(new DependencyAnalysis().prepare(reference.publication()),residentOutput);
        var resident=mapper.readTree(residentOutput.toByteArray());var nativeResult=mapper.readTree(json);
        for(var expectedSite:resident.get("sites")) {
            var operation=expectedSite.get("operation").get("localId").asText();
            var header=reference.publication().units().stream().flatMap(u->u.sequences().stream()).map(s->s.terminator().header())
                .filter(h->h.id().localId().equals(operation)).findFirst().orElseThrow();
            ((com.fasterxml.jackson.databind.node.ObjectNode)expectedSite).put("coverage",header.coverage().name());
        }
        for(var field:java.util.List.of("sites","edges","origins","artifacts","sourceUncertaintyRefs","fileDependencies","sourceDependencies"))
            assertEquals(resident.get(field),nativeResult.get(field),"native general engine must preserve complete fact contract: "+field);
        var site=nativeResult.get("sites").get(0);
        assertEquals("BEFORE",site.get("valuePoint").get("position").asText());
        assertEquals("REACHABLE",site.get("reachability").asText());
        assertFalse(site.get("modelValueRemainder").asBoolean());
        assertTrue(site.get("sourceValueRemainder").asBoolean());
        assertTrue(site.get("interpretationUnknownRemainder").asBoolean());
        assertTrue(site.get("openControlRemainder").asBoolean());
        assertEquals(1,site.get("candidates").size());
        assertEquals("PROGA",site.get("candidates").get(0).get("referenceName").asText());
        assertEquals("PROGA   ",site.get("candidates").get(0).get("rawValue").asText());
        var assign=reference.publication().units().getFirst().sequences().stream().flatMap(s->s.instructions().stream())
            .filter(io.github.gustavo2358.air.model.Operations.Assign.class::isInstance).findFirst().orElseThrow();
        var support=site.get("candidates").get(0).get("supports").get(0);
        assertEquals(assign.header().id().localId(),support.get("producer").get("localId").asText());
        assertEquals(assign.header().origin().localId(),support.get("origin").get("localId").asText());
        var standalone=dir.resolve("snapshot-standalone.dependencies");
        assertEquals(0,AnalysisDependencies.run(new String[]{fixture().toString(),standalone.toString()},errors()));
        assertArrayEquals(Files.readAllBytes(dependencies),Files.readAllBytes(standalone));
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
        var product=new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertEquals(4,product.get("sites").size());assertEquals(4,product.get("edges").size());
        for(var site:product.get("sites")) {
            assertEquals(1,site.get("candidates").size());
            assertEquals("PROGA",site.get("candidates").get(0).get("referenceName").asText());
        }
        for(var edge:product.get("edges"))assertEquals("PROGA",edge.get("candidate").get("referenceName").asText());
        assertEquals(1,product.get("dependencies").get("programs").size());
        assertTrue(json.contains("\"rawValue\":\"PROGA   \""));assertTrue(json.contains("\"kind\":\"VALUE_PRODUCER\""));assertTrue(json.contains("\"analysisStatus\":\"PARTIAL\""));
        var separate=dir.resolve("standalone.dependencies");
        assertEquals(0,AnalysisDependencies.run(new String[]{input.toString(),separate.toString()},new PrintStream(diagnostic)),diagnostic.toString());
        assertArrayEquals(Files.readAllBytes(dependencies),Files.readAllBytes(separate));
        var different=new java.util.ArrayList<>(entries);var later=different.getLast();
        different.set(different.size()-1,new io.github.gustavo2358.air.model.Entries.Entry(later.id(),java.util.Optional.of(unit.sequences().getLast().label()),later.signature(),later.state(),later.origin()));
        var differentUnit=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),different,
                unit.sequences(),unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var distinctEntry=new io.github.gustavo2358.air.model.Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),java.util.List.of(differentUnit),
                base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        // Distinct entry labels are valid AIR; full mandatory validation now admits this shape.
        Files.write(input,codec.encode(distinctEntry));
        var residentDependencies=dir.resolve("distinct-entry.resident.dependencies");
        assertEquals(0,AnalysisPipeline.run(args(input,expected,residentDependencies),new PrintStream(diagnostic),new DataflowAirReader()::read),diagnostic.toString());
        assertEquals(0,AnalysisPipeline.run(args(input,cfg,dependencies),new PrintStream(diagnostic)),diagnostic.toString());
        sameNativeSections(Files.readAllBytes(residentDependencies),Files.readAllBytes(dependencies),distinctEntry);
        assertArrayEquals(Files.readAllBytes(expected),Files.readAllBytes(cfg));
        assertEquals(0,AnalysisDependencies.run(new String[]{input.toString(),separate.toString()},new PrintStream(diagnostic)),diagnostic.toString());
        assertArrayEquals(Files.readAllBytes(dependencies),Files.readAllBytes(separate));
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
    private AnalysisPipeline.SnapshotAirRead nativeOnly(AtomicInteger reads) {
        return (path,resources)-> {
            reads.incrementAndGet();return new DataflowAirReader().readSnapshot(path,resources);
        };
    }
    private void sameNativeSections(byte[] reference,byte[] actual,io.github.gustavo2358.air.model.Publication publication)throws IOException {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var expected=mapper.readTree(reference);var observed=mapper.readTree(actual);
        for(var site:expected.get("sites")) {
            var operation=site.get("operation").get("localId").asText();
            var header=publication.units().stream().flatMap(u->u.sequences().stream()).map(s->s.terminator().header())
                .filter(h->h.id().localId().equals(operation)).findFirst().orElseThrow();
            ((com.fasterxml.jackson.databind.node.ObjectNode)site).put("coverage",header.coverage().name());
        }
        for(var field:java.util.List.of("sites","edges","origins","artifacts","sourceUncertaintyRefs","fileDependencies","sourceDependencies","sourceQualifiedDependencies","dependencies"))
            assertEquals(expected.get(field),observed.get(field),"complete native optional-mode section: "+field);
        assertEquals("3.0.0",observed.path("version").asText());
        assertEquals("VALIDATED_SNAPSHOT_DEPENDENCY",observed.path("modelScope").asText());
    }
    @Test void explicitSourceEvidenceUsesOneCheckedSnapshotAndPreservesEverySection()throws Exception {
        var input=dir.resolve("source.air.json");var source=dir.resolve("source.json");
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.air.json")){Files.write(input,stream.readAllBytes());}
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.source.json")){Files.write(source,stream.readAllBytes());}
        var resident=new DataflowAirReader().read(input);var reads=new AtomicInteger();
        for(boolean physical:new boolean[]{false,true}) {
            var suffix=physical?"both":"source";var cfg=dir.resolve(suffix+".cfg");var dependencies=dir.resolve(suffix+".dependencies");
            var expectedCfg=dir.resolve(suffix+".resident.cfg");var expectedDependencies=dir.resolve(suffix+".resident.dependencies");
            var invocation=new java.util.ArrayList<>(java.util.List.of(input.toString(),cfg.toString(),dependencies.toString(),"--source-evidence",source.toString()));
            if(physical)invocation.add("--experimental-physical");
            var independent=new java.util.ArrayList<>(invocation);independent.set(1,expectedCfg.toString());independent.set(2,expectedDependencies.toString());
            assertEquals(0,AnalysisPipeline.run(independent.toArray(String[]::new),errors(),new DataflowAirReader()::read));
            int before=reads.get();var diagnostic=new ByteArrayOutputStream();
            assertEquals(0,AnalysisPipeline.runSnapshot(invocation.toArray(String[]::new),new PrintStream(diagnostic),nativeOnly(reads)),diagnostic.toString());
            assertEquals(before+1,reads.get());assertArrayEquals(Files.readAllBytes(expectedCfg),Files.readAllBytes(cfg));
            sameNativeSections(Files.readAllBytes(expectedDependencies),Files.readAllBytes(dependencies),resident.publication());
            var originalCfg=Files.readAllBytes(cfg);var originalDependencies=Files.readAllBytes(dependencies);var originalInput=Files.readAllBytes(input);
            Files.writeString(input,Files.readString(input)+" ");
            assertEquals(3,AnalysisPipeline.runSnapshot(invocation.toArray(String[]::new),errors(),nativeOnly(reads)));
            assertArrayEquals(originalCfg,Files.readAllBytes(cfg));assertArrayEquals(originalDependencies,Files.readAllBytes(dependencies));
            Files.write(input,originalInput);
        }
    }
    @Test void experimentalPhysicalPipelineUsesNativeValuesWithoutDroppingChoiceSupports()throws Exception {
        var expectedNames=java.util.Map.of("ambiguous",java.util.List.of("PROGA","PROGB"),"ambiguous-must",java.util.List.of("PROGC"),"ambiguous-mixed",java.util.List.of("PROGA"));
        var reads=new AtomicInteger();
        for(var name:java.util.List.of("ambiguous","ambiguous-must","ambiguous-mixed")) {
            var input=dir.resolve(name+".air.json");
            try(var stream=getClass().getResourceAsStream("/recall/"+name+".air.json")){Files.write(input,stream.readAllBytes());}
            var cfg=dir.resolve(name+".cfg");var dependencies=dir.resolve(name+".dependencies");
            var expectedCfg=dir.resolve(name+".resident.cfg");var expectedDependencies=dir.resolve(name+".resident.dependencies");
            var invocation=new String[]{input.toString(),cfg.toString(),dependencies.toString(),"--experimental-physical"};
            assertEquals(0,AnalysisPipeline.run(new String[]{input.toString(),expectedCfg.toString(),expectedDependencies.toString(),"--experimental-physical"},errors(),new DataflowAirReader()::read));
            int before=reads.get();var diagnostic=new ByteArrayOutputStream();
            assertEquals(0,AnalysisPipeline.runSnapshot(invocation,new PrintStream(diagnostic),nativeOnly(reads)),diagnostic.toString());
            assertEquals(before+1,reads.get());assertArrayEquals(Files.readAllBytes(expectedCfg),Files.readAllBytes(cfg));
            sameNativeSections(Files.readAllBytes(expectedDependencies),Files.readAllBytes(dependencies),new DataflowAirReader().read(input).publication());
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readAllBytes(dependencies));
            assertEquals(1,json.path("generalAnalysisMetrics").path("experimentalPhysicalMode").asLong());
            var site=json.path("sites").get(0);var names=new java.util.ArrayList<String>();
            for(var candidate:site.path("candidates")){names.add(candidate.path("referenceName").asText());assertFalse(candidate.path("supports").isEmpty());}
            assertEquals(expectedNames.get(name),names);assertEquals("BEFORE",site.path("valuePoint").path("position").asText());
            assertTrue(site.path("effectiveUnknownRemainder").asBoolean());
        }
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
