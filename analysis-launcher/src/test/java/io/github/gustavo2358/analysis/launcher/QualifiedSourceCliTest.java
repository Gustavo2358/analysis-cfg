package io.github.gustavo2358.analysis.launcher;

import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QualifiedSourceCliTest {
    @TempDir Path dir;
    @Test void removedQualifiedSourceOptionIsRejectedWithoutReplacingDestination()throws Exception {
        var air=dir.resolve("air.json");var source=dir.resolve("source.json");var output=dir.resolve("dependencies.json");
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.air.json")){Files.write(air,stream.readAllBytes());}
        try(var stream=getClass().getResourceAsStream("/qualified-source-r9/conditional.source.json")){Files.write(source,stream.readAllBytes());}
        Files.writeString(output,"sentinel");var errors=new ByteArrayOutputStream();
        assertEquals(2,AnalysisDependencies.run(new String[]{air.toString(),output.toString(),"--source-evidence",source.toString()},new PrintStream(errors)));
        assertEquals("sentinel",Files.readString(output));assertTrue(errors.toString().contains("usage:"));
    }
}
