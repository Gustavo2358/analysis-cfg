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
}
