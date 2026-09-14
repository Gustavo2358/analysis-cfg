package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
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
    @Test void regionalFileCopyThenComputedCallPreservesControlAndCodec() throws Exception {
        var original=new AirJsonFileReader().read(Path.of(getClass().getResource("/air/regional.canonical.json").toURI()));
        var u=original.units().getFirst();var s=u.sequences().getFirst();var origin=s.origin();
        var region=original.storage().getFirst().header().id();
        var viewId=new ObjectId(u.id(),"call-view");var text=Types.known(Types.Builtin.TEXT);
        var code=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",text);
        var object=new Memory.ObjectDeclaration(viewId,java.util.Optional.empty(),text,
                new Memory.ViewBinding(region,java.math.BigInteger.TWO,java.math.BigInteger.valueOf(4),code),Memory.Visibility.PRIVATE,
                origin,Evidence.CoverageStatus.MODELED,s.terminator().header().precision());
        var objects=new java.util.ArrayList<>(u.objects());objects.add(object);
        var op=new OperationId(u.id(),"computed-call");var owner=new OperationOwner(op);
        var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"target-place"),Operand.Role.VALUE_READ,origin),viewId);
        var read=new Expressions.Read(new Operand.Header(new OperandId(owner,"target"),Operand.Role.CALL_TARGET,origin),place);
        var target=new Interactions.ComputedTarget("program","manual.runtime",read,Interactions.ExactName.INSTANCE,origin);
        var next=new LabelId(u.id(),"after-call");var header=s.terminator().header();
        var call=new Operations.Invoke(new Operations.Header(op,origin,header.coverage(),header.precision(),header.uncertainties()),"call",target,List.of(),List.of(),
                new Interactions.ExternalSignature(u.entries().getFirst().signature()),List.of(),
                new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,Scopes.NoMemory.INSTANCE,List.of()),List.of()),
                new Control.InvocationOutcomes(List.of(new Control.Normal(next)),Scopes.NoControl.INSTANCE),
                new Interactions.KnownContract(new Interactions.ContractRef("manual.pure-call","1",List.of(origin))));
        var sequences=List.of(new Sequence(s.label(),s.instructions(),call,origin),new Sequence(next,List.of(),s.terminator(),origin));
        var unit=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),objects,u.visibleObjects(),u.entries(),sequences,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        var publication=new Publication(original.id(),original.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),
                original.artifacts(),List.of(unit),original.storage(),original.resources(),original.artifactRelations(),original.origins(),original.coverage(),original.uncertainties(),original.premises());
        var codec=new AirJson();var bytes=codec.encode(publication);Path input=temporary.resolve("regional.air.json");Files.write(input,bytes);
        var restored=new AirJsonFileReader().read(input);assertEquals(publication,restored);
        var builder=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        var memory=builder.build(publication,BuildOptions.defaults());var file=builder.build(restored,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,memory.status(),memory.toString());assertEquals(memory.status(),file.status());
        assertArrayEquals(new CfgJsonWriter().encode(memory),new CfgJsonWriter().encode(file));
        var graph=file.graph().orElseThrow();assertEquals(4,graph.nodes().size());assertEquals(3,graph.transitions().size());
        assertEquals(1,graph.transitions().stream().filter(t->t.kind()==CfgTransition.Kind.INVOKE_NORMAL).count());
        var body=graph.nodes().stream().filter(n->n instanceof CfgNode.SequenceNode q && q.source().label().equals(s.label()))
                .map(n->(CfgNode.SequenceNode)n).findFirst().orElseThrow();
        assertEquals(s.instructions(),body.source().instructions());assertInstanceOf(Operations.CopyBytes.class,body.source().instructions().get(1));
        assertEquals(call,body.source().terminator());
        assertEquals(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),graph.preciseControlCapabilities());
        assertFalse(io.github.gustavo2358.analysis.cfg.domain.CoreCfgProjection.supportsControlCapability(new Capabilities.Capability("text.ebcdic.ibm1047","2")));
        assertFalse(io.github.gustavo2358.analysis.cfg.domain.CoreCfgProjection.supportsControlCapability(new Capabilities.Capability("text.unknown","1")));
        // This role only projects control. Concrete codec interpretation is shared and explicitly selected.
        assertEquals(new Values.TextValue(" Aé "),MemoryCodecs.decodeText(code,new Values.BytesValue(List.of(0x40,0xc1,0x51,0x40)),java.math.BigInteger.valueOf(4)).value().orElseThrow());
    }
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
    @Test void resourceLimitPreservesTypedCodecIssues() throws Exception {
        var options = new ValidationOptions(128, 1, 100);
        var limits = AirJson.Limits.defaults();
        var direct = assertThrows(AirJsonException.class, () -> new AirJson(limits, options).decode(Files.readAllBytes(fixture())));
        var actual = assertThrows(AirJsonException.class, () -> new AirJsonFileReader(limits, options).read(fixture()));
        assertEquals(AirJsonException.Code.RESOURCE_LIMIT, actual.code());
        var validation=actual.validationResult().orElseThrow();
        assertEquals(io.github.gustavo2358.air.validation.ValidationResult.Status.INCOMPLETE_VALIDATION,validation.status());
        assertTrue(validation.hasIssues(io.github.gustavo2358.air.validation.ValidationIssue.Kind.RESOURCE_LIMIT));
        assertFalse(validation.diagnostics().traversalCompleted());
        assertEquals(direct.path(), actual.path());
        assertEquals(direct.issues(), actual.issues());
        assertFalse(actual.issues().isEmpty());
    }
    @Test void unsupportedCapabilityRemainsACodecFailure() throws Exception {
        Path source = temporary.resolve("capability.json");
        String original = Files.readString(fixture());
        String modified = original.replace("\"required\":[]", "\"required\":[{\"name\":\"unimplemented.capability\",\"version\":\"1\"}]");
        assertNotEquals(original, modified);
        Files.writeString(source, modified);
        var direct = assertThrows(AirJsonException.class, () -> new AirJson().decode(Files.readAllBytes(source)));
        var actual = assertThrows(AirJsonException.class, () -> new AirJsonFileReader().read(source));
        assertEquals(AirJsonException.Code.UNSUPPORTED_CAPABILITY, actual.code());
        assertEquals(direct.path(), actual.path());
        assertEquals(direct.issues(), actual.issues());
    }

}
