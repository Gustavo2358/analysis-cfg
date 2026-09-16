package io.github.gustavo2358.analysis.values;

import java.util.*;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.*;
import io.github.gustavo2358.analysis.structure.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.EpR2Fixtures.*;

class EpR2FactorizationTest {
    @Test void choiceReadProjectsOneSelectedSourceRatherThanTheirCartesianProduct() {
        var p=fixture(4,2,0,0,0,false,true,"control",false);var u=p.units().getFirst();var h=header("choice-copy");
        var sources=new ArrayList<Place>();
        for(int i=0;i<4;i++)sources.add(new Places.ObjectPlace(op(h,"source-"+i,Operand.Role.VALUE_READ),obj(i)));
        var choice=new Places.Choice(op(h,"choice",Operand.Role.VALUE_READ),sources,Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
        var copy=new Operations.Assign(h,new Places.ObjectPlace(op(h,"dest",Operand.Role.VALUE_WRITE),obj(3)),
            new Expressions.FitText(op(h,"fit",Operand.Role.VALUE_READ),new Expressions.Read(op(h,"read",Operand.Role.VALUE_READ),choice),BigInteger.valueOf(8)," "));
        var sequences=new ArrayList<>(u.sequences());var first=sequences.getFirst();sequences.set(0,new Sequence(first.label(),List.of(copy),first.terminator(),first.origin()));
        var unit=new Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),u.entries(),sequences,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION,AirValidator.validate(p).status(),AirValidator.validate(p).issues().toString());
        var defaults=BuildOptions.defaults();
        var options=new BuildOptions(defaults.validation(),ProjectionPolicy.PARTIAL_ANALYSIS);
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,options);
        var session=AnalysisSession.open(cfg,p,options.projectionPolicy(),p.units().getFirst().entries()).session().orElseThrow();
        var ex=RegionalValuesAnalysis.prepare(session).analysis().orElseThrow().execute();var fact=EpR2EntryTest.at(ex,3);
        assertEquals(List.of(new Values.TextValue("PROG0000"),new Values.TextValue("PROG0001")),fact.candidates());
        assertTrue(ex.solveMetrics().get("projectedAlternatives")<=13,"a Choice reads one of four sources, never all four simultaneously");
    }
    /** Structural oracle: count materialized alternatives, not elapsed time. */
    static long alternatives(RegionalValuesAnalysis.State state) throws ReflectiveOperationException {
        return state.materializedAlternatives();
    }
    @Test void connectedEntryFactorsGrowBySum() throws ReflectiveOperationException {
        for(int p:List.of(1,2,4,7)) {
            var publication=fixture(8,p,0,0,7,false,true,"diamonds",false);
            var analysis=EpR2EntryTest.analysis(publication);var engine=analysis.new Engine();
            var sessionField=analysis.getClass().getDeclaredField("session");sessionField.setAccessible(true);
            var session=(AnalysisSession)sessionField.get(analysis);
            var boundary=engine.boundaries(session).iterator().next();
            assertTrue(alternatives(boundary.state())<=8L*(p+1),"G must store component alternatives, not complete worlds: P="+p);
        }
    }
    @Test void independentSlicesWithinOneRegionAreAlsoFactored() throws ReflectiveOperationException {
        var publication=fixture(1,7,0,0,0,false,true,"control",false);
        var analysis=EpR2EntryTest.analysis(publication);var engine=analysis.new Engine();
        var sessionField=analysis.getClass().getDeclaredField("session");sessionField.setAccessible(true);
        var session=(AnalysisSession)sessionField.get(analysis);
        assertTrue(alternatives(engine.boundaries(session).iterator().next().state())<=14,"seven independent slices need fourteen alternatives");
    }
    @Test void emptyEntryWithThirtyTwoOpenWeakCopiesDoesNotBuildWorlds() {
        var ex=EpR2EntryTest.analysis(fixture(32,0,0,0,32,false,true,"copies",false)).execute();
        assertTrue(ex.solveMetrics().get("maxStateAlternatives")<=32L*32,"at most writes × components supported alternatives for this fixture");
        assertTrue(EpR2EntryTest.at(ex,0).candidates().isEmpty());
        assertTrue(EpR2EntryTest.at(ex,0).modelValueRemainder());
        System.out.println("EP_R2_I "+ex.solveMetrics());
    }
    @Test void connectedCopiesAndScaledJoinsPreserveRecall() {
        for(boolean proof:List.of(true,false))for(int p:List.of(1,2,4,7)) {
            var ex=EpR2EntryTest.analysis(fixture(8,p,0,0,1000,proof,!proof,"diamonds",false)).execute();
            var fact=EpR2EntryTest.at(ex,0);
            assertTrue(fact.candidates().contains(new Values.TextValue("PROG0000")));
            assertTrue(fact.modelValueRemainder());assertFalse(fact.candidateSupports().isEmpty());
            assertTrue(ex.solveMetrics().get("boundaryAlternatives")<=8L*(p+1));
            System.out.println("EP_R2_G p="+p+" proof="+proof+" "+ex.solveMetrics());
        }
    }
}
