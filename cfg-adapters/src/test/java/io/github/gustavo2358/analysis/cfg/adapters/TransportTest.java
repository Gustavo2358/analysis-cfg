package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.analysis.cfg.application.BuildOptions;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TransportTest {
    @TempDir Path temporary;
    private Path fixture() throws Exception {
        return Path.of(getClass().getResource("/air/goback.canonical.json").toURI());
    }
    private CfgBuildResult build() throws Exception {
        return new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(
                new AirJsonFileReader().read(fixture()), BuildOptions.defaults());
    }
    @Test void fileDecodePreservesExpectedAirFacts() throws Exception {
        var publication = new AirJsonFileReader().read(fixture());
        assertEquals("goback-0b-manual", publication.id().localId());
        assertEquals(Evidence.InventoryStatus.PARTIAL, publication.coverage().inventory());
        assertEquals(1, publication.units().size());
        var unit = publication.units().getFirst();
        assertEquals(Evidence.InventoryStatus.PARTIAL, unit.coverage().inventory());
        assertEquals("unit", unit.id().localId());
        assertEquals("primary-entry", unit.entries().getFirst().id().localId());
        assertEquals("sequence", unit.entries().getFirst().initialLabel().orElseThrow().localId());
        assertInstanceOf(Operations.Return.class, unit.sequences().getFirst().terminator());
        assertEquals("return", unit.sequences().getFirst().terminator().header().id().localId());
        assertFalse(publication.uncertainties().isEmpty());
        assertEquals(new AirJson().decode(Files.readAllBytes(fixture())), publication);
    }
    @Test void realBuildHasExactManualTopologyAndCorrelations() throws Exception {
        var result = build();
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        assertEquals(ProjectionPolicy.KNOWN_SUBSET, result.options().projectionPolicy());
        var graph = result.graph().orElseThrow();
        assertEquals(3, graph.nodes().size());
        var sequence = assertInstanceOf(CfgNode.SequenceNode.class, graph.nodes().get(0));
        var entry = assertInstanceOf(CfgNode.EntryNode.class, graph.nodes().get(1));
        var exit = assertInstanceOf(CfgNode.NormalExit.class, graph.nodes().get(2));
        var p = new Ids.PublicationId("goback-0b-manual");
        var u = new Ids.UnitId(p, "unit");
        var e = new Ids.EntryId(u, "primary-entry");
        assertEquals(e, entry.source().id());
        assertEquals(new Ids.LabelId(u, "sequence"), sequence.source().label());
        assertEquals(new Ids.OperationId(u, "return"), sequence.source().terminator().header().id());
        assertInstanceOf(Operations.Return.class, sequence.source().terminator());
        assertEquals(u, exit.unitId());
        assertEquals(e, exit.entryId());
        assertEquals(List.of(new CfgTransition(entry.id(), sequence.id(), CfgTransition.Kind.ENTRY, e),
                new CfgTransition(sequence.id(), exit.id(), CfgTransition.Kind.RETURN, e)), graph.transitions());
        assertEquals(Evidence.InventoryStatus.PARTIAL, graph.publication().coverage().inventory());
    }
    @Test void writerMatchesIndependentGoldenBytesAndIsDeterministic() throws Exception {
        byte[] golden = getClass().getResourceAsStream("/cfg/goback.manual.json").readAllBytes();
        var writer = new CfgJsonWriter();
        assertArrayEquals(golden, writer.encode(build()));
        Path a = temporary.resolve("a.json"), b = temporary.resolve("b.json");
        writer.write(build(), a);
        writer.write(build(), b);
        assertArrayEquals(golden, Files.readAllBytes(a));
        assertArrayEquals(Files.readAllBytes(a), Files.readAllBytes(b));
        assertNotEquals('\n', golden[golden.length - 1]);
    }
    @Test void readerPhysicalBoundAcceptsExactSizeAndRejectsOneExtra() throws Exception {
        int size = Math.toIntExact(Files.size(fixture()));
        var reader = new AirJsonFileReader(new AirJson.Limits(size, 128), ValidationOptions.defaults());
        assertEquals("goback-0b-manual", reader.read(fixture()).id().localId());
        Path excess = temporary.resolve("excess.json");
        Files.write(excess, Files.readAllBytes(fixture()));
        Files.writeString(excess, " ", java.nio.file.StandardOpenOption.APPEND);
        var failure = assertThrows(AirInputLimitException.class, () -> reader.read(excess));
        assertEquals(size, failure.maximumDocumentBytes());
    }
    @Test void codecFailurePreservesCodePathAndIssues() throws Exception {
        byte[] invalid = Files.readString(fixture()).replace("\"inputs\":[{\"domain\":\"origin\",\"localId\":\"entry\",\"publication\":\"goback-0b-manual\"}]", "\"inputs\":[]").getBytes(StandardCharsets.UTF_8);
        Path file = temporary.resolve("invalid.json");Files.write(file, invalid);
        var direct = assertThrows(AirJsonException.class, () -> new AirJson().decode(invalid));
        var actual = assertThrows(AirJsonException.class, () -> new AirJsonFileReader().read(file));
        assertEquals(AirJsonException.Code.INVALID_IR, actual.code());
        assertEquals(direct.code(), actual.code());
        assertEquals(direct.path(), actual.path());
        assertFalse(actual.issues().isEmpty());
        assertEquals(direct.issues(), actual.issues());
    }
    @Test void outputLimitPreservesExistingDestinationBeforeAnyTemp() throws Exception {
        Path output = temporary.resolve("cfg.json");
        Files.writeString(output, "sentinel");
        var result = build();
        assertThrows(CfgJsonException.class, () -> new CfgJsonWriter(10).write(result, output));
        assertEquals("sentinel", Files.readString(output));
        try (var files = Files.list(temporary)) { assertEquals(List.of(output), files.toList()); }
    }
    @Test void nonBuiltResultCannotBeSerializedOrPublished() throws Exception {
        var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(
                new AirJsonFileReader().read(fixture()), new BuildOptions(ValidationOptions.defaults(), ProjectionPolicy.STRICT));
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT, result.status());
        Path output = temporary.resolve("cfg.json");
        assertThrows(CfgJsonException.class, () -> new CfgJsonWriter().write(result, output));
        assertFalse(Files.exists(output));
    }
}
