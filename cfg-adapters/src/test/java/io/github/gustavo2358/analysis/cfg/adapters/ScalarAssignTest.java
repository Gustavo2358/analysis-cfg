package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** EVAL-CFG-032: real merged 4B bytes, independent control oracle, no AIR encoding in setup. */
class ScalarAssignTest {
    @TempDir Path temporary;

    private Path fixture() throws Exception {
        return Path.of(getClass().getResource("/air/scalar-assign.canonical.json").toURI());
    }

    private Publication read() throws Exception { return new AirJsonFileReader().read(fixture()); }

    private CfgBuildResult build(Publication publication) {
        BuildCfg builder = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        var result = builder.build(publication, BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        assertEquals(ProjectionPolicy.KNOWN_SUBSET, result.options().projectionPolicy());
        return result;
    }

    @Test void sequencePayloadAndObjectCellIdentitySurviveRealReaderAndBuild() throws Exception {
        var publication = read();
        var unit = publication.units().getFirst();
        var original = unit.sequences().getFirst();
        var graph = build(publication).graph().orElseThrow();
        var source = assertInstanceOf(CfgNode.SequenceNode.class, graph.nodes().getFirst()).source();
        assertSame(publication, graph.publication());
        assertSame(unit, graph.publication().units().getFirst());
        assertSame(original, source);
        assertEquals(1, source.instructions().size());
        var assign = assertInstanceOf(Operations.Assign.class, source.instructions().getFirst());
        assertSame(original.instructions().getFirst(), assign);
        var destination = assertInstanceOf(Places.ObjectPlace.class, assign.destination());
        var literal = assertInstanceOf(Expressions.Literal.class, assign.value());
        var text = assertInstanceOf(Values.TextValue.class, literal.value());
        assertEquals("PROGA", text.value());
        var originalAssign = (Operations.Assign) original.instructions().getFirst();
        assertSame(originalAssign.destination(), destination);
        assertSame(originalAssign.value(), literal);
        assertSame(((Expressions.Literal) originalAssign.value()).value(), text);
        var p = new Ids.PublicationId("cp4b-scalar-manual");
        var u = new Ids.UnitId(p, "alpha");
        assertEquals(new Ids.OperationId(u, "set-program"), assign.header().id());
        assertEquals(new Ids.ObjectId(u, "data-slot"), destination.object());
        assertEquals(1, unit.objects().size());
        var object = unit.objects().getFirst();
        // ObjectPlace carries a full ObjectId, not a Java pointer to a declaration.
        assertEquals(object.id(), destination.object());
        assertSame(object, graph.publication().units().getFirst().objects().getFirst());
        var binding = assertInstanceOf(Memory.CellBinding.class, object.storage());
        assertEquals(new Ids.StorageId(p, "backing-cell"), binding.storage());
        assertEquals(1, publication.storage().size());
        var cell = assertInstanceOf(Memory.Cell.class, publication.storage().getFirst());
        assertEquals(binding.storage(), cell.header().id());
        assertSame(cell, graph.publication().storage().getFirst());
        var returned = assertInstanceOf(Operations.Return.class, source.terminator());
        assertSame(original.terminator(), returned);
        assertEquals(new Ids.OperationId(u, "leave"), returned.header().id());
    }

    @Test void topologyIsExactlyEntrySequenceReturnAndPartialKnowledgeIsPreserved() throws Exception {
        var publication = read();
        var graph = build(publication).graph().orElseThrow();
        assertEquals(3, graph.nodes().size());
        var sequence = assertInstanceOf(CfgNode.SequenceNode.class, graph.nodes().get(0));
        var entry = assertInstanceOf(CfgNode.EntryNode.class, graph.nodes().get(1));
        var exit = assertInstanceOf(CfgNode.NormalExit.class, graph.nodes().get(2));
        var p = new Ids.PublicationId("cp4b-scalar-manual");
        var u = new Ids.UnitId(p, "alpha");
        var e = new Ids.EntryId(u, "start");
        assertEquals(e, entry.source().id());
        assertEquals(new Ids.LabelId(u, "body"), sequence.source().label());
        assertEquals(u, exit.unitId());
        assertEquals(e, exit.entryId());
        assertEquals(List.of(new CfgTransition(entry.id(), sequence.id(), CfgTransition.Kind.ENTRY, e),
                new CfgTransition(sequence.id(), exit.id(), CfgTransition.Kind.RETURN, e)), graph.transitions());
        assertEquals(Evidence.InventoryStatus.PARTIAL, graph.publication().coverage().inventory());
        assertEquals(Evidence.InventoryStatus.PARTIAL, graph.publication().units().getFirst().coverage().inventory());
        assertSame(publication.coverage(), graph.publication().coverage());
        assertSame(publication.units().getFirst().coverage(), graph.publication().units().getFirst().coverage());
        assertFalse(publication.uncertainties().isEmpty());
        assertSame(publication.uncertainties(), graph.publication().uncertainties());
        assertSame(publication.origins(), graph.publication().origins());
        assertSame(publication.premises(), graph.publication().premises());
    }

    @Test void manualCfgGoldenRemainsTopologyOnlyAndDeterministic() throws Exception {
        byte[] expected;
        try (var stream = getClass().getResourceAsStream("/cfg/scalar-assign.manual.json")) {
            expected = stream.readAllBytes();
        }
        var writer = new CfgJsonWriter();
        assertArrayEquals(expected, writer.encode(build(read())));
        assertArrayEquals(expected, writer.encode(build(read())));
    }

    @Test void defaultPhysicalAndCodecLimitsRemainSixteenMiBAndDepth128() throws Exception {
        assertEquals(16 * 1024 * 1024, AirJson.Limits.defaults().maximumDocumentBytes());
        assertEquals(128, AirJson.Limits.defaults().maximumDepth());
        assertEquals(14554, Files.size(fixture()));
        assertNotNull(read());
        Path excess = temporary.resolve("over-default.json");
        try (var file = new java.io.RandomAccessFile(excess.toFile(), "rw")) {
            file.setLength(16L * 1024 * 1024 + 1);
        }
        var failure = assertThrows(AirInputLimitException.class, () -> new AirJsonFileReader().read(excess));
        assertEquals(16 * 1024 * 1024, failure.maximumDocumentBytes());
    }

    @Test void manyAssignsRemainOneSequenceNodeWithoutCopyingPayload() throws Exception {
        var publication = read();
        var unit = publication.units().getFirst();
        var source = unit.sequences().getFirst();
        var first = (Operations.Assign) source.instructions().getFirst();
        var target = (Places.ObjectPlace) first.destination();
        var literal = (Expressions.Literal) first.value();
        List<Instruction> instructions = new ArrayList<>(source.instructions());
        for (int i = 1; i < 4096; i++) {
            var id = new Ids.OperationId(unit.id(), "smoke-" + i);
            var owner = new Ids.OperationOwner(id);
            var header = first.header();
            instructions.add(new Operations.Assign(new Operations.Header(id, header.origin(), header.coverage(),
                    header.precision(), header.uncertainties()),
                    new Places.ObjectPlace(new Operand.Header(new Ids.OperandId(owner, "target"),
                            target.header().role(), target.header().origin()), target.object()),
                    new Expressions.Literal(new Operand.Header(new Ids.OperandId(owner, "value"),
                            literal.header().role(), literal.header().origin()), literal.value())));
        }
        var sequence = new Sequence(source.label(), instructions, source.terminator(), source.origin());
        var expandedUnit = new Unit(unit.id(), unit.containingUnit(), unit.objects(), unit.visibleObjects(),
                unit.entries(), List.of(sequence), unit.completionPorts(), unit.body(), unit.bodyUnavailable(),
                unit.coverage(), unit.origin());
        var expanded = new Publication(publication.id(), publication.airVersion(), publication.capabilities(),
                publication.artifacts(), List.of(expandedUnit), publication.storage(), publication.resources(),
                publication.artifactRelations(), publication.origins(), publication.coverage(),
                publication.uncertainties(), publication.premises());
        var graph = build(expanded).graph().orElseThrow();
        assertEquals(3, graph.nodes().size());
        assertEquals(2, graph.transitions().size());
        assertEquals(1, graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance).count());
        var retained = ((CfgNode.SequenceNode) graph.nodes().getFirst()).source();
        assertSame(expanded, graph.publication());
        assertSame(sequence, retained);
        assertEquals(4096, retained.instructions().size());
        for (int i = 0; i < instructions.size(); i++) assertSame(instructions.get(i), retained.instructions().get(i));
        assertSame(unit.objects().getFirst(), graph.publication().units().getFirst().objects().getFirst());
        assertSame(publication.storage().getFirst(), graph.publication().storage().getFirst());
    }
}
