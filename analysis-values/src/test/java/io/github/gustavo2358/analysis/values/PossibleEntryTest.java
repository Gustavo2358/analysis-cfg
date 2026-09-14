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
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.with;

/** RF laws apply at the invocation boundary, never by union at a query. */
class PossibleEntryTest {
    static final UncertaintyId GAP=new UncertaintyId(P,"lifecycle");
    static Publication possible(List<Sequence> sequences,String... texts) {
        var first=RegionalInitialTest.seed("entry-candidate",0,texts[0]);
        var literals=new ArrayList<Expressions.Literal>();
        for(int i=0;i<texts.length;i++)literals.add(((Entries.LiteralInitial)RegionalInitialTest.seed("value-"+i,0,texts[i]).value()).value());
        var condition=new Entries.InitialCondition(first.place(),new Entries.PossibleLiterals(literals,GAP),first.origin(),List.of());
        var p=RegionalInitialTest.seeded(sequences,List.of(condition));var required=new ArrayList<>(p.capabilities().required());required.add(Capabilities.ENTRY_POSSIBILITIES);
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(required,List.of()),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),
            List.of(new Evidence.Uncertainty(GAP,"ENTRY_LIFECYCLE_OPEN",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"possible entry content",origin(P))),p.premises());
    }
    @Test void candidateAndUnknownCoexistAtEntryAndWithoutWrites() {
        var p=possible(List.of(returning(U,"s0",List.of())),"PGM00001","PGM00002");
        var execution=run(p);var value=at(execution,"return-s0",WHOLE);
        assertEquals(List.of("PGM00001","PGM00002"),texts(value));assertTrue(value.modelValueRemainder());
        var initial=execution.observe(List.of(new PointQuery<>(ProgramPoint.entry(new EntryId(U,"entry")),WHOLE))).observations().getFirst().value();
        assertEquals(texts(value),texts(initial));assertTrue(initial.modelValueRemainder());
        var rd=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
        var definitions=rd.observe(List.of(new PointQuery<>(ProgramPoint.entry(new EntryId(U,"entry")),WHOLE))).observations().getFirst().value();
        assertTrue(definitions.unknownRemainder());
        assertTrue(definitions.definitions().stream().anyMatch(d->d.definition().kind().name().equals("ENTRY_POSSIBILITY")));
        assertTrue(definitions.definitions().stream().allMatch(d->d.definition().operation().isEmpty()));
    }
    @Test void mayWholeMemoryAndEffectFreeOpaquePreserveCandidates() {
        for(boolean may:List.of(false,true)) {
            Scopes.MemoryBound writes=may?new Scopes.WithinMemory(new Scopes.AllMemory(P,true)):Scopes.NoMemory.INSTANCE;
            var envelope=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),writes,List.of()),
                new Control.ControlEnvelope(List.of(new Control.JumpAlternative(new LabelId(U,"exit"))),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
            var h=header(U,"opaque");
            var opaque=new Operations.Opaque(new Operations.Header(h.id(),h.origin(),h.coverage(),h.precision(),List.of(GAP)),"uninterpreted typed operation",List.of(),List.of(),envelope);
            var p=possible(List.of(new Sequence(new LabelId(U,"s0"),List.of(),opaque,origin(P)),returning(U,"exit",List.of())),"PGM00001");
            var value=at(run(p),"return-exit",WHOLE);assertEquals(List.of("PGM00001"),texts(value));assertTrue(value.modelValueRemainder());
        }
    }
    @Test void mustOverwriteKillsBothOldPossibilityAndOldLifecycleRemainder() {
        var value=at(run(possible(List.of(returning(U,"s0",List.of(assign(U,"overwrite",WHOLE,"OTHERPGM")))),"PGM00001")),"return-s0",WHOLE);
        assertEquals(List.of("OTHERPGM"),texts(value));assertFalse(value.modelValueRemainder());
        var h=header(U,"unknown-overwrite");var havoc=new Operations.HavocMust(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),WHOLE),GAP);
        value=at(run(possible(List.of(returning(U,"s0",List.of(havoc))),"PGM00001")),"return-s0",WHOLE);
        assertEquals(List.of(),texts(value));assertTrue(value.modelValueRemainder());
    }
    @Test void conditionalOverwritePreservesEntryAlternativeOnTheOtherPath() {
        var p=possible(List.of(branch(U,"s0","a","b"),with(jump(U,"a","exit"),assign(U,"overwrite",WHOLE,"OTHERPGM")),jump(U,"b","exit"),returning(U,"exit",List.of())),"PGM00001");
        var value=at(run(p),"return-exit",WHOLE);assertEquals(List.of("OTHERPGM","PGM00001"),texts(value));assertTrue(value.modelValueRemainder());
    }
    @Test void backedgeToInitialLabelNeverExecutesEntrySeedAgain() {
        var p=possible(List.of(with(jump(U,"s0","head"),assign(U,"overwrite",WHOLE,"OTHERPGM")),branch(U,"head","s0","exit"),returning(U,"exit",List.of())),"PGM00001");
        var value=at(run(p),"return-exit",WHOLE);assertEquals(List.of("OTHERPGM"),texts(value));assertFalse(value.modelValueRemainder());
        assertEquals(List.of("overwrite"),value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
    }
}
