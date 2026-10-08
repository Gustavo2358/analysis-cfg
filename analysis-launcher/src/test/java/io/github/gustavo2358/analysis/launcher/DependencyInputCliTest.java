package io.github.gustavo2358.analysis.launcher;

import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DependencyInputCliTest {
    @TempDir Path directory;
    @Test void removedResidentBundleRouteFailsClosedAndPreservesDestination()throws Exception {
        var input=Path.of(getClass().getResource("/dependency-input-v1/dependency-input.json").toURI());
        var output=directory.resolve("result.json");Files.writeString(output,"keep");
        var errors=new ByteArrayOutputStream();
        assertEquals(3,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},new PrintStream(errors)));
        assertEquals("keep",Files.readString(output));
        assertTrue(errors.toString().contains("DEPENDENCY_INPUT_INVALID")||errors.toString().contains("INPUT_CODEC"));
    }
}
