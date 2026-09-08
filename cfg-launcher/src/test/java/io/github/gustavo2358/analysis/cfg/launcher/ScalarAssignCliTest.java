package io.github.gustavo2358.analysis.cfg.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Two independent real CLI processes; the manual golden includes CFG_BUILT and PARTIAL. */
class ScalarAssignCliTest {
    @TempDir Path temporary;

    @Test void twoRealCliProcessesMatchScalarManualGoldenByteForByte() throws Exception {
        Path input = Path.of(getClass().getResource("/air/scalar-assign.canonical.json").toURI());
        byte[] expected;
        try (var stream = getClass().getResourceAsStream("/cfg/scalar-assign.manual.json")) {
            expected = stream.readAllBytes();
        }
        byte[] previous = null;
        for (int run = 0; run < 2; run++) {
            Path output = temporary.resolve("scalar-" + run + ".cfg.json");
            Path log = temporary.resolve("process-" + run + ".log");
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), AnalysisCfg.class.getName(),
                    input.toString(), output.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(log));
            assertEquals("", Files.readString(log));
            byte[] actual = Files.readAllBytes(output);
            assertArrayEquals(expected, actual);
            if (previous != null) assertArrayEquals(previous, actual);
            previous = actual;
        }
        try (var files = Files.list(temporary)) { assertEquals(4, files.count()); }
    }
}
