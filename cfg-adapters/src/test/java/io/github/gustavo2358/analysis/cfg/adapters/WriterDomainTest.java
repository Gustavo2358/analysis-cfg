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
import java.io.ByteArrayOutputStream;
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
    @Test void partialAnalysisRequiresExplicitPartialResultWire() {
        var cfg=build(MemoryFacts.goback(),ProjectionPolicy.PARTIAL_ANALYSIS);
        assertThrows(CfgJsonException.class,()->new CfgJsonWriter().encode(cfg));
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
    @Test void productionEncoderStreamsTheExactCompatibilityWire() throws Exception {
        var result=build(MemoryFacts.mixed("streamed"),ProjectionPolicy.KNOWN_SUBSET);
        var writer=new CfgJsonWriter();byte[] expected=writer.encode(result);
        var output=new ByteArrayOutputStream(){@Override public synchronized void write(byte[] value,int offset,int length){
            fail("CFG production encoder must not submit a resident aggregate");
        }};
        writer.encode(result,output);
        assertArrayEquals(expected,output.toByteArray());
    }
    @Test void schemaSelectionDoesNotAllocateTheEntryTimesBodyProductBeforeFirstByte() {
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported(),"HotSpot allocation oracle required");
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId();
        for(int count:new int[]{32,128,512,2048}) {
            var result=build(entryBodyGeometry(count),ProjectionPolicy.KNOWN_SUBSET);
            var graph=result.graph().orElseThrow();
            long physical=((io.github.gustavo2358.analysis.cfg.domain.CfgTransitionTable)graph.transitions()).stored().size();
            assertEquals(2L*count,physical);
            assertEquals((long)count*(count+1),graph.transitions().size());
            for(int warm=0;warm<3;warm++)assertThrows(CfgJsonException.class,()->new CfgJsonWriter(1).encode(result));
            long before=bean.getThreadAllocatedBytes(thread);
            assertThrows(CfgJsonException.class,()->new CfgJsonWriter(1).encode(result));
            long allocated=bean.getThreadAllocatedBytes(thread)-before;
            assertTrue(allocated<=1_048_576+256*physical,"schema prologue expanded entry/body product: count="+count+" physical="+physical+" allocated="+allocated);
            System.out.println("CFG_SCHEMA_SELECTION_METRICS entries="+count+" body="+count+" physical="+physical+" allocated="+allocated);
        }
    }
    private static Publication entryBodyGeometry(int count) {
        var p=new io.github.gustavo2358.air.model.Ids.PublicationId("writer-entry-body");
        var u=new io.github.gustavo2358.air.model.Ids.UnitId(p,"unit");var origin=new io.github.gustavo2358.air.model.Ids.OriginId(p,"origin");
        var entries=new java.util.ArrayList<io.github.gustavo2358.air.model.Entries.Entry>();
        var sequences=new java.util.ArrayList<io.github.gustavo2358.air.model.Sequence>();
        var initial=new io.github.gustavo2358.air.model.Ids.LabelId(u,"sequence-00000");
        for(int i=0;i<count;i++) {
            String suffix=String.format("%05d",i);
            entries.add(MemoryFacts.entry(new io.github.gustavo2358.air.model.Ids.EntryId(u,"entry-"+suffix),initial,origin));
            sequences.add(new io.github.gustavo2358.air.model.Sequence(new io.github.gustavo2358.air.model.Ids.LabelId(u,"sequence-"+suffix),List.of(),
                    new io.github.gustavo2358.air.model.Operations.Return(MemoryFacts.header(new io.github.gustavo2358.air.model.Ids.OperationId(u,"return-"+suffix),origin),List.of()),origin));
        }
        var unit=MemoryFacts.unit(u,origin,entries,sequences,MemoryFacts.complete(new io.github.gustavo2358.air.model.Scopes.UnitScope(u)));
        return new Publication(p,io.github.gustavo2358.air.model.SemanticVersion.AIR_2_0_0,
                new io.github.gustavo2358.air.model.Capabilities.Manifest(List.of(),List.of()),List.of(),List.of(unit),List.of(),List.of(),List.of(),
                List.of(new io.github.gustavo2358.air.model.Origins.Unavailable(origin,"synthetic header geometry")),MemoryFacts.complete(new io.github.gustavo2358.air.model.Scopes.PublicationScope(p)),List.of(),List.of());
    }
    @Test void atomicProductionWriterMetersTheLogicalWire() throws Exception {
        var result=build(MemoryFacts.mixed("metered"),ProjectionPolicy.KNOWN_SUBSET);
        var writer=new CfgJsonWriter();byte[] expected=writer.encode(result);long[] metered={0};
        Path output=temporary.resolve("metered.json");writer.write(result,output,count->metered[0]+=count);
        assertEquals(expected.length,metered[0]);assertArrayEquals(expected,Files.readAllBytes(output));
    }
    @Test void interruptedStreamingWritePreservesDestinationAndCleansTemporary() throws Exception {
        var result=build(MemoryFacts.mixed("cancelled"),ProjectionPolicy.KNOWN_SUBSET);
        Path output=temporary.resolve("cancelled.json");Files.writeString(output,"sentinel");
        var failure=new RuntimeException("injected cancellation");
        assertSame(failure,assertThrows(RuntimeException.class,()->new CfgJsonWriter().write(result,output,count->{throw failure;})));
        assertEquals("sentinel",Files.readString(output));
        try(var files=Files.list(temporary)){assertEquals(List.of(output),files.toList());}
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
