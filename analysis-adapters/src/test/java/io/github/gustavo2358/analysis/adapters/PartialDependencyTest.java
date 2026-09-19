package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

class PartialDependencyTest {
    static Publication unsupportedValues() {
        var p=W1dModelTest.model(1,u->{
            var object=new ObjectId(u,"object-0");
            var call=W1dModelTest.call(u,"computed","end",object,false);var effects=call.effectBound().otherwise();
            call=W1dEffectsTest.replace(call,new Interactions.EffectBound(effects,List.of(new Interactions.OutcomeEffects(Control.NormalOutcome.INSTANCE,effects))),call.outcomes());
            return List.of(MultiCallModelTest.invoke(u,"start",List.of(),MultiCallModelTest.literal(u,"direct","DIRECT","second")),
                MultiCallModelTest.invoke(u,"second",List.of(),call),returning(u,"end",List.of()));
        });
        // W5 can now analyze partial effect profiles. This independent hard boundary
        // is an unknown required semantic capability, never treated as harmless.
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(new Capabilities.Capability("ep.unknown-semantics","1")),List.of()),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void unsupportedComputedPreparationDoesNotDiscardIndependentLiteral() {
        var result=assertDoesNotThrow(()->new DependencyAnalysis().prepare(unsupportedValues()));
        assertEquals(2,result.sites().size());
        var direct=result.sites().stream().filter(s->s.operation().localId().equals("direct")).findFirst().orElseThrow();
        assertEquals(List.of("DIRECT"),direct.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(result.edges().stream().anyMatch(e->e.candidate().referenceName().equals("DIRECT")));
        var unknown=result.sites().stream().filter(s->s.operation().localId().equals("computed")).findFirst().orElseThrow();
        assertTrue(unknown.candidates().isEmpty());assertTrue(unknown.effectiveUnknownRemainder());
    }
    static Publication unsupportedControl() {
        var p=W1dModelTest.model(1,u->List.of(MultiCallModelTest.invoke(u,"start",List.of(),MultiCallModelTest.literal(u,"direct","DIRECT","end")),returning(u,"end",List.of())));
        var unit=p.units().getFirst();var sequence=unit.sequences().getFirst();var call=(Operations.Invoke)sequence.terminator();
        var changed=W1dEffectsTest.replace(call,call.effectBound(),new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(unit.id(),"end")),Control.Diverge.INSTANCE),Scopes.NoControl.INSTANCE));
        var partial=W1dEffectsTest.sequences(p,List.of(new Sequence(sequence.label(),sequence.instructions(),changed,sequence.origin()),unit.sequences().get(1)),unit.entries());
        return partial;
    }
    @Test void unmodeledControlCannotEraseAnInventoriedLiteralOccurrence() {
        var result=assertDoesNotThrow(()->new DependencyAnalysis().prepare(unsupportedControl()));
        assertEquals(List.of("DIRECT"),result.sites().getFirst().candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(result.sites().getFirst().openControlRemainder());
    }
    @Test void partialSchemaPreservesFactsReasonsAndExactWireForIndependentReader() throws Exception {
        var output=Path.of("target/ep-w4");Files.createDirectories(output);
        for(var entry:Map.of("unsupported-values",unsupportedValues(),"unsupported-control",unsupportedControl()).entrySet()) {
            var p=entry.getValue();
            var result=new DependencyAnalysis().prepare(p);assertTrue(result.partial());
            assertFalse(result.analysisReasons().isEmpty());
            assertTrue(result.sites().stream().filter(f->f.analysisStatus()==DependencySiteFact.AnalysisStatus.PARTIAL).allMatch(f->!f.analysisReasons().isEmpty()));
            var wire=W1dAdversarialTest.wire(result);assertArrayEquals(wire,W1dAdversarialTest.wire(result));
            assertTrue(new String(wire,java.nio.charset.StandardCharsets.UTF_8).contains("\"version\":\"2.4.0\""));
            Files.write(output.resolve(entry.getKey()+".json"),wire);
        }
    }
    @Test void structurallyInvalidReferenceStillRejectsTheWholePublication() {
        var p=unsupportedValues();var u=p.units().getFirst();var sequence=u.sequences().getFirst();var call=(Operations.Invoke)sequence.terminator();
        var invalid=W1dEffectsTest.replace(call,call.effectBound(),new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(u.id(),"missing-sequence"))),Scopes.NoControl.INSTANCE));
        var sequences=new ArrayList<>(u.sequences());sequences.set(0,new Sequence(sequence.label(),sequence.instructions(),invalid,sequence.origin()));
        var malformed=W1dEffectsTest.sequences(p,sequences,u.entries());
        var failure=assertThrows(DependencyAnalysis.Failure.class,()->new DependencyAnalysis().prepare(malformed));
        assertEquals(DependencyAnalysis.Kind.INVALID_INPUT,failure.kind());
    }

}
