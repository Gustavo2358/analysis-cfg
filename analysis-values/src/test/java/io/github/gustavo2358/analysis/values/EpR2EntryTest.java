package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.EpR2Fixtures.*;

class EpR2EntryTest {
    static AnalysisSession session(Publication p) {
        var validation=AirValidator.validate(p);
        assertNotEquals(ValidationResult.Status.INVALID_IR,validation.status(),validation.issues().toString());
        var options=BuildOptions.defaults();
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,options);
        return AnalysisSession.open(cfg,p,options.projectionPolicy(),p.units().getFirst().entries()).session().orElseThrow();
    }
    static RegionalValuesAnalysis analysis(Publication p) {
        var s=session(p);var rd=ReachingDefinitions.prepare(new StatementEffects(new StorageIndex(s)));
        assertEquals(ReachingDefinitions.Status.ACCEPTED,rd.status(),rd.reason());
        var rv=RegionalValuesAnalysis.prepare(s);assertEquals(RegionalValuesAnalysis.Status.ACCEPTED,rv.status(),rv.reason());
        return rv.analysis().orElseThrow();
    }
    static RegionalValueFact at(RegionalValuesAnalysis.Execution execution,int object) {
        return execution.observe(List.of(new PointQuery<>(ProgramPoint.before(E,new OperationId(U,"call-0")),obj(object)))).observations().getFirst().value();
    }
    static Publication conditions(Publication p,List<Entries.InitialCondition> cc) {
        var u=p.units().getFirst();var e=u.entries().getFirst();
        var ee=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(cc,e.state().uncertainties()),e.origin());
        var uu=new Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),List.of(ee),u.sequences(),u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(uu),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void openExternalPairIsAdmittedAndRemainsUnknown() {
        var ex=analysis(fixture(2,0,2,0,0,false,true,"control",false)).execute();
        for(int i=0;i<2;i++){assertTrue(at(ex,i).modelValueRemainder());assertTrue(at(ex,i).candidates().isEmpty());}
    }
    @Test void allSixSimultaneousOrdersPreservePossibleSupport() {
        var p=fixture(3,1,2,0,0,false,true,"control",false);var c=p.units().getFirst().entries().getFirst().state().conditions();
        RegionalValueFact expected=null;
        for(var order:List.of(List.of(0,1,2),List.of(0,2,1),List.of(1,0,2),List.of(1,2,0),List.of(2,0,1),List.of(2,1,0))) {
            var fact=at(analysis(conditions(p,order.stream().map(c::get).toList())).execute(),0);
            assertEquals(List.of(new Values.TextValue("PROG0000")),fact.candidates());assertTrue(fact.modelValueRemainder());
            assertFalse(fact.candidateSupports().getFirst().producers().isEmpty());
            if(expected!=null)assertEquals(expected,fact);expected=fact;
        }
    }
    @Test void h2StrongLiteralSurvivesOpenExternalEntryWithoutWrites() {
        var p=fixture(3,0,2,1,0,false,true,"control",false);
        var fact=at(analysis(p).execute(),2);
        assertEquals(List.of(new Values.TextValue("PROG0002")),fact.candidates());assertTrue(fact.modelValueRemainder());
        assertEquals("place-2",fact.candidateSupports().getFirst().producers().getFirst().evidence().localId());
    }
    @Test void realStrongContradictionStillFailsI17() {
        var result=AirValidator.validate(fixture(2,0,0,2,0,false,true,"control",true));
        assertEquals(ValidationResult.Status.INVALID_IR,result.status());assertTrue(result.issues().toString().contains("I-17"));
    }
    @Test void externalUnknownOnTheSamePlaceIsNotAFakeStrongAssignment() {
        var p=fixture(2,0,1,1,0,false,true,"control",false);var c=p.units().getFirst().entries().getFirst().state().conditions();
        var open=c.getFirst();var place=(Places.ObjectPlace)open.place();
        var same=new Entries.InitialCondition(new Places.ObjectPlace(place.header(),obj(1)),open.value(),open.origin(),open.premises());
        for(var order:List.of(List.of(same,c.getLast()),List.of(c.getLast(),same))) {
            var fact=at(analysis(conditions(p,order)).execute(),1);
            assertEquals(List.of(new Values.TextValue("PROG0001")),fact.candidates());assertTrue(fact.modelValueRemainder());
            assertFalse(fact.candidateSupports().getFirst().producers().isEmpty());
        }
    }
    @Test void mixedPairsAreIndependentOfEntryOrder() {
        for(int[] kinds:List.of(new int[]{0,2,0},new int[]{1,1,0},new int[]{2,0,0},new int[]{0,1,1})) {
            var p=fixture(2,kinds[0],kinds[1],kinds[2],0,false,true,"control",false);
            var c=p.units().getFirst().entries().getFirst().state().conditions();
            var a=analysis(p).execute();var b=analysis(conditions(p,List.of(c.getLast(),c.getFirst()))).execute();
            for(int i=0;i<2;i++) {
                assertEquals(at(a,i),at(b,i));assertTrue(at(a,i).modelValueRemainder());
                if(i<kinds[0]||i>=kinds[0]+kinds[1]) {
                    assertEquals(List.of(new Values.TextValue(String.format(java.util.Locale.ROOT,"PROG%04d",i))),at(a,i).candidates());
                    assertFalse(at(a,i).candidateSupports().getFirst().producers().isEmpty());
                }
            }
        }
    }
}
