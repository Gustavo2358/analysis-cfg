package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.*;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class DependencyCliTest {
    @TempDir Path dir;
    byte[] input() throws IOException {return Files.readAllBytes(Path.of("../analysis-adapters/src/test/resources/cp6/dynamic-x8.air.json"));}
    PrintStream err(){return new PrintStream(new ByteArrayOutputStream());}
    @Test void successReplacesExistingFileAndMatchesMemoryExactly() throws Exception {
        var in=dir.resolve("input");var out=dir.resolve("output");Files.write(in,input());Files.writeString(out,"old bytes");
        assertEquals(0,AnalysisDependencies.run(new String[]{in.toString(),out.toString()},err()));
        var memory=new DependencyAnalysis().prepare(new AirJson().decode(input()));var bytes=new ByteArrayOutputStream();new DependencyJson().write(memory,bytes);
        assertArrayEquals(bytes.toByteArray(),Files.readAllBytes(out));try(var files=Files.list(dir)){assertEquals(2,files.count());}
    }
    @Test void inputAndOutputFailuresPreserveDestinationAndCleanupTemporaryFile() throws Exception {
        var in=dir.resolve("input");var out=dir.resolve("output");Files.writeString(out,"sentinel");Files.writeString(in,"{");
        assertEquals(2,AnalysisDependencies.run(new String[]{},err()));assertEquals(3,AnalysisDependencies.run(new String[]{in.toString(),out.toString()},err()));assertEquals("sentinel",Files.readString(out));
        assertEquals(3,AnalysisDependencies.run(new String[]{dir.resolve("absent").toString(),out.toString()},err()));
        Files.write(in,input());assertEquals(6,AnalysisDependencies.run(new String[]{in.toString(),dir.resolve("missing/out").toString()},err()));
        var occupied=dir.resolve("directory");Files.createDirectory(occupied);Files.writeString(occupied.resolve("keep"),"keep");
        assertEquals(6,AnalysisDependencies.run(new String[]{in.toString(),occupied.toString()},err()));assertEquals("keep",Files.readString(occupied.resolve("keep")));
        try(var files=Files.list(dir)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".dependencies-")));}
    }
    @Test void realCodecOperationalLimitIsSevenAndProducesNoPartialResult() throws Exception {
        var in=dir.resolve("input");var out=dir.resolve("output");Files.write(in,input());Files.writeString(out,"sentinel");
        var codec=new AirJson(new AirJson.Limits(1,128),io.github.gustavo2358.air.validation.ValidationOptions.defaults());
        assertEquals(7,AnalysisDependencies.run(new String[]{in.toString(),out.toString()},err(),new DataflowAirReader(codec)));
        assertEquals("sentinel",Files.readString(out));assertEquals(0,AnalysisDependencies.run(new String[]{in.toString(),out.toString()},err()));
    }
    @Test void cfgAndAnalysisUnsupportedHaveDifferentExitsAndNoPartialOutput() throws Exception {
        var in=dir.resolve("input");var out=dir.resolve("output");Files.writeString(out,"sentinel");
        String raw=new String(input(),java.nio.charset.StandardCharsets.UTF_8);
        String cfg=raw.replace("\"known\":[{\"kind\":\"normal\"","\"known\":[{\"kind\":\"diverge\"},{\"kind\":\"normal\"");
        assertNotEquals(raw,cfg);Files.writeString(in,cfg);assertEquals(4,AnalysisDependencies.run(new String[]{in.toString(),out.toString()},err()));assertEquals("sentinel",Files.readString(out));
        String pub=new AirJson().decode(input()).id().localId();
        String analysis=raw.replace("\"includingEnvironment\":true,\"kind\":\"all\",\"publication\":{\"domain\":\"publication\",\"localId\":\""+pub+"\"}","\"includingExternal\":true,\"kind\":\"visible\",\"unit\":{\"domain\":\"unit\",\"localId\":\"unit\",\"publication\":\""+pub+"\"}");
        assertNotEquals(raw,analysis);Files.writeString(in,analysis);assertEquals(5,AnalysisDependencies.run(new String[]{in.toString(),out.toString()},err()));assertEquals("sentinel",Files.readString(out));
    }
}
