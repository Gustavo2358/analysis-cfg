package io.github.gustavo2358.analysis.values;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.EpR2Fixtures.*;

class EpR2FactorizationTest {
    /** Structural oracle: count materialized alternatives, not elapsed time. */
    static long alternatives(RegionalValuesAnalysis.State state) throws ReflectiveOperationException {
        return state.materializedAlternatives();
    }
    @Test void connectedEntryFactorsGrowBySum() throws ReflectiveOperationException {
        for(int p:List.of(1,2,4,7)) {
            var publication=fixture(8,p,0,0,7,false,true,"diamonds",false);
            var analysis=EpR2EntryTest.analysis(publication);var engine=analysis.new Engine();
            var sessionField=analysis.getClass().getDeclaredField("session");sessionField.setAccessible(true);
            var session=(io.github.gustavo2358.analysis.structure.AnalysisSession)sessionField.get(analysis);
            var boundary=engine.boundaries(session).iterator().next();
            assertTrue(alternatives(boundary.state())<=8L*(p+1),"G must store component alternatives, not complete worlds: P="+p);
        }
    }
    @Test void independentSlicesWithinOneRegionAreAlsoFactored() throws ReflectiveOperationException {
        var publication=fixture(1,7,0,0,0,false,true,"control",false);
        var analysis=EpR2EntryTest.analysis(publication);var engine=analysis.new Engine();
        var sessionField=analysis.getClass().getDeclaredField("session");sessionField.setAccessible(true);
        var session=(io.github.gustavo2358.analysis.structure.AnalysisSession)sessionField.get(analysis);
        assertTrue(alternatives(engine.boundaries(session).iterator().next().state())<=14,"seven independent slices need fourteen alternatives");
    }
    @Test void emptyEntryWithThirtyTwoOpenWeakCopiesDoesNotBuildWorlds() {
        var ex=EpR2EntryTest.analysis(fixture(32,0,0,0,32,false,true,"copies",false)).execute();
        assertTrue(ex.solveMetrics().get("maxStateAlternatives")<100_000,"structural regression, not a solver cap");
        assertTrue(EpR2EntryTest.at(ex,0).candidates().isEmpty());
        assertTrue(EpR2EntryTest.at(ex,0).modelValueRemainder());
        System.out.println("EP_R2_I "+ex.solveMetrics());
    }
    @Test void connectedCopiesAndScaledJoinsPreserveRecall() {
        for(boolean proof:List.of(true,false))for(int p:List.of(1,2,4,7)) {
            var ex=EpR2EntryTest.analysis(fixture(8,p,0,0,1000,proof,!proof,"diamonds",false)).execute();
            var fact=EpR2EntryTest.at(ex,0);
            assertTrue(fact.candidates().contains(new io.github.gustavo2358.air.model.Values.TextValue("PROG0000")));
            assertTrue(fact.modelValueRemainder());assertFalse(fact.candidateSupports().isEmpty());
            assertTrue(ex.solveMetrics().get("boundaryAlternatives")<=8L*(p+1));
            System.out.println("EP_R2_G p="+p+" proof="+proof+" "+ex.solveMetrics());
        }
    }
}
