package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.*;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class DependencyCliTest {
    @TempDir Path dir;
    Path fixture(){return Path.of("../analysis-adapters/src/test/resources/cp6/dynamic-x8.air.json");}
    PrintStream err(ByteArrayOutputStream bytes){return new PrintStream(bytes);}

    @Test void sharedDeadlineExpiringAfterAdmissionPreservesBothCliDestinationsAndCleansStores() throws Exception {
        var output=dir.resolve("dependencies.json");var cfg=dir.resolve("cfg.json");
        for(boolean pipeline:new boolean[]{false,true}) {
            Files.writeString(output,"dependency sentinel");Files.writeString(cfg,"cfg sentinel");
            boolean[] expired={false};
            java.util.function.LongSupplier clock=()->expired[0]?5:0;
            // Physical read and validation use DECODE/VALIDATION; CFG/index use
            // their own phases. Advance only at actual productive CONTROL work.
            java.util.function.Consumer<io.github.gustavo2358.analysis.solver.AnalysisResources.Phase> progress=
                phase->{if(phase==io.github.gustavo2358.analysis.solver.AnalysisResources.Phase.CONTROL)expired[0]=true;};
            var constructor=io.github.gustavo2358.analysis.solver.AnalysisResources.class.getDeclaredConstructor(
                io.github.gustavo2358.analysis.solver.AnalysisResources.Limits.class,long.class,java.util.function.LongSupplier.class,java.util.function.Consumer.class);
            constructor.setAccessible(true);
            var resources=constructor.newInstance(new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(
                64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE),5L,clock,progress);
            var diagnostics=new ByteArrayOutputStream();
            int status=pipeline?AnalysisPipeline.runSnapshot(new String[]{fixture().toString(),cfg.toString(),output.toString()},err(diagnostics),new DataflowAirReader(),resources)
                :AnalysisDependencies.run(new String[]{fixture().toString(),output.toString()},err(diagnostics),new DataflowAirReader(),resources);
            assertTrue(expired[0],"clock must expire in actual native analysis, not decode");
            assertEquals(7,status,diagnostics.toString());assertTrue(diagnostics.toString().contains("TIME phase=CONTROL"),diagnostics.toString());
            assertEquals("dependency sentinel",Files.readString(output));assertEquals("cfg sentinel",Files.readString(cfg));
            for(var pool:io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
            try(var files=Files.list(dir)){assertEquals(2,files.count(),"no staged output survives failed execution");}
        }
    }

    @Test void realAirFileUsesValidatedSnapshotChainAndPublishesEvidenceAtomically() throws Exception {
        var output=dir.resolve("dependencies.json");Files.writeString(output,"old bytes");var diagnostics=new ByteArrayOutputStream();
        assertEquals(0,AnalysisDependencies.run(new String[]{fixture().toString(),output.toString()},err(diagnostics)),diagnostics.toString());
        var json=Files.readString(output);var publication=new AirJson().decode(Files.readAllBytes(fixture()));var unit=publication.units().getFirst();var assign=(Operations.Assign)unit.sequences().getFirst().instructions().getFirst();var invoke=(Operations.Invoke)unit.sequences().getFirst().terminator();
        assertTrue(json.contains("\"version\":\"3.0.0\""));assertTrue(json.contains("\"analysisStatus\":\"PARTIAL\""));assertTrue(json.contains("\"referenceName\":\"PROGA\""));assertTrue(json.contains("\"rawValue\":\"PROGA   \""));
        assertTrue(json.contains(assign.header().id().localId()));assertTrue(json.contains(assign.header().origin().localId()));assertTrue(json.contains(invoke.header().origin().localId()));assertTrue(json.contains("\"coverage\":\"ABSTRACTED\""));assertTrue(json.contains("\"premises\":[]"));
        try(var files=Files.list(dir)){assertEquals(1,files.count());}
    }

    @Test void malformedIncompleteAndOutputFailuresNeverReplaceDestination() throws Exception {
        Path input=dir.resolve("input.air.json"),output=dir.resolve("dependencies.json");Files.writeString(output,"sentinel");var diagnostics=new ByteArrayOutputStream();
        Files.writeString(input,"{");assertEquals(3,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},err(diagnostics)));assertEquals("sentinel",Files.readString(output));
        assertEquals(7,AnalysisDependencies.run(new String[]{Path.of("../analysis-adapters/src/test/resources/ep/unproved-codec.air.json").toString(),output.toString()},err(diagnostics)));assertEquals("sentinel",Files.readString(output));
        Files.copy(fixture(),input,StandardCopyOption.REPLACE_EXISTING);assertEquals(6,AnalysisDependencies.run(new String[]{input.toString(),dir.resolve("missing/output.json").toString()},err(diagnostics)));
        try(var files=Files.list(dir)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".dependencies-")));}
    }

    @Test void codecBudgetFailureAndRemovedLegacyOptionsAreExplicit() throws Exception {
        var output=dir.resolve("dependencies.json");Files.writeString(output,"sentinel");var diagnostics=new ByteArrayOutputStream();
        var codec=new AirJson(new AirJson.Limits(1,128),io.github.gustavo2358.air.validation.ValidationOptions.defaults());
        assertEquals(7,AnalysisDependencies.run(new String[]{fixture().toString(),output.toString()},err(diagnostics),new DataflowAirReader(codec)));assertEquals("sentinel",Files.readString(output));
        assertEquals(2,AnalysisDependencies.run(new String[]{fixture().toString(),output.toString(),"--experimental-physical"},err(diagnostics)));assertEquals("sentinel",Files.readString(output));
    }

    @Test void invocationContradictionsAreRejectedBeforeEitherCliPublishes() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        Path input=dir.resolve("contradiction.air.json"),cfg=dir.resolve("cfg.json"),output=dir.resolve("dependencies.json");
        for(int mutation=0;mutation<4;mutation++) {
            var document=mapper.readTree(Files.readAllBytes(fixture()));
            var call=document.path("publication").path("units").get(0).path("sequences").get(0).path("terminator");
            var known=(com.fasterxml.jackson.databind.node.ArrayNode)call.path("outcomes").path("known");
            if(mutation==0)known.add(known.get(0).deepCopy());
            else if(mutation==1||mutation==2) {
                var alternative=mapper.createObjectNode();alternative.put("kind",mutation==1?"exception":"any_exception");
                if(mutation==1)alternative.put("tag","synthetic-tag");
                alternative.set("destination",mapper.createObjectNode().put("kind","propagate"));
                known.add(alternative);known.add(alternative.deepCopy());
            } else {
                var parameter=mapper.createObjectNode();parameter.put("position","0");
                parameter.set("mode",mapper.createObjectNode().put("kind","known").put("mode","VALUE"));
                var type=mapper.createObjectNode().put("kind","known");type.set("type",mapper.createObjectNode().put("kind","text"));parameter.set("typeRef",type);
                parameter.set("objectBinding",mapper.createObjectNode().put("kind","external"));
                parameter.set("origin",call.path("header").path("origin").deepCopy());
                ((com.fasterxml.jackson.databind.node.ArrayNode)call.path("signature").path("signature").path("parameters").path("known")).add(parameter);
            }
            Files.write(input,mapper.writeValueAsBytes(document));
            // Independent resident validation proves the malformed obligation, not a codec shape failure.
            var resident=assertThrows(AirJsonException.class,()->new AirJson().decode(Files.readAllBytes(input)));
            String rule=mutation==3?"I-08":"I-60";
            assertEquals(AirJsonException.Code.INVALID_IR,resident.code());
            assertTrue(resident.issues().stream().anyMatch(issue->issue.rule().equals(rule)),resident.toString());
            Files.writeString(cfg,"cfg sentinel");Files.writeString(output,"dependency sentinel");
            var diagnostics=new ByteArrayOutputStream();
            assertEquals(3,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},err(diagnostics)),diagnostics.toString());
            assertTrue(diagnostics.toString().contains("INPUT_VALIDATION: INVALID_IR"),diagnostics.toString());
            assertEquals("dependency sentinel",Files.readString(output));
            diagnostics.reset();
            assertEquals(3,AnalysisPipeline.run(new String[]{input.toString(),cfg.toString(),output.toString()},err(diagnostics)),diagnostics.toString());
            assertTrue(diagnostics.toString().contains("PIPELINE_INPUT_INVALID"),diagnostics.toString());
            assertEquals("cfg sentinel",Files.readString(cfg));assertEquals("dependency sentinel",Files.readString(output));
        }
        try(var files=Files.list(dir)){assertEquals(3,files.count());}
    }
}
