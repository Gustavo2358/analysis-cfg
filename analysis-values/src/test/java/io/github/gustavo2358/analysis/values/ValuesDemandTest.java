package io.github.gustavo2358.analysis.values;

import java.util.*;
import io.github.gustavo2358.air.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;

class ValuesDemandTest {
    @Test void copiesCloseDemandOverSourcesAndCaptureBeforeLaterWrites() {
        var p=ValuesScaleTest.longSequence(3,3,true,true);var u=p.units().getFirst();var s=u.sequences().getFirst();
        var h=header(u.id(),"copy");var target=u.objects().get(0).id();var source=u.objects().get(1).id();
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),source));
        var writes=new ArrayList<Instruction>(s.instructions());
        writes.add(new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),target),read));
        writes.add(assign(u.id(),"later-source",source,"AFTER"));
        var changed=replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),writes,s.terminator(),s.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
        var run=PossibleValuesAnalysis.prepare(session(changed),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(target)).analysis().orElseThrow().execute();
        expected(fact(run,before(changed,0,0)),false,"v1");assertEquals(2L,run.preparationMetrics().get("demandCellsPrepared"));
    }
    @Test void sameCellAliasesRemainRelevantAndIndependentBasesAreAdmitted() {
        var p=ValuesScaleTest.longSequence(3,3,false,true);var query=before(p,0,2);
        var run=PossibleValuesAnalysis.prepare(session(p),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(query.subject())).analysis().orElseThrow().execute();
        expected(fact(run,query),false,"v2");assertEquals(3L,run.preparationMetrics().get("demandWritesPrepared"));
        var unproved=graph(new String[]{"A"},new int[][]{{}},3,true,false);
        assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,PossibleValuesAnalysis.prepare(session(unproved),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(unproved.units().getFirst().objects().getFirst().id())).status());
    }
    @Test void irrelevantWritesDoNotPrepareCandidatesOrDetailedState() {
        for(int n:new int[]{10,100,1000}) {
            var p=ValuesScaleTest.longSequence(n,n,true,true);var query=before(p,0,0);
            var a=PossibleValuesAnalysis.prepare(session(p),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(query.subject()));
            assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,a.status());var run=a.analysis().orElseThrow().execute();
            expected(fact(run,query),false,"v0");
            assertEquals(1L,run.preparationMetrics().get("demandCellsPrepared"));
            assertEquals(1L,run.preparationMetrics().get("producersPrepared"));
            assertEquals(1L,run.solveMetrics().get("maxSparseBindings"));
        }
    }
}
