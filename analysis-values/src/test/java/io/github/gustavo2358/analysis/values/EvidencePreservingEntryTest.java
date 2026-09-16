package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** EP: remove physical proof without removing independent logical support. */
class EvidencePreservingEntryTest {
    static Publication logical(List<Sequence> sequences) {
        var p=PossibleEntryTest.possible(sequences,"PGM00001");var u=p.units().getFirst();var e=u.entries().getFirst();
        var before=e.state().conditions().getFirst();var old=(Entries.PossibleLiterals)before.value();
        var value=new Expressions.Literal(old.candidates().getFirst().header(),new Values.TextValue("PGM00001"));
        var condition=new Entries.InitialCondition(new Places.ObjectPlace(before.place().header(),WHOLE),new Entries.PossibleLiterals(List.of(value),PossibleEntryTest.GAP),before.origin(),List.of());
        var entry=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(List.of(condition),List.of()),e.origin());
        var objects=u.objects().stream().map(o->o.id().equals(WHOLE)?new Memory.ObjectDeclaration(o.id(),o.displayName(),o.typeRef(),
            new Memory.UnknownBinding(new Scopes.AllMemory(P,true),PossibleEntryTest.GAP),Memory.Visibility.UNKNOWN,o.origin(),Evidence.CoverageStatus.ABSTRACTED,o.precision()):o).toList();
        var required=new ArrayList<>(p.capabilities().required());required.remove(Capabilities.ENTRY_POSSIBILITIES);required.add(new Capabilities.Capability("entry.possibilities","2"));
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(required,List.of()),p.artifacts(),List.of(unit(u.id(),List.of(entry),u.sequences(),objects)),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void unknownBindingPreservesEntrySupportInBothAnalyses() {
        var p=logical(List.of(returning(U,"s0",List.of())));
        var values=at(run(p),"return-s0",WHOLE);
        assertEquals(List.of("PGM00001"),texts(values));assertTrue(values.modelValueRemainder());
        assertFalse(values.candidateSupports().getFirst().producers().isEmpty());
        var rd=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
        var facts=rd.observe(List.of(new PointQuery<>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),WHOLE))).observations().getFirst().value();
        assertTrue(facts.unknownRemainder());assertTrue(facts.definitions().stream().anyMatch(d->d.definition().kind()==DefinitionEvent.Kind.ENTRY_POSSIBILITY));
        assertTrue(facts.definitions().stream().allMatch(d->d.contributedRanges().isEmpty()),"logical evidence must not invent physical ranges");
    }
    @Test void unprovedBindingNeverPromotesAssignmentToStrongKill() {
        var p=logical(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"OTHERPGM")))));
        var value=at(run(p),"return-s0",WHOLE);
        assertEquals(List.of("OTHERPGM","PGM00001"),texts(value));assertTrue(value.modelValueRemainder());
    }
    @Test void genericUnknownEffectCannotClearLogicalEvidence() {
        var h=header(U,"arbitrary");var gap=PossibleEntryTest.GAP;
        var havoc=new Operations.HavocMay(h,new Scopes.AllMemory(P,true),gap);
        var value=at(run(logical(List.of(returning(U,"s0",List.of(havoc))))),"return-s0",WHOLE);
        assertEquals(List.of("PGM00001"),texts(value));assertTrue(value.modelValueRemainder());
    }
}
