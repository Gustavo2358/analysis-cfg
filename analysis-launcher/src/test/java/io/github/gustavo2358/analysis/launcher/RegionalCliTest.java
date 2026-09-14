package io.github.gustavo2358.analysis.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionalCliTest {
    @TempDir Path directory;
    @Test void missingInputAndBadQueryNeverReplaceOutput() throws Exception {
        var destination=directory.resolve("result.json");Files.writeString(destination,"previous");var diagnostics=new ByteArrayOutputStream();
        assertEquals(2,RegionalAnalysis.run(new String[]{},new PrintStream(diagnostics)));
        var args=new String[]{directory.resolve("missing.air.json").toString(),destination.toString(),"--result-id","fixture","--unit","unit","--entry","entry","--before","return-body","--range","copy-region","0","8","ascii"};
        assertEquals(3,RegionalAnalysis.run(args,new PrintStream(diagnostics)));assertEquals("previous",Files.readString(destination));
        args[12]="-1";assertEquals(2,RegionalAnalysis.run(args,new PrintStream(diagnostics)));assertEquals("previous",Files.readString(destination));
    }
    @Test void realRegionalFileHasStableOutputAndExplicitUnsupportedQuery() throws Exception {
        // The adapter contract test writes the AIR fixture during the same reactor test run.
        var source=Path.of("../analysis-adapters/target/regional-wire/manual.air.json").toAbsolutePath();assertTrue(Files.exists(source));
        var result=directory.resolve("result.json");var err=new ByteArrayOutputStream();
        var args=new String[]{source.toString(),result.toString(),"--result-id","cli-regional","--unit","unit","--entry","entry","--before","return-body","--range","copy-region","0","8","ascii"};
        assertEquals(0,RegionalAnalysis.run(args,new PrintStream(err)),err.toString());var bytes=Files.readAllBytes(result);assertTrue(new String(bytes,java.nio.charset.StandardCharsets.UTF_8).contains("WXYZEFGH"));
        assertEquals(0,RegionalAnalysis.run(args,new PrintStream(err)),err.toString());assertArrayEquals(bytes,Files.readAllBytes(result));
        args[9]="absent";assertEquals(0,RegionalAnalysis.run(args,new PrintStream(err)),err.toString());assertTrue(Files.readString(result).contains("UNKNOWN_OPERATION"));
        var published=Path.of("target/regional-cli");Files.createDirectories(published);Files.write(published.resolve("known.result.json"),bytes);Files.copy(result,published.resolve("unsupported.result.json"),StandardCopyOption.REPLACE_EXISTING);
    }
    @Test void malformedAirAndOutputFailurePreserveExistingContentAndCleanTemporary() throws Exception {
        var source=Path.of("../analysis-adapters/target/regional-wire/manual.air.json").toAbsolutePath();var output=directory.resolve("occupied");Files.createDirectory(output);Files.writeString(output.resolve("sentinel"),"previous");
        var args=new String[]{source.toString(),output.toString(),"--result-id","fixture","--unit","unit","--entry","entry","--before","return-body","--object","dest"};
        var err=new ByteArrayOutputStream();assertEquals(6,RegionalAnalysis.run(args,new PrintStream(err)));assertEquals("previous",Files.readString(output.resolve("sentinel")));
        try(var files=Files.list(directory)){assertTrue(files.noneMatch(p->p.getFileName().toString().startsWith(".regional-result-")));}
        var malformed=directory.resolve("malformed.json");Files.writeString(malformed,"{");args[0]=malformed.toString();assertEquals(3,RegionalAnalysis.run(args,new PrintStream(err)));assertEquals("previous",Files.readString(output.resolve("sentinel")));
    }
}
