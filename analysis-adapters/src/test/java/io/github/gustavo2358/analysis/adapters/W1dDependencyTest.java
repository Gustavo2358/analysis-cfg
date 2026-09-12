package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.air.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.W1dBoundaryTest.input;
import static io.github.gustavo2358.analysis.adapters.W1dEffectsTest.*;

final class W1dDependencyTest {
    @Test void rawValueExistsButDependencyMustBeProduced() throws Exception {
        var p=input("dynamic-x8");var raw=value(p,invoke(p).header().id());
        assertEquals(List.of(new Values.TextValue("PROGA   ")),raw.candidates());
        var result=new DependencyAnalysis().prepare(p);assertEquals(1,result.sites().size());
        var fact=result.sites().getFirst();assertEquals(DependencySiteFact.TargetKind.COMPUTED,fact.targetKind());
        assertEquals(p.units().getFirst().id(),fact.caller());assertEquals(invoke(p).header().id(),fact.operation());
        assertEquals("PROGA   ",fact.rawCandidates().getFirst().rawValue());assertEquals("PROGA",fact.candidates().getFirst().referenceName());
        assertFalse(fact.modelValueRemainder());assertTrue(fact.sourceValueRemainder());assertTrue(fact.interpretationUnknownRemainder());assertTrue(fact.effectiveUnknownRemainder());
        assertEquals(raw.candidateSupports().getFirst().producers().getFirst().evidence(),fact.candidates().getFirst().supports().getFirst().producer());
        assertEquals(1,result.edges().size());assertEquals(1L,result.metrics().get("possibleValuesRuns"));
    }
    @Test void literalHasNoPossibleValuesPreparationOrRun() throws Exception {
        var result=new DependencyAnalysis().prepare(input("literal"));var fact=result.sites().getFirst();
        assertEquals("PROGA",fact.candidates().getFirst().referenceName());assertEquals(DependencySiteFact.TargetKind.LITERAL,fact.targetKind());
        assertEquals(DependencySiteFact.SupportKind.CALL_LITERAL,fact.candidates().getFirst().supports().getFirst().kind());
        assertNull(fact.subject());assertNull(fact.valuePoint());assertEquals(0L,result.metrics().get("possibleValuesRuns"));assertEquals(0L,result.metrics().get("possibleValuesPreparations"));
        assertEquals(1L,result.metrics().get("reachabilityRuns"));assertEquals(DependencySiteFact.Reachability.REACHABLE,fact.reachability());
    }
    @Test void noMoveIsReachableOpenWithoutInventedProgram() throws Exception {
        var result=new DependencyAnalysis().prepare(input("dynamic-no-move"));var fact=result.sites().getFirst();
        assertEquals(DependencySiteFact.TargetStatus.OPEN_TARGET,fact.targetStatus());assertTrue(fact.modelValueRemainder());
        assertTrue(fact.candidates().isEmpty());assertTrue(result.edges().isEmpty());assertEquals(DependencySiteFact.Reachability.REACHABLE,fact.reachability());
    }
}
