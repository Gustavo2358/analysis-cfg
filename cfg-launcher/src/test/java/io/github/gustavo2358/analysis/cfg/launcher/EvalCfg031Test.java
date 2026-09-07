package io.github.gustavo2358.analysis.cfg.launcher;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.analysis.cfg.adapters.AirJsonFileReader;
import io.github.gustavo2358.analysis.cfg.adapters.CfgJsonWriter;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** EVAL-CFG-031: static upstream AIR bytes and a handwritten CFG oracle; no AIR encoding in setup. */
class EvalCfg031Test {
    @TempDir Path temporary;
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8);
    private Path input() throws Exception {
        Path input = temporary.resolve("air.json");
        try (var stream = getClass().getResourceAsStream("/air/goback.canonical.json")) {
            Files.write(input, stream.readAllBytes());
        }
        return input;
    }
    private Path output() { return temporary.resolve("cfg.json"); }
    private int run(Path input) { return AnalysisCfg.run(new String[]{input.toString(), output().toString()}, err); }
    private String diagnostic() { return errors.toString(StandardCharsets.UTF_8); }
    private void failsWithoutPublishing(Path input, String expected) throws Exception {
        assertEquals(3, run(input));
        assertFalse(Files.exists(output()));
        assertTrue(diagnostic().contains(expected), diagnostic());
        assertFalse(diagnostic().contains("\tat "));
        Files.writeString(output(), "prior artifact");
        assertEquals(3, run(input));
        assertEquals("prior artifact", Files.readString(output()));
    }
    @Test void fileThroughRealCliMatchesManualGolden() throws Exception {
        assertEquals(0, run(input()));
        try (var stream = getClass().getResourceAsStream("/cfg/goback.manual.json")) {
            assertArrayEquals(stream.readAllBytes(), Files.readAllBytes(output()));
        }
        assertEquals("", diagnostic());
    }
    @Test void twoIndependentExecutionsProduceIdenticalBytes() throws Exception {
        Path source = input();assertEquals(0, run(source));
        byte[] first = Files.readAllBytes(output());
        Files.delete(output());assertEquals(0, run(source));
        assertArrayEquals(first, Files.readAllBytes(output()));
    }
    @Test void missingAirFileCannotPublish() throws Exception {
        failsWithoutPublishing(temporary.resolve("missing.json"), "INPUT_IO");
    }
    @Test void bomIsTypedAirFailureWithoutOutput() throws Exception {
        Path source = input();Files.writeString(source, "\ufeff" + Files.readString(source));
        failsWithoutPublishing(source, "INPUT_ERROR");
    }
    @Test void malformedUtf8IsTypedAirFailureWithoutOutput() throws Exception {
        Path source = input();Files.write(source, new byte[]{(byte)0xc3, 0x28});
        failsWithoutPublishing(source, "INPUT_ERROR");
    }
    @Test void wrongBindingVersionPreservesCodecPath() throws Exception {
        Path source = input();Files.writeString(source, Files.readString(source).replace("\"bindingVersion\":\"1.0.0\"", "\"bindingVersion\":\"9.0.0\""));
        failsWithoutPublishing(source, "VERSION_MISMATCH");
        assertTrue(diagnostic().contains("$.bindingVersion"), diagnostic());
    }
    @Test void unsupportedCodecFormNeverBecomesCfgUnsupportedInput() throws Exception {
        Path source = input();
        // NORMAL Halt is valid AIR, outside the pinned codec's Return-only transport coverage.
        Files.writeString(source, Files.readString(source).replace("\"kind\":\"return\",\"values\":[]", "\"kind\":\"halt\",\"haltKind\":\"NORMAL\""));
        var codec = assertThrows(AirJsonException.class, () -> new AirJson().decode(Files.readAllBytes(source)));
        assertEquals(AirJsonException.Code.IMPLEMENTATION_LIMIT, codec.code());
        failsWithoutPublishing(source, "IMPLEMENTATION_LIMIT");
        assertTrue(diagnostic().contains(codec.path()), diagnostic());
        assertFalse(diagnostic().contains("UNSUPPORTED_INPUT"));
    }
    @Test void unavailableInventoryReachesRealKernelAndIsRejected() throws Exception {
        Path source = input();
        Files.writeString(source, Files.readString(source).replace("\"inventory\":\"PARTIAL\"", "\"inventory\":\"UNAVAILABLE\""));
        assertNotNull(new AirJsonFileReader().read(source));
        assertEquals(4, run(source));
        assertFalse(Files.exists(output()));
        assertTrue(diagnostic().contains("UNSUPPORTED_INPUT"), diagnostic());
        assertTrue(diagnostic().contains("INCOMPLETE_INVENTORY"), diagnostic());
        Files.writeString(output(), "prior artifact");
        assertEquals(4, run(source));
        assertEquals("prior artifact", Files.readString(output()));
    }
    @Test void allNonBuiltStatusesReturnFourWithoutOutput() throws Exception {
        Path source = input();
        var real = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        for (var status : CfgBuildResult.Status.values()) {
            if (status == CfgBuildResult.Status.CFG_BUILT) continue;
            int code = AnalysisCfg.run(new String[]{source.toString(), output().toString()}, err,
                    new AirJsonFileReader(), (p, o) -> {
                        var accepted = real.build(p, o);
                        return new CfgBuildResult(status, p.id(), p.airVersion(), o, accepted.preflight(),
                                accepted.unsupportedCapabilities(), accepted.projectionIssues(), Optional.empty());
                    }, new CfgJsonWriter());
            assertEquals(4, code, status.toString());
            assertFalse(Files.exists(output()));
            assertTrue(diagnostic().contains(status.toString()), diagnostic());
        }
    }
    @Test void outputFilesystemFailureIsSixAndCleansTemp() throws Exception {
        Path source = input();
        Files.createDirectory(output());Files.writeString(output().resolve("keep"), "sentinel");
        assertEquals(6, run(source));
        assertEquals("sentinel", Files.readString(output().resolve("keep")));
        assertTrue(diagnostic().contains("OUTPUT_IO"), diagnostic());
        try (var files = Files.list(temporary)) {
            assertEquals(2, files.count());
        }
    }
    @Test void missingOutputParentIsSix() throws Exception {
        Path source = input();
        assertEquals(6, AnalysisCfg.run(new String[]{source.toString(), temporary.resolve("absent/cfg.json").toString()}, err));
        assertFalse(Files.exists(temporary.resolve("absent")));
    }
    @Test void serializationFailureIsFiveAndPreservesDestination() throws Exception {
        Path source = input();Files.writeString(output(), "sentinel");
        assertEquals(5, AnalysisCfg.run(new String[]{source.toString(), output().toString()}, err,
                new AirJsonFileReader(), new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()), new CfgJsonWriter(10)));
        assertEquals("sentinel", Files.readString(output()));
        assertTrue(diagnostic().contains("OUTPUT_SERIALIZATION"), diagnostic());
    }
    @Test void usageNeverCallsBuild() {
        for (String[] args : new String[][]{{}, {"input"}, {"a", "b", "c"}, {"", "out"}, {"in", "\u0000"}}) {
            assertEquals(2, AnalysisCfg.run(args, err, new AirJsonFileReader(),
                    (p, o) -> { fail("usage must not build"); return null; }, new CfgJsonWriter()));
        }
        assertTrue(diagnostic().contains("usage: analysis-cfg <air.json> <cfg.json>"));
        assertFalse(Files.exists(output()));
    }
    @Test void unexpectedBuildBugPropagatesUnchanged() throws Exception {
        Path source = input();var bug = new IllegalStateException("test bug");
        assertSame(bug, assertThrows(IllegalStateException.class, () -> AnalysisCfg.run(
                new String[]{source.toString(), output().toString()}, err, new AirJsonFileReader(),
                (p, o) -> { throw bug; }, new CfgJsonWriter())));
    }
    @Test void mainProcessUsesRealExitCodesAndFilePipeline() throws Exception {
        Path source = input();
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        Path log = temporary.resolve("process.log");
        var process = new ProcessBuilder(javaExecutable, "-cp", classpath, AnalysisCfg.class.getName(),
                source.toString(), output().toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), Files.readString(log));
        try (var golden = getClass().getResourceAsStream("/cfg/goback.manual.json")) {
            assertArrayEquals(golden.readAllBytes(), Files.readAllBytes(output()));
        }
        assertEquals("", Files.readString(log));
        var invalid = new ProcessBuilder(javaExecutable, "-cp", classpath, AnalysisCfg.class.getName(),
                temporary.resolve("missing").toString(), output().toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(invalid.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(3, invalid.exitValue());
        assertTrue(Files.readString(log).contains("INPUT_IO"));
    }

    @Test void oversizedPhysicalInputReturnsThreeBeforeCodec() throws Exception {
        Path source = temporary.resolve("oversized.json");
        try (var file = new java.io.RandomAccessFile(source.toFile(), "rw")) {
            file.setLength((long) AirJson.Limits.defaults().maximumDocumentBytes() + 1);
        }
        failsWithoutPublishing(source, "IMPLEMENTATION_LIMIT");
        assertTrue(diagnostic().contains("maximumDocumentBytes="));
    }

}
