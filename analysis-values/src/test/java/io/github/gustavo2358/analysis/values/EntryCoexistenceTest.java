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
}
