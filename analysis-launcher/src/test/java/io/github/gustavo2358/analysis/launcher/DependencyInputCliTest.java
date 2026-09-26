package io.github.gustavo2358.analysis.launcher;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DependencyInputCliTest {
    @TempDir Path directory;
    private final ObjectMapper mapper=new ObjectMapper();
    private Path fixture()throws Exception {
        var source=Path.of(Objects.requireNonNull(getClass().getResource("/dependency-input-v1/dependency-input.json")).toURI());
        try(var files=Files.list(source.getParent())){for(var file:files.toList())Files.copy(file,directory.resolve(file.getFileName()));}
        return directory.resolve("dependency-input.json");
    }
    private int run(Path input,Path output){return AnalysisDependencies.run(new String[]{input.toString(),output.toString()},new PrintStream(new ByteArrayOutputStream()));}
    @Test void normalEntryPointUsesSourceAndExecutableEvidenceWithoutFlags()throws Exception {
        var input=fixture();var output=directory.resolve("result.json");assertEquals(0,run(input,output));var bytes=Files.readAllBytes(output);var result=mapper.readTree(bytes);
        var programs=result.path("dependencies").path("programs");assertEquals(1,programs.size());var occurrence=programs.get(0);
        assertEquals("PROGA",occurrence.path("candidates").get(0).path("referenceName").asText());
        assertEquals(Set.of("EXECUTABLE_FLOW","SOURCE_QUALIFIED"),mapper.convertValue(occurrence.path("authorities"),Set.class));
        assertEquals(1,result.path("metrics").path("possibleValuesRuns").asInt());
        assertEquals(1,result.path("metrics").path("sourceQualifiedResolvedByExistingQuery").asInt());
        assertEquals(0,run(input,output));assertArrayEquals(bytes,Files.readAllBytes(output));
    }
    @Test void corruptBundleCannotReplaceDestination()throws Exception {
        var input=fixture();var original=Files.readAllBytes(input);var output=directory.resolve("result.json");var sentinel="keep".getBytes();
        for(var mutation:List.of("airDigest","sourceDigest","sourceSnapshot","operation","duplicateLink","foreignField","version","duplicateKey","trailing")) {
            Files.write(output,sentinel);var root=(ObjectNode)mapper.readTree(original);
            switch(mutation) {
                case "airDigest" -> ((ObjectNode)root.path("air")).put("sha256","0".repeat(64));
                case "sourceDigest" -> ((ObjectNode)root.path("qualifiedSource")).put("sha256","0".repeat(64));
                case "sourceSnapshot" -> root.put("sourceSha256","0".repeat(64));
                case "operation" -> ((ObjectNode)root.path("correlations").get(0).path("operation")).put("localId","missing");
                case "duplicateLink" -> ((ArrayNode)root.path("correlations")).add(root.path("correlations").get(0).deepCopy());
                case "foreignField" -> root.put("inferAdjacentEvidence",true);
                case "version" -> root.put("version","2.0.0");
                default -> { }
            }
            var json=mapper.writeValueAsString(root);
            if(mutation.equals("duplicateKey"))json=json.replaceFirst("\\{","{\"version\":\"1.0.0\",");
            if(mutation.equals("trailing"))json+=" {}";
            Files.writeString(input,json);assertEquals(3,run(input,output),mutation);assertArrayEquals(sentinel,Files.readAllBytes(output),mutation);
        }
    }
}
