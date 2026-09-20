package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;

/** W1 laws: diagnostic metadata is excluded only from the presentation comparison. */
class PositiveMemoryTopologyTest {
    static Publication diagnostics(Publication p,int count) {
        if(count==0)return p;
        var gaps=new ArrayList<Evidence.Uncertainty>();var refs=new ArrayList<UncertaintyId>();
        for(int i=0;i<count;i++) {
            var id=new UncertaintyId(p.id(),"diagnostic-"+i);refs.add(id);
            gaps.add(new Evidence.Uncertainty(id,"OMITTED_ASPECT_"+i,List.of(Evidence.Dimension.VALUES),new Scopes.PublicationScope(p.id()),"coverage only",origin(p.id())));
        }
        var units=new ArrayList<Unit>();
        for(var u:p.units()) {
            var claim=new Evidence.Claim(new Scopes.UnitScope(u.id()),Evidence.PrecisionStatus.UNAVAILABLE,refs);
            var precision=new Evidence.Precision(claim,claim,claim,claim,claim);
            var objects=u.objects().stream().map(o->new Memory.ObjectDeclaration(o.id(),o.displayName(),o.typeRef(),o.storage(),o.visibility(),o.origin(),Evidence.CoverageStatus.ABSTRACTED,precision)).toList();
            var sequences=u.sequences().stream().map(s->new Sequence(s.label(),s.instructions().stream().map(op->{
                var h=op.header();var dh=new Operations.Header(h.id(),h.origin(),Evidence.CoverageStatus.ABSTRACTED,precision,refs);
                return op instanceof Operations.Assign a?(Instruction)new Operations.Assign(dh,a.destination(),a.value()):op instanceof Operations.CopyBytes c?new Operations.CopyBytes(dh,c.destination(),c.source(),c.length(),c.fallback()):op;
            }).toList(),s.terminator(),s.origin())).toList();
            units.add(unit(u.id(),u.entries(),sequences,objects));
        }
        var coverage=new Evidence.Coverage(Evidence.InventoryStatus.PARTIAL,new Scopes.PublicationScope(p.id()),List.of(),refs);
        return replace(p,units,coverage,gaps,p.premises());
    }
    @Test void diagnosticsAndRegionsNeverActivatePhysicalDefault() {
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"seed",WHOLE,"ABCDEFGH")))));
        for(int count:List.of(0,1,50)) {
            var result=RegionalValuesAnalysis.prepare(session(diagnostics(p,count))).analysis().orElseThrow().execute();
            assertEquals(1L,result.metrics().get("logicalOnlyMode"));
            assertEquals(0L,result.metrics().get("physicalGroupsApplied"));assertEquals(0L,result.metrics().get("physicalWritesApplied"));
            assertEquals(List.of(),texts(at(result,"return-s0",PREFIX)));
        }
    }
    @Test void distinctBasesNeedNoNegativePremisesOrCrossBaseEvents() {
        for(var size:List.of(new int[]{4,5},new int[]{16,50},new int[]{32,100})) {
            var p=RegionalExplosionFixturesTest.fixture(size[0],size[1],false);
            var effects=new StatementEffects(new StorageIndex(session(p)));
            assertEquals(size[1],effects.preparationMetrics().get("targetsPrepared"));
            assertEquals(0L,effects.preparationMetrics().get("baseComparisons"));
            var result=run(p);assertEquals((long)size[0],result.preparationMetrics().get("basesIndexed"));
            assertEquals((long)size[1],result.preparationMetrics().get("eventsPrepared"));
            assertEquals(List.of("ABCDEFGH"),texts(at(result,"return-s0",RegionalExplosionFixturesTest.object(0))));
            assertTrue(result.solveMetrics().get("physicalWritesApplied")>0);
        }
    }
    @Test void regionalDiagnosticMutationCannotChangeCopyCapturesSupportsOrWork() {
        var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"seed",WHOLE,"ABCDEFGH"),copy("copy",R,0,Y,0,8),assign(U,"later",WHOLE,"XXXXXXXX")))));
        var query=new PointQuery<StorageSubject>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),new StorageSubject.NamedObject(YWHOLE));
        var baseline=run(p);var value=baseline.observeStorage(List.of(query)).observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("ABCDEFGH")),value.candidates());assertFalse(value.effectiveUnknownRemainder());
        for(int count:List.of(1,50)) {
            var result=run(diagnostics(p,count));var observed=result.observeStorage(List.of(query)).observations().getFirst().value();
            assertEquals(value.candidates(),observed.candidates());assertEquals(value.alternatives(),observed.alternatives());
            assertEquals(value.candidateSupports(),observed.candidateSupports());assertEquals(value.evidence(),observed.evidence());
            assertEquals(value.modelValueRemainder(),observed.modelValueRemainder());assertFalse(observed.effectiveUnknownRemainder());assertTrue(observed.sourceUnknownRemainder());
            assertEquals(baseline.preparationMetrics(),result.preparationMetrics());assertEquals(baseline.solveMetrics(),result.solveMetrics());
        }
    }
    @Test void scalarDiagnosticMutationKeepsClosedModelAndSupports() {
        var p=linear(1,2,1,1);var u=p.units().getFirst();
        var q=new PointQuery<ObjectId>(ProgramPoint.before(u.entries().getFirst().id(),u.sequences().getFirst().terminator().header().id()),u.objects().getFirst().id());
        var baseline=execute(p);var value=baseline.observe(List.of(q)).batch().observations().getFirst().value();
        for(int count:List.of(1,50)) {
            var result=execute(diagnostics(p,count));var observed=result.observe(List.of(q)).batch().observations().getFirst().value();
            assertEquals(value.candidates(),observed.candidates());assertEquals(value.candidateSupports(),observed.candidateSupports());
            assertTrue(observed.sourceUnknownRemainder());assertFalse(observed.effectiveUnknownRemainder());
            assertEquals(baseline.preparationMetrics(),result.preparationMetrics());assertEquals(baseline.solveMetrics(),result.solveMetrics());
        }
    }
}
