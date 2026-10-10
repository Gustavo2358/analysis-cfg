package io.github.gustavo2358.analysis.launcher;

import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DependencyInputCliTest {
    @TempDir Path directory;
    @Test void nativeBundleAdmitsExactSnapshotsAndRejectsContradictionsAtomically()throws Exception {
        var fixture=Path.of(getClass().getResource("/dependency-input-v1/dependency-input.json").toURI());
        try(var files=Files.list(fixture.getParent())) {
            for(var file:files.filter(p->p.toString().endsWith(".json")).toList())Files.copy(file,directory.resolve(file.getFileName()));
        }
        var input=directory.resolve("dependency-input.json");
        var output=directory.resolve("result.json");Files.writeString(output,"keep");
        var reader=new io.github.gustavo2358.analysis.adapters.DataflowAirReader();
        var errors=new ByteArrayOutputStream();var owner=resources();
        assertEquals(0,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},new PrintStream(errors),reader,owner),errors.toString());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var result=mapper.readTree(Files.readAllBytes(output));
        assertEquals("3.0.0",result.path("version").asText());
        assertEquals("PROGA",result.path("sites").get(0).path("candidates").get(0).path("referenceName").asText());
        assertEquals("PROGA   ",result.path("sites").get(0).path("candidates").get(0).path("rawValue").asText());
        var occurrence=result.path("dependencies").path("programs").get(0);
        assertEquals(2,occurrence.path("authorities").size());
        assertTrue(occurrence.path("authorities").toString().contains("EXECUTABLE_FLOW"));
        assertTrue(occurrence.path("authorities").toString().contains("SOURCE_QUALIFIED"));
        assertTrue(result.has("sourceQualifiedDependencies"));released(owner);
        var original=mapper.readTree(Files.readAllBytes(input));
        for(int mutation=0;mutation<9;mutation++) {
            var changed=(com.fasterxml.jackson.databind.node.ObjectNode)original.deepCopy();
            switch(mutation) {
                case 0 -> ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("air")).put("sha256","wrong");
                case 1 -> ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("qualifiedSource")).put("sha256","wrong");
                case 2 -> changed.put("sourceSha256","wrong");
                case 3 -> changed.put("version","1.1.0");
                case 4 -> changed.put("unknown",true);
                case 5 -> ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("correlations").get(0).path("operation")).put("localId","absent");
                case 6 -> ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("correlations").get(0).path("label")).put("localId","absent");
                case 7 -> ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("correlations").get(0).path("origin")).put("localId","absent");
                case 8 -> ((com.fasterxml.jackson.databind.node.ArrayNode)changed.path("correlations")).add(changed.path("correlations").get(0).deepCopy());
            }
            Files.write(input,mapper.writeValueAsBytes(changed));Files.writeString(output,"keep");
            errors.reset();owner=resources();
            assertEquals(3,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},new PrintStream(errors),reader,owner),"mutation="+mutation+" "+errors);
            assertEquals("keep",Files.readString(output));released(owner);
        }
        Files.write(input,mapper.writeValueAsBytes(original));
        owner=io.github.gustavo2358.analysis.solver.AnalysisResources.withDeadline(
            new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(0,0,0,0,8,Long.MAX_VALUE,Long.MAX_VALUE),java.time.Duration.ofMinutes(8));
        errors.reset();assertEquals(7,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},new PrintStream(errors),reader,owner),errors.toString());
        assertEquals("keep",Files.readString(output));released(owner);
        assertTrue(errors.toString().contains("HEAP phase=DECODE"),errors.toString());
    }
    private static io.github.gustavo2358.analysis.solver.AnalysisResources resources() {
        return io.github.gustavo2358.analysis.solver.AnalysisResources.withDeadline(
            new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,16L*1024*1024*1024,8,Long.MAX_VALUE,4L*1024*1024*1024),java.time.Duration.ofMinutes(8));
    }
    private static void released(io.github.gustavo2358.analysis.solver.AnalysisResources resources) {
        for(var pool:io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
    }
}
