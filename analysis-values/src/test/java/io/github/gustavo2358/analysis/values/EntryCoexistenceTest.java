package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

class EntryCoexistenceTest {
    static Publication conditions(Publication p,List<Entries.InitialCondition> conditions) {
        var u=p.units().getFirst();var e=u.entries().getFirst();
        var entry=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(conditions,e.state().uncertainties()),e.origin());
        var required=new ArrayList<>(p.capabilities().required());required.remove(Capabilities.ENTRY_POSSIBILITIES);required.add(Capabilities.ENTRY_POSSIBILITIES_V2);
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(required,List.of()),p.artifacts(),List.of(unit(u.id(),List.of(entry),u.sequences(),u.objects())),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void preserveAndExternalUnknownCannotErasePossibleSupportInEitherOrder() {
        var p=PossibleEntryTest.possible(List.of(returning(U,"s0",List.of())),"PGM00001");
        var possible=p.units().getFirst().entries().getFirst().state().conditions().getFirst();
        var place=RegionalInitialTest.seed("unknown-condition",0,"XXXXXXXX").place();
        for(var value:List.<Entries.InitialValue>of(Entries.Preserve.INSTANCE,new Entries.ExternalUnknown(PossibleEntryTest.GAP))) {
            var unknown=new Entries.InitialCondition(place,value,possible.origin(),List.of());
            for(var order:List.of(List.of(possible,unknown),List.of(unknown,possible))) {
                var fact=at(run(conditions(p,order)),"return-s0",WHOLE);
                assertEquals(List.of("PGM00001"),texts(fact));assertTrue(fact.modelValueRemainder());
                assertFalse(fact.candidateSupports().getFirst().producers().isEmpty());
            }
        }
    }
    @Test void multiplePossibleContributionsUnionTheirOwnSupportInEitherOrder() {
        var p=PossibleEntryTest.possible(List.of(returning(U,"s0",List.of())),"PGM00001");
        var first=p.units().getFirst().entries().getFirst().state().conditions().getFirst();
        var seed=RegionalInitialTest.seed("second-condition",0,"PGM00002");
        var second=new Entries.InitialCondition(seed.place(),new Entries.PossibleLiterals(List.of(((Entries.LiteralInitial)seed.value()).value()),PossibleEntryTest.GAP),seed.origin(),List.of());
        for(var order:List.of(List.of(first,second),List.of(second,first))) {
            var fact=at(run(conditions(p,order)),"return-s0",WHOLE);
            assertEquals(List.of("PGM00001","PGM00002"),texts(fact));assertTrue(fact.modelValueRemainder());
            assertEquals(2,fact.candidateSupports().size());
        }
    }
    @Test void unprovedSeparationBetweenPossibleEntriesDoesNotRejectEitherObject() {
        var p=PossibleEntryTest.possible(List.of(returning(U,"s0",List.of())),"PGM00001");
        var base=RegionalCompositionTest.twoBases(p.units().getFirst().sequences());
        var first=p.units().getFirst().entries().getFirst().state().conditions().getFirst();
        var seed=RegionalInitialTest.seed("other-base",0,"PGM00002");var slice=(Places.RegionSlice)seed.place();
        var place=new Places.RegionSlice(slice.header(),RegionalCompositionTest.Y,slice.offset(),slice.length(),slice.codec(),slice.typeRef());
        var second=new Entries.InitialCondition(place,new Entries.PossibleLiterals(List.of(((Entries.LiteralInitial)seed.value()).value()),PossibleEntryTest.GAP),seed.origin(),List.of());
        var combined=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),base.units(),base.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),List.of());
        for(var order:List.of(List.of(first,second),List.of(second,first))) {
            var execution=run(conditions(combined,order));
            assertEquals(List.of("PGM00001"),texts(at(execution,"return-s0",WHOLE)));
            assertEquals(List.of("PGM00002"),texts(at(execution,"return-s0",RegionalCompositionTest.YWHOLE)));
            assertTrue(at(execution,"return-s0",WHOLE).modelValueRemainder());
        }
    }
    @Test void scalarAndRegionalCoexistenceAgreeWithoutOrderBasedKill() {
        var p=graph(new String[]{null},new int[][]{{}},1,false,false);
        var u=p.units().getFirst();var e=u.entries().getFirst();var owner=new EntryOwner(e.id());var origin=e.origin();
        var gap=new UncertaintyId(p.id(),"lifecycle");
        var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"source-place"),Operand.Role.VALUE_WRITE,origin),u.objects().getFirst().id());
        var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"source-value"),Operand.Role.VALUE_READ,origin),new Values.TextValue("PROGA"));
        var possible=new Entries.InitialCondition(place,new Entries.PossibleLiterals(List.of(literal),gap),origin,List.of());
        var otherPlace=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"preserve-place"),Operand.Role.VALUE_WRITE,origin),u.objects().getFirst().id());
        var preserve=new Entries.InitialCondition(otherPlace,new Entries.ExternalUnknown(gap),origin,List.of());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),
            List.of(new Evidence.Uncertainty(gap,"EP_PARTIAL",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"possible boundary",origin)),p.premises());
        for(var order:List.of(List.of(possible,preserve),List.of(preserve,possible))) {
            var combined=conditions(p,order);var query=ValuesTest.before(combined,0,0);
            ValuesTest.expected(ValuesTest.fact(execute(combined),query),true,"PROGA");
            assertEquals(List.of("PROGA"),texts(run(combined).observe(List.of(query)).observations().getFirst().value()));
        }
    }

}
