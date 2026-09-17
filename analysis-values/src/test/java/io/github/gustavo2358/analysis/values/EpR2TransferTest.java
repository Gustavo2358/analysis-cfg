package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

class EpR2TransferTest {
    @Test void mayBeforeAndAfterExactWriteCannotEraseItsSupportedValue() {
        var before=new Operations.HavocMay(header(U,"may-before"),new Scopes.AllMemory(P,true),RegionalTransferTest.UNKNOWN);
        var after=new Operations.HavocMay(header(U,"may-after"),new Scopes.AllMemory(P,true),RegionalTransferTest.UNKNOWN);
        var p=RegionalTransferTest.uncertainty(regional(List.of(returning(U,"s0",List.of(before,assign(U,"must",WHOLE,"PROG0001"),after)))));
        var ex=run(p);assertEquals(List.of("PROG0001"),texts(at(ex,"may-after",WHOLE)));
        assertFalse(at(ex,"may-after",WHOLE).modelValueRemainder());
        var finalValue=at(ex,"return-s0",WHOLE);assertEquals(List.of("PROG0001"),texts(finalValue));assertTrue(finalValue.modelValueRemainder());
        assertEquals(List.of("must"),finalValue.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
    }
    @Test void normalCallResultKillsOnlyAfterTheBeforeCaptureAndKeepsOpenAliasPeers() {
        var original=ExternalStorageScopeTest.call(new Scopes.ObjectsMemory(List.of(WHOLE)),false,true,false);var h=original.header();
        var resultPlace=new Places.ObjectPlace(operand(h.id(),"result",Operand.Role.RESULT_TARGET),WHOLE);
        var signature=new Interactions.Signature(new Interactions.ParameterInventory(List.of(),Interactions.NoRemainder.INSTANCE),
            new Interactions.ResultInventory(List.of(new Interactions.ResultSlot(BigInteger.ZERO,Types.known(Types.Builtin.TEXT),origin(P))),Interactions.NoRemainder.INSTANCE),origin(P));
        var call=new Operations.Invoke(h,original.action(),original.target(),original.arguments(),List.of(resultPlace),new Interactions.ExternalSignature(signature),original.effectOperands(),new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,Scopes.NoMemory.INSTANCE,List.of()),List.of()),original.outcomes(),original.contract());
        var p=ExternalStorageScopeTest.program(call);p=replace(p,p.units(),p.coverage(),p.uncertainties(),List.of());
        var ex=run(p);assertEquals(List.of("PGM00001"),texts(at(ex,"external",WHOLE)));
        assertEquals(List.of(),texts(at(ex,"return-done",WHOLE)));assertTrue(at(ex,"return-done",WHOLE).modelValueRemainder());
        var peer=at(ex,"return-done",RegionalCompositionTest.YWHOLE);assertEquals(List.of("OTHERPGM"),texts(peer));assertTrue(peer.modelValueRemainder());
        var outcome=new PointQuery<>(new ProgramPoint(new EntryId(U,"entry"),ProgramPoint.Kind.OUTCOME,h.id(),Control.NormalOutcome.INSTANCE),WHOLE);
        assertEquals(List.of(),ex.observe(List.of(outcome)).observations().getFirst().value().candidates());
    }
}
