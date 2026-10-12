package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public decision-only contract: no execution or global cross-session cache. */
final class ScalarAdmissionDecisionTest {
    @Test void selectiveExpressionRefusalCannotDowngradeIndependentCells() {
        var p=mixed();var s=session(p);var objects=p.units().getFirst().objects();
        for(var profile:List.of(PossibleValuesAnalysis.PROFILE,PossibleValuesAnalysis.EFFECTS_PROFILE))
            try(var decisions=PossibleValuesAnalysis.admissionDecisions(s,profile)) {
                for(int at:new int[]{2,1,0,1,2,0}) {
                    var actual=decisions.decide(objects.get(at).id());
                    assertEquals(at==1?PossibleValuesAnalysis.Status.UNSUPPORTED:PossibleValuesAnalysis.Status.ACCEPTED,actual.status());
                    assertEquals(at==1?"UNSUPPORTED_EFFECT_PROFILE":null,actual.reason());
                    assertEquals(0,actual.unsupportedStorageProfiles());assertEquals(at==1?1:0,actual.unsupportedEffectProfiles());
                }
            }
    }
    @Test void storageAndUnknownProfilePrecedeArithmeticAndFailedBuildCanRetry() {
        var p=ScalarAdmissionGuardTest.overflowWithView();var s=session(p);var objects=p.units().getFirst().objects();
        try(var decisions=PossibleValuesAnalysis.admissionDecisions(s,PossibleValuesAnalysis.EFFECTS_PROFILE)) {
            assertEquals("UNSUPPORTED_DEMAND_STORAGE",decisions.decide(objects.get(1).id()).reason());
            for(int root:new int[]{0,2,0})assertThrows(ArithmeticException.class,()->decisions.decide(objects.get(root).id()));
            assertEquals("UNSUPPORTED_DEMAND_STORAGE",decisions.decide(objects.get(1).id()).reason());
        }
        try(var decisions=PossibleValuesAnalysis.admissionDecisions(s,"unsupported-profile")) {
            var refused=decisions.decide(objects.getFirst().id());
            assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,refused.status());
            assertEquals("UNSUPPORTED_EFFECT_PROFILE",refused.reason());assertEquals(0,refused.unsupportedStorageProfiles());assertEquals(1,refused.unsupportedEffectProfiles());
        }
    }
    @Test void identicalIdsInDifferentSessionsCannotShareDecisionsAndCloseIsLocal() {
        var good=graph(new String[]{null},new int[][]{{}},3,true,false);var bad=mixed();
        var object=good.units().getFirst().objects().get(1).id();assertEquals(object,bad.units().getFirst().objects().get(1).id());
        var first=PossibleValuesAnalysis.admissionDecisions(session(good),PossibleValuesAnalysis.EFFECTS_PROFILE);
        try(var second=PossibleValuesAnalysis.admissionDecisions(session(bad),PossibleValuesAnalysis.EFFECTS_PROFILE)) {
            assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,first.decide(object).status());
            assertEquals("UNSUPPORTED_EFFECT_PROFILE",second.decide(object).reason());
            first.close();first.close();assertThrows(IllegalStateException.class,()->first.decide(object));
            assertEquals("UNSUPPORTED_EFFECT_PROFILE",second.decide(object).reason());
        } finally {first.close();}
    }
    @Test void retiringDecisionControlsPreservesCompleteAcceptedPointFacts() {
        var p=graph(new String[]{"PROGA"},new int[][]{{}},3,true,false);var s=session(p);var query=before(p,0,0);
        var baseline=PossibleValuesAnalysis.prepare(s,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(query.subject()));
        ValueFact expected;
        try(var preparation=baseline.analysis().orElseThrow();var run=preparation.execute()){expected=fact(run,query);}
        try(var decisions=PossibleValuesAnalysis.admissionDecisions(s,PossibleValuesAnalysis.EFFECTS_PROFILE)) {
            assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,decisions.decide(query.subject()).status());
        }
        var admitted=PossibleValuesAnalysis.prepare(s,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(query.subject()));
        try(var preparation=admitted.analysis().orElseThrow();var run=preparation.execute()) {
            var actual=fact(run,query);assertEquals(expected,actual,"entire detached ValueFact, including supports/premises/provenance");
            expected(actual,false,"PROGA");assertFalse(actual.sourceUnknownRemainder());assertFalse(actual.effectiveUnknownRemainder());
            var producer=p.units().getFirst().sequences().getFirst().instructions().getFirst().header();
            assertEquals(List.of(new ValueFact.CandidateSupport(new Values.TextValue("PROGA"),List.of(new ValueFact.Support(producer.id(),producer.origin(),List.of())))),actual.candidateSupports());
        }
    }
    @Test void selectedEntriesWithTheSameIndexKeepDistinctInitialConstraints() {
        var p=graph(new String[]{null},new int[][]{{}},2,true,false);var u=p.units().getFirst();
        var objects=new ArrayList<>(u.objects());var object=objects.get(1);var type=Types.known(Types.Builtin.INT);
        objects.set(1,new Memory.ObjectDeclaration(object.id(),object.displayName(),type,object.storage(),object.visibility(),object.origin(),object.coverage(),object.precision()));
        var storage=new ArrayList<>(p.storage());var cell=(Memory.Cell)storage.get(1);storage.set(1,new Memory.Cell(cell.header(),type));
        var first=u.entries().getFirst();var second=entry(u.id(),"integer-entry",first.initialLabel().orElseThrow().localId());
        var owner=new EntryOwner(second.id());var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"initial-place"),Operand.Role.VALUE_WRITE,second.origin()),object.id());
        var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"initial-value"),Operand.Role.VALUE_READ,second.origin()),new Values.IntValue(BigInteger.ONE));
        second=new Entries.Entry(second.id(),second.initialLabel(),second.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),second.origin(),List.of())),List.of()),second.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),List.of(first,second),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var all=session(p);var good=all.selectEntries(List.of(first.id()));var bad=all.selectEntries(List.of(second.id()));assertSame(good.index(),bad.index());
        for(var profile:List.of(PossibleValuesAnalysis.PROFILE,PossibleValuesAnalysis.EFFECTS_PROFILE)) {
            try(var a=PossibleValuesAnalysis.admissionDecisions(good,profile);var b=PossibleValuesAnalysis.admissionDecisions(bad,profile)) {
                assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,a.decide(object.id()).status());
                assertEquals("UNSUPPORTED_INITIAL_VALUE",b.decide(object.id()).reason());
                assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,b.decide(objects.getFirst().id()).status());
                var original=PossibleValuesAnalysis.prepare(bad,profile,Set.of(object.id()));
                assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,original.status());assertEquals("UNSUPPORTED_INITIAL_VALUE",original.reason());assertTrue(original.analysis().isEmpty());
            }
        }
    }
    private static Publication mixed() {
        var p=graph(new String[]{null},new int[][]{{}},3,true,false);var u=p.units().getFirst();var seq=u.sequences().getFirst();
        var h=header(u.id(),"bare-concat");
        var write=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),u.objects().get(1).id()),
            new Expressions.Binary(operand(h.id(),"concat",Operand.Role.VALUE_READ),Expressions.BinaryOperator.CONCAT,
                new Expressions.Literal(operand(h.id(),"left",Operand.Role.VALUE_READ),new Values.TextValue("A")),
                new Expressions.Literal(operand(h.id(),"right",Operand.Role.VALUE_READ),new Values.TextValue("B"))));
        return replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(seq.label(),List.of(write),seq.terminator(),seq.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
    }
}
