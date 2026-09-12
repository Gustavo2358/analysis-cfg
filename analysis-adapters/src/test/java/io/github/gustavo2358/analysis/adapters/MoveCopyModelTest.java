package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

class MoveCopyModelTest {
    static Publication diamond(boolean open) {
        var p = W2dModelTest.diamond(open,true); var u = p.units().getFirst();
        var storage = W1dModelTest.model(2,id -> List.of(returning(id,"start",List.of())));
        var a = new ObjectId(u.id(),"object-0"); var b = new ObjectId(u.id(),"object-1"); var h = header(u.id(),"copy");
        var copy = new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"target",Operand.Role.VALUE_WRITE),b),
            new Expressions.Read(operand(h.id(),"value",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),a)));
        var seq = u.sequences().stream().map(s -> !s.label().localId().equals("call") ? s :
            new Sequence(s.label(),List.<Instruction>of(copy),W1dModelTest.call(u.id(),"invoke","end",b,true),s.origin())).toList();
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),
            List.of(unit(u.id(),u.entries(),seq,storage.units().getFirst().objects())),storage.storage(),p.resources(),p.artifactRelations(),
            p.origins(),p.coverage(),p.uncertainties(),storage.premises());
    }
    static DependencyResult oracle(Publication p, boolean open) {
        var result = new DependencyAnalysis().prepare(p); assertEquals(1,result.sites().size());
        var f = result.sites().getFirst(); var u = p.units().getFirst();
        var names = open ? List.of("PROGA") : List.of("PROGA","PROGB");
        assertEquals(names,f.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertEquals(names.stream().map(n -> n + "   ").toList(),f.rawCandidates().stream().map(DependencySiteFact.RawCandidate::rawValue).toList());
        assertEquals(open,f.modelValueRemainder()); assertFalse(f.sourceValueRemainder());
        assertEquals(new ObjectId(u.id(),"object-1"),f.subject());
        assertEquals(ProgramPoint.before(u.entries().getFirst().id(),new OperationId(u.id(),"invoke")),f.valuePoint());
        assertEquals(1,f.offset()); assertEquals(names.size(),result.edges().size());
        for(int i=0;i<names.size();i++) {
            var producer = i==0 ? "assign-a" : "assign-b";
            assertEquals(List.of(producer),f.candidates().get(i).supports().stream().map(s -> s.producer().localId()).toList());
            assertEquals(new OriginId(p.id(),producer),f.candidates().get(i).supports().getFirst().origin());
        }
        assertEquals(1L,result.metrics().get("possibleValuesRuns")); return result;
    }
    @Test void branchClosedCopyResolvesBothCandidatesWithSpecificSupports() { oracle(diamond(false),false); }
    @Test void branchOpenCopyRetainsCandidateAndModelRemainder() { oracle(diamond(true),true); }
    @Test void copyResultsIgnoreUnreachableBlockAndPhysicalSequenceOrder() {
        for (boolean open : List.of(false,true)) {
            var p = diamond(open); var expected = oracle(p,open); var sequences = new ArrayList<>(p.units().getFirst().sequences());
            Collections.reverse(sequences);
            var actual = oracle(W1dEffectsTest.sequences(p,sequences,p.units().getFirst().entries()),open);
            assertEquals(expected.sites(),actual.sites()); assertEquals(expected.edges(),actual.edges());
        }
    }
}
