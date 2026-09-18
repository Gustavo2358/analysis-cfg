package io.github.gustavo2358.analysis.values;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

/** Product boundary: candidate evidence survives while physical completeness stays open. */
@org.junit.jupiter.api.Timeout(10)
class LogicalOnlyBoundaryTest {
    private RegionalValuesAnalysis.Execution run(StorageAnalysisMode mode) {
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"ABCDEFGH")))));
        return RegionalValuesAnalysis.prepare(session(p),mode).analysis().orElseThrow().execute();
    }
    @Test void defaultDisablesPhysicalWorkAndRetainsNamedCandidateWithOpenCompleteness() {
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"ABCDEFGH")))));
        var execution=RegionalValuesAnalysis.prepare(session(p)).analysis().orElseThrow().execute();
        var fact=at(execution,"return-s0",WHOLE);
        assertEquals(List.of("ABCDEFGH"),texts(fact));
        assertTrue(fact.modelValueRemainder());assertTrue(fact.effectiveUnknownRemainder());
        assertTrue(fact.modelReasons().contains("PHYSICAL_PROPAGATION_DISABLED"));
        assertFalse(fact.candidateSupports().isEmpty());
        assertEquals(0L,execution.metrics().get("physicalGroupsApplied"));
        assertEquals(0L,execution.metrics().get("physicalWritesApplied"));
        assertEquals(0L,execution.metrics().get("contentReads"));
    }
    @Test void physicalProjectionRemainsUnknownWithoutAutomaticFallback() {
        var execution=run(StorageAnalysisMode.LOGICAL_ONLY);
        var fact=at(execution,"return-s0",PREFIX);
        assertEquals(List.of(),texts(fact));assertTrue(fact.modelValueRemainder());
        assertEquals(0L,execution.metrics().get("physicalGroupsApplied"));
    }
    @Test void physicalProjectionRequiresExplicitExperimentalOptIn() {
        var execution=run(StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
        assertEquals(List.of("ABCD"),texts(at(execution,"return-s0",PREFIX)));
        assertTrue(execution.metrics().get("physicalGroupsApplied")>0);
        assertTrue(execution.metrics().get("physicalWritesApplied")>0);
    }
    @Test void policySeparatesProviderCacheIdentityAndDefaultKeys() {
        var entry=new io.github.gustavo2358.air.model.Ids.EntryId(U,"entry");
        assertNotEquals(RegionalValuesProvider.key(entry),RegionalValuesProvider.key(entry,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL));
        assertEquals(StorageAnalysisMode.LOGICAL_ONLY.profile(),RegionalValuesProvider.key(entry).profile());
        assertEquals(StorageAnalysisMode.LOGICAL_ONLY.profile(),StorageValuesProvider.key(entry).profile());
    }
    @Test void unknownBindingRetainsSourceEvidenceWithoutCompletenessOrPhysicalFallback() {
        var p=EvidencePreservingEntryTest.logical(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"OTHERPGM")))));
        var execution=RegionalValuesAnalysis.prepare(session(p)).analysis().orElseThrow().execute();
        var fact=at(execution,"return-s0",WHOLE);
        assertEquals(List.of("OTHERPGM","PGM00001"),texts(fact));assertTrue(fact.modelValueRemainder());
        assertEquals(0L,execution.metrics().get("physicalGroupsApplied"));
        assertEquals(0L,execution.preparationMetrics().get("physicalPlansPrepared"));
    }
    @Test void scalarCopyIsASnapshotEvenWhenSourceChangesLater() {
        var p=ValuesScaleTest.longSequence(3,3,true,true);var u=p.units().getFirst();var sequence=u.sequences().getFirst();
        var h=header(u.id(),"copy");var target=u.objects().get(0).id();var source=u.objects().get(1).id();
        var read=new io.github.gustavo2358.air.model.Expressions.Read(operand(h.id(),"read",io.github.gustavo2358.air.model.Operand.Role.VALUE_READ),
            new io.github.gustavo2358.air.model.Places.ObjectPlace(operand(h.id(),"source",io.github.gustavo2358.air.model.Operand.Role.VALUE_READ),source));
        var writes=new ArrayList<io.github.gustavo2358.air.model.Instruction>(sequence.instructions());
        writes.add(new io.github.gustavo2358.air.model.Operations.Assign(h,new io.github.gustavo2358.air.model.Places.ObjectPlace(operand(h.id(),"destination",io.github.gustavo2358.air.model.Operand.Role.VALUE_WRITE),target),read));
        writes.add(assign(u.id(),"later-source",source,"AFTER"));
        var changed=replace(p,List.of(unit(u.id(),u.entries(),List.of(new io.github.gustavo2358.air.model.Sequence(sequence.label(),writes,sequence.terminator(),sequence.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
        var run=RegionalValuesAnalysis.prepare(session(changed)).analysis().orElseThrow().execute();
        var fact=run.observe(List.of(ValuesTest.before(changed,0,0))).observations().getFirst().value();
        assertEquals(List.of("v1"),texts(fact));assertTrue(fact.modelValueRemainder());
        assertEquals(0L,run.metrics().get("physicalGroupsApplied"));
    }
    @Test void sharedCellIdentityDoesNotRequirePhysicalAliasing() {
        var p=ValuesScaleTest.longSequence(3,3,false,true);
        var run=RegionalValuesAnalysis.prepare(session(p)).analysis().orElseThrow().execute();
        var fact=run.observe(List.of(ValuesTest.before(p,0,2))).observations().getFirst().value();
        assertEquals(List.of("v2"),texts(fact));assertEquals(0L,run.metrics().get("physicalGroupsApplied"));
    }
}
