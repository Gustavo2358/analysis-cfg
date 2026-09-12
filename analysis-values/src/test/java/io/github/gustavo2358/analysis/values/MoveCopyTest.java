package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;

class MoveCopyTest {
    static Operations.Assign copy(UnitId u, String id, ObjectId source, ObjectId target) {
        var h = header(u, id);
        return new Operations.Assign(h, new Places.ObjectPlace(operand(h.id(), "target", Operand.Role.VALUE_WRITE), target),
            new Expressions.Read(operand(h.id(), "read", Operand.Role.VALUE_READ),
                new Places.ObjectPlace(operand(h.id(), "source", Operand.Role.VALUE_READ), source)));
    }
    static Publication linear(java.util.function.Function<UnitId, List<Instruction>> operations) {
        var p = graph(new String[]{null}, new int[][]{{}}, 3, true, true); var u = p.units().getFirst();
        return replace(p, List.of(unit(u.id(), u.entries(), List.of(returning(u.id(), "s0", operations.apply(u.id()))), u.objects())),
            p.coverage(), p.uncertainties(), p.premises());
    }
    static ObjectId object(UnitId u, int n) { return new ObjectId(u, "object-" + n); }
    @Test void copyOverwritesAndRetainsSnapshotAndOriginalSupport() {
        var p = linear(u -> List.of(assign(u,"old",object(u,1),"OLDPROG "), assign(u,"producer",object(u,0),"PROGA   "),
            copy(u,"copy",object(u,0),object(u,1)), assign(u,"later",object(u,0),"PROGB   ")));
        var run = execute(p); var answer = fact(run,before(p,0,1));
        expected(answer,false,"PROGA   "); expected(fact(run,before(p,0,0)),false,"PROGB   ");
        assertEquals(List.of(new OperationId(p.units().getFirst().id(),"producer")),answer.evidence());
        assertEquals(1,answer.candidateSupports().size());
    }
    @Test void missingSourceStronglyReplacesKnownDestinationWithOpenEmpty() {
        var p = linear(u -> List.of(assign(u,"old",object(u,1),"OLDPROG "),copy(u,"copy",object(u,0),object(u,1))));
        expected(fact(execute(p),before(p,0,1)),true);
    }
    @Test void repeatedForwardCopiesHaveNoHopLimit() {
        var p = linear(u -> {
            var list = new ArrayList<Instruction>(); list.add(assign(u,"producer",object(u,0),"PROGA   "));
            for (int i=0;i<128;i++) list.add(copy(u,"copy-"+i,object(u,i%2),object(u,(i+1)%2)));
            list.add(copy(u,"last",object(u,0),object(u,2))); return list;
        });
        var answer = fact(execute(p),before(p,0,2)); expected(answer,false,"PROGA   ");
        assertEquals(List.of(new OperationId(p.units().getFirst().id(),"producer")),answer.evidence());
    }
    @Test void copiesDoNotCreateStorageIndependence() {
        var p = linear(u -> List.of(copy(u,"copy",object(u,0),object(u,1))));
        var missing = replace(p,p.units(),p.coverage(),p.uncertainties(),List.of());
        var admission = PossibleValuesAnalysis.prepare(session(missing));
        assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,admission.status());
        assertEquals("UNSUPPORTED_STORAGE_DISJOINTNESS",admission.reason());
    }
}
