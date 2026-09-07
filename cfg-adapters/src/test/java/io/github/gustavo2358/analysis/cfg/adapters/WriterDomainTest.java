package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.analysis.cfg.application.BuildOptions;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WriterDomainTest {
    @TempDir Path temporary;
    private CfgBuildResult build(Publication p, ProjectionPolicy policy) {
        var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,
                new BuildOptions(ValidationOptions.defaults(), policy));
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status(), result.preflight().issues().toString());
        return result;
    }
    @Test void memoryAndFileHaveEquivalentControlCoverageWire() throws Exception {
        var writer = new CfgJsonWriter();
        byte[] memory = writer.encode(build(MemoryFacts.goback(), ProjectionPolicy.KNOWN_SUBSET));
        try (var golden = getClass().getResourceAsStream("/cfg/goback.manual.json")) {
            assertArrayEquals(golden.readAllBytes(), memory);
        }
        var fromFile = new AirJsonFileReader().read(Path.of(getClass().getResource("/air/goback.canonical.json").toURI()));
        assertArrayEquals(memory, writer.encode(build(fromFile, ProjectionPolicy.KNOWN_SUBSET)));
        // Only the CFG product observations coincide: full AIR origins/precision are intentionally different.
        assertNotEquals(MemoryFacts.goback(), fromFile);
    }
    @Test void writerCoversEveryCurrentKindWithExactContextualTransitions() throws Exception {
        var result = build(MemoryFacts.mixed("P"), ProjectionPolicy.STRICT);
        String json = new String(new CfgJsonWriter().encode(result), StandardCharsets.UTF_8);
        assertEquals(11, result.graph().orElseThrow().nodes().size());
        assertTrue(json.contains("\"projectionPolicy\":\"STRICT\""));
        assertTrue(json.contains("\"publicationInventory\":\"COMPLETE\""));
        String[] nodeKinds = {"SEQUENCE", "SEQUENCE", "SEQUENCE", "SEQUENCE", "HALT_EXIT", "SEQUENCE", "HALT_EXIT", "ENTRY", "NORMAL_EXIT", "ENTRY", "NORMAL_EXIT"};
        String nodes = json.substring(json.indexOf("\"nodes\":["), json.indexOf("],\"transitions\""));
        int offset = 0;
        for (String kind : nodeKinds) {
            offset = nodes.indexOf("\"kind\":\"" + kind + "\"", offset);
            assertTrue(offset >= 0, kind); offset++;
        }
        for (String kind : List.of("JUMP", "BRANCH", "RETURN", "HALT"))
            assertTrue(nodes.contains("\"terminator\":{\"kind\":\"" + kind + "\""));
        assertTrue(nodes.contains("\"operation\":{\"publication\":\"P\",\"unit\":\"U\",\"localId\":\"halt\"},\"haltKind\":\"NORMAL\""));
        assertTrue(nodes.contains("\"operation\":{\"publication\":\"P\",\"unit\":\"U\",\"localId\":\"abnormal\"},\"haltKind\":\"ABNORMAL\""));
        // Handwritten topology: both arms to node 1; orphans and both activation contexts retained.
        String expected = "\"transitions\":[" + String.join(",",
                edge("ENTRY", 7, 0, "E"), edge("BRANCH_TRUE", 0, 1, "E"), edge("BRANCH_FALSE", 0, 1, "E"),
                edge("JUMP", 1, 2, "E"), edge("RETURN", 2, 8, "E"), edge("HALT", 3, 4, "E"), edge("HALT", 5, 6, "E"),
                edge("ENTRY", 9, 3, "F"), edge("BRANCH_TRUE", 0, 1, "F"), edge("BRANCH_FALSE", 0, 1, "F"),
                edge("JUMP", 1, 2, "F"), edge("RETURN", 2, 10, "F"), edge("HALT", 3, 4, "F"), edge("HALT", 5, 6, "F")) + "]}";
        assertEquals(expected, json.substring(json.indexOf("\"transitions\"")));
    }
    private static String edge(String kind, int from, int to, String entry) {
        return "{\"kind\":\"" + kind + "\",\"from\":{\"publication\":\"P\",\"ordinal\":\"" + from
                + "\"},\"to\":{\"publication\":\"P\",\"ordinal\":\"" + to
                + "\"},\"activationEntry\":{\"publication\":\"P\",\"unit\":\"U\",\"localId\":\"" + entry + "\"}}";
    }
    @Test void utf8EscapingAndByteLimitAreExact() throws Exception {
        var bytes = new CfgJsonBytes(100);
        bytes.string("a\"\\\n\u0000\u001fá😀");
        byte[] expected = "\"a\\\"\\\\\\u000a\\u0000\\u001fá😀\"".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, bytes.bytes());
        var exact = new CfgJsonBytes(expected.length);exact.string("a\"\\\n\u0000\u001fá😀");
        assertArrayEquals(expected, exact.bytes());
        assertThrows(CfgJsonException.class, () -> new CfgJsonBytes(expected.length - 1).string("a\"\\\n\u0000\u001fá😀"));
        String json = new String(new CfgJsonWriter().encode(build(MemoryFacts.mixed("P\"\\\ná😀"), ProjectionPolicy.KNOWN_SUBSET)), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"publication\":\"P\\\"\\\\\\u000aá😀\""));
    }
    @Test void invalidUnicodeIsRejectedByOutputPrimitiveAndAirModel() {
        for (String bad : List.of("P\ud800", "P\udc00", "P\ud800z")) {
            assertThrows(CfgJsonException.class, () -> new CfgJsonBytes(100).string(bad));
            assertThrows(IllegalArgumentException.class, () -> MemoryFacts.mixed(bad));
        }
    }
    @Test void nonAtomicFallbackIsExplicitAndMovesCompleteBytes() throws Exception {
        Path destination = temporary.resolve("out.json");Files.writeString(destination, "old");
        byte[] bytes = "complete bytes".getBytes(StandardCharsets.UTF_8);
        var calls = new AtomicInteger();
        CfgJsonWriter.publish(bytes, destination, (from, to, options) -> {
            assertEquals(destination.getParent(), from.getParent());
            assertArrayEquals(bytes, Files.readAllBytes(from));
            assertEquals("old", Files.readString(to));
            if (calls.getAndIncrement() == 0) {
                assertEquals(List.of(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING), List.of(options));
                throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected unsupported provider");
            }
            assertEquals(List.of(StandardCopyOption.REPLACE_EXISTING), List.of(options));
            Files.move(from, to, options);
        });
        assertEquals(2, calls.get());assertArrayEquals(bytes, Files.readAllBytes(destination));
        try (var files = Files.list(temporary)) { assertEquals(List.of(destination), files.toList()); }
    }
    @Test void failedMoveCleansTemporaryAndDoesNotReportSuccess() throws Exception {
        Path destination = temporary.resolve("out.json");Files.writeString(destination, "old");
        var failure = new IOException("injected move failure");
        assertSame(failure, assertThrows(IOException.class, () -> CfgJsonWriter.publish(new byte[]{1}, destination,
                (from, to, options) -> { throw failure; })));
        assertEquals("old", Files.readString(destination));
        try (var files = Files.list(temporary)) { assertEquals(List.of(destination), files.toList()); }
    }
}
