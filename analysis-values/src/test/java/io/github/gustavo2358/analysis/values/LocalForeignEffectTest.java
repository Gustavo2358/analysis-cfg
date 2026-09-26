package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

/** A foreign option write opens only its proved cell, independent of the target. */
class LocalForeignEffectTest {
    private static Publication program(int write) { return program(write,false); }
    private static Publication program(int write,boolean visibleRead) {
        var p=graph(new String[]{null,null},new int[][]{{1},{}},2,true,true);
        var u=p.units().getFirst();var first=u.sequences().getFirst();var second=u.sequences().get(1);
        var a=u.objects().get(0).id();var b=u.objects().get(1).id();
        var h=header(u.id(),"cics-effect");
        var readPlace=new Places.ObjectPlace(operand(h.id(),"commarea",Operand.Role.VALUE_READ),b);
        var writePlace=new Places.ObjectPlace(operand(h.id(),"resp",Operand.Role.VALUE_WRITE),a);
        var readBound=visibleRead?new Scopes.WithinMemory(new Scopes.VisibleMemory(u.id(),true)):
            new Scopes.WithinMemory(new Scopes.ObjectsMemory(List.of(b)));
        Scopes.MemoryBound writeBound=write<0?Scopes.NoMemory.INSTANCE:
            new Scopes.WithinMemory(new Scopes.ObjectsMemory(List.of(write==0?a:b)));
        var invoke=new Operations.Invoke(h,"call",new Interactions.LiteralTarget("program","cics.program","PROGC001",Interactions.ExactName.INSTANCE,origin(p.id())),
            List.of(),List.of(),new Interactions.ExternalSignature(u.entries().getFirst().signature()),List.of(readPlace,writePlace),
            new Interactions.EffectBound(new Interactions.ForeignEffects(readBound,writeBound,List.of()),List.of()),
            new Control.InvocationOutcomes(List.of(new Control.Normal(second.label())),Scopes.NoControl.INSTANCE),
            new Interactions.KnownContract(new Interactions.ContractRef("w8-local-effects","1",List.of(origin(p.id())))));
        var seq=new Sequence(first.label(),List.of(assign(u.id(),"seed-a",a,"PROGA001"),assign(u.id(),"seed-b",b,"PROGB001")),invoke,first.origin());
        return replace(p,List.of(unit(u.id(),u.entries(),List.of(seq,second),u.objects())),p.coverage(),p.uncertainties(),p.premises());
    }
    private static ValueFact at(Publication p,int object) {
        var prepared=PossibleValuesAnalysis.prepare(session(p),PossibleValuesAnalysis.EFFECTS_PROFILE);
        assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,prepared.status(),prepared.reason());
        return ValuesTest.fact(prepared.analysis().orElseThrow().execute(),ValuesTest.before(p,1,object));
    }
    @Test void localizedForeignWriteKeepsIndependentValueAndMutationsAreDetected() {
        var p=program(0);
        ValuesTest.expected(at(p,0),true,"PROGA001");
        ValuesTest.expected(at(p,1),false,"PROGB001");
        var noWrite=program(-1);
        ValuesTest.expected(at(noWrite,0),false,"PROGA001");
        var wrongWrite=program(1);
        ValuesTest.expected(at(wrongWrite,0),false,"PROGA001");
        ValuesTest.expected(at(wrongWrite,1),true,"PROGB001");
        var broadRead=program(-1,true);
        ValuesTest.expected(at(broadRead,0),false,"PROGA001");
        ValuesTest.expected(at(broadRead,1),false,"PROGB001");
    }
}
