package io.github.gustavo2358.analysis.values;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;
import static io.github.gustavo2358.analysis.values.RegionalTransferTest.*;
class ExternalStorageScopeTest {
    static Operations.Invoke call(Scopes.MemoryScope scope,boolean must,boolean normal,boolean perOutcome) {
        var h=header(U,"external");var place=new Places.ObjectPlace(operand(h.id(),"effect",Operand.Role.VALUE_WRITE),PREFIX);
        var read=new Expressions.Read(operand(h.id(),"target",Operand.Role.CALL_TARGET),new Places.ObjectPlace(operand(h.id(),"read",Operand.Role.VALUE_READ),WHOLE));
        var effects=new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,new Scopes.WithinMemory(scope),must?List.of(place.header().id()):List.of());
        var otherwise=perOutcome?new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,new Scopes.WithinMemory(new Scopes.AllMemory(P,true)),List.of()):effects;
        return new Operations.Invoke(h,"manual-external",new Interactions.ComputedTarget("program","manual",read,Interactions.ExactName.INSTANCE,origin(P)),List.of(),List.of(),
            new Interactions.ExternalSignature(entry(U,"entry","s0").signature()),must?List.of(place):List.of(),
            new Interactions.EffectBound(otherwise,perOutcome?List.of(new Interactions.OutcomeEffects(Control.NormalOutcome.INSTANCE,effects)):List.of()),
            new Control.InvocationOutcomes(normal?List.of(new Control.Normal(new LabelId(U,"done"))):List.of(),normal?Scopes.NoControl.INSTANCE:new Scopes.WithinControl(new Scopes.LabelsControl(List.of(new LabelId(U,"sink"))))),
            new Interactions.KnownContract(new Interactions.ContractRef("manual-scope","1",List.of(origin(P)))));
    }
    static Publication program(Operations.Invoke invoke) {
        return twoBases(List.of(new Sequence(new LabelId(U,"s0"),List.of(assign(U,"target-value",WHOLE,"PGM00001"),assign(U,"other-value",YWHOLE,"OTHERPGM")),invoke,origin(P)),returning(U,"done",List.of()),jump(U,"sink","sink")));
    }
    @Test void namedUnionMayAffectsOnlyProvedPrefixAndKeepsOldCandidate() {
        var scope=new Scopes.MemoryUnion(List.of(new Scopes.ObjectsMemory(List.of(PREFIX)),new Scopes.ObjectsMemory(List.of(PREFIX))));
        var execution=run(program(call(scope,false,true,false)));
        assertEquals(List.of("PGM00001"),texts(at(execution,"external",WHOLE)));assertFalse(at(execution,"external",WHOLE).modelValueRemainder());
        assertEquals(List.of("PGM00001"),texts(at(execution,"return-done",WHOLE)));assertTrue(at(execution,"return-done",WHOLE).modelValueRemainder());
        assertEquals(List.of("0001"),texts(at(execution,"return-done",SUFFIX)));assertFalse(at(execution,"return-done",SUFFIX).modelValueRemainder());
        assertEquals(List.of("OTHERPGM"),texts(at(execution,"return-done",YWHOLE)));assertFalse(at(execution,"return-done",YWHOLE).modelValueRemainder());
    }
    @Test void privateStorageIsIncludedByAnExplicitAllMemoryEffect() {
        var execution=run(program(call(new Scopes.AllMemory(P,true),false,true,false)));
        for(var object:List.of(WHOLE,YWHOLE))assertTrue(at(execution,"return-done",object).modelValueRemainder());
        assertEquals(List.of("PGM00001"),texts(at(execution,"external",WHOLE)));
    }
    @Test void normalOutcomeMustOverridesBroadOtherwiseAndKillsOnlyItsRange() {
        var invoke=call(new Scopes.ObjectsMemory(List.of(PREFIX)),true,true,true);var execution=run(program(invoke));
        var after=at(execution,"return-done",WHOLE);assertEquals(List.of(),texts(after));assertTrue(after.modelValueRemainder());
        assertEquals(List.of("0001"),texts(at(execution,"return-done",SUFFIX)));assertFalse(at(execution,"return-done",SUFFIX).modelValueRemainder());
        assertEquals(List.of("OTHERPGM"),texts(at(execution,"return-done",YWHOLE)));assertFalse(at(execution,"return-done",YWHOLE).modelValueRemainder());
        var q=new PointQuery<>(new ProgramPoint(new EntryId(U,"entry"),ProgramPoint.Kind.OUTCOME,invoke.header().id(),Control.NormalOutcome.INSTANCE),WHOLE);
        var outcome=execution.observe(List.of(q)).observations().getFirst().value();assertEquals(after.candidates(),outcome.candidates());assertEquals(after.candidateSupports(),outcome.candidateSupports());
    }
    @Test void noNormalOutcomeCannotInventContinuationOrEraseTheBeforeRead() {
        var execution=run(program(call(new Scopes.AllMemory(P,true),false,false,false)));
        assertEquals(List.of("PGM00001"),texts(at(execution,"external",WHOLE)));assertFalse(at(execution,"external",WHOLE).modelValueRemainder());
        assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,at(execution,"return-done",WHOLE).reachability());
    }
    @Test void explicitDivergenceKeepsTheExistingUnsupportedControlBoundary() {
        var original=call(new Scopes.AllMemory(P,true),false,true,false);
        var divergent=new Operations.Invoke(original.header(),original.action(),original.target(),original.arguments(),original.results(),original.signature(),original.effectOperands(),original.effectBound(),
            new Control.InvocationOutcomes(List.of(Control.Diverge.INSTANCE),Scopes.NoControl.INSTANCE),original.contract());
        var result=new io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator(io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry.empty())
            .build(program(divergent),io.github.gustavo2358.analysis.cfg.application.BuildOptions.defaults());
        assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.UNSUPPORTED_INPUT,result.status());assertTrue(result.graph().isEmpty());
    }
    @Test void aliasScopeUsesPhysicalIntersection() {
        var alias=new ObjectId(U,"prefix-alias");var may=new Operations.HavocMay(header(U,"alias-havoc"),new Scopes.MemoryUnion(List.of(new Scopes.ObjectsMemory(List.of(alias)))),UNKNOWN);
        var p=uncertainty(twoBases(List.of(returning(U,"s0",List.of(assign(U,"original",WHOLE,"PGM00001"),assign(U,"other",YWHOLE,"OTHERPGM"),may)))));
        var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());objects.add(new Memory.ObjectDeclaration(alias,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.AliasBinding(PREFIX),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"a").precision()));
        p=replace(p,List.of(unit(U,u.entries(),u.sequences(),objects)),p.coverage(),p.uncertainties(),p.premises());
        var execution=run(p);assertTrue(at(execution,"return-s0",WHOLE).modelValueRemainder());assertFalse(at(execution,"return-s0",SUFFIX).modelValueRemainder());assertFalse(at(execution,"return-s0",YWHOLE).modelValueRemainder());
    }
}
