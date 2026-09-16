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
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;

/** Oracles vary proof and execution, never a language keyword. */
class EvidencePreservingPolicyTest {
    @Test void unknownMayKeepsEntryCandidateAndItsOriginalSupport() {
        var may=new Operations.HavocMay(header(U,"future-effect"),new Scopes.AllMemory(P,true),PossibleEntryTest.GAP);
        var p=PossibleEntryTest.possible(List.of(returning(U,"s0",List.of(may))),"PGM00001");
        var value=at(run(p),"return-s0",WHOLE);
        assertEquals(List.of("PGM00001"),texts(value));assertTrue(value.modelValueRemainder());
        assertTrue(value.candidateSupports().getFirst().producers().stream().anyMatch(s->s.evidence() instanceof OperandId));
        var rd=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
        var fact=rd.observe(List.of(new PointQuery<>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),WHOLE))).observations().getFirst().value();
        assertTrue(fact.definitions().stream().anyMatch(d->d.definition().kind()==DefinitionEvent.Kind.ENTRY_POSSIBILITY));
    }
    @Test void missingAliasProofNeitherKillsNorCopiesTheOtherObjectsLiteral() {
        var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"supported",WHOLE,"PGM00001"),assign(U,"other-write",YWHOLE,"OTHERPGM")))));
        p=replace(p,p.units(),p.coverage(),p.uncertainties(),List.of());
        var value=at(run(p),"return-s0",WHOLE);
        assertEquals(List.of("PGM00001"),texts(value));assertTrue(value.modelValueRemainder());
        assertEquals(List.of("supported"),value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
    }
    @Test void unknownBindingPreservesEvidenceWithoutInventingPhysicalRanges() {
        var p=EvidencePreservingEntryTest.logical(List.of(returning(U,"s0",List.of(assign(U,"unproved-write",WHOLE,"OTHERPGM")))));
        var value=at(run(p),"return-s0",WHOLE);
        assertEquals(List.of("OTHERPGM","PGM00001"),texts(value));assertTrue(value.modelValueRemainder());
        assertTrue(value.candidateSupports().stream().allMatch(s->!s.producers().isEmpty()));
    }
    @Test void mustAndRepeatedQueriesCannotReseedEntryEvidence() {
        var p=PossibleEntryTest.possible(List.of(with(jump(U,"s0","head"),assign(U,"overwrite",WHOLE,"OTHERPGM")),
            branch(U,"head","s0","exit"),returning(U,"exit",List.of())),"PGM00001");
        var execution=run(p);
        for(int i=0;i<3;i++) {
            var value=at(execution,"return-exit",WHOLE);
            assertEquals(List.of("OTHERPGM"),texts(value));assertFalse(value.modelValueRemainder());
            assertEquals(List.of("overwrite"),value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
        }
    }
}
