package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

class PartialDependencyTest {
    static Publication unsupportedValues() {
        return W1dModelTest.model(1,u->{
            var object=new ObjectId(u,"object-0");
            var call=W1dModelTest.call(u,"computed","end",object,false);var effects=call.effectBound().otherwise();
            call=W1dEffectsTest.replace(call,new Interactions.EffectBound(effects,List.of(new Interactions.OutcomeEffects(Control.NormalOutcome.INSTANCE,effects))),call.outcomes());
            return List.of(MultiCallModelTest.invoke(u,"start",List.of(),MultiCallModelTest.literal(u,"direct","DIRECT","second")),
                MultiCallModelTest.invoke(u,"second",List.of(),call),returning(u,"end",List.of()));
        });
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
    @Test void unmodeledControlCannotEraseAnInventoriedLiteralOccurrence() {
        var p=W1dModelTest.model(1,u->{
            var call=MultiCallModelTest.literal(u,"direct","DIRECT","end");
            call=W1dEffectsTest.replace(call,call.effectBound(),new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(u,"end")),Control.Diverge.INSTANCE),Scopes.NoControl.INSTANCE));
            return List.of(MultiCallModelTest.invoke(u,"start",List.of(),call),returning(u,"end",List.of()));
        });
        var result=assertDoesNotThrow(()->new DependencyAnalysis().prepare(p));
        assertEquals(List.of("DIRECT"),result.sites().getFirst().candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(result.sites().getFirst().openControlRemainder());
    }
}
